package com.tidal.wear.debug

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.tidal.sdk.eventproducer.EventProducer
import com.tidal.sdk.eventproducer.model.EventsConfig
import com.tidal.sdk.player.common.model.AudioQuality
import com.tidal.sdk.player.common.model.AssetPresentation
import com.tidal.sdk.player.common.model.AudioMode
import com.tidal.sdk.player.offlineplay.OfflinePlayProvider
import com.tidal.sdk.player.playbackengine.Encryption
import com.tidal.sdk.player.playbackengine.offline.cache.OfflineCacheProvider
import com.tidal.sdk.player.streamingapi.offline.Storage
import com.tidal.sdk.player.streamingapi.playbackinfo.model.ManifestMimeType
import com.tidal.sdk.player.streamingapi.playbackinfo.model.PlaybackInfo
import com.tidal.sdk.player.streamingapi.playbackinfo.offline.OfflinePlaybackInfoProvider
import com.tidal.sdk.player.streamingapi.playbackinfo.model.PlaybackMode
import com.tidal.wear.BuildConfig
import com.tidal.wear.core.auth.TidalAuthRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Debug-only proof runner for UNTIDY-011.
 *
 * Starts from adb, uses the signed-in debug app session, and probes only sanctioned
 * offline/download surfaces. It intentionally redacts bearer tokens, manifest URIs,
 * download links, licenses, and other secret material from logs/artifacts.
 */
class OfflineProofService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.DEBUG) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val trackId = intent?.getStringExtra(EXTRA_TRACK_ID)?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_TRACK_ID
        val countryCode = intent?.getStringExtra(EXTRA_COUNTRY_CODE)?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_COUNTRY
        scope.launch {
            runCatching { runProof(trackId, countryCode) }
                .onFailure { Log.e(TAG, "offline proof failed reason=${it::class.java.simpleName}: ${it.message}") }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runProof(trackId: String, countryCode: String) {
        val authRepository = TidalAuthRepositoryProvider.get(applicationContext)
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientIdForApi()
        val account = authRepository.accountInfo.first()
        val results = mutableListOf<JsonObject>()
        val startedAt = Instant.now().toString()

        results += event(
            "auth",
            buildJsonObject {
                put("hasToken", !token.isNullOrBlank())
                put("tokenLength", token?.length ?: 0)
                put("clientIdHash", sha256Short(clientId))
                put("userIdPresent", !account?.userId.isNullOrBlank())
                put("scopeCount", account?.scopes?.size ?: 0)
                put("scopes", account?.scopes?.sorted()?.joinToString(" ").orEmpty())
            },
        )

        if (token.isNullOrBlank()) {
            writeArtifact(startedAt, trackId, countryCode, results)
            return
        }

        val manifestUrl = "https://openapi.tidal.com/v2/trackManifests/$trackId".toHttpUrl().newBuilder()
            .addQueryParameter("manifestType", "MPEG_DASH")
            .addQueryParameter("formats", "HEAACV1")
            .addQueryParameter("uriScheme", "DATA")
            .addQueryParameter("usage", "DOWNLOAD")
            .addQueryParameter("adaptive", "false")
            .addQueryParameter("countryCode", countryCode)
            .build()
        results += getJsonProbe("trackManifestDownload", manifestUrl.toString(), token, clientId) { root ->
            summarizeTrackManifest(root)
        }

        val installationId = probeInstallationInventory(trackId, countryCode, token, clientId, results)
        probeOfflineDiscoverySurfaces(trackId, countryCode, token, clientId, account?.userId, results)

        val downloadsUrl = "https://openapi.tidal.com/v2/downloads/$trackId".toHttpUrl().newBuilder()
            .addQueryParameter("countryCode", countryCode)
            .addQueryParameter("include", "owners")
            .build()
        results += getJsonProbe("downloadsByTrackId", downloadsUrl.toString(), token, clientId) { root ->
            summarizeDownloads(root)
        }

        val offlineTasksUrl = "https://openapi.tidal.com/v2/offlineTasks".toHttpUrl().newBuilder()
            .addQueryParameter("countryCode", countryCode)
            .build()
        results += getJsonProbe("offlineTasks", offlineTasksUrl.toString(), token, clientId) { root ->
            summarizeOfflineTasks(root)
        }

        val offlineTasksFilteredUrl = "https://openapi.tidal.com/v2/offlineTasks".toHttpUrl().newBuilder()
            .addQueryParameter("countryCode", countryCode)
            .addQueryParameter("filter[state]", "PENDING")
            .addQueryParameter("filter[state]", "IN_PROGRESS")
            .addQueryParameter("filter[state]", "COMPLETED")
            .addQueryParameter("filter[state]", "FAILED")
            .addQueryParameter("filter[action]", "ADD")
            .addQueryParameter("filter[action]", "REMOVE")
            .build()
        results += getJsonProbe("offlineTasksFiltered", offlineTasksFilteredUrl.toString(), token, clientId) { root ->
            summarizeOfflineTasks(root)
        }

        val offlineTasksGeneratedShapeUrl = "https://openapi.tidal.com/v2/offlineTasks".toHttpUrl().newBuilder()
            .addQueryParameter("include", "item,collection,owners")
            .build()
        results += getJsonProbe("offlineTasksGeneratedShape", offlineTasksGeneratedShapeUrl.toString(), token, clientId) { root ->
            summarizeOfflineTasks(root)
        }

        val offlineTasksFirstPageUrl = "https://openapi.tidal.com/v2/offlineTasks".toHttpUrl().newBuilder()
            .addQueryParameter("page[cursor]", "0")
            .addQueryParameter("include", "item,collection,owners")
            .build()
        results += getJsonProbe("offlineTasksFirstPage", offlineTasksFirstPageUrl.toString(), token, clientId) { root ->
            summarizeOfflineTasks(root)
        }

        if (!installationId.isNullOrBlank()) {
            val offlineTasksByInstallationUrl = "https://openapi.tidal.com/v2/offlineTasks".toHttpUrl().newBuilder()
                .addQueryParameter("filter[installation.id]", installationId)
                .addQueryParameter("include", "item,collection,owners")
                .build()
            results += getJsonProbe("offlineTasksByInstallation", offlineTasksByInstallationUrl.toString(), token, clientId) { root ->
                summarizeOfflineTasks(root)
            }
        }

        results += offlineProviderWiringProbe(trackId)
        results += playerOfflineProviderInjectionProbe(trackId, authRepository)
        results += sdkOfflinePlaybackProbe(trackId, authRepository)

        val path = writeArtifact(startedAt, trackId, countryCode, results)
        Log.i(TAG, "offline proof complete artifact=$path")
    }

    private fun getJsonProbe(
        name: String,
        url: String,
        token: String,
        clientId: String,
        summarize: (JsonObject?) -> JsonObject,
    ): JsonObject = requestJsonProbe(name, "GET", url, token, clientId, null, summarize = summarize)

    private fun postJsonProbe(
        name: String,
        url: String,
        token: String,
        clientId: String,
        body: JsonObject,
        summarize: (JsonObject?) -> JsonObject,
    ): JsonObject = requestJsonProbe(name, "POST", url, token, clientId, body, emptyMap(), summarize)

    private fun postJsonProbeWithHeaders(
        name: String,
        url: String,
        token: String,
        clientId: String,
        body: JsonObject,
        headers: Map<String, String>,
        summarize: (JsonObject?) -> JsonObject,
    ): JsonObject = requestJsonProbe(name, "POST", url, token, clientId, body, headers, summarize)

    private fun requestJsonProbe(
        name: String,
        method: String,
        url: String,
        token: String,
        clientId: String,
        body: JsonObject?,
        extraHeaders: Map<String, String> = emptyMap(),
        summarize: (JsonObject?) -> JsonObject,
    ): JsonObject {
        val requestBody = body?.toString()?.toRequestBody(JSON_API_ACCEPT.toMediaType())
        val builder = Request.Builder()
            .url(url)
            .header("accept", JSON_API_ACCEPT)
            .header("X-Tidal-Token", clientId)
            .header("Authorization", "Bearer $token")
        extraHeaders.forEach { (key, value) -> builder.header(key, value) }
        if (requestBody != null) {
            builder.header("content-type", JSON_API_ACCEPT).method(method, requestBody)
        } else {
            builder.get()
        }
        val request = builder.build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                val root = bodyText.takeIf { it.isNotBlank() }?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
                event(
                    name,
                    buildJsonObject {
                        put("method", method)
                        put("urlPathHash", sha256Short(url.substringBefore('?')))
                        put("status", response.code)
                        put("successful", response.isSuccessful)
                        put("bodyBytes", bodyText.toByteArray().size)
                        put("topLevelKeys", root?.keys?.sorted()?.joinToString(",").orEmpty())
                        put("summary", summarize(root).toString())
                        put("errorSummary", summarizeErrors(root).toString())
                    },
                )
            }
        }.getOrElse {
            event(name, buildJsonObject {
                put("exception", it::class.java.simpleName)
                put("message", it.message.orEmpty().take(160))
            })
        }
    }

    private fun probeInstallationInventory(
        trackId: String,
        countryCode: String,
        token: String,
        clientId: String,
        results: MutableList<JsonObject>,
    ): String? {
        val prefs = getSharedPreferences("offline-proof", MODE_PRIVATE)
        val clientProvidedId = prefs.getString("clientProvidedInstallationId", null)
            ?.takeIf { it.isNotBlank() }
            ?: "untidy-debug-${UUID.randomUUID()}".also {
                prefs.edit().putString("clientProvidedInstallationId", it).apply()
            }

        val lookupUrl = "https://openapi.tidal.com/v2/installations".toHttpUrl().newBuilder()
            .addQueryParameter("filter[clientProvidedInstallationId]", clientProvidedId)
            .addQueryParameter("include", "offlineInventory,owners")
            .build()
        val lookup = getJsonProbe("installationsByClientProvidedId", lookupUrl.toString(), token, clientId) { root ->
            summarizeInstallations(root, clientProvidedId)
        }
        results += lookup

        var installationId = lookup.stringFromSummary("firstId")
        if (installationId.isNullOrBlank()) {
            val createUrl = "https://openapi.tidal.com/v2/installations".toHttpUrl().newBuilder()
                .build()
            val createBody = buildJsonObject {
                put("data", buildJsonObject {
                    put("type", "installations")
                    put("attributes", buildJsonObject {
                        put("clientProvidedInstallationId", clientProvidedId)
                        put("name", "Untidy Debug Offline Proof")
                    })
                })
            }
            val create = postJsonProbeWithHeaders(
                "installationsCreateWithIdempotencyKey",
                createUrl.toString(),
                token,
                clientId,
                createBody,
                mapOf("Idempotency-Key" to "untidy-installation-$clientProvidedId"),
            ) { root ->
                summarizeInstallations(root, clientProvidedId)
            }
            results += create
            installationId = create.stringFromSummary("firstId")
        }

        if (installationId.isNullOrBlank()) {
            return null
        }

        val inventoryPostUrl = "https://openapi.tidal.com/v2/installations/$installationId/relationships/offlineInventory".toHttpUrl().newBuilder()
            .build()
        val inventoryBody = buildJsonObject {
            put("data", JsonArray(listOf(buildJsonObject {
                put("id", trackId)
                put("type", "tracks")
            })))
        }
        results += postJsonProbeWithHeaders(
            "installationOfflineInventoryAddTrackWithIdempotencyKey",
            inventoryPostUrl.toString(),
            token,
            clientId,
            inventoryBody,
            mapOf("Idempotency-Key" to "untidy-inventory-$installationId-$trackId"),
        ) { root ->
            summarizeRelationshipMutation(root)
        }

        val inventoryGetUrl = "https://openapi.tidal.com/v2/installations/$installationId/relationships/offlineInventory".toHttpUrl().newBuilder()
            .addQueryParameter("page[cursor]", "0")
            .addQueryParameter("include", "items")
            .addQueryParameter("filter[state]", "PENDING")
            .addQueryParameter("filter[state]", "STORED")
            .addQueryParameter("filter[type]", "tracks")
            .build()
        results += getJsonProbe("installationOfflineInventoryGet", inventoryGetUrl.toString(), token, clientId) { root ->
            summarizeOfflineInventory(root, trackId)
        }

        return installationId
    }

    private fun probeOfflineDiscoverySurfaces(
        trackId: String,
        countryCode: String,
        token: String,
        clientId: String,
        userId: String?,
        results: MutableList<JsonObject>,
    ) {
        val downloadsListUrl = "https://openapi.tidal.com/v2/downloads".toHttpUrl().newBuilder()
            .addQueryParameter("include", "owners")
            .addQueryParameter("filter[id]", trackId)
            .build()
        results += getJsonProbe("downloadsListFilterTrackId", downloadsListUrl.toString(), token, clientId) { root ->
            summarizeDownloadsList(root)
        }

        if (!userId.isNullOrBlank()) {
            val ownerInstallationsUrl = "https://openapi.tidal.com/v2/installations".toHttpUrl().newBuilder()
                .addQueryParameter("page[cursor]", "0")
                .addQueryParameter("include", "offlineInventory,owners")
                .addQueryParameter("filter[owners.id]", userId)
                .build()
            results += getJsonProbe("installationsByOwnerId", ownerInstallationsUrl.toString(), token, clientId) { root ->
                summarizeInstallations(root, clientProvidedId = "")
            }

            val offlineMixUrl = "https://openapi.tidal.com/v2/userOfflineMixes/$userId".toHttpUrl().newBuilder()
                .addQueryParameter("countryCode", countryCode)
                .addQueryParameter("locale", "en_US")
                .addQueryParameter("include", "items")
                .build()
            results += getJsonProbe("userOfflineMixByUserId", offlineMixUrl.toString(), token, clientId) { root ->
                summarizeUserOfflineMix(root)
            }

            val offlineMixUrlHyphenLocale = "https://openapi.tidal.com/v2/userOfflineMixes/$userId".toHttpUrl().newBuilder()
                .addQueryParameter("countryCode", countryCode)
                .addQueryParameter("locale", "en-US")
                .addQueryParameter("include", "items")
                .build()
            results += getJsonProbe("userOfflineMixByUserIdHyphenLocale", offlineMixUrlHyphenLocale.toString(), token, clientId) { root ->
                summarizeUserOfflineMix(root)
            }

            val offlineMixItemsUrl = "https://openapi.tidal.com/v2/userOfflineMixes/$userId/relationships/items".toHttpUrl().newBuilder()
                .addQueryParameter("page[cursor]", "0")
                .addQueryParameter("locale", "en_US")
                .addQueryParameter("include", "items")
                .build()
            results += getJsonProbe("userOfflineMixItemsByUserId", offlineMixItemsUrl.toString(), token, clientId) { root ->
                summarizeRelationshipItems(root)
            }

            val offlineMixItemsNoCursorUrl = "https://openapi.tidal.com/v2/userOfflineMixes/$userId/relationships/items".toHttpUrl().newBuilder()
                .addQueryParameter("locale", "en-US")
                .addQueryParameter("include", "items")
                .build()
            results += getJsonProbe("userOfflineMixItemsByUserIdNoCursor", offlineMixItemsNoCursorUrl.toString(), token, clientId) { root ->
                summarizeRelationshipItems(root)
            }

            val offlineMixItemsNoIncludeUrl = "https://openapi.tidal.com/v2/userOfflineMixes/$userId/relationships/items".toHttpUrl().newBuilder()
                .addQueryParameter("locale", "en-US")
                .build()
            results += getJsonProbe("userOfflineMixItemsByUserIdNoCursorNoInclude", offlineMixItemsNoIncludeUrl.toString(), token, clientId) { root ->
                summarizeRelationshipItems(root)
            }
        }
    }

    private fun offlineProviderWiringProbe(trackId: String): JsonObject {
        var cache: SimpleCache? = null
        return runCatching {
            val cacheDir = File(filesDir, "offline-proof/cache-$trackId").apply { mkdirs() }
            cache = SimpleCache(cacheDir, NoOpCacheEvictor(), StandaloneDatabaseProvider(applicationContext))
            val storage = Storage(externalStorage = false, path = cacheDir.absolutePath)
            val track = PlaybackInfo.Track(
                trackId.toIntOrNull() ?: 0,
                AudioQuality.LOW,
                AssetPresentation.FULL,
                AudioMode.STEREO,
                "untidy-proof-manifest-hash",
                null,
                "untidy-proof-session-${UUID.randomUUID()}",
                ManifestMimeType.DASH,
                "untidy-proof-manifest-placeholder",
                "",
                0f,
                0f,
                0f,
                0f,
                0L,
                0L,
            )
            val offlineTrack = PlaybackInfo.Offline.Track(
                track = track,
                offlineLicense = "",
                storage = storage,
                partiallyEncrypted = false,
            )
            val provider = OfflinePlayProvider(
                offlinePlaybackInfoProvider = object : OfflinePlaybackInfoProvider {
                    override suspend fun getOfflineTrackPlaybackInfo(trackId: String, streamingSessionId: String): PlaybackInfo = offlineTrack
                    override suspend fun getOfflineVideoPlaybackInfo(videoId: String, streamingSessionId: String): PlaybackInfo {
                        throw UnsupportedOperationException("video offline proof not implemented")
                    }
                },
                offlineCacheProvider = object : OfflineCacheProvider {
                    override fun getExternal(path: String): Cache = requireNotNull(cache) { "cache released" }
                    override fun getInternal(path: String): Cache = requireNotNull(cache) { "cache released" }
                },
                encryption = object : Encryption {
                    override val secretKey: ByteArray = MessageDigest.getInstance("SHA-256")
                        .digest("untidy-debug-offline-proof".toByteArray())
                    override fun getDecryptedHeader(productId: String): ByteArray = ByteArray(0)
                },
            )
            val resolvedInfo = kotlinx.coroutines.runBlocking {
                requireNotNull(provider.offlinePlaybackInfoProvider).getOfflineTrackPlaybackInfo(trackId, "proof-session")
            }
            val internalCache = requireNotNull(provider.offlineCacheProvider).getInternal(storage.path)
            event("offlineProviderWiring", buildJsonObject {
                put("providerConstructed", true)
                put("offlineInfoClass", resolvedInfo.javaClass.name)
                put("offlineLicensePresent", (resolvedInfo as? PlaybackInfo.Offline.Track)?.offlineLicense?.isNotBlank() == true)
                put("storageExternal", (resolvedInfo as? PlaybackInfo.Offline.Track)?.storage?.externalStorage ?: true)
                put("storagePathHash", sha256Short((resolvedInfo as? PlaybackInfo.Offline.Track)?.storage?.path.orEmpty()))
                put("cacheClass", internalCache.javaClass.name)
                put("cacheUidPresent", internalCache.uid != Cache.UID_UNSET)
                put("cacheKeysCount", internalCache.keys.size)
                put("secretKeyLength", requireNotNull(provider.encryption).secretKey.size)
                put("decryptedHeaderLength", requireNotNull(provider.encryption).getDecryptedHeader("proof").size)
            })
        }.getOrElse {
            event("offlineProviderWiring", buildJsonObject {
                put("exception", it::class.java.simpleName)
                put("message", it.message.orEmpty().take(180))
            })
        }.also {
            runCatching { cache?.release() }
        }
    }

    private suspend fun playerOfflineProviderInjectionProbe(trackId: String, authRepository: com.tidal.wear.core.auth.TidalAuthRepository): JsonObject {
        var cache: SimpleCache? = null
        return runCatching {
            val cacheDir = File(filesDir, "offline-proof/player-cache-$trackId").apply { mkdirs() }
            cache = SimpleCache(cacheDir, NoOpCacheEvictor(), StandaloneDatabaseProvider(applicationContext))
            val storage = Storage(externalStorage = false, path = cacheDir.absolutePath)
            val track = PlaybackInfo.Track(
                trackId.toIntOrNull() ?: 0,
                AudioQuality.LOW,
                AssetPresentation.FULL,
                AudioMode.STEREO,
                "untidy-proof-player-manifest-hash",
                null,
                "untidy-proof-player-session-${UUID.randomUUID()}",
                ManifestMimeType.DASH,
                "untidy-proof-player-manifest-placeholder",
                "",
                0f,
                0f,
                0f,
                0f,
                0L,
                0L,
            )
            val offlineTrack = PlaybackInfo.Offline.Track(
                track = track,
                offlineLicense = "",
                storage = storage,
                partiallyEncrypted = false,
            )
            val offlineProvider = OfflinePlayProvider(
                offlinePlaybackInfoProvider = object : OfflinePlaybackInfoProvider {
                    override suspend fun getOfflineTrackPlaybackInfo(trackId: String, streamingSessionId: String): PlaybackInfo = offlineTrack
                    override suspend fun getOfflineVideoPlaybackInfo(videoId: String, streamingSessionId: String): PlaybackInfo {
                        throw UnsupportedOperationException("video offline proof not implemented")
                    }
                },
                offlineCacheProvider = object : OfflineCacheProvider {
                    override fun getExternal(path: String): Cache = requireNotNull(cache) { "cache released" }
                    override fun getInternal(path: String): Cache = requireNotNull(cache) { "cache released" }
                },
                encryption = object : Encryption {
                    override val secretKey: ByteArray = MessageDigest.getInstance("SHA-256")
                        .digest("untidy-debug-player-offline-provider".toByteArray())
                    override fun getDecryptedHeader(productId: String): ByteArray = ByteArray(0)
                },
            )
            val eventProducer = EventProducer.getInstance(
                credentialsProvider = authRepository.credentialsProvider,
                config = EventsConfig(maxDiskUsageBytes = 1_000_000, blockedConsentCategories = emptySet(), appVersion = "0.1.0-debug"),
                context = applicationContext,
                coroutineScope = scope,
            )
            val player = com.tidal.sdk.player.Player(
                application = applicationContext as Application,
                credentialsProvider = authRepository.credentialsProvider,
                eventSender = eventProducer.eventSender,
                useLibflacAudioRenderer = false,
                enableDecoderFallback = true,
                offlinePlayProvider = offlineProvider,
                version = "0.1.0-debug",
            )
            try {
                val info = player.streamingApi.getOfflineTrackPlaybackInfo(trackId, "proof-session-${UUID.randomUUID()}")
                val offline = info as? PlaybackInfo.Offline.Track
                event("playerOfflineProviderInjection", buildJsonObject {
                    put("playerConstructed", true)
                    put("providerInjected", true)
                    put("offlineInfoReturned", offline != null)
                    put("offlineInfoClass", info.javaClass.name)
                    put("manifestPresent", reflectedString(info, "getManifest")?.isNotBlank() == true)
                    put("offlineLicensePresent", offline?.offlineLicense?.isNotBlank() == true)
                    put("storagePathHash", sha256Short(offline?.storage?.path.orEmpty()))
                    put("storageExternal", offline?.storage?.externalStorage ?: true)
                    put("cacheUidPresent", cache?.uid != Cache.UID_UNSET)
                    put("cacheKeysCount", cache?.keys?.size ?: 0)
                    put("secretKeyLength", requireNotNull(offlineProvider.encryption).secretKey.size)
                })
            } finally {
                player.release()
            }
        }.getOrElse {
            event("playerOfflineProviderInjection", buildJsonObject {
                put("exception", it::class.java.simpleName)
                put("message", it.message.orEmpty().take(180))
            })
        }.also {
            runCatching { cache?.release() }
        }
    }

    private suspend fun sdkOfflinePlaybackProbe(trackId: String, authRepository: com.tidal.wear.core.auth.TidalAuthRepository): JsonObject {
        return runCatching {
            val eventProducer = EventProducer.getInstance(
                credentialsProvider = authRepository.credentialsProvider,
                config = EventsConfig(maxDiskUsageBytes = 1_000_000, blockedConsentCategories = emptySet(), appVersion = "0.1.0-debug"),
                context = applicationContext,
                coroutineScope = scope,
            )
            val player = com.tidal.sdk.player.Player(
                application = applicationContext as Application,
                credentialsProvider = authRepository.credentialsProvider,
                eventSender = eventProducer.eventSender,
                useLibflacAudioRenderer = false,
                enableDecoderFallback = true,
                version = "0.1.0-debug",
            )
            try {
                val info = player.streamingApi.getTrackPlaybackInfo(
                    trackId,
                    AudioQuality.LOW,
                    PlaybackMode.OFFLINE,
                    false,
                    UUID.randomUUID().toString(),
                    false,
                )
                event("sdkPlaybackModeOffline", reflectPlaybackInfo(info))
            } finally {
                player.release()
            }
        }.getOrElse {
            event("sdkPlaybackModeOffline", buildJsonObject {
                put("exception", it::class.java.simpleName)
                put("message", it.message.orEmpty().take(180))
            })
        }
    }

    private fun reflectPlaybackInfo(info: Any?): JsonObject = buildJsonObject {
        put("returned", info != null)
        put("className", info?.javaClass?.name.orEmpty())
        if (info != null) {
            put("manifestPresent", reflectedString(info, "getManifest")?.isNotBlank() == true)
            put("manifestLength", reflectedString(info, "getManifest")?.length ?: 0)
            put("licenseUrlPresent", reflectedString(info, "getLicenseUrl")?.isNotBlank() == true)
            put("streamingSessionIdPresent", reflectedString(info, "getStreamingSessionId")?.isNotBlank() == true)
            put("offlineRevalidateAt", reflectedLong(info, "getOfflineRevalidateAt"))
            put("offlineValidUntil", reflectedLong(info, "getOfflineValidUntil"))
        }
    }

    private fun reflectedString(target: Any, method: String): String? = runCatching {
        target.javaClass.methods.firstOrNull { it.name == method && it.parameterCount == 0 }?.invoke(target) as? String
    }.getOrNull()

    private fun reflectedLong(target: Any, method: String): Long = runCatching {
        (target.javaClass.methods.firstOrNull { it.name == method && it.parameterCount == 0 }?.invoke(target) as? Number)?.toLong() ?: 0L
    }.getOrDefault(0L)

    private fun summarizeInstallations(root: JsonObject?, clientProvidedId: String): JsonObject = buildJsonObject {
        val data = root?.get("data")
        val first = when (data) {
            is JsonArray -> data.firstOrNull()?.jsonObjectOrNull()
            is JsonObject -> data
            else -> null
        }
        val items = when (data) {
            is JsonArray -> data
            is JsonObject -> JsonArray(listOf(data))
            else -> JsonArray(emptyList())
        }
        val firstId = first?.string("id").orEmpty()
        val attrs = first?.jsonObject("attributes")
        put("count", items.size)
        put("firstId", firstId)
        put("firstIdPresent", firstId.isNotBlank())
        put("firstIdHash", sha256Short(firstId))
        put("clientProvidedInstallationIdHash", sha256Short(clientProvidedId))
        put("matchedClientProvidedId", attrs?.string("clientProvidedInstallationId") == clientProvidedId)
        put("namePresent", !attrs?.string("name").isNullOrBlank())
    }

    private fun summarizeRelationshipMutation(root: JsonObject?): JsonObject = buildJsonObject {
        put("emptyBody", root == null)
        put("topLevelKeys", root?.keys?.sorted()?.joinToString(",").orEmpty())
    }

    private fun summarizeOfflineInventory(root: JsonObject?, trackId: String): JsonObject = buildJsonObject {
        val data = root?.get("data")
        val items = when (data) {
            is JsonArray -> data
            else -> JsonArray(emptyList())
        }
        put("count", items.size)
        put("trackPresent", items.any { item ->
            val obj = item.jsonObjectOrNull()
            obj?.string("id") == trackId && obj.string("type") == "tracks"
        })
        put("types", items.mapNotNull { it.jsonObjectOrNull()?.string("type") }.distinct().joinToString(","))
        put("metaKeys", items.firstOrNull()?.jsonObjectOrNull()?.jsonObject("meta")?.keys?.sorted()?.joinToString(",").orEmpty())
    }

    private fun summarizeTrackManifest(root: JsonObject?): JsonObject = buildJsonObject {
        val attrs = root?.jsonObject("data")?.jsonObject("attributes")
        put("dataType", root?.jsonObject("data")?.string("type").orEmpty())
        put("dataIdPresent", !root?.jsonObject("data")?.string("id").isNullOrBlank())
        put("uriPresent", !attrs?.string("uri").isNullOrBlank())
        put("uriLength", attrs?.string("uri")?.length ?: 0)
        put("hashPresent", !attrs?.string("hash").isNullOrBlank())
        val drm = attrs?.jsonObject("drmData")
        put("drmDataPresent", drm != null)
        put("drmLicenseUrlPresent", !drm?.string("licenseUrl").isNullOrBlank())
        put("drmCertificateUrlPresent", !drm?.string("certificateUrl").isNullOrBlank())
        put("drmInitDataCount", drm?.jsonArray("initData")?.size ?: 0)
        put("formatCount", attrs?.jsonArray("formats")?.size ?: 0)
    }

    private fun summarizeDownloadsList(root: JsonObject?): JsonObject = buildJsonObject {
        val data = root?.get("data")
        val items = when (data) {
            is JsonArray -> data
            else -> JsonArray(emptyList())
        }
        put("count", items.size)
        put("firstIdPresent", !items.firstOrNull()?.jsonObjectOrNull()?.string("id").isNullOrBlank())
        put("types", items.mapNotNull { it.jsonObjectOrNull()?.string("type") }.distinct().joinToString(","))
    }

    private fun summarizeDownloads(root: JsonObject?): JsonObject = buildJsonObject {
        val attrs = root?.jsonObject("data")?.jsonObject("attributes")
        val links = attrs?.jsonArray("downloadLinks")
        put("dataType", root?.jsonObject("data")?.string("type").orEmpty())
        put("dataIdPresent", !root?.jsonObject("data")?.string("id").isNullOrBlank())
        put("downloadLinkCount", links?.size ?: 0)
        put("anyHrefPresent", links?.any { it.jsonObjectOrNull()?.string("href")?.isNotBlank() == true } == true)
    }

    private fun summarizeUserOfflineMix(root: JsonObject?): JsonObject = buildJsonObject {
        val data = root?.jsonObject("data")
        put("dataType", data?.string("type").orEmpty())
        put("dataIdPresent", !data?.string("id").isNullOrBlank())
        put("relationshipKeys", data?.jsonObject("relationships")?.keys?.sorted()?.joinToString(",").orEmpty())
    }

    private fun summarizeRelationshipItems(root: JsonObject?): JsonObject = buildJsonObject {
        val data = root?.get("data")
        val items = when (data) {
            is JsonArray -> data
            else -> JsonArray(emptyList())
        }
        put("count", items.size)
        put("types", items.mapNotNull { it.jsonObjectOrNull()?.string("type") }.distinct().joinToString(","))
        put("firstIdPresent", !items.firstOrNull()?.jsonObjectOrNull()?.string("id").isNullOrBlank())
    }

    private fun summarizeOfflineTasks(root: JsonObject?): JsonObject = buildJsonObject {
        val data = root?.get("data")
        val items = when (data) {
            is JsonArray -> data
            else -> JsonArray(emptyList())
        }
        put("count", items.size)
        put("states", items.mapNotNull { it.jsonObjectOrNull()?.jsonObject("attributes")?.string("state") }.distinct().joinToString(","))
        put("actions", items.mapNotNull { it.jsonObjectOrNull()?.jsonObject("attributes")?.string("action") }.distinct().joinToString(","))
    }

    private fun summarizeErrors(root: JsonObject?): JsonObject = buildJsonObject {
        val errors = root?.jsonArray("errors").orEmpty()
        put("count", errors.size)
        put("statuses", errors.mapNotNull { it.jsonObjectOrNull()?.string("status") }.distinct().joinToString(","))
        put("codes", errors.mapNotNull { it.jsonObjectOrNull()?.string("code") }.distinct().joinToString(","))
        put("titles", errors.mapNotNull { it.jsonObjectOrNull()?.string("title") }.distinct().joinToString(" | ").take(240))
    }

    private fun writeArtifact(startedAt: String, trackId: String, countryCode: String, results: List<JsonObject>): String {
        val dir = File(filesDir, "offline-proof").apply { mkdirs() }
        val stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-').replace('.', '-')
        val file = File(dir, "offline-proof-$stamp.json")
        val payload = buildJsonObject {
            put("issue", "UNTIDY-011")
            put("startedAt", startedAt)
            put("finishedAt", Instant.now().toString())
            put("trackId", trackId)
            put("countryCode", countryCode)
            put("redaction", "tokens, manifest URIs, download hrefs, licenses, and secrets are omitted")
            put("events", JsonArray(results))
        }
        file.writeText(payload.toString())
        File(dir, "latest.json").writeText(payload.toString())
        return file.absolutePath
    }

    private fun event(name: String, fields: JsonObject): JsonObject = buildJsonObject {
        put("name", name)
        put("at", Instant.now().toString())
        put("fields", fields)
    }

    private fun sha256Short(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(12)

    private fun JsonObject.stringFromSummary(key: String): String? {
        val summary = this.jsonObject("fields")?.string("summary") ?: return null
        return runCatching { json.parseToJsonElement(summary).jsonObject.string(key) }.getOrNull()
    }

    private fun JsonObject.jsonObject(key: String): JsonObject? = this[key]?.jsonObjectOrNull()
    private fun JsonObject.jsonArray(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    companion object {
        private const val TAG = "OfflineProof"
        const val EXTRA_TRACK_ID = "trackId"
        const val EXTRA_COUNTRY_CODE = "countryCode"
        private const val DEFAULT_TRACK_ID = "5120026"
        private const val DEFAULT_COUNTRY = "US"
        private const val JSON_API_ACCEPT = "application/vnd.api+json"
    }
}

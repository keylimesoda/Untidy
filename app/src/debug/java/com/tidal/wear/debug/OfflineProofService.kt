package com.tidal.wear.debug

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.tidal.sdk.eventproducer.EventProducer
import com.tidal.sdk.eventproducer.model.EventsConfig
import com.tidal.sdk.player.common.model.AudioQuality
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
import okhttp3.Request
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
    ): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("accept", JSON_API_ACCEPT)
            .header("X-Tidal-Token", clientId)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = body.takeIf { it.isNotBlank() }?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
                event(
                    name,
                    buildJsonObject {
                        put("method", "GET")
                        put("urlPathHash", sha256Short(url.substringBefore('?')))
                        put("status", response.code)
                        put("successful", response.isSuccessful)
                        put("bodyBytes", body.toByteArray().size)
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

    private fun summarizeDownloads(root: JsonObject?): JsonObject = buildJsonObject {
        val attrs = root?.jsonObject("data")?.jsonObject("attributes")
        val links = attrs?.jsonArray("downloadLinks")
        put("dataType", root?.jsonObject("data")?.string("type").orEmpty())
        put("dataIdPresent", !root?.jsonObject("data")?.string("id").isNullOrBlank())
        put("downloadLinkCount", links?.size ?: 0)
        put("anyHrefPresent", links?.any { it.jsonObjectOrNull()?.string("href")?.isNotBlank() == true } == true)
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

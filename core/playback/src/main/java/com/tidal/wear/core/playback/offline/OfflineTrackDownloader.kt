@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback.offline

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.dash.offline.DashDownloader
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.playback.manifestFormatAttempts
import com.tidal.wear.core.playback.settings.sharedSettingsDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tidal.wear.core.playback.settings.downloadOverLteEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

class OfflineTrackDownloader(
    context: Context,
    private val authRepository: TidalAuthRepository,
) {
    private val appContext = context.applicationContext
    private val client = com.tidal.wear.core.auth.TidalOkHttp.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadTrack(track: TidalTrack): OfflineDownloadResult = withContext(Dispatchers.IO) {
        val record = track.toOfflineDownloadRecordForDownloader()
            ?: return@withContext OfflineDownloadResult.Skipped
        if (appContext.isOfflineTrackDownloadRemovalRequested(record.id)) {
            return@withContext OfflineDownloadResult.Skipped
        }
        val downloadOverLteEnabled = appContext.downloadOverLteEnabled.first()
        if (!appContext.hasAllowedOfflineDownloadNetwork(downloadOverLteEnabled)) {
            // Keep direct, user-initiated downloads subject to the same policy
            // as collection downloads. The durable queue will retry when the
            // required network becomes available.
            OfflineDownloadScheduler(appContext).enqueue(listOf(track))
            return@withContext OfflineDownloadResult.WaitingForNetwork
        }
        try {
            val token = authRepository.getAccessToken() ?: throw IOException("No TIDAL access token")
            val clientId = authRepository.getClientIdForApi()
            val presetName = appContext.sharedSettingsDataStore.data.first()[stringPreferencesKey("audio_preset")]
            val preset = AudioPreset.entries.firstOrNull { it.name == presetName } ?: AudioPreset.BatterySaver
            val manifest = fetchDownloadManifest(record.id, token, clientId, preset)
            val cacheDir = appContext.offlineTrackCacheDir(record.id).apply { mkdirs() }
            val cache = OfflineCacheRegistry.acquire(appContext, record.id)
            var discardCache = false
            try {
                val dataSourceFactory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setCacheKeyFactory(canonicalDownloadCacheKeyFactory(record.id))
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            appContext,
                            androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                .setDefaultRequestProperties(
                                    mapOf(
                                        "accept" to "*/*",
                                        "X-Tidal-Token" to clientId,
                                        "Authorization" to "Bearer $token",
                                    ),
                                ),
                        ),
                    )
                val mediaItem = MediaItem.Builder()
                    .setMediaId(record.id)
                    .setUri(manifest.uri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .setCustomCacheKey("untidy-download-${record.id}-${sha256Short(manifest.uri)}")
                    .build()
                DashDownloader(mediaItem, dataSourceFactory).download { _, _, _ -> }
                if (cache.cacheSpace <= 0L || cache.keys.isEmpty()) {
                    throw IOException("Download produced no local cache")
                }
                if (appContext.isOfflineTrackDownloadRemovalRequested(record.id)) {
                    discardCache = true
                    return@withContext OfflineDownloadResult.Skipped
                }
                // Commit the marker and descriptor only after DashDownloader has completed and
                // the cache is non-empty. This prevents a completed-looking download that still
                // needs an authenticated manifest request at replay time.
                appContext.markOfflineTrackDownloaded(
                    track,
                    OfflinePlaybackDescriptor(
                        trackId = record.id,
                        manifestUri = manifest.uri,
                        format = manifest.format,
                        presentation = manifest.presentation,
                        previewReason = manifest.previewReason,
                    ),
                )
                OfflineDownloadResult.Downloaded
            } finally {
                runCatching { OfflineCacheRegistry.release(appContext, record.id) }
                if (discardCache) runCatching { cacheDir.deleteRecursively() }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            appContext.markOfflineTrackDownloadFailed(record.id)
            OfflineDownloadResult.Failed(error::class.java.simpleName)
        }
    }

    /**
     * Schedules collection downloads in WorkManager. This survives navigation and process death;
     * direct [downloadTrack] remains for the existing single-track action sheet flow.
     */
    suspend fun enqueueTracks(tracks: List<TidalTrack>): OfflineBatchScheduleResult =
        OfflineDownloadScheduler(appContext).enqueue(tracks)

    fun observeCollectionSummary(tracks: List<TidalTrack>) =
        OfflineDownloadScheduler(appContext).observeSummary(tracks)

    suspend fun downloadTracks(tracks: List<TidalTrack>): OfflineBatchDownloadResult {
        var downloaded = 0
        var failed = 0
        var skipped = 0
        tracks.toOfflineDownloadRecords().forEach { record ->
            val track = tracks.first { it.id == record.id }
            when (downloadTrack(track)) {
                OfflineDownloadResult.Downloaded -> downloaded++
                is OfflineDownloadResult.Failed -> failed++
                OfflineDownloadResult.Skipped -> skipped++
                OfflineDownloadResult.WaitingForNetwork -> skipped++
            }
        }
        return OfflineBatchDownloadResult(downloaded = downloaded, failed = failed, skipped = skipped)
    }

    private fun fetchDownloadManifest(trackId: String, token: String, clientId: String, preset: AudioPreset): DownloadManifest {
        var lastError: IOException? = null
        for (formats in manifestFormatAttempts(preset)) {
            try {
                return fetchDownloadManifestFormat(trackId, token, clientId, formats)
            } catch (error: DownloadFormatUnavailable) {
                lastError = error
            }
        }
        throw lastError ?: IOException("No download format available")
    }

    private fun fetchDownloadManifestFormat(trackId: String, token: String, clientId: String, formats: List<String>): DownloadManifest {
        val urlBuilder = "https://openapi.tidal.com/v2/trackManifests/$trackId".toHttpUrl().newBuilder()
            .addQueryParameter("manifestType", "MPEG_DASH")
            .addQueryParameter("uriScheme", "DATA")
            .addQueryParameter("usage", "DOWNLOAD")
            .addQueryParameter("adaptive", "false")
            .addQueryParameter("countryCode", countryCode())
        formats.forEach { urlBuilder.addQueryParameter("formats", it) }
        val url = urlBuilder.build()
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/vnd.api+json")
            .header("X-Tidal-Token", clientId)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code in listOf(403, 404, 406)) throw DownloadFormatUnavailable("Download format unavailable")
            if (!response.isSuccessful) throw IOException("download manifest ${response.code}")
            val attributes = JSONObject(body)
                .getJSONObject("data")
                .getJSONObject("attributes")
            val uri = attributes.optString("uri")
                .takeIf { it.isNotBlank() }
                ?: throw IOException("download manifest missing uri")
            val format = attributes.optJSONArray("formats")
                ?.let { formats -> List(formats.length()) { formats.optString(it) } }
                ?.lastOrNull { it.isNotBlank() }
                ?: formats.last()
            return DownloadManifest(
                uri = uri,
                format = format,
                presentation = attributes.optString("trackPresentation", "UNKNOWN"),
                previewReason = attributes.optString("previewReason").takeIf { it.isNotBlank() },
            )
        }
    }

    private fun countryCode(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
}

sealed interface OfflineDownloadResult {
    data object Downloaded : OfflineDownloadResult
    data class Failed(val reason: String) : OfflineDownloadResult
    data object Skipped : OfflineDownloadResult
    /** The request was persisted and is waiting for Wi-Fi or an allowed connected network. */
    data object WaitingForNetwork : OfflineDownloadResult
}

private data class DownloadManifest(
    val uri: String,
    val format: String,
    val presentation: String,
    val previewReason: String?,
)

data class OfflineBatchDownloadResult(
    val downloaded: Int,
    val failed: Int,
    val skipped: Int,
)

private fun canonicalDownloadCacheKeyFactory(trackId: String): CacheKeyFactory = CacheKeyFactory { dataSpec: DataSpec ->
    dataSpec.key ?: "untidy-download-$trackId-${sha256Short(dataSpec.uri.toString())}"
}

private fun TidalTrack.toOfflineDownloadRecordForDownloader(): OfflineDownloadRecord? =
    id.takeIf(String::isNotBlank)?.let {
        OfflineDownloadRecord(
            id = it,
            title = title.ifBlank { "Downloaded track" },
            artist = artist,
        )
    }

private fun sha256Short(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
    .take(12)

private class DownloadFormatUnavailable(message: String) : IOException(message)

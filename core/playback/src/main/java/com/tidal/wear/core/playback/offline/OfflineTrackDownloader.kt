@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tidal.wear.core.playback.offline

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.dash.offline.DashDownloader
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.core.model.TidalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadTrack(track: TidalTrack): OfflineDownloadResult = withContext(Dispatchers.IO) {
        val record = track.toOfflineDownloadRecordForDownloader()
            ?: return@withContext OfflineDownloadResult.Skipped
        runCatching {
            val token = authRepository.getAccessToken() ?: throw IOException("No TIDAL access token")
            val clientId = authRepository.getClientIdForApi()
            val manifestUri = fetchDownloadManifest(record.id, token, clientId)
            val cacheDir = appContext.offlineTrackCacheDir(record.id).apply { mkdirs() }
            val cache = SimpleCache(cacheDir, NoOpCacheEvictor(), StandaloneDatabaseProvider(appContext))
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
                    .setUri(manifestUri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .setCustomCacheKey("untidy-download-proof-${record.id}-${sha256Short(manifestUri)}")
                    .build()
                DashDownloader(mediaItem, dataSourceFactory).download { _, _, _ -> }
                if (cache.cacheSpace <= 0L || cache.keys.isEmpty()) {
                    throw IOException("Download produced no local cache")
                }
                appContext.markOfflineTrackDownloaded(track)
                OfflineDownloadResult.Downloaded
            } finally {
                runCatching { cache.release() }
            }
        }.getOrElse { error ->
            appContext.markOfflineTrackDownloadFailed(record.id)
            OfflineDownloadResult.Failed(error::class.java.simpleName)
        }
    }

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
            }
        }
        return OfflineBatchDownloadResult(downloaded = downloaded, failed = failed, skipped = skipped)
    }

    private fun fetchDownloadManifest(trackId: String, token: String, clientId: String): String {
        val url = "https://openapi.tidal.com/v2/trackManifests/$trackId".toHttpUrl().newBuilder()
            .addQueryParameter("manifestType", "MPEG_DASH")
            .addQueryParameter("formats", "HEAACV1")
            .addQueryParameter("uriScheme", "DATA")
            .addQueryParameter("usage", "DOWNLOAD")
            .addQueryParameter("adaptive", "false")
            .addQueryParameter("countryCode", countryCode())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/vnd.api+json")
            .header("X-Tidal-Token", clientId)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("download manifest ${response.code}: ${body.take(160)}")
            return JSONObject(body)
                .getJSONObject("data")
                .getJSONObject("attributes")
                .getString("uri")
                .takeIf { it.isNotBlank() }
                ?: throw IOException("download manifest missing uri")
        }
    }

    private fun countryCode(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
}

sealed interface OfflineDownloadResult {
    data object Downloaded : OfflineDownloadResult
    data class Failed(val reason: String) : OfflineDownloadResult
    data object Skipped : OfflineDownloadResult
}

data class OfflineBatchDownloadResult(
    val downloaded: Int,
    val failed: Int,
    val skipped: Int,
)

private fun canonicalDownloadCacheKeyFactory(trackId: String): CacheKeyFactory = CacheKeyFactory { dataSpec: DataSpec ->
    dataSpec.key ?: "untidy-download-proof-$trackId-${sha256Short(dataSpec.uri.toString())}"
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

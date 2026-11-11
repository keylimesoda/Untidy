package com.tidal.wear.core.auth

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single process-wide OkHttp foundation shared by every network user (auth,
 * API, playback manifests, downloads).
 *
 * Sharing one [Dispatcher] and one [ConnectionPool] lets sockets/TLS sessions
 * be reused between the UI, the media service, and WorkManager downloads
 * instead of each component keeping its own pools on a memory- and
 * battery-starved watch. Per-component timeouts and interceptors stay intact:
 * always derive with [newBuilder] (OkHttp copies carry the shared
 * dispatcher/connection pool) rather than constructing a new OkHttpClient.
 */
object TidalOkHttp {
    private val sharedDispatcher = Dispatcher()

    private val sharedConnectionPool = ConnectionPool(
        // OkHttp defaults are tuned for desktop; keep idle sockets shorter so
        // the radio can sleep between watch interactions.
        maxIdleConnections = 4,
        keepAliveDuration = 2,
        timeUnit = TimeUnit.MINUTES,
    )

    /** Base client with only shared plumbing; use [newBuilder] to specialize. */
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(sharedDispatcher)
            .connectionPool(sharedConnectionPool)
            .build()
    }

    fun newBuilder(): OkHttpClient.Builder = sharedClient.newBuilder()
}

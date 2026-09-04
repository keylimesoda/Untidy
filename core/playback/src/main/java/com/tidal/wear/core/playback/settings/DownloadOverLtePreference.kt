package com.tidal.wear.core.playback.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Shared download-network preference.
 *
 * This lives in the playback core rather than the app settings package so that
 * WorkManager and the settings UI always use the same persisted value. Missing
 * values intentionally resolve to false (Wi-Fi/unmetered only).
 */
val Context.downloadOverLteEnabled: Flow<Boolean>
    get() = sharedSettingsDataStore.data.map { preferences ->
        preferences[DOWNLOAD_OVER_LTE] ?: false
    }

suspend fun Context.setDownloadOverLteEnabled(enabled: Boolean) {
    sharedSettingsDataStore.edit { preferences ->
        preferences[DOWNLOAD_OVER_LTE] = enabled
    }
}

private val DOWNLOAD_OVER_LTE = booleanPreferencesKey("download_over_lte")

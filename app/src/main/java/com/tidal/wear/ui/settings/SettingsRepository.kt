package com.tidal.wear.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tidal.wear.core.model.AudioPreset
import com.tidal.wear.core.model.ReleaseVersionPreference
import com.tidal.wear.core.playback.offline.OfflineDownloadScheduler
import com.tidal.wear.core.playback.settings.downloadOverLteEnabled
import com.tidal.wear.core.playback.settings.setDownloadOverLteEnabled
import com.tidal.wear.core.playback.settings.sharedSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {
    val preset: Flow<AudioPreset> = context.sharedSettingsDataStore.data.map { prefs ->
        prefs[AUDIO_PRESET]?.let { runCatching { AudioPreset.valueOf(it) }.getOrNull() }
            ?: AudioPreset.BatterySaver
    }

    val releaseVersionPreference: Flow<ReleaseVersionPreference> = context.sharedSettingsDataStore.data.map { prefs ->
        prefs[RELEASE_VERSION_PREFERENCE]?.let { runCatching { ReleaseVersionPreference.valueOf(it) }.getOrNull() }
            ?: ReleaseVersionPreference.Explicit
    }

    val downloadOverLte: Flow<Boolean> = context.downloadOverLteEnabled

    suspend fun setPreset(p: AudioPreset) {
        context.sharedSettingsDataStore.edit { it[AUDIO_PRESET] = p.name }
    }

    suspend fun setReleaseVersionPreference(preference: ReleaseVersionPreference) {
        context.sharedSettingsDataStore.edit { it[RELEASE_VERSION_PREFERENCE] = preference.name }
    }

    suspend fun setDownloadOverLte(enabled: Boolean) {
        context.setDownloadOverLteEnabled(enabled)
        OfflineDownloadScheduler(context).refreshQueuedWorkConstraints()
    }

    private companion object {
        val AUDIO_PRESET = stringPreferencesKey("audio_preset")
        val RELEASE_VERSION_PREFERENCE = stringPreferencesKey("release_version_preference")
    }
}

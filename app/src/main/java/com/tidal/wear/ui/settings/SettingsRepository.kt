package com.tidal.wear.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tidal.wear.core.model.AudioPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "tidal_settings")

class SettingsRepository(private val context: Context) {
    val preset: Flow<AudioPreset> = context.settingsDataStore.data.map { prefs ->
        prefs[AUDIO_PRESET]?.let { runCatching { AudioPreset.valueOf(it) }.getOrNull() }
            ?: AudioPreset.BatterySaver
    }

    suspend fun setPreset(p: AudioPreset) {
        context.settingsDataStore.edit { it[AUDIO_PRESET] = p.name }
    }

    private companion object {
        val AUDIO_PRESET = stringPreferencesKey("audio_preset")
    }
}

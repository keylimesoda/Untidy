package com.tidal.wear.core.playback.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.sharedSettingsDataStore by preferencesDataStore(name = "tidal_settings")

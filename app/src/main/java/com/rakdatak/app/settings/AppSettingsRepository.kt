package com.rakdatak.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val soundCuesEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOnDuringWorkout: Boolean = true,
)

class AppSettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { preferences ->
        AppSettings(
            soundCuesEnabled = preferences[SOUND_CUES_ENABLED] ?: true,
            vibrationEnabled = preferences[VIBRATION_ENABLED] ?: true,
            keepScreenOnDuringWorkout = preferences[KEEP_SCREEN_ON] ?: true,
        )
    }

    suspend fun setSoundCuesEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[SOUND_CUES_ENABLED] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setKeepScreenOnDuringWorkout(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON] = enabled
        }
    }

    private companion object {
        val SOUND_CUES_ENABLED = booleanPreferencesKey("sound_cues_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on_during_workout")
    }
}

package com.example.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DecoderPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore by preferencesDataStore(name = "nova_settings")

class SettingsManager(private val context: Context) {

    private val KEY_AMOLED = booleanPreferencesKey("amoled_mode")
    private val KEY_GESTURES = booleanPreferencesKey("gestures_enabled")
    private val KEY_BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness_gesture")
    private val KEY_VOLUME_GESTURE = booleanPreferencesKey("volume_gesture")
    private val KEY_SEEK_GESTURE = booleanPreferencesKey("seek_gesture")
    private val KEY_SKIP_INTERVAL = intPreferencesKey("skip_interval_sec")
    private val KEY_DECODER_PREF = stringPreferencesKey("decoder_pref")
    private val KEY_RESUME_PROMPT = booleanPreferencesKey("resume_prompt")
    private val KEY_BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
    private val KEY_VAULT_PIN = stringPreferencesKey("vault_pin")

    val isAmoled: Flow<Boolean> = context.dataStore.data.mapCatching { it[KEY_AMOLED] ?: false }
    val isGesturesEnabled: Flow<Boolean> = context.dataStore.data.mapCatching { it[KEY_GESTURES] ?: true }
    val isBrightnessGestureEnabled: Flow<Boolean> = context.dataStore.data.mapCatching { it[KEY_BRIGHTNESS_GESTURE] ?: true }
    val isVolumeGestureEnabled: Flow<Boolean> = context.dataStore.data.mapCatching { it[KEY_VOLUME_GESTURE] ?: true }
    val isSeekGestureEnabled: Flow<Boolean> = context.dataStore.data.mapCatching { it[KEY_SEEK_GESTURE] ?: true }
    val skipIntervalSec: Flow<Int> = context.dataStore.data.mapCatching { it[KEY_SKIP_INTERVAL] ?: 10 }
    val decoderPreference: Flow<DecoderPreference> = context.dataStore.data.mapCatching {
        val name = it[KEY_DECODER_PREF] ?: DecoderPreference.AUTO.name
        try { DecoderPreference.valueOf(name) } catch (_: Exception) { DecoderPreference.AUTO }
    }
    val isResumePromptEnabled: Flow<Boolean> = context.dataStore.data.mapCatching { it[KEY_RESUME_PROMPT] ?: true }
    val isBackgroundAudioEnabled: Flow<Boolean> = context.dataStore.data.mapCatching { it[KEY_BACKGROUND_AUDIO] ?: true }
    val vaultPin: Flow<String?> = context.dataStore.data.mapCatching { it[KEY_VAULT_PIN] }

    suspend fun setAmoled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AMOLED] = enabled }
    }

    suspend fun setGesturesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GESTURES] = enabled }
    }

    suspend fun setSkipInterval(seconds: Int) {
        context.dataStore.edit { it[KEY_SKIP_INTERVAL] = seconds }
    }

    suspend fun setDecoderPreference(pref: DecoderPreference) {
        context.dataStore.edit { it[KEY_DECODER_PREF] = pref.name }
    }

    suspend fun setBackgroundAudio(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BACKGROUND_AUDIO] = enabled }
    }

    suspend fun setVaultPin(pin: String?) {
        context.dataStore.edit {
            if (pin == null) it.remove(KEY_VAULT_PIN) else it[KEY_VAULT_PIN] = pin
        }
    }

    private fun <T> Flow<Preferences>.mapCatching(transform: (Preferences) -> T): Flow<T> {
        return this.catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map(transform)
    }

    companion object {
        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getSettingsManager(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

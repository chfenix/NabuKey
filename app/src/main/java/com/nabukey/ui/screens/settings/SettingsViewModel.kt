package com.nabukey.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.nabukey.R
import com.nabukey.settings.MicrophoneSettingsStore
import com.nabukey.settings.PlayerSettingsStore
import com.nabukey.settings.VoiceSatelliteSettingsStore
import com.nabukey.wakewords.models.WakeWordWithId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@Immutable
data class MicrophoneState(
    val wakeWord: WakeWordWithId,
    val wakeWords: List<WakeWordWithId>,
    val customWakeWordLocation: Uri?
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val satelliteSettingsStore: VoiceSatelliteSettingsStore,
    private val playerSettingsStore: PlayerSettingsStore,
    private val microphoneSettingsStore: MicrophoneSettingsStore
) : ViewModel() {
    val satelliteSettingsState = satelliteSettingsStore.getFlow()

    val microphoneSettingsState = combine(
        microphoneSettingsStore.getFlow(),
        microphoneSettingsStore.availableWakeWords
    ) { settings, wakeWords ->
        MicrophoneState(
            wakeWord = wakeWords.firstOrNull { wakeWord ->
                wakeWord.id == settings.wakeWord
            } ?: wakeWords.first(),
            wakeWords = wakeWords,
            customWakeWordLocation = settings.customWakeWordLocation?.toUri()
        )
    }

    val playerSettingsState = playerSettingsStore.getFlow()

    suspend fun saveName(name: String) {
        if (validateName(name).isNullOrBlank()) {
            satelliteSettingsStore.name.set(name)
        } else {
            Log.w(TAG, "Cannot save invalid server name: $name")
        }
    }

    suspend fun saveServerPort(port: Int?) {
        if (validatePort(port).isNullOrBlank()) {
            satelliteSettingsStore.serverPort.set(port!!)
        } else {
            Log.w(TAG, "Cannot save invalid server port: $port")
        }
    }

    suspend fun saveAutoStart(autoStart: Boolean) {
        satelliteSettingsStore.autoStart.set(autoStart)
    }

    suspend fun saveForceContinuousConversation(enabled: Boolean) {
        satelliteSettingsStore.forceContinuousConversation.set(enabled)
    }

    suspend fun saveWakeWord(wakeWordId: String) {
        if (validateWakeWord(wakeWordId).isNullOrBlank()) {
            microphoneSettingsStore.wakeWord.set(wakeWordId)
        } else {
            Log.w(TAG, "Cannot save invalid wake word: $wakeWordId")
        }
    }

    suspend fun saveCustomWakeWordDirectory(uri: Uri?) {
        if (uri != null) {
            // Get persistable permission to read from the location
            // ToDo: This should potentially handled elsewhere
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            microphoneSettingsStore.customWakeWordLocation.set(uri.toString())
        }
    }

    suspend fun saveEnableWakeSound(enableWakeSound: Boolean) {
        playerSettingsStore.enableWakeSound.set(enableWakeSound)
    }


    fun validateName(name: String): String? =
        if (name.isBlank())
            context.getString(R.string.validation_voice_satellite_name_empty)
        else null


    fun validatePort(port: Int?): String? =
        if (port == null || port < 1 || port > 65535)
            context.getString(R.string.validation_voice_satellite_port_invalid)
        else null

    suspend fun saveVadThreshold(threshold: Float?) {
        val valid = threshold != null && threshold >= 0.01f && threshold <= 1.0f
        if (valid) {
            satelliteSettingsStore.vadThreshold.set(threshold!!)
        }
    }

    suspend fun saveSilenceTimeoutSeconds(timeout: Int?) {
        if (timeout != null && timeout > 0) {
            satelliteSettingsStore.silenceTimeoutSeconds.set(timeout)
        }
    }

    fun validateVadThreshold(threshold: Float?): String? =
        if (threshold == null || threshold < 0.01f || threshold > 1.0f) "Must be 0.01 - 1.0" else null

    fun validateSilenceTimeout(timeout: Int?): String? =
        if (timeout == null || timeout <= 0) "Must be > 0" else null

    suspend fun validateWakeWord(wakeWordId: String): String? {
        val wakeWordWithId = microphoneSettingsStore.availableWakeWords.first()
            .firstOrNull { it.id == wakeWordId }
        return if (wakeWordWithId == null)
            context.getString(R.string.validation_voice_satellite_wake_word_invalid)
        else
            null
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
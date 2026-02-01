package com.nabukey.esphome.voicesatellite

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import com.nabukey.esphome.Connected
import com.nabukey.esphome.EspHomeDevice
import com.nabukey.esphome.EspHomeState
import com.nabukey.esphome.entities.MediaPlayerEntity
import com.nabukey.esphome.entities.SwitchEntity
import com.nabukey.settings.VoiceSatelliteSettingsStore
import com.example.esphomeproto.api.DeviceInfoResponse
import com.example.esphomeproto.api.VoiceAssistantAnnounceRequest
import com.example.esphomeproto.api.VoiceAssistantConfigurationRequest
import com.example.esphomeproto.api.VoiceAssistantEventResponse
import com.example.esphomeproto.api.VoiceAssistantFeature
import com.example.esphomeproto.api.VoiceAssistantSetConfiguration
import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.VoiceAssistantTimerEventResponse
import com.example.esphomeproto.api.deviceInfoResponse
import com.example.esphomeproto.api.voiceAssistantAnnounceFinished
import com.example.esphomeproto.api.voiceAssistantConfigurationResponse
import com.example.esphomeproto.api.voiceAssistantWakeWord
import com.google.protobuf.MessageLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import android.content.Context
import com.nabukey.audio.VadDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder

data object Listening : EspHomeState
data object Responding : EspHomeState
data object Processing : EspHomeState
data object Waking : EspHomeState



class VoiceSatellite(
    val context: Context,
    coroutineContext: CoroutineContext,
    name: String,
    port: Int,
    val audioInput: VoiceSatelliteAudioInput,
    val player: VoiceSatellitePlayer,
    val settingsStore: VoiceSatelliteSettingsStore
) : EspHomeDevice(
    coroutineContext,
    name,
    port,
    listOf(
        MediaPlayerEntity(0, "Media Player", "media_player", player),
        SwitchEntity(
            1,
            "Mute Microphone",
            "mute_microphone",
            audioInput.muted
        ) { audioInput.setMuted(it) },
        SwitchEntity(
            2,
            "Play Wake Sound",
            "play_wake_sound",
            player.enableWakeSound
        ) { player.enableWakeSound.set(it) }
    )
) {
    private var timerFinished = false
    private var pipeline: VoicePipeline? = null
    private var manualStop = false // New flag to prevent auto-restart
    private val vadDetector = VadDetector(context)
    private var lastSpeechTime = System.currentTimeMillis()
    private val SILENCE_TIMEOUT = 5000L // 5 seconds silence timeout

    init {
        addEntity(
            com.nabukey.esphome.entities.ButtonEntity(
                3,
                "Stop Conversation",
                "stop_conversation"
            ) {
                stopConversation(isManual = true)
            }
        )
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        super.start()
        startAudioInput()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioInput() = server.isConnected
        .flatMapLatest { isConnected ->
            if (isConnected) audioInput.start() else emptyFlow()
        }
        .flowOn(Dispatchers.IO)
        .onEach {
            handleAudioResult(audioResult = it)
        }
        .launchIn(scope)

    override suspend fun onDisconnected() {
        super.onDisconnected()
        audioInput.isStreaming = false
        pipeline = null
        timerFinished = false
        player.ttsPlayer.stop()
    }

    override suspend fun getDeviceInfo(): DeviceInfoResponse = deviceInfoResponse {
        val settings = settingsStore.get()
        name = settings.name
        macAddress = settings.macAddress
        voiceAssistantFeatureFlags = VoiceAssistantFeature.VOICE_ASSISTANT.flag or
                VoiceAssistantFeature.API_AUDIO.flag or
                VoiceAssistantFeature.TIMERS.flag or
                VoiceAssistantFeature.ANNOUNCE.flag or
                VoiceAssistantFeature.START_CONVERSATION.flag
    }

    override suspend fun handleMessage(message: MessageLite) {
        when (message) {
            is VoiceAssistantConfigurationRequest -> sendMessage(
                voiceAssistantConfigurationResponse {
                    availableWakeWords += audioInput.availableWakeWords.map {
                        voiceAssistantWakeWord {
                            id = it.id
                            wakeWord = it.wakeWord.wake_word
                            trainedLanguages += it.wakeWord.trained_languages.toList()
                        }
                    }
                    activeWakeWords += audioInput.activeWakeWords.value
                    maxActiveWakeWords = 1
                })

            is VoiceAssistantSetConfiguration -> {
                val activeWakeWords =
                    message.activeWakeWordsList.filter { audioInput.availableWakeWords.any { wakeWord -> wakeWord.id == it } }
                Log.d(TAG, "Setting active wake words: $activeWakeWords")
                if (activeWakeWords.isNotEmpty()) {
                    audioInput.setActiveWakeWords(activeWakeWords)
                }
                val ignoredWakeWords =
                    message.activeWakeWordsList.filter { !activeWakeWords.contains(it) }
                if (ignoredWakeWords.isNotEmpty())
                    Log.w(TAG, "Ignoring wake words: $ignoredWakeWords")
            }

            is VoiceAssistantAnnounceRequest -> handleAnnouncement(
                startConversation = message.startConversation,
                mediaId = message.mediaId,
                preannounceId = message.preannounceMediaId
            )

            is VoiceAssistantEventResponse -> pipeline?.handleEvent(message)

            is VoiceAssistantTimerEventResponse -> handleTimerMessage(message)

            else -> super.handleMessage(message)
        }
    }

    private suspend fun handleTimerMessage(timerEvent: VoiceAssistantTimerEventResponse) {
        Log.d(TAG, "Timer event: ${timerEvent.eventType}")
        when (timerEvent.eventType) {
            VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED -> {
                if (!timerFinished) {
                    timerFinished = true
                    player.duck()
                    player.playTimerFinishedSound {
                        scope.launch { onTimerFinished() }
                    }
                }
            }

            else -> {}
        }
    }

    private fun handleAnnouncement(
        startConversation: Boolean,
        mediaId: String,
        preannounceId: String
    ) {
        _state.value = Responding
        player.duck()
        player.playAnnouncement(preannounceId, mediaId) {
            scope.launch {
                onTtsFinished(startConversation)
            }
        }
    }

    private suspend fun handleAudioResult(audioResult: VoiceSatelliteAudioInput.AudioResult) {
        when (audioResult) {
            is VoiceSatelliteAudioInput.AudioResult.Audio -> {
                pipeline?.processMicAudio(audioResult.audio)
                
                // VAD Check for Silence Timeout
                if (pipeline?.state == Listening) {
                     val bytes = audioResult.audio.toByteArray()
                     val floats = bytesToFloats(bytes)
                     val isSpeech = vadDetector.predict(floats) > 0.5f
                     
                     if (isSpeech) {
                         if (System.currentTimeMillis() - lastSpeechTime > 1000) {
                             Log.d(TAG, "VAD: Speech detected, resetting timer. (Silence was ${System.currentTimeMillis() - lastSpeechTime}ms)")
                         }
                         lastSpeechTime = System.currentTimeMillis()
                     } else {
                         val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                         if (silenceDuration > SILENCE_TIMEOUT && !manualStop) {
                             Log.i(TAG, "Silence timeout detected ($SILENCE_TIMEOUT ms). Stopping conversation.")
                             // Play exit sound for feedback? The user asked for "auto pause".
                             // Let's use stopConversation(isManual=true) so it plays the sound.
                             // This gives clear feedback "I stopped listening".
                             // Also set manualStop implicitly to prevent re-entry, although stopConversation sets it too.
                             stopConversation(isManual = true)
                         }
                     }
                } else {
                    lastSpeechTime = System.currentTimeMillis()
                }
            }

            is VoiceSatelliteAudioInput.AudioResult.WakeDetected ->
                onWakeDetected(audioResult.wakeWord)

            is VoiceSatelliteAudioInput.AudioResult.StopDetected ->
                onStopDetected()
        }
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return FloatArray(shorts.size) { i ->
            shorts[i] / 32768f
        }
    }

    private suspend fun onWakeDetected(wakeWordPhrase: String) {
        // Allow using the wake word to stop the timer
        // TODO: Should the satellite also wake?
        if (timerFinished) {
            stopTimer()
        }
        // Multiple wake detections from the same wake word can be triggered
        // so care needs to be taken to ensure the satellite is only woken once.
        // Currently this is achieved by creating a pipeline in the Listening state
        // on the first wake detection and checking for that here.
        else if (pipeline?.state != Listening) {
            wakeSatellite(wakeWordPhrase)
        }
    }

    private suspend fun onStopDetected() {
        if (timerFinished) {
            stopTimer()
        } else {
            stopSatellite()
        }
    }

    private var lastWakeTime: Long = 0
    private suspend fun wakeSatellite(
        wakeWordPhrase: String = "",
        isContinueConversation: Boolean = false
    ) {
        manualStop = false // Reset manual stop flag
        lastSpeechTime = System.currentTimeMillis() // Reset VAD timer on wake
        lastWakeTime = System.currentTimeMillis()
        Log.d(TAG, "Wake satellite")
        _state.value = Waking
        player.duck()
        pipeline = createPipeline()
        if (!isContinueConversation) {
            // Start streaming audio only after the wake sound has finished
            player.playWakeSound {
                scope.launch { pipeline?.start(wakeWordPhrase) }
            }
        } else {
            pipeline?.start()
        }
    }

    private fun createPipeline() = VoicePipeline(
        player = player.ttsPlayer,
        sendMessage = { sendMessage(it) },
        listeningChanged = { audioInput.isStreaming = it },
        stateChanged = { _state.value = it },
        ended = {
            scope.launch { onTtsFinished(it) }
        }
    )

    private suspend fun stopSatellite() {
        Log.d(TAG, "Stop satellite")
        pipeline = null
        audioInput.isStreaming = false
        player.ttsPlayer.stop()
        _state.value = Connected
        sendMessage(voiceAssistantAnnounceFinished { })
        // lastWakeTime = 0 // Optional: reset wake time if stopped manually? No, keep it.
    }

    fun stopConversation(isManual: Boolean = true) {
        scope.launch {
            if (isManual) {
                manualStop = true
                if (_state.value == Responding) {
                    Log.d(TAG, "Graceful stop requested: waiting for TTS to finish.")
                    return@launch
                }
                // Immediate stop: play sound then stop
                player.playExitSound {
                   scope.launch { stopSatellite() }
                }
                return@launch
            }
            stopSatellite()
        }
    }

    private fun stopTimer() {
        Log.d(TAG, "Stop timer")
        if (timerFinished) {
            timerFinished = false
            player.ttsPlayer.stop()
        }
    }

    private suspend fun onTtsFinished(continueConversation: Boolean) {
        Log.d(TAG, "TTS finished")
        sendMessage(voiceAssistantAnnounceFinished { })
        val forceContinuous = settingsStore.get().forceContinuousConversation
        
        // Prevent rapid loops: if session lasted less than 0.5 seconds, don't force restart
        val sessionDuration = System.currentTimeMillis() - lastWakeTime
        val isRapidFailure = sessionDuration < 500

        Log.d(TAG, "TtsFinished decision: manualStop=$manualStop, continue=$continueConversation, force=$forceContinuous, rapid=$isRapidFailure, duration=$sessionDuration")

        if (!manualStop && (continueConversation || (forceContinuous && !isRapidFailure))) {
            Log.d(TAG, "Continuing conversation (Server request: $continueConversation, Force: $forceContinuous, Duration: ${sessionDuration}ms)")
            // Add delay to prevent capturing TTS echo/reverb as new speech
            delay(500)
            // If forced, we might want to ensure we don't loop forever on silence.
            // But for now, just restart the pipeline.
            wakeSatellite(isContinueConversation = true)
        } else {
            if (manualStop) {
                Log.d(TAG, "Conversation stopped manually.")
                player.playExitSound()
                manualStop = false // Reset flag
            }
            if (isRapidFailure && forceContinuous) {
                 Log.w(TAG, "Continuous conversation aborted due to rapid failure (Duration: ${sessionDuration}ms)")
            }
            player.unDuck()
            _state.value = Connected
        }
    }

    private suspend fun onTimerFinished() {
        delay(1000)
        if (timerFinished) {
            player.playTimerFinishedSound {
                scope.launch { onTimerFinished() }
            }
        } else {
            player.unDuck()
        }
    }

    override fun close() {
        super.close()
        player.close()
    }

    companion object {
        private const val TAG = "VoiceSatellite"
    }
}
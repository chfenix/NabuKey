package com.nabukey.esphome.voicesatellite

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.nabukey.esphome.Connected
import com.nabukey.esphome.EspHomeDevice
import com.nabukey.esphome.EspHomeState
import com.nabukey.esphome.entities.MediaPlayerEntity
import com.nabukey.esphome.entities.SwitchEntity
import com.nabukey.esphome.entities.BinarySensorEntity
import kotlinx.coroutines.flow.Flow
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
import com.nabukey.audio.VadDetector
import com.nabukey.audio.SpeechDetector
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
    var vadThreshold: Float,
    var silenceTimeoutSeconds: Int,

    val audioInput: VoiceSatelliteAudioInput,
    val player: VoiceSatellitePlayer,
    val settingsStore: VoiceSatelliteSettingsStore,
    val presenceFlow: Flow<Boolean>
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
        ) { player.enableWakeSound.set(it) },
        BinarySensorEntity(
            5,
            "Presence Detected",
            "presence_detected",
            presenceFlow
        )
    )
) {
    private var timerFinished = false
    private var pipeline: VoicePipeline? = null
    private var isStopping = false // Prevent re-entrant stop loops
    private var explicitStop = false // Flag to indicate manual/local stop to prevent auto-restart
    private val vadDetector = VadDetector(context)
    private val speechDetector = SpeechDetector(
        threshold = vadThreshold,
        minSpeechDurationMs = 60,
        minSilenceDurationMs = 800
    )

    private var lastActivityTime = System.currentTimeMillis()
    private val LISTENING_TIMEOUT_MS = 5000L

    enum class StopReason {
        Manual,
        Timeout,
        Keyword, // Stop command detected
        Server,
        Error,
        Unknown
    }

    init {
        addEntity(
            com.nabukey.esphome.entities.ButtonEntity(
                3,
                "Stop Conversation",
                "stop_conversation"
            ) {
                stopConversation(StopReason.Manual)
            }
        )
        addEntity(
            com.nabukey.esphome.entities.ButtonEntity(
                4,
                "Reset Conversation",
                "reset_conversation"
            ) {
                Log.i(TAG, "Resetting conversation context.")
                stopConversation(StopReason.Manual)
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
        // Mark as Responding so we don't treat it as a regular conversation wake
        _state.value = Responding
        player.duck()
        Log.d(TAG, "Starting announcement. StartConv=$startConversation, Media=$mediaId")
        player.playAnnouncement(preannounceId, mediaId) {
            scope.launch {
                // For announcements, we treat it as an isolated event.
                // We do NOT want to trigger continuous conversation logic unless explicitly requested
                // via startConversation flag (which is rare for simple announcements).
                onTtsFinished(
                    continueConversation = startConversation, // Respect the flag from HA
                    conversationId = null,
                    hasError = false,
                    ttsPlayed = true,
                    isConversation = false // Verify: Is this existing logic? No, adding param
                )
            }
        }
    }

    private suspend fun handleAudioResult(audioResult: VoiceSatelliteAudioInput.AudioResult) {
        if (isStopping) return // Ignore all audio inputs when stopping

        when (audioResult) {
            is VoiceSatelliteAudioInput.AudioResult.Audio -> {
                pipeline?.processMicAudio(audioResult.audio)

                if (pipeline?.state == Listening) {
                     val bytes = audioResult.audio.toByteArray()
                     val floats = bytesToFloats(bytes)
                     val probability = vadDetector.predict(floats)

                     when (speechDetector.process(probability)) {
                         SpeechDetector.Action.START -> {
                             Log.d(TAG, "Local VAD: Speech Started")
                             lastActivityTime = System.currentTimeMillis()
                         }
                         SpeechDetector.Action.END -> {
                             Log.d(TAG, "Local VAD: Speech Ended")
                             lastActivityTime = System.currentTimeMillis()
                         }
                         SpeechDetector.Action.NONE -> {
                             if (!speechDetector.hasDetectedSpeech) {
                                  val timeout = if (silenceTimeoutSeconds > 0) silenceTimeoutSeconds * 1000L else LISTENING_TIMEOUT_MS
                                  if (System.currentTimeMillis() - lastActivityTime > timeout) {
                                      Log.i(TAG, "Listening Timeout ($timeout ms) - No speech detected.")
                                      stopConversation(StopReason.Timeout)
                                  }
                             } else {
                                  // Speech has been detected in this session, so we don't timeout rapidly.
                                  // Wait for Speech Action.END or HA to take over.
                             }
                         }
                     }
                } else {
                    lastActivityTime = System.currentTimeMillis()
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
        if (System.currentTimeMillis() - lastStopTime < 2000) {
            return
        }

        if (_state.value != Connected) {
             return
        }

        Log.d(TAG, "onWakeDetected: $wakeWordPhrase")

        if (timerFinished) {
            stopTimer()
        }
        else if (pipeline?.state != Listening) {
            wakeSatellite(wakeWordPhrase)
        }
    }

    private suspend fun onStopDetected() {
        if (timerFinished) {
            stopTimer()
        } else {
            // Only allow stop words to interrupt an active session (Listening/Responding/etc)
            // If we are Connected (Idle), we ignore "Stop" commands to prevent phantom triggers from ambient noise.
            if (_state.value != Connected) {
                Log.d(TAG, "Stop detected. Requesting stop.")
                stopConversation(StopReason.Keyword)
            } else {
                Log.d(TAG, "Ignored Stop keyword while in Connected state.")
            }
        }
    }


    private var lastWakeTime: Long = 0
    private var lastStopTime: Long = 0

    private suspend fun wakeSatellite(
        wakeWordPhrase: String = "",
        isContinueConversation: Boolean = false
    ) {
        explicitStop = false // Reset explicit stop flag
        isStopping = false // Reset stopping flag

        Log.d(TAG, "wakeSatellite: isContinueConversation=$isContinueConversation")

        lastActivityTime = System.currentTimeMillis()
        lastWakeTime = System.currentTimeMillis()
        speechDetector.reset()

        Log.d(TAG, "Wake satellite")
        _state.value = Waking
        player.duck()
        pipeline = createPipeline()
        if (!isContinueConversation) {
            player.playWakeSound {
                scope.launch { pipeline?.start(wakeWordPhrase) }
            }
        } else {
            pipeline?.start()
        }
    }

    private fun createPipeline() = VoicePipeline(
        scope = scope,
        player = player.ttsPlayer,
        sendMessage = { sendMessage(it) },
        listeningChanged = {
            audioInput.isStreaming = it
            if (it) {
                 speechDetector.reset()
                 lastActivityTime = System.currentTimeMillis()
            }
        },
        stateChanged = { _state.value = it },
        ended = { continueConversation, conversationId, hasError, ttsPlayed ->
            scope.launch {
                onTtsFinished(
                    continueConversation,
                    conversationId,
                    hasError,
                    ttsPlayed,
                    isConversation = true
                )
            }
        },
        onSpeechDetected = {
            Log.d(TAG, "Server VAD detected speech. Resetting local timer.")
            lastActivityTime = System.currentTimeMillis()
        }
    )

    private suspend fun stopSatellite() {
        Log.d(TAG, "Stop satellite")
        isStopping = false // Reset here
        lastStopTime = System.currentTimeMillis()
        pipeline = null
        audioInput.isStreaming = false
        player.ttsPlayer.stop()
        _state.value = Connected
        sendMessage(voiceAssistantAnnounceFinished { })
        speechDetector.reset()
    }

    fun stopConversation(reason: StopReason) {
        Log.d(TAG, "stopConversation request. Reason: $reason, State: ${_state.value}, isStopping: $isStopping")

        if (isStopping) {
            Log.d(TAG, "Already stopping, ignoring request")
            return
        }

        // CRITICAL GUARD: If we are already Idle/Connected, and this is a TIMEOUT, ignore it.
        // This prevents phantom timeouts from the VAD loop if it's running when it shouldn't be.
        if (_state.value == Connected && reason == StopReason.Timeout) {
            Log.w(TAG, "Ignoring Timeout while in Connected state.")
            return
        }

        isStopping = true // Lock immediately
        explicitStop = true // Set flag to prevent restart loops

        scope.launch {
            // Determine if we should play the exit sound
            // We usually play it for Manual stops, Keyword stops, or Timeouts (to indicate we gave up listening)
            val shouldPlayExitSound = when (reason) {
                StopReason.Manual, StopReason.Keyword, StopReason.Timeout -> true
                else -> false
            }

            if (shouldPlayExitSound) {
                if (_state.value == Responding) {
                    Log.d(TAG, "Graceful stop requested during Response: waiting for TTS to finish.")
                    isStopping = false // Release lock if we are just waiting
                    // Do NOT reset explicitStop here, we still want to stop after TTS
                    return@launch
                }
                player.playExitSound {
                   scope.launch { stopSatellite() }
                }
            } else {
                stopSatellite()
            }
        }
    }

    private fun stopTimer() {
        Log.d(TAG, "Stop timer")
        if (timerFinished) {
            timerFinished = false
            player.ttsPlayer.stop()
        }
    }

    private suspend fun onTtsFinished(
        continueConversation: Boolean,
        conversationId: String?,
        hasError: Boolean,
        ttsPlayed: Boolean,
        isConversation: Boolean
    ) {
        Log.d(TAG, "TTS finished (continue=$continueConversation, conversationId=$conversationId, error=$hasError, played=$ttsPlayed, isConv=$isConversation)")
        // Removed persistent ID logic as requested
        sendMessage(voiceAssistantAnnounceFinished { })

        
        // If explicitly stopped (e.g. manual button or timeout), do NOT continue
        if (explicitStop) {
             Log.d(TAG, "Stopping conversation: Explicit stop was requested.")
             stopSatellite()
             return
        }

        // If it's just an announcement (not a conversation exchange), we usually just stop,
        // unless HA explicitly told us to start a conversation in the announce request.
        if (!isConversation && !continueConversation) {
            Log.d(TAG, "Announcement finished, returning to idle.")
            stopSatellite()
            return
        }

        val forceContinuous = settingsStore.get().forceContinuousConversation

        if (hasError) {
             Log.w(TAG, "Stopping conversation due to error.")
             stopSatellite()
             return
        }

        if (isConversation && !ttsPlayed) {
             Log.w(TAG, "Stopping conversation: No TTS playback occurred during this session.")
             stopSatellite()
             return
        }

        val sessionDuration = System.currentTimeMillis() - lastWakeTime
        val isRapidFailure = sessionDuration < 500

        // Only continue if HA request OR (force active AND not a rapid failure)
        // We relax the speechDetector requirement so that if 'forceContinuous' is ON,
        // we ALWAYS listen again, letting the Silence Timeout handle the stop if the user says nothing.
        val shouldLoop = (continueConversation || (isConversation && forceContinuous && !isRapidFailure))


        if (shouldLoop) {
            Log.d(TAG, "Continuing conversation (Server request: $continueConversation, Force: $forceContinuous, Duration: ${sessionDuration}ms)")
            delay(500)
            wakeSatellite(isContinueConversation = true)
        } else {
            if (isRapidFailure && forceContinuous) {
                 Log.w(TAG, "Continuous conversation aborted due to rapid failure")
            }
            // Normal end of conversation
            stopSatellite()
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
        speechDetector.reset()
        vadDetector.close()
    }

    companion object {
        private const val TAG = "VoiceSatellite"
    }
}

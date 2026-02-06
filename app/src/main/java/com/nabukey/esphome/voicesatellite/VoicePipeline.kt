package com.nabukey.esphome.voicesatellite

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.nabukey.esphome.EspHomeState
import com.nabukey.players.AudioPlayer
import com.example.esphomeproto.api.VoiceAssistantEvent
import com.example.esphomeproto.api.VoiceAssistantEventResponse
import com.example.esphomeproto.api.voiceAssistantAudio
import com.example.esphomeproto.api.voiceAssistantRequest
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracks the state of a voice pipeline run.
 */
@OptIn(UnstableApi::class)
class VoicePipeline(
    private val scope: CoroutineScope,
    private val player: AudioPlayer,
    private val sendMessage: suspend (MessageLite) -> Unit,
    private val listeningChanged: (listening: Boolean) -> Unit,
    private val stateChanged: (state: EspHomeState) -> Unit,
    private val ended: (continueConversation: Boolean, conversationId: String?, hasError: Boolean, ttsPlayed: Boolean) -> Unit,
    private val onSpeechDetected: () -> Unit = {}
) {
    private var continueConversation = false
    private var conversationId: String? = null
    private var hasError = false
    private val micAudioBuffer = ArrayDeque<ByteString>()
    private var isRunning = false
    private var ttsStreamUrl: String? = null
    private var ttsPlayed = false
    private var ttsTimeoutJob: Job? = null

    private var _state: EspHomeState = Listening
    val state get() = _state

    /**
     * Requests that a new pipeline run be started.
     * Calls the stateChanged and listeningChanged callbacks with the initial state.
     */
    suspend fun start(wakeWordPhrase: String = "") {
        stateChanged(state)
        listeningChanged(state == Listening)
        sendMessage(voiceAssistantRequest {
            start = true
            this.wakeWordPhrase = wakeWordPhrase
        })
    }

    /**
     * Handles a new voice assistant event, updating state and TTS playback as required..
     */
    fun handleEvent(voiceEvent: VoiceAssistantEventResponse) {
        val dataListString = voiceEvent.dataList.joinToString { "${it.name}=${it.value}" }
        Log.d(TAG, "Event: ${voiceEvent.eventType}, Data: $dataListString")

        when (voiceEvent.eventType) {
            VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START -> {
                Log.d(TAG, "Pipeline run started")
                // From this point microphone audio can be sent
                isRunning = true
                // Prepare TTS playback
                ttsStreamUrl = voiceEvent.dataList.firstOrNull { data -> data.name == "url" }?.value
                // Init the player early so it gains system audio focus, this ducks any
                // background audio whilst the microphone is capturing voice
                player.init()
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_STT_START -> {
                Log.d(TAG, "Server STT started")
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_STT_VAD_START -> {
                onSpeechDetected()
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_STT_VAD_END, VoiceAssistantEvent.VOICE_ASSISTANT_STT_END -> {
                // Received after the user has finished speaking
                updateState(Processing)
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_START -> { 
                // Intent processing started
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_PROGRESS -> {
                // If the pipeline supports TTS streaming it is started here
                if (voiceEvent.dataList.firstOrNull { data -> data.name == "tts_start_streaming" }?.value == "1") {
                    ttsStreamUrl?.let {
                        ttsPlayed = true
                        startTtsTimeout()
                        player.play(it, ::fireEnded)
                    }
                }
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_END -> {
                // Get whether a further response is required from the user and
                // therefore a new pipeline should be started when this one ends
                if (voiceEvent.dataList.firstOrNull { data -> data.name == "continue_conversation" }?.value == "1") {
                    continueConversation = true
                }
                // Capture conversation ID for context tracking
                voiceEvent.dataList.firstOrNull { it.name == "conversation_id" }?.value?.let {
                    conversationId = it
                }
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_TTS_START -> {
                // TTS response is being generated
                updateState(Responding)
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_TTS_END -> {
                // If the pipeline doesn't support TTS streaming, play the complete TTS response now
                if (!ttsPlayed) {
                    voiceEvent.dataList.firstOrNull { data -> data.name == "url" }?.value?.let {
                        ttsPlayed = true
                        startTtsTimeout()
                        player.play(it, ::fireEnded)
                    }
                }
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_ERROR -> {
                Log.e(TAG, "Voice assistant error: ${voiceEvent.dataList.firstOrNull { it.name == "message" }?.value}")
                hasError = true
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END -> {
                // Ignore RUN_END if the pipeline hasn't started (e.g. leftover from previous session)
                if (!isRunning) {
                    Log.w(TAG, "Ignoring RUN_END event for pipeline that hasn't started.")
                    return
                }
                
                // If playback was never started but we have a URL from RUN_START,
                // play it now before ending.
                if (!ttsPlayed && ttsStreamUrl != null) {
                    Log.d(TAG, "Playing fallback TTS URL from RUN_START: $ttsStreamUrl")
                    ttsPlayed = true
                    startTtsTimeout()
                    player.play(ttsStreamUrl!!, ::fireEnded)
                } else if (!ttsPlayed) {
                    // No playback performed and no fallback URL, end normally
                    fireEnded()
                }
                // If ttsPlayed is true, fireEnded will be called when playback completes
            }

            else -> {
                Log.d(TAG, "Unhandled voice assistant event: ${voiceEvent.eventType}")
            }
        }
    }

    private var isEnded = false

    private fun startTtsTimeout() {
        ttsTimeoutJob?.cancel()
        ttsTimeoutJob = scope.launch {
            delay(300000) // 300 seconds (5 minutes) safety timeout
            Log.w(TAG, "TTS Playback timed out after 300s. Forcing completion.")
            fireEnded()
        }
    }


    private fun fireEnded() {
        ttsTimeoutJob?.cancel()
        if (isEnded) return
        isEnded = true
        ended(continueConversation, conversationId, hasError, ttsPlayed)
    }

    /**
     * Updates the state of the pipeline and calls the listeningChanged callback
     * if the state has changed from or to Listening.
     */
    private fun updateState(state: EspHomeState) {
        if (state != _state) {
            val oldState = _state
            _state = state
            stateChanged(state)
            // Started listening
            if (state == Listening)
                listeningChanged(true)
            // Stopped listening
            else if (oldState == Listening)
                listeningChanged(false)
        }
    }

    /**
     * If the pipeline is not in the Listening state, drops the microphone audio.
     * Else either buffers the audio internally if the pipeline is not yet ready,
     * or sends any buffered audio and the new audio.
     */
    suspend fun processMicAudio(audio: ByteString) {
        if (_state != Listening)
            return
        if (!isRunning) {
            micAudioBuffer.add(audio)
            if (micAudioBuffer.size % 20 == 0) Log.d(TAG, "Buffering mic audio, current size: ${micAudioBuffer.size}")
        } else {
            while (micAudioBuffer.isNotEmpty()) {
                sendMessage(voiceAssistantAudio { data = micAudioBuffer.removeFirst() })
            }
            sendMessage(voiceAssistantAudio { data = audio })
        }
    }

    companion object {
        private const val TAG = "VoicePipeline"
    }
}

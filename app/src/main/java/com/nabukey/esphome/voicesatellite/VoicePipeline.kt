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
import com.nabukey.stt.LocalSTT
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tracks the state of a voice pipeline run.
 */
@OptIn(UnstableApi::class)
class VoicePipeline(
    private val player: AudioPlayer,
    private val localSTT: LocalSTT?,
    private val sendMessage: suspend (MessageLite) -> Unit,
    private val listeningChanged: (listening: Boolean) -> Unit,
    private val stateChanged: (state: EspHomeState) -> Unit,
    private val ended: (continueConversation: Boolean) -> Unit
) {
    private var continueConversation = false
    private val micAudioBuffer = ArrayDeque<ByteString>()
    private var isRunning = false
    private var ttsStreamUrl: String? = null
    private var ttsPlayed = false
    private val localSttAudioBuffer = mutableListOf<ByteString>()

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
        when (voiceEvent.eventType) {
            VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START -> {
                // From this point microphone audio can be sent
                isRunning = true
                // Prepare TTS playback
                ttsStreamUrl = voiceEvent.dataList.firstOrNull { data -> data.name == "url" }?.value
                // Init the player early so it gains system audio focus, this ducks any
                // background audio whilst the microphone is capturing voice
                player.init()
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_STT_VAD_END, VoiceAssistantEvent.VOICE_ASSISTANT_STT_END -> {
                // Received after the user has finished speaking
                updateState(Processing)
                
                // If we are using local STT, now is the time to transcribe and send text
                if (localSTT != null && localSttAudioBuffer.isNotEmpty()) {
                    val capturedAudio = localSttAudioBuffer.toList()
                    localSttAudioBuffer.clear()
                    
                    // Launch transcription
                    kotlinx.coroutines.GlobalScope.launch {
                        performLocalTranscription(capturedAudio)
                    }
                }
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_PROGRESS -> {
                // If the pipeline supports TTS streaming it is started here
                if (voiceEvent.dataList.firstOrNull { data -> data.name == "tts_start_streaming" }?.value == "1") {
                    ttsStreamUrl?.let {
                        ttsPlayed = true
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
                        player.play(it, ::fireEnded)
                    }
                }
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END -> {
                // If playback was never started, fire the ended callback,
                // otherwise it will/was fired when playback finished
                if (!ttsPlayed)
                    fireEnded()
            }

            else -> {
                Log.d(TAG, "Unhandled voice assistant event: ${voiceEvent.eventType}")
            }
        }
    }

    private fun fireEnded() {
        ended(continueConversation)
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
        
        if (localSTT != null) {
            // In local STT mode, we buffer everything to recognize at the end of VAD
            localSttAudioBuffer.add(audio)
            // Optional: for better UX, we could still progress VAD on server, 
            // but here we just wait for VAD_END event from HA (which still listens to the stream if we send it)
            // Wait, if we DON'T send audio, HA won't know when VAD ends.
            // So for now, we STILL send audio so HA can do VAD, effectively doing Dual-STT,
            // BUT we will override the result with our local text.
        }

        if (!isRunning) {
            micAudioBuffer.add(audio)
            Log.d(TAG, "Buffering mic audio, current size: ${micAudioBuffer.size}")
        } else {
            while (micAudioBuffer.isNotEmpty()) {
                sendMessage(voiceAssistantAudio { data = micAudioBuffer.removeFirst() })
            }
            sendMessage(voiceAssistantAudio { data = audio })
        }
    }

    private suspend fun performLocalTranscription(audioChunks: List<ByteString>) {
        val stt = localSTT ?: return
        Log.d(TAG, "Local STT: Starting transcription of ${audioChunks.size} chunks")
        
        // Flatten chunks into a single FloatArray
        val totalBytes = audioChunks.sumOf { it.size() }
        val byteBuffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (chunk in audioChunks) {
            byteBuffer.put(chunk.asReadOnlyByteBuffer())
        }
        byteBuffer.flip()
        
        val shortBuffer = byteBuffer.asShortBuffer()
        val floatArray = FloatArray(shortBuffer.limit())
        for (i in 0 until shortBuffer.limit()) {
            floatArray[i] = shortBuffer.get(i).toFloat() / 32768.0f
        }
        
        val result = stt.transcribe(floatArray)
        if (result.text.isNotBlank()) {
            Log.e(TAG, "Local STT Recognized: ${result.text}")
            // Send the text to HA to override/provide the intent
            sendMessage(voiceAssistantRequest {
                conversationText = result.text
            })
        } else {
            Log.w(TAG, "Local STT result was empty")
        }
    }

    companion object {
        private const val TAG = "VoicePipeline"
    }
}
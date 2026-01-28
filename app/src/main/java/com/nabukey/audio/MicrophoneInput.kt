package com.nabukey.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.nio.ByteBuffer

class MicrophoneInput(
    val audioSource: Int = DEFAULT_AUDIO_SOURCE, // Reverted to default (VOICE_RECOGNITION)
    val sampleRateInHz: Int = DEFAULT_SAMPLE_RATE_IN_HZ,
    val channelConfig: Int = DEFAULT_CHANNEL_CONFIG,
    val audioFormat: Int = DEFAULT_AUDIO_FORMAT
) : AutoCloseable {
    private val bufferSize =
        AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
    private val buffer = ByteBuffer.allocateDirect(bufferSize)
    private var audioRecord: AudioRecord? = null
    private var aec: android.media.audiofx.AcousticEchoCanceler? = null
    val isRecording get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (audioRecord == null) {
            audioRecord = createAudioRecord()
            /*
            // Disable AEC for now as it causes issues with VOICE_RECOGNITION on some devices
            // and relying on FORCE_COMMUNICATION caused a loop.
            try {
                if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                    aec = android.media.audiofx.AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
                    aec?.enabled = true
                    Log.d(TAG, "AcousticEchoCanceler enabled: ${aec?.enabled}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable AEC", e)
            }
            */
        }
        if (!isRecording) {
            Log.d(TAG, "Starting microphone")
            audioRecord?.startRecording()
        } else {
            Log.w(TAG, "Microphone already started")
        }
    }

    fun read(): ByteBuffer {
        val audioRecord = this.audioRecord ?: error("Microphone not started")
        buffer.clear()
        val read = audioRecord.read(buffer, bufferSize)
        check(read >= 0) {
            "error reading audio, read: $read"
        }
        buffer.limit(read)
        return buffer
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createAudioRecord(): AudioRecord {
        val audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            bufferSize * 2
        )
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            "Failed to initialize AudioRecord"
        }
        return audioRecord
    }

    override fun close() {
        aec?.release()
        aec = null
        audioRecord?.let {
            if (isRecording) {
                it.stop()
            }
            it.release()
            audioRecord = null
        }
    }

    companion object {
        const val TAG = "MicrophoneInput"
        const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val DEFAULT_SAMPLE_RATE_IN_HZ = 16000
        const val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
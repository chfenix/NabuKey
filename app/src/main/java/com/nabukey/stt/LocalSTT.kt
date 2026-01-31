package com.nabukey.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.nabukey.settings.VoiceSatelliteSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSTT @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: VoiceSatelliteSettingsStore
) {
    private var recognizer: OfflineRecognizer? = null
    private var currentModelPath: String? = null

    val isInitialized: Boolean
        get() = recognizer != null

    suspend fun initialize(): Boolean {
        // 1. Resolve Model Path
        // Priority: User Settings > Default External Dir
        val configuredPath = settingsStore.sttModelPath.get()
        // Default: /sdcard/Android/data/com.nabukey/files/models
        val externalDir: String? = context.getExternalFilesDir(null)?.absolutePath
        val defaultPath: String? = externalDir?.let { "$it/models" }

        val basePath: String? = if (configuredPath.isNotEmpty()) {
            configuredPath
        } else {
            defaultPath
        }

        if (basePath == null) {
            Log.e(TAG, "Could not resolve STT model path.")
            return false
        }

        // Avoid re-initialization if path hasn't changed
        if (basePath == currentModelPath && recognizer != null) {
            return true
        }

        // 2. Locate Model Files
        val modelFile = File(basePath, "model.int8.onnx")
        val tokensFile = File(basePath, "tokens.txt")

        if (!modelFile.exists() || !tokensFile.exists()) {
            Log.e(TAG, "Critical: STT models not found at: $basePath")
            Log.e(TAG, "Please follow project_rules.md: Download SenseVoice-Small and place in local_models/, then run installDebug.")
            return false
        }

        Log.e(TAG, "==========================================")
        Log.e(TAG, "SENSEVOICE STT: STARTING INITIALIZATION...")
        Log.e(TAG, "Model Path: $basePath")
        Log.e(TAG, "==========================================")

        // 3. Configure & Initialize Engine
        return try {
            val modelConfig = OfflineModelConfig.builder()
                .setSenseVoice(
                    OfflineSenseVoiceModelConfig.builder()
                        .setModel(modelFile.absolutePath)
                        .setInverseTextNormalization(true)
                        .build()
                )
                .setTokens(tokensFile.absolutePath)
                .setNumThreads(1)
                .setDebug(true)
                .setProvider("cpu")
                .build()

            val config = OfflineRecognizerConfig.builder()
                .setFeatureConfig(
                    FeatureConfig.builder()
                        .setSampleRate(16000)
                        .setFeatureDim(80)
                        .build()
                )
                .setOfflineModelConfig(modelConfig)
                .build()

            recognizer = OfflineRecognizer(config)
            currentModelPath = basePath
            Log.e(TAG, "==========================================")
            Log.e(TAG, "SENSEVOICE STT: INITIALIZED SUCCESSFULLY!")
            Log.e(TAG, "==========================================")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sherpa-ONNX Engine", e)
            recognizer = null
            false
        }
    }

    /**
     * Transcribes the given audio samples.
     * @param samples Audio samples (float array, normalized -1.0 to 1.0)
     * @param sampleRate Sample rate, default 16000Hz
     */
    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): STTResult {
        val safeRecognizer = recognizer
        if (safeRecognizer == null) {
            Log.w(TAG, "Transcribe called but recognizer is not initialized.")
            return STTResult(text = "")
        }

        return try {
            val stream = safeRecognizer.createStream()
            stream.acceptWaveform(samples, sampleRate)
            safeRecognizer.decode(stream)
            val result = safeRecognizer.getResult(stream)
            stream.release()

            // TODO: Parse emotion if supported by SenseVoice result in future binding versions
            // For now, raw text is returned. SenseVoice might include tags if configured.
            val text = result.text
            
            // Basic log for debugging
            if (text.isNotEmpty()) {
                Log.d(TAG, "STT recognized: $text")
            }
            
            STTResult(text = text)
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed during decode", e)
            STTResult(text = "")
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        currentModelPath = null
    }

    companion object {
        private const val TAG = "LocalSTT"
    }
}

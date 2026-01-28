package com.nabukey.audio

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Collections

class VadDetector(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // VAD States for RNN (2, 1, 64)
    private var h: FloatArray = FloatArray(2 * 1 * 64)
    private var c: FloatArray = FloatArray(2 * 1 * 64)
    private val sr: LongArray = longArrayOf(16000)

    init {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("silero_vad.onnx").use { it.readBytes() }
            ortSession = ortEnv?.createSession(modelBytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Process audio chunk and return speech probability (0.0 - 1.0)
     * @param audioData Float array of audio samples. 
     *                  Silero VAD supports chunks of 512, 1024, 1536 samples for 16kHz.
     */
    fun predict(audioData: FloatArray): Float {
        if (ortSession == null || ortEnv == null) return 0f

        try {
            // 1. Prepare Inputs
            // Input tensor: [1, N]
            val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audioData), longArrayOf(1, audioData.size.toLong()))
            
            // State tensors: [2, 1, 64]
            val hTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(h), longArrayOf(2, 1, 64))
            val cTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(c), longArrayOf(2, 1, 64))
            
            // SR tensor: [1] (scalar in some versions, but usually tensor of size 1)
            val srTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(sr), longArrayOf(1))

            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "h" to hTensor,
                "c" to cTensor
            )

            // 2. Run Inference
            val results = ortSession?.run(inputs)
            
            // 3. Process Outputs
            // output: [1, 1] probability
            // hn: [2, 1, 64]
            // cn: [2, 1, 64]
            
            val outputTensor = results?.get("output")?.get() as OnnxTensor
            val hnTensor = results.get("hn").get() as OnnxTensor
            val cnTensor = results.get("cn").get() as OnnxTensor

            val probability = outputTensor.floatBuffer.get(0)
            
            // Update states
            hnTensor.floatBuffer.get(h)
            cnTensor.floatBuffer.get(c)

            // Close resources
            inputTensor.close()
            hTensor.close()
            cTensor.close()
            srTensor.close()
            
            // We should not close output tensors if we want to use them? 
            // Actually we extracted data (floatBuffer.get(h)), so we can close them.
            // But we must NOT close 'results' before using tensors if they are views?
            // OnnxRuntime Java: The tensors returned are owned by the Result usually?
            // Wait, if we close 'results' later, do we need to close individual tensors?
            // "The Result object contains the output values. Closing the Result object closes all the output values."
            // So we should just close 'results' at the end.
            // But we cast them to OnnxTensor and used them.
            
            results.close() 


            return probability

        } catch (e: Exception) {
            e.printStackTrace()
            return 0f
        }
    }

    fun reset() {
        h = FloatArray(2 * 1 * 64)
        c = FloatArray(2 * 1 * 64)
    }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
    }
}

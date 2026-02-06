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

    // VAD States
    // v4: h(2,1,64), c(2,1,64)
    // v5: state(2,1,128)
    private var h: FloatArray = FloatArray(2 * 1 * 64)
    private var c: FloatArray = FloatArray(2 * 1 * 64)
    private var state: FloatArray = FloatArray(2 * 1 * 128)
    
    private val sr: LongArray = longArrayOf(16000)
    
    private var isV5 = false
    private var hasSR = true
    
    // Dynamic output names
    private var probOutputName = "output"
    private var stateOutputName = "state"
    private var hnOutputName = "hn"
    private var cnOutputName = "cn"

    init {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("silero_vad.onnx").use { it.readBytes() }
            ortSession = ortEnv?.createSession(modelBytes)
            
            ortSession?.let { session ->
                android.util.Log.i("VadDetector", "Model Loaded. Inputs: ${session.inputNames}, Count: ${session.numInputs}")
                val inputNames = session.inputNames
                isV5 = inputNames.contains("state")
                hasSR = inputNames.contains("sr")
                
                android.util.Log.i("VadDetector", "Outputs: ${session.outputNames}")
                val outNames = session.outputNames
                if (outNames.isNotEmpty()) {
                    // Assume first output is always probability
                    probOutputName = outNames.first()
                    
                    if (isV5) {
                        stateOutputName = outNames.find { it.contains("state") } ?: outNames.elementAtOrNull(1) ?: "state"
                    } else {
                        hnOutputName = outNames.find { it.contains("h") } ?: "hn"
                        cnOutputName = outNames.find { it.contains("c") } ?: "cn"
                    }
                }
                android.util.Log.i("VadDetector", "Detected Format: V5=$isV5. outputs: prob=$probOutputName, state=$stateOutputName")
            }
        } catch (e: Exception) {
            android.util.Log.e("VadDetector", "Initialization failed", e)
        }
    }

    /**
     * Process audio chunk and return speech probability (0.0 - 1.0)
     * @param audioData Float array of audio samples. 
     *                  Silero VAD supports chunks of 512, 1024, 1536 samples for 16kHz.
     */
    fun predict(audioData: FloatArray): Float {
        if (ortSession == null) return 0f

        try {
            // 1. Prepare Inputs
            val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audioData), longArrayOf(1, audioData.size.toLong()))
            val inputs = mutableMapOf<String, OnnxTensor>()
            inputs["input"] = inputTensor

            if (hasSR) {
                val srTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(sr), longArrayOf(1))
                inputs["sr"] = srTensor
            }

            if (isV5) {
                // v5 uses a single 'state' tensor
                val stateTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(state), longArrayOf(2, 1, 128))
                inputs["state"] = stateTensor
            } else {
                // v4 uses split 'h' and 'c' tensors
                val hTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(h), longArrayOf(2, 1, 64))
                val cTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(c), longArrayOf(2, 1, 64))
                inputs["h"] = hTensor
                inputs["c"] = cTensor
            }

            // 2. Run Inference
            val results = ortSession?.run(inputs)
            
            // 3. Process Outputs
            val outputTensor = results?.get(probOutputName)?.get() as OnnxTensor
            val probability = outputTensor.floatBuffer.get(0)
            
            // Update states
            if (isV5) {
                val stateOut = results.get(stateOutputName).get() as OnnxTensor
                stateOut.floatBuffer.get(state)
            } else {
                val hnTensor = results.get(hnOutputName).get() as OnnxTensor
                val cnTensor = results.get(cnOutputName).get() as OnnxTensor
                hnTensor.floatBuffer.get(h)
                cnTensor.floatBuffer.get(c)
            }

            // Close resources
            inputs.values.forEach { it.close() }
            results.close() 

            return probability

        } catch (e: Exception) {
            android.util.Log.e("VadDetector", "Prediction failed", e)
            return 0f
        }
    }

    fun reset() {
        h = FloatArray(2 * 1 * 64)
        c = FloatArray(2 * 1 * 64)
        state = FloatArray(2 * 1 * 128)
    }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
    }
}

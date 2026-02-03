package com.nabukey.sensors

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

class PresenceDetector(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val _isPresent = MutableStateFlow(false)
    val isPresent = _isPresent.asStateFlow()
    private var frameCount = 0
    private var lastAnalysisTime = 0L

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    fun start() {
        Log.d(TAG, "Starting PresenceDetector")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                // Default to front camera
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
                Log.d(TAG, "Camera bound to lifecycle")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        // Log every 50 frames to avoid spam, but at least once at start to confirm frames are arriving
        if (frameCount++ % 50 == 0) Log.d(TAG, "Frame received. Counter: $frameCount")

        val currentTime = System.currentTimeMillis()
        // Limit detection to ~2 times per second (every 500ms) to reduce CPU/GPU usage and heat
        if (currentTime - lastAnalysisTime < 500) {
            imageProxy.close()
            return
        }
        lastAnalysisTime = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    val detected = faces.isNotEmpty()
                    if (_isPresent.value != detected) {
                        Log.d(TAG, "Presence changed: $detected")
                        _isPresent.value = detected
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
    
    fun stop() {
        Log.d(TAG, "Stopping PresenceDetector")
        // Just shutdown executor, camera unbinds with lifecycle
        // But if we want to explicitly unbind:
        // ProcessCameraProvider.getInstance(context).get().unbindAll() 
        // (but async)
    }

    companion object {
        private const val TAG = "PresenceDetector"
    }
}

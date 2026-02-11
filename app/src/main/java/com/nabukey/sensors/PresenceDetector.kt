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
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.concurrent.Executors

class PresenceDetector(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val _isPresent = MutableStateFlow(false)
    val isPresent = _isPresent.asStateFlow()
    private var frameCount = 0
    private var lastAnalysisTime = 0L
    private var lastPresenceTrueTime = 0L
    private var gracePeriodMs = 3000L
    private var minFaceRatio = 0.15f
    private var debugLogging = true
    private var workHoursStart = 9
    private var workMinutesStart = 0
    private var workHoursEnd = 18
    private var workMinutesEnd = 0
    private var workDaysOnly = true
    private var haWorkdayState: Boolean? = null
    private var haWorkdayStateEpochDay: Long? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    fun updateConfiguration(
        minFaceRatio: Float, 
        gracePeriodMs: Long, 
        debugLogging: Boolean,
        workHoursStart: Int,
        workMinutesStart: Int,
        workHoursEnd: Int,
        workMinutesEnd: Int,
        workDaysOnly: Boolean
    ) {
        this.minFaceRatio = minFaceRatio
        this.gracePeriodMs = gracePeriodMs
        this.debugLogging = debugLogging
        this.workHoursStart = workHoursStart
        this.workMinutesStart = workMinutesStart
        this.workHoursEnd = workHoursEnd
        this.workMinutesEnd = workMinutesEnd
        this.workDaysOnly = workDaysOnly
        if (debugLogging) {
            val startTime = String.format("%02d:%02d", workHoursStart, workMinutesStart)
            val endTime = String.format("%02d:%02d", workHoursEnd, workMinutesEnd)
            Log.d(TAG, "Config: faceRatio=$minFaceRatio, grace=$gracePeriodMs, debug=$debugLogging, hours=$startTime-$endTime, workDays=$workDaysOnly")
        }
    }

    fun updateHaWorkdayState(isWorkday: Boolean?) {
        haWorkdayState = isWorkday
        haWorkdayStateEpochDay = if (isWorkday == null) null else LocalDate.now().toEpochDay()
        if (debugLogging) {
            Log.d(TAG, "HA workday updated: state=$haWorkdayState, day=$haWorkdayStateEpochDay")
        }
    }

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
        if (frameCount++ % 50 == 0 && debugLogging) Log.d(TAG, "Frame received. Counter: $frameCount")

        val currentTime = System.currentTimeMillis()
        // Limit detection to ~1 time per second (every 1000ms) to reduce CPU/GPU usage and heat
        if (currentTime - lastAnalysisTime < 1000) {
            imageProxy.close()
            return
        }
        
        // Time Restriction Check
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // Sun=1, Mon=2...
        
        val todayEpochDay = LocalDate.now().toEpochDay()
        val hasTodayHaWorkday = haWorkdayState != null && haWorkdayStateEpochDay == todayEpochDay
        if (workDaysOnly) {
            if (hasTodayHaWorkday) {
                if (haWorkdayState == false) {
                    if (debugLogging && frameCount % 60 == 0) {
                        Log.d(TAG, "Skipping presence: HA workday=false for today")
                    }
                    imageProxy.close()
                    return
                }
            } else {
                val isWeekend = dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY
                if (isWeekend) {
                    if (debugLogging && frameCount % 60 == 0) Log.d(TAG, "Skipping presence: Weekend restriction")
                    imageProxy.close()
                    return
                }
            }
        }
        
        val currentTimeMinutes = hour * 60 + minute
        val startTimeMinutes = workHoursStart * 60 + workMinutesStart
        val endTimeMinutes = workHoursEnd * 60 + workMinutesEnd
        if (currentTimeMinutes < startTimeMinutes || currentTimeMinutes >= endTimeMinutes) {
             if (debugLogging && frameCount % 60 == 0) {
                 val current = String.format("%02d:%02d", hour, minute)
                 val start = String.format("%02d:%02d", workHoursStart, workMinutesStart)
                 val end = String.format("%02d:%02d", workHoursEnd, workMinutesEnd)
                 Log.d(TAG, "Skipping presence: Time ($current) outside $start-$end")
             }
             imageProxy.close()
             return
        }

        lastAnalysisTime = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    // Filter faces based on size (distance)
                    // We check if *any* detected face is large enough.
                    val detected = faces.any { face ->
                         val ratio = face.boundingBox.width().toFloat() / image.width.toFloat()
                         if (debugLogging) Log.d(TAG, "Face detected. Ratio: $ratio")
                         ratio >= minFaceRatio
                    }
                    
                    val now = System.currentTimeMillis()

                    if (detected) {
                        lastPresenceTrueTime = now
                        if (!_isPresent.value) {
                            Log.d(TAG, "Presence detected (immediate)")
                            _isPresent.value = true
                        }
                    } else {
                        // Not detected. Check if we should hold the state.
                        if (_isPresent.value) {
                            if (now - lastPresenceTrueTime > gracePeriodMs) {
                                Log.d(TAG, "Presence lost (after grace period)")
                                _isPresent.value = false
                            }
                        }
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

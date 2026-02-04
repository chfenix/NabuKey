package com.nabukey.screen

import android.app.Activity
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenStateManager @Inject constructor(
    // We cannot inject PresenceDetector directly because it is created in Service.
    // Instead, we will expose a method to set the flow, or better, make PresenceDetector a Singleton managed by Hilt?
    // Given the current architecture where Service creates it, we'll add a method to observe it.
) {
    private val _screenState = MutableStateFlow(ScreenState.SLEEPING)
    val screenState = _screenState.asStateFlow()

    private var activity: Activity? = null
    private var idleTimeoutJob: Job? = null
    private var deepSleepJob: Job? = null
    private var presenceJob: Job? = null
    private var isUserPresent: Boolean = false
    
    // Configurable timeout
    private val IDLE_TIMEOUT_MS = 30000L // 30 seconds to go from ACTIVE to IDLE/SLEEPING
    private val DEEP_SLEEP_TIMEOUT_MS = 60000L // 60 seconds to go from SLEEPING to SCREEN_OFF
    
    // External dependency injection for presence flow
    fun observePresence(presenceFlow: kotlinx.coroutines.flow.Flow<Boolean>) {
        presenceJob?.cancel()
        presenceJob = CoroutineScope(Dispatchers.Main).launch {
            presenceFlow.collect { isPresent ->
                // Check edge (Absent -> Present)
                val wasPresent = isUserPresent
                isUserPresent = isPresent
                
                if (isPresent && !wasPresent) {
                    // Only trigger logic on "Arrival" (0 -> 1)
                    onUserArrived()
                } else if (!isPresent && wasPresent) {
                    onPresenceLost()
                }
            }
        }
    }

    fun attach(activity: Activity) {
        this.activity = activity
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "Activity attached, setting initial state: ${_screenState.value}")
        updateScreen(_screenState.value)
    }

    fun detach() {
        this.activity = null
        idleTimeoutJob?.cancel()
        deepSleepJob?.cancel()
        presenceJob?.cancel()
    }

    fun wake() {
        if (_screenState.value == ScreenState.SLEEPING || 
            _screenState.value == ScreenState.IDLE || 
            _screenState.value == ScreenState.SCREEN_OFF) {
            updateState(ScreenState.WAKING)
            // Simulating transition or just going straight to ACTIVE
            updateState(ScreenState.ACTIVE)
        }
        resetIdleTimer()
    }

    fun sleep() {
        updateState(ScreenState.SLEEPING)
        idleTimeoutJob?.cancel()
    }

    fun userInteraction() {
        if (_screenState.value == ScreenState.SLEEPING || _screenState.value == ScreenState.SCREEN_OFF) {
            wake()
        } else {
            // Extend active time
            resetIdleTimer()
        }
    }

    fun onConversationActive() {
        if (_screenState.value != ScreenState.ACTIVE) {
            wake()
        } else {
            resetIdleTimer()
        }
    }

    private fun resetIdleTimer(isPresenceHold: Boolean = false) {
        idleTimeoutJob?.cancel()
        
        if (isPresenceHold) {
            // If presence is holding it, we don't start the countdown to sleep.
            // We just ensure we are at least in IDLE behavior if not Active.
            return
        }
        
        idleTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
            // 1. Wait for IDLE timeout (ACTIVE -> IDLE)
            delay(IDLE_TIMEOUT_MS)
            
            if (_screenState.value == ScreenState.ACTIVE) {
                 updateState(ScreenState.IDLE)
            }
            
            // 2. Wait for Sleep timeout (IDLE -> SLEEPING)
            // Even if user is present, we eventually sleep to save power.
            delay(5000L) 
            
            // Go to sleep
            sleep()
        }
    }
    
    private fun onUserArrived() {
        Log.d(TAG, "User Arrived (Presence 0->1). Current state: ${_screenState.value}")
        
        // 1. If screen is OFF/SLEEPING -> Wake up to IDLE
        if (_screenState.value == ScreenState.SCREEN_OFF || _screenState.value == ScreenState.SLEEPING) {
             updateState(ScreenState.IDLE)
             // Start normal idle timer cycle (30s Active(virtual) -> Idle -> Sleep)
             // But wait, if we wake to IDLE, we shouldn't assume ACTIVE.
             // We can just start the IDLE->SLEEP timer? Or full cycle?
             // Usually "Wake up" implies we give them some time.
             // Let's start the full idle timer logic.
             resetIdleTimer()
        }
        
        // 2. If ACTIVE or IDLE
        // Requirement: "If from absent to present... restart timer."
        else if (_screenState.value == ScreenState.ACTIVE || _screenState.value == ScreenState.IDLE) {
            Log.d(TAG, "User re-entered during active session, resetting timer.")
            resetIdleTimer()
        }
    }

    private fun startDeepSleepTimer() {
        deepSleepJob?.cancel()
        deepSleepJob = CoroutineScope(Dispatchers.Main).launch {
            delay(DEEP_SLEEP_TIMEOUT_MS)
            if (_screenState.value == ScreenState.SLEEPING) {
                updateState(ScreenState.SCREEN_OFF)
            }
        }
    }
    
    private fun onPresenceDetected() {
        Log.d(TAG, "Presence detected. Current state: ${_screenState.value}")
        
        // Scenario 1: Screen is OFF/SLEEPING -> User came back -> Wake to IDLE
        if (_screenState.value == ScreenState.SCREEN_OFF || _screenState.value == ScreenState.SLEEPING) {
             updateState(ScreenState.IDLE)
             // Start timer to eventally sleep again if no interaction
             resetIdleTimer()
        }
        // Scenario 2: Active or Idle (e.g. conversation just ended) -> User is already here
        // We do NOT want to force reset/interrupt the natural flow to sleep just because presence "updates".
        // BUT, if the user *was absent* and now *is present* (re-entry), we might want to keep it awake?
        // The implementation of presenceFlow just emits boolean. 
        // Because we set `isUserPresent = isPresent` in collect, this function is called every time flow emits true.
        // Assuming the flow emits safely (e.g. distinctUntilChanged), this fits "state change" or "periodic check".
        
        // Re-read requirement: "If user leaves and comes back (absent -> present), restart timer."
        // If we are already running a timer (ACTIVE/IDLE), and user simply "stays", we let the timer run out (to sleep).
        // So we actually don't need to do anything special here for ACTIVE/IDLE if we want them to sleep eventualy.
        
        // HOWEVER, if we are in IDLE counting down 5s to sleep, and user waves hand (re-detected?), 
        // usually we want to keep it awake?
        // User said: "even if person is there... wait 30s then sleep".
        // So we explicitly DO NOT reset timer just for presence presence.
    }
    
    private fun onPresenceLost() {
         Log.d(TAG, "Presence lost.")
         // If user leaves, we don't need to do anything special. 
         // The timer is likely already running (since we don't hold anymore).
         // If we wanted to "speed up" sleep when user leaves, we could do it here,
         // but requirement is to just let normal logic flow.
    }

    private fun updateState(newState: ScreenState) {
        if (_screenState.value != newState) {
            _screenState.value = newState
            Log.d(TAG, "State changed to $newState")
            updateScreen(newState)
            
            // Handle Deep Sleep Timer
            if (newState == ScreenState.SLEEPING) {
                startDeepSleepTimer()
            } else {
                deepSleepJob?.cancel()
            }
        }
    }

    private fun updateScreen(state: ScreenState) {
        activity?.let { act ->
            val params = act.window.attributes
            when (state) {
                ScreenState.SLEEPING, ScreenState.SCREEN_OFF -> {
                    params.screenBrightness = 0.01f // Minimum brightness but ON
                }
                ScreenState.ACTIVE, ScreenState.WAKING -> {
                    // All active-like states keep full brightness
                    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE 
                }
                ScreenState.IDLE -> {
                    // User reported 0.1f is same as active, trying lower.
                    // If system brightness is low, 0.1f might be indistinguishable.
                    params.screenBrightness = 0.05f 
                }
            }
            Log.d(TAG, "Updating screen brightness for state $state to ${params.screenBrightness}")
            act.window.attributes = params
        }
    }

    companion object {
        private const val TAG = "ScreenStateManager"
    }
}

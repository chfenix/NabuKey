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
                isUserPresent = isPresent
                if (isPresent) {
                    onPresenceDetected()
                } else {
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
            delay(IDLE_TIMEOUT_MS)
            
            // Timeout valid only if user is NOT present (or we want to drop to IDLE anyway)
            // If user is present, we drop from ACTIVE to IDLE, but HOLD at IDLE.
            
            if (_screenState.value == ScreenState.ACTIVE) {
                 updateState(ScreenState.IDLE)
            }
            
            if (isUserPresent) {
                // User is here, do NOT sleep. Stay in IDLE.
                Log.d(TAG, "User present, holding at IDLE")
                return@launch
            }
            
            // If we are IDLE (either just transitioned or was already IDLE) and user not here
            if (_screenState.value == ScreenState.IDLE) {
                 delay(5000L) 
                 // Check again just in case presence changed during 5s delay without triggering full reset
                 if (!isUserPresent) {
                     sleep()
                 }
            }
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
        // If screen is OFF or SLEEPING, wake to IDLE (dimmed but visible)
        if (_screenState.value == ScreenState.SCREEN_OFF || _screenState.value == ScreenState.SLEEPING) {
             updateState(ScreenState.IDLE)
        }
        else if (_screenState.value == ScreenState.ACTIVE) {
            // Do nothing, let timer run (it will drop to IDLE and hold)
        }
        else if (_screenState.value == ScreenState.IDLE) {
             // If IDLE, cancel timer so we don't sleep
             idleTimeoutJob?.cancel()
        }
    }
    
    private fun onPresenceLost() {
         Log.d(TAG, "Presence lost. Resuming normal timeout.")
         // Resume countdown to sleep
         resetIdleTimer(isPresenceHold = false)
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

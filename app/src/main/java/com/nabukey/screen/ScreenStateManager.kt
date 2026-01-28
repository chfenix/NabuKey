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
class ScreenStateManager @Inject constructor() {
    private val _screenState = MutableStateFlow(ScreenState.SLEEPING)
    val screenState = _screenState.asStateFlow()

    private var activity: Activity? = null
    private var idleTimeoutJob: Job? = null
    
    // Configurable timeout
    private val IDLE_TIMEOUT_MS = 30000L // 30 seconds to go from ACTIVE to IDLE/SLEEPING

    fun attach(activity: Activity) {
        this.activity = activity
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "Activity attached, setting initial state: ${_screenState.value}")
        updateScreen(_screenState.value)
    }

    fun detach() {
        this.activity = null
        idleTimeoutJob?.cancel()
    }

    fun wake() {
        if (_screenState.value == ScreenState.SLEEPING || _screenState.value == ScreenState.IDLE) {
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
        if (_screenState.value == ScreenState.SLEEPING) {
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

    private fun resetIdleTimer() {
        idleTimeoutJob?.cancel()
        idleTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(IDLE_TIMEOUT_MS)
            if (_screenState.value == ScreenState.ACTIVE) {
                 // First go to IDLE or straight to SLEEPING?
                 // Requirement says "Active -> Idle -> Sleeping" in diagram but "无交互一段时间后自动休眠"
                 // Diagram: ACTIVE -> IDLE -> SLEEPING
                 updateState(ScreenState.IDLE)
                 delay(5000L) // Stay in IDLE for 5 seconds then sleep
                 sleep()
            }
        }
    }

    private fun updateState(newState: ScreenState) {
        if (_screenState.value != newState) {
            _screenState.value = newState
            Log.d(TAG, "State changed to $newState")
            updateScreen(newState)
        }
    }

    private fun updateScreen(state: ScreenState) {
        activity?.let { act ->
            val params = act.window.attributes
            when (state) {
                ScreenState.SLEEPING -> {
                    params.screenBrightness = 0.01f // Minimum brightness but ON
                }
                ScreenState.ACTIVE, ScreenState.WAKING -> {
                    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE // System preferred or Max
                }
                ScreenState.IDLE -> {
                     params.screenBrightness = 0.1f // Dimmed
                }
            }
            act.window.attributes = params
        }
    }

    companion object {
        private const val TAG = "ScreenStateManager"
    }
}

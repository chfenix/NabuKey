package com.nabukey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.nabukey.permissions.VOICE_SATELLITE_PERMISSIONS
import com.nabukey.ui.MainNavHost
import com.nabukey.ui.services.ServiceViewModel
import com.nabukey.ui.services.rememberLaunchWithMultiplePermissions
import com.nabukey.ui.theme.NabuKeyTheme
import android.view.MotionEvent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nabukey.esphome.voicesatellite.Listening
import com.nabukey.esphome.voicesatellite.Processing
import com.nabukey.esphome.voicesatellite.Responding
import com.nabukey.esphome.voicesatellite.Waking
import com.nabukey.screen.ScreenStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var screenStateManager: ScreenStateManager

    private var created = false
    private val serviceViewModel: ServiceViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        screenStateManager.attach(this)
        observeVoiceState()

        setContent {
            NabuKeyTheme {
                OnCreate()
                MainNavHost()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenStateManager.detach()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        screenStateManager.userInteraction()
        return super.dispatchTouchEvent(ev)
    }

    private fun observeVoiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                serviceViewModel.satellite
                    .flatMapLatest { service ->
                        service?.voiceSatelliteState ?: emptyFlow()
                    }
                    .collect { state ->
                        when (state) {
                            is Listening, is Processing, is Responding, is Waking -> {
                                screenStateManager.onConversationActive()
                            }
                            else -> {
                                // Other states don't force wake, but don't force sleep either
                                // implicit handling via idle timer
                            }
                        }
                    }
            }
        }
    }

    @Composable
    fun OnCreate() {
        val permissionsLauncher = rememberLaunchWithMultiplePermissions(
            onPermissionGranted = {
                android.util.Log.d("MainActivity", "Permissions granted, auto-starting service")
                serviceViewModel.autoStartServiceIfRequired()
            },
            onPermissionDenied = { denied ->
                android.util.Log.e("MainActivity", "Permissions denied: ${denied.joinToString()}")
            }
        )
        DisposableEffect(Unit) {
            android.util.Log.d("MainActivity", "Requesting permissions: ${VOICE_SATELLITE_PERMISSIONS.joinToString()}")
            permissionsLauncher.launch(VOICE_SATELLITE_PERMISSIONS)
            onDispose { }
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}
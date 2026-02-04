package com.nabukey.ui.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nabukey.services.VoiceSatelliteService
import com.nabukey.settings.VoiceSatelliteSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.nabukey.screen.ScreenStateManager

@HiltViewModel
class ServiceViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: VoiceSatelliteSettingsStore,
    private val screenStateManager: ScreenStateManager
) : ViewModel() {
    val screenState = screenStateManager.screenState
    private var created = false

    private val _satellite = MutableStateFlow<VoiceSatelliteService?>(null)
    val satellite = _satellite.asStateFlow()

    private val serviceConnection = bindService(context) { service ->
        _satellite.value = service
        if (service != null) {
             // Always observe presence. If not ready, flow will be empty/false initially and update later.
             screenStateManager.observePresence(service.presenceFlow)
        }
    }


    override fun onCleared() {
        context.unbindService(serviceConnection)
        super.onCleared()
    }

    private fun bindService(
        context: Context,
        connectedChanged: (VoiceSatelliteService?) -> Unit
    ): ServiceConnection {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                (binder as? VoiceSatelliteService.VoiceSatelliteBinder)?.let {
                    connectedChanged(it.service)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connectedChanged(null)
            }
        }
        val serviceIntent = Intent(context, VoiceSatelliteService::class.java)
        val bound = context.bindService(
            serviceIntent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound)
            Log.e(TAG, "Cannot bind to VoiceAssistantService")
        return serviceConnection
    }

    fun autoStartServiceIfRequired() {
        if (created)
            return
        created = true
        viewModelScope.launch {
            // Force auto-start regardless of setting value, as per user request
            // val autoStart = settings.autoStart.get()
            // if (autoStart)
            _satellite.dropWhile { it == null }.first()?.startVoiceSatellite()
        }
    }

    fun notifyUserInteraction() {
        screenStateManager.userInteraction()
    }

    companion object {
        private const val TAG = "ServiceViewModel"
    }
}
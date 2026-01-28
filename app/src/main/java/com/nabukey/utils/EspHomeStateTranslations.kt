package com.nabukey.utils

import android.content.res.Resources
import com.nabukey.R
import com.nabukey.esphome.Connected
import com.nabukey.esphome.Disconnected
import com.nabukey.esphome.EspHomeState
import com.nabukey.esphome.ServerError
import com.nabukey.esphome.Stopped
import com.nabukey.esphome.voicesatellite.Listening
import com.nabukey.esphome.voicesatellite.Processing
import com.nabukey.esphome.voicesatellite.Responding
import com.nabukey.esphome.voicesatellite.Waking

fun EspHomeState.translate(resources: Resources): String = when (this) {
    is Stopped -> resources.getString(R.string.satellite_state_stopped)
    is Disconnected -> resources.getString(R.string.satellite_state_disconnected)
    is Connected -> resources.getString(R.string.satellite_state_idle)
    is Listening -> resources.getString(R.string.satellite_state_listening)
    is Processing -> resources.getString(R.string.satellite_state_processing)
    is Responding -> resources.getString(R.string.satellite_state_responding)
    is Waking -> resources.getString(R.string.satellite_state_waking)
    is ServerError -> resources.getString(R.string.satellite_state_server_error, message)
    else -> this.toString()
}
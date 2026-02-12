package com.nabukey.ui.screens.home

import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nabukey.R
import com.nabukey.esphome.Stopped
import com.nabukey.esphome.voicesatellite.Listening
import com.nabukey.esphome.voicesatellite.Processing
import com.nabukey.esphome.voicesatellite.Responding
import com.nabukey.esphome.voicesatellite.Waking
import com.nabukey.screen.ScreenState
import com.nabukey.ui.Settings
import com.nabukey.ui.components.expression.ExpressionState
import com.nabukey.ui.services.ServiceViewModel
import com.nabukey.ui.services.StartStopVoiceSatellite
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ServiceViewModel = hiltViewModel()
) {
    val service by viewModel.satellite.collectAsStateWithLifecycle(initialValue = null)
    val currentService = service
    val serviceState by (currentService?.voiceSatelliteState ?: flowOf(Stopped))
        .collectAsStateWithLifecycle(initialValue = Stopped)

    if (serviceState !is Stopped && currentService != null) {
        val screenState by viewModel.screenState.collectAsStateWithLifecycle()

        if (screenState == ScreenState.SCREEN_OFF) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            viewModel.notifyUserInteraction()
                        }
                    }
            )
            return
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val statusColor = when (serviceState) {
                is com.nabukey.esphome.Disconnected,
                is com.nabukey.esphome.ServerError -> Color(0xFFF44336)
                is com.nabukey.esphome.Connected -> Color.Green
                is Waking,
                is Listening -> Color(0xFFFF5722)
                is Processing -> Color(0xFF9C27B0)
                is Responding -> Color(0xFFFFC107)
                else -> Color.Gray
            }

            val expressionState = remember(serviceState, screenState) {
                when {
                    serviceState is Listening -> ExpressionState.Listening
                    serviceState is Processing -> ExpressionState.Thinking
                    serviceState is Responding -> ExpressionState.Speaking
                    screenState == ScreenState.SLEEPING -> ExpressionState.Sleeping
                    else -> ExpressionState.Idle()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                com.nabukey.ui.components.FaceView(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                viewModel.notifyUserInteraction()

                                val cornerSize = 150.dp.toPx()
                                val isTopRightCorner = offset.x > size.width - cornerSize &&
                                    offset.y < cornerSize

                                if (isTopRightCorner) {
                                    currentService.stopVoiceSatellite()
                                } else {
                                    currentService.stopConversation()
                                }
                            }
                        },
                    expressionState = expressionState,
                    eyeColor = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatteryIndicator(
                        modifier = Modifier.size(width = 34.dp, height = 16.dp),
                        frameColor = Color.White
                    )

                    IconButton(
                        onClick = { navController.navigate(Settings) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert_24px),
                            contentDescription = stringResource(R.string.label_settings),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawCircle(
                            color = statusColor,
                            radius = size.minDimension / 2
                        )
                    }
                }
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    colors = topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        Box {
                            var expanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert_24px),
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.label_settings)) },
                                    onClick = { navController.navigate(Settings) }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StartStopVoiceSatellite(viewModel)
            }
        }
    }
}

@Composable
private fun BatteryIndicator(
    modifier: Modifier = Modifier,
    frameColor: Color
) {
    val context = LocalContext.current
    val batteryLevel by produceState(initialValue = 1f) {
        callbackFlow {
            fun levelFromIntent(intent: Intent?): Float {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                return if (level in 0..scale && scale > 0) {
                    (level / scale.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: Intent?) {
                    trySend(levelFromIntent(intent))
                }
            }

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = context.registerReceiver(receiver, filter)
            trySend(levelFromIntent(sticky))

            awaitClose {
                context.unregisterReceiver(receiver)
            }
        }.collectLatest { level ->
            value = level
        }
    }

    val levelColor = when {
        batteryLevel <= 0.2f -> Color(0xFFE53935)
        batteryLevel <= 0.5f -> Color(0xFFFFB300)
        else -> Color(0xFF43A047)
    }

    Canvas(modifier = modifier) {
        val capWidth = size.width * 0.11f
        val bodyWidth = size.width - capWidth - 2f
        val strokeWidth = 2.4f
        val bodyRadius = CornerRadius(size.height * 0.2f, size.height * 0.2f)

        drawRoundRect(
            color = frameColor,
            topLeft = Offset.Zero,
            size = Size(bodyWidth, size.height),
            cornerRadius = bodyRadius,
            style = Stroke(width = strokeWidth)
        )

        val capHeight = size.height * 0.45f
        drawRoundRect(
            color = frameColor,
            topLeft = Offset(bodyWidth + 2f, (size.height - capHeight) / 2f),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(capHeight * 0.2f, capHeight * 0.2f)
        )

        val innerPadding = strokeWidth + 1.5f
        val innerWidth = (bodyWidth - innerPadding * 2f).coerceAtLeast(0f)
        val fillWidth = (innerWidth * batteryLevel).coerceAtLeast(size.height * 0.12f)
        val fillHeight = (size.height - innerPadding * 2f).coerceAtLeast(0f)
        drawRoundRect(
            color = levelColor,
            topLeft = Offset(innerPadding, innerPadding),
            size = Size(fillWidth, fillHeight),
            cornerRadius = CornerRadius(fillHeight * 0.2f, fillHeight * 0.2f)
        )
    }
}

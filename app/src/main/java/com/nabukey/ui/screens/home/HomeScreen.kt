package com.nabukey.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.nabukey.R
import com.nabukey.ui.Settings
import com.nabukey.ui.services.StartStopVoiceSatellite
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabukey.ui.services.ServiceViewModel
import com.nabukey.esphome.Stopped
import com.nabukey.ui.components.Eyes
import androidx.compose.foundation.clickable
import kotlinx.coroutines.flow.flowOf
import com.nabukey.esphome.voicesatellite.Listening
import com.nabukey.esphome.voicesatellite.Processing
import com.nabukey.esphome.voicesatellite.Responding
import com.nabukey.esphome.voicesatellite.Waking
import com.nabukey.screen.ScreenState
import com.nabukey.utils.translate

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
        // Observe screen state
        val screenState by viewModel.screenState.collectAsStateWithLifecycle()

        if (screenState == ScreenState.SCREEN_OFF) {
            // Fake Off Mode: Pure Black Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            viewModel.notifyUserInteraction()
                        }
                    }
            )
        } else {
            // Full Screen Face Mode with Status Overlay
            Box(modifier = Modifier.fillMaxSize()) {
                val statusColor = when (serviceState) {
                    // 未连接 -> 纯红色（错误）
                    is com.nabukey.esphome.Disconnected,
                    is com.nabukey.esphome.ServerError -> androidx.compose.ui.graphics.Color(0xFFF44336) // Material Red

                    // 待机 -> 绿色
                    is com.nabukey.esphome.Connected -> androidx.compose.ui.graphics.Color.Green

                    // 唤醒中/监听 -> 橙红色（录音灯）
                    is Waking,
                    is Listening -> androidx.compose.ui.graphics.Color(0xFFFF5722) // Material Deep Orange (录音红)

                    // 处理中 -> 紫色（AI 思考）
                    is Processing -> androidx.compose.ui.graphics.Color(0xFF9C27B0) // Material Purple

                    // 说话 -> 琥珀色/金色（温暖输出）
                    is Responding -> androidx.compose.ui.graphics.Color(0xFFFFC107) // Material Amber

                    // 其他状态 -> 灰色（不应该出现）
                    else -> androidx.compose.ui.graphics.Color.Gray
                }

                // Map combined states to ExpressionState
                val expressionState = remember(serviceState, screenState) {
                    when {
                        // Critical service states override screen state (except sleeping?)
                        // If we are talking/listening, we should probably look alert even if screen was dim
                        // But ScreenManager handles waking up the screen, so screenState should become ACTIVE/WAKING.

                        // Priority 1: Service Activities
                        serviceState is Listening -> com.nabukey.ui.components.expression.ExpressionState.Listening
                        serviceState is Processing -> com.nabukey.ui.components.expression.ExpressionState.Thinking
                        serviceState is Responding -> com.nabukey.ui.components.expression.ExpressionState.Speaking

                        // Priority 2: Screen States (Idle/Sleeping)
                        // If service is Connected/Stopped but screen is Sleeping -> Sleep
                        screenState == ScreenState.SLEEPING -> com.nabukey.ui.components.expression.ExpressionState.Sleeping

                        // Priority 3: Default Active
                        else -> com.nabukey.ui.components.expression.ExpressionState.Idle()
                    }
                }

                // 使用新的 FaceView 组件 with zone-based touch
                Box(modifier = Modifier.fillMaxSize()) {
                    com.nabukey.ui.components.FaceView(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    // Wake up screen on any touch
                                    viewModel.notifyUserInteraction()

                                    // Define top-right corner zone (150dp x 150dp from top-right)
                                    val cornerSize = 150.dp.toPx()
                                    val isTopRightCorner = offset.x > size.width - cornerSize &&
                                            offset.y < cornerSize

                                    if (isTopRightCorner) {
                                        // Stop service and return to idle screen
                                        currentService.stopVoiceSatellite()
                                    } else {
                                        // Just stop current conversation
                                        currentService.stopConversation()
                                    }
                                }
                            },
                        expressionState = expressionState,
                        eyeColor = androidx.compose.ui.graphics.Color.White
                    )
                }

                // Status indicator dot and settings button in top-right corner
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        // Settings button
                        IconButton(
                            onClick = { navController.navigate(Settings) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert_24px),
                                contentDescription = stringResource(R.string.label_settings),
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Status indicator dot
                        Canvas(modifier = Modifier.size(20.dp)) {
                            drawCircle(
                                color = statusColor,
                                radius = size.minDimension / 2
                            )
                        }
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
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                title = {
                    Text(stringResource(R.string.app_name))
                },
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
package com.nabukey.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun Eyes(
    modifier: Modifier = Modifier,
    eyeColor: Color = Color.White
) {
    val leftEyeScaleY = remember { Animatable(1f) }
    val rightEyeScaleY = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2000, 6000))
            
            val blinkDuration = 100
             
            // Launch in parallel within the scope
            val job1 = launch {
                 leftEyeScaleY.animateTo(0.1f, animationSpec = tween(blinkDuration))
                 leftEyeScaleY.animateTo(1f, animationSpec = tween(blinkDuration))
            }
            val job2 = launch {
                 rightEyeScaleY.animateTo(0.1f, animationSpec = tween(blinkDuration))
                 rightEyeScaleY.animateTo(1f, animationSpec = tween(blinkDuration))
            }
            
            // Wait for both to finish
            job1.join()
            job2.join()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(300.dp, 150.dp)) {
            val eyeWidth = size.width / 2.5f
            // Use width for height to make them circles by default, then scale height
            val eyeHeight = eyeWidth 

            // Left Eye
            drawOval(
                color = eyeColor,
                topLeft = Offset(
                    x = 0f, 
                    y = (size.height - eyeHeight * leftEyeScaleY.value) / 2
                ),
                size = Size(eyeWidth, eyeHeight * leftEyeScaleY.value)
            )

            // Right Eye
            drawOval(
                color = eyeColor,
                topLeft = Offset(
                    x = size.width - eyeWidth, 
                    y = (size.height - eyeHeight * rightEyeScaleY.value) / 2
                ),
                size = Size(eyeWidth, eyeHeight * rightEyeScaleY.value)
            )
        }
    }
}

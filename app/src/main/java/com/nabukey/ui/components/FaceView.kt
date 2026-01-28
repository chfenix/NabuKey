package com.nabukey.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nabukey.ui.components.expression.ExpressionState
import com.nabukey.ui.components.expression.rememberExpressionAnimator

/**
 * 主表情视图组件
 * 根据表情状态显示不同的表情动画
 * 
 * 使用方法：
 * ```
 * FaceView(
 *     expressionState = ExpressionState.Idle(),
 *     eyeColor = Color.White
 * )
 * ```
 */
@Composable
fun FaceView(
    modifier: Modifier = Modifier,
    expressionState: ExpressionState = ExpressionState.Idle(),
    eyeColor: Color = Color.White,
    backgroundColor: Color = Color.Black
) {
    // 画布尺寸
    val canvasWidth = 300.dp
    val canvasHeight = 150.dp
    
    // 转换 dp 到像素
    val density = LocalDensity.current
    val canvasWidthPx = with(density) { canvasWidth.toPx() }
    val canvasHeightPx = with(density) { canvasHeight.toPx() }
    val eyeWidthPx = canvasWidthPx / 2.5f
    val eyeHeightPx = eyeWidthPx
    
    // 眼睛中心位置
    val leftEyeCenter = Offset(
        x = eyeWidthPx / 2,
        y = canvasHeightPx / 2
    )
    val rightEyeCenter = Offset(
        x = canvasWidthPx - eyeWidthPx / 2,
        y = canvasHeightPx / 2
    )
    
    // 获取表情动画控制器
    val animator = rememberExpressionAnimator(
        state = expressionState,
        eyeWidth = eyeWidthPx
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(canvasWidth, canvasHeight)) {
            // 应用整体缩放
            val scale = animator.eyeScale
            val actualEyeWidth = eyeWidthPx * scale
            val actualEyeHeight = eyeHeightPx * scale
            
            // 绘制左眼
            val leftEyeHeight = actualEyeHeight * animator.leftEyeScaleY
            drawOval(
                color = eyeColor,
                topLeft = Offset(
                    x = leftEyeCenter.x - actualEyeWidth / 2 + animator.eyeOffsetX,
                    y = leftEyeCenter.y - leftEyeHeight / 2
                ),
                size = Size(actualEyeWidth, leftEyeHeight)
            )
            
            // 绘制右眼
            val rightEyeHeight = actualEyeHeight * animator.rightEyeScaleY
            drawOval(
                color = eyeColor,
                topLeft = Offset(
                    x = rightEyeCenter.x - actualEyeWidth / 2 + animator.eyeOffsetX,
                    y = rightEyeCenter.y - rightEyeHeight / 2
                ),
                size = Size(actualEyeWidth, rightEyeHeight)
            )
        }
    }
}

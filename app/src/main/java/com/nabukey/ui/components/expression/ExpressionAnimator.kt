package com.nabukey.ui.components.expression

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 表情动画控制器
 * 使用密封类定义不同的表情状态，每个状态包含自己的动画逻辑
 */
sealed class ExpressionAnimator {
    /**
     * 获取左眼的垂直缩放值
     */
    abstract val leftEyeScaleY: Float
    
    /**
     * 获取右眼的垂直缩放值
     */
    abstract val rightEyeScaleY: Float
    
    /**
     * 获取眼睛的水平偏移值
     */
    abstract val eyeOffsetX: Float
    
    /**
     * 获取眼睛的整体缩放值
     */
    abstract val eyeScale: Float
    
    /**
     * 待机状态 - 随机眨眼 + 四处看
     */
    class Idle(
        private val leftEyeAnim: Animatable<Float, *>,
        private val rightEyeAnim: Animatable<Float, *>,
        private val eyeOffsetAnim: Animatable<Float, *>
    ) : ExpressionAnimator() {
        override val leftEyeScaleY: Float get() = leftEyeAnim.value
        override val rightEyeScaleY: Float get() = rightEyeAnim.value
        override val eyeOffsetX: Float get() = eyeOffsetAnim.value
        override val eyeScale: Float = 1f
        
        suspend fun animate() {
            while (true) {
                delay(Random.nextLong(2000, 6000))
                
                // 30% 概率四处看，70% 概率眨眼
                if (Random.nextFloat() < 0.3f) {
                    lookAround()
                } else {
                    blink()
                }
            }
        }
        
        private suspend fun blink() {
            val blinkDuration = 100
            kotlinx.coroutines.coroutineScope {
                val job1 = launch {
                    leftEyeAnim.animateTo(0.1f, animationSpec = tween(blinkDuration))
                    leftEyeAnim.animateTo(1f, animationSpec = tween(blinkDuration))
                }
                val job2 = launch {
                    rightEyeAnim.animateTo(0.1f, animationSpec = tween(blinkDuration))
                    rightEyeAnim.animateTo(1f, animationSpec = tween(blinkDuration))
                }
                job1.join()
                job2.join()
            }
        }

        private suspend fun lookAround() {
            val lookDuration = 400
            val holdDuration = 1000L
            val offset = 40f // 像素偏移量
            
            // 随机方向：左或右
            val direction = if (Random.nextBoolean()) 1f else -1f
            val targetOffset = offset * direction

            eyeOffsetAnim.animateTo(targetOffset, animationSpec = tween(lookDuration))
            delay(holdDuration)
            eyeOffsetAnim.animateTo(0f, animationSpec = tween(lookDuration))
        }
    }

    /**
     * 休眠状态 - 闭眼呼吸
     */
    class Sleeping(
        private val leftEyeAnim: Animatable<Float, *>,
        private val rightEyeAnim: Animatable<Float, *>
    ) : ExpressionAnimator() {
        // 初始状态为闭眼 (0.1f)
        override val leftEyeScaleY: Float get() = leftEyeAnim.value
        override val rightEyeScaleY: Float get() = rightEyeAnim.value
        override val eyeOffsetX: Float = 0f
        override val eyeScale: Float = 1f

        suspend fun animate() {
            // 确保初始闭眼
            leftEyeAnim.snapTo(0.1f)
            rightEyeAnim.snapTo(0.1f)

            while (true) {
                // 缓慢呼吸效果：0.1 -> 0.15 -> 0.1
                val breathDuration = 2000
                
                kotlinx.coroutines.coroutineScope {
                    launch {
                         leftEyeAnim.animateTo(0.15f, animationSpec = tween(breathDuration))
                         leftEyeAnim.animateTo(0.1f, animationSpec = tween(breathDuration))
                    }
                    launch {
                        rightEyeAnim.animateTo(0.15f, animationSpec = tween(breathDuration))
                        rightEyeAnim.animateTo(0.1f, animationSpec = tween(breathDuration))
                    }
                }
                delay(1000) // 呼吸间歇
            }
        }
    }
    
    /**
     * 监听状态 - 眼睛微微放大
     */
    class Listening(
        private val scaleAnim: Animatable<Float, *>
    ) : ExpressionAnimator() {
        override val leftEyeScaleY: Float = 1f
        override val rightEyeScaleY: Float = 1f
        override val eyeOffsetX: Float = 0f
        override val eyeScale: Float get() = scaleAnim.value
        
        suspend fun animate() {
            scaleAnim.animateTo(1.1f, animationSpec = tween(300))
        }
    }
    
    /**
     * 思考状态 - 眼睛左右移动
     */
    class Thinking(
        private val offsetAnim: Animatable<Float, *>,
        private val eyeWidth: Float
    ) : ExpressionAnimator() {
        override val leftEyeScaleY: Float = 1f
        override val rightEyeScaleY: Float = 1f
        override val eyeOffsetX: Float get() = offsetAnim.value
        override val eyeScale: Float = 1f
        
        suspend fun animate() {
            // 先向右看
            offsetAnim.animateTo(eyeWidth * 0.3f, animationSpec = tween(800))
            // 然后循环左右移动
            offsetAnim.animateTo(
                targetValue = -eyeWidth * 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }
    
    /**
     * 说话状态 - 眼睛轻微眨动
     */
    class Speaking(
        private val scaleYAnim: Animatable<Float, *>
    ) : ExpressionAnimator() {
        override val leftEyeScaleY: Float get() = scaleYAnim.value
        override val rightEyeScaleY: Float get() = scaleYAnim.value
        override val eyeOffsetX: Float = 0f
        override val eyeScale: Float = 1f
        
        suspend fun animate() {
            scaleYAnim.animateTo(
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }
}

/**
 * 创建并启动表情动画
 */
@Composable
fun rememberExpressionAnimator(
    state: ExpressionState,
    eyeWidth: Float
): ExpressionAnimator {
    val leftEyeScaleY = remember { Animatable(1f) }
    val rightEyeScaleY = remember { Animatable(1f) }
    val eyeScale = remember { Animatable(1f) }
    val eyeOffsetX = remember { Animatable(0f) }
    val scaleY = remember { Animatable(1f) }
    
    val animator = remember(state) {
        when (state) {
            is ExpressionState.Idle -> ExpressionAnimator.Idle(leftEyeScaleY, rightEyeScaleY, eyeOffsetX)
            is ExpressionState.Listening -> ExpressionAnimator.Listening(eyeScale)
            is ExpressionState.Thinking -> ExpressionAnimator.Thinking(eyeOffsetX, eyeWidth)
            is ExpressionState.Speaking -> ExpressionAnimator.Speaking(scaleY)
            is ExpressionState.Error -> ExpressionAnimator.Idle(leftEyeScaleY, rightEyeScaleY, eyeOffsetX)
            is ExpressionState.Sleeping -> ExpressionAnimator.Sleeping(leftEyeScaleY, rightEyeScaleY)
        }
    }
    
    // 启动动画
    LaunchedEffect(state) {
        // 重置所有动画值
        leftEyeScaleY.snapTo(1f)
        rightEyeScaleY.snapTo(1f)
        eyeScale.snapTo(1f)
        eyeOffsetX.snapTo(0f)
        scaleY.snapTo(1f)
        
        // 启动对应的动画
        when (animator) {
            is ExpressionAnimator.Idle -> animator.animate()
            is ExpressionAnimator.Listening -> animator.animate()
            is ExpressionAnimator.Thinking -> animator.animate()
            is ExpressionAnimator.Speaking -> animator.animate()
            is ExpressionAnimator.Sleeping -> animator.animate()
        }
    }
    
    return animator
}

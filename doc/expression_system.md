# NabuKey 表情系统开发文档

## 概述

NabuKey 的表情系统使用模块化设计，基于 Jetpack Compose 和 Kotlin 密封类实现。系统支持多种表情动画，易于扩展和维护。

## 架构设计

### 文件结构

```
app/src/main/java/com/nabukey/ui/components/
├── expression/
│   ├── ExpressionState.kt      # 表情状态枚举（密封类）
│   └── ExpressionAnimator.kt   # 表情动画控制器（密封类）
└── FaceView.kt                 # 主表情视图组件
```

### 核心组件

#### 1. ExpressionState（表情状态）

**位置**: `ui/components/expression/ExpressionState.kt`

**作用**: 定义所有可用的表情状态

**实现方式**: 使用密封类（Sealed Class）

```kotlin
sealed class ExpressionState {
    data object Idle : ExpressionState()        // 待机
    data object Listening : ExpressionState()   // 监听
    data object Thinking : ExpressionState()    // 思考
    data object Speaking : ExpressionState()    // 说话
    data object Error : ExpressionState()       // 错误
}
```

#### 2. ExpressionAnimator（表情动画控制器）

**位置**: `ui/components/expression/ExpressionAnimator.kt`

**作用**: 实现每种表情的动画逻辑

**核心属性**:
- `leftEyeScaleY`: 左眼垂直缩放（0.0 = 闭合, 1.0 = 睁开）
- `rightEyeScaleY`: 右眼垂直缩放
- `eyeOffsetX`: 眼睛水平偏移（用于左右看）
- `eyeScale`: 眼睛整体缩放（用于放大/缩小）

**实现方式**: 使用密封类，每个表情是一个子类

#### 3. FaceView（主视图组件）

**位置**: `ui/components/FaceView.kt`

**作用**: 渲染表情视图，根据状态显示不同动画

**参数**:
- `expressionState`: 表情状态
- `eyeColor`: 眼睛颜色（默认白色）
- `backgroundColor`: 背景颜色（默认黑色）

## 当前支持的表情

| 表情状态 | 动画效果 | 实现类 | 用途场景 |
|---------|---------|--------|---------|
| **Idle** | 随机眨眼（2-6秒间隔） | `ExpressionAnimator.Idle` | 待机、已连接 |
| **Listening** | 眼睛放大 10% | `ExpressionAnimator.Listening` | 正在监听用户说话 |
| **Thinking** | 眼睛左右移动 | `ExpressionAnimator.Thinking` | 正在处理请求 |
| **Speaking** | 轻微上下眨动 | `ExpressionAnimator.Speaking` | 正在播放语音回复 |
| **Error** | 同 Idle | `ExpressionAnimator.Idle` | 发生错误 |

## 如何添加新表情

### 步骤 1: 定义表情状态

在 `ExpressionState.kt` 中添加新的状态：

```kotlin
sealed class ExpressionState {
    // ... 现有状态 ...
    
    /**
     * 新表情 - 描述
     */
    data object Happy : ExpressionState()
}
```

### 步骤 2: 实现动画控制器

在 `ExpressionAnimator.kt` 中添加新的动画类：

```kotlin
sealed class ExpressionAnimator {
    // ... 现有属性 ...
    
    /**
     * 开心表情 - 眼睛变成弯月形
     */
    class Happy(
        private val scaleYAnim: Animatable<Float, *>
    ) : ExpressionAnimator() {
        override val leftEyeScaleY: Float get() = scaleYAnim.value
        override val rightEyeScaleY: Float get() = scaleYAnim.value
        override val eyeOffsetX: Float = 0f
        override val eyeScale: Float = 1f
        
        suspend fun animate() {
            // 实现你的动画逻辑
            scaleYAnim.animateTo(0.5f, animationSpec = tween(300))
        }
    }
}
```

### 步骤 3: 注册到动画工厂

在 `ExpressionAnimator.kt` 的 `rememberExpressionAnimator` 函数中添加分支：

```kotlin
@Composable
fun rememberExpressionAnimator(
    state: ExpressionState,
    eyeWidth: Float
): ExpressionAnimator {
    // ... 现有代码 ...
    
    val animator = remember(state) {
        when (state) {
            // ... 现有分支 ...
            is ExpressionState.Happy -> ExpressionAnimator.Happy(scaleY)
        }
    }
    
    // 在 LaunchedEffect 中添加动画启动逻辑
    LaunchedEffect(state) {
        // ... 重置代码 ...
        
        when (animator) {
            // ... 现有分支 ...
            is ExpressionAnimator.Happy -> animator.animate()
        }
    }
    
    return animator
}
```

### 步骤 4: 在 UI 中使用

在 `HomeScreen.kt` 或其他地方使用新表情：

```kotlin
FaceView(
    expressionState = ExpressionState.Happy,
    eyeColor = Color.White
)
```

## 动画技巧和最佳实践

### 1. 使用 Animatable

所有动画值都应该使用 `Animatable<Float, *>`：

```kotlin
val scaleY = remember { Animatable(1f) }
```

### 2. 动画规范

- **眨眼动画**: 100ms 闭眼 + 100ms 睁眼
- **状态切换**: 300ms 过渡动画
- **循环动画**: 使用 `infiniteRepeatable`

```kotlin
// 单次动画
scaleY.animateTo(0.1f, animationSpec = tween(100))

// 循环动画
scaleY.animateTo(
    targetValue = 0.85f,
    animationSpec = infiniteRepeatable(
        animation = tween(400),
        repeatMode = RepeatMode.Reverse
    )
)
```

### 3. 并行动画

使用协程的 `launch` 实现并行动画：

```kotlin
kotlinx.coroutines.coroutineScope {
    val job1 = launch { leftEye.animateTo(...) }
    val job2 = launch { rightEye.animateTo(...) }
    job1.join()
    job2.join()
}
```

### 4. 动画值范围

| 属性 | 范围 | 说明 |
|-----|------|------|
| `leftEyeScaleY` / `rightEyeScaleY` | 0.0 ~ 1.0 | 0.0=完全闭合, 1.0=完全睁开 |
| `eyeScale` | 0.5 ~ 1.5 | 整体缩放，1.0=正常大小 |
| `eyeOffsetX` | -eyeWidth*0.5 ~ eyeWidth*0.5 | 水平偏移，0=居中 |

### 5. 性能优化

- ✅ 使用 `remember` 缓存 `Animatable` 实例
- ✅ 在 `LaunchedEffect(state)` 中启动动画
- ✅ 状态切换时重置所有动画值（`snapTo`）
- ❌ 避免在 `@Composable` 函数中直接创建 `Animatable`

## 表情状态映射

### 当前映射规则

在 `HomeScreen.kt` 中，服务状态映射到表情状态：

```kotlin
// 当前实现：所有状态都使用 Idle
val expressionState = ExpressionState.Idle

// TODO: 未来可以根据更详细的状态切换
// 例如：
// val expressionState = when (voiceSatelliteState) {
//     is VoiceState.Listening -> ExpressionState.Listening
//     is VoiceState.Processing -> ExpressionState.Thinking
//     is VoiceState.Speaking -> ExpressionState.Speaking
//     else -> ExpressionState.Idle
// }
```

### 扩展建议

如果 `VoiceSatelliteService` 提供了更详细的状态，可以实现更丰富的表情切换：

1. 在 `VoiceSatelliteService` 中添加状态流
2. 在 `HomeScreen` 中监听状态变化
3. 根据状态映射到对应的表情

## 测试和调试

### 手动测试表情

创建一个测试界面，可以手动切换表情：

```kotlin
@Composable
fun ExpressionTestScreen() {
    var currentState by remember { mutableStateOf<ExpressionState>(ExpressionState.Idle) }
    
    Column {
        FaceView(
            modifier = Modifier.weight(1f),
            expressionState = currentState
        )
        
        Row {
            Button(onClick = { currentState = ExpressionState.Idle }) {
                Text("Idle")
            }
            Button(onClick = { currentState = ExpressionState.Listening }) {
                Text("Listening")
            }
            // ... 其他按钮
        }
    }
}
```

### 调试技巧

1. **查看动画值**: 在 `ExpressionAnimator` 中添加日志
2. **调整动画速度**: 修改 `tween(duration)` 的时长
3. **检查状态切换**: 在 `LaunchedEffect(state)` 中添加日志

## 常见问题

### Q1: 表情切换不流畅？

**A**: 确保在状态切换时重置了所有动画值：

```kotlin
LaunchedEffect(state) {
    leftEyeScaleY.snapTo(1f)  // 立即重置，不要用 animateTo
    rightEyeScaleY.snapTo(1f)
    // ... 其他值
}
```

### Q2: 动画不执行？

**A**: 检查以下几点：
1. `LaunchedEffect` 的 key 是否正确（应该是 `state`）
2. `animate()` 函数是否是 `suspend` 函数
3. 是否在 `when` 分支中调用了 `animate()`

### Q3: 如何实现更复杂的动画？

**A**: 可以组合多个动画属性：

```kotlin
class ComplexExpression(...) : ExpressionAnimator() {
    override val leftEyeScaleY: Float get() = scaleY.value
    override val rightEyeScaleY: Float get() = scaleY.value
    override val eyeOffsetX: Float get() = offsetX.value
    override val eyeScale: Float get() = scale.value
    
    suspend fun animate() {
        coroutineScope {
            launch { scaleY.animateTo(...) }
            launch { offsetX.animateTo(...) }
            launch { scale.animateTo(...) }
        }
    }
}
```

## 未来扩展方向

### 1. 更多表情元素

- 添加嘴巴（微笑、说话）
- 添加眉毛（惊讶、疑惑）
- 添加脸颊（害羞、生气）

### 2. 表情组合

支持多个表情同时显示（例如：边说话边眨眼）

### 3. 动态参数

根据音量、情绪等动态调整表情强度

### 4. 自定义主题

支持不同的表情风格（卡通、写实等）

## 维护日志

| 日期 | 版本 | 修改内容 | 作者 |
|-----|------|---------|------|
| 2026-01-27 | 1.0 | 初始版本，实现基础表情系统 | - |

---

**最后更新**: 2026-01-27  
**文档版本**: 1.0

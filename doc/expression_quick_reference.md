# 表情系统快速参考

## 🚀 快速添加新表情（3步）

### 1️⃣ 定义状态 (`ExpressionState.kt`)
```kotlin
data object YourExpression : ExpressionState()
```

### 2️⃣ 实现动画 (`ExpressionAnimator.kt`)
```kotlin
class YourExpression(
    private val anim: Animatable<Float, *>
) : ExpressionAnimator() {
    override val leftEyeScaleY: Float get() = anim.value
    override val rightEyeScaleY: Float get() = anim.value
    override val eyeOffsetX: Float = 0f
    override val eyeScale: Float = 1f
    
    suspend fun animate() {
        // 你的动画代码
    }
}
```

### 3️⃣ 注册到工厂 (`ExpressionAnimator.kt` 的 `rememberExpressionAnimator`)
```kotlin
// 在 remember(state) 的 when 中添加：
is ExpressionState.YourExpression -> ExpressionAnimator.YourExpression(yourAnim)

// 在 LaunchedEffect(state) 的 when 中添加：
is ExpressionAnimator.YourExpression -> animator.animate()
```

## 📊 动画属性速查

| 属性 | 类型 | 范围 | 用途 |
|-----|------|------|------|
| `leftEyeScaleY` | Float | 0.0 ~ 1.0 | 左眼垂直缩放 |
| `rightEyeScaleY` | Float | 0.0 ~ 1.0 | 右眼垂直缩放 |
| `eyeOffsetX` | Float | -width*0.5 ~ width*0.5 | 左右看 |
| `eyeScale` | Float | 0.5 ~ 1.5 | 整体缩放 |

## 🎨 常用动画代码片段

### 眨眼
```kotlin
suspend fun blink() {
    scaleY.animateTo(0.1f, tween(100))
    scaleY.animateTo(1f, tween(100))
}
```

### 循环动画
```kotlin
scaleY.animateTo(
    targetValue = 0.85f,
    animationSpec = infiniteRepeatable(
        animation = tween(400),
        repeatMode = RepeatMode.Reverse
    )
)
```

### 并行动画
```kotlin
coroutineScope {
    launch { leftEye.animateTo(...) }
    launch { rightEye.animateTo(...) }
}
```

### 延迟
```kotlin
delay(2000)  // 延迟 2 秒
delay(Random.nextLong(2000, 6000))  // 随机延迟
```

## 📁 文件位置

```
app/src/main/java/com/nabukey/ui/components/
├── expression/
│   ├── ExpressionState.kt      ← 添加新状态
│   └── ExpressionAnimator.kt   ← 添加新动画类
└── FaceView.kt                 ← 主视图（通常不需要修改）
```

## ✅ 检查清单

添加新表情时，确保：
- [ ] 在 `ExpressionState` 中添加了新状态
- [ ] 在 `ExpressionAnimator` 中创建了新类
- [ ] 实现了 4 个必需属性
- [ ] 实现了 `animate()` 函数
- [ ] 在 `rememberExpressionAnimator` 的两个 `when` 中都添加了分支
- [ ] 测试了表情切换是否流畅

## 🐛 常见错误

❌ **忘记在 LaunchedEffect 的 when 中添加分支**
```kotlin
// 错误：只在 remember 中添加了
is ExpressionState.New -> ExpressionAnimator.New(...)

// 正确：两个 when 都要添加
```

❌ **animate() 不是 suspend 函数**
```kotlin
// 错误
fun animate() { ... }

// 正确
suspend fun animate() { ... }
```

❌ **没有重置动画值**
```kotlin
// 在 LaunchedEffect 中要先重置
yourAnim.snapTo(1f)  // ✅
```

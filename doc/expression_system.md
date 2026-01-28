# 表情系统功能说明

## 概述

NabuKey 的表情系统已经完成重构,现在支持更丰富的表情动画和待机表情循环功能。

## 表情类型

### 基础表情

1. **待机 (Idle)**
   - 随机眨眼
   - 四处看(左右随机)
   - 表情循环(可选,默认开启)
   - 循环周期:30秒自动切换

2. **监听 (Listening)**
   - 眼睛微微放大(1.1倍)
   - 表示正在监听用户说话

3. **思考 (Thinking)**
   - 眼睛左右移动
   - 表示正在处理用户请求

4. **说话 (Speaking)**
   - 眼睛轻微眨动
   - 表示正在播放语音回复

5. **休眠 (Sleeping)**
   - 闭眼呼吸效果
   - 缓慢的呼吸动画

6. **错误 (Error)**
   - 使用待机表情,但不启用表情循环

### 情绪表情

根据 `doc/emoji` 目录中的表情资源,新增了以下情绪表情:

1. **愤怒 (Angry)**
   - 眼睛缩小(0.7倍)
   - 整体缩小(0.8倍)
   - 轻微抖动效果
   - 对应资源:`anger.c`

2. **兴奋 (Excited)**
   - 眼睛放大(1.2倍)
   - 快速眨眼
   - 对应资源:`excited.c`

3. **恐惧 (Fear)**
   - 眼睛睁大(1.3倍)
   - 快速左右看
   - 对应资源:`fear.c`

4. **悲伤 (Sad)**
   - 眼睛下垂(0.7倍)
   - 整体缩小(0.9倍)
   - 缓慢眨眼(3秒一次)
   - 对应资源:`sad.c`

5. **轻蔑 (Disdain)**
   - 单眼眯起(左眼0.5倍,右眼正常)
   - 稍微向一侧看
   - 对应资源:`disdain.c`

## 待机表情循环

### 功能说明

待机状态下,系统会自动在以下表情之间循环切换:

1. **正常 (NORMAL)** - 标准待机表情
2. **微笑 (SLIGHT_SMILE)** - 眼睛稍微缩小,模拟微笑
3. **好奇 (CURIOUS)** - 稍微向一侧看,模拟好奇

### 循环参数

- **切换周期**: 30秒
- **是否启用**: 可通过 `ExpressionState.Idle(cycleExpressions = true/false)` 控制
- **默认状态**: 启用

### 使用示例

```kotlin
// 启用表情循环的待机状态(默认)
val idleState = ExpressionState.Idle()

// 禁用表情循环的待机状态
val idleStateNoCycle = ExpressionState.Idle(cycleExpressions = false)
```

## 资源文件映射

| 表情状态 | 资源文件 | 说明 |
|---------|---------|------|
| Angry | `anger.c` | 愤怒表情 |
| Excited | `excited.c` | 兴奋表情 |
| Fear | `fear.c` | 恐惧表情 |
| Sad | `sad.c` | 悲伤表情 |
| Disdain | `disdain.c` | 轻蔑表情 |
| - | `close_eys_quick.c` | 快速闭眼(用于眨眼动画) |
| - | `close_eys_slow.c` | 慢速闭眼(用于休眠动画) |
| - | `left.c` | 向左看 |
| - | `right.c` | 向右看 |
| - | `voice_effect.c` | 语音效果 |

## 技术实现

### 表情状态定义

表情状态使用 Kotlin 的 `sealed class` 定义,确保类型安全:

```kotlin
sealed class ExpressionState {
    data class Idle(val cycleExpressions: Boolean = true) : ExpressionState()
    data object Listening : ExpressionState()
    data object Thinking : ExpressionState()
    data object Speaking : ExpressionState()
    data object Error : ExpressionState()
    data object Sleeping : ExpressionState()
    
    // 情绪表情
    data object Angry : ExpressionState()
    data object Excited : ExpressionState()
    data object Fear : ExpressionState()
    data object Sad : ExpressionState()
    data object Disdain : ExpressionState()
}
```

### 动画实现

每个表情都有对应的 `ExpressionAnimator` 类,使用 Jetpack Compose 的 `Animatable` 实现流畅的动画效果:

- **leftEyeScaleY**: 左眼垂直缩放
- **rightEyeScaleY**: 右眼垂直缩放
- **eyeOffsetX**: 眼睛水平偏移
- **eyeScale**: 眼睛整体缩放

### 待机循环实现

待机表情循环通过以下机制实现:

1. 维护一个表情池 `idleExpressionPool`
2. 记录上次切换时间 `lastExpressionChangeTime`
3. 每次动画循环检查是否超过切换周期(30秒)
4. 如果超过,则切换到下一个表情

## 未来扩展

### 可能的改进方向

1. **更多情绪表情**: 可以根据需要添加更多情绪表情
2. **自定义循环池**: 允许用户自定义待机表情循环的表情池
3. **动态调整周期**: 根据使用场景动态调整表情切换周期
4. **表情组合**: 支持多个表情的组合和过渡
5. **语音情绪识别**: 根据语音情绪自动切换表情

### 待实现的资源

目前 `doc/emoji` 目录中还有以下资源文件未使用:

- `voice_effect.c` - 可用于说话时的特效
- `left.c` / `right.c` - 可用于更精细的眼睛移动控制

## 更新日志

### 2026-01-28

- ✅ 添加了5种情绪表情(愤怒、兴奋、恐惧、悲伤、轻蔑)
- ✅ 实现了待机表情循环功能
- ✅ 优化了表情动画的流畅度
- ✅ 更新了表情系统文档

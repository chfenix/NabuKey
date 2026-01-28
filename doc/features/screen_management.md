# 屏幕唤醒与休眠管理

> **状态**: ✅ 已完成 | **优先级**: ⭐⭐⭐⭐ 高 | **复杂度**: 低

## 概述

实现屏幕的智能休眠与唤醒管理，平常保持屏幕休眠（黑屏/低功耗），唤醒词触发时亮起，无交互一段时间后自动休眠。

## 状态机设计

```kotlin
enum class ScreenState {
    SLEEPING,    // 休眠中（屏幕暗）
    WAKING,      // 唤醒中（过渡动画）
    ACTIVE,      // 活跃中（屏幕亮）
    IDLE         // 空闲中（即将休眠）
}
```

**状态转换图**:
```
                 唤醒词触发
    ┌────────────────────────────────┐
    ▼                                │
SLEEPING ──▶ WAKING ──▶ ACTIVE ──▶ IDLE ──▶ SLEEPING
                          ▲          │
                          └──────────┘
                          用户交互
```

## 实现任务

- [ ] 创建 `ScreenStateManager` 管理屏幕状态
- [ ] 实现唤醒词触发屏幕亮起
- [ ] 实现无交互超时检测
- [ ] 添加屏幕亮度控制 (WindowManager)
- [ ] 添加设置项：休眠超时时间
- [ ] 整合到表情系统（休眠时显示特殊表情？）

## 文件结构

```
app/src/main/java/com/nabukey/
├── screen/
│   ├── ScreenStateManager.kt    # 屏幕状态管理
│   └── ScreenState.kt           # 状态枚举
```

## 技术要点

### 屏幕亮度控制

```kotlin
// 设置屏幕亮度
fun setScreenBrightness(activity: Activity, brightness: Float) {
    val params = activity.window.attributes
    params.screenBrightness = brightness // 0.0 ~ 1.0
    activity.window.attributes = params
}

// 休眠时设置最低亮度
setScreenBrightness(activity, 0.01f)

// 唤醒时恢复正常亮度
setScreenBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
```

### 保持屏幕常亮

```kotlin
// 在 Activity 中
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

# 屏幕唤醒与休眠管理

> **状态**: ✅ 已完成 | **优先级**: ⭐⭐⭐⭐ 高 | **复杂度**: 低

## 概述

实现屏幕的智能休眠与唤醒管理，平常保持屏幕休眠（黑屏/低功耗），唤醒词触发时亮起，无交互一段时间后自动休眠。

## 状态机设计

```kotlin
enum class ScreenState {
    SLEEPING,    // 休眠中（屏幕暗，闭眼动画）
    WAKING,      // 唤醒中（过渡状态）
    ACTIVE,      // 活跃中（屏幕亮，正常交互）
    IDLE,        // 待机中（即将休眠，低亮度，待机动画）
    SCREEN_OFF   // 关屏（模拟关机，全黑）
}
```

**状态转换图**:
```
                 唤醒词触发
    ┌────────────────────────────────┐
    ▼                                │
SLEEPING ──▶ WAKING ──▶ ACTIVE ──▶ IDLE ──▶ SLEEPING ──▶ SCREEN_OFF
  ▲                         ▲          │          ▲
  │                         └──────────┘          │
  └───────────────────────────────────────────────┘
                          用户交互 / 人脸检测
```

## 实现任务

- [x] 创建 `ScreenStateManager` 管理屏幕状态
- [x] 实现唤醒词触发屏幕亮起
- [x] 实现无交互超时检测 (30s Active -> 5s Idle -> Sleeping -> 60s -> Screen Off)
- [x] 添加屏幕亮度控制 (Active=User Pref, Idle=5%, Sleeping/Off=1%)
- [x] 集成人脸检测 (PresenceDetector)
  - 有人也休眠 (User Present Sleep)
  - 离开重返唤醒 (Re-entry Wake)
  - 防抖动保护 (Jitter Protection)

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

package com.nabukey.screen

enum class ScreenState {
    SLEEPING,    // 休眠中（屏幕暗）
    WAKING,      // 唤醒中（过渡动画）
    ACTIVE,      // 活跃中（屏幕亮）
    IDLE         // 空闲中（准备休眠）
}

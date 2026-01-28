# 人脸/人体检测

> **状态**: 📋 规划中 | **优先级**: ⭐⭐ 低 | **复杂度**: 高 | **类型**: 可选高级功能

## 概述

使用前置摄像头检测是否有人在设备跟前，有人时自动唤醒屏幕，无人时自动休眠，实现智能交互。

## 技术方案对比

| 方案 | 技术 | 优点 | 缺点 |
|-----|------|------|------|
| **ML Kit** | Google ML Kit Face Detection | 易集成，准确 | 需要 Google Play 服务 |
| **CameraX + TFLite** | 自定义检测模型 | 完全离线 | 需要优化性能 |
| **简单运动检测** | 帧差分析 | 简单，低功耗 | 精度较低 |

## 实现任务

- [ ] 调研可用的检测方案
- [ ] 集成摄像头权限和预览
- [ ] 实现人脸/运动检测
- [ ] 与屏幕唤醒系统集成
- [ ] 优化功耗和性能
- [ ] 添加隐私保护设置

## 文件结构

```
app/src/main/java/com/nabukey/
├── detection/
│   ├── FaceDetector.kt          # 人脸检测封装
│   └── PresenceManager.kt       # 存在检测管理
```

## 权限要求

需要在 `AndroidManifest.xml` 中添加：
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

# 自定义提示词系统

> **状态**: 📋 规划中 | **优先级**: ⭐⭐⭐ 中 | **复杂度**: 中

## 概述

允许用户自定义与 Home Assistant 语音助手交互时使用的提示词，实现个性化的助手行为。

## 功能需求

- 在设置界面添加提示词编辑功能
- 支持多套提示词模板
- 通过 Conversation API 传递给 Home Assistant

## 实现任务

- [ ] 设计提示词数据结构
- [ ] 在 `VoiceSatelliteSettings` 中添加提示词字段
- [ ] 创建提示词编辑界面
- [ ] 实现提示词与 HA 的通信
- [ ] 添加预设模板功能

## 文件结构

```
app/src/main/java/com/nabukey/
├── settings/
│   └── VoiceSatelliteSettings.kt  # 添加提示词字段
├── ui/screens/settings/
│   └── PromptSettingsScreen.kt    # 提示词编辑界面
```

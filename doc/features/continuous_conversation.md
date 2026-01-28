# 连续对话 (Continuous Conversation)

> **状态**: 📋 规划中 | **优先级**: ⭐⭐⭐⭐ 高 | **复杂度**: 高 | **依赖**: 本地 STT

## 概述

实现类似于真人的连续对话体验。用户唤醒一次后，可以进行多轮对话，无需每次重复唤醒词。系统自动判断用户说话结束，并支持在播报时打断（Barge-in）。

## 核心特性

1. **自动端点检测 (VAD)**：智能判断用户何时开始说话，何时说完。
2. **打断 (Barge-in)**：助手说话时，用户可以直接插嘴打断。
3. **回声消除 (AEC)**：滤除设备扬声器发出的声音，确保打断功能可用且不误触发。
4. **自动超时**：无交互一段时间后自动退出对话模式。

## 技术方案

### 1. 音频输入管道 (AEC 核心)

使用 Android 原生机制实现硬件级回声消除。

```kotlin
val bufferSize = AudioRecord.getMinBufferSize(...)
val recorder = AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION, // 关键：开启系统 AEC/NS/AGC
    16000,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize
)

// 检查 AEC 是否可用
if (AcousticEchoCanceler.isAvailable()) {
    val aec = AcousticEchoCanceler.create(recorder.audioSessionId)
    aec.enabled = true
}
```

**优势**:
- **低功耗**: 利用 DSP 硬件加速。
- **简单**: 无需集成复杂的 WebRTC 库。
- **资源预留**: 节省 CPU 资源给本地 LLM 和 STT。

### 2. 语音活动检测 (VAD)

**选型**: **Silero VAD** (ONNX Runtime)

**逻辑**:
- **Chunk**: 每 30ms-64ms 分析一次音频块。
- **Speech Start**: 连续 N 个块概率 > 0.5 → 开始录音。
- **Speech End**: 连续 M 个块概率 < 0.3 (静音时长 > 0.8s) → 停止录音，发送识别。
- **No Speech**: 连续 L 秒 (如 10s) 无语音 → 退出对话模式。

### 3. 对话状态机

```mermaid
graph TD
    IDLE[休眠/待唤醒] -->|唤醒词| LISTENING
    
    subgraph Conversation Loop
        LISTENING[监听中 (VAD)] -->|检测到说话| RECORDING[录音中]
        RECORDING -->|检测到静音| PROCESSING[识别/思考]
        PROCESSING -->|生成回复| SPEAKING[TTS 播报]
        
        SPEAKING -->|播放完毕| LISTENING
        SPEAKING -->|VAD检测到说话| RECORDING  -->|打断| RECORDING
    end
    
    LISTENING -->|超时无语音| IDLE
```

## 实现任务

- [ ] 验证 `VOICE_COMMUNICATION` 在目标设备上的 AEC 效果
- [ ] 集成 `Silero VAD` (ONNX)
- [ ] 实现 `ConversationManager` 状态机
- [ ] 实现 Barge-in 逻辑（检测到新语音立即停止当前 TTS）
- [ ] 优化 VAD 参数（阈值、静音时长）以适应中文语速
- [ ] 添加 UI 反馈（监听中、思考中、说话中不同的表情/光效）

## 文件结构

```
app/src/main/java/com/nabukey/
├── audio/
│   ├── AudioRecorder.kt         # 支持 AEC 的录音器
│   └── VadDetector.kt           # Silero VAD 封装
├── conversation/
│   ├── ConversationManager.kt   # 核心状态机
│   └── ConversationState.kt     # 状态定义
```

## 注意事项

1. **音频焦点**: 使用 `VOICE_COMMUNICATION` 会占用麦克风，需处理好音频焦点，避免被其他应用打断。
2. **音量控制**: 确保 TTS 音量足够大，但不要大到导致硬件 AEC 失效（虽然硬件 AEC 通常很强）。
3. **模型冲突**: 如果同时运行 STT、LLM 和 VAD，需确内存充足（当前设备 12GB 目前看来没问题）。
4. **隐私**: 确保在 IDLE 状态下不进行录音或仅进行低功耗唤醒词检测。

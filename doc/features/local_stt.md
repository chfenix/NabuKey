# 本地语音识别 (Local STT) 集成方案

本文档详细记录了在 NabuKey 中集成离线语音识别 (Sherpa-ONNX + SenseVoice) 的技术方案、架构决策及部署指南。

## 1. 方案概述

为了减少对云端或 Home Assistant 服务器算力的依赖，并为未来的完全离线交互做准备，我们集成了 **Sherpa-ONNX** 引擎，运行 **SenseVoice-Small-ONNX** 模型进行端侧中文语音识别。

目前采用了 **双路并行 (Dual-Stack)** 架构：
1.  **本地识别**：手机端实时转录音频，用于日志验证、实时字幕及本地意图匹配。
2.  **远程执行**：同时将音频流发送给 Home Assistant (Assist Pipeline)，由服务器进行再次识别和意图执行。

## 2. 核心架构与决策

### 2.1 为什么采用双路架构？(技术限制说明)

在集成过程中，我们遇到了 ESPHome Native API 的协议限制：
*   **限制**：ESPHome 的 `VoiceAssistantRequest` (Protobuf 消息) 仅支持启动语音会话，不支持客户端直接注入文本 (`text`) 负载。
*   **现象**：如果我们仅发送本地识别的文本而不发送音频，Home Assistant 将无法收到有效的输入流，导致管道挂起或超时。
*   **决策**：为了保证现有的 Home Assistant 语音助手功能（如控制家电）正常工作，我们保留了音频流发送逻辑。本地 STT 目前作为“增强层”运行，其识别结果主要用于验证和未来的本地功能扩展，而不直接驱动 HA 的意图引擎。

### 2.2 核心组件变化

#### A. 引擎封装 (`LocalSTT.kt`)
*   **位置**: `app/src/main/java/com/nabukey/stt/LocalSTT.kt`
*   **职责**:
    *   封装 Sherpa-ONNX 的 `OfflineRecognizer`。
    *   管理模型文件路径 (`/sdcard/Android/data/.../files/models/`).
    *   提供 `initialize()` 和 `transcribe(floatArray)` 接口。
*   **注入方式**: 由于 Hilt/KSP 对 K2FSA 库的解析可能存在问题，我们采用了**手动依赖注入**模式，而非 `@Inject`。

#### B. 管道处理 (`VoicePipeline.kt`)
*   **修改点**: `processMicAudio` 和 `handleEvent`。
*   **逻辑**:
    1.  当 VAD (Voice Activity Detection) 激活时，音频数据被同时放入 `micAudioBuffer` (用于发送给 HA) 和 `localSttAudioBuffer` (用于本地推理)。
    2.  当收到 HA 发来的 `VOICE_ASSISTANT_STT_VAD_END` 事件时，触发本地 `performLocalTranscription`。
    3.  将缓冲的音频块合并、转换为 FloatArray (归一化)，通过 `LocalSTT` 进行推理。
    4.  输出识别日志 `Local STT Recognized (Local Only): ...`。

#### C. 服务管理 (`VoiceSatelliteService.kt`)
*   **职责**: 负责 `LocalSTT` 实例的生命周期。
*   **初始化**: 在 `onCreate` 中，利用 `lifecycleScope.launch` 异步调用 `localSTT.initialize()`，防止阻塞主线程。

## 3. 部署与模型管理

### 3.1 依赖库 (JNI)
为了支持 Sherpa-ONNX，以下原生库已被放入项目：
*   `app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so`
*   `app/src/main/jniLibs/arm64-v8a/libonnxruntime.so`
*   (以及 `armeabi-v7a`, `x86`, `x86_64` 对应的库文件)

### 3.2 模型文件部署 (SenseVoice)
模型文件较大，**不存放在 Git 仓库中**。部署流程如下：

1.  **下载模型**:
    *   下载 `SenseVoice-Small-ONNX` 模型包。
    *   需要文件: `model.int8.onnx` 和 `tokens.txt`。

2.  **本地放置**:
    *   在项目根目录创建 `local_models/` 文件夹。
    *   将上述两个文件放入该目录。
    *   *注：此目录已在 `.gitignore` 中排除。*

3.  **自动同步**:
    *   执行 `./gradlew installDebug` 时，Gradle 脚本（如配置了 `pushSttModels` 任务）会将 `local_models/` 下的文件推送到设备的 `/sdcard/Android/data/com.nabukey/files/models/` 目录。
    *   `LocalSTT` 初始化时会自动从该目录加载模型。

## 4. 关键代码片段 (防踩坑)

**VoicePipeline.kt - Buffer 处理与推理触发**
```kotlin
// 1. 必须保留音频发送给 HA，否则 HA 不会执行指令
sendMessage(voiceAssistantAudio { data = audio })

// 2. 本地 Buffer 用于离线识别
if (localSTT != null) {
    localSttAudioBuffer.add(audio)
}

// 3. 在 VAD 结束时进行推理
if (eventType == VOICE_ASSISTANT_STT_VAD_END) {
    GlobalScope.launch { // 使用协程进行耗时推理
        // ... FloatBuffer 转换逻辑 ...
        val result = stt.transcribe(floatArray)
        Log.e(TAG, "Local STT Recognized: ${result.text}")
    }
}
```

**VoiceSatelliteService.kt - 手动初始化**
```kotlin
// 避免使用 @Inject，防止 KSP 编译错误
private lateinit var localSTT: LocalSTT

override fun onCreate() {
    super.onCreate()
    localSTT = LocalSTT(applicationContext, satelliteSettingsStore)
    // 必须在协程中初始化，因为加载模型是耗时操作
    lifecycleScope.launch {
        localSTT.initialize()
    }
}
```

## 5. 常见问题排查

*   **编译报错 (KSP/Hilt)**: 如果遇到 `LocalSTT could not be resolved`，请确保移除 `VoiceSatelliteService` 中该字段的 `@Inject` 注解，改用手动 `new`。
*   **初始化失败**: 检查日志 `LocalSTT`，如果是模型未找到，请确认 `local_models` 目录文件是否存在，以及应用是否具有存储权限（通常无需额外权限，因使用 App 私有目录）。
*   **识别为空**: 确保麦克风输入的音频格式为 16kHz, 16-bit PCM。SenseVoice 对采样率敏感。

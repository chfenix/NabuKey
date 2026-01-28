# NabuKey 项目开发规则

## 项目概述

NabuKey 是一个基于 Android 的 Home Assistant 语音助手应用，使用 ESPHome 协议与 Home Assistant 通信。

## 技术栈

### 核心技术
- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **依赖注入**: Hilt (Dagger)
- **构建系统**: Gradle 8.13 (Kotlin DSL)
- **最低 SDK**: API 26 (Android 8.0)
- **目标 SDK**: API 36
- **编译 SDK**: API 36

### 关键依赖
- **Compose BOM**: 2026.01.00
- **Kotlin**: 2.3.0
- **AGP (Android Gradle Plugin)**: 8.12.3
- **协程**: kotlinx-coroutines
- **序列化**: kotlinx-serialization
- **Protobuf**: 用于 ESPHome 协议通信
- **TensorFlow Lite**: 用于本地唤醒词检测

## 开发环境要求

### 必需工具
1. **JDK 17**: 项目强制要求 JDK 17
   - 配置路径: `D:\Programs\Java\jdk-17.0.12`
   - 已在 `gradle.properties` 中配置: `org.gradle.java.home`

2. **Android SDK**:
   - SDK 路径: `D:\Programs\Android\Sdk`
   - 必需组件:
     - `platform-tools`
     - `build-tools;34.0.0`
     - `platforms;android-34`

3. **ADB**:
   - 路径: `D:\Programs\adb\adb.exe`
   - 测试设备 ID: `cd2215ea`

### 环境变量 (重要)

虽然 `gradle.properties` 配置了编译用的 JDK，但 **启动 Gradle 包装器 (gradlew) 本身需要系统环境中有 Java**。在此环境中，建议在执行命令前临时设置变量：

```powershell
# 1. 必需：用于启动 Gradle Wrapper
$env:JAVA_HOME = "D:\Programs\Java\jdk-17.0.12"

# 2. 必需：如果项目根目录没有 local.properties 文件，必须设置此项
$env:ANDROID_HOME = "D:\Programs\Android\Sdk"
```

## 项目结构

```
NabuKey/
├── app/                          # 主应用模块
│   ├── src/main/java/com/nabukey/
│   │   ├── MainActivity.kt       # 主 Activity
│   │   ├── NabuKeyApplication.kt # Application 类
│   │   ├── audio/                # 音频输入处理
│   │   ├── esphome/              # ESPHome 协议实现
│   │   ├── services/             # 前台服务
│   │   ├── settings/             # 设置数据存储
│   │   ├── ui/                   # UI 组件和屏幕
│   │   └── wakewords/            # 唤醒词检测
│   └── build.gradle.kts
├── esphomeproto/                 # ESPHome Protobuf 定义
├── microfeatures/                # 音频特征提取 (JNI/C++)
├── gradle.properties             # Gradle 配置
└── settings.gradle.kts
```

## 包名和命名空间

- **应用 ID**: `com.nabukey`
- **命名空间**: `com.nabukey`
- **应用名称**: NabuKey
- **主题**: `Theme.NabuKey`

## 编译和发布流程

### 1. 环境准备与清理

**首次编译前，必须确保 Android SDK 路径被识别**。

方法 A (推荐)：创建 `local.properties`
```powershell
echo "sdk.dir=D\:\\Programs\\Android\\Sdk" > local.properties
```

方法 B：设置环境变量
```powershell
$env:ANDROID_HOME = "D:\Programs\Android\Sdk"
```

### 2. 清理和编译 Debug 版本

```powershell
# 设置 JAVA_HOME 以启动 gradlew，然后编译
$env:JAVA_HOME = "D:\Programs\Java\jdk-17.0.12"; ./gradlew clean assembleDebug
```

**输出位置**: `app/build/outputs/apk/debug/NabuKey-0.0.0-debug.apk`

### 2. 卸载旧版本（可选，推荐）

```powershell
D:\Programs\adb\adb.exe -s cd2215ea uninstall com.nabukey
```

### 3. 安装到设备

```powershell
D:\Programs\adb\adb.exe -s cd2215ea install -r app/build/outputs/apk/debug/NabuKey-0.0.0-debug.apk
```

### 4. 启动应用

```powershell
D:\Programs\adb\adb.exe -s cd2215ea shell am start -n com.nabukey/com.nabukey.MainActivity
```

### 完整部署脚本

```powershell
# 一键部署脚本

# 0. 环境准备
$env:JAVA_HOME = "D:\Programs\Java\jdk-17.0.12"
if (!(Test-Path local.properties)) {
    echo "sdk.dir=D\:\\Programs\\Android\\Sdk" > local.properties
}

# 1. 卸载旧版本
D:\Programs\adb\adb.exe -s cd2215ea uninstall com.nabukey

# 2. 清理编译
./gradlew clean assembleDebug

# 3. 安装并启动
D:\Programs\adb\adb.exe -s cd2215ea install -r app/build/outputs/apk/debug/NabuKey-0.0.0-debug.apk
D:\Programs\adb\adb.exe -s cd2215ea shell am start -n com.nabukey/com.nabukey.MainActivity
```

## 常见问题和注意事项

### 1. 图标资源
- 图标位置: `app/src/main/res/mipmap-*/ic_launcher.png`
- Play Store 图标: `app/src/main/ic_launcher-playstore.png`
- **注意**: 已删除 `mipmap-anydpi-v26` 目录以避免图标缓存问题

### 2. MAC 地址固定
为了开发调试方便，避免每次重装应用时 Home Assistant 创建新的集成，在 `VoiceSatelliteSettings.kt` 中使用了固定的 MAC 地址：
```kotlin
if (it.macAddress == DEFAULT_MAC_ADDRESS) it.copy(macAddress = "02:00:00:00:00:01") else it
```

### 3. Gradle 配置
- Gradle 版本: 8.13
- 配置文件: `gradle.properties` 中已设置 JDK 路径
- 如果遇到 JDK 版本问题，检查 `org.gradle.java.home` 配置

### 4. 原生库编译
`microfeatures` 模块包含 C++ 代码，使用 CMake 编译：
- 支持架构: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`
- 首次编译会较慢，后续增量编译会快很多

## 开发调试

### 查看日志
```powershell
D:\Programs\adb\adb.exe -s cd2215ea logcat -s NabuKey
```

### 清除应用数据
```powershell
D:\Programs\adb\adb.exe -s cd2215ea shell pm clear com.nabukey
```

### 检查应用是否安装
```powershell
D:\Programs\adb\adb.exe -s cd2215ea shell pm list packages | Select-String nabukey
```

## Git 工作流

### 重要规则
**不要自动提交代码到 GitHub**。只有在用户明确要求"提交到 GitHub"、"push 到远程"或类似指令时，才执行 `git commit` 和 `git push` 操作。

### 提交代码
```bash
git add .
git commit -m "描述性的提交信息"
git push
```

### 远程仓库
- GitHub: https://github.com/chfenix/NabuKey.git
- 主分支: main

## License

本项目使用 **Apache License 2.0**，是 [Ava](https://github.com/brownard/Ava) 项目的衍生作品。

## 重要提醒

1. **始终使用 JDK 17**，其他版本会导致编译失败
2. **编译前设置环境变量**，必须确保 `JAVA_HOME` 指向 JDK 17，且 `local.properties` 存在 (或设置 `ANDROID_HOME`)
3. **卸载旧版本后再安装**，避免签名冲突和缓存问题
4. **首次编译会下载大量依赖**，需要稳定的网络连接
5. **C++ 代码修改后需要 clean**，否则可能不会重新编译

## 流程规范

### 1. 编译验证 (必需)
**在完成代码修改后，必须立即执行编译命令以验证代码正确性。**
- 不得跳过编译直接尝试安装或提交。
- 验证命令: `$env:JAVA_HOME = "D:\Programs\Java\jdk-17.0.12"; ./gradlew assembleDebug`

### 2. 代码审查 (必需)
**在完成任何 Android 代码的编写或修改后，必须使用 `@[requesting-code-review]` Skill。**
- 不得跳过审查直接提交。
- 审查必须基于下方的 "Android 架构和质量规范" 进行严格检查。

## Android 架构和质量规范

在代码审查和开发过程中，必须严格遵守以下规范：

### 1. 架构设计 (Clean Architecture + MVVM)
- **UI Layer (Presentation)**:
  - 仅负责 UI 渲染和用户交互。
  - **ViewModel**: 必须承载 UI 状态 (StateFlow/State) 和业务逻辑调用。
  - **State Hoisting**: 可复用的 Composable 函数不得包含 ViewModel，状态必须提升。
  - **Unidirectional Data Flow (UDF)**: 事件向上流动 (UI -> ViewModel)，数据向下流动 (ViewModel -> UI)。
- **Domain Layer**:
  - 纯 Kotlin 代码，不依赖 Android SDK。
  - 包含 UseCases 和 Repository 接口。
- **Data Layer**:
  - 负责数据获取 (API, Database, Proto)。
  - 实现 Repository 接口。
  - 使用 DataSource 分离本地和远程数据源。

### 2. Jetpack Compose 规范
- **状态管理**: 优先使用 `StateFlow` 或 `Compose State`。
- **Side Effects**: 正确使用 `LaunchedEffect`, `DisposableEffect`，避免在 Composable 中直接执行副作用。
- **性能优化**: 使用 `remember`, `derivedStateOf` 避免不必要的重组。
- **Modifiers**: 遵循调用顺序（先布局后绘制），参数通过 Modifier 传递。

### 3. 并发处理 (Coroutines & Flow)
- **禁用**: 禁止使用 `Thread`, `AsyncTask`, `RxJava`。
- **Scope**: 必须使用 `viewModelScope` 或 `lifecycleScope`，严禁使用 `GlobalScope`。
- **Dispatcher**: 耗时操作必须切换到 `Dispatchers.IO`。

### 4. 依赖注入 (Hilt)
- 全面使用 Hilt 进行依赖注入。
- 避免手动实例化 Repository 或 ViewModel。
- 正确使用 `@Singleton`, `@ViewModelScoped` 等作用域注解。

### 5. 代码质量与资源
- **硬编码**: 严禁硬编码字符串 (使用 `strings.xml`) 和尺寸 (使用 `dimens.xml`)。
- **魔术数字**: 禁止魔术数字，必须定义常量。
- **命名规范**: 严格遵循 Kotlin 官方编码规范。
- **错误处理**: 这里的错误必须被捕获并给用户友好的提示，严禁 App 崩溃。

### 6. 权限与安全
- 动态申请权限，处理“拒绝”和“不再询问”的情况。
- 敏感数据必须加密存储。

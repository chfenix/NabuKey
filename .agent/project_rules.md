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

### 环境变量
在执行 Gradle 命令前，需要设置以下环境变量：
```powershell
$env:JAVA_HOME = "D:\Programs\Java\jdk-17.0.12"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
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

### 1. 清理和编译 Debug 版本

```powershell
# 设置环境变量
$env:JAVA_HOME = "D:\Programs\Java\jdk-17.0.12"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:ANDROID_HOME = "D:\Programs\Android\Sdk"

# 清理并编译
./gradlew clean assembleDebug
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
$env:JAVA_HOME = "D:\Programs\Java\jdk-17.0.12"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:ANDROID_HOME = "D:\Programs\Android\Sdk"

# 卸载旧版本
D:\Programs\adb\adb.exe -s cd2215ea uninstall com.nabukey

# 清理编译
./gradlew clean assembleDebug

# 安装并启动
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
2. **编译前设置环境变量**，特别是 `JAVA_HOME` 和 `ANDROID_HOME`
3. **卸载旧版本后再安装**，避免签名冲突和缓存问题
4. **首次编译会下载大量依赖**，需要稳定的网络连接
5. **C++ 代码修改后需要 clean**，否则可能不会重新编译

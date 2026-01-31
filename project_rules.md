# NabuKey Project Rules & Development Guidelines

## 1. Local STT Model Management (Sherpa-ONNX)

Due to the large size of speech recognition models, we adopt an **External Injection Strategy**.

### **Rule 1: No Large Models in Git**
- **Do NOT** commit `.onnx` model files to the Git repository.
- The `local_models/` directory is valid in `.gitignore`.

### **Rule 2: Developer Setup Requirement**
- **Action A (Models)**: Download **SenseVoice-Small-ONNX** models.
  - Create `local_models/` in project root.
  - Place `.onnx` and `tokens.txt` inside.
- **Action B (Library)**: Download **sherpa-onnx-android AAR**.
  - Download `sherpa-onnx-{version}-android.tar.bz2` from [Sherpa-ONNX Releases](https://github.com/k2-fsa/sherpa-onnx/releases).
  - Extract `sherpa-onnx.aar` (rename if needed).
  - Place it in **`app/libs/`** directory.

### Rule 3: Automated Device Deployment
- The `pushSttModels` task is configured to **automatically run** after `installDebug`.
- **Workflow**:
    1. Place models in `local_models/` (One-time setup).
    2. Run `./gradlew installDebug` in terminal.
    3. The APK installs, and models are automatically synced to `/sdcard/Android/data/com.nabukey/files/models/`.
    4. **No manual adb push required.**

## 2. Compilation Environment

### JDK Configuration
- **Java Version**: JDK 17
- **Path**: `D:\Programs\Java\jdk-17.0.12`
- **Configuration**: Ensure `local.properties` contains `java.home=D\:\\Programs\\Java\\jdk-17.0.12`.

### Build Commands
- **Install & Deploy Models**:
  ```bash
  ./gradlew installDebug
  ```
  (This assumes `local_models` folder is populated as per Rule 2)

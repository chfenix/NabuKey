@echo off
setlocal

:: Config area
set JAVA_HOME=D:\Programs\Java\jdk-17.0.12
set ADB_PATH=D:\Programs\adb\adb.exe
set DEVICE_ID=cd2215ea
set PACKAGE_NAME=com.nabukey
set MAIN_ACTIVITY=com.nabukey.MainActivity
set APK_PATH=app\build\outputs\apk\debug\NabuKey-0.0.0-debug.apk

echo [1/4] Compiling Debug APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed! Please check your code.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/4] Uninstalling old version (Device: %DEVICE_ID%)...
"%ADB_PATH%" -s %DEVICE_ID% uninstall %PACKAGE_NAME%

echo.
echo [3/4] Installing new version...
"%ADB_PATH%" -s %DEVICE_ID% install -r "%APK_PATH%"
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Install failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [4/4] Starting application...
"%ADB_PATH%" -s %DEVICE_ID% shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%

echo.
echo ==========================================
echo [SUCCESS] Build and Deployed!
echo ==========================================
pause

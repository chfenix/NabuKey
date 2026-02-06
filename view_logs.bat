@echo off
echo ============================================
echo   NabuKey Presence Detector Log Viewer
echo ============================================
echo.
echo Searching for PresenceDetector logs...
echo (Press Ctrl+C to stop)
echo.

:: Using -v time to show timestamps
:: Using findstr for filtering to ensure it works on vanilla Windows
adb logcat -v time | findstr PresenceDetector

pause

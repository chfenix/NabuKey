@echo off
chcp 65001
echo ============================================
echo   NabuKey Presence Detector Log Viewer
echo ============================================
echo.
echo Searching for PresenceDetector logs...
echo (Press Ctrl+C to stop)
echo.

:: Using -v time to show timestamps
:: Using findstr to show PresenceDetector, VoiceSatelliteService, and VoicePipeline logs
adb logcat -v time | findstr /C:"PresenceDetector" /C:"VoiceSatellite" /C:"VoicePipeline" /C:"HAConnection" /C:"Microphone" /C:"Sherpa" /C:"VadDetector"

pause

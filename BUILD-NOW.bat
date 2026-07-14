@echo off
cd /d "%~dp0"
echo === Build Windows Agent ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0windows-agent\build-agent.ps1"
if errorlevel 1 goto error
echo.
echo === Build Android APK ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0android-app\build-apk.ps1"
if errorlevel 1 goto error
echo.
echo Build hoan tat. Xem thu muc dist.
pause
exit /b 0
:error
echo.
echo Build that bai. Xem loi phia tren.
pause
exit /b 1

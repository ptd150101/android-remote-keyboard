@echo off
cd /d "%~dp0"
if exist "RemoteKeyAgent.exe" (
  RemoteKeyAgent.exe
  goto :eof
)
where py >nul 2>nul
if %errorlevel%==0 (
  py -3 RemoteKeyAgent.py
  goto :eof
)
where python >nul 2>nul
if %errorlevel%==0 (
  python RemoteKeyAgent.py
  goto :eof
)
echo Khong tim thay Python va cung chua co RemoteKeyAgent.exe.
echo Hay cai Python 3.11+ hoac chay build-agent.ps1.
pause

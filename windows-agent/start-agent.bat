@echo off
cd /d "%~dp0"

if exist "RemoteKeyAgent.exe" (
  RemoteKeyAgent.exe
  goto :eof
)

where uv >nul 2>nul
if %errorlevel%==0 (
  uv run python RemoteKeyAgentEntry.py
  goto :eof
)

echo uv was not found and RemoteKeyAgent.exe does not exist.
echo Run build-agent.ps1 once to install uv and build the EXE.
pause

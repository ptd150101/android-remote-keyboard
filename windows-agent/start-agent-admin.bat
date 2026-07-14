@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -Verb RunAs -WorkingDirectory '%~dp0' -FilePath 'cmd.exe' -ArgumentList '/c','""%~dp0start-agent.bat""'"

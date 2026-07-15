$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $Root
$OutputRoot = Join-Path $ProjectRoot "dist\windows-agent"
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

if (Get-Command py -ErrorAction SilentlyContinue) {
    $PythonExe = "py"
    $PythonPrefix = @("-3")
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    $PythonExe = "python"
    $PythonPrefix = @()
} else {
    throw "Python 3 was not found. Install Python 3 and run this script again."
}

Push-Location $Root
try {
    & $PythonExe @PythonPrefix -m pip install --upgrade pyinstaller
    if ($LASTEXITCODE -ne 0) { throw "Failed to install PyInstaller." }

    & $PythonExe @PythonPrefix -m PyInstaller `
        --noconfirm `
        --clean `
        --onefile `
        --console `
        --name RemoteKeyAgent `
        RemoteKeyAgent.py
    if ($LASTEXITCODE -ne 0) { throw "Failed to build the Windows EXE." }

    Copy-Item -Force "dist\RemoteKeyAgent.exe" $OutputRoot
    Copy-Item -Force "agent_config.json", "start-agent.bat", "start-agent-admin.bat", "add-firewall-rule.ps1" $OutputRoot
} finally {
    Pop-Location
}

Write-Host "Created: $OutputRoot\RemoteKeyAgent.exe" -ForegroundColor Green

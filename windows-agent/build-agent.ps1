$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $Root
$OutputRoot = Join-Path $ProjectRoot "dist\windows-agent"
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

function Resolve-Uv {
    $command = Get-Command uv -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    Write-Host "uv was not found. Installing uv..." -ForegroundColor Yellow

    $previousNoModifyPath = $env:UV_NO_MODIFY_PATH
    $env:UV_NO_MODIFY_PATH = "1"
    try {
        $installer = Invoke-RestMethod -UseBasicParsing -Uri "https://astral.sh/uv/install.ps1"
        Invoke-Expression $installer
    } finally {
        if ($null -eq $previousNoModifyPath) {
            Remove-Item Env:UV_NO_MODIFY_PATH -ErrorAction SilentlyContinue
        } else {
            $env:UV_NO_MODIFY_PATH = $previousNoModifyPath
        }
    }

    $candidates = @(
        "$env:USERPROFILE\.local\bin\uv.exe",
        "$env:USERPROFILE\.cargo\bin\uv.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    $command = Get-Command uv -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "uv installation completed, but uv.exe could not be found. Open a new terminal and run this script again."
}

$UvExe = Resolve-Uv
Write-Host "Using uv: $UvExe"

Push-Location $Root
try {
    & $UvExe sync
    if ($LASTEXITCODE -ne 0) { throw "uv sync failed." }

    & $UvExe run --no-sync pyinstaller `
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

$ErrorActionPreference = "Stop"
$Config = Get-Content (Join-Path $PSScriptRoot "agent_config.json") -Raw | ConvertFrom-Json
$Port = [int]$Config.port
$RuleName = "RemoteKey Agent TCP $Port"

if (-not ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Start-Process powershell -Verb RunAs -ArgumentList @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", "`"$PSCommandPath`""
    )
    exit
}

Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule `
    -DisplayName $RuleName `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $Port `
    -Profile Private

Write-Host "Đã cho phép TCP port $Port trên mạng Private." -ForegroundColor Green
pause

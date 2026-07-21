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

# Use netsh instead of the NetSecurity PowerShell module. Some Windows
# installations have a missing/broken MSFT_NetFirewallRule CIM class, which
# makes New-NetFirewallRule fail with HRESULT 0x80041010 (Invalid class).
& netsh.exe advfirewall firewall delete rule `
    "name=$RuleName" `
    protocol=TCP `
    "localport=$Port" | Out-Null

& netsh.exe advfirewall firewall add rule `
    "name=$RuleName" `
    dir=in `
    action=allow `
    protocol=TCP `
    "localport=$Port" `
    profile=private `
    enable=yes

if ($LASTEXITCODE -ne 0) {
    throw "Failed to add the Windows Firewall rule. netsh exit code: $LASTEXITCODE"
}

Write-Host "Allowed TCP port $Port on Private networks." -ForegroundColor Green
pause

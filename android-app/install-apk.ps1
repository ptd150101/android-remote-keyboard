$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Split-Path -Parent $ProjectRoot
$Apk = Join-Path $Root "dist\RemoteKey-debug.apk"
if (-not (Test-Path $Apk)) { throw "Chưa có APK. Hãy chạy build-apk.ps1 trước." }

$SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } `
    elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } `
    else { "$env:LOCALAPPDATA\Android\Sdk" }
$Adb = Join-Path $SdkRoot "platform-tools\adb.exe"
if (-not (Test-Path $Adb)) { throw "Không tìm thấy adb tại $Adb" }

& $Adb devices
& $Adb install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "Cài APK thất bại." }
Write-Host "Đã cài RemoteKey lên thiết bị." -ForegroundColor Green

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Split-Path -Parent $ProjectRoot
$Apk = Join-Path $Root "dist\RemoteKey-debug.apk"
if (-not (Test-Path $Apk)) { throw "APK not found. Run build-apk.ps1 first." }

$SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } `
    elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } `
    else { "$env:LOCALAPPDATA\Android\Sdk" }
$Adb = Join-Path $SdkRoot "platform-tools\adb.exe"
if (-not (Test-Path $Adb)) { throw "adb was not found at $Adb" }

& $Adb devices
& $Adb install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "Failed to install the APK." }
Write-Host "RemoteKey was installed on the Android device." -ForegroundColor Green

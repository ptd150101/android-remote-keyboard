$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ToolsRoot = Join-Path $ProjectRoot ".tools"
$DistRoot = Join-Path (Split-Path -Parent $ProjectRoot) "dist"
New-Item -ItemType Directory -Force -Path $ToolsRoot, $DistRoot | Out-Null

function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return $env:JAVA_HOME
    }

    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:ProgramFiles\Android\Android Studio\jre",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) { return $candidate }
    }
    throw "Java was not found. Install Android Studio or JDK 17 or newer."
}

function Resolve-AndroidSdk {
    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        "$env:LOCALAPPDATA\Android\Sdk"
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }

    $sdk = "$env:LOCALAPPDATA\Android\Sdk"
    New-Item -ItemType Directory -Force -Path $sdk | Out-Null
    return $sdk
}

$env:JAVA_HOME = Resolve-JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$SdkRoot = Resolve-AndroidSdk
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_HOME = $SdkRoot

$SdkManager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $SdkManager)) {
    Write-Host "Downloading Android command-line tools..."
    $zip = Join-Path $ToolsRoot "commandlinetools.zip"
    Invoke-WebRequest -UseBasicParsing `
        -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" `
        -OutFile $zip

    $extract = Join-Path $ToolsRoot "cmdline-extract"
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $extract
    Expand-Archive -Force $zip $extract
    $latest = Join-Path $SdkRoot "cmdline-tools\latest"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $latest) | Out-Null
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $latest
    Move-Item (Join-Path $extract "cmdline-tools") $latest
}

Write-Host "Installing required Android SDK packages..."
1..20 | ForEach-Object { "y" } | & $SdkManager --licenses | Out-Null
& $SdkManager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
if ($LASTEXITCODE -ne 0) { throw "Failed to install Android SDK packages." }

$escapedSdk = $SdkRoot.Replace("\", "\\")
Set-Content -Encoding ASCII -Path (Join-Path $ProjectRoot "local.properties") -Value "sdk.dir=$escapedSdk"

$GradleHome = Join-Path $ToolsRoot "gradle-8.9"
$GradleExe = Join-Path $GradleHome "bin\gradle.bat"
if (-not (Test-Path $GradleExe)) {
    Write-Host "Downloading Gradle 8.9..."
    $gradleZip = Join-Path $ToolsRoot "gradle-8.9-bin.zip"
    Invoke-WebRequest -UseBasicParsing `
        -Uri "https://services.gradle.org/distributions/gradle-8.9-bin.zip" `
        -OutFile $gradleZip
    Expand-Archive -Force $gradleZip $ToolsRoot
}

Push-Location $ProjectRoot
try {
    & $GradleExe --no-daemon :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }
} finally {
    Pop-Location
}

$apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
$output = Join-Path $DistRoot "RemoteKey-debug.apk"
Copy-Item -Force $apk $output
Write-Host ""
Write-Host "Created APK: $output" -ForegroundColor Green

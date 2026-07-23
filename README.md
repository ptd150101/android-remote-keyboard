# RemoteKey

RemoteKey is a small Android-to-Windows keyboard shortcut bridge designed to run alongside remote desktop and remote gaming apps such as Parsec, Steam Link, and StarDesk.

It does **not** relay normal typing. Letters, numbers, text editing, and ordinary shortcuts stay on the remote app's native input path. RemoteKey only intercepts selected system shortcuts that Android may otherwise handle locally, then forwards them to a Windows agent.

## Supported shortcuts

The current shortcut-only mode supports:

- `Alt + Tab`
- `Windows + E`
- `Windows + Tab`

> Some Android vendors, especially HyperOS/MIUI devices, may consume the physical Windows/Meta key before an accessibility service can receive it. In that case, Windows-based shortcuts may remain device-dependent even though `Alt + Tab` works.

## Why shortcut-only mode?

Forwarding every keystroke through a second TCP connection can add unnecessary latency, create duplicate input, and interfere with the remote app's own keyboard pipeline.

RemoteKey therefore uses two paths:

```text
Normal typing and ordinary shortcuts
Physical keyboard -> remote desktop app -> Windows

Selected system shortcuts
Physical keyboard -> Android AccessibilityService
                  -> TCP JSON Lines
                  -> RemoteKeyAgent on Windows
                  -> Win32 SendInput
```

## Repository layout

```text
android-app/                 Native Android app written in Kotlin
windows-agent/               Windows receiver and SendInput injector
tools/test_client.py         Protocol test client
dist/                        Build output directory
BUILD-NOW.bat                Builds both the Windows agent and Android APK
```

## Requirements

### Android

- Android 8.0 or newer
- A physical Bluetooth or USB keyboard
- Accessibility permission for RemoteKey
- USB debugging when installing with ADB

### Windows

- Windows 10 or Windows 11
- Administrator access is recommended when controlling elevated applications
- Python 3.11 to 3.14 only when running or building the agent from source

### Network

The Android device must be able to reach the Windows PC over:

- the same LAN/Wi-Fi network, or
- a trusted private VPN such as Tailscale

RemoteKey uses an unencrypted TCP connection with token authentication. Do not expose its port directly to the public Internet.

## Quick start

### 1. Clone the repository

```powershell
git clone https://github.com/ptd150101/android-remote-keyboard.git
cd android-remote-keyboard
```

### 2. Build the APK and Windows agent

From the repository root:

```powershell
.\BUILD-NOW.bat
```

Build outputs:

```text
dist/
├── RemoteKey-debug.apk
└── windows-agent/
    ├── RemoteKeyAgent.exe
    ├── agent_config.json
    ├── start-agent.bat
    ├── start-agent-admin.bat
    └── add-firewall-rule.ps1
```

### 3. Configure the Windows agent

Edit:

```text
dist/windows-agent/agent_config.json
```

Example:

```json
{
  "listen_host": "0.0.0.0",
  "port": 45892,
  "token": "replace-this-with-a-private-token",
  "verbose": false
}
```

Change the default token before using RemoteKey on any shared network.

### 4. Add the Windows Firewall rule

Open PowerShell as Administrator and run:

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\dist\windows-agent\add-firewall-rule.ps1
```

The script opens the configured TCP port for the Windows Private network profile.

### 5. Start the Windows agent

```powershell
.\dist\windows-agent\start-agent-admin.bat
```

The console should display values similar to:

```text
LAN IP: 192.168.1.20
Listening on 0.0.0.0:45892
Token: your-private-token
Mode: Windows SendInput
```

Keep this window open while using RemoteKey.

### 6. Install the Android APK

With USB debugging enabled:

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\android-app\install-apk.ps1
```

Or install directly with ADB:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r -t --no-streaming ".\dist\RemoteKey-debug.apk"
```

### 7. Configure the Android app

1. Open **RemoteKey**.
2. Enter the Windows PC IP address, port, and the same token from `agent_config.json`.
3. Tap **Save configuration**.
4. Open Android Accessibility settings and enable **RemoteKey keyboard relay**.
5. Return to the app and enable **Relay special shortcuts to PC**.
6. Open your remote desktop or remote gaming app.

Normal typing should continue through the remote app. Only the supported special shortcuts should be handled by RemoteKey.

## Xiaomi / HyperOS setup notes

Sideloaded apps may be blocked from enabling accessibility services.

Open:

```text
Settings -> Apps -> Manage apps -> RemoteKey -> More
```

Then enable:

```text
Allow restricted settings
```

After that, open Accessibility settings and enable **RemoteKey keyboard relay**.

When installing through ADB, HyperOS may also require these Developer options:

```text
USB debugging
Install via USB
USB debugging (Security settings), when available
```

## Build the Windows agent only

The Windows project uses `uv` for dependency management. `PyInstaller` is a development dependency; the packaged executable does not require Python or `uv` on the target machine.

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\windows-agent\build-agent.ps1
```

Output:

```text
dist/windows-agent/RemoteKeyAgent.exe
```

The build script installs `uv` automatically when it is not already available.

## Run the Windows agent from source

```powershell
cd .\windows-agent
uv sync
uv run python .\RemoteKeyAgentEntry.py
```

Use the fixed entry point above rather than starting `RemoteKeyAgent.py` directly.

To control applications running as Administrator, launch the terminal or agent with Administrator privileges.

## Build the Android APK only

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\android-app\build-apk.ps1
```

Output:

```text
dist/RemoteKey-debug.apk
```

The script uses the JDK bundled with Android Studio or `JAVA_HOME`, prepares the required Android SDK components, and builds a debug-signed APK.

## Emergency stop

Hold:

```text
Ctrl + Alt + Shift + F12
```

RemoteKey disables shortcut capture and sends `release_all` to the Windows agent to reduce the risk of a stuck modifier key.

## Testing the protocol

Run the agent without injecting input:

```powershell
cd .\windows-agent
uv run python .\RemoteKeyAgentEntry.py --dry-run --verbose
```

In another terminal, from the repository root:

```powershell
uv run --project .\windows-agent python .\tools\test_client.py
```

The agent should log the expected key-down and key-up sequence.

## Troubleshooting

### `INSTALL_FAILED_USER_RESTRICTED`

Unlock the Android device, approve the installation prompt, and enable **Install via USB** in Developer options when available.

### Accessibility says `App was denied access`

Open the RemoteKey app information page and enable **Allow restricted settings**, then try enabling the accessibility service again.

### Android connects, but the agent logs `SendInput failed` with error 87

Pull the latest code and rebuild the Windows agent. Older builds used an incorrect native `INPUT` structure size on 64-bit Windows.

### The Android app cannot connect

Check that:

- the Windows agent is still running;
- the IP address, port, and token match;
- the PC firewall allows the configured TCP port;
- the Android device can reach the selected LAN or VPN IP;
- another Android client is not already connected to the agent.

### Normal keys do not appear in the RemoteKey agent log

This is expected. Normal keyboard input intentionally bypasses RemoteKey and remains on the remote app's native input path.

### `Windows + E` or `Windows + Tab` still opens something on Android

The device firmware may reserve the Windows/Meta key before RemoteKey receives it. This is an Android vendor limitation, not a Windows agent or network error.

## Technical limitations

- `Ctrl + Alt + Delete` is the Windows Secure Attention Sequence and cannot be generated with `SendInput`.
- The Windows secure desktop used by UAC may reject injected input.
- Running the agent as Administrator helps with ordinary elevated applications but does not bypass the secure desktop.
- Android firmware may reserve Power, Home, Meta/Windows, or vendor-specific system keys.
- The protocol is currently unencrypted. Use it only on a trusted LAN or private VPN.

## Logs

The Windows agent writes logs next to the executable:

```text
RemoteKeyAgent.log
```

Enable verbose logging in `agent_config.json` when debugging:

```json
{
  "verbose": true
}
```

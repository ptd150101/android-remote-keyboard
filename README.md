# RemoteKey

RemoteKey is a small Android-to-Windows keyboard shortcut bridge for people who use an Android tablet as a remote desktop or remote gaming client.

It is designed to run alongside apps such as Parsec, Steam Link, StarDesk, Moonlight, or similar software.

## What problem does this solve?

When a physical Bluetooth or USB keyboard is connected to an Android tablet, normal typing is usually forwarded correctly by the remote desktop app. However, Android or the device firmware may intercept operating-system shortcuts before the remote app can send them to the Windows PC.

Typical examples include:

- `Alt + Tab` switching Android applications instead of Windows applications
- `Windows + E` being handled locally instead of opening File Explorer on the PC
- `Windows + Tab` opening Android recent apps instead of Windows Task View

This makes a tablet-based remote Windows session feel incomplete: text input works, but important desktop shortcuts do not reliably reach the computer.

## What does RemoteKey do?

RemoteKey adds a second, shortcut-only input path:

1. An Android accessibility service watches the physical keyboard.
2. It intercepts only selected system shortcuts.
3. The shortcut events are sent over TCP to a small Windows agent.
4. The Windows agent recreates the shortcut with Win32 `SendInput`.

Normal typing does **not** pass through RemoteKey. Letters, numbers, text editing, and ordinary shortcuts continue to use the remote desktop app's native keyboard pipeline.

```text
Normal typing and ordinary shortcuts
Physical keyboard -> remote desktop app -> Windows

Selected system shortcuts
Physical keyboard -> Android AccessibilityService
                  -> TCP JSON Lines
                  -> RemoteKeyAgent on Windows
                  -> Win32 SendInput
```

## Project goal

The goal is not to replace the keyboard support built into Parsec, Steam Link, StarDesk, or other remote software.

The goal is to complement those applications by forwarding the small set of desktop shortcuts that Android would otherwise consume locally, while keeping normal input on the lowest-latency path provided by the remote app.

## Typical use case

A common setup looks like this:

```text
Bluetooth/USB keyboard
        |
        v
Android tablet running a remote desktop app
        |
        +-- normal keys ----------> remote app ----------> Windows PC
        |
        +-- selected shortcuts ---> RemoteKey Android ---> RemoteKeyAgent
                                                           |
                                                           v
                                                     Windows SendInput
```

For example, a Redmi Pad Pro can be used as a portable Windows terminal while the actual PC remains at home or in another room. RemoteKey handles the missing desktop shortcuts without relaying every keystroke through an additional connection.

## What RemoteKey is not

RemoteKey is not:

- a remote desktop application
- a screen streaming application
- a full keyboard-over-network replacement
- a public Internet relay service
- a way to bypass Windows secure desktop or Android firmware restrictions

You still need a remote desktop or remote gaming application for video, audio, mouse input, and normal keyboard input.

## Supported shortcuts

The current shortcut-only mode supports:

- `Alt + Tab`
- `Windows + E`
- `Windows + Tab`

> Some Android vendors, especially HyperOS/MIUI devices, may consume the physical Windows/Meta key before an accessibility service can receive it. In that case, Windows-based shortcuts remain device-dependent even though `Alt + Tab` works.

## Why shortcut-only mode?

Forwarding every key through a second TCP connection would be unnecessary and could:

- add extra input latency
- create duplicate keystrokes
- interfere with the remote application's own keyboard handling
- produce ordering problems while typing quickly

RemoteKey therefore relays only the shortcuts that need help. All other keys return immediately to Android so the active remote application can process them normally.

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

- the same LAN or Wi-Fi network, or
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

Change the default token before using RemoteKey on a shared network.

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
6. Open the remote desktop or remote gaming application.

Normal typing should continue through the remote app. Only the supported special shortcuts should be handled by RemoteKey.

## Xiaomi / HyperOS setup notes

Sideloaded applications may be blocked from enabling accessibility services.

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

Use `RemoteKeyAgentEntry.py` rather than starting `RemoteKeyAgent.py` directly.

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

- the Windows agent is still running
- the IP address, port, and token match
- the PC firewall allows the configured TCP port
- the Android device can reach the selected LAN or VPN IP
- another Android client is not already connected to the agent

### Normal keys do not appear in the RemoteKey agent log

This is expected. Normal keyboard input intentionally bypasses RemoteKey and remains on the remote application's native input path.

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

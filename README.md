# RemoteKey

RemoteKey chuyển sự kiện bàn phím vật lý Bluetooth/USB từ Android sang máy tính Windows qua mạng LAN. Nó được thiết kế để dùng song song với Parsec, Steam Link, StarDesk hoặc phần mềm remote gaming khác.

## Thành phần

- `android-app/`: dự án APK native Kotlin.
- `windows-agent/`: agent Windows nhận phím và gọi Win32 `SendInput`.
- `tools/test_client.py`: kiểm thử giao thức mà không cần Android.
- `dist/`: nơi script build chép APK và EXE vào.

## Luồng hoạt động

```text
Bàn phím Bluetooth/USB
        ↓
AccessibilityService trên Android
        ↓ TCP LAN, JSON Lines
RemoteKeyAgent trên Windows
        ↓ SendInput
Windows / game / ứng dụng đang remote
```

## Cài và chạy agent Windows

### Cách nhanh, chưa cần đóng gói EXE

1. Cài Python 3.11 trở lên.
2. Mở `windows-agent/agent_config.json` và đổi `token`.
3. Chạy `windows-agent/start-agent.bat`.
4. Khi Windows Firewall hỏi, chỉ cho phép mạng **Private**.
5. Agent sẽ in địa chỉ `LAN IP`, cổng và token ra cửa sổ.

Nên chạy `start-agent-admin.bat` nếu cần điều khiển ứng dụng đang chạy quyền Administrator.

### Đóng gói thành EXE

Nhấp chuột phải `windows-agent/build-agent.ps1` → **Run with PowerShell**, hoặc chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\windows-agent\build-agent.ps1
```

Kết quả:

```text
dist/windows-agent/RemoteKeyAgent.exe
```

Script chỉ dùng PyInstaller lúc build. File EXE sau đó không cần cài Python.

### Mở firewall thủ công

Chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\windows-agent\add-firewall-rule.ps1
```

## Build APK

Trên Windows, chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\android-app\build-apk.ps1
```

Script sẽ:

1. Dùng JDK đi kèm Android Studio hoặc `JAVA_HOME`.
2. Tự tải Android command-line tools nếu máy chưa có SDK.
3. Cài Android Platform 35 và Build Tools 35.0.0.
4. Tải Gradle 8.9.
5. Build debug APK đã ký sẵn bằng debug key.

Kết quả:

```text
dist/RemoteKey-debug.apk
```

Có thể cài bằng cách chép APK sang Android, hoặc bật USB debugging rồi chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\android-app\install-apk.ps1
```

## Cấu hình Android

1. Mở RemoteKey.
2. Nhập `LAN IP` của PC, cổng `45892`, và token giống `agent_config.json`.
3. Bấm **Lưu cấu hình**.
4. Bấm **Mở cài đặt Trợ năng** và bật dịch vụ RemoteKey.
5. Quay lại app, bật **Chuyển toàn bộ bàn phím vật lý sang PC**.
6. Mở Parsec, Steam Link hoặc StarDesk.

## Thoát khẩn cấp

Giữ:

```text
Ctrl + Alt + Shift + F12
```

RemoteKey sẽ tắt capture và gửi `release_all` để tránh kẹt Ctrl, Alt, Shift hoặc Windows trên PC.

## Các tổ hợp đã hỗ trợ trong keymap

- Alt+Tab, Alt+F4.
- Windows, Windows+D, Windows+E và các tổ hợp Windows thông thường.
- Ctrl/Alt/Shift trái và phải.
- F1–F12.
- Insert, Delete, Home, End, Page Up, Page Down, phím mũi tên.
- Numpad cơ bản.
- Phím âm lượng và media phổ biến.
- Chữ cái, số và dấu câu bàn phím US tiêu chuẩn.

Phím không có trong keymap sẽ được ghi vào `RemoteKeyAgent.log` dưới dạng `Unsupported Android keyCode=...` để bổ sung dễ dàng.

## Giới hạn kỹ thuật

- `Ctrl+Alt+Delete` là Secure Attention Sequence của Windows và không thể tạo bằng `SendInput`.
- Màn hình UAC secure desktop có thể không nhận input. Chạy agent Administrator chỉ giúp với ứng dụng elevated thông thường, không vượt secure desktop.
- Một số firmware Android có thể giữ lại Power, Home hoặc phím hệ thống riêng trước khi AccessibilityService nhận được.
- Dữ liệu hiện dùng TCP không mã hóa, có token xác thực. Chỉ dùng trong LAN và không mở port ra Internet.

## Kiểm thử giao thức

Chạy agent ở chế độ không inject phím:

```powershell
python .\windows-agent\RemoteKeyAgent.py --dry-run --verbose
```

Mở terminal khác:

```powershell
python .\tools\test_client.py
```

Agent phải ghi thứ tự:

```text
Left Alt DOWN
Tab DOWN
Tab UP
Left Alt UP
```

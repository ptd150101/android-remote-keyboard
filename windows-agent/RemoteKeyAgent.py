from __future__ import annotations

import argparse
import ctypes
import hmac
import json
import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path
import platform
import socket
import socketserver
import sys
import threading
from dataclasses import dataclass
from typing import Any, Optional

PROTOCOL_VERSION = 1
DEFAULT_PORT = 45892
DEFAULT_TOKEN = "remotekey-123456"

# Windows virtual-key constants.
VK_BACK = 0x08
VK_TAB = 0x09
VK_CLEAR = 0x0C
VK_RETURN = 0x0D
VK_PAUSE = 0x13
VK_CAPITAL = 0x14
VK_ESCAPE = 0x1B
VK_SPACE = 0x20
VK_PRIOR = 0x21
VK_NEXT = 0x22
VK_END = 0x23
VK_HOME = 0x24
VK_LEFT = 0x25
VK_UP = 0x26
VK_RIGHT = 0x27
VK_DOWN = 0x28
VK_SNAPSHOT = 0x2C
VK_INSERT = 0x2D
VK_DELETE = 0x2E
VK_LWIN = 0x5B
VK_RWIN = 0x5C
VK_APPS = 0x5D
VK_SLEEP = 0x5F
VK_NUMPAD0 = 0x60
VK_MULTIPLY = 0x6A
VK_ADD = 0x6B
VK_SEPARATOR = 0x6C
VK_SUBTRACT = 0x6D
VK_DECIMAL = 0x6E
VK_DIVIDE = 0x6F
VK_F1 = 0x70
VK_NUMLOCK = 0x90
VK_SCROLL = 0x91
VK_LSHIFT = 0xA0
VK_RSHIFT = 0xA1
VK_LCONTROL = 0xA2
VK_RCONTROL = 0xA3
VK_LMENU = 0xA4
VK_RMENU = 0xA5
VK_BROWSER_BACK = 0xA6
VK_BROWSER_FORWARD = 0xA7
VK_BROWSER_REFRESH = 0xA8
VK_BROWSER_STOP = 0xA9
VK_BROWSER_SEARCH = 0xAA
VK_BROWSER_FAVORITES = 0xAB
VK_BROWSER_HOME = 0xAC
VK_VOLUME_MUTE = 0xAD
VK_VOLUME_DOWN = 0xAE
VK_VOLUME_UP = 0xAF
VK_MEDIA_NEXT_TRACK = 0xB0
VK_MEDIA_PREV_TRACK = 0xB1
VK_MEDIA_STOP = 0xB2
VK_MEDIA_PLAY_PAUSE = 0xB3
VK_LAUNCH_MAIL = 0xB4
VK_OEM_1 = 0xBA
VK_OEM_PLUS = 0xBB
VK_OEM_COMMA = 0xBC
VK_OEM_MINUS = 0xBD
VK_OEM_PERIOD = 0xBE
VK_OEM_2 = 0xBF
VK_OEM_3 = 0xC0
VK_OEM_4 = 0xDB
VK_OEM_5 = 0xDC
VK_OEM_6 = 0xDD
VK_OEM_7 = 0xDE


@dataclass(frozen=True)
class KeySpec:
    vk: int
    extended: bool = False
    name: str = ""


STATIC_KEY_MAP: dict[int, KeySpec] = {
    4: KeySpec(VK_BROWSER_BACK, True, "Browser Back"),
    19: KeySpec(VK_UP, True, "Up"),
    20: KeySpec(VK_DOWN, True, "Down"),
    21: KeySpec(VK_LEFT, True, "Left"),
    22: KeySpec(VK_RIGHT, True, "Right"),
    24: KeySpec(VK_VOLUME_UP, True, "Volume Up"),
    25: KeySpec(VK_VOLUME_DOWN, True, "Volume Down"),
    28: KeySpec(VK_CLEAR, False, "Clear"),
    55: KeySpec(VK_OEM_COMMA, False, "Comma"),
    56: KeySpec(VK_OEM_PERIOD, False, "Period"),
    57: KeySpec(VK_LMENU, False, "Left Alt"),
    58: KeySpec(VK_RMENU, True, "Right Alt"),
    59: KeySpec(VK_LSHIFT, False, "Left Shift"),
    60: KeySpec(VK_RSHIFT, False, "Right Shift"),
    61: KeySpec(VK_TAB, False, "Tab"),
    62: KeySpec(VK_SPACE, False, "Space"),
    64: KeySpec(VK_BROWSER_HOME, True, "Browser Home"),
    65: KeySpec(VK_LAUNCH_MAIL, True, "Mail"),
    66: KeySpec(VK_RETURN, False, "Enter"),
    67: KeySpec(VK_BACK, False, "Backspace"),
    68: KeySpec(VK_OEM_3, False, "Grave"),
    69: KeySpec(VK_OEM_MINUS, False, "Minus"),
    70: KeySpec(VK_OEM_PLUS, False, "Equals"),
    71: KeySpec(VK_OEM_4, False, "Left Bracket"),
    72: KeySpec(VK_OEM_6, False, "Right Bracket"),
    73: KeySpec(VK_OEM_5, False, "Backslash"),
    74: KeySpec(VK_OEM_1, False, "Semicolon"),
    75: KeySpec(VK_OEM_7, False, "Apostrophe"),
    76: KeySpec(VK_OEM_2, False, "Slash"),
    81: KeySpec(VK_OEM_PLUS, False, "Plus"),
    82: KeySpec(VK_APPS, True, "Menu"),
    84: KeySpec(VK_BROWSER_SEARCH, True, "Browser Search"),
    85: KeySpec(VK_MEDIA_PLAY_PAUSE, True, "Media Play/Pause"),
    86: KeySpec(VK_MEDIA_STOP, True, "Media Stop"),
    87: KeySpec(VK_MEDIA_NEXT_TRACK, True, "Media Next"),
    88: KeySpec(VK_MEDIA_PREV_TRACK, True, "Media Previous"),
    91: KeySpec(VK_VOLUME_MUTE, True, "Mute"),
    92: KeySpec(VK_PRIOR, True, "Page Up"),
    93: KeySpec(VK_NEXT, True, "Page Down"),
    111: KeySpec(VK_ESCAPE, False, "Escape"),
    112: KeySpec(VK_DELETE, True, "Delete"),
    113: KeySpec(VK_LCONTROL, False, "Left Ctrl"),
    114: KeySpec(VK_RCONTROL, True, "Right Ctrl"),
    115: KeySpec(VK_CAPITAL, False, "Caps Lock"),
    116: KeySpec(VK_SCROLL, False, "Scroll Lock"),
    117: KeySpec(VK_LWIN, True, "Left Windows"),
    118: KeySpec(VK_RWIN, True, "Right Windows"),
    120: KeySpec(VK_SNAPSHOT, True, "Print Screen"),
    121: KeySpec(VK_PAUSE, False, "Break"),
    122: KeySpec(VK_HOME, True, "Home"),
    123: KeySpec(VK_END, True, "End"),
    124: KeySpec(VK_INSERT, True, "Insert"),
    125: KeySpec(VK_BROWSER_FORWARD, True, "Browser Forward"),
    126: KeySpec(VK_MEDIA_PLAY_PAUSE, True, "Media Play"),
    127: KeySpec(VK_PAUSE, False, "Pause"),
    143: KeySpec(VK_NUMLOCK, True, "Num Lock"),
    154: KeySpec(VK_DIVIDE, True, "Numpad Divide"),
    155: KeySpec(VK_MULTIPLY, False, "Numpad Multiply"),
    156: KeySpec(VK_SUBTRACT, False, "Numpad Subtract"),
    157: KeySpec(VK_ADD, False, "Numpad Add"),
    158: KeySpec(VK_DECIMAL, False, "Numpad Decimal"),
    159: KeySpec(VK_SEPARATOR, False, "Numpad Separator"),
    160: KeySpec(VK_RETURN, True, "Numpad Enter"),
    161: KeySpec(VK_OEM_PLUS, False, "Numpad Equals"),
    164: KeySpec(VK_VOLUME_MUTE, True, "Volume Mute"),
    223: KeySpec(VK_SLEEP, True, "Sleep"),
}


def resolve_key(android_key_code: int) -> Optional[KeySpec]:
    # Android KEYCODE_0..KEYCODE_9 = 7..16.
    if 7 <= android_key_code <= 16:
        number = android_key_code - 7
        return KeySpec(0x30 + number, False, str(number))

    # Android KEYCODE_A..KEYCODE_Z = 29..54.
    if 29 <= android_key_code <= 54:
        letter_index = android_key_code - 29
        return KeySpec(0x41 + letter_index, False, chr(0x41 + letter_index))

    # Android KEYCODE_F1..KEYCODE_F12 = 131..142.
    if 131 <= android_key_code <= 142:
        number = android_key_code - 130
        return KeySpec(VK_F1 + number - 1, False, f"F{number}")

    # Android KEYCODE_NUMPAD_0..KEYCODE_NUMPAD_9 = 144..153.
    if 144 <= android_key_code <= 153:
        number = android_key_code - 144
        return KeySpec(VK_NUMPAD0 + number, False, f"Numpad {number}")

    return STATIC_KEY_MAP.get(android_key_code)


class KeyboardInjector:
    def __init__(self, dry_run: bool = False) -> None:
        self.dry_run = dry_run or platform.system() != "Windows"
        self._pressed: dict[int, KeySpec] = {}
        self._lock = threading.RLock()
        self._user32: Any = None
        self._INPUT: Any = None
        self._KEYBDINPUT: Any = None

        if not self.dry_run:
            self._initialize_win32()

    def _initialize_win32(self) -> None:
        from ctypes import wintypes

        ULONG_PTR = ctypes.c_size_t

        class KEYBDINPUT(ctypes.Structure):
            _fields_ = [
                ("wVk", wintypes.WORD),
                ("wScan", wintypes.WORD),
                ("dwFlags", wintypes.DWORD),
                ("time", wintypes.DWORD),
                ("dwExtraInfo", ULONG_PTR),
            ]

        class INPUT_UNION(ctypes.Union):
            _fields_ = [("ki", KEYBDINPUT)]

        class INPUT(ctypes.Structure):
            _anonymous_ = ("u",)
            _fields_ = [("type", wintypes.DWORD), ("u", INPUT_UNION)]

        user32 = ctypes.WinDLL("user32", use_last_error=True)
        user32.SendInput.argtypes = (
            wintypes.UINT,
            ctypes.POINTER(INPUT),
            ctypes.c_int,
        )
        user32.SendInput.restype = wintypes.UINT
        user32.MapVirtualKeyW.argtypes = (wintypes.UINT, wintypes.UINT)
        user32.MapVirtualKeyW.restype = wintypes.UINT

        self._user32 = user32
        self._INPUT = INPUT
        self._KEYBDINPUT = KEYBDINPUT

    def send_android_key(self, android_key_code: int, is_down: bool, repeat_count: int = 0) -> bool:
        spec = resolve_key(android_key_code)
        if spec is None:
            logging.warning("Unsupported Android keyCode=%s", android_key_code)
            return False

        with self._lock:
            self._emit(spec, is_down)
            if is_down:
                self._pressed[android_key_code] = spec
            else:
                self._pressed.pop(android_key_code, None)

        logging.debug(
            "%s %s (Android=%d VK=0x%02X repeat=%d)",
            "DOWN" if is_down else "UP",
            spec.name,
            android_key_code,
            spec.vk,
            repeat_count,
        )
        return True

    def release_all(self) -> None:
        with self._lock:
            if not self._pressed:
                return
            logging.info("Releasing %d pressed key(s)", len(self._pressed))
            for android_key_code, spec in reversed(list(self._pressed.items())):
                try:
                    self._emit(spec, False)
                except Exception:
                    logging.exception("Failed releasing Android keyCode=%d", android_key_code)
            self._pressed.clear()

    def _emit(self, spec: KeySpec, is_down: bool) -> None:
        if self.dry_run:
            logging.info("DRY-RUN %s %s", "DOWN" if is_down else "UP", spec.name)
            return

        INPUT_KEYBOARD = 1
        KEYEVENTF_EXTENDEDKEY = 0x0001
        KEYEVENTF_KEYUP = 0x0002
        MAPVK_VK_TO_VSC = 0

        flags = 0
        if spec.extended:
            flags |= KEYEVENTF_EXTENDEDKEY
        if not is_down:
            flags |= KEYEVENTF_KEYUP

        scan_code = self._user32.MapVirtualKeyW(spec.vk, MAPVK_VK_TO_VSC)
        keyboard = self._KEYBDINPUT(
            wVk=spec.vk,
            wScan=scan_code,
            dwFlags=flags,
            time=0,
            dwExtraInfo=0,
        )
        input_event = self._INPUT(type=INPUT_KEYBOARD)
        input_event.ki = keyboard

        sent = self._user32.SendInput(1, ctypes.byref(input_event), ctypes.sizeof(self._INPUT))
        if sent != 1:
            error = ctypes.get_last_error()
            raise OSError(error, f"SendInput failed for {spec.name}")


class AgentState:
    def __init__(self, token: str, injector: KeyboardInjector) -> None:
        self.token = token
        self.injector = injector
        self.client_lock = threading.Lock()


class RemoteKeyHandler(socketserver.StreamRequestHandler):
    MAX_LINE = 64 * 1024

    @property
    def state(self) -> AgentState:
        return self.server.state  # type: ignore[attr-defined]

    def handle(self) -> None:
        peer = f"{self.client_address[0]}:{self.client_address[1]}"
        if not self.state.client_lock.acquire(blocking=False):
            self._send({"type": "hello", "ok": False, "error": "Agent đang có thiết bị khác kết nối"})
            return

        authenticated = False
        try:
            hello = self._read_json()
            if hello is None or hello.get("type") != "hello":
                self._send({"type": "hello", "ok": False, "error": "Thiếu gói hello"})
                return

            supplied_token = str(hello.get("token", ""))
            if not hmac.compare_digest(supplied_token, self.state.token):
                logging.warning("Rejected client %s: invalid token", peer)
                self._send({"type": "hello", "ok": False, "error": "Sai mã kết nối"})
                return

            protocol_version = int(hello.get("protocol", 0))
            if protocol_version != PROTOCOL_VERSION:
                self._send({"type": "hello", "ok": False, "error": "Sai phiên bản giao thức"})
                return

            authenticated = True
            device = str(hello.get("device", "Android"))
            logging.info("Connected: %s from %s", device, peer)
            self._send({"type": "hello", "ok": True, "protocol": PROTOCOL_VERSION})
            self.state.injector.release_all()

            while True:
                message = self._read_json()
                if message is None:
                    break

                message_type = message.get("type")
                if message_type == "ping":
                    continue
                if message_type == "release_all":
                    self.state.injector.release_all()
                    continue
                if message_type != "key":
                    logging.warning("Unknown packet type from %s: %r", peer, message_type)
                    continue

                action = message.get("action")
                if action not in ("down", "up"):
                    logging.warning("Invalid key action from %s: %r", peer, action)
                    continue

                try:
                    key_code = int(message["keyCode"])
                    repeat_count = int(message.get("repeatCount", 0))
                except (KeyError, TypeError, ValueError):
                    logging.warning("Invalid key packet from %s: %r", peer, message)
                    continue

                self.state.injector.send_android_key(
                    android_key_code=key_code,
                    is_down=action == "down",
                    repeat_count=repeat_count,
                )
        except (ConnectionError, OSError) as exc:
            logging.info("Connection ended for %s: %s", peer, exc)
        except Exception:
            logging.exception("Unhandled client error from %s", peer)
        finally:
            if authenticated:
                self.state.injector.release_all()
                logging.info("Disconnected: %s", peer)
            self.state.client_lock.release()

    def _read_json(self) -> Optional[dict[str, Any]]:
        raw = self.rfile.readline(self.MAX_LINE + 1)
        if not raw:
            return None
        if len(raw) > self.MAX_LINE:
            raise ValueError("Packet too large")
        return json.loads(raw.decode("utf-8"))

    def _send(self, payload: dict[str, Any]) -> None:
        data = (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        self.wfile.write(data)
        self.wfile.flush()


class RemoteKeyServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address: tuple[str, int], state: AgentState):
        self.state = state
        super().__init__(address, RemoteKeyHandler)


def app_directory() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def load_config(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    with path.open("r", encoding="utf-8") as file:
        value = json.load(file)
    if not isinstance(value, dict):
        raise ValueError("agent_config.json must contain a JSON object")
    return value


def configure_logging(directory: Path, verbose: bool) -> None:
    level = logging.DEBUG if verbose else logging.INFO
    formatter = logging.Formatter("%(asctime)s | %(levelname)s | %(message)s")

    console = logging.StreamHandler()
    console.setFormatter(formatter)

    log_file = RotatingFileHandler(
        directory / "RemoteKeyAgent.log",
        maxBytes=2_000_000,
        backupCount=3,
        encoding="utf-8",
    )
    log_file.setFormatter(formatter)

    logging.basicConfig(level=level, handlers=[console, log_file])


def find_lan_ip() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("8.8.8.8", 80))
        return str(probe.getsockname()[0])
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "127.0.0.1"
    finally:
        probe.close()


def is_windows_admin() -> Optional[bool]:
    if platform.system() != "Windows":
        return None
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return None


def parse_args(config: dict[str, Any]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Receive Android keyboard events and inject them into Windows.")
    parser.add_argument("--host", default=config.get("listen_host", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(config.get("port", DEFAULT_PORT)))
    parser.add_argument("--token", default=str(config.get("token", DEFAULT_TOKEN)))
    parser.add_argument("--dry-run", action="store_true", default=bool(config.get("dry_run", False)))
    parser.add_argument("--verbose", action="store_true", default=bool(config.get("verbose", False)))
    return parser.parse_args()


def main() -> int:
    directory = app_directory()
    config_path = directory / "agent_config.json"

    try:
        config = load_config(config_path)
        args = parse_args(config)
        configure_logging(directory, args.verbose)
    except Exception as exc:
        print(f"Configuration error: {exc}", file=sys.stderr)
        return 2

    if not 1 <= args.port <= 65535:
        logging.error("Port must be between 1 and 65535")
        return 2
    if not args.token:
        logging.error("Token must not be empty")
        return 2

    injector = KeyboardInjector(dry_run=args.dry_run)
    state = AgentState(token=args.token, injector=injector)

    try:
        server = RemoteKeyServer((args.host, args.port), state)
    except OSError as exc:
        logging.error("Cannot listen on %s:%d: %s", args.host, args.port, exc)
        return 1

    admin = is_windows_admin()
    logging.info("RemoteKey Agent protocol v%d", PROTOCOL_VERSION)
    logging.info("LAN IP: %s", find_lan_ip())
    logging.info("Listening on %s:%d", args.host, args.port)
    logging.info("Token: %s", args.token)
    logging.info("Mode: %s", "DRY-RUN" if injector.dry_run else "Windows SendInput")
    if admin is False:
        logging.warning("Agent is not running as Administrator; elevated apps may reject injected input.")
    logging.info("Press Ctrl+C in this window to stop the agent.")

    try:
        server.serve_forever(poll_interval=0.25)
    except KeyboardInterrupt:
        logging.info("Stopping...")
    finally:
        injector.release_all()
        server.shutdown()
        server.server_close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

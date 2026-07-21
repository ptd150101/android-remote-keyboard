from __future__ import annotations

import ctypes
import logging

import RemoteKeyAgent as core


def initialize_win32(self: core.KeyboardInjector) -> None:
    """Initialize Win32 input structures with the exact native INPUT layout.

    INPUT's union must be large enough for MOUSEINPUT, even when only keyboard
    events are sent. Defining the union with KEYBDINPUT alone makes INPUT 32
    bytes on 64-bit Windows instead of the required 40 bytes, causing
    SendInput to fail with ERROR_INVALID_PARAMETER (87).
    """
    from ctypes import wintypes

    ULONG_PTR = ctypes.c_size_t

    class MOUSEINPUT(ctypes.Structure):
        _fields_ = [
            ("dx", wintypes.LONG),
            ("dy", wintypes.LONG),
            ("mouseData", wintypes.DWORD),
            ("dwFlags", wintypes.DWORD),
            ("time", wintypes.DWORD),
            ("dwExtraInfo", ULONG_PTR),
        ]

    class KEYBDINPUT(ctypes.Structure):
        _fields_ = [
            ("wVk", wintypes.WORD),
            ("wScan", wintypes.WORD),
            ("dwFlags", wintypes.DWORD),
            ("time", wintypes.DWORD),
            ("dwExtraInfo", ULONG_PTR),
        ]

    class HARDWAREINPUT(ctypes.Structure):
        _fields_ = [
            ("uMsg", wintypes.DWORD),
            ("wParamL", wintypes.WORD),
            ("wParamH", wintypes.WORD),
        ]

    class INPUT_UNION(ctypes.Union):
        _fields_ = [
            ("mi", MOUSEINPUT),
            ("ki", KEYBDINPUT),
            ("hi", HARDWAREINPUT),
        ]

    class INPUT(ctypes.Structure):
        _anonymous_ = ("u",)
        _fields_ = [
            ("type", wintypes.DWORD),
            ("u", INPUT_UNION),
        ]

    expected_input_size = 40 if ctypes.sizeof(ctypes.c_void_p) == 8 else 28
    actual_input_size = ctypes.sizeof(INPUT)
    if actual_input_size != expected_input_size:
        raise RuntimeError(
            f"Invalid Win32 INPUT size: got {actual_input_size}, "
            f"expected {expected_input_size}"
        )

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
    logging.debug("Win32 INPUT size: %d bytes", actual_input_size)


core.KeyboardInjector._initialize_win32 = initialize_win32


if __name__ == "__main__":
    raise SystemExit(core.main())

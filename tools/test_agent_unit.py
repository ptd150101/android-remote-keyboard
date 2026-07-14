from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import socket
import threading
import time
import unittest

MODULE_PATH = Path(__file__).resolve().parents[1] / "windows-agent" / "RemoteKeyAgent.py"
spec = importlib.util.spec_from_file_location("remote_key_agent", MODULE_PATH)
assert spec and spec.loader
agent = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = agent
spec.loader.exec_module(agent)


class RecordingInjector(agent.KeyboardInjector):
    def __init__(self) -> None:
        super().__init__(dry_run=True)
        self.events: list[tuple[int, bool]] = []

    def send_android_key(self, android_key_code: int, is_down: bool, repeat_count: int = 0) -> bool:
        self.events.append((android_key_code, is_down))
        return super().send_android_key(android_key_code, is_down, repeat_count)


class KeyMapTests(unittest.TestCase):
    def test_common_shortcut_keys(self) -> None:
        expected = {
            57: agent.VK_LMENU,
            61: agent.VK_TAB,
            117: agent.VK_LWIN,
            113: agent.VK_LCONTROL,
            59: agent.VK_LSHIFT,
            131: agent.VK_F1,
            142: agent.VK_F1 + 11,
            29: ord("A"),
            54: ord("Z"),
            7: ord("0"),
            16: ord("9"),
        }
        for android_code, windows_vk in expected.items():
            with self.subTest(android_code=android_code):
                resolved = agent.resolve_key(android_code)
                self.assertIsNotNone(resolved)
                self.assertEqual(resolved.vk, windows_vk)

    def test_numpad_enter_is_extended(self) -> None:
        resolved = agent.resolve_key(160)
        self.assertIsNotNone(resolved)
        self.assertTrue(resolved.extended)


class ProtocolTests(unittest.TestCase):
    def test_alt_tab_sequence(self) -> None:
        injector = RecordingInjector()
        state = agent.AgentState("secret", injector)
        server = agent.RemoteKeyServer(("127.0.0.1", 0), state)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()

        try:
            with socket.create_connection(server.server_address, timeout=2) as sock:
                reader = sock.makefile("rb")
                writer = sock.makefile("wb")

                def send(payload):
                    writer.write((json.dumps(payload) + "\n").encode())
                    writer.flush()

                send({"type": "hello", "token": "secret", "device": "test", "protocol": 1})
                self.assertTrue(json.loads(reader.readline())["ok"])
                for action, key_code in [("down", 57), ("down", 61), ("up", 61), ("up", 57)]:
                    send({"type": "key", "action": action, "keyCode": key_code})
                send({"type": "release_all"})
                deadline = time.monotonic() + 1.0
                while len(injector.events) < 4 and time.monotonic() < deadline:
                    time.sleep(0.01)

            self.assertEqual(injector.events, [(57, True), (61, True), (61, False), (57, False)])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()

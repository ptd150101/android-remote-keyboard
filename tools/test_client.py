from __future__ import annotations

import argparse
import json
import socket
import time


def send_line(writer, payload):
    writer.write((json.dumps(payload, separators=(",", ":")) + "\n").encode("utf-8"))
    writer.flush()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=45892)
    parser.add_argument("--token", default="remotekey-123456")
    args = parser.parse_args()

    with socket.create_connection((args.host, args.port), timeout=3) as sock:
        reader = sock.makefile("rb")
        writer = sock.makefile("wb")
        send_line(writer, {
            "type": "hello",
            "token": args.token,
            "device": "Protocol test client",
            "protocol": 1,
        })
        response = json.loads(reader.readline())
        if not response.get("ok"):
            raise RuntimeError(response)

        # Alt+Tab: ALT down, TAB down/up, ALT up.
        for action, key_code in [
            ("down", 57),
            ("down", 61),
            ("up", 61),
            ("up", 57),
        ]:
            send_line(writer, {
                "type": "key",
                "action": action,
                "keyCode": key_code,
                "scanCode": 0,
                "metaState": 0,
                "repeatCount": 0,
                "deviceId": 1,
                "eventTime": int(time.monotonic() * 1000),
            })
            time.sleep(0.05)

        send_line(writer, {"type": "release_all"})
        print("Protocol test passed: Alt+Tab sequence sent.")


if __name__ == "__main__":
    main()

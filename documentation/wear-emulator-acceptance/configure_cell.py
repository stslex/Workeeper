#!/usr/bin/env python3
"""Configure an isolated emulator before a trial; actual Activity configuration is the oracle."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--locale", choices=("en", "ru"), required=True)
    parser.add_argument("--scale", choices=("1.0", "1.24"), required=True)
    parser.add_argument("--package", choices=("io.github.stslex.workeeper", "io.github.stslex.workeeper.dev"),
                        default="io.github.stslex.workeeper.dev")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--screen-timeout-ms", choices=("15000", "120000"), default="120000",
                        help="Use 120000 for interactive captures; 15000 for passive lifecycle comparisons")
    parser.add_argument("--disable-tilt-to-wake", action="store_true",
                        help="Work around a separately recorded emulator wrist-sensor HAL failure")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Only an explicitly selected emulator is permitted")
    if args.output.exists():
        parser.error("Refusing to overwrite a previous setup receipt")
    commands = []

    def adb(*parts):
        command = [str(args.adb), "-s", args.serial, *parts]
        result = subprocess.run(command, capture_output=True, text=True, timeout=45)
        commands.append({"command": command, "stdout": result.stdout,
                         "stderr": result.stderr, "exit_code": result.returncode})
        result.check_returncode()
        return result.stdout.strip()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    receipt = {"requested_locale": args.locale, "requested_scale": float(args.scale),
               "package": args.package, "commands": commands, "status": "BLOCKED",
               "disable_tilt_to_wake": args.disable_tilt_to_wake,
               "requested_screen_timeout_ms": int(args.screen_timeout_ms)}
    try:
        api = int(adb("shell", "getprop", "ro.build.version.sdk"))
        if api not in (30, 36):
            raise RuntimeError(f"Unexpected API {api}")
        receipt["api"] = api
        receipt["before"] = {key: adb("shell", "settings", "get", table, key)
                             for table, key in (("system", "font_scale"), ("global", "ambient_enabled"),
                                                ("system", "screen_off_timeout"),
                                                ("global", "stay_on_while_plugged_in"),
                                                ("global", "ambient_tilt_to_wake"))}
        tag = {"en": "en-US", "ru": "ru-RU"}[args.locale]
        if api >= 33:
            adb("shell", "cmd", "locale", "set-app-locales", args.package,
                "--user", "0", "--locales", tag)
            receipt["app_locales"] = adb("shell", "cmd", "locale", "get-app-locales",
                                         args.package, "--user", "0")
        elif adb("shell", "getprop", "persist.sys.locale") != tag:
            response = adb("root")
            if "cannot run as root" in response:
                raise RuntimeError("API30 requires the official userdebug image for system locale changes")
            adb("wait-for-device")
            adb("shell", "setprop", "persist.sys.locale", tag)
            adb("shell", "stop")
            adb("shell", "start")
            deadline = time.monotonic() + 90
            while True:
                try:
                    if "package:" in adb("shell", "pm", "path", "android"):
                        adb("shell", "settings", "get", "system", "font_scale")
                        break
                except subprocess.CalledProcessError:
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError("Framework did not become available after locale change")
                time.sleep(1)
        adb("shell", "settings", "put", "system", "font_scale", args.scale)
        receipt["battery_before"] = adb("shell", "dumpsys", "battery")
        adb("shell", "dumpsys", "battery", "unplug")
        receipt["battery_after"] = adb("shell", "dumpsys", "battery")
        for source in ("AC", "USB", "Wireless"):
            if not re.search(rf"(?m)^\s*{source} powered: false\s*$", receipt["battery_after"]):
                raise RuntimeError(f"Emulator still reports {source} power after unplug")
        adb("shell", "settings", "put", "global", "ambient_enabled", "1")
        if args.disable_tilt_to_wake:
            adb("shell", "settings", "put", "global", "ambient_tilt_to_wake", "0")
            receipt["tilt_to_wake"] = adb("shell", "settings", "get", "global", "ambient_tilt_to_wake")
            if receipt["tilt_to_wake"] != "0":
                raise RuntimeError("Tilt-to-wake workaround did not take effect")
        adb("shell", "settings", "put", "system", "screen_off_timeout", args.screen_timeout_ms)
        receipt["screen_timeout_ms"] = adb("shell", "settings", "get", "system", "screen_off_timeout")
        if receipt["screen_timeout_ms"] != args.screen_timeout_ms:
            raise RuntimeError("Requested screen timeout did not take effect")
        adb("shell", "svc", "power", "stayon", "false")
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        receipt["display"] = {"size": adb("shell", "wm", "size"),
                              "density": adb("shell", "wm", "density")}
        receipt["status"] = "CONFIGURED_NOT_VERIFIED"
    finally:
        args.output.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")
    print(args.output)


if __name__ == "__main__":
    main()

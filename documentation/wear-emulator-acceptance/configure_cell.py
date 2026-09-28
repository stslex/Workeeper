#!/usr/bin/env python3
"""Configure an isolated emulator before a trial; actual Activity configuration is the oracle."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def locale_configuration_matches(raw: str, tag: str) -> bool:
    declarations = re.findall(r"(?m)^[ \t]*config:[^\r\n]*$", raw)
    if len(declarations) != 1:
        return False
    match = re.fullmatch(r"[ \t]*config: (?:(?:mcc[0-9]+|mnc[0-9]+)-)*(en-rUS|ru-rRU)-[^\r\n]+",
                         declarations[0])
    return bool(match and match[1] == {"en-US": "en-rUS", "ru-RU": "ru-rRU"}[tag])


def locale_needs_reload(setting: str, prop: str, effective: str, tag: str) -> bool:
    return setting.strip() != tag or prop.strip() != tag or not locale_configuration_matches(effective, tag)


def persisted_locale(raw: str) -> str:
    declaration = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
    if not raw.startswith(declaration):
        raise RuntimeError("Missing observed XML declaration from abx2xml")
    body = raw[len(declaration):]
    if "<?" in body or "<!" in body:
        raise RuntimeError("Unexpected declaration or markup in settings fragments")
    try:
        root = ET.fromstring("<persisted-settings>" + body + "</persisted-settings>")
    except ET.ParseError as error:
        raise RuntimeError("Malformed persisted settings fragments") from error
    children = list(root)
    if [node.tag for node in children] not in (["settings"], ["settings", "namespaceHashes"]):
        raise RuntimeError("Expected settings and at most one following namespaceHashes root")
    if (root.text or "").strip() or any((node.tail or "").strip() for node in children):
        raise RuntimeError("Unexpected text outside persisted settings roots")
    settings = children[0]
    if not re.fullmatch(r"[0-9]+", settings.get("version", "")) or (settings.text or "").strip():
        raise RuntimeError("Missing settings version or unexpected settings text")
    entries = list(settings)
    if any(node.tag != "setting" or len(node) or (node.text or "").strip() or (node.tail or "").strip()
           for node in entries):
        raise RuntimeError("Malformed direct setting entries")
    locales = [node for node in entries if node.get("name") == "system_locales"]
    if len(locales) != 1 or "value" not in locales[0].attrib or "valueBase64" in locales[0].attrib:
        raise RuntimeError("Expected one direct system_locales with a plain value")
    value = locales[0].attrib["value"]
    if value not in ("en-US", "ru-RU"):
        raise RuntimeError("Persisted locale is outside the acceptance languages")
    return value


def configure_api30_locale(adb, tag: str, receipt: dict) -> None:
    if adb("shell", "am", "get-current-user") != "0":
        raise RuntimeError("API30 locale setup requires user 0")
    setting = adb("shell", "settings", "--user", "0", "get", "system", "system_locales")
    prop = adb("shell", "getprop", "persist.sys.locale")
    effective = adb("shell", "am", "get-config")
    receipt["locale_before"] = {"system_locales": setting, "property": prop, "effective": effective}
    reload = locale_needs_reload(setting, prop, effective, tag)
    receipt["framework_reload"] = reload
    if reload:
        response = adb("root")
        if "cannot run as root" in response:
            raise RuntimeError("API30 requires the official userdebug image for system locale changes")
        adb("wait-for-device")
        adb("shell", "setprop", "persist.sys.locale", tag)
        adb("shell", "settings", "--user", "0", "put", "system", "system_locales", tag)
        persistence_deadline = time.monotonic() + 90
        while True:
            raw = adb("exec-out", "abx2xml", "/data/system/users/0/settings_system.xml", "-")
            if persisted_locale(raw) == tag:
                receipt["persisted_locale_before_restart"] = tag
                break
            if time.monotonic() >= persistence_deadline:
                raise RuntimeError("Requested locale was not committed; framework was not stopped")
            time.sleep(0.1)
        adb("shell", "stop")
        adb("shell", "start")
        deadline = time.monotonic() + 90
        while True:
            try:
                if locale_configuration_matches(adb("shell", "am", "get-config"), tag):
                    break
            except subprocess.CalledProcessError:
                pass
            if time.monotonic() >= deadline:
                raise RuntimeError("Requested framework locale did not appear after restart")
            time.sleep(1)
    setting = adb("shell", "settings", "--user", "0", "get", "system", "system_locales")
    prop = adb("shell", "getprop", "persist.sys.locale")
    effective = adb("shell", "am", "get-config")
    user = adb("shell", "am", "get-current-user")
    receipt["locale_after"] = {"system_locales": setting, "property": prop, "effective": effective, "user": user}
    if user != "0" or locale_needs_reload(setting, prop, effective, tag):
        raise RuntimeError("Locale setting, property, effective configuration or user disagrees")


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
        else:
            configure_api30_locale(adb, tag, receipt)
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

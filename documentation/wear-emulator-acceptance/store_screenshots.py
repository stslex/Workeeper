#!/usr/bin/env python3
"""Capture the Wear store screenshots from an isolated emulator (wear-release-pipeline.md §7.4).

Prerequisites, as in README "Isolated emulator setup": a round API 36 240dp AVD made by
prepare_avd.py and booted; the store-flavored debug APK installed (`io.github.stslex.workeeper`);
configure_cell.py with --locale en --scale 1.0; POST_NOTIFICATIONS granted. Synthetic data only:
every screen comes from a SyntheticSurfaceFixtures fixture and the acceptance receiver's `refresh`.

    python3 documentation/wear-emulator-acceptance/store_screenshots.py \
        --adb "$ANDROID_SDK_ROOT/platform-tools/adb" --serial emulator-5580 \
        --output fastlane/metadata-wear/android/en-US/images/wearScreenshots

Writes 1_en-US.png .. 4_en-US.png into an empty or new directory: the active controller, its reps
editor, the system Tile and the workout-complete screen. screencap emits RGBA; every pixel must be
opaque, and the alpha channel is then dropped, so the files are RGB PNGs of the same pixels. Prints a
JSON record of the device, the installed package and each file's sha256.
"""

import argparse
import hashlib
import json
import re
import struct
import subprocess
import sys
import time
import zlib
from pathlib import Path

PACKAGE = "io.github.stslex.workeeper"
ACTIVITY = f"{PACKAGE}/{PACKAGE}.wear.MainActivity"
RECEIVER = f"{PACKAGE}/{PACKAGE}.wear.runtime.AcceptanceScenarioReceiver"
TILE_SERVICE = f"{PACKAGE}/{PACKAGE}.wear.tile.WorkoutTileService"
SCREEN_PX = 480
# The reps card's centre on the 480 px controller (the right of the two value cards).
REPS_CARD = (369, 197)
SETTLE_S = 3


class Device:
    def __init__(self, adb, serial):
        self.base = [adb, "-s", serial]

    def shell(self, *args):
        result = subprocess.run(self.base + ["shell", *args], capture_output=True, text=True, check=True)
        return result.stdout.strip()

    def screencap(self):
        return subprocess.run(self.base + ["exec-out", "screencap", "-p"], capture_output=True, check=True).stdout

    def launch_fixture(self, fixture):
        self.shell("am", "force-stop", PACKAGE)
        out = self.shell("am", "start", "-W", "-n", ACTIVITY, "--es", "wear_surface_fixture", fixture)
        if "Status: ok" not in out:
            raise SystemExit(f"launching fixture {fixture} failed: {out}")
        time.sleep(SETTLE_S)

    def scenario(self, name):
        out = self.shell("am", "broadcast", "-n", RECEIVER, "-a", f"{PACKAGE}.wear.ACCEPTANCE_SCENARIO",
                         "--es", "scenario", name)
        if f'result=-1, data="accepted:{name}"' not in out:
            raise SystemExit(f"scenario {name} not acknowledged: {out}")
        time.sleep(SETTLE_S)

    def awake_capture(self):
        # The watch dozes within seconds and screencap would record the ambient screen instead.
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        time.sleep(SETTLE_S)
        if "mWakefulness=Awake" not in self.shell("dumpsys", "power"):
            raise SystemExit("the display is not awake")
        return self.screencap()


def rgba_png_to_rgb(data):
    """Decode an 8-bit non-interlaced RGBA PNG, require full opacity, and re-encode it as RGB."""
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit("screencap did not return a PNG")
    offset, idat, header = 8, b"", None
    while offset < len(data):
        length, name = struct.unpack(">I4s", data[offset:offset + 8])
        body = data[offset + 8:offset + 8 + length]
        if name == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif name == b"IDAT":
            idat += body
        offset += 12 + length
    width, height, depth, color, _, _, interlace = header
    if (depth, color, interlace) != (8, 6, 0):
        raise SystemExit(f"unexpected screencap format: depth {depth}, colour type {color}, interlace {interlace}")
    pixels = unfilter(zlib.decompress(idat), width, height, 4)
    if any(alpha != 255 for alpha in pixels[3::4]):
        raise SystemExit("screencap has non-opaque pixels; dropping alpha would change the image")
    rgb = bytearray(len(pixels) // 4 * 3)
    rgb[0::3], rgb[1::3], rgb[2::3] = pixels[0::4], pixels[1::4], pixels[2::4]
    stride = width * 3
    raw = b"".join(b"\x00" + bytes(rgb[y * stride:(y + 1) * stride]) for y in range(height))

    def chunk(name, body):
        return struct.pack(">I", len(body)) + name + body + struct.pack(">I", zlib.crc32(name + body))

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")), (width, height)


def unfilter(data, width, height, bpp):
    stride, out, prev, i = width * bpp, bytearray(), bytearray(width * bpp), 0
    for _ in range(height):
        kind, row = data[i], bytearray(data[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            left = row[x - bpp] if x >= bpp else 0
            up, upper_left = prev[x], (prev[x - bpp] if x >= bpp else 0)
            if kind == 1:
                row[x] = (row[x] + left) & 255
            elif kind == 2:
                row[x] = (row[x] + up) & 255
            elif kind == 3:
                row[x] = (row[x] + ((left + up) >> 1)) & 255
            elif kind == 4:
                estimate = left + up - upper_left
                pa, pb, pc = abs(estimate - left), abs(estimate - up), abs(estimate - upper_left)
                row[x] = (row[x] + (left if pa <= pb and pa <= pc else up if pb <= pc else upper_left)) & 255
            elif kind != 0:
                raise SystemExit(f"unknown PNG filter {kind}")
        out += row
        prev = row
    return out


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)
    if args.output.exists() and any(args.output.iterdir()):
        raise SystemExit(f"{args.output} is not empty; remove its files to recapture")
    device = Device(args.adb, args.serial)
    size = device.shell("wm", "size")
    if f"Physical size: {SCREEN_PX}x{SCREEN_PX}" not in size:
        raise SystemExit(f"expected a {SCREEN_PX}x{SCREEN_PX} display (240dp at 320dpi), got: {size}")
    package = device.shell("dumpsys", "package", PACKAGE)
    version = dict(re.findall(r"(versionCode|versionName)=(\S+)", package))
    if not version.get("versionName", "").endswith("-wear"):
        raise SystemExit(f"{PACKAGE} is not the installed Wear store build: {version}")

    shots = []
    device.launch_fixture("refresh_required")
    device.scenario("refresh")
    shots.append(("active controller: refresh_required + refresh", device.awake_capture()))
    device.shell("input", "tap", *map(str, REPS_CARD))
    shots.append(("reps editor opened from that controller", device.awake_capture()))
    added = device.shell("am", "broadcast", "-a", "com.google.android.wearable.app.DEBUG_SURFACE",
                         "--es", "operation", "add-tile", "--ecn", "component", TILE_SERVICE)
    index = re.search(r"Index=\[(\d+)\]", added)
    if not index:
        raise SystemExit(f"adding the system Tile failed: {added}")
    device.shell("input", "keyevent", "KEYCODE_WAKEUP")
    device.shell("am", "broadcast", "-a", "com.google.android.wearable.app.DEBUG_SYSUI",
                 "--es", "operation", "show-tile", "--ei", "index", index.group(1))
    time.sleep(SETTLE_S)
    shots.append((f"system Tile at carousel index {index.group(1)} for that session", device.awake_capture()))
    device.launch_fixture("complete")
    shots.append(("workout complete: complete", device.awake_capture()))

    args.output.mkdir(parents=True, exist_ok=True)
    record = {
        "device": {key: device.shell("getprop", key) for key in
                   ("ro.build.fingerprint", "ro.build.version.sdk", "persist.sys.locale")},
        "display": {"size": size, "density": device.shell("wm", "density")},
        "package": {"name": PACKAGE, **version},
        "files": [],
    }
    for number, (subject, png) in enumerate(shots, start=1):
        rgb, (width, height) = rgba_png_to_rgb(png)
        path = args.output / f"{number}_en-US.png"
        path.write_bytes(rgb)
        record["files"].append({"file": path.name, "subject": subject, "size": f"{width}x{height}",
                                "sha256": hashlib.sha256(rgb).hexdigest()})
    print(json.dumps(record, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

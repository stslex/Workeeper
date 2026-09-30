#!/usr/bin/env python3
"""Create isolated round Wear AVDs; never rewrite or remove existing profiles."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess


IMAGES = {
    30: "system-images;android-30;android-wear;arm64-v8a",
    36: "system-images;android-36;android-wear-signed;arm64-v8a",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--avd-home", type=Path, required=True)
    parser.add_argument("--api", type=int, choices=IMAGES, required=True)
    parser.add_argument("--dp", type=int, choices=(192, 240), required=True)
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    home = args.avd_home.resolve()
    home.mkdir(parents=True, exist_ok=True)
    name = f"workeeper_acceptance_api{args.api}_{args.dp}_round"
    path = home / f"{name}.avd"
    if path.exists() or (home / f"{name}.ini").exists():
        parser.error(f"Already exists, left untouched: {path}")
    image = sdk.joinpath(*IMAGES[args.api].split(";"))
    source = image / "source.properties"
    if not source.is_file():
        parser.error(f"Install the official SDK package first: {IMAGES[args.api]}")
    if shutil.disk_usage(home).free < 3 * 1024**3:
        parser.error("Less than 3 GiB free; existing artifacts will not be removed")
    env = dict(os.environ, ANDROID_AVD_HOME=str(home), ANDROID_SDK_ROOT=str(sdk))
    subprocess.run([
        str(sdk / "cmdline-tools/latest/bin/avdmanager"), "create", "avd",
        "--name", name, "--path", str(path), "--package", IMAGES[args.api],
        "--device", "wearos_small_round",
    ], input="no\n", text=True, env=env, check=True)
    config = path / "config.ini"
    values = dict(line.split("=", 1) for line in config.read_text().splitlines() if "=" in line)
    values.update({
        "hw.lcd.width": str(args.dp * 2), "hw.lcd.height": str(args.dp * 2),
        "hw.lcd.density": "320", "hw.lcd.circular": "true",
        "hw.ramSize": "1536", "hw.cpu.ncore": "2", "hw.rotaryInput": "yes",
        "hw.gpu.enabled": "yes", "hw.gpu.mode": "auto", "hw.keyboard": "yes",
        "disk.dataPartition.size": "2G", "hw.sdCard": "no",
        "showDeviceFrame": "no", "skin.name": f"{args.dp * 2}x{args.dp * 2}",
        "skin.dynamic": "yes", "fastboot.forceColdBoot": "yes",
        "fastboot.forceFastBoot": "no", "firstboot.saveToLocalSnapshot": "no",
    })
    values.pop("skin.path", None)
    config.write_text("".join(f"{key}={value}\n" for key, value in sorted(values.items())))
    record = {
        "name": name, "api": args.api, "screen_dp": args.dp,
        "density_dpi": 320, "circular": True, "avd_home": str(home),
        "image": IMAGES[args.api], "image_properties": source.read_text(),
        "config_sha256": hashlib.sha256(config.read_bytes()).hexdigest(),
        "start": [str(sdk / "emulator/emulator"), "-avd", name,
                  "-no-window", "-no-snapshot", "-no-boot-anim", "-no-audio",
                  "-gpu", "host", "-port", "5580"],
    }
    (home / f"{name}.json").write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))


if __name__ == "__main__":
    main()

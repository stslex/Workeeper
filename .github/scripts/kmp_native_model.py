"""Validate the evaluated Gradle export against the required Native roster."""

import argparse
import json
from pathlib import Path
import subprocess

MODEL = Path("build/kmp-verification/model.json")
SIMULATOR_START = Path("build/kmp-verification/native-test-start")
DEVICE_START = Path("build/kmp-verification/device-link-start")
IDENTITIES = Path(__file__).resolve().parents[2] / "gradle/kmp-native-identities.json"
OUTPUT_FIELDS = ("simulatorResults", "simulatorBinary", "deviceBinary")
UPLOAD_ESCAPES = str.maketrans({char: f"\\{char}" for char in "\\*?[]"})


def unique_object(pairs: list[tuple[str, object]]) -> dict:
    result = dict(pairs)
    if len(result) != len(pairs):
        raise ValueError("duplicate key in a Native verification manifest")
    return result


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)


def load_model(path: Path = MODEL) -> list[dict]:
    required = read_json(IDENTITIES)
    if not isinstance(required, dict) or not required:
        raise ValueError("missing independent Native module inventory")
    rows = read_json(path)
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise ValueError("expected a list of evaluated Native modules")
    required_paths = [f":{module}" for module in required]
    actual_paths = [row.get("module") for row in rows]
    if actual_paths != required_paths:
        raise ValueError(f"required Native modules {required_paths} differ from evaluated {actual_paths}")
    root = Path.cwd()
    seen_outputs = set()
    for row in rows:
        module = row["module"]
        for field, terminal in (
            ("simulatorTask", "iosSimulatorArm64Test"),
            ("deviceLinkTask", "linkDebugTestIosArm64"),
        ):
            if row.get(field) != f"{module}:{terminal}":
                raise ValueError(f"{module}: unexpected {field} {row.get(field)!r}")
        for field in OUTPUT_FIELDS:
            raw = row.get(field)
            if not isinstance(raw, str) or not raw or any(ord(char) < 32 for char in raw):
                raise ValueError(f"{module}: invalid {field}")
            output = Path(raw)
            if output.is_absolute() or ".." in output.parts or not output.resolve().is_relative_to(root):
                raise ValueError(f"{module}: {field} leaves the checkout: {output}")
            resolved = output.resolve()
            if resolved in seen_outputs or resolved == root:
                raise ValueError(f"{module}: duplicate or invalid output {output}")
            seen_outputs.add(resolved)
    return rows


def mark_start(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.touch()


def upload_pattern(path: str) -> str:
    # upload-artifact parses glob syntax on the macOS runner, even for one exact path.
    return "./" + Path(path).as_posix().translate(UPLOAD_ESCAPES)


def check_device_links(rows: list[dict], marker: Path = DEVICE_START) -> None:
    if not marker.is_file():
        raise ValueError(f"device-link start marker is missing: {marker}")
    started = marker.stat().st_mtime_ns
    failures = []
    for row in rows:
        binary = Path(row["deviceBinary"])
        try:
            stat = binary.stat()
            if not binary.is_file() or stat.st_size == 0 or stat.st_mtime_ns <= started:
                raise ValueError(f"missing, empty or stale test binary {binary}")
            tool = subprocess.run(
                ["xcrun", "vtool", "-show-build", str(binary)], capture_output=True, text=True, check=True,
            )
            platforms = [
                parts[1] for line in tool.stdout.splitlines()
                if len(parts := line.split()) == 2 and parts[0] == "platform"
            ]
            if platforms != ["IOS"]:
                raise ValueError(f"expected IOS device binary, inspected {platforms}")
        except (OSError, ValueError, subprocess.CalledProcessError) as error:
            failures.append(f"{row['module']}: {error}")
    print(f"{len(rows) - len(failures)}/{len(rows)} fresh IOS device binaries")
    if failures:
        raise ValueError("\n".join(failures))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=(
        "simulator-tasks", "device-tasks", "result-upload-patterns", "epoch-binary", "apple-device-upload-pattern",
        "mark-simulator", "mark-device", "check-device",
    ))
    args = parser.parse_args()
    try:
        rows = load_model()
        if args.command == "mark-simulator":
            mark_start(SIMULATOR_START)
        elif args.command == "mark-device":
            mark_start(DEVICE_START)
        elif args.command == "check-device":
            check_device_links(rows)
        elif args.command in ("epoch-binary", "apple-device-upload-pattern"):
            apple_host = next(row for row in rows if row["module"] == ":app:ios")
            if args.command == "epoch-binary":
                print(apple_host["simulatorBinary"])
            else:
                print(upload_pattern(apple_host["deviceBinary"]))
        elif args.command == "result-upload-patterns":
            print("\n".join(upload_pattern(row["simulatorResults"]) for row in rows))
        else:
            field = {
                "simulator-tasks": "simulatorTask",
                "device-tasks": "deviceLinkTask",
            }[args.command]
            print("\n".join(row[field] for row in rows))
    except (OSError, ValueError, StopIteration) as error:
        raise SystemExit(f"Native verification: {error}") from error


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Run one exact Native storage case in two sequential, owned simulator processes."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid

IDENTITY = "io.github.stslex.workeeper.app.ios.IosInstallEpochSourceTest.publicSourceUsesExcludedInstallationDirectory"
PREFIX = "WORKEEPER_INSTALL_EPOCH_RECEIPT "
BINARY = Path("app/ios/build/bin/iosSimulatorArm64/debugTest/test.kexe")
RESULTS = Path("app/ios/build/install-epoch-relaunch")
MARKER = Path("app/ios/build/native-test-start")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(f"installation epoch relaunch: {message}")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def exact_case(log: str) -> dict:
    started = re.findall(r"^\[ RUN\s+\] (\S+)\s*$", log, re.MULTILINE)
    passed = re.findall(r"^\[\s+OK\s+\] (\S+)(?:\s+.*)?$", log, re.MULTILINE)
    require(started == [IDENTITY] and passed == [IDENTITY], "expected exactly one started and passing case")
    require(not re.search(r"^\[\s*FAILED\s*\]", log, re.MULTILINE), "a Native case failed")
    lines = [line[len(PREFIX):] for line in log.splitlines() if line.startswith(PREFIX)]
    require(len(lines) == 1, "expected exactly one storage receipt")
    return json.loads(lines[0])


def validate_relaunch(directory: Path = RESULTS, marker: Path = MARKER, binary: Path = BINARY) -> str:
    require(marker.is_file(), "full-suite freshness marker is missing")
    manifest_path = directory / "run.json"
    manifest = json.loads(manifest_path.read_text())
    started = manifest["started_ns"]
    require(started >= marker.stat().st_mtime_ns, "run predates the full-suite marker")
    require(manifest_path.stat().st_mtime_ns >= started, "stale run manifest")
    nonce = manifest["nonce"]
    require(str(uuid.UUID(nonce)) == nonce, "invalid run nonce")
    expected_nonce = os.environ.get("WORKEEPER_INSTALL_EPOCH_NONCE") or os.environ.get(
        "SIMCTL_CHILD_WORKEEPER_INSTALL_EPOCH_NONCE"
    )
    require(expected_nonce is None or expected_nonce == nonce, "run nonce differs from the current run")
    simulator = manifest["simulator"]
    expected_device = os.environ.get("WORKEEPER_DATABASE_TEST_SIMULATOR")
    require(expected_device is None or expected_device == simulator, "simulator differs from the current run")
    receipts = []
    previous_end = started
    for index in (1, 2):
        path = directory / f"launch-{index}.json"
        log_path = directory / f"launch-{index}.log"
        row = json.loads(path.read_text())
        require(path.stat().st_mtime_ns >= started and log_path.stat().st_mtime_ns >= started, "stale launch files")
        require(row["started_ns"] >= previous_end and row["ended_ns"] >= row["started_ns"], "overlapping or stale launches")
        require(row["exit_code"] == 0, "Native launch did not exit successfully")
        require(row["identity"] == IDENTITY, "incorrect filtered identity")
        require(row["binary_sha256"] == digest(binary), "launch used a different executable")
        require(row["log_sha256"] == digest(log_path), "launch log changed")
        require(row["command"] == ["xcrun", "simctl", "spawn", simulator, str(binary.resolve()),
                                   f"--ktest_filter={IDENTITY}", "--ktest_logger=GTEST"], "incorrect launch invocation")
        observed = exact_case(log_path.read_text())
        require(row["storage"] == observed, "receipt does not match the Native observation")
        require(observed["nonce"] == nonce, "launch reused a different run nonce")
        require(observed["launch"] == row["launch"] and str(uuid.UUID(row["launch"])) == row["launch"], "incorrect launch ID")
        require(observed["expected_udid"] == observed["actual_udid"] == simulator, "simulator UUID mismatch")
        require(type(observed["pid"]) is int and observed["pid"] > 0, "invalid process ID")
        require(observed["io_main"] is False, "storage did not prove off-Main I/O")
        location = observed["path"]
        require(f"/Devices/{simulator}/data/" in location, "path is outside the owned simulator sandbox")
        require(location.endswith("/Library/Application Support/WorkeeperInstallation/install_epoch"), "incorrect canonical path")
        require(re.fullmatch(r"[0-9a-f]{64}", observed["sha256"]) is not None, "invalid file digest")
        previous_end = row["ended_ns"]
        receipts.append(row)
    first, second = receipts
    require(first["launch"] != second["launch"], "reused launch ID")
    require(first["storage"]["pid"] != second["storage"]["pid"], "reused process ID")
    for key in ("nonce", "expected_udid", "actual_udid", "path", "sha256"):
        require(first["storage"][key] == second["storage"][key], f"relaunch changed {key}")
    return "two exited Native processes, two exact passing cases, matching owned path and digest"


def run(simulator: str, nonce: str, output: Path) -> None:
    require(simulator == os.environ.get("WORKEEPER_DATABASE_TEST_SIMULATOR"), "caller does not own the simulator")
    require(BINARY.is_file() and BINARY.stat().st_size > 0, "simulator executable is missing or empty")
    require(MARKER.is_file(), "execution marker is missing")
    output.mkdir(parents=True, exist_ok=False)
    started = time.time_ns()
    (output / "run.json").write_text(json.dumps({
        "started_ns": started, "simulator": simulator, "nonce": nonce,
    }, indent=2) + "\n")
    for index in (1, 2):
        launch = str(uuid.uuid4())
        environment = os.environ.copy()
        environment.update({
            "SIMCTL_CHILD_WORKEEPER_DATABASE_TEST_SIMULATOR": simulator,
            "SIMCTL_CHILD_WORKEEPER_INSTALL_EPOCH_NONCE": nonce,
            "SIMCTL_CHILD_WORKEEPER_INSTALL_EPOCH_LAUNCH": launch,
        })
        command = ["xcrun", "simctl", "spawn", simulator, str(BINARY.resolve()),
                   f"--ktest_filter={IDENTITY}", "--ktest_logger=GTEST"]
        row = {"launch": launch, "started_ns": time.time_ns(), "command": command,
               "binary_sha256": digest(BINARY), "identity": IDENTITY}
        log_path = output / f"launch-{index}.log"
        with log_path.open("w") as log:
            result = subprocess.run(command, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=120)
        # subprocess.run waits for process exit before the next launch can begin.
        row.update({"ended_ns": time.time_ns(), "exit_code": result.returncode, "log_sha256": digest(log_path)})
        (output / f"launch-{index}.json").write_text(json.dumps(row, indent=2) + "\n")
        require(result.returncode == 0, f"launch {index} failed; see {log_path}")
        row["storage"] = exact_case(log_path.read_text())
        (output / f"launch-{index}.json").write_text(json.dumps(row, indent=2) + "\n")
    print(validate_relaunch(output))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--simulator", required=True)
    parser.add_argument("--nonce", required=True)
    parser.add_argument("--output", type=Path, default=RESULTS)
    args = parser.parse_args()
    run(args.simulator, args.nonce, args.output)


if __name__ == "__main__":
    main()

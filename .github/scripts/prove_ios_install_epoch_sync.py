"""Observe actual Darwin synchronization around an isolated production epoch publication."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid

from kmp_native_model import load_model, read_json

IDENTITY = "io.github.stslex.workeeper.app.ios.IosInstallEpochSourceTest.orphanCandidateIsNeverAdopted"
PREFIX = "WORKEEPER_EPOCH_SYNC "
SOURCE = Path(__file__).with_name("ios_install_epoch_sync_observer.c")


def require(value, message):
    if not value:
        raise ValueError("installation epoch synchronization: " + message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_operations(rows, nonce, simulator):
    require(len(rows) >= 1, "missing observer activation or storage operations")
    require([row["sequence"] for row in rows] == list(range(1, len(rows) + 1)), "missing or reordered trace records")
    require(rows[0]["operation"] == "activate", "observer was not injected")
    require({row["nonce"] for row in rows} == {nonce}, "wrong observer nonce")
    require(len({row["pid"] for row in rows}) == 1 and rows[0]["pid"] > 0, "wrong process identity")
    publications = [row for row in rows if row["operation"] == "linkat"]
    require(len(publications) == 1, "expected one real exclusive publication")
    publication = publications[0]
    require(publication["result"] == 0, "publication failed")
    candidate = Path(publication["path"])
    directory = candidate.parent
    require(directory.name == "WorkeeperInstallation", "wrong installation directory")
    require(f"/Devices/{simulator}/data/" in str(directory), "storage is outside the owned simulator")
    require(Path(publication["target"]) == directory / "install_epoch", "wrong canonical destination")
    require(publication["size"] == 36 and not publication["directory"], "candidate is not a completed epoch")
    identity = (publication["device"], publication["inode"])
    opens = [row for row in rows if row["operation"] == "openat" and row["path"] == str(candidate)
             and row["result"] >= 0]
    require(len(opens) == 1, "missing or ambiguous candidate creation")
    opened = opens[0]
    require(all(opened.get(flag) is True for flag in ("create", "exclusive", "no_follow")),
            "candidate creation was not exclusive and symlink-safe")
    require((opened["device"], opened["inode"]) == identity and opened.get("regular") is True and opened["size"] == 0,
            "opened a different candidate")
    candidate_sync = [row for row in rows if row["operation"] == "fsync" and row["path"] == str(candidate)]
    require(len(candidate_sync) == 1, "missing or ambiguous candidate fsync")
    synced = candidate_sync[0]
    require(synced["result"] == 0 and synced["size"] == 36 and not synced["directory"], "candidate fsync failed")
    require((synced["device"], synced["inode"]) == identity, "synced a different candidate")
    require(synced["sequence"] < publication["sequence"], "candidate fsync followed publication")
    writes = [row for row in rows if row["operation"] == "write" and row["result"] > 0
              and (row["path"] == str(candidate) or (row["device"], row["inode"]) == identity)]
    require(writes, "candidate writes were not observed")
    require(all((row["device"], row["inode"]) == identity for row in writes), "wrote a different candidate")
    require(all(opened["sequence"] < row["sequence"] for row in writes), "candidate write preceded creation")
    require(all(row["sequence"] < synced["sequence"] for row in writes), "candidate write followed fsync")
    require(writes[-1]["size"] == 36, "last candidate write was incomplete")
    cleanup = [row for row in rows if row["operation"] == "unlinkat" and row["path"] == str(candidate)]
    require(len(cleanup) == 1 and cleanup[0]["result"] == 0, "owned candidate was not cleaned up")
    removed = cleanup[0]
    require((removed["device"], removed["inode"]) == identity, "removed a different candidate")
    require(publication["sequence"] < removed["sequence"], "cleanup preceded publication")
    directory_sync = [row for row in rows if row["operation"] == "fsync" and row["path"] == str(directory)]
    require(all(row["result"] == 0 and row["directory"] for row in directory_sync), "directory fsync failed")
    require(len({(row["device"], row["inode"]) for row in directory_sync}) == 1, "synced different directories")
    require(any(publication["sequence"] < row["sequence"] < removed["sequence"] for row in directory_sync),
            "publication directory was not synced before cleanup")
    require(any(removed["sequence"] < row["sequence"] for row in directory_sync),
            "candidate cleanup directory was not synced")
    return "exclusive candidate creation; candidate fsync before publication; directory fsync before cleanup; cleanup directory fsync"


def exact_case(log):
    started = re.findall(r"^\[ RUN\s+\] (\S+)\s*$", log, re.MULTILINE)
    passed = re.findall(r"^\[\s+OK\s+\] (\S+)(?:\s+.*)?$", log, re.MULTILINE)
    require(started == passed == [IDENTITY], "expected exactly the isolated passing Native case")
    require(not re.search(r"^\[\s*FAILED\s*\]", log, re.MULTILINE), "Native case failed")


def validate_receipt(output, binary, *, toolchain, invocation_root=None):
    receipt = read_json(output / "receipt.json")
    require(receipt["exit_code"] == 0, "Native process did not exit successfully")
    require(receipt["xcode"] == toolchain["xcode"], "observer Xcode differs from execution job")
    require(receipt["sdk"] == toolchain["iphonesimulator_sdk"], "observer SDK differs from execution job")
    original_root = Path(invocation_root) if invocation_root else Path.cwd()
    compiled = list(receipt["build_command"])
    require(len(compiled) == 16, "unexpected observer compiler invocation")
    for index in (13, 15):
        compiled[index] = str(original_root / compiled[index])
    expected = compiler_command(receipt["sdk"], toolchain["iphonesimulator_sdk_path"],
                                original_root / ".github/scripts/ios_install_epoch_sync_observer.c",
                                original_root / output / "observer.dylib")
    require(compiled == expected, "observer compiler invocation differs")
    require(receipt["binary_sha256"] == digest(binary), "Native executable differs")
    require(receipt["observer_source_sha256"] == digest(SOURCE), "observer source differs")
    require(receipt["observer_sha256"] == digest(output / "observer.dylib"), "observer executable differs")
    require(receipt["log_sha256"] == digest(output / "native.log"), "Native log differs")
    log = (output / "native.log").read_text()
    exact_case(log)
    rows = [json.loads(line[len(PREFIX):]) for line in log.splitlines() if line.startswith(PREFIX)]
    recorded = [json.loads(line) for line in (output / "operations.jsonl").read_text().splitlines()]
    require(rows == recorded, "parsed operations differ from Native log")
    require(receipt["started_ns"] < receipt["ended_ns"], "invalid execution interval")
    require(str(uuid.UUID(receipt["nonce"])) == receipt["nonce"], "invalid nonce")
    require(receipt["command"] == ["xcrun", "simctl", "spawn", receipt["simulator"], str(original_root / binary),
                                  f"--ktest_filter={IDENTITY}", "--ktest_logger=GTEST"], "wrong Native invocation")
    return validate_operations(rows, receipt["nonce"], receipt["simulator"])


def compiler_command(version, sdk, source, library):
    return ["xcrun", "--sdk", "iphonesimulator", "clang", "-arch", "arm64",
            f"-mios-simulator-version-min={version}", "-isysroot", sdk,
            "-Wall", "-Wextra", "-Werror", "-dynamiclib", str(source), "-o", str(library)]


def run(simulator, binary, output):
    require(simulator == os.environ["WORKEEPER_DATABASE_TEST_SIMULATOR"], "caller does not own the simulator")
    output.mkdir(parents=True, exist_ok=False)
    sdk = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], text=True).strip()
    version = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-version"], text=True).strip()
    xcode = subprocess.check_output(["xcodebuild", "-version"], text=True).strip()
    library = output / "observer.dylib"
    build = compiler_command(version, sdk, SOURCE, library)
    subprocess.run(build, check=True)
    nonce = str(uuid.uuid4())
    environment = dict(os.environ, SIMCTL_CHILD_DYLD_INSERT_LIBRARIES=str(library.resolve()),
                       SIMCTL_CHILD_WORKEEPER_EPOCH_SYNC_NONCE=nonce,
                       SIMCTL_CHILD_WORKEEPER_DATABASE_TEST_SIMULATOR=simulator)
    command = ["xcrun", "simctl", "spawn", simulator, str(binary.resolve()),
               f"--ktest_filter={IDENTITY}", "--ktest_logger=GTEST"]
    receipt = {"command": command, "build_command": build, "nonce": nonce, "simulator": simulator,
               "xcode": xcode, "sdk": version, "started_ns": time.time_ns(),
               "binary_sha256": digest(binary), "observer_source_sha256": digest(SOURCE),
               "observer_sha256": digest(library)}
    try:
        with (output / "native.log").open("w") as log:
            result = subprocess.run(command, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=120)
        receipt["exit_code"] = result.returncode
    except subprocess.TimeoutExpired:
        receipt.update(exit_code=124, error="Native process exceeded 120 seconds")
    finally:
        receipt.update(ended_ns=time.time_ns(), log_sha256=digest(output / "native.log"))
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        operations = [line[len(PREFIX):] for line in (output / "native.log").read_text().splitlines()
                      if line.startswith(PREFIX)]
        (output / "operations.jsonl").write_text("\n".join(operations) + "\n")
    print(validate_receipt(output, binary, toolchain={
        "xcode": xcode, "iphonesimulator_sdk": version, "iphonesimulator_sdk_path": sdk,
    }))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--simulator", required=True)
    args = parser.parse_args()
    apple = next(row for row in load_model() if row["module"] == ":app:ios")
    run(args.simulator, Path(apple["simulatorBinary"]), Path(apple["buildDirectory"]) / "install-epoch-sync")


if __name__ == "__main__":
    main()

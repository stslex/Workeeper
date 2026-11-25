"""Build and exercise the installed iOS product on CI-owned simulator data."""
import contextlib
import json
import os
import shutil
import plistlib
import sqlite3
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path

from ios_app_results import HOST, PERSISTENCE, RECOVERY, executed, require, validate, validate_embed
from ios_app_test_photo import write_photo
from kmp_native_evidence import ROOT, command, inventory, owned_simulator_runtime, provenance
from kmp_native_model import load_model

BUNDLE = "io.github.stslex.workeeper.dev"
PROJECT = "iosApp/Workeeper.xcodeproj"
FLAGS = ["--rerun-tasks", "--no-build-cache", "--no-configuration-cache"]
APP = ROOT / "installed"
SCENE = Path("iosApp/Workeeper/AppDelegate.swift")
DATABASE = next(Path("core/data/database/src/iosMain").rglob("AppDatabaseFactory.ios.kt"))
CONTROLS = {
    "empty": (HOST, "Expected the production Compose hosting controller"),
    "process": (PERSISTENCE, "Post-relaunch template missing from Trainings"),
}


def run(args, log, expected=0):
    with log.open("x") as outfile:
        process = subprocess.run(args, stdout=outfile, stderr=subprocess.STDOUT)
    print(f"exit {process.returncode}: {' '.join(map(str, args))}", flush=True)
    if process.returncode != expected:
        print(log.read_text(errors="replace")[-12000:], file=sys.stderr)
        raise ValueError(f"{log}: expected exit {expected}, observed {process.returncode}")
    return process.returncode


@contextlib.contextmanager
def device(label, runtime):
    name = f"Workeeper-app-{os.environ['GITHUB_RUN_ID']}-{os.environ['GITHUB_RUN_ATTEMPT']}-{label}"
    udid = command("xcrun", "simctl", "create", name, "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro", runtime)
    try:
        command("xcrun", "simctl", "boot", udid)
        command("xcrun", "simctl", "bootstatus", udid, "-b")
        yield udid
    finally:
        subprocess.run(["xcrun", "simctl", "shutdown", udid], check=False)
        command("xcrun", "simctl", "delete", udid)


@contextlib.contextmanager
def isolated_source(role):
    if role not in CONTROLS:
        yield
        return
    require(os.environ.get("GITHUB_ACTIONS") == "true", "controls require the disposable Actions checkout")
    if role == "empty":
        path, original, mutation = SCENE, "let controller = app.host.makeController()", "let controller = UIViewController()"
    else:
        path = DATABASE
        original = 'URLByAppendingPathComponent("Workeeper", true)'
        mutation = 'URLByAppendingPathComponent("Workeeper-${platform.Foundation.NSProcessInfo.processInfo.processIdentifier}", true)'
    before = path.read_bytes()
    require(before == subprocess.check_output(["git", "show", f"HEAD:{path.as_posix()}"]) and
            before.count(original.encode()) == 1, f"unexpected control source: {path}")
    try:
        path.write_bytes(before.replace(original.encode(), mutation.encode()))
        yield
    finally:
        path.write_bytes(before)
        require(path.read_bytes() == before, "failed to restore controlled bytes")


def views(bundle, directory):
    for view in ("summary", "tests"):
        run(["xcrun", "xcresulttool", "get", "test-results", view, "--path", bundle], directory / f"{view}.json")
    return [json.loads((directory / f"{view}.json").read_text()) for view in ("summary", "tests")]


@contextlib.contextmanager
def separate_builds(destination, modules):
    require(os.environ.get("GITHUB_ACTIONS") == "true", "build isolation requires a disposable runner")
    destination.mkdir()
    linked = []
    try:
        for path in modules:
            require(path.name == "build" and not path.is_absolute() and ".." not in path.parts and
                    not path.is_symlink(), f"unexpected build directory {path}")
            output = destination / path
            output.mkdir(parents=True)
            original = output.with_name("original")
            if path.exists():
                path.rename(original)
            try:
                path.symlink_to(output, target_is_directory=True)
            except BaseException:
                if original.exists():
                    original.rename(path)
                raise
            linked.append((path, original))
        yield
    finally:
        for path, original in reversed(linked):
            path.unlink()
            if original.exists():
                original.rename(path)
        shutil.rmtree(destination)


def exercise(role, runtime, build_root):
    evidence = APP / role
    evidence.mkdir()
    derived = build_root / role
    with device(role, runtime) as udid, isolated_source(role):
        observed_runtime = owned_simulator_runtime(udid, json.loads(command("xcrun", "simctl", "list", "-j")))
        if role not in CONTROLS:
            photo = build_root / "Workeeper-picker.png"
            write_photo(photo)
            command("xcrun", "simctl", "addmedia", udid, str(photo))
        xcode = ["xcodebuild", "-project", PROJECT, "-scheme", "Workeeper", "-configuration", "Debug",
                 "-destination", f"platform=iOS Simulator,id={udid}", "-derivedDataPath", str(derived),
                 "-parallel-testing-enabled", "NO"]
        embed = evidence / "build.log"
        run([*xcode, "WORKKEEPER_VERIFY_GRADLE=YES", "build-for-testing"], embed)
        log = embed.read_text()
        count = validate_embed(log)
        bundle = evidence / "Workeeper.xcresult"
        args = [*xcode, "-resultBundlePath", str(bundle.absolute())]
        control = CONTROLS.get(role)
        if control:
            args.append("-only-testing:" + control[0])
        code = run([*args, "test-without-building"], evidence / "test.log", expected=65 if control else 0)
        summary, tests = views(bundle, evidence)
        identities = validate(summary, tests, udid, control)
        if role == "process":
            require("Initial unique template row saved" in (evidence / "test.log").read_text(),
                    "per-process control did not save the initial row")
        record = {"device": observed_runtime, "identities": identities, "exit": code, "summary": count,
                  "bundle_id": BUNDLE, "scheme": "Workeeper"}
        (evidence / "run.json").write_text(json.dumps(record, indent=2))
        if role == "positive":
            recovery(udid, evidence, derived)
    shutil.rmtree(derived)


def recovery(udid, evidence, derived):
    # XCTest can leave its target in the background after handing a URL to Safari.
    def app_running():
        jobs = command("xcrun", "simctl", "spawn", udid, "launchctl", "list").splitlines()
        return any(fields[0].isdigit() and BUNDLE + "[" in fields[-1]
                   for row in jobs if (fields := row.split()))
    if app_running():
        command("xcrun", "simctl", "terminate", udid, BUNDLE)
    require(not app_running(), "installed app is still using its database")
    data = Path(command("xcrun", "simctl", "get_app_container", udid, BUNDLE, "data"))
    require(udid in data.parts and "Application" in data.parts, "unowned app container")
    path = data / "Library/Application Support/Workeeper/app.db"
    require(path.is_file(), "installed app database missing")
    sentinel = str(uuid.uuid4())
    with contextlib.closing(sqlite3.connect(path)) as db, db:
        db.execute("CREATE TABLE phase8_sentinel (value TEXT PRIMARY KEY)")
        db.execute("INSERT INTO phase8_sentinel VALUES (?)", (sentinel,))
        db.execute("PRAGMA user_version = 87008")
    original, = (derived / "Build/Products").glob("*.xctestrun")
    with original.open("rb") as stream:
        test_run = plistlib.load(stream)
    if "TestConfigurations" in test_run:
        ui, = [target for config in test_run["TestConfigurations"] for target in config["TestTargets"]
               if target["BlueprintName"] == "WorkeeperUITests"]
    else:
        ui = test_run["WorkeeperUITests"]
    ui.setdefault("EnvironmentVariables", {})["EXPECTED_RECOVERY"] = "YES"
    control = original.with_name("unsupported-version.xctestrun")
    with control.open("xb") as stream:
        plistlib.dump(test_run, stream)
    xcode = ["xcodebuild", "-xctestrun", str(control), "-destination", f"platform=iOS Simulator,id={udid}",
             "-parallel-testing-enabled", "NO"]
    # Use the system accessibility tree to assert the DB-independent recovery surface.
    folder = evidence / "recovery"
    folder.mkdir()
    bundle = folder / "Recovery.xcresult"
    run([*xcode, "-resultBundlePath", str(bundle.absolute()),
         "-only-testing:" + RECOVERY,
         "test-without-building"], evidence / "recovery.log")
    summary, tests = views(bundle, folder)
    require(validate(summary, tests, udid, required={RECOVERY}) == {RECOVERY: "Passed"}, "recovery proof missing")
    current = Path(command("xcrun", "simctl", "get_app_container", udid, BUNDLE, "data"))
    require(udid in current.parts and "Application" in current.parts, "lost owned data container")
    path = current / path.relative_to(data)
    with contextlib.closing(sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)) as db:
        require(db.execute("PRAGMA user_version").fetchone() == (87008,), "unsupported database version changed")
        require(db.execute("SELECT value FROM phase8_sentinel").fetchall() == [(sentinel,)], "sentinel changed")
    (evidence / "sentinel.json").write_text(json.dumps({"version": 87008, "sentinel": sentinel, "device": udid}))


def main():
    APP.mkdir(parents=True, exist_ok=False)
    prod = provenance("app")
    (ROOT / "app-provenance.json").write_text(json.dumps(prod))
    available = json.loads(command("xcrun", "simctl", "list", "runtimes", "-j"))["runtimes"]
    runtime = max((r for r in available if r["isAvailable"] and ".iOS-" in r["identifier"]),
                  key=lambda r: tuple(map(int, r["version"].split("."))))["identifier"]
    with tempfile.TemporaryDirectory(prefix="workeeper-app-", dir=os.environ["RUNNER_TEMP"]) as tmp:
        root = Path(tmp).resolve()
        run(["xcodebuild", "-list", "-project", PROJECT], APP / "project.log")
        run(["./gradlew", ":verifyKmpMigrationContracts", ":exportKmpVerificationModel",
             "verifyKmpNativeTargets", *FLAGS], APP / "contracts.log")
        executed((APP / "contracts.log").read_text())
        initial = subprocess.check_output(["git", "diff", "--binary", "HEAD"])
        build_dirs = {Path(row["buildDirectory"]) for row in load_model()}
        scripts = command("git", "ls-files", "--", ":(glob)**/build.gradle.kts").splitlines()
        build_dirs.update(Path(script).parent / "build" for script in scripts if "/" in script)
        build_dirs = sorted(build_dirs)
        for role in ("entry", "empty", "process", "positive"):
            with separate_builds(root / (role + "-gradle"), build_dirs):
                exercise(role, runtime, root)
            require(subprocess.check_output(["git", "diff", "--binary", "HEAD"]) == initial, "source tree changed")
    paths = [p for p in APP.rglob("*") if p.is_file()] + [ROOT / "app-provenance.json", Path("gradle/kmp-native-identities.json")]
    receipt = {"role": "app", "valid": True, "provenance": prod, "files": inventory(paths),
               "summary": executed((APP / "positive/build.log").read_text())}
    (ROOT / "app-receipt.json").write_text(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()

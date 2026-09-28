#!/usr/bin/env python3
"""Serial acceptance on already provisioned Wear AVDs. Never builds, installs, or boots an AVD."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import io
import json
import os
import re
import shlex
import subprocess
import sys
import tarfile
import time
import uuid
from dataclasses import asdict
from pathlib import Path

from acceptance_model import (
    ACCEPTANCE_PACKAGE, ACTIVITY_CLASS, FIXTURES, InvalidEvidence, Observation,
    ObservedFailure, boot_id, cell_by_id, expected_inventory, instrumentation_results,
    inventory_result, matrix, no_shell_error, notification_deadline, notification_key,
    process_birth, removal_result, sha256, strict_json, uptime_ms, validate_config,
    validate_elapsed_calibration, validate_observation, validate_retention_progress,
    validate_passive_timeout,
    validate_ui_receipt, resumed_activities, background_activity_state, validate_background_continuity,
)

ROOT = Path(__file__).resolve().parents[2]
RECEIVER = "io.github.stslex.workeeper.wear.runtime.AcceptanceScenarioReceiver"
RECEIVER_ACTION = "io.github.stslex.workeeper.wear.ACCEPTANCE_SCENARIO"
UI_METHOD = f"{ACCEPTANCE_PACKAGE}.WearActivityAcceptanceTest#fixtureRendersThroughRealActivity"
ROTARY_METHOD = f"{ACCEPTANCE_PACKAGE}.WearActivityAcceptanceTest#rotaryEditorsAndPressUseRealActivity"
AMBIENT_METHOD = f"{ACCEPTANCE_PACKAGE}.WearAmbientDeviceAcceptanceTest#systemSleepWakePreservesEligibleSurface"
UI_INVOCATIONS = tuple(f"fixture:{fixture}" for fixture in FIXTURES) + ("rotary", "ambient:none", "ambient:reps", "ambient:weight")
RETENTION_SECONDS = 600
REFRESH_SECONDS = 60
POLL_SECONDS = 0.25
WATCHDOG_MS = 5000


def utc() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def write_json(path: Path, value: dict) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    try:
        with temporary.open("x", encoding="utf-8") as stream:
            stream.write(json.dumps(value, indent=2, ensure_ascii=False) + "\n")
    except FileExistsError as error:
        raise InvalidEvidence("Refusing an existing JSON temporary path") from error
    temporary.replace(path)


def local_git(*args: str) -> bytes:
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def source_identity() -> dict:
    paths = local_git("ls-files", "--cached", "--others", "--exclude-standard", "-z").decode().split("\0")
    files = {}
    for relative in sorted(set(filter(None, paths))):
        path = ROOT / relative
        if path.is_symlink():
            files[relative] = hashlib.sha256(os.readlink(path).encode()).hexdigest()
        elif path.is_file():
            files[relative] = sha256(path)
        else:
            files[relative] = "MISSING"
    return {"head": local_git("rev-parse", "HEAD").decode().strip(),
            "branch": local_git("branch", "--show-current").decode().strip(), "files": files}


def require_local_path(directory: Path, path: Path) -> None:
    try:
        relative = path.relative_to(directory)
    except ValueError as error:
        raise InvalidEvidence("Evidence path is outside its declared directory") from error
    current = directory
    for part in (".", *relative.parts):
        current = current / part
        if current.is_symlink():
            raise InvalidEvidence("Evidence paths cannot contain symbolic links")
    if not path.resolve().is_relative_to(directory.resolve()):
        raise InvalidEvidence("Resolved evidence path leaves its declared directory")


def create_case_directory(cohort: Path, case_id: str) -> Path:
    destination = cohort / "cases" / case_id
    require_local_path(cohort, destination)
    destination.mkdir(parents=True, exist_ok=False)
    return destination


def create_manual_staging_directory(cohort: Path, case_id: str) -> Path:
    staging = cohort / "manual-attempts" / f"{case_id.replace('/', '--')}-{uuid.uuid4().hex}"
    require_local_path(cohort, staging)
    staging.mkdir(parents=True, exist_ok=False)
    return staging


def publish_manual_directory(cohort: Path, case_id: str, staging: Path) -> None:
    destination = cohort / "cases" / case_id
    require_local_path(cohort, destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    # rename can replace an empty directory; recorded and incomplete cases both stay immutable.
    if destination.exists():
        raise FileExistsError(destination)
    staging.rename(destination)


def artifact_inventory(directory: Path) -> dict:
    require_local_path(directory, directory)
    for path in directory.rglob("*"):
        require_local_path(directory, path)
    return {str(path.relative_to(directory)): {"sha256": sha256(path), "bytes": path.stat().st_size}
            for path in sorted(directory.rglob("*")) if path.is_file() and path != directory / "receipt.json"}


class Adb:
    def __init__(self, serial: str, directory: Path, executable: str = "adb"):
        if not re.fullmatch(r"[A-Za-z0-9_.:-]+", serial):
            raise InvalidEvidence("Invalid explicit ADB serial")
        self.prefix = [executable, "-s", serial]
        self.directory = directory
        self.count = 0

    def command(self, arguments: list[str], *, timeout: float = 30, binary: bool = False,
                allowed_codes: tuple[int, ...] = (0,)) -> bytes | str:
        self.count += 1
        stem = f"command-{self.count:05d}"
        began = utc()
        start = time.monotonic()
        try:
            result = subprocess.run(self.prefix + arguments, capture_output=True, timeout=timeout, check=False)
            code, out, err = result.returncode, result.stdout, result.stderr
        except subprocess.TimeoutExpired as error:
            code, out, err = None, error.stdout or b"", error.stderr or b""
        (self.directory / f"{stem}.stdout").write_bytes(out)
        (self.directory / f"{stem}.stderr").write_bytes(err)
        record = {"argv": self.prefix + arguments, "started_at": began, "finished_at": utc(),
                  "duration_seconds": time.monotonic() - start, "exit_code": code,
                  "stdout": f"{stem}.stdout", "stderr": f"{stem}.stderr"}
        with (self.directory / "commands.jsonl").open("a") as stream:
            stream.write(json.dumps(record) + "\n")
        if code not in allowed_codes:
            raise InvalidEvidence(f"ADB command {stem} failed/timed out (exit={code})")
        if binary:
            return out
        try:
            return out.decode("utf-8", errors="strict")
        except UnicodeDecodeError as error:
            raise InvalidEvidence(f"Non-UTF8 command result: {stem}") from error

    def shell(self, *args: str, **kwargs) -> str:
        return self.command(["shell", shlex.join(args)], **kwargs)

    def script(self, script: str, **kwargs) -> str:
        return self.shell("sh", "-c", script, **kwargs)

    def screenshot(self, name: str) -> None:
        data = self.command(["exec-out", "screencap", "-p"], binary=True)
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise InvalidEvidence("screencap did not return PNG")
        (self.directory / name).write_bytes(data)


def validate_plan(directory: Path) -> dict:
    require_local_path(directory, directory / "plan.json")
    plan = strict_json((directory / "plan.json").read_text())
    if plan.get("schema") != 1 or plan.get("expected") != expected_inventory():
        raise InvalidEvidence("Plan inventory/schema differs from this runner")
    if plan.get("source") != source_identity():
        raise InvalidEvidence("Checkout/source/test/tool bytes changed since plan creation")
    return plan


def verify_installed(adb: Adb, plan: dict, package: str, *, test_apk: bool) -> None:
    for key, installed_package in [("apk", package)] + ([("test_apk", plan["test_package"])] if test_apk else []):
        text = adb.shell("pm", "path", installed_package)
        paths = re.findall(r"(?m)^package:(/[^\r\n]+)$", text)
        if len(paths) != 1:
            raise InvalidEvidence(f"Expected one pinned base APK for {installed_package}")
        digest = adb.shell("sha256sum", paths[0])
        match = re.fullmatch(r"([a-f0-9]{64})\s+" + re.escape(paths[0]) + r"\s*", digest)
        if not match or match[1] != plan[key]["sha256"]:
            raise InvalidEvidence(f"Installed {key} differs from pinned artifact")


def preflight(adb: Adb, plan: dict, api: int, *, test_apk: bool) -> None:
    if adb.command(["get-state"]).strip() != "device":
        raise InvalidEvidence("ADB target is not ready")
    if adb.shell("getprop", "ro.kernel.qemu").strip() != "1":
        raise InvalidEvidence("This acceptance runner is restricted to an emulator")
    if adb.shell("getprop", "ro.build.version.sdk").strip() != str(api):
        raise InvalidEvidence("Wrong Android API for selected row")
    if adb.shell("am", "get-current-user").strip() != str(plan["user"]):
        raise InvalidEvidence("Wrong Android user")
    for command in (("getprop", "ro.build.fingerprint"), ("wm", "size"), ("wm", "density"),
                    ("getprop", "persist.sys.locale"), ("settings", "get", "system", "font_scale"),
                    ("dumpsys", "power"), ("dumpsys", "package", plan["package"])):
        adb.shell(*command)
    verify_installed(adb, plan, plan["package"], test_apk=test_apk)


def fresh_setup(adb: Adb, plan: dict, api: int, locale: str) -> None:
    # Setup is outside each measured interval. No reset is allowed inside an observation.
    result = adb.shell("pm", "clear", "--user", str(plan["user"]), plan["package"])
    if result.strip() != "Success":
        raise InvalidEvidence("Could not establish a fresh independent scenario")
    if api >= 33:
        adb.shell("cmd", "locale", "set-app-locales", plan["package"], "--user", str(plan["user"]),
                  "--locales", "ru-RU" if locale == "ru" else "en-US")
        adb.shell("pm", "grant", "--user", str(plan["user"]), plan["package"], "android.permission.POST_NOTIFICATIONS")
        adb.shell("cmd", "locale", "get-app-locales", plan["package"], "--user", str(plan["user"]))


def extract_artifacts(data: bytes, directory: Path) -> None:
    try:
        with tarfile.open(fileobj=io.BytesIO(data), mode="r:*") as archive:
            members = archive.getmembers()
            if sum(member.size for member in members) > 64 * 1024 * 1024:
                raise InvalidEvidence("Unbounded instrumented artifact archive")
            names = set()
            for member in members:
                parts = Path(member.name).parts
                if member.isdir() and member.name in (".", "./"):
                    continue
                if not member.isfile() or Path(member.name).is_absolute() or ".." in parts:
                    raise InvalidEvidence("Unsafe/non-file artifact archive member")
                target = directory / member.name
                if target in names:
                    raise InvalidEvidence("Duplicate artifact archive member")
                names.add(target)
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open("xb") as stream:
                    stream.write(archive.extractfile(member).read())
    except (tarfile.TarError, OSError) as error:
        raise InvalidEvidence(f"Cannot read instrumented artifacts: {error}") from error


def run_instrumentation(adb: Adb, plan: dict, cell, method: str, fixture: str, editor: str | None = None,
                        expire_authority: bool = False) -> dict:
    fresh_setup(adb, plan, cell.api, cell.locale)
    token = f"{cell.id}-{fixture}-{editor or method.split('#')[1]}-{uuid.uuid4().hex}"
    arguments = {"class": method, "acceptanceCellId": token, "fixture": fixture,
                 "expectedDp": str(cell.dp), "expectedLocale": cell.locale,
                 "expectedFontScale": cell.font, "expectedApi": str(cell.api)}
    if editor is not None:
        arguments.update(editor=editor, ambientWaitMs="15000", wakeTimeoutMs="15000",
                         expireAuthority=str(expire_authority).lower())
    command = ["am", "instrument", "-w", "-r"]
    for key, value in arguments.items():
        command += ["-e", key, value]
    command.append(plan["instrumentation"])
    clock_before = uptime_ms(adb.shell("cat", "/proc/uptime"))
    result = adb.shell(*command, timeout=210 if expire_authority else 180)
    clock_after = uptime_ms(adb.shell("cat", "/proc/uptime"))
    test_error = None
    try:
        identities = instrumentation_results(result, {tuple(method.split("#"))})
    except (InvalidEvidence, ObservedFailure) as error:
        test_error = error
        identities = []
    folder = f"/sdcard/Android/data/{plan['package']}/files/wear-acceptance/{token}"
    artifacts = adb.directory / token
    artifacts.mkdir()
    try:
        raw = adb.command(["exec-out", "tar", "-C", folder, "-cf", "-", "."], binary=True)
        extract_artifacts(raw, artifacts)
    except InvalidEvidence as error:
        if test_error:
            raise test_error from error
        raise
    if test_error:
        raise test_error
    config = strict_json((artifacts / "config.json").read_text())
    receipt = strict_json((artifacts / "receipt.json").read_text())
    validate_ui_receipt(receipt, config, cell, token, fixture, method.split("#")[1])
    validate_elapsed_calibration(config, clock_before, clock_after)
    if method != UI_METHOD and receipt["sourceClassification"] != "SYNTHETIC_RUNTIME":
        raise InvalidEvidence("A static preview cannot prove runtime interaction")
    labels = ("before-sleep", "system-ambient", "after-wake") if editor is not None else (
        ("reps-upper", "weight-upper", "weight-unset", "expired", "rotary-retryable", "rotary-complete", "submitted")
        if method == ROTARY_METHOD else ("first-view",))
    required = tuple(label + suffix for label in labels
                     for suffix in (".png", "-system.png", "-merged.txt", "-unmerged.txt", "-bounds.json"))
    for name in required:
        path = artifacts / name
        if name not in receipt["artifacts"] or not path.is_file() or path.stat().st_size == 0:
            raise InvalidEvidence(f"Missing instrumented evidence: {name}")
    if editor is not None and receipt.get("expireAuthority") is not expire_authority:
        raise InvalidEvidence("Ambient receipt does not identify the requested expiry scenario")
    return {"invocation": token, "fixture": fixture, "editor": editor, "tests": identities,
            "expire_authority": expire_authority,
            "elapsed_calibration": {"before_ms": clock_before, "after_ms": clock_after,
                                    "observed_ms": config["elapsedRealtimeMs"]},
            "artifacts": str(artifacts.relative_to(adb.directory)), "receipt": receipt}


def run_cell(adb: Adb, plan: dict, cell, selected: list[str] | None = None) -> dict:
    preflight(adb, plan, cell.api, test_apk=True)
    invocations = []
    selected = list(UI_INVOCATIONS) if selected is None else selected
    if len(set(selected)) != len(selected) or not set(selected) <= set(UI_INVOCATIONS):
        raise InvalidEvidence("Duplicate or unknown UI invocation selection")
    for selection in UI_INVOCATIONS:
        if selection not in selected:
            continue
        began = utc()
        try:
            if selection.startswith("fixture:"):
                result = run_instrumentation(adb, plan, cell, UI_METHOD, selection.split(":", 1)[1])
            elif selection == "rotary":
                result = run_instrumentation(adb, plan, cell, ROTARY_METHOD, "active_boundary")
            else:
                result = run_instrumentation(adb, plan, cell, AMBIENT_METHOD, "active_boundary", selection.split(":", 1)[1])
            verify_installed(adb, plan, plan["package"], test_apk=True)
            row = {"selection": selection, "status": "PASS", "detail": result}
        except ObservedFailure as error:
            row = {"selection": selection, "status": "FAIL", "reason": str(error)}
        except (InvalidEvidence, OSError, ValueError) as error:
            row = {"selection": selection, "status": "BLOCKED", "reason": str(error)}
        row.update(started_at=began, finished_at=utc())
        invocations.append(row)
        write_json(adb.directory / "invocations.json", {"invocations": invocations, "expected": selected})
    return {"cell": asdict(cell), "invocations": invocations, "selected": selected,
            "expected": list(UI_INVOCATIONS), "complete": set(selected) == set(UI_INVOCATIONS)}


def observation_verdict(detail: dict) -> tuple[str, str]:
    failures = [row for row in detail.get("invocations", []) if row["status"] == "FAIL"]
    blocked = [row for row in detail.get("invocations", []) if row["status"] == "BLOCKED"]
    if failures:
        return "FAIL", f"{len(failures)} independent invocation(s) failed; first: {failures[0]['selection']}: {failures[0]['reason']}"
    if blocked:
        return "BLOCKED", f"{len(blocked)} independent invocation(s) inconclusive; first: {blocked[0]['selection']}: {blocked[0]['reason']}"
    if detail.get("complete") is False:
        return "BLOCKED", "Selected observations passed, but this is an explicitly partial cell"
    return "PASS", "All declared observations completed"


def scenario(adb: Adb, plan: dict, name: str) -> None:
    if name not in ("refresh", "disconnect", "terminal"):
        raise InvalidEvidence("Scenario is outside the headless allowlist")
    result = adb.shell("am", "broadcast", "--user", str(plan["user"]), "-n", f"{plan['package']}/{RECEIVER}",
                       "-a", RECEIVER_ACTION, "--es", "scenario", name)
    no_shell_error(result)
    expected = f'Broadcast completed: result=-1, data="accepted:{name}"'
    if sum(line.strip() == expected for line in result.splitlines()) != 1:
        raise InvalidEvidence("Headless scenario did not return its exact success acknowledgement")


def parse_sections(text: str, names: tuple[str, ...]) -> dict[str, str]:
    matches = list(re.finditer(r"(?m)^@@WEAR:([a-z_]+)\r?$", text))
    if tuple(match[1] for match in matches) != names or text[:matches[0].start()].strip():
        raise InvalidEvidence("Missing/reordered shell observation fields")
    return {match[1]: text[match.end(): matches[index + 1].start() if index + 1 < len(matches) else len(text)].strip()
            for index, match in enumerate(matches)}


def observe(adb: Adb, plan: dict) -> Observation:
    package = shlex.quote(plan["package"])
    script = f'''set -e
printf '@@WEAR:boot\\n'; cat /proc/sys/kernel/random/boot_id
printf '@@WEAR:before\\n'; cat /proc/uptime
printf '@@WEAR:notifications\\n'; cmd notification list
printf '@@WEAR:process\\n'
pids=$(pidof {package} || true)
if [ -z "$pids" ]; then printf 'ABSENT\\n'; else
  set -- $pids
  [ "$#" -eq 1 ] || exit 90
  printf '%s\\n' "$1"
  run-as {package} cat /proc/"$1"/stat
fi
printf '@@WEAR:after\\n'; cat /proc/uptime
'''
    fields = parse_sections(adb.script(script, timeout=5), ("boot", "before", "notifications", "process", "after"))
    if fields["process"] == "ABSENT":
        pid = birth = None
    else:
        pid_line, separator, stat = fields["process"].partition("\n")
        if not separator or not pid_line.isdigit():
            raise InvalidEvidence("Malformed process observation")
        pid = int(pid_line)
        birth = process_birth(stat, pid)
    sample = Observation(boot_id(fields["boot"]), uptime_ms(fields["before"]), uptime_ms(fields["after"]),
                         notification_key(fields["notifications"], plan["package"], plan["user"]), pid, birth)
    validate_observation(sample)
    with (adb.directory / "observations.jsonl").open("a") as stream:
        stream.write(json.dumps(asdict(sample)) + "\n")
    return sample


def deadline(adb: Adb, key: str) -> int:
    return notification_deadline(adb.shell("cmd", "notification", "get", key, timeout=5), key)


def activity_sample(adb: Adb, plan: dict, api: int) -> dict:
    raw = adb.shell("dumpsys", "activity", "activities", timeout=5)
    resumed = resumed_activities(raw)
    if api >= 36:
        if not resumed:
            raise InvalidEvidence("No recognizable resumed Activity record")
        if not any(f" {plan['package']}/" in row for row in resumed):
            raise ObservedFailure("Ongoing app returned to another Activity during retention")
    return {"resumed": resumed, "retention_guarantee": "required" if api >= 36 else "N/A: Wear OS 3"}


def start_trial(adb: Adb, plan: dict, api: int) -> Observation:
    preflight(adb, plan, api, test_apk=True)
    cell = cell_by_id(f"api{api}-192-ru-1.24")
    calibration = run_instrumentation(adb, plan, cell, UI_METHOD, "active_boundary")
    write_json(adb.directory / "pre-trial-calibration.json", calibration)
    validate_passive_timeout(calibration["receipt"]["configuration"])
    # The instrumentation has finished. The timed trial starts in a new ordinary app process.
    fresh_setup(adb, plan, api, "ru")
    raw = adb.shell("am", "start", "-W", "--user", str(plan["user"]), "-n", f"{plan['package']}/{ACTIVITY_CLASS}",
                    "--es", "wear_surface_fixture", "active_boundary")
    if not re.search(r"(?m)^Status: ok\s*$", raw):
        raise InvalidEvidence("Could not launch initial synthetic session")
    initial = observe(adb, plan)
    if initial.key is None or initial.pid is None:
        raise InvalidEvidence("Initial admitted session has no notification/process")
    adb.screenshot("initial.png")
    return initial


def background_for_death(adb: Adb, plan: dict) -> dict:
    home = adb.shell("cmd", "package", "resolve-activity", "--brief", "--components", "--user", str(plan["user"]),
                     "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME").strip()
    no_shell_error(home)
    adb.shell("input", "keyevent", "KEYCODE_HOME")
    until = time.monotonic() + 5
    reason = "HOME transition was not observed"
    while time.monotonic() < until:
        raw = adb.shell("dumpsys", "activity", "activities", timeout=5)
        try:
            state = background_activity_state(raw, plan["package"], plan["user"], home)
            write_json(adb.directory / "background-before-kill.json", state)
            return state
        except InvalidEvidence as error:
            reason = str(error)
        time.sleep(POLL_SECONDS)
    raise InvalidEvidence(reason)


def validate_death_signal(api: int, scenario_name: str, death_signal: str) -> None:
    if death_signal not in ("kill", "term-default"):
        raise InvalidEvidence("Unknown process-death instrument")
    if death_signal == "term-default" and (api != 30 or scenario_name == "retention"):
        raise InvalidEvidence("Default-disposition SIGTERM is only available for explicit API30 death trials")


def default_term_disposition(text: str, expected_pid: int) -> dict:
    no_shell_error(text)
    if type(expected_pid) is not int or expected_pid <= 0 or not text.endswith("\n"):
        raise InvalidEvidence("Missing signal status or invalid expected PID")
    if len(text.encode("utf-8")) > 65536:
        raise InvalidEvidence("Unexpectedly large process status")
    fields = {}
    for name in ("Pid", "Tgid", "SigCgt", "SigIgn", "SigBlk"):
        values = re.findall(r"(?m)^" + name + r":[ \t]*([^\r\n]*)$", text)
        if len(values) != 1:
            raise InvalidEvidence("Missing or duplicate signal status field: " + name)
        fields[name] = values[0]
    for name in ("Pid", "Tgid"):
        if not re.fullmatch(r"[1-9][0-9]*", fields[name]) or int(fields[name]) != expected_pid:
            raise InvalidEvidence("Signal status PID/Tgid differs from the verified process")
    for name in ("SigCgt", "SigIgn", "SigBlk"):
        if not re.fullmatch(r"[0-9a-fA-F]{16}", fields[name]):
            raise InvalidEvidence("Malformed signal mask: " + name)
        if int(fields[name], 16) & 0x4000:
            raise InvalidEvidence("SIGTERM is caught, ignored, or blocked: " + name)
    return {"pid": expected_pid, "tgid": expected_pid, "signal": 15,
            "masks_hex": {name: fields[name] for name in ("SigCgt", "SigIgn", "SigBlk")},
            "default_disposition_observed": True, "main_thread_unblocked_observed": True}


def signal_process_death(adb: Adb, plan: dict, before: Observation, api: int, death_signal: str) -> dict:
    validate_death_signal(api, "death", death_signal)
    signal = "-15" if death_signal == "term-default" else "-9"
    record = {"backend": "RUN_AS_SIGTERM_DEFAULT_DISPOSITION" if death_signal == "term-default" else "RUN_AS_SIGKILL",
              "api": api, "signal": -int(signal), "expected_pid": before.pid,
              "expected_birth_ticks": before.birth, "no_fallback_or_retry": True,
              "status": "PRECONDITIONS_PENDING_SIGNAL_NOT_YET_SENT"}
    write_json(adb.directory / "signal-backend.json", record)
    if death_signal == "term-default":
        raw = adb.shell("run-as", plan["package"], "cat", f"/proc/{before.pid}/status")
        record["disposition"] = default_term_disposition(raw, before.pid)
        record["status_sha256"] = hashlib.sha256(raw.encode()).hexdigest()
        record["status"] = "PRECONDITIONS_VALIDATED_SIGNAL_NOT_YET_SENT"
        write_json(adb.directory / "signal-backend.json", record)
    # No device operation is inserted between this birth check and the signal.
    stat = adb.shell("run-as", plan["package"], "cat", f"/proc/{before.pid}/stat")
    if process_birth(stat, before.pid) != before.birth:
        raise InvalidEvidence("PID was reused before kill")
    no_shell_error(adb.shell("run-as", plan["package"], "kill", signal, str(before.pid)))
    record["status"] = "SIGNAL_COMMAND_RETURNED_SUCCESS_NOT_DEATH_PROOF"
    write_json(adb.directory / "signal-backend.json", record)
    return record


def death_trial(adb: Adb, plan: dict, api: int, disconnected: bool, death_signal: str = "kill") -> dict:
    validate_death_signal(api, "death", death_signal)
    initial = start_trial(adb, plan, api)
    original_deadline = deadline(adb, initial.key)
    if disconnected:
        scenario(adb, plan, "disconnect")
    before = observe(adb, plan)
    validate_observation(before, initial)
    if before.key != initial.key or (before.pid, before.birth) != (initial.pid, initial.birth):
        raise InvalidEvidence("Pre-kill process/notification changed")
    stop_at = deadline(adb, before.key)
    if disconnected and stop_at >= original_deadline:
        raise ObservedFailure("Disconnect did not shorten the absolute notification deadline")
    if before.after_ms >= stop_at:
        raise InvalidEvidence("Process kill would occur after the notification deadline")
    background = background_for_death(adb, plan)
    after_home = observe(adb, plan)
    validate_background_continuity(before, after_home, stop_at, deadline(adb, before.key))
    before = after_home
    cache = adb.command(["exec-out", "run-as", plan["package"], "cat", "no_backup/synthetic_watch_snapshot"], binary=True)
    (adb.directory / "cache-before-kill.bin").write_bytes(cache)
    signal_backend = signal_process_death(adb, plan, before, api, death_signal)
    samples = []
    previous = before
    until = time.monotonic() + max(0, (stop_at + WATCHDOG_MS - before.before_ms) / 1000) + 10
    while time.monotonic() < until:
        started = time.monotonic()
        sample = observe(adb, plan)
        validate_observation(sample, previous)
        if sample.pid is not None:
            raise InvalidEvidence("Target remained alive or restarted after kill")
        samples.append(sample)
        if sample.key is None or sample.before_ms >= stop_at + WATCHDOG_MS:
            break
        previous = sample
        time.sleep(max(0, POLL_SECONDS - (time.monotonic() - started)))
    result = removal_result(samples, stop_at, WATCHDOG_MS)
    adb.screenshot("after-removal.png")
    return {"initial": asdict(initial), "before_kill": asdict(before), "original_deadline_ms": original_deadline,
            "disconnected": disconnected, "background": background, "signal_backend": signal_backend,
            "removal": result, "cache_sha256": sha256(adb.directory / "cache-before-kill.bin")}


def retention_trial(adb: Adb, plan: dict, api: int) -> dict:
    initial = start_trial(adb, plan, api)
    prior_deadline = deadline(adb, initial.key)
    first_elapsed = initial.after_ms
    next_refresh = first_elapsed + REFRESH_SECONDS * 1000
    next_activity = first_elapsed
    receipts = []
    previous = initial
    # System ambient entry is setup, before the 500 ms observation cadence begins.
    adb.shell("input", "keyevent", "KEYCODE_SLEEP")
    previous = observe(adb, plan)
    validate_observation(previous, initial)
    if (previous.pid, previous.birth, previous.key) != (initial.pid, initial.birth, initial.key):
        raise InvalidEvidence("Retention process/notification changed during ambient setup")
    first_elapsed = previous.after_ms
    host_limit = time.monotonic() + RETENTION_SECONDS + 30
    while previous.after_ms - first_elapsed < RETENTION_SECONDS * 1000:
        started = time.monotonic()
        if time.monotonic() > host_limit:
            raise InvalidEvidence("Retention monotonic clock did not reach the finite observation window")
        sample = observe(adb, plan)
        validate_retention_progress(sample, previous, next_refresh)
        if (sample.pid, sample.birth) != (initial.pid, initial.birth):
            raise InvalidEvidence("Target process changed during retention")
        if sample.key != initial.key:
            raise ObservedFailure("Ongoing notification disappeared during fresh retention")
        if sample.after_ms >= next_refresh:
            scenario(adb, plan, "refresh")
            refreshed = deadline(adb, sample.key)
            if refreshed <= prior_deadline:
                raise ObservedFailure("Fresh correlated refresh did not renew the lifecycle")
            receipts.append({"elapsed_ms": sample.after_ms, "deadline_ms": refreshed})
            prior_deadline = refreshed
            next_refresh += REFRESH_SECONDS * 1000
        if sample.after_ms >= next_activity:
            receipts.append({"elapsed_ms": sample.after_ms, **activity_sample(adb, plan, api)})
            next_activity = sample.after_ms + 1000
        previous = sample
        time.sleep(max(0, POLL_SECONDS - (time.monotonic() - started)))
    adb.screenshot("retention-end.png")
    return {"duration_ms": previous.after_ms - first_elapsed, "required_duration_ms": RETENTION_SECONDS * 1000,
            "refresh_interval_ms": REFRESH_SECONDS * 1000, "activity_sample_interval_ms": 1000,
            "notification_poll_target_ms": 250, "notification_poll_max_ms": 500, "initial": asdict(initial),
            "final": asdict(previous), "events": receipts,
            "retention_guarantee": "observed on Wear OS 6" if api >= 36 else "N/A: older OS behavior recorded"}


def run_case(args, plan: dict, case_id: str, operation) -> None:
    destination = create_case_directory(args.cohort, case_id)
    began = utc()
    adb = Adb(args.serial, destination, args.adb)
    detail = {}
    status, reason = "BLOCKED", "Interrupted or unexpected runner failure"
    try:
        detail = operation(adb)
        verify_installed(adb, plan, plan["package"], test_apk=plan["expected"][case_id]["kind"] in ("ui", "ambient_expiry"))
        status, reason = observation_verdict(detail)
    except ObservedFailure as error:
        status, reason = "FAIL", str(error)
    except (InvalidEvidence, OSError, ValueError) as error:
        status, reason = "BLOCKED", str(error)
    finally:
        unchanged = plan["source"] == source_identity()
        if not unchanged:
            status, reason = "BLOCKED", "Source/test/tool bytes changed during the run"
        write_json(destination / "receipt.json", {"schema": 1, "cohort": plan["cohort"], "case_id": case_id,
                   "serial": args.serial, "started_at": began, "finished_at": utc(), "status": status,
                   "reason": reason, "detail": detail, "source_unchanged": unchanged,
                   "artifacts": artifact_inventory(destination)})
    print(json.dumps({"case_id": case_id, "status": status, "reason": reason}))
    if status != "PASS":
        raise SystemExit(2 if status == "BLOCKED" else 1)


def report(directory: Path) -> dict:
    plan = validate_plan(directory)
    receipts = {}
    require_local_path(directory, directory / "cases")
    for path in sorted((directory / "cases").rglob("receipt.json")):
        # Instrumented nested receipts are dependencies of their case, not new case results.
        relative = str(path.parent.relative_to(directory / "cases"))
        require_local_path(directory, path)
        row = strict_json(path.read_text())
        if relative not in plan["expected"]:
            if row.get("acceptanceCellId") and row.get("hostType") == "real-MainActivity":
                continue
            raise InvalidEvidence(f"Unexpected case receipt: {relative}")
        if row.get("case_id") != relative or row.get("cohort") != plan["cohort"]:
            raise InvalidEvidence("Receipt path/cohort mismatch")
        if row.get("artifacts") != artifact_inventory(path.parent):
            raise InvalidEvidence(f"Evidence files changed or went missing: {relative}")
        receipts[relative] = row
    unpublished = []
    for path in sorted((directory / "manual-attempts").glob("*")):
        require_local_path(directory, path)
        unpublished.append(str(path.relative_to(directory)))
    result = {"cohort": plan["cohort"], "source": plan["source"]["head"], **inventory_result(receipts),
              "unpublished_manual_attempts": unpublished,
              "cases": {key: {"status": row["status"], "reason": row["reason"]} for key, row in receipts.items()}}
    write_json(directory / "report.json", result)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    planning = commands.add_parser("plan")
    planning.add_argument("--cohort", type=Path, required=True)
    planning.add_argument("--apk", type=Path, required=True)
    planning.add_argument("--test-apk", type=Path, required=True)
    planning.add_argument("--package", default="io.github.stslex.workeeper.dev",
                          choices=("io.github.stslex.workeeper.dev", "io.github.stslex.workeeper"))
    planning.add_argument("--test-package")
    planning.add_argument("--user", type=int, choices=(0,), default=0)
    for name in ("run-cell", "run-lifecycle", "run-ambient-expiry"):
        command = commands.add_parser(name)
        command.add_argument("--cohort", type=Path, required=True)
        command.add_argument("--serial", required=True)
        command.add_argument("--adb", default="adb")
        if name == "run-cell":
            command.add_argument("--cell", required=True, choices=[cell.id for cell in matrix()])
            command.add_argument("--only", action="append", choices=UI_INVOCATIONS,
                                 help="Run selected invocations; cell remains BLOCKED/partial, never PASS")
        elif name == "run-lifecycle":
            command.add_argument("--api", type=int, required=True, choices=(30, 36))
            command.add_argument("--scenario", required=True, choices=("retention", "death-fresh", "death-disconnect"))
            command.add_argument("--repetition", type=int, required=True, choices=(1, 2, 3))
            command.add_argument("--death-signal", choices=("kill", "term-default"), default="kill",
                                 help="Default SIGKILL; explicit term-default requires API30 default/unblocked SIGTERM")
        else:
            command.add_argument("--api", type=int, required=True, choices=(30, 36))
            command.add_argument("--editor", required=True, choices=("reps", "weight"))
    reporting = commands.add_parser("report")
    reporting.add_argument("--cohort", type=Path, required=True)
    manual = commands.add_parser("record-manual")
    manual.add_argument("--cohort", type=Path, required=True)
    manual.add_argument("--case", required=True)
    manual.add_argument("--status", required=True, choices=("PASS", "FAIL", "BLOCKED"))
    manual.add_argument("--reason", required=True)
    manual.add_argument("--artifact", type=Path, action="append", required=True)
    args = parser.parse_args()
    args.cohort = args.cohort.resolve()
    if args.cohort == ROOT or ROOT in args.cohort.parents:
        raise InvalidEvidence("Evidence output must be outside the repository")
    if args.command == "plan":
        args.cohort.mkdir(parents=True, exist_ok=False)
        test_package = args.test_package or args.package + ".test"
        write_json(args.cohort / "plan.json", {"schema": 1, "cohort": uuid.uuid4().hex, "created_at": utc(),
                   "source": source_identity(), "package": args.package, "test_package": test_package,
                   "instrumentation": test_package + "/androidx.test.runner.AndroidJUnitRunner", "user": args.user,
                   "apk": {"path": str(args.apk.resolve()), "sha256": sha256(args.apk)},
                   "test_apk": {"path": str(args.test_apk.resolve()), "sha256": sha256(args.test_apk)},
                   "expected": expected_inventory(), "physical_device_claim": False})
        print(args.cohort / "plan.json")
        return
    plan = validate_plan(args.cohort)
    if args.command == "run-cell":
        cell = cell_by_id(args.cell)
        run_case(args, plan, f"ui/{cell.id}", lambda adb: run_cell(adb, plan, cell, args.only))
    elif args.command == "run-lifecycle":
        validate_death_signal(args.api, args.scenario, args.death_signal)
        key = f"lifecycle/api{args.api}/{args.scenario}/{args.repetition}"
        operation = (lambda adb: retention_trial(adb, plan, args.api)) if args.scenario == "retention" else (
            lambda adb: death_trial(adb, plan, args.api, args.scenario == "death-disconnect", args.death_signal))
        run_case(args, plan, key, operation)
    elif args.command == "run-ambient-expiry":
        cell = cell_by_id(f"api{args.api}-192-ru-1.24")
        def expiry(adb):
            preflight(adb, plan, cell.api, test_apk=True)
            return run_instrumentation(adb, plan, cell, AMBIENT_METHOD, "active_boundary", args.editor, True)
        run_case(args, plan, f"ambient-expiry/api{args.api}/{args.editor}", expiry)
    elif args.command == "record-manual":
        if plan["expected"].get(args.case, {}).get("kind") != "manual":
            raise InvalidEvidence("Only declared manual cases can receive an operator verdict")
        prepared = []
        for path in args.artifact:
            if not path.is_file() or not path.stat().st_size:
                raise InvalidEvidence("Manual verdict needs existing, nonempty supporting evidence")
            digest = hashlib.sha256()
            size = 0
            with path.open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
                    size += len(chunk)
            if not size:
                raise InvalidEvidence("Manual evidence became empty during preflight")
            prepared.append((path, size, digest.hexdigest()))
        destination = create_manual_staging_directory(args.cohort, args.case)
        for index, (path, size, expected_hash) in enumerate(prepared):
            content = path.read_bytes()
            if len(content) != size or hashlib.sha256(content).hexdigest() != expected_hash:
                raise InvalidEvidence("Manual evidence changed after preflight")
            (destination / f"operator-{index:02d}-{path.name}").write_bytes(content)
        write_json(destination / "receipt.json", {"schema": 1, "cohort": plan["cohort"], "case_id": args.case,
                   "status": args.status, "reason": args.reason, "recorded_at": utc(), "operator_attestation": True,
                   "artifacts": artifact_inventory(destination)})
        publish_manual_directory(args.cohort, args.case, destination)
    else:
        result = report(args.cohort)
        print(json.dumps(result, indent=2))
        if result["status"] != "PASS":
            raise SystemExit(1 if result["status"] == "FAIL" else 2)


if __name__ == "__main__":
    try:
        main()
    except InvalidEvidence as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        raise SystemExit(2)

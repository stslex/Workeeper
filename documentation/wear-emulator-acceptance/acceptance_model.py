#!/usr/bin/env python3
"""Pure, fail-closed evidence parsing for the Wear emulator acceptance runner."""

from __future__ import annotations

import hashlib
import itertools
import json
import re
from dataclasses import asdict, dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path


class InvalidEvidence(ValueError):
    """The observation cannot establish either application success or failure."""


class ObservedFailure(AssertionError):
    """A complete observation contradicts the declared acceptance condition."""


APIS = (30, 36)
FIXTURES = (
    "active_boundary", "weightless", "field_error", "weight_error", "command_in_flight",
    "unset_weight", "anonymous_exercise", "anonymous_complete", "refresh_required",
    "disconnected", "no_sets", "unsupported", "payload_too_large", "complete", "retryable",
    "protocol_mismatch", "no_session", "loading",
)
NOTIFICATION_ID = 35501
UPTIME_PRECISION_MS = 10
DEADLINE_EXTRA = "io.github.stslex.workeeper.ongoing.stop_at_elapsed_ms"
STATUSES = frozenset(("PASS", "FAIL", "BLOCKED", "N/A"))
ACTIVITY_CLASS = "io.github.stslex.workeeper.wear.MainActivity"
ACCEPTANCE_PACKAGE = "io.github.stslex.workeeper.wear.acceptance"


@dataclass(frozen=True)
class Cell:
    api: int
    dp: int
    locale: str
    font: str

    @property
    def id(self) -> str:
        return f"api{self.api}-{self.dp}-{self.locale}-{self.font}"


def matrix() -> tuple[Cell, ...]:
    return tuple(Cell(*parts) for parts in itertools.product(APIS, (192, 240), ("en", "ru"), ("1.00", "1.24")))


def cell_by_id(value: str) -> Cell:
    matches = [cell for cell in matrix() if cell.id == value]
    if len(matches) != 1:
        raise InvalidEvidence(f"Unknown matrix cell: {value}")
    return matches[0]


def expected_inventory() -> dict[str, dict]:
    result = {f"ui/{cell.id}": {"kind": "ui", **asdict(cell)} for cell in matrix()}
    for api, scenario, repetition in itertools.product(APIS, ("retention", "death-fresh", "death-disconnect"), range(1, 4)):
        result[f"lifecycle/api{api}/{scenario}/{repetition}"] = {
            "kind": "lifecycle", "api": api, "scenario": scenario, "repetition": repetition,
        }
    for api, editor in itertools.product(APIS, ("reps", "weight")):
        result[f"ambient-expiry/api{api}/{editor}"] = {
            "kind": "ambient_expiry", "api": api, "editor": editor, "dp": 192, "locale": "ru", "font": "1.24",
        }
    for cell in matrix():
        result[f"manual/visual/{cell.id}"] = {"kind": "manual", "api": cell.api, "subject": "visual"}
    subjects = ("system-tile", "notification-denial-restoration", "ongoing-return", "release-boundary",
                "store-debug-parity", "process-death-restoration", "inactivity-baseline", "disconnect-terminal",
                "ambient-capabilities")
    for api, subject in itertools.product(APIS, subjects):
        result[f"manual/api{api}/{subject}"] = {"kind": "manual", "api": api, "subject": subject}
    return result


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def strict_json(text: str) -> dict:
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise InvalidEvidence(f"Duplicate JSON key: {key}")
            result[key] = value
        return result
    try:
        value = json.loads(text, object_pairs_hook=unique)
    except (ValueError, TypeError) as error:
        raise InvalidEvidence(f"Invalid JSON: {error}") from error
    if not isinstance(value, dict):
        raise InvalidEvidence("Expected a JSON object")
    return value


def no_shell_error(text: str) -> None:
    if re.search(r"(?im)^\s*(?:error[: ]|exception|securityexception|permission denial|unknown command|can't find service|/system/bin/sh:)", text):
        raise InvalidEvidence("Shell returned an error instead of an observation")


def uptime_ms(text: str) -> int:
    match = re.fullmatch(r"\s*(\d+\.\d{2})\s+\d+\.\d{2}\s*", text)
    if not match:
        raise InvalidEvidence("Malformed /proc/uptime; require both raw centisecond fields")
    return int(Decimal(match[1]) * 1000)


def boot_id(text: str) -> str:
    value = text.strip()
    if not re.fullmatch(r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}", value):
        raise InvalidEvidence("Missing or invalid kernel boot ID")
    return value


def process_birth(text: str, expected_pid: int) -> int:
    # The comm field can contain spaces and parentheses; split after its last closing parenthesis.
    match = re.fullmatch(r"(\d+) \((.*)\) (.+)\s*", text.strip())
    if not match or int(match[1]) != expected_pid:
        raise InvalidEvidence("PID/stat identity mismatch")
    fields = match[3].split()
    if len(fields) < 20 or not fields[19].isdigit():
        raise InvalidEvidence("Missing /proc/PID/stat starttime")
    return int(fields[19])


def notification_key(text: str, package: str, user: int) -> str | None:
    no_shell_error(text)
    matches = []
    for line in text.splitlines():
        if not line.strip():
            continue
        fields = line.strip().split("|")
        if len(fields) < 5 or not (fields[0].isdigit() or fields[0] == "-1") or not fields[2].lstrip("-").isdigit() or not fields[4].isdigit():
            raise InvalidEvidence("Malformed notification list record")
        if fields[1] == package and int(fields[0]) == user and int(fields[2]) == NOTIFICATION_ID and fields[3] == "null":
            matches.append(line.strip())
    if len(matches) > 1:
        raise InvalidEvidence("Multiple matching notification keys")
    return matches[0] if matches else None


def notification_deadline(text: str, key: str) -> int:
    no_shell_error(text)
    if not re.search(rf"(?m)^NotificationRecord\([^\r\n]*\bkey={re.escape(key)}(?:\s|\)|: Notification\()", text):
        raise InvalidEvidence("Notification dump does not identify the selected key")
    # NotificationRecord.dump formats Long extras as `name=Long (123)`; accept plain Long
    # values too, while requiring a complete, unique line rather than a loose number search.
    pattern = rf"(?m)^\s*{re.escape(DEADLINE_EXTRA)}\s*=\s*(?:Long\s*\((\d+)\)|(\d+))\s*$"
    matches = re.findall(pattern, text)
    if len(matches) != 1:
        raise InvalidEvidence("Missing, duplicate, or malformed absolute notification deadline")
    deadline = int(matches[0][0] or matches[0][1])
    if deadline <= 0:
        raise InvalidEvidence("Non-positive notification deadline")
    return deadline


@dataclass(frozen=True)
class Observation:
    boot: str
    before_ms: int
    after_ms: int
    key: str | None
    pid: int | None
    birth: int | None


def validate_observation(current: Observation, previous: Observation | None = None) -> None:
    boot_id(current.boot)
    if current.before_ms < 0 or current.after_ms < current.before_ms:
        raise InvalidEvidence("Reversed elapsed-time bracket")
    if (current.pid is None) != (current.birth is None):
        raise InvalidEvidence("Incomplete process identity")
    if previous:
        if previous.boot != current.boot:
            raise InvalidEvidence("Device rebooted during the observation")
        if current.before_ms < previous.after_ms:
            raise InvalidEvidence("Stale/reversed observation time")


def validate_retention_progress(current: Observation, previous: Observation, next_refresh_ms: int) -> None:
    validate_observation(current, previous)
    if current.before_ms - previous.before_ms > 500:
        raise InvalidEvidence("Retention observation polling gap exceeded 500 ms")
    if current.after_ms - current.before_ms > 500:
        raise InvalidEvidence("Retention observation command exceeded the 500 ms resolution budget")
    if current.after_ms - next_refresh_ms > 2000:
        raise InvalidEvidence("Synthetic refresh missed its 60-second schedule by more than 2 seconds")


def validate_elapsed_calibration(config: dict, before_ms: int, after_ms: int) -> None:
    value = config.get("elapsedRealtimeMs")
    if type(value) is not int or after_ms < before_ms or not before_ms <= value < after_ms + 10:
        raise InvalidEvidence("Actual SystemClock.elapsedRealtime did not match the external boot-time bracket")


def resumed_activities(text: str) -> list[str]:
    no_shell_error(text)
    if "ACTIVITY MANAGER ACTIVITIES" not in text:
        raise InvalidEvidence("Missing ActivityManager observation header")
    return re.findall(r"(?m)^\s*(?:mResumedActivity|topResumedActivity)\s*[:=]\s*ActivityRecord\{([^\r\n]+)\}", text)


def background_activity_state(text: str, package: str, user: int, home_component: str) -> dict:
    def component(value):
        match = re.fullmatch(r"([A-Za-z_][\w.]*)/([\w.$]+)", value)
        if not match:
            raise InvalidEvidence("Missing or malformed resolved HOME component")
        owner, activity = match.groups()
        return owner + "/" + (owner + activity if activity.startswith(".") else activity)

    def identity(record):
        match = re.search(r"(?:^|\s)u(\d+)\s+([^\s}]+)", record)
        if not match:
            raise InvalidEvidence("Activity record lacks user/component identity")
        return int(match[1]), component(match[2])

    home_lines = home_component.strip().splitlines()
    if len(home_lines) == 2 and re.fullmatch(
        r"priority=-?\d+ preferredOrder=-?\d+ match=0x[0-9a-fA-F]+ specificIndex=-?\d+ isDefault=(?:true|false)",
        home_lines[0],
    ):
        home_lines = home_lines[1:]
    if len(home_lines) != 1:
        raise InvalidEvidence("Missing or ambiguous resolved HOME component")
    home = component(home_lines[0])
    resumed = [identity(record) for record in resumed_activities(text)]
    if (user, home) not in resumed or any(owner == user and value.startswith(package + "/") for owner, value in resumed):
        raise InvalidEvidence("HOME is not resumed or target Activity is still resumed")
    targets = []
    lines = text.splitlines()
    for index, line in enumerate(lines):
        heading = re.fullmatch(r"(\s*)\*?\s*Hist\s+#\d+:\s+ActivityRecord\{([^\r\n]+)\}\s*", line)
        if not heading:
            continue
        owner, activity = identity(heading[2])
        if owner != user or not activity.startswith(package + "/"):
            continue
        indent = len(heading[1])
        details = []
        for detail in lines[index + 1:]:
            if detail.strip() and len(detail) - len(detail.lstrip()) <= indent:
                break
            details.append(detail)
        block = "\n".join(details)
        if re.findall(r"(?m)^\s*state=(\w+)(?:\s|$)", block) != ["STOPPED"]:
            raise InvalidEvidence("Target Activity has not reached STOPPED")
        for flag in ("mVisibleRequested", "mVisible"):
            if re.findall(r"\b" + flag + r"=(true|false)\b", block) != ["false"]:
                raise InvalidEvidence("Target Activity visibility is not conclusively false")
        targets.append(activity)
    if not targets:
        raise InvalidEvidence("Target Activity record missing after HOME")
    return {"home_component": home, "resumed": resumed, "stopped_nonvisible_targets": targets}


def validate_background_continuity(previous: Observation, current: Observation,
                                   previous_deadline: int, current_deadline: int) -> None:
    validate_observation(current, previous)
    if current.pid is None or current.key is None or (current.pid, current.birth, current.key) != (previous.pid, previous.birth, previous.key):
        raise InvalidEvidence("Process or notification changed while moving to HOME")
    if current_deadline != previous_deadline or current.after_ms >= current_deadline:
        raise InvalidEvidence("Notification deadline changed or expired while moving to HOME")


def validate_ui_receipt(receipt: dict, config: dict, cell: Cell, token: str, fixture: str, method: str) -> None:
    validate_config(config, cell)
    static = fixture in {"weight_error", "retryable", "protocol_mismatch", "loading"}
    expected_source = "STATIC_PREVIEW" if static else "SYNTHETIC_RUNTIME"
    if (receipt.get("schema") != 1 or receipt.get("acceptanceCellId") != token
            or receipt.get("status") != "PASS" or receipt.get("fixture") != fixture
            or receipt.get("method") != method or receipt.get("hostType") != "real-MainActivity"
            or receipt.get("configuration") != config or receipt.get("sourceClassification") != expected_source):
        raise InvalidEvidence("Missing/stale/misclassified instrumented receipt")
    times = [receipt.get("startedAtEpochMs"), receipt.get("finishedAtEpochMs")]
    if any(type(value) is not int or value <= 0 for value in times) or times[1] < times[0]:
        raise InvalidEvidence("Invalid instrumented receipt timing")
    artifacts = receipt.get("artifacts")
    if (not isinstance(artifacts, list) or not artifacts
            or any(not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_.-]+", name) for name in artifacts)
            or len(set(artifacts)) != len(artifacts)):
        raise InvalidEvidence("Missing/unsafe instrumented artifact inventory")


def removal_result(samples: list[Observation], deadline_ms: int, tolerance_ms: int = 5000) -> dict:
    if len(samples) < 2 or samples[0].key is None:
        raise InvalidEvidence("Removal trial needs an observed present notification")
    key = samples[0].key
    previous = None
    last_present = None
    for sample in samples:
        validate_observation(sample, previous)
        if sample.after_ms - sample.before_ms > 500:
            raise InvalidEvidence("Observation command exceeded the 500 ms resolution budget")
        if previous and sample.before_ms - previous.before_ms > 500:
            raise InvalidEvidence("Observation polling gap exceeded 500 ms")
        if sample.pid is not None:
            raise InvalidEvidence("Target process survived or restarted during death observation")
        if sample.key is not None and sample.key != key:
            raise InvalidEvidence("Notification identity changed")
        if sample.key is None:
            if last_present is None:
                raise InvalidEvidence("No present-to-absent transition")
            absent_upper_ms = sample.after_ms + UPTIME_PRECISION_MS
            if absent_upper_ms <= deadline_ms:
                raise ObservedFailure("Notification disappeared before its persisted deadline")
            if absent_upper_ms > deadline_ms + tolerance_ms:
                raise InvalidEvidence("Removal interval straddles or exceeds the declared watchdog")
            return {"last_present": asdict(last_present), "first_absent": asdict(sample),
                    "removal_interval_ms": [last_present.before_ms, absent_upper_ms],
                    "deadline_ms": deadline_ms, "tolerance_ms": tolerance_ms,
                    "clock_precision_ms": UPTIME_PRECISION_MS}
        last_present = sample
        previous = sample
    if samples[-1].before_ms >= deadline_ms + tolerance_ms:
        raise ObservedFailure("Notification still present at deadline + 5 seconds")
    raise InvalidEvidence("Observation stopped before its watchdog or before removal")


def instrumentation_results(text: str, expected: set[tuple[str, str]]) -> list[dict]:
    """Read actual AndroidJUnitRunner event identities, never console's aggregate alone."""
    fields = {}
    started = set()
    finished = {}
    final_codes = []
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            if separator:
                fields[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            try:
                code = int(line.split(":", 1)[1])
            except ValueError as error:
                raise InvalidEvidence("Invalid instrumentation status code") from error
            identity = (fields.get("class", ""), fields.get("test", ""))
            if identity not in expected:
                raise InvalidEvidence(f"Unexpected/missing test identity: {identity}")
            if code == 1:
                if identity in started:
                    raise InvalidEvidence(f"Duplicate test start: {identity}")
                started.add(identity)
            else:
                if identity not in started or identity in finished:
                    raise InvalidEvidence(f"Unpaired/duplicate test completion: {identity}")
                finished[identity] = {"class": identity[0], "method": identity[1], "code": code,
                                      "stack": fields.get("stack", "")}
            fields = {}
        elif line.startswith("INSTRUMENTATION_CODE: "):
            try:
                final_codes.append(int(line.split(":", 1)[1]))
            except ValueError as error:
                raise InvalidEvidence("Invalid final instrumentation code") from error
    if final_codes != [-1] or set(finished) != expected or started != expected:
        raise InvalidEvidence("Missing/incomplete instrumentation identities or final result")
    if any(row["code"] in (-3, -4) for row in finished.values()):
        raise InvalidEvidence("Skipped/assumption-failed test is not acceptance")
    assertion_types = {"AssertionError", "java.lang.AssertionError", "junit.framework.AssertionFailedError",
                       "org.opentest4j.AssertionFailedError", "junit.framework.ComparisonFailure",
                       "org.junit.ComparisonFailure", "org.junit.internal.ArrayComparisonFailure"}
    for row in finished.values():
        if row["code"] not in (0, -2):
            raise InvalidEvidence("Instrumentation execution failed or reported an unsupported status")
        if row["code"] == -2:
            first_line = next((line.strip() for line in row["stack"].splitlines() if line.strip()), "")
            failure_type = first_line.partition(":")[0].strip()
            if failure_type not in assertion_types:
                raise InvalidEvidence("Instrumentation failure lacks a top-level assertion or comparison failure")
    if any(row["code"] != 0 for row in finished.values()):
        raise ObservedFailure("Named instrumentation test failed")
    return list(finished.values())


def validate_passive_timeout(config: dict) -> None:
    value = config.get("screenOffTimeoutMs")
    if type(value) is not int or value != 15000:
        raise InvalidEvidence("Passive lifecycle trials require the measured 15000 ms screen timeout")


def validate_config(config: dict, cell: Cell) -> None:
    required = {"api": cell.api, "screenWidthDp": cell.dp, "screenHeightDp": cell.dp,
                "locale": cell.locale, "isScreenRound": True}
    for key, expected in required.items():
        if config.get(key) != expected:
            raise InvalidEvidence(f"Actual configuration {key}={config.get(key)!r}, expected {expected!r}")
    try:
        if abs(Decimal(str(config["fontScale"])) - Decimal(cell.font)) > Decimal("0.005"):
            raise InvalidEvidence("Actual fontScale differs from matrix cell")
    except (KeyError, InvalidOperation) as error:
        raise InvalidEvidence("Missing/malformed actual fontScale") from error


def inventory_result(receipts: dict[str, dict]) -> dict:
    expected = expected_inventory()
    unexpected = sorted(set(receipts) - set(expected))
    if unexpected:
        raise InvalidEvidence(f"Unexpected case IDs: {unexpected}")
    missing = sorted(set(expected) - set(receipts))
    counts = dict.fromkeys(sorted(STATUSES), 0)
    for case_id, row in receipts.items():
        status = row.get("status")
        if status not in STATUSES:
            raise InvalidEvidence(f"Unknown verdict: {case_id}")
        # No entire required trial/cell is inapplicable; capability-level N/A is recorded
        # inside the row, never substituted for running its other required assertions.
        if status == "N/A":
            raise InvalidEvidence(f"N/A cannot replace a required acceptance row: {case_id}")
        counts[status] += 1
    status = "FAIL" if counts["FAIL"] else "BLOCKED" if missing or counts["BLOCKED"] else "PASS"
    return {"status": status, "expected": len(expected), "recorded": len(receipts),
            "counts": counts, "missing": missing, "complete": not missing}

#!/usr/bin/env python3
"""Wear upload decision: FAIL, SKIP or UPLOAD from the Play tracks the deploy_wear lane read.

Spec: documentation/feature-specs/wear-release-pipeline.md §7.2 step 2. The lane reads Play in a
read-only edit (fastlane/play_state.rb) and writes the JSON this script decides on:

    python3 .github/scripts/wear_track_decision.py --state <json> --track wear:internal \
        --toml gradle/libs.versions.toml
    python3 .github/scripts/wear_track_decision.py --self-test

- no tracks at all: FAIL. An empty list proves nothing about the configured track.
- the configured track absent: FAIL, printing every track id. supply reads a missing track as an
  empty one (spec §3 F12), so a wrong id must never reach the upload.
- the expected Wear versionCode (1_000_000 + the TOML versionCode) already on it: SKIP.
- otherwise: UPLOAD.

Prints every track with its version codes, then `DECISION UPLOAD|SKIP` (exit 0) or
`DECISION FAIL: <reason>` (exit 1). Exit 2: the state or TOML cannot be read.
"""

import argparse
import contextlib
import io
import json
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from assert_play_bundle import GateError, expected_identity, read_toml_identity  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[2]


class StateError(Exception):
    """The state file is not what the lane's reader writes."""


def decide(state, track, expected_code):
    """Return (decision, reason). Raises StateError on a malformed state."""
    if not isinstance(state, dict) or not isinstance(state.get("tracks"), list):
        raise StateError("state has no 'tracks' list")
    if state.get("configuredTrack") != track:
        raise StateError(f"state was read for track {state.get('configuredTrack')!r}, not {track!r}")
    tracks = state["tracks"]
    print(f"Play returned {len(tracks)} tracks:")
    for entry in tracks:
        codes = sorted({code for release in entry.get("releases", []) for code in release.get("versionCodes", [])})
        print(f"  {entry.get('id')}: version codes {codes}")
    ids = [entry.get("id") for entry in tracks]
    if not tracks:
        return "FAIL", "Play returned no tracks, so the configured track cannot be confirmed"
    if track not in ids:
        return "FAIL", f"configured track {track!r} is not among the {len(ids)} Play tracks {ids}; " \
                       "correct the track id in fastlane/Fastfile (WEAR_TRACK) from this list"
    codes = state.get("configuredTrackVersionCodes")
    if not isinstance(codes, list):
        raise StateError(f"no version codes were read for the listed track {track!r}")
    print(f"expected versionCode {expected_code}; on {track}: {sorted(codes)}")
    elsewhere = [entry.get("id") for entry in tracks if entry.get("id") != track and any(
        expected_code in release.get("versionCodes", []) for release in entry.get("releases", []))]
    if expected_code in codes:
        return "SKIP", f"versionCode {expected_code} is already on {track}"
    if elsewhere:
        print(f"WARNING: versionCode {expected_code} is already on {elsewhere}; Play rejects a reused code "
              "(release-flow.md §8.9)")
    return "UPLOAD", f"versionCode {expected_code} is not on {track}"


def run(state_path, track, toml_path):
    try:
        state = json.loads(Path(state_path).read_text(encoding="utf-8"))
        toml_name, toml_code = read_toml_identity(toml_path)
        expected_code, _ = expected_identity("wear", toml_name, toml_code)
        decision, reason = decide(state, track, expected_code)
    except (OSError, json.JSONDecodeError, StateError, GateError) as error:
        print(f"DECISION ERROR (exit 2): {error}")
        return 2
    if decision == "FAIL":
        print(f"DECISION FAIL: {reason}")
        return 1
    print(f"reason: {reason}")
    print(f"DECISION {decision}")
    return 0


def release(*codes, status="completed"):
    return {"status": status, "versionCodes": list(codes)}


SELF_TEST_CASES = [
    # (name, tracks, configured-track codes or None, expected exit, text the output must contain)
    ("track absent, FAIL with the list (M-B1)",
     [{"id": "production", "releases": [release(52)]}, {"id": "wear:qa", "releases": []}], None, 1,
     "DECISION FAIL: configured track 'wear:internal' is not among the 2 Play tracks ['production', 'wear:qa']"),
    ("code present, SKIP (M-B2)",
     [{"id": "production", "releases": [release(52)]}, {"id": "wear:internal", "releases": [release(1000052)]}],
     [1000052], 0, "DECISION SKIP"),
    ("code absent, UPLOAD (M-B3)",
     [{"id": "production", "releases": [release(52)]}, {"id": "wear:internal", "releases": [release(1000051)]}],
     [1000051], 0, "DECISION UPLOAD"),
    ("empty track list, FAIL (M-B4)", [], None, 1, "DECISION FAIL: Play returned no tracks"),
    ("empty configured track, UPLOAD", [{"id": "wear:internal", "releases": []}], [], 0, "DECISION UPLOAD"),
    ("code only on another track, UPLOAD with a warning",
     [{"id": "wear:internal", "releases": []}, {"id": "wear:production", "releases": [release(1000052)]}],
     [], 0, "WARNING: versionCode 1000052 is already on ['wear:production']"),
    ("listed track without read codes, error", [{"id": "wear:internal", "releases": []}], None, 2,
     "DECISION ERROR (exit 2): no version codes were read"),
]


def self_test():
    toml = REPO_ROOT / ".github" / "scripts" / "fixtures" / "assert_play_bundle" / "versions.toml"
    print(f"self-test: {len(SELF_TEST_CASES) + 2} cases, expected versionCode from {toml.relative_to(REPO_ROOT)}")
    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        states = [(name, {"tracks": tracks, "configuredTrack": "wear:internal",
                          "configuredTrackVersionCodes": codes}, code, evidence)
                  for name, tracks, codes, code, evidence in SELF_TEST_CASES]
        states.append(("state read for another track, error",
                       {"tracks": [], "configuredTrack": "wear:qa", "configuredTrackVersionCodes": None}, 2,
                       "read for track 'wear:qa', not 'wear:internal'"))
        states.append(("state without a track list, error", {"configuredTrack": "wear:internal"}, 2,
                        "state has no 'tracks' list"))
        for name, state, expected, evidence in states:
            path = Path(tmp) / "state.json"
            path.write_text(json.dumps(state), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                actual = run(path, "wear:internal", toml)
            matched = actual == expected and evidence in output.getvalue()
            print(f"  {'ok' if matched else 'MISMATCH':8} {name}: exit {actual}, expected {expected}")
            if not matched:
                print(output.getvalue(), end="")
                failures.append(name)
    outcomes = {evidence.split(":")[0] for _, _, _, _, evidence in SELF_TEST_CASES if evidence.startswith("DECISION")}
    if not {"DECISION FAIL", "DECISION SKIP", "DECISION UPLOAD"} <= outcomes:
        print(f"self-test FAIL: the cases do not show every decision: {outcomes}")
        return 1
    if failures:
        print(f"self-test FAIL: {len(failures)} mismatched: {failures}")
        return 1
    print(f"self-test PASS: {len(states)} cases, FAIL, SKIP and UPLOAD each shown")
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--state")
    parser.add_argument("--track")
    parser.add_argument("--toml", default=str(REPO_ROOT / "gradle" / "libs.versions.toml"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.state or not args.track:
        parser.error("--state and --track are required unless --self-test")
    return run(args.state, args.track, args.toml)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

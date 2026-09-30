#!/usr/bin/env python3
"""Wear upload decision: FAIL, SKIP or UPLOAD from the Play tracks the deploy_wear lane read.

Spec: documentation/feature-specs/wear-release-pipeline.md §7.2 step 2. The lane reads Play in a
read-only edit (fastlane/play_state.rb) and writes the JSON this script decides on:

    python3 .github/scripts/wear_track_decision.py --state <json> --track wear:internal \
        --toml gradle/libs.versions.toml
    python3 .github/scripts/wear_track_decision.py --self-test

- the track id (WEAR_TRACK, or a dispatch's wear_track) is not a non-public Wear track: FAIL before
  anything Play returned is looked at. It must start with `wear:` and must not be `wear:production`
  or `wear:beta`: a phone track would ship the watch bundle to phones' tracks, and a public Wear
  track would release it beyond internal or closed testing (spec §7.2, §12).
- no tracks at all: FAIL. An empty list proves nothing about the configured track.
- the configured track not listed: the reader probed it with edits.tracks.get. A returned track
  ("found") is decided like a listed one with the codes it carries; an empty track (404 trackEmpty)
  is UPLOAD, since supply creates its release; a nonexistent one (404 Track not found) is FAIL,
  printing every track id, because supply reads a missing track as an empty one (spec §3 F12) and a
  wrong id must never reach the upload.
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


WEAR_TRACK_PREFIX = "wear:"
PUBLIC_WEAR_TRACKS = ("wear:production", "wear:beta")


class StateError(Exception):
    """The state file is not what the lane's reader writes."""


def track_rule(track):
    """None when the id names a non-public Wear track, else the rule it breaks."""
    if not track.startswith(WEAR_TRACK_PREFIX):
        return f"track id {track!r} is not a Wear track: it must start with {WEAR_TRACK_PREFIX!r}"
    if track in PUBLIC_WEAR_TRACKS:
        return f"track id {track!r} is a public Wear track; only internal or closed Wear tracks are allowed"
    return None


def decide(state, track, expected_code):
    """Return (decision, reason). Raises StateError on a malformed state."""
    broken = track_rule(track)
    if broken:
        return "FAIL", broken
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
    probe = state.get("configuredTrackProbe")
    if track in ids:
        if probe != "listed":
            raise StateError(f"track {track!r} is listed, but the reader recorded probe {probe!r}")
        codes = state.get("configuredTrackVersionCodes")
        if not isinstance(codes, list):
            raise StateError(f"no version codes were read for the listed track {track!r}")
    elif probe == "absent":
        return "FAIL", f"configured track {track!r} does not exist: it is not among the {len(ids)} Play " \
                       f"tracks {ids} and edits.tracks.get answers 'Track not found'; correct WEAR_TRACK in " \
                       "fastlane/Fastfile from this list, or dispatch with wear_track (release-flow.md §8.7)"
    elif probe == "empty":
        print(f"configured track {track!r} is not listed; edits.tracks.get answers trackEmpty: "
              "an empty track, which the upload fills")
        codes = []
    elif probe == "found":
        print(f"configured track {track!r} is not listed; edits.tracks.get returns it: decided like a "
              "listed track")
        codes = state.get("configuredTrackVersionCodes")
        if not isinstance(codes, list):
            raise StateError(f"no version codes were read for the found track {track!r}")
    else:
        raise StateError(f"configured track {track!r} is not listed and its probe is {probe!r}, "
                         "not 'found', 'empty' or 'absent'")
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


PRODUCTION = {"id": "production", "releases": [release(52)]}
INTERNAL = "wear:internal"

SELF_TEST_CASES = [
    # (name, configured track, tracks, probe, configured-track codes, expected exit, output must contain)
    ("not listed, Track not found: FAIL with the list (M-B1)", INTERNAL,
     [PRODUCTION, {"id": "wear:qa", "releases": []}], "absent", None, 1,
     "DECISION FAIL: configured track 'wear:internal' does not exist: it is not among the 2 Play tracks "
     "['production', 'wear:qa']"),
    ("code present, SKIP (M-B2)", INTERNAL,
     [PRODUCTION, {"id": INTERNAL, "releases": [release(1000052)]}], "listed", [1000052], 0, "DECISION SKIP"),
    ("code absent, UPLOAD (M-B3); id 'wear:internal' reaches the Play decision", INTERNAL,
     [PRODUCTION, {"id": INTERNAL, "releases": [release(1000051)]}], "listed", [1000051], 0,
     ("expected versionCode 1000052; on wear:internal", "DECISION UPLOAD")),
    ("empty track list, FAIL (M-B4)", INTERNAL, [], "empty", [], 1, "DECISION FAIL: Play returned no tracks"),
    ("not listed, trackEmpty: UPLOAD", INTERNAL, [PRODUCTION], "empty", [], 0,
     ("edits.tracks.get answers trackEmpty", "DECISION UPLOAD")),
    ("not listed, found with the code: SKIP", INTERNAL, [PRODUCTION], "found", [1000052], 0,
     ("edits.tracks.get returns it", "DECISION SKIP")),
    ("not listed, found without the code: UPLOAD", INTERNAL, [PRODUCTION], "found", [1000051], 0,
     ("edits.tracks.get returns it", "DECISION UPLOAD")),
    ("not listed, found without read codes: exit 2", INTERNAL, [PRODUCTION], "found", None, 2,
     "no version codes were read for the found track"),
    ("listed with no releases, UPLOAD", INTERNAL, [{"id": INTERNAL, "releases": []}], "listed", [], 0,
     "DECISION UPLOAD"),
    ("code only on another track, UPLOAD with a warning", INTERNAL,
     [{"id": INTERNAL, "releases": []}, {"id": "wear:production", "releases": [release(1000052)]}], "listed", [], 0,
     "WARNING: versionCode 1000052 is already on ['wear:production']"),
    ("not listed, probe missing or unknown (another error): exit 2", INTERNAL, [PRODUCTION], "error", None, 2,
     "is not listed and its probe is 'error'"),
    ("listed, probe says otherwise: exit 2", INTERNAL, [{"id": INTERNAL, "releases": []}], "absent", None, 2,
     "is listed, but the reader recorded probe 'absent'"),
    ("listed track without read codes: exit 2", INTERNAL, [{"id": INTERNAL, "releases": []}], "listed", None, 2,
     "no version codes were read"),
    ("id 'production': FAIL, not a Wear track", "production", [PRODUCTION], "listed", [52], 1,
     "DECISION FAIL: track id 'production' is not a Wear track"),
    ("id 'internal': FAIL, not a Wear track", "internal", [PRODUCTION, {"id": "internal", "releases": []}],
     "listed", [], 1, "DECISION FAIL: track id 'internal' is not a Wear track"),
    ("id 'wear:production': FAIL, public", "wear:production", [PRODUCTION, {"id": "wear:production", "releases": []}],
     "listed", [], 1, "DECISION FAIL: track id 'wear:production' is a public Wear track"),
    ("id 'wear:beta': FAIL, public", "wear:beta", [PRODUCTION, {"id": "wear:beta", "releases": []}],
     "listed", [], 1, "DECISION FAIL: track id 'wear:beta' is a public Wear track"),
    ("id 'wear:owner-test' (closed): reaches the Play decision, UPLOAD", "wear:owner-test",
     [PRODUCTION, {"id": "wear:owner-test", "releases": []}], "listed", [], 0,
     ("expected versionCode 1000052; on wear:owner-test", "DECISION UPLOAD")),
]


def self_test():
    toml = REPO_ROOT / ".github" / "scripts" / "fixtures" / "assert_play_bundle" / "versions.toml"
    states = [(name, track, {"tracks": tracks, "configuredTrack": track, "configuredTrackProbe": probe,
                             "configuredTrackVersionCodes": codes}, code, evidence)
              for name, track, tracks, probe, codes, code, evidence in SELF_TEST_CASES]
    states.append(("state read for another track: exit 2", INTERNAL,
                   {"tracks": [], "configuredTrack": "wear:qa", "configuredTrackVersionCodes": None}, 2,
                   "read for track 'wear:qa', not 'wear:internal'"))
    states.append(("state without a track list: exit 2", INTERNAL, {"configuredTrack": INTERNAL}, 2,
                   "state has no 'tracks' list"))
    print(f"self-test: {len(states)} cases, expected versionCode from {toml.relative_to(REPO_ROOT)}")
    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        for name, track, state, expected, evidence in states:
            path = Path(tmp) / "state.json"
            path.write_text(json.dumps(state), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                actual = run(path, track, toml)
            wanted = evidence if isinstance(evidence, tuple) else (evidence,)
            matched = actual == expected and all(text in output.getvalue() for text in wanted)
            print(f"  {'ok' if matched else 'MISMATCH':8} {name}: exit {actual}, expected {expected}")
            if not matched:
                print(output.getvalue(), end="")
                failures.append(name)
    decisions = {"FAIL" if code == 1 else "ERROR" if code == 2 else "SKIP" if "SKIP" in str(evidence) else "UPLOAD"
                 for _, _, _, _, _, code, evidence in SELF_TEST_CASES}
    if not {"FAIL", "SKIP", "UPLOAD", "ERROR"} <= decisions:
        print(f"self-test FAIL: the cases do not show every outcome: {sorted(decisions)}")
        return 1
    if failures:
        print(f"self-test FAIL: {len(failures)} of {len(states)} mismatched: {failures}")
        return 1
    print(f"self-test PASS: {len(states)} cases, FAIL, SKIP, UPLOAD and exit 2 each shown")
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

#!/usr/bin/env python3
"""Release PR check: PASS, RETRY or FAIL for whether the release PR can merge into master.

Spec: documentation/feature-specs/release-signing.md §4.3. android_deploy_prod.yml reads the PR
with `gh pr view <n> --json number,mergeable,mergeStateStatus,headRefOid` twice, in the guard job
and right before the Play upload, and decides on that JSON:

    python3 .github/scripts/release_pr_guard.py --pr <json> --head <sha>
    python3 .github/scripts/release_pr_guard.py --self-test

- PASS needs all of: `mergeable` is MERGEABLE; `mergeStateStatus` is CLEAN, HAS_HOOKS or UNSTABLE;
  `headRefOid` is the pinned SHA. A PR that master's ruleset refuses (an unverified commit, a
  required check not passed yet) is BLOCKED, so the release stops before anything ships.
- RETRY: either field is UNKNOWN (GitHub has not computed it yet) and the head matches.
- FAIL otherwise, printing every value.

Prints `DECISION PASS` (exit 0), `DECISION RETRY` (exit 3) or `DECISION FAIL: <reason>` (exit 1).
Exit 2: the JSON cannot be read.
"""

import argparse
import contextlib
import io
import json
import sys
import tempfile
from pathlib import Path

MERGEABLE_STATES = ("MERGEABLE", "CONFLICTING", "UNKNOWN")
MERGE_STATE_STATUSES = ("BEHIND", "BLOCKED", "CLEAN", "DIRTY", "DRAFT", "HAS_HOOKS", "UNKNOWN", "UNSTABLE")
PASSING_STATUSES = ("CLEAN", "HAS_HOOKS", "UNSTABLE")


class PrError(Exception):
    """The JSON is not what `gh pr view --json number,mergeable,mergeStateStatus,headRefOid` writes."""


def decide(pr, head):
    """Return (decision, reason). Raises PrError on a malformed PR."""
    if not isinstance(pr, dict):
        raise PrError("the JSON is not an object")
    missing = [key for key in ("number", "mergeable", "mergeStateStatus", "headRefOid") if key not in pr]
    if missing:
        raise PrError(f"the JSON has no {missing}")
    mergeable, status, actual = pr["mergeable"], pr["mergeStateStatus"], pr["headRefOid"]
    values = f"PR #{pr['number']}: mergeable={mergeable} mergeStateStatus={status} headRefOid={actual} " \
             f"pinned={head}"
    print(values)
    if mergeable == "MERGEABLE" and status in PASSING_STATUSES and actual == head:
        return "PASS", "the release PR can merge at the pinned SHA"
    if actual == head and "UNKNOWN" in (mergeable, status):
        return "RETRY", "GitHub has not computed whether the PR can merge yet"
    return "FAIL", f"the release PR cannot merge at the pinned SHA ({values}); see release-flow.md §8.11"


def run(pr_path, head):
    try:
        pr = json.loads(Path(pr_path).read_text(encoding="utf-8"))
        decision, reason = decide(pr, head)
    except (OSError, json.JSONDecodeError, PrError) as error:
        print(f"DECISION ERROR (exit 2): {error}")
        return 2
    if decision == "FAIL":
        print(f"DECISION FAIL: {reason}")
        return 1
    print(f"reason: {reason}")
    print(f"DECISION {decision}")
    return 0 if decision == "PASS" else 3


PINNED = "4" * 40
MOVED = "5" * 40


EVIDENCE = {0: "DECISION PASS", 1: "DECISION FAIL: the release PR cannot merge", 3: "DECISION RETRY"}

# (mergeable, mergeStateStatus, headRefOid, expected exit), written out rather than derived from the
# constants above, so that a mutation of the decision cannot move its own expectations.
SELF_TEST_CASES = [
    ("MERGEABLE", "BEHIND", PINNED, 1),
    ("MERGEABLE", "BLOCKED", PINNED, 1),  # M-R5
    ("MERGEABLE", "CLEAN", PINNED, 0),
    ("MERGEABLE", "DIRTY", PINNED, 1),
    ("MERGEABLE", "DRAFT", PINNED, 1),
    ("MERGEABLE", "HAS_HOOKS", PINNED, 0),
    ("MERGEABLE", "UNKNOWN", PINNED, 3),  # M-R6
    ("MERGEABLE", "UNSTABLE", PINNED, 0),
    ("CONFLICTING", "BEHIND", PINNED, 1),
    ("CONFLICTING", "BLOCKED", PINNED, 1),
    ("CONFLICTING", "CLEAN", PINNED, 1),
    ("CONFLICTING", "DIRTY", PINNED, 1),
    ("CONFLICTING", "DRAFT", PINNED, 1),
    ("CONFLICTING", "HAS_HOOKS", PINNED, 1),
    ("CONFLICTING", "UNKNOWN", PINNED, 3),
    ("CONFLICTING", "UNSTABLE", PINNED, 1),
    ("UNKNOWN", "BEHIND", PINNED, 3),
    ("UNKNOWN", "BLOCKED", PINNED, 3),
    ("UNKNOWN", "CLEAN", PINNED, 3),
    ("UNKNOWN", "DIRTY", PINNED, 3),
    ("UNKNOWN", "DRAFT", PINNED, 3),
    ("UNKNOWN", "HAS_HOOKS", PINNED, 3),
    ("UNKNOWN", "UNKNOWN", PINNED, 3),
    ("UNKNOWN", "UNSTABLE", PINNED, 3),
    ("MERGEABLE", "CLEAN", MOVED, 1),  # M-R7: a passing pair whose head moved
]


def self_test():
    print(f"self-test: {len(SELF_TEST_CASES)} fixtures, pinned head {PINNED[:7]}")
    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        for number, (mergeable, status, actual, expected) in enumerate(SELF_TEST_CASES, start=1):
            name = f"{mergeable} / {status}" + (", head moved" if actual != PINNED else "")
            pr_path = Path(tmp) / "pr.json"
            pr_path.write_text(json.dumps({"number": number, "mergeable": mergeable, "mergeStateStatus": status,
                                           "headRefOid": actual}), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = run(pr_path, PINNED)
            matched = code == expected and EVIDENCE[expected] in output.getvalue()
            print(f"  {'ok' if matched else 'MISMATCH':8} {name}: exit {code}, expected {expected}")
            if not matched:
                print(output.getvalue(), end="")
                failures.append(name)
    outcomes = {expected for *_, expected in SELF_TEST_CASES}
    if outcomes != {0, 1, 3}:
        print(f"self-test FAIL: the fixtures do not show PASS, RETRY and FAIL: {sorted(outcomes)}")
        return 1
    if failures:
        print(f"self-test FAIL: {len(failures)} of {len(SELF_TEST_CASES)} mismatched: {failures}")
        return 1
    print(f"self-test PASS: {len(SELF_TEST_CASES)} fixtures, PASS, RETRY and FAIL each shown")
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--pr")
    parser.add_argument("--head")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.pr or not args.head:
        parser.error("--pr and --head are required unless --self-test")
    return run(args.pr, args.head)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

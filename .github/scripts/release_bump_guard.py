#!/usr/bin/env python3
"""Release bump proof: PASS, RETRY or FAIL for the version bump commit a cut made.

Spec: documentation/feature-specs/release-signing.md §4.2. cut_release.yml makes the bump commit
through GitHub's REST Git database API, reads it back with `GET /repos/{owner}/{repo}/commits/{sha}`
and decides on that JSON before it opens the release PR (spec §4.1 step 5):

    python3 .github/scripts/release_bump_guard.py --commit <json> --parent <sha> --path <path>
    python3 .github/scripts/release_bump_guard.py --self-test

- PASS needs all of: `commit.verification.verified` is true; `parents` holds exactly one entry,
  whose `sha` is the source head; `files` holds exactly one entry, the TOML, with status `modified`.
- RETRY: `commit.verification.reason` is `gpgverify_unavailable` or `gpgverify_error`. Both mean
  that GitHub could not check the signature yet, so the cut reads the commit again.
- FAIL otherwise, naming every failed check and `commit.verification.reason`.

Prints the commit's verification, parents and files, then `DECISION PASS` (exit 0),
`DECISION RETRY` (exit 3) or `DECISION FAIL: <reason>` (exit 1). Exit 2: the JSON cannot be read.
"""

import argparse
import contextlib
import copy
import io
import json
import sys
import tempfile
from pathlib import Path

RETRY_REASONS = ("gpgverify_unavailable", "gpgverify_error")


class CommitError(Exception):
    """The JSON is not a REST commit."""


def read_commit(commit):
    """Return (verified, reason, parent shas, [(filename, status)]). Raises CommitError."""
    if not isinstance(commit, dict) or not isinstance(commit.get("commit"), dict):
        raise CommitError("the JSON is not a commit object")
    verification = commit["commit"].get("verification")
    if not isinstance(verification, dict) or "verified" not in verification:
        raise CommitError("the JSON has no commit.verification")
    parents = commit.get("parents")
    files = commit.get("files")
    if not isinstance(parents, list) or not isinstance(files, list):
        raise CommitError("the JSON has no 'parents' or 'files' list")
    if not all(isinstance(entry, dict) for entry in parents + files):
        raise CommitError("an entry of 'parents' or 'files' is not an object")
    return (verification.get("verified"), verification.get("reason"),
            [parent.get("sha") for parent in parents],
            [(entry.get("filename"), entry.get("status")) for entry in files])


def decide(commit, parent, path):
    """Return (decision, reason). Raises CommitError on a malformed commit."""
    verified, reason, parents, files = read_commit(commit)
    print(f"commit {commit.get('sha')}: verified={verified} reason={reason}")
    print(f"parents: {parents}; expected exactly [{parent!r}]")
    print(f"files: {files}; expected exactly [({path!r}, 'modified')]")
    failed = []
    if verified is not True:
        failed.append(f"commit.verification.verified is {json.dumps(verified)}, not true")
    if parents != [parent]:
        failed.append(f"parents are {parents}, not exactly [{parent!r}]")
    if files != [(path, "modified")]:
        failed.append(f"files are {files}, not exactly [({path!r}, 'modified')]")
    if not failed:
        return "PASS", f"verified, one parent {parent}, one modified file {path}"
    if reason in RETRY_REASONS:
        return "RETRY", f"GitHub could not check the signature yet (verification.reason={reason})"
    return "FAIL", f"{'; '.join(failed)} (verification.reason={reason})"


def run(commit_path, parent, path):
    try:
        commit = json.loads(Path(commit_path).read_text(encoding="utf-8"))
        decision, reason = decide(commit, parent, path)
    except (OSError, json.JSONDecodeError, CommitError) as error:
        print(f"DECISION ERROR (exit 2): {error}")
        return 2
    if decision == "FAIL":
        print(f"DECISION FAIL: {reason}")
        return 1
    print(f"reason: {reason}")
    print(f"DECISION {decision}")
    return 0 if decision == "PASS" else 3


PARENT = "1" * 40
OTHER = "2" * 40
TOML = "gradle/libs.versions.toml"
BASE = {
    "sha": "3" * 40,
    "commit": {"message": "chore: bump to v.1.54.0 (code 58)",
               "verification": {"verified": True, "reason": "valid"}},
    "parents": [{"sha": PARENT}],
    "files": [{"filename": TOML, "status": "modified"}],
}


def variant(verified=True, reason="valid", parents=(PARENT,), files=((TOML, "modified"),)):
    commit = copy.deepcopy(BASE)
    commit["commit"]["verification"] = {"verified": verified, "reason": reason}
    commit["parents"] = [{"sha": sha} for sha in parents]
    commit["files"] = [{"filename": name, "status": state} for name, state in files]
    return commit


SELF_TEST_CASES = [
    # (name, commit, expected exit, output must contain)
    ("verified, one parent, one modified TOML: PASS", variant(), 0, "DECISION PASS"),
    ("unverified, reason unsigned: FAIL (M-R1)", variant(verified=False, reason="unsigned"), 1,
     "DECISION FAIL: commit.verification.verified is false, not true (verification.reason=unsigned)"),
    ("gpgverify_unavailable: RETRY (M-R4)", variant(verified=False, reason="gpgverify_unavailable"), 3,
     "DECISION RETRY"),
    ("gpgverify_error: RETRY (M-R4)", variant(verified=False, reason="gpgverify_error"), 3, "DECISION RETRY"),
    ("two parents: FAIL", variant(parents=(PARENT, OTHER)), 1, "DECISION FAIL: parents are"),
    ("another parent: FAIL (M-R2)", variant(parents=(OTHER,)), 1, "DECISION FAIL: parents are"),
    ("two files: FAIL (M-R3)", variant(files=((TOML, "modified"), ("README.md", "modified"))), 1,
     "DECISION FAIL: files are"),
    ("another file: FAIL", variant(files=(("README.md", "modified"),)), 1, "DECISION FAIL: files are"),
    ("status added: FAIL", variant(files=((TOML, "added"),)), 1, "DECISION FAIL: files are"),
]


def self_test():
    print(f"self-test: {len(SELF_TEST_CASES)} fixtures, parent {PARENT[:7]}, path {TOML}")
    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        for name, commit, expected, evidence in SELF_TEST_CASES:
            commit_path = Path(tmp) / "commit.json"
            commit_path.write_text(json.dumps(commit), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                actual = run(commit_path, PARENT, TOML)
            matched = actual == expected and evidence in output.getvalue()
            print(f"  {'ok' if matched else 'MISMATCH':8} {name}: exit {actual}, expected {expected}")
            if not matched:
                print(output.getvalue(), end="")
                failures.append(name)
    if failures:
        print(f"self-test FAIL: {len(failures)} of {len(SELF_TEST_CASES)} mismatched: {failures}")
        return 1
    print(f"self-test PASS: {len(SELF_TEST_CASES)} fixtures, PASS, RETRY and FAIL each shown")
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--commit")
    parser.add_argument("--parent")
    parser.add_argument("--path")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.commit or not args.parent or not args.path:
        parser.error("--commit, --parent and --path are required unless --self-test")
    return run(args.commit, args.parent, args.path)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

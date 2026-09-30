#!/usr/bin/env python3
"""Execute named controls with the canonical restoring harness and fresh JUnit identity."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]


ASSERTION_TYPES = frozenset((
    "AssertionError", "java.lang.AssertionError", "junit.framework.AssertionFailedError",
    "org.opentest4j.AssertionFailedError", "junit.framework.ComparisonFailure",
    "org.junit.ComparisonFailure", "org.junit.internal.ArrayComparisonFailure",
))


def is_assertion_test_failure(test: ET.Element) -> bool:
    failures = test.findall("failure")
    if not failures or test.find("error") is not None or test.find("skipped") is not None:
        return False
    return all(is_assertion_failure(failure) for failure in failures)


def is_assertion_failure(failure: ET.Element) -> bool:
    declared_type = failure.get("type", "").strip()
    if declared_type:
        return declared_type in ASSERTION_TYPES
    # AGP connected-test XML omits type; only the leading exception identifies the failure.
    first_line = (failure.text or "").strip().splitlines()
    reported_type = first_line[0].partition(":")[0].strip() if first_line else ""
    return reported_type in ASSERTION_TYPES


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--only", action="append", default=[])
    args = parser.parse_args()
    cases = json.loads(args.cases.read_text())
    if args.only:
        cases = [case for case in cases if case["name"] in args.only]
        if {case["name"] for case in cases} != set(args.only):
            parser.error("Unknown control name")
    args.output.mkdir(parents=True, exist_ok=False)
    results = []
    for case in cases:
        directory = args.output / case["name"]
        directory.mkdir()
        source = ROOT / case["file"]
        before = source.read_bytes()
        began = time.time()
        command = ["python3", "documentation/mockups/mutation_harness.py", "--name", case["name"], "--file", case["file"],
                   "--find", case["find"], "--replace", case["replace"], "--task", case["task"], "--expect", "RED"]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
        output = result.stdout + result.stderr
        (directory / "harness.log").write_text(output)
        (directory / "command.json").write_text(json.dumps(command, indent=2) + "\n")
        restored = source.read_bytes() == before
        matches = []
        for path in (ROOT / case.get("xml_root", "app/wear/build")).rglob("TEST-*.xml"):
            if path.stat().st_mtime < began:
                continue
            for test in ET.parse(path).getroot().iter("testcase"):
                if test.get("classname") == case["expected_class"] and test.get("name") == case["expected_method"]:
                    target = directory / ("xml-" + hashlib.sha256(str(path).encode()).hexdigest()[:8] + ".xml")
                    target.write_bytes(path.read_bytes())
                    failures = "\n".join(failure.get("message", "") + (failure.text or "")
                                         for failure in test.findall("failure"))
                    matches.append(is_assertion_test_failure(test)
                                   and case.get("expected_assertion", "") in failures)
        summaries = re.findall(r"(\d+) actionable tasks?: (\d+) executed([^\n]*)", output)
        fresh = len(summaries) == 1 and summaries[0][0] == summaries[0][1] and not summaries[0][2].strip()
        valid = result.returncode == 0 and restored and fresh and matches == [True]
        record = {"name": case["name"], "verdict": "RED" if valid else "INVALID",
                  "restored": restored, "fresh_executed_summary": fresh, "expected_assertions": len(matches),
                  "source_sha256": hashlib.sha256(before).hexdigest()}
        results.append(record)
        print(json.dumps(record), flush=True)
        (args.output / "results.json").write_text(json.dumps(results, indent=2) + "\n")
        if not restored:
            raise SystemExit("Source restoration failed; stopped without attempting a hand revert")
    raise SystemExit(0 if results and all(row["verdict"] == "RED" for row in results) else 1)


if __name__ == "__main__":
    main()

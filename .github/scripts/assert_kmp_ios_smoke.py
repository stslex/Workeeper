#!/usr/bin/env python3
"""Native XML identity oracle for the required `KMP iOS kit smoke` context.

An exit code is not evidence and neither is a total: two kit tests and zero
navigation tests would satisfy any repo-wide count, and a classname from one
case plus a method name from another can forge an identity out of substrings.
This script therefore requires, per module and structurally parsed, exactly one
executed `<testcase>` whose (classname, name) tuple matches the expected
identity — no skips, no failures, no errors, no duplicates, no missing XML.

Run from the repository root, after the forced two-task Gradle invocation:

    python3 .github/scripts/assert_kmp_ios_smoke.py

The same file is the known-negative vehicle: pointed at XML produced by a
renamed navigation test method it must fail on the navigation tuple mismatch
while the kit tuple still verifies (kmp-phase-7-2-navigation.md §11.4.3).
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# The Kotlin/Native Gradle test task prefixes every suite/testcase classname
# with its own name ("iosSimulatorArm64Test.<fqcn>"). Strip exactly that one
# known prefix before comparing, then require full equality — an endswith()
# would accept a foreign package that merely ends in the expected name.
KNOWN_SUITE_PREFIX = "iosSimulatorArm64Test."

EXPECTED = [
    {
        "module": "core:ui:kit",
        "results_dir": Path("core/ui/kit/build/test-results/iosSimulatorArm64Test"),
        "classname": "io.github.stslex.workeeper.core.ui.kit.IosKitSceneSmokeTest",
        "name": "sheetLayoutRendersMigratedStringFontAndIcon[iosSimulatorArm64]",
    },
    {
        "module": "core:ui:navigation",
        "results_dir": Path(
            "core/ui/navigation/build/test-results/iosSimulatorArm64Test"
        ),
        "classname": "io.github.stslex.workeeper.core.ui.navigation.ScreenSerializationIosTest",
        "name": "allCurrentRoutesRoundTripThroughProductionRegistry[iosSimulatorArm64]",
    },
]


def normalize_classname(raw: str) -> str:
    if raw.startswith(KNOWN_SUITE_PREFIX):
        return raw[len(KNOWN_SUITE_PREFIX):]
    return raw


def check_module(expected: dict) -> str:
    """Verify one module's result directory; return the verified identity line."""
    module = expected["module"]
    results_dir = expected["results_dir"]

    if not results_dir.is_dir():
        sys.exit(f"{module}: result directory {results_dir} does not exist")
    xml_files = sorted(results_dir.glob("TEST-*.xml"))
    if not xml_files:
        sys.exit(f"{module}: no TEST-*.xml under {results_dir}")

    testcases = []
    totals = {"tests": 0, "skipped": 0, "failures": 0, "errors": 0}
    for xml_file in xml_files:
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as error:
            sys.exit(f"{module}: {xml_file} is not parseable XML: {error}")
        suites = [root] if root.tag == "testsuite" else root.iter("testsuite")
        parsed_any = False
        for suite in suites:
            parsed_any = True
            for key in totals:
                totals[key] += int(suite.get(key, 0))
        if not parsed_any:
            sys.exit(f"{module}: {xml_file} contains no <testsuite> element")
        testcases.extend(root.iter("testcase"))

    if (totals["tests"], totals["skipped"], totals["failures"], totals["errors"]) != (
        1,
        0,
        0,
        0,
    ):
        sys.exit(
            f"{module}: expected exactly 1 executed / 0 skipped / 0 failed / 0 errored, "
            f"got {totals}"
        )
    if len(testcases) != 1:
        names = [
            (case.get("classname", "?"), case.get("name", "?")) for case in testcases
        ]
        sys.exit(f"{module}: expected exactly one <testcase>, found {len(testcases)}: {names}")

    case = testcases[0]
    classname = normalize_classname(case.get("classname", ""))
    name = case.get("name", "")
    if classname != expected["classname"] or name != expected["name"]:
        sys.exit(
            f"{module}: testcase identity mismatch — "
            f"expected classname={expected['classname']!r} name={expected['name']!r}, "
            f"got classname={classname!r} name={name!r}"
        )
    for child in case:
        if child.tag in ("failure", "error", "skipped"):
            sys.exit(f"{module}: the testcase carries a <{child.tag}> element")

    return f"{module}: 1 executed / 0 skipped / 0 failed — {classname}.{name}"


def main() -> None:
    verified = [check_module(expected) for expected in EXPECTED]
    print("native gate live:")
    for line in verified:
        print(f"  {line}")


if __name__ == "__main__":
    main()

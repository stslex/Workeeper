#!/usr/bin/env python3
"""Native XML identity oracle for the required `KMP iOS kit smoke` context.

An exit code is not evidence and neither is a total: two kit tests and zero
navigation tests would satisfy any repo-wide count, and a classname from one
case plus a method name from another can forge an identity out of substrings.

Per configured module this script therefore requires, structurally parsed:

* the result directory exists and holds at least one parseable `TEST-*.xml`;
* at least one `<testsuite>` and at least one `<testcase>`;
* the declared aggregate `tests` equals the number of parsed `<testcase>`
  elements — a suite that claims more cases than it emitted is inconsistent
  XML, not evidence;
* aggregate `skipped` / `failures` / `errors` are zero, and no testcase
  carries a `<failure>`, `<error>` or `<skipped>` child;
* the expected normalized (classname, name) tuple occurs exactly once.

Additional *successful* testcases are allowed, so a module may grow a second
native test without touching this file; none of the guarantees above weaken,
because every extra case still has to pass and still has to be counted.

Every configured module is checked even when an earlier one fails, so a
kit-side problem cannot hide the navigation verdict — the same reason the
Gradle invocation uses `--continue`.

Run from the repository root, after the forced Native Gradle invocation:

    python3 .github/scripts/assert_kmp_ios_smoke.py

The workflow runs it even when that Gradle step went red (as long as the step
actually started), so a native failure is diagnosed from the XML it produced
rather than reported only as an exit code.
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# The Kotlin/Native Gradle test task prefixes every suite/testcase classname
# with its own name ("iosSimulatorArm64Test.<fqcn>"). Strip exactly that one
# known prefix before comparing, then require full equality — an endswith()
# would accept a foreign package, and stripping repeatedly would accept a
# doubled prefix.
KNOWN_SUITE_PREFIX = "iosSimulatorArm64Test."

COUNT_ATTRIBUTES = ("tests", "skipped", "failures", "errors")

NON_SUCCESS_TAGS = ("failure", "error", "skipped")


class ModuleError(Exception):
    """One module failed its contract. Raised, not exited, so every module is reported."""


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
    """Remove exactly one leading task prefix. A doubled prefix stays a mismatch."""
    if raw.startswith(KNOWN_SUITE_PREFIX):
        return raw[len(KNOWN_SUITE_PREFIX):]
    return raw


def count_attribute(suite: ET.Element, key: str, module: str, xml_file: Path) -> int:
    raw = suite.get(key, "0")
    try:
        return int(raw)
    except ValueError:
        raise ModuleError(f"{module}: {xml_file} has non-numeric {key}={raw!r} on <testsuite>")


def identify(case: ET.Element) -> str:
    return f"{normalize_classname(case.get('classname', ''))}.{case.get('name', '')}"


def check_module(expected: dict) -> str:
    """Verify one module's result directory; return the verified summary line."""
    module = expected["module"]
    results_dir = expected["results_dir"]

    if not results_dir.is_dir():
        raise ModuleError(f"{module}: result directory {results_dir} does not exist")
    xml_files = sorted(results_dir.glob("TEST-*.xml"))
    if not xml_files:
        raise ModuleError(f"{module}: no TEST-*.xml under {results_dir}")

    testcases = []
    totals = dict.fromkeys(COUNT_ATTRIBUTES, 0)
    suites_seen = 0
    for xml_file in xml_files:
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as error:
            raise ModuleError(f"{module}: {xml_file} is not parseable XML: {error}")
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        if not suites:
            raise ModuleError(f"{module}: {xml_file} contains no <testsuite> element")
        for suite in suites:
            suites_seen += 1
            for key in COUNT_ATTRIBUTES:
                totals[key] += count_attribute(suite, key, module, xml_file)
        testcases.extend(root.iter("testcase"))

    if suites_seen < 1:
        raise ModuleError(f"{module}: no <testsuite> parsed under {results_dir}")

    executed = len(testcases)
    if executed < 1:
        raise ModuleError(f"{module}: no <testcase> parsed under {results_dir}")
    if totals["tests"] != executed:
        raise ModuleError(
            f"{module}: inconsistent XML — <testsuite> declares tests={totals['tests']} "
            f"but {executed} <testcase> element(s) were parsed"
        )
    for key in ("skipped", "failures", "errors"):
        if totals[key] != 0:
            raise ModuleError(f"{module}: expected 0 {key}, got {totals[key]} across {executed} testcase(s)")
    for case in testcases:
        for child in case:
            if child.tag in NON_SUCCESS_TAGS:
                raise ModuleError(f"{module}: testcase {identify(case)} carries a <{child.tag}> element")

    matches = [
        case
        for case in testcases
        if normalize_classname(case.get("classname", "")) == expected["classname"]
        and case.get("name", "") == expected["name"]
    ]
    if len(matches) != 1:
        found = sorted(identify(case) for case in testcases)
        raise ModuleError(
            f"{module}: expected exactly one testcase with classname="
            f"{expected['classname']!r} name={expected['name']!r}, found {len(matches)}; "
            f"parsed {executed} testcase(s): {found}"
        )

    return (
        f"{module}: {executed} executed / {totals['skipped']} skipped / "
        f"{totals['failures']} failed / {totals['errors']} errored — verified "
        f"{expected['classname']}.{expected['name']}"
    )


def main() -> None:
    verified: list[str] = []
    failures: list[str] = []
    for expected in EXPECTED:
        try:
            verified.append(check_module(expected))
        except ModuleError as error:
            failures.append(str(error))

    if failures:
        for line in verified:
            print(f"  ok: {line}")
        raise SystemExit(
            "native gate FAILED (every module was checked, so no verdict is hidden):\n"
            + "\n".join(f"  {message}" for message in failures)
        )

    print("native gate live:")
    for line in verified:
        print(f"  {line}")


if __name__ == "__main__":
    main()

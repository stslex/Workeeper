#!/usr/bin/env python3
"""Shared structural JUnit-XML identity oracle.

Three gates need the same guarantees — the Native `KMP iOS kit smoke` job, the
MVI Android-host step inside `Build and Unit Tests`, and the MVI device step in
`ui_tests.yml`. Three hand-written parsers would drift, and a weaker one would
be the one that matters, so the rules live here once.

Per configured module this requires, structurally parsed:

* the result directory exists and holds at least one parseable `TEST-*.xml`;
* at least one `<testsuite>` and at least one `<testcase>`;
* **per `<testsuite>`**, every count attribute is a non-negative integer, the
  declared `tests` equals that suite's own `<testcase>` children, and its
  `skipped` / `failures` / `errors` are each zero;
* no testcase carries a `<failure>`, `<error>` or `<skipped>` child;
* each expected normalized (classname, name) tuple occurs exactly once.

The per-suite checks are the point. A module-level sum alone lets malformed
suites cancel each other out: one file declaring `tests=2` with a single
testcase and another declaring `tests=0` with a single testcase sums to
`2 == 2` and would pass, while both files are lying about what ran. Validating
each suite before it contributes to any total closes that, and requiring each
suite's status counters to be zero means a negative counter can never offset a
positive one.

Additional *successful* testcases are allowed, so a module may grow another
test without touching a gate; none of the guarantees above weaken, because
every extra case still has to pass and still has to be counted.

Every configured module is checked even when an earlier one fails, so one
module's problem cannot hide another's verdict — the same reason the Native
Gradle invocation uses `--continue`.

`classname_prefix` differs per runner and is why this is a parameter rather
than a constant: the Kotlin/Native Gradle test task prefixes every classname
with its own task name (`iosSimulatorArm64Test.<fqcn>`), while the JVM and
Android device runners emit the bare fqcn. Exactly one known prefix is
stripped and then full equality is required — an `endswith()` would accept a
foreign package, and stripping repeatedly would accept a doubled prefix.
"""

import xml.etree.ElementTree as ET
from pathlib import Path

COUNT_ATTRIBUTES = ("tests", "skipped", "failures", "errors")

NON_SUCCESS_TAGS = ("failure", "error", "skipped")


class ModuleError(Exception):
    """One module failed its contract. Raised, not exited, so every module is reported."""


def normalize_classname(raw: str, prefix: str) -> str:
    """Remove exactly one leading task prefix. A doubled prefix stays a mismatch."""
    if prefix and raw.startswith(prefix):
        return raw[len(prefix):]
    return raw


def count_attribute(suite: ET.Element, key: str, module: str, xml_file: Path) -> int:
    """Parse one count attribute, rejecting non-integers and negatives.

    A negative counter is not merely odd: summed across suites it would subtract from a real
    failure elsewhere, which is exactly the compensation this parser must not allow.
    """
    raw = suite.get(key, "0")
    try:
        value = int(raw)
    except ValueError:
        raise ModuleError(f"{module}: {xml_file.name} has non-numeric {key}={raw!r} on <testsuite>")
    if value < 0:
        raise ModuleError(f"{module}: {xml_file.name} has negative {key}={value} on <testsuite>")
    return value


def suites_of(root: ET.Element, module: str, xml_file: Path) -> list[ET.Element]:
    """Return the <testsuite> elements to validate, handling a <testsuites> wrapper explicitly.

    Only one level is modelled. A nested <testsuite> would make "which cases belong to which
    declaration" ambiguous, and guessing is how a miscount becomes a green gate — so it is
    refused with a precise diagnostic instead.
    """
    if root.tag == "testsuite":
        suites = [root]
    elif root.tag == "testsuites":
        suites = root.findall("testsuite")
        if not suites:
            raise ModuleError(
                f"{module}: {xml_file.name} <testsuites> wrapper contains no <testsuite> element"
            )
    else:
        raise ModuleError(
            f"{module}: {xml_file.name} root element is <{root.tag}>, "
            "expected <testsuite> or <testsuites>"
        )
    for suite in suites:
        if suite.find("testsuite") is not None:
            raise ModuleError(
                f"{module}: {xml_file.name} nests <testsuite> inside <testsuite>; this parser "
                "does not model that structure and refuses to guess which cases each declares"
            )
    return suites


def identify(case: ET.Element, prefix: str) -> str:
    classname = normalize_classname(case.get("classname", ""), prefix)
    return f"{classname}.{case.get('name', '')}"


def check_module(expected: dict) -> str:
    """Verify one module's result directory; return the verified summary line.

    `expected` keys: `module`, `results_dir`, `identities` (list of {classname, name}) and the
    optional `classname_prefix` (default none).
    """
    module = expected["module"]
    results_dir = Path(expected["results_dir"])
    prefix = expected.get("classname_prefix", "")
    identities = expected["identities"]
    if not identities:
        raise ModuleError(f"{module}: no expected identities configured; that is not a gate")

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
            raise ModuleError(f"{module}: {xml_file.name} is not parseable XML: {error}")
        for suite in suites_of(root, module, xml_file):
            suites_seen += 1
            # Only this suite's OWN testcase children, so a wrapper cannot double-count.
            suite_cases = suite.findall("testcase")
            declared = {
                key: count_attribute(suite, key, module, xml_file) for key in COUNT_ATTRIBUTES
            }
            label = f"<testsuite name={suite.get('name', '?')!r}> in {xml_file.name}"
            # Validated BEFORE contributing to any total: this is what stops two malformed
            # suites cancelling out into a module aggregate that looks consistent.
            if declared["tests"] != len(suite_cases):
                raise ModuleError(
                    f"{module}: inconsistent XML — {label} declares tests={declared['tests']} "
                    f"but contains {len(suite_cases)} <testcase> element(s)"
                )
            for key in ("skipped", "failures", "errors"):
                if declared[key] != 0:
                    raise ModuleError(f"{module}: {label} reports {key}={declared[key]}, expected 0")
            for key in COUNT_ATTRIBUTES:
                totals[key] += declared[key]
            testcases.extend(suite_cases)

    if suites_seen < 1:
        raise ModuleError(f"{module}: no <testsuite> parsed under {results_dir}")

    executed = len(testcases)
    if executed < 1:
        raise ModuleError(f"{module}: no <testcase> parsed under {results_dir}")
    # Defence in depth: every suite was already validated individually above.
    if totals["tests"] != executed:
        raise ModuleError(
            f"{module}: inconsistent XML — suites declare tests={totals['tests']} "
            f"but {executed} <testcase> element(s) were parsed"
        )
    for case in testcases:
        for child in case:
            if child.tag in NON_SUCCESS_TAGS:
                raise ModuleError(
                    f"{module}: testcase {identify(case, prefix)} carries a <{child.tag}> element"
                )

    missing = []
    for wanted in identities:
        matches = [
            case
            for case in testcases
            if normalize_classname(case.get("classname", ""), prefix) == wanted["classname"]
            and case.get("name", "") == wanted["name"]
        ]
        if len(matches) != 1:
            missing.append(
                f"expected exactly one testcase with classname={wanted['classname']!r} "
                f"name={wanted['name']!r}, found {len(matches)}"
            )
    if missing:
        found = sorted(identify(case, prefix) for case in testcases)
        raise ModuleError(
            f"{module}: " + "; ".join(missing) + f"; parsed {executed} testcase(s): {found}"
        )

    return (
        f"{module}: {executed} executed / {totals['skipped']} skipped / "
        f"{totals['failures']} failed / {totals['errors']} errored — verified "
        f"{len(identities)} exact identit{'y' if len(identities) == 1 else 'ies'}"
    )


def run_gate(gate_name: str, expected_modules: list[dict]) -> None:
    """Check every configured module, then print or raise a single combined verdict."""
    verified: list[str] = []
    failures: list[str] = []
    for expected in expected_modules:
        try:
            verified.append(check_module(expected))
        except ModuleError as error:
            failures.append(str(error))

    if failures:
        for line in verified:
            print(f"  ok: {line}")
        raise SystemExit(
            f"{gate_name} FAILED (every module was checked, so no verdict is hidden):\n"
            + "\n".join(f"  {message}" for message in failures)
        )

    print(f"{gate_name} live:")
    for line in verified:
        print(f"  {line}")

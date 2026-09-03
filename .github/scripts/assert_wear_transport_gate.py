#!/usr/bin/env python3
"""Un-suppressible source gate for the Wear transport privacy blocker.

The privacy gate on sending workout payloads (wear-phase-1-active-workout-tile.md
section 6) is enforced in two layers. Detekt is the fast one: `ForbiddenImport`
covers imports, `WearDataLayerApiRule` covers every spelling that carries no
import, and both run in the pre-commit hook. Neither can be the whole gate,
because both are ordinary detekt rules and detekt honours `@Suppress` by rule id
and by rule-set id -- this repository already suppresses custom rule ids in five
places, so the mechanism is live, not hypothetical. A rule cannot police its own
suppression either: `@Suppress("WearDataLayerApiRule")` silences the very finding
that would have reported the annotation.

This script is that second layer, and it is deliberately not a detekt rule:

1. No tracked Kotlin or Java source may contain the Data Layer package name at
   all. Text matching, not AST matching, so it also covers the reflective route
   (`Class.forName("com.google.android.gms.wearable.Wearable")`) that no AST
   visitor can see. That route needs no build-file edit in `app/wear` or
   `feature/wear-bridge`, which already declare `play-services-wearable`.

2. No tracked source may suppress the gate, by rule id, by rule-set id, by
   detekt's prefixed spellings, or by a blanket `ALL`.

Java is scanned for the same reason it is easy to forget: detekt does not read it
at all, so both detekt layers are blind to a `.java` call site by construction.

The single exemption is `lint-rules/`, where the gate is defined and tested: the
rule names the package it bans, and its fixtures spell out the violations it must
catch. Nothing there is a transport call site.

Run from the repository root:

    python3 .github/scripts/assert_wear_transport_gate.py
    python3 .github/scripts/assert_wear_transport_gate.py --self-test
"""

import re
import subprocess
import sys
from pathlib import Path

FORBIDDEN_PACKAGE = "com.google.android.gms.wearable"

# Matched on a package boundary, so `...gms.wearablefake` is not a hit. Only source files are
# scanned, which is why this script may spell the package it bans.
FORBIDDEN_REFERENCE = re.compile(re.escape(FORBIDDEN_PACKAGE) + r"(?![A-Za-z0-9_])")

# The gate defines and tests itself here; every other tracked Kotlin file is a call site.
EXEMPT_PREFIXES = ("lint-rules/",)

# Every argument that would silence either half of the gate. Rule ids, the rule-set ids that
# contain them, detekt's `detekt:`/`detekt.` prefixed spellings, and the blanket form.
SUPPRESSION_TARGETS = (
    "WearDataLayerApiRule",
    "ForbiddenImport",
    "mvi-architecture",
    "style",
    "ALL",
    "all",
)

_TARGETS = "|".join(
    re.escape(target) for target in SUPPRESSION_TARGETS
)
SUPPRESS_CALL = re.compile(r"@(?:file:)?Suppress\s*\(([^)]*)\)", re.DOTALL)
SUPPRESSED_TARGET = re.compile(rf'"(?:detekt[:.])?(?:{_TARGETS})"')


# Java as well as Kotlin: these are Android modules, AGP compiles `.java` in the same variants,
# and detekt does not read Java at all — so a tracked `.java` transport call site would be invisible
# to BOTH detekt rules. There are none today; the glob is here so adding one is not a way in.
SOURCE_GLOBS = ("*.kt", "*.kts", "*.java")


def tracked_source_files() -> list[Path]:
    """Tracked sources only: an untracked scratch file is not what ships."""
    out = subprocess.run(
        ["git", "ls-files", "-z", *SOURCE_GLOBS],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    return [Path(name) for name in out.split("\0") if name]


def is_exempt(path: Path) -> bool:
    return str(path).startswith(EXEMPT_PREFIXES)


def package_violations(path: Path, text: str) -> list[str]:
    return [
        f"{path}:{number}: names {FORBIDDEN_PACKAGE}"
        for number, line in enumerate(text.splitlines(), start=1)
        if FORBIDDEN_REFERENCE.search(line)
    ]


def suppression_violations(path: Path, text: str) -> list[str]:
    violations = []
    for match in SUPPRESS_CALL.finditer(text):
        silenced = SUPPRESSED_TARGET.findall(match.group(1))
        if not silenced:
            continue
        number = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{path}:{number}: @Suppress({match.group(1).strip()}) silences the Wear transport gate"
        )
    return violations


def scan(paths: list[Path]) -> list[str]:
    violations: list[str] = []
    for path in paths:
        if is_exempt(path):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        violations += package_violations(path, text)
        violations += suppression_violations(path, text)
    return violations


def self_test() -> int:
    """A gate never shown to fire is not a gate. Both anchors, on synthetic content."""
    cases = [
        ("clean file", "package io.github.stslex.workeeper.wear\n\nval x = 1\n", 0),
        (
            "java import",
            f"package io.github.stslex.workeeper.wear;\n\nimport {FORBIDDEN_PACKAGE}.Wearable;\n",
            1,
        ),
        (
            "reflective load",
            'val c = Class.forName("' + FORBIDDEN_PACKAGE + '.Wearable")\n',
            1,
        ),
        ("qualified call", "val c = " + FORBIDDEN_PACKAGE + ".Wearable.get()\n", 1),
        ("package directive", "package " + FORBIDDEN_PACKAGE + "\n", 1),
        ('rule suppression', '@file:Suppress("WearDataLayerApiRule")\n', 1),
        ('rule-set suppression', '@Suppress("style")\nval x = 1\n', 1),
        ('prefixed suppression', '@Suppress("detekt:ForbiddenImport")\nval x = 1\n', 1),
        ('blanket suppression', '@file:Suppress("ALL")\n', 1),
        ('unrelated suppression', '@Suppress("TooManyFunctions")\nval x = 1\n', 0),
        ("near-miss package", "package com.google.android.gms.wearablefake\n", 0),
    ]
    failures = 0
    for name, content, expected in cases:
        path = Path("synthetic.kt")
        found = len(package_violations(path, content) + suppression_violations(path, content))
        verdict = "ok" if found == expected else "MISMATCH"
        if found != expected:
            failures += 1
        print(f"  [{verdict}] {name}: {found} violation(s), expected {expected}")
    if failures:
        print(f"\nself-test FAILED: {failures} case(s) disagree")
        return 1
    print(f"\nself-test passed: {len(cases)} cases, both anchors exercised")
    return 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()

    paths = tracked_source_files()
    scanned = [path for path in paths if not is_exempt(path)]
    violations = scan(paths)

    print(f"wear transport gate: {len(scanned)} tracked source file(s) scanned, "
          f"{len(paths) - len(scanned)} exempt under {', '.join(EXEMPT_PREFIXES)}")
    if not violations:
        print(f"no reference to {FORBIDDEN_PACKAGE}, and nothing suppresses the gate")
        return 0

    print(f"\n{len(violations)} violation(s):\n")
    for violation in violations:
        print(f"  {violation}")
    print(
        "\nSending any workout payload over the Wearable Data Layer is blocked on the privacy\n"
        "review in documentation/feature-specs/wear-phase-1-active-workout-tile.md section 6.\n"
        "This gate is not a detekt rule precisely so that it cannot be suppressed from source."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())

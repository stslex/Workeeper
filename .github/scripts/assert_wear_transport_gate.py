#!/usr/bin/env python3
r"""Un-suppressible source gate for the Wear transport privacy blocker.

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
   all, after the text is canonicalised the way its compiler reads it: Java
   `\uXXXX` escapes decoded, comments reduced to one separating space, and
   trivia around the dots of a qualified name collapsed. Text matching, not AST
   matching, so it also covers the reflective route
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

# javac decodes `\uXXXX` in step 1 of lexical translation, ANYWHERE in the file including inside
# identifiers, so `we\u0061rable` compiles as `wearable` while the raw bytes contain no such
# package. Kotlin has no equivalent source-level pass, so this normalisation is applied to Java
# only, exactly matching what its compiler does. Over-decoding could only ever add a match.
JAVA_UNICODE_ESCAPE = re.compile(r"\\u+([0-9a-fA-F]{4})")

# Both languages allow trivia between the tokens of a qualified name, so `com. /*gap*/ google` and
# a name split across lines are the same name to the compiler and must be the same name here.
SPACES_AROUND_DOT = re.compile(r"[ \t]*\.[ \t]*")
WHITESPACE_AROUND_DOT = re.compile(r"\s*\.\s*")


def strip_comments(text: str) -> str:
    """Comments become one space -- what a tokenizer does with them.

    One space, not nothing: `a/*x*/b` is two tokens to both compilers and must not be joined into
    one. String and character literals are walked rather than skipped, so a `//` inside a URL
    literal does not eat the rest of its line, and newlines are preserved so reported line numbers
    stay true.
    """
    out: list[str] = []
    index = 0
    end = len(text)
    while index < end:
        char = text[index]
        if text.startswith('"""', index):
            close = text.find('"""', index + 3)
            close = end if close == -1 else close + 3
            out.append(text[index:close])
            index = close
        elif char in "\"'":
            cursor = index + 1
            while cursor < end:
                if text[cursor] == "\\":
                    cursor += 2
                    continue
                if text[cursor] == char or text[cursor] == "\n":
                    cursor += 1
                    break
                cursor += 1
            out.append(text[index:cursor])
            index = cursor
        elif text.startswith("//", index):
            close = text.find("\n", index)
            close = end if close == -1 else close
            out.append(" ")
            index = close
        elif text.startswith("/*", index):
            close = text.find("*/", index + 2)
            close = end if close == -1 else close + 2
            out.append(" " + "\n" * text.count("\n", index, close))
            index = close
        else:
            out.append(char)
            index += 1
    return "".join(out)


def canonical(path: Path, text: str) -> str:
    """The text as its compiler reads it: escapes decoded, comments gone."""
    if path.suffix == ".java":
        # javac decodes escapes in step 1 of lexical translation, before it tokenises -- so this
        # runs first, and only for Java. Kotlin has no equivalent source-level pass.
        text = JAVA_UNICODE_ESCAPE.sub(lambda m: chr(int(m.group(1), 16)), text)
    return strip_comments(text)


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
    """[text] must already be [canonical] for its language."""
    violations = [
        f"{path}:{number}: names {FORBIDDEN_PACKAGE}"
        for number, line in enumerate(SPACES_AROUND_DOT.sub(".", text).splitlines(), start=1)
        if FORBIDDEN_REFERENCE.search(line)
    ]
    if violations:
        return violations
    # A qualified name split across lines is one name to the compiler. Collapsing newlines too
    # would move every line number after it, so this second pass reports the file instead.
    if FORBIDDEN_REFERENCE.search(WHITESPACE_AROUND_DOT.sub(".", text)):
        return [f"{path}: names {FORBIDDEN_PACKAGE}, split across lines"]
    return violations


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
        text = canonical(path, path.read_text(encoding="utf-8", errors="replace"))
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
        # javac decodes this to the forbidden package before it tokenises; the raw bytes do not
        # contain it, so an undecoded scan reports nothing while the call compiles.
        (
            "java unicode escape",
            "import com.google.android.gms.we\\u0061rable.Wearable;\n",
            1,
        ),
        (
            "java doubled-u escape",
            "import com.google.android.gms.we\\uu0061rable.Wearable;\n",
            1,
        ),
        # The same bytes in Kotlin are NOT decoded by kotlinc, so they name no package.
        ("kotlin escape is not decoded", "import com.google.android.gms.we\\u0061rable.Wearable\n", 0),
        # Trivia between the tokens of a qualified name: legal in both languages, one name to both
        # compilers, and invisible to a contiguous-text match.
        (
            "java comment inside the name",
            "import com./*gap*/google.android.gms.wearable.Wearable;\n",
            1,
        ),
        (
            "kotlin comment inside the name",
            "val c = com. /*gap*/ google.android.gms.wearable.Wearable\n",
            1,
        ),
        (
            "name split across lines",
            "val c = com.\n    google.android.gms.wearable.Wearable\n",
            1,
        ),
        # A `//` inside a string literal is not a comment, so the rest of its line still counts.
        (
            "string literal is not a comment",
            'val u = "https://example.com"; val c = com.google.android.gms.wearable.Wearable\n',
            1,
        ),
        # A commented-out reference is not a call site. Comments are trivia to the compiler and to
        # this gate alike.
        ("commented-out reference", "// com.google.android.gms.wearable.Wearable\n", 0),
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
        path = Path("synthetic.java" if name.startswith("java") else "synthetic.kt")
        text = canonical(path, content)
        found = len(package_violations(path, text) + suppression_violations(path, text))
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

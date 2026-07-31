#!/usr/bin/env python3
"""Personal data must not appear in source, resources, drawings or committed images.

## Why this exists, and why review cannot be the guard

A real email address and a real name shipped as fixture data in `BackupSection.kt`'s `@Preview`
and `SettingsGoldenTest`'s signed-in state — and the golden fixture **renders into committed PNGs**,
so `ilya977.077@gmail.com` and the account holder's name were legible in four images in a public
repository. They had been through the reviews that landed the settings rebuild.

That is the same shape as the token-parity seam: a class of defect that is individually obvious and
collectively invisible, because nobody re-reads a fixture they have already read. So it is checked
mechanically, with every exception named individually and cited, rather than pattern-loosened.

    python3 documentation/personal_data_gate.py        # quiet unless it fails
    python3 documentation/personal_data_gate.py -v     # evidence table

## What it can and cannot see

It reads TEXT. A name rendered into a PNG is not reachable by grep, so this gate guards the
*fixture* rather than the image — which is the right place, because every rendered leak arrives
through one. The images are covered transitively: change the fixture, re-record, and the pixels
follow. There is deliberately no OCR here; a gate that half-reads images would be worse than one
that states its scope.

Placeholder conventions are the fix, not just the absence of real data. `SettingsScreen.kt` already
used `user@example.com` / `"User"` while the other three sites carried the real values — the
convention existed and was diverged from, which is exactly what a mechanical check catches and a
reviewer does not.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys

# --- what counts as personal data -------------------------------------------------------------

# Each pattern carries the reason for THE PATTERN, not for the name.
PATTERNS: dict[str, str] = {
    # Any address that is not on a reserved documentation domain. RFC 2606 reserves example.com /
    # .org / .net and `.invalid`/`.test`/`.example` precisely so fixtures have somewhere safe to
    # live, so the check is "is it reserved", not "is it Ilya's".
    "email": r"[A-Za-z0-9._%+-]+@(?!example\.(?:com|org|net)\b)(?!.*\.(?:invalid|test|example)\b)"
             r"[A-Za-z0-9.-]+\.[A-Za-z]{2,}",
}

# Literal strings that are personal but match no general pattern — a human name is not
# distinguishable from any other capitalised pair by regex, so the ones this repo has carried are
# listed by hand. Adding a name here is cheap; the alternative is a name detector, which would
# either miss most names or flag every two-word identifier in the tree.
LITERALS: tuple[str, ...] = (
    "Ilya Alexandrovich",
)

# Exceptions, each cited individually. Loosening this to a directory glob would make the check stop
# seeing a real leak among the noise of expected ones — the same argument `shell_gate.py` makes for
# its token exceptions.
EXCEPTIONS: dict[str, str] = {
    "CODE_OF_CONDUCT.md": "the project's DELIBERATE contact address. A code of conduct with no "
                          "route to a human is not a code of conduct, and the address is published "
                          "on purpose — this is the one file where the value is the point.",
    "docs/index.md": "the published privacy policy's contact address, and the file is LOCKED BY "
                     "PLAY CONSOLE (CLAUDE.md: 'do not modify'). A privacy policy is required to "
                     "name a data controller, so the address is the point here too — and this is "
                     "the one exception that could not be fixed even if it were wrong.",
}

SCANNED_SUFFIXES = (".kt", ".kts", ".java", ".xml", ".html", ".md", ".json", ".yml", ".yaml", ".pro")

SKIP_DIRS = {".git", "build", ".gradle", ".idea", "__pycache__", ".kotlin"}


def repo_root() -> pathlib.Path:
    root = pathlib.Path(__file__).resolve().parent.parent
    if not (root / "settings.gradle.kts").is_file():
        raise SystemExit(f"personal_data_gate: derived root {root} has no settings.gradle.kts")
    return root


def tracked_files(root: pathlib.Path) -> list[pathlib.Path]:
    """Git-tracked files only.

    Untracked scratch files are not in the repository and are not what this gate is about; scanning
    them would make a local working directory able to fail a check about what is published.
    """
    out = subprocess.run(
        ["git", "ls-files", "-z"], cwd=root, capture_output=True, text=True, check=True,
    ).stdout
    return [root / p for p in out.split("\0") if p]


def scan(root: pathlib.Path) -> tuple[list[str], list[str]]:
    fails: list[str] = []
    excused: list[str] = []
    compiled = {name: re.compile(rx) for name, rx in PATTERNS.items()}

    for path in tracked_files(root):
        rel = path.relative_to(root).as_posix()
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.suffix not in SCANNED_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue

        hits: list[tuple[int, str, str]] = []
        for lineno, line in enumerate(text.splitlines(), start=1):
            for kind, rx in compiled.items():
                for match in rx.finditer(line):
                    value = match.group(0)
                    # `this@Receiver.member` is Kotlin's qualified-this, not an address. Discarded
                    # here rather than by a lookbehind in the pattern: the pattern is about what an
                    # address looks like, and this is about what Kotlin syntax looks like — mixing
                    # the two would make the pattern unreadable and unauditable.
                    if value.startswith("this@"):
                        continue
                    hits.append((lineno, kind, value))
            for literal in LITERALS:
                if literal in line:
                    hits.append((lineno, "name", literal))

        if not hits:
            continue
        if rel in EXCEPTIONS:
            excused.append(f"    (excused) {rel} — {EXCEPTIONS[rel]}")
            continue
        for lineno, kind, value in hits:
            fails.append(f"    {rel}:{lineno}  {kind}: {value}")

    return fails, excused


def main() -> int:
    ap = argparse.ArgumentParser(description="Personal data must not be committed.")
    ap.add_argument("-v", "--verbose", action="store_true", help="print the evidence when green")
    args = ap.parse_args()

    root = repo_root()
    fails, excused = scan(root)

    if fails:
        print("  [FAIL] personal data in tracked files")
        for line in fails:
            print(line)
        print()
        print("  Use a reserved placeholder — this repo's convention is already")
        print("  `user@example.com` / \"User\" (SettingsScreen.kt). If a value is deliberate and")
        print("  published on purpose, add the FILE to EXCEPTIONS with the reason, never widen a")
        print("  pattern. A fixture change needs its goldens re-recorded: the images carry what")
        print("  the fixture renders, and this gate cannot read pixels.")
        return 1

    if args.verbose:
        print("  [PASS] no personal data in tracked files")
        print(f"           {len(PATTERNS)} pattern(s), {len(LITERALS)} literal(s), "
              f"{len(EXCEPTIONS)} named exception(s)")
        for line in excused:
            print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())

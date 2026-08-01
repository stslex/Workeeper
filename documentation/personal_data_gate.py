#!/usr/bin/env python3
"""Personal data must not appear in source, resources, drawings or committed images.

## Why this exists, and why review cannot be the guard

A real email address and a real name shipped as fixture data in `BackupSection.kt`'s `@Preview`
and `SettingsGoldenTest`'s signed-in state — and the golden fixture **renders into committed PNGs**,
so both were legible at full size in committed images in a public repository. They had been through
the reviews that landed the settings rebuild. The values are not quoted here; a file that names them
is a file that carries them, which is the whole point of the gate.

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

**Every tracked file is a candidate, and the skip list is by binary suffix rather than an allowlist
of text ones.** An allowlist is blind to whatever kind of file nobody thought of, and blindness by
omission reads exactly like a pass — a gate that names the kinds it looks at reports "clean" about
the kinds it does not. Anything that fails to decode as UTF-8 is skipped in the same breath, so the
denylist is a speed measure and not the correctness boundary.

Placeholder conventions are the fix, not just the absence of real data. `SettingsScreen.kt` already
used `user@example.com` / `"User"` while the other three sites carried the real values — the
convention existed and was diverged from, which is exactly what a mechanical check catches and a
reviewer does not.
"""

from __future__ import annotations

import argparse
import hashlib
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

# Exceptions are keyed by (file, EXACT VALUE), never by file alone. A file-level excuse waves
# through every hit in the file, and it does it precisely where personal values already live — the
# two files below carry two DIFFERENT real addresses, so "this file is allowed to have one" cannot
# tell a third one from the excused one. That is also the scoping `shell_gate.py` uses for its token
# exceptions, which this list cites as its model: it excepts a value, not a drawing.
#
# The value is keyed by SHA-256 rather than in the clear, and the asymmetry with LITERALS above is
# deliberate. A detector that hashed what it looks for would be unreadable — nobody could say what
# the gate matches — but an EXCEPTION only has to answer "is this exact hit the excused one", which
# a digest answers exactly, and the reason string says in words which value it is. Written in the
# clear, this table would be a second copy of every address the gate exists to keep out of the tree,
# in the file least able to argue it is an accident. Mint one with:
#
#     python3 -c "import hashlib,sys; print(hashlib.sha256(sys.argv[1].encode()).hexdigest())" VALUE
EXCEPTIONS: dict[tuple[str, str], str] = {
    ("CODE_OF_CONDUCT.md", "c3b8a3f96959251b3a93767be7751cf083aff510fd5167e4306cb2285dd2275f"):
        "the project's DELIBERATE contact address. A code of conduct with no route to a human is "
        "not a code of conduct, and the address is published on purpose — this is the one file "
        "where the value is the point.",
    ("docs/index.md", "fad486d71013d1ab2ac71a5db614949f2f81706c06dcbf36e38b4fda7e703f5f"):
        "the published privacy policy's contact address, and the file is LOCKED BY PLAY CONSOLE "
        "(CLAUDE.md: 'do not modify'). A privacy policy is required to name a data controller, so "
        "the address is the point here too — and this is the one exception that could not be fixed "
        "even if it were wrong.",
    ("documentation/personal_data_gate.py", "22124678c5287a6e7a73a12d63b2fb38f147f8c57f9eb468050fd6020e5469f1"):
        "this gate's own LITERALS list, which is the one site where a forbidden value must appear "
        "in the clear: a name detector cannot look for a name without naming it. Bound to that "
        "exact value, so any OTHER personal value appearing in this file still fails.",
}

# Binary kinds, skipped for speed. NOT the correctness boundary: everything else tracked is read,
# and anything that fails to decode as UTF-8 is skipped where it is read. An allowlist of text
# suffixes sat here first and it is what let this gate's own `.py` source go unscanned while it
# reported PASS.
BINARY_SUFFIXES = frozenset({
    ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico", ".pdf",
    ".ttf", ".otf", ".woff", ".woff2",
    ".jar", ".aar", ".zip", ".apk", ".aab", ".keystore", ".jks", ".so", ".bin", ".db",
})

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
        if path.suffix.lower() in BINARY_SUFFIXES:
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

        # Per HIT, not per file: an excused value in a file does not excuse the next value in it.
        for lineno, kind, value in hits:
            reason = EXCEPTIONS.get((rel, hashlib.sha256(value.encode()).hexdigest()))
            if reason is None:
                fails.append(f"    {rel}:{lineno}  {kind}: {value}")
            else:
                excused.append(f"    (excused) {rel}:{lineno} {kind} — {reason}")

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
        print("  published on purpose, add the (FILE, sha256-of-VALUE) pair to EXCEPTIONS with the")
        print("  reason — never the file alone and never a wider pattern. Mint the digest with the")
        print("  one-liner in this file's EXCEPTIONS comment. A fixture change needs its goldens")
        print("  re-recorded: the images carry what the fixture renders, and this cannot read pixels.")
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

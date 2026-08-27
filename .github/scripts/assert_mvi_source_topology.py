#!/usr/bin/env python3
"""Source-set topology oracle for `core:ui:mvi`, run inside `Build and Unit Tests`.

This closes the legacy-source false green. Assembling a KMP device-test APK can
succeed while a test left behind in `src/androidTest` executes zero times: the
KMP module compiles `src/androidDeviceTest`, so the orphan is neither compiled
nor run, and nothing else notices it is gone. An old directory that happens not
to be compiled is not harmless, so it is rejected structurally rather than
trusted to stay empty.

It also pins the platform split the conversion exists to create — Android,
Java and Firebase types must not reach common or iOS production — and the
deletion of the Context-backed generation seam.

Run from the repository root:

    python3 .github/scripts/assert_mvi_source_topology.py
"""

import re
import sys
from pathlib import Path

MODULE = Path("core/ui/mvi")
SRC = MODULE / "src"

# §5.4: at exit there is zero Kotlin under any of these.
LEGACY_DIRS = ("main", "test", "androidTest")

# The intended split. Every one of these must exist and hold at least one Kotlin file,
# so a source set silently losing its content is a failure and not a quiet pass.
REQUIRED_SOURCE_SETS = (
    "commonMain",
    "androidMain",
    "iosMain",
    "commonTest",
    "androidHostTest",
    "androidDeviceTest",
    "iosTest",
)

# Platform-typed imports that must never appear in common or iOS production code.
FORBIDDEN_IMPORTS = re.compile(
    r"^\s*import\s+(android\.|androidx\.activity\.|java\.|javax\.|com\.google\.firebase\.)",
    re.MULTILINE,
)

# Only these source sets are allowed to name the Android deps holder.
ANDROID_ONLY_SYMBOLS = ("AppDepsHolder",)

# Removed with the Context-backed lifetime lookup; nothing may reintroduce the name.
DELETED_SYMBOLS = ("StoreGenerationDeps",)


def kotlin_files(source_set: str) -> list[Path]:
    root = SRC / source_set
    return sorted(root.rglob("*.kt")) if root.is_dir() else []


def is_test_source(path: Path) -> bool:
    """Return whether a Kotlin path belongs to a classic or KMP test source set."""
    parts = path.parts
    try:
        source_set = parts[parts.index("src") + 1]
    except (ValueError, IndexError):
        return False
    return "test" in source_set.lower()


def main() -> None:
    failures: list[str] = []

    if not SRC.is_dir():
        raise SystemExit(f"topology gate FAILED: {SRC} does not exist — run from the repo root")

    # 1. No Kotlin may survive in a legacy directory.
    for legacy in LEGACY_DIRS:
        stragglers = kotlin_files(legacy)
        if stragglers:
            failures.append(
                f"{SRC / legacy} still holds {len(stragglers)} Kotlin file(s): "
                f"{[str(path) for path in stragglers]}. A KMP module compiles "
                f"src/androidDeviceTest, not src/androidTest, so these execute zero times."
            )

    # 2. Every intended source set exists and is populated.
    for source_set in REQUIRED_SOURCE_SETS:
        if not kotlin_files(source_set):
            failures.append(
                f"{SRC / source_set} holds no Kotlin file; the intended "
                f"common/Android/iOS/device split requires it"
            )

    # 3. Common and iOS production stay free of Android, Java and Firebase types.
    for source_set in ("commonMain", "iosMain"):
        for path in kotlin_files(source_set):
            for match in FORBIDDEN_IMPORTS.finditer(path.read_text(encoding="utf-8")):
                failures.append(
                    f"{path} imports a platform type in {source_set}: {match.group(0).strip()!r}"
                )

    # 4. The Android deps holder is Android-only.
    for source_set in ("commonMain", "iosMain", "commonTest", "iosTest"):
        for path in kotlin_files(source_set):
            text = path.read_text(encoding="utf-8")
            for symbol in ANDROID_ONLY_SYMBOLS:
                if symbol in text:
                    failures.append(f"{path} names {symbol}, which must stay Android-only")

    # 5. The deleted Context seam stays deleted from production. The ABI oracle intentionally
    # names the removed class to prove it cannot be loaded.
    for symbol in DELETED_SYMBOLS:
        hits = [
            str(path)
            for path in Path(".").rglob("*.kt")
            if ".git" not in path.parts
            and "build" not in path.parts
            and ".claude" not in path.parts
            and not is_test_source(path)
            and symbol in path.read_text(encoding="utf-8", errors="ignore")
        ]
        if hits:
            failures.append(
                f"{symbol} was deleted with the Context-backed lifetime lookup but is "
                f"still named in: {hits}"
            )

    if failures:
        raise SystemExit(
            "topology gate FAILED:\n" + "\n".join(f"  {message}" for message in failures)
        )

    counts = ", ".join(f"{name}={len(kotlin_files(name))}" for name in REQUIRED_SOURCE_SETS)
    print("topology gate live:")
    print(f"  core:ui:mvi legacy dirs empty (src/main, src/test, src/androidTest)")
    print(f"  Kotlin per source set: {counts}")
    print("  no android./java./javax./firebase imports in commonMain or iosMain")
    print("  StoreGenerationDeps absent from production Kotlin")


if __name__ == "__main__":
    sys.exit(main())

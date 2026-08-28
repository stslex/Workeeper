#!/usr/bin/env python3
"""Explicit source-topology oracle for shared KMP UI leaf modules.

The manifest is intentionally path-based rather than count-based: moving a reviewed file into a
different source set while adding a same-count replacement must fail. Add future shared UI leaves
as new manifest entries instead of weakening the checks for the first migrated leaf.

Run from the repository root:

    python3 .github/scripts/assert_kmp_ui_source_topology.py
"""

import re
import sys
from pathlib import Path


MODULES = {
    "core:ui:start-mode": {
        "root": Path("core/ui/start-mode"),
        "files": {
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/start_mode/StartCardModeName.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/start_mode/StartCardModeSheet.kt",
            "src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/start_mode/model/StartCardModeUi.kt",
            "src/commonMain/composeResources/values/strings.xml",
            "src/commonMain/composeResources/values-ru/strings.xml",
            "src/commonTest/kotlin/io/github/stslex/workeeper/core/ui/start_mode/model/StartCardModeCatalogTest.kt",
            "src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/start_mode/golden/StartCardModeSheetGoldenTest.kt",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.start_mode.golden_StartCardModeSheetGoldenTest_modeSheet_dark.png",
            "src/androidHostTest/snapshots/images/io.github.stslex.workeeper.core.ui.start_mode.golden_StartCardModeSheetGoldenTest_modeSheet_light.png",
            "src/iosTest/kotlin/io/github/stslex/workeeper/core/ui/start_mode/StartModeSceneIosTest.kt",
        },
        "kotlin_source_sets": {
            "commonMain",
            "commonTest",
            "androidHostTest",
            "iosTest",
        },
        "resource_dirs": {
            "src/commonMain/composeResources/values",
            "src/commonMain/composeResources/values-ru",
        },
    },
}

LEGACY_SOURCE_SETS = ("main", "test", "androidTest")

FORBIDDEN_IMPORT = re.compile(
    r"^\s*import\s+(android\.|androidx\.compose\.ui\.res(?:\.|\s|$)|java\.|javax\.)",
    re.MULTILINE,
)

ANDROID_R_ACCESS = re.compile(r"(?<![A-Za-z0-9_])R\.")


def files_below(root: Path) -> list[Path]:
    return sorted(path for path in root.rglob("*") if path.is_file()) if root.is_dir() else []


def kotlin_files(source_root: Path, source_set: str) -> list[Path]:
    root = source_root / source_set
    return sorted(root.rglob("*.kt")) if root.is_dir() else []


def check_module(name: str, manifest: dict) -> list[str]:
    failures: list[str] = []
    root = manifest["root"]
    source_root = root / "src"

    if not source_root.is_dir():
        return [f"{name}: {source_root} does not exist; run from the repository root"]

    actual_files = {
        path.relative_to(root).as_posix()
        for path in files_below(source_root)
    }
    missing = sorted(manifest["files"] - actual_files)
    extra = sorted(actual_files - manifest["files"])
    if missing or extra:
        failures.append(f"{name}: source manifest mismatch; missing={missing}, extra={extra}")

    for legacy in LEGACY_SOURCE_SETS:
        stragglers = files_below(source_root / legacy)
        if stragglers:
            failures.append(
                f"{name}: legacy source set src/{legacy} still contains "
                f"{[path.relative_to(root).as_posix() for path in stragglers]}"
            )

    kotlin_by_source_set = {
        path.name: kotlin_files(source_root, path.name)
        for path in sorted(source_root.iterdir())
        if path.is_dir() and kotlin_files(source_root, path.name)
    }
    unexpected_source_sets = sorted(
        set(kotlin_by_source_set) - manifest["kotlin_source_sets"]
    )
    for source_set in unexpected_source_sets:
        failures.append(
            f"{name}: unexpected Kotlin-bearing source set src/{source_set}: "
            f"{[path.relative_to(root).as_posix() for path in kotlin_by_source_set[source_set]]}"
        )

    for relative in sorted(manifest["resource_dirs"]):
        resource_dir = root / relative
        if not resource_dir.is_dir():
            failures.append(f"{name}: required CMP resource directory is missing: {relative}")

    android_resources = files_below(source_root / "main" / "res")
    if android_resources:
        failures.append(
            f"{name}: Android resources remain under src/main/res: "
            f"{[path.relative_to(root).as_posix() for path in android_resources]}"
        )

    for source_set in ("commonMain", "iosMain"):
        for path in kotlin_files(source_root, source_set):
            source = path.read_text(encoding="utf-8")
            for match in FORBIDDEN_IMPORT.finditer(source):
                failures.append(
                    f"{name}: {path.relative_to(root)} imports a platform API in "
                    f"{source_set}: {match.group(0).strip()!r}"
                )
            if ANDROID_R_ACCESS.search(source):
                failures.append(
                    f"{name}: {path.relative_to(root)} uses Android R from {source_set}; "
                    "use the generated Compose Res class"
                )

    return failures


def main() -> None:
    failures: list[str] = []
    for name, manifest in MODULES.items():
        failures.extend(check_module(name, manifest))

    if failures:
        raise SystemExit(
            "shared KMP UI topology gate FAILED:\n"
            + "\n".join(f"  {failure}" for failure in failures)
        )

    print("shared KMP UI topology gate live:")
    for name, manifest in MODULES.items():
        print(f"  {name}: {len(manifest['files'])} exact source/resource/test files")
        for path in sorted(manifest["files"]):
            print(f"    {path}")
    print("  legacy source sets empty; common/native production has no Android/Java/Javax API")


if __name__ == "__main__":
    sys.exit(main())

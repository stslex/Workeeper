#!/usr/bin/env python3
"""Compute the CI cache key before Gradle resolves the Native toolchain."""

import os
from pathlib import Path
import re
import tomllib


def cache_key(gradle_dir: Path, runner_os: str) -> tuple[str, str]:
    if runner_os not in {"Linux", "macOS", "Windows"}:
        raise ValueError(f"invalid RUNNER_OS: {runner_os!r}")
    with (gradle_dir / "libs.versions.toml").open("rb") as catalog:
        version = tomllib.load(catalog)["versions"]["kotlin"]
    if not isinstance(version, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.+-]*", version):
        raise ValueError("expected one pinned Kotlin version in libs.versions.toml")
    targets = (gradle_dir / "kmp-native-targets.txt").read_text(encoding="utf-8").splitlines()
    targets = [name.strip() for name in targets if name.strip()]
    if not targets or len(set(targets)) != len(targets):
        raise ValueError("kmp-native-targets.txt must contain a nonempty, unique target list")
    if any(not re.fullmatch(r"[a-z][A-Za-z0-9]*", target) for target in targets):
        raise ValueError("kmp-native-targets.txt contains an invalid target identifier")
    prefix = f"konan-{runner_os}-kotlin-{version}-"
    return prefix + "-".join(sorted(targets)), prefix


def main() -> None:
    try:
        key, prefix = cache_key(Path("gradle"), os.environ["RUNNER_OS"])
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            output.write(f"key={key}\nprefix={prefix}\n")
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise SystemExit(f"Cannot compute Kotlin/Native cache key: {error}") from error
    print(f"key={key}")


if __name__ == "__main__":
    main()

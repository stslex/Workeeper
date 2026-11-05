#!/usr/bin/env python3
"""Native XML identity oracle for the required `KMP iOS kit smoke` context.

An exit code is not evidence and neither is a total: two kit tests and zero
navigation tests would satisfy any repo-wide count, and a classname from one
case plus a method name from another can forge an identity out of substrings.

The structural rules live in `junit_identity.py`, shared with the MVI
Android-host and MVI device gates. The independent module and identity inventory is in
`gradle/kmp-native-identities.json`.

Run from the repository root, after the forced Native Gradle invocation:

    python3 .github/scripts/assert_kmp_ios_smoke.py

The workflow runs it whenever the Gradle step actually started, which covers
three distinct outcomes:

* **a red test run** — XML exists, and this script names the module and the
  tuple that broke, which a Gradle exit code cannot;
* **a compile or simulator-boot failure** — Gradle died before producing XML,
  so the script reports the missing result directory. That is the honest
  answer, not a false pass;
* **a setup failure that skipped Gradle entirely** — the workflow condition
  keeps this script skipped too, so a missing-results error cannot mask the
  real failure upstream.
"""

from pathlib import Path
import re

from junit_identity import run_gate
from kmp_native_model import IDENTITIES, SIMULATOR_START, load_model, read_json
from prove_ios_install_epoch_relaunch import validate_relaunch

# The Kotlin/Native Gradle test task prefixes every suite/testcase classname
# with its own name ("iosSimulatorArm64Test.<fqcn>"), and suffixes every method
# name with "[iosSimulatorArm64]". Both are measured from real output, never
# guessed.
KNOWN_SUITE_PREFIX = "iosSimulatorArm64Test."

TARGET_SUFFIX = "[iosSimulatorArm64]"


def load_expected() -> list[dict]:
    modules = read_json(IDENTITIES)
    if not isinstance(modules, dict) or not modules:
        raise ValueError("missing required Native module inventory")
    expected = []
    for module, classes in modules.items():
        if not re.fullmatch(r"[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)+", module):
            raise ValueError(f"invalid Native module {module!r}")
        if not isinstance(classes, dict) or not classes:
            raise ValueError(f"{module}: missing required Native classes")
        identities = []
        for classname, names in classes.items():
            if not classname or not isinstance(names, list) or not names or any(
                not isinstance(name, str) or not name for name in names
            ):
                raise ValueError(f"{module}: invalid required identities for {classname!r}")
            identities.extend(
                {"classname": classname, "name": name + TARGET_SUFFIX} for name in names
            )
        expected.append({
            "module": module,
            "results_dir": f"{module.replace(':', '/')}/build/test-results/iosSimulatorArm64Test",
            "classname_prefix": KNOWN_SUITE_PREFIX,
            "identities": identities,
        })
    return expected


EXPECTED = load_expected()


def main() -> None:
    try:
        models = {row["module"].removeprefix(":"): row for row in load_model()}
    except (OSError, ValueError) as error:
        raise SystemExit(f"native gate FAILED: {error}") from error
    relaunch_failures = ()
    try:
        binary = Path(models["app:ios"]["simulatorBinary"])
        print("  ok: " + validate_relaunch(marker=SIMULATOR_START, binary=binary))
    except (OSError, ValueError, KeyError, TypeError) as error:
        relaunch_failures = (str(error),)
    expected_modules = [
        dict(row, freshness_marker=SIMULATOR_START, results_dir=models[row["module"]]["simulatorResults"])
        for row in EXPECTED
    ]
    run_gate("native gate", expected_modules, preflight_failures=relaunch_failures)


if __name__ == "__main__":
    main()

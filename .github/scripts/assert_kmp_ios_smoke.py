#!/usr/bin/env python3
"""Native XML identity oracle for the required `KMP iOS kit smoke` context.

An exit code is not evidence and neither is a total: two kit tests and zero
navigation tests would satisfy any repo-wide count, and a classname from one
case plus a method name from another can forge an identity out of substrings.

The structural rules live in `junit_identity.py`, shared with the MVI
Android-host and MVI device gates so the three cannot drift apart. This file
is the Native gate's configuration: which modules run here, and which exact
`(classname, name)` tuples each must contain.

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

from junit_identity import run_gate

# The Kotlin/Native Gradle test task prefixes every suite/testcase classname
# with its own name ("iosSimulatorArm64Test.<fqcn>"), and suffixes every method
# name with "[iosSimulatorArm64]". Both are measured from real output, never
# guessed.
KNOWN_SUITE_PREFIX = "iosSimulatorArm64Test."

TARGET_SUFFIX = "[iosSimulatorArm64]"


def native(classname: str, name: str) -> dict:
    return {"classname": classname, "name": f"{name}{TARGET_SUFFIX}"}


MVI_PACKAGE = "io.github.stslex.workeeper.core.ui.mvi"

EXPECTED = [
    {
        "module": "core:ui:kit",
        "results_dir": "core/ui/kit/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            native(
                "io.github.stslex.workeeper.core.ui.kit.IosKitSceneSmokeTest",
                "sheetLayoutRendersMigratedStringFontAndIcon",
            ),
        ],
    },
    {
        "module": "core:ui:navigation",
        "results_dir": "core/ui/navigation/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            native(
                "io.github.stslex.workeeper.core.ui.navigation.ScreenSerializationIosTest",
                "allCurrentRoutesRoundTripThroughProductionRegistry",
            ),
        ],
    },
    {
        # Phase 7.3. Four independent claims, because one tuple could pass while the
        # Store runtime this module exists for never executed natively at all.
        "module": "core:ui:mvi",
        "results_dir": "core/ui/mvi/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            # The generation-join contract: Store jobs descend from the injected lifetime.
            native(
                f"{MVI_PACKAGE}.StoreGenerationJoinTest",
                "aStoreJobStartedViaLaunchDefaultIsJoinedByTheGenerationLifetime",
            ),
            # Navigation result delivery and clearing.
            native(
                f"{MVI_PACKAGE}.NavigationResultContractTest",
                "each produced result is delivered exactly once per cycle",
            ),
            native(
                f"{MVI_PACKAGE}.NavigationResultContractTest",
                "clear returns the destination to no-result so re-entry sees nothing",
            ),
            # Event delivery under buffer pressure.
            native(
                f"{MVI_PACKAGE}.StoreEventPressureTest",
                "everyEventSubmittedUnderBufferPressureIsObservedExactlyOnce",
            ),
            # The production rememberMetroStoreProcessor -> rememberStoreProcessor scene.
            native(
                f"{MVI_PACKAGE}.StoreProcessorSceneIosTest",
                "productionProcessorRetainsOneStoreAndDrivesTheRenderSeam",
            ),
        ],
    },
    {
        "module": "core:ui:start-mode",
        "results_dir": "core/ui/start-mode/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            native(
                "io.github.stslex.workeeper.core.ui.start_mode.StartModeSceneIosTest",
                "sheetRendersMigratedCatalogAndDispatchesSelection",
            ),
        ],
    },
]


def main() -> None:
    run_gate("native gate", EXPECTED)


if __name__ == "__main__":
    main()

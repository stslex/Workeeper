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


def natives(classname: str, names: list[str]) -> list[dict]:
    return [native(classname, name) for name in names]


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
    {
        "module": "core:ui:plan-editor",
        "results_dir": "core/ui/plan-editor/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            native(
                "io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorSceneIosTest",
                "readOnlyCopyAndEditableAddRenderAndDispatch",
            ),
        ],
    },
    {
        "module": "feature:image-viewer",
        "results_dir": "feature/image-viewer/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            native(
                "io.github.stslex.workeeper.feature.image_viewer.ImageViewerSceneIosTest",
                "resourcesBranchesAndActionsRenderAndDispatch",
            ),
        ],
    },
    {
        "module": "feature:plan-editor",
        "results_dir": "feature/plan-editor/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.plan_editor.mappers.PlanEditorMapperTest",
                [
                    "formatPlanSummary falls back to reps-only when weight is null",
                    "formatPlanSummary truncates after the fifth row with an ellipsis suffix",
                    "formatPlanSummary keeps decimals for non-integer weights",
                    "formatPlanSummary joins rows with bullet separators and formats integer weights",
                    "formatPlanSummary on empty list yields an empty string",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.plan_editor.model.SetTypeUiModelTest",
                [
                    "toUiKitType maps every variant to the kit's chip enum",
                    "every SetTypeUiModel has a unique labelRes",
                    "DROP labelRes resolves to drop string and not failure",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.plan_editor.mvi.handler.ClickHandlerTest",
                [
                    "OnConfirmDiscard closes the sheet and navigates back without persisting",
                    "back with the discard sheet open hides it and never navigates",
                    "the discard sheet and the type-change sheet cannot be open at once",
                    "OnAddSet copies reps from previous set when draft has rows",
                    "state is dirty when type differs from initialType even with stable draft",
                    "state is dirty when draft differs from initialDraft",
                    "interceptBack stays armed while the type-change sheet is shown",
                    "OnSetRemove with out-of-bounds index leaves draft unchanged",
                    "OnTypeToggle WEIGHTLESS to WEIGHTED applies new type silently regardless of draft",
                    "OnBackClick with open dialog dismisses dialog before propagating",
                    "OnTypeChangeConfirm wipes weights from draft, applies type, hides dialog",
                    "OnTypeToggle to same type is no-op",
                    "OnDismissDiscard closes the sheet without navigating",
                    "OnTypeToggle WEIGHTED to WEIGHTLESS with weighted draft opens confirm dialog",
                    "OnSetRemove drops the row at the given index",
                    "interceptBack stays enabled when type-change confirm dialog is open",
                    "OnBackClick on dirty state opens discard dialog instead of popping",
                    "OnBackClick on clean state dispatches Navigation Back",
                    "OnTypeChangeDismiss clears pending and hides dialog without changing type",
                    "OnTypeToggle with empty draft applies new type silently without dialog",
                    "OnSetTypeChange updates the type of the row at the given index",
                    "interceptBack stays armed while the discard sheet is shown",
                    "OnAddSet appends a new work set with default reps when draft is empty",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.plan_editor.mvi.handler.CommonHandlerTest",
                [
                    "NotFound clears isLoading and reports, same reason",
                    "a successful load clears isLoading and hydrates the type the seed guessed wrong",
                    "a load that throws clears isLoading, or the route is composed on nothing forever",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.plan_editor.mvi.handler.NavigationHandlerTest",
                [
                    "BackAfterSave pops handing true back to the PlanEditor destination",
                    "Back pops the navigation stack with no result attributes",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStateRouteArgTest",
                [
                    "blank trainingUuid falls through to Exercise mode rather than PerformedExercise",
                    "live workout entry maps to PerformedExercise mode",
                    "live workout adhoc entry maps to PerformedExercise mode without training uuid",
                    "null exerciseUuid is rejected because the editor needs an exercise to load against",
                    "exercise default plan entry maps to Exercise mode",
                    "single-training edit entry maps to PerformedExercise mode without performed uuid",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.plan_editor.PlanEditorFeatureSceneIosTest",
                "resourcesBranchesAndActionsRenderAndDispatch",
            ),
        ],
    },
]


def main() -> None:
    run_gate("native gate", EXPECTED)


if __name__ == "__main__":
    main()

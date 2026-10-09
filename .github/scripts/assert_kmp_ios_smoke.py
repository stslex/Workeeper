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
    {
        # Phase 7.8: the five portable suites (9/1/7/4/4) plus the one production scene, 26 exact.
        "module": "feature:archive",
        "results_dir": "feature/archive/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.archive.mvi.handler.ArchiveClickHandlerTest",
                [
                    "OnSegmentChange updates selectedSegment and emits SegmentTick haptic",
                    "OnSegmentChange to current segment is no-op",
                    "OnRestoreClick emits ContextClick haptic",
                    "OnUndoRestore emits ContextClick haptic",
                    "OnDeleteDismiss does not emit haptic",
                    "OnDeleteDismiss clears pending delete state",
                    "OnPermanentDeleteClick emits LongPress haptic and stores target",
                    "OnDeleteConfirm emits LongPress haptic and clears target",
                    "OnDeleteConfirm without target does nothing",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.archive.mvi.handler.ArchivePagingHandlerTest",
                "placeholder",
            ),
            *natives(
                "io.github.stslex.workeeper.feature.archive.mvi.mapper.ArchiveMetaLineTest",
                [
                    "an exercise leads with its kind word",
                    "a training leads with the other kind word",
                    "the kind is first, ahead of the date",
                    "tags come last, after the date",
                    "no tags leaves no dangling separator",
                    "the date is day-and-month, not a relative span",
                    "a missing timestamp degrades to the bare word rather than a wrong date",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.archive.ui.components.ArchiveListSurfaceTest",
                [
                    "rows win over everything",
                    "an unsettled refresh with no rows is loading, not empty",
                    "a failed first page is its own verdict",
                    "settled with no rows is the empty state",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.archive.ui.components.PagingTailKindTest",
                [
                    "appending draws the loading footer",
                    "a failed page draws the error footer, not silence",
                    "exhausted draws no footer at all",
                    "idle mid-list draws no footer either",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.archive.ArchiveFeatureSceneIosTest",
                "resourcesPagingBranchesAndActionsRenderAndDispatch",
            ),
        ],
    },
    {
        # Phase 7.9: the nine portable suites (3/18/2/3/2/9/4/4/4) plus the one production scene,
        # 50 exact.
        "module": "feature:all-trainings",
        "results_dir": "feature/all-trainings/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractorImplTest",
                [
                    "archiveTrainings delegates to repository bulkArchive",
                    "deleteTrainings returns target count and delegates",
                    "canPermanentlyDelete delegates to repository",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.mvi.handler.ClickHandlerTest",
                [
                    "OnTrainingClick emits haptic and navigates to OpenDetail",
                    "OnFabClick emits haptic and navigates to OpenCreate",
                    "OnFabClick with selection fires no haptic and sets pendingBulkDelete",
                    "OnTagFilterToggle adds tag when not selected",
                    "OnTagFilterToggle removes tag when already selected",
                    "OnSelectionExit clears selection mode",
                    "OnBulkDeleteConfirm calls archiveTrainings and clears selection on success",
                    "OnBulkDeleteDismiss clears pending delete",
                    "entering selection by long press fires LongPress",
                    "toggling an item inside selection fires ContextClick not LongPress",
                    "untoggling an item inside selection fires ContextClick",
                    "long press inside selection fires ContextClick not a second LongPress",
                    "toggling a tag filter fires no haptic",
                    "confirmed bulk archive fires Confirm",
                    "OnClearTagFilter empties the whole filter in one act",
                    "OnClearTagFilter fires no haptic",
                    "OnClearTagFilter on an already-empty filter changes nothing",
                    "OnEmptyCreate opens create and fires no haptic",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.mvi.handler.NavigationHandlerTest",
                [
                    "OpenDetail navigates to Screen Training with uuid",
                    "OpenCreate navigates to Screen Training with null uuid",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.mvi.store.StartBlankGateTest",
                [
                    "no workout running — the drawn pair is whole",
                    "a workout is running — the blank-start CTA withdraws",
                    "before the first emission the CTA is withheld not offered",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.ui.AllTrainingsClearanceTest",
                [
                    "list bottom clearance is the drawn 88 not the 72 it shipped with",
                    "each drawn part is the value the mockup gives it",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.ui.components.ListSurfaceTest",
                [
                    "rows win over everything",
                    "an unsettled refresh with no rows is loading not empty",
                    "loading outranks selection and the filter",
                    "a failed first page is its own verdict",
                    "no rows nothing done is the first-run empty",
                    "a filter that matches nothing is its own state not the first-run empty",
                    "selection outranks the filter because the selection block carries the filter recovery",
                    "the crossfade covers the drawn blocks and neither non-block verdict",
                    "selection empty and filtered empty are both in the crossfade so the pair transits",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingTailKindTest",
                [
                    "appending draws the loading footer",
                    "a failed page draws the error footer not silence",
                    "exhausted draws no footer at all",
                    "idle mid-list draws no footer either",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.ui.components.TopBarModeTest",
                [
                    "off is the resting bar",
                    "on is the selection bar",
                    "different selections are one mode so the count cannot drive the crossfade",
                    "an empty selection is still the selection bar",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_trainings.ui.components.TrailingSlotKindTest",
                [
                    "at rest the slot promises a destination",
                    "an unselected row in selection mode draws nothing and keeps its slot",
                    "a selected row draws the check",
                    "selected outranks selecting so the mark never blanks",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.all_trainings.AllTrainingsFeatureSceneIosTest",
                "resourcesPagingBranchesSelectionAndActionsRenderAndDispatch",
            ),
        ],
    },
]


def main() -> None:
    run_gate("native gate", EXPECTED)


if __name__ == "__main__":
    main()

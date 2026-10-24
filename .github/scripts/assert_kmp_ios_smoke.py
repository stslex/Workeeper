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
            native(
                "io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolverIosTest",
                "resolvesTheEnglishCatalog",
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
    {
        # Phase 7.10: the ten portable suites (5/24/2/1/11/2/9/4/4/4) plus the one production
        # scene, 67 exact.
        "module": "feature:all-exercises",
        "results_dir": "feature/all-exercises/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractorImplTest",
                [
                    "archiveExercise returns Success when no active trainings",
                    "archiveExercise returns Blocked when active trainings exist",
                    "restoreExercise delegates to repository",
                    "canPermanentlyDelete delegates to repository",
                    "permanentlyDelete delegates to repository",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.mvi.handler.ClickHandlerTest",
                [
                    "OnExerciseClick emits haptic and navigates to OpenDetail",
                    "OnFabClick emits haptic and navigates to OpenCreate",
                    "OnTagFilterToggle adds tag when not selected",
                    "OnTagFilterToggle removes tag when already selected",
                    "OnCancelPermanentDelete clears pending delete",
                    "OnConfirmPermanentDelete with no pending is no-op",
                    "OnConfirmPermanentDelete with pending clears state and emits Confirm haptic",
                    "OnBulkDelete fires no haptic — the morph is silent",
                    "OnTagFilterToggle fires no haptic",
                    "OnBulkDeleteConfirm emits Confirm haptic",
                    "OnBulkDelete with selection opens confirm dialog",
                    "OnBulkDeleteConfirm calls bulkArchive and clears selection on success",
                    "OnBulkDeleteConfirm opens blocked dialog and no snackbar when all blocked",
                    "OnBulkDeleteConfirm opens blocked dialog with archived summary on partial block",
                    "OnBlockedArchiveDismiss clears the blocked dialog",
                    "OnBulkDeleteDismiss clears pending",
                    "OnClearTagFilter empties the whole filter in one act",
                    "OnClearTagFilter fires no haptic",
                    "OnClearTagFilter on an already-empty filter changes nothing",
                    "entering selection by long press fires LongPress",
                    "long press inside selection fires ContextClick not a second LongPress",
                    "selection toggle fires ContextClick",
                    "selection exit fires ContextClick",
                    "OnEmptyCreate opens create and fires no haptic",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.mvi.handler.NavigationHandlerTest",
                [
                    "OpenDetail navigates to Screen Exercise with uuid",
                    "OpenCreate navigates to Screen Exercise with null uuid",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.mvi.handler.PagingHandlerTest",
                [
                    "Init invokes observeAvailableTags via scope launch",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapperTest",
                [
                    "composeFooterLabel hides every segment when all values are zero or null",
                    "composeFooterLabel renders all three segments when present",
                    "composeFooterLabel hides session segment when count is zero",
                    "composeFooterLabel hides linked-trainings segment when count is zero",
                    "composeFooterLabel hides last-trained segment when value is null",
                    "composeFooterLabel just-now segment when delta below one minute",
                    "composeFooterLabel minute granularity for sub-hour deltas",
                    "ExerciseListItemDomain toUi propagates non-zero stats into the model",
                    "ExerciseListItemDomain toUi yields empty footer when stats are absent",
                    "toBlockedArchiveDialog truncates trainings and sets archived summary on partial block",
                    "toBlockedArchiveDialog omits archived summary and truncation when nothing archived",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.ui.AllExercisesClearanceTest",
                [
                    "list bottom clearance is the drawn 88 not the 72 it shipped with",
                    "each drawn part is the value the mockup gives it",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.ui.components.ListSurfaceTest",
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
                "io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingTailKindTest",
                [
                    "appending draws the loading footer",
                    "a failed page draws the error footer not silence",
                    "exhausted draws no footer at all",
                    "idle mid-list draws no footer either",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.ui.components.TopBarModeTest",
                [
                    "off is the resting bar",
                    "on is the selection bar",
                    "different selections are one mode so the count cannot drive the crossfade",
                    "an empty selection is still the selection bar",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.all_exercises.ui.components.TrailingSlotKindTest",
                [
                    "at rest the slot promises a destination",
                    "an unselected row in selection mode draws nothing and keeps its slot",
                    "a selected row draws the check",
                    "selected outranks selecting so the mark never blanks",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.all_exercises.AllExercisesFeatureSceneIosTest",
                "resourcesPagingBranchesSelectionAndActionsRenderAndDispatch",
            ),
        ],
    },
    {
        # Phase 7.11: the six portable suites (4/11/6/5/1/13) plus the one production
        # scene, 41 exact.
        "module": "feature:past-session",
        "results_dir": "feature/past-session/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractorImplTest",
                [
                    "observeDetailWithPrs emits null when session is missing",
                    "observeDetailWithPrs combines fresh detail with PR set uuids",
                    "updateSet maps domain SetDomain to data SetsDataModel and forwards",
                    "deleteSession forwards to SessionRepository deleteSession",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.past_session.mvi.handler.ClickHandlerTest",
                [
                    "OnDeleteClick opens the confirmation and closes the sheet it came from",
                    "OnSessionMenuClick opens the overflow sheet with a haptic",
                    "OnSheetDismiss hides the sheet",
                    "OnPrTagClick opens the explainer and OnPrExplainerDismiss hides it",
                    "OnDeleteClick while the explainer is shown replaces the dialog",
                    "OnDeleteConfirm deletes the session closes dialog and navigates back with snackbar",
                    "OnRetryLoad consumes Init",
                    "OnSetTypeChange updates the row and persists the changed type",
                    "OnExerciseHeaderClick expands exactly the tapped card and touches nothing else",
                    "OnExerciseHeaderClick collapses the tapped card and only that card",
                    "OnExerciseHeaderClick with an unknown uuid changes nothing",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.past_session.mvi.handler.CommonHandlerTest",
                [
                    "Init success loads mapped detail",
                    "Init with missing session switches to SessionNotFound phase",
                    "Init failure switches to LoadFailed phase",
                    "Init flags PR set with isPersonalRecord",
                    "Init seeds the first card open on the first Loaded emission",
                    "a re-emission carries the user's open set instead of reseeding",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.past_session.mvi.handler.InputHandlerTest",
                [
                    "OnSetWeightChange marks invalid weight and skips persist",
                    "OnSetRepsChange clears reps error when value becomes valid",
                    "rapid valid inputs debounce to a single persist with the latest value",
                    "invalid follow-up input cancels in-flight persist",
                    "persist failure reverts to the last saved set and emits SaveFailedSnackbar",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.past_session.mvi.handler.NavigationHandlerTest",
                [
                    "Back triggers popBack",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.past_session.mvi.mapper.PastSessionUiMapperTest",
                [
                    "mapper covers adhoc header skipped rows weighted volume and no-set exercise",
                    "mapper marks the PR-bearing set with isPersonalRecord",
                    "mapper leaves all sets unflagged when prSetUuids is empty",
                    "mapper sums tonnage over weighted sets",
                    "mapper excludes a weightless exercise carrying a residual weight from tonnage",
                    "mapper omits the tonnage term when a weighted session logged no weights",
                    "mapper builds the collapsed summary per exercise type",
                    "mapper excludes unfilled sets from the collapsed summary",
                    "mapper marks a weighted set with no logged weight instead of printing bare reps",
                    "first Loaded state seeds exactly the first card open",
                    "first Loaded state with no exercises seeds nothing",
                    "Loaded to Loaded carries the previous open set pruned to live exercises",
                    "a non-Loaded next phase is returned untouched",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.past_session.PastSessionFeatureSceneIosTest",
                "resourcesTotalsPhasesSheetDialogsAndActionsRenderAndDispatch",
            ),
        ],
    },
    {
        # Phase 7.12: the twelve portable suites (6/9/2/16/2/5/14/3/1/5/3/4), the iOS mode-label
        # identity and the one production scene, 72 exact.
        "module": "feature:home",
        "results_dir": "feature/home/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.home.domain.HomeInteractorImplTest",
                [
                    "observeActiveSession maps repository ActiveSessionWithStats to domain",
                    "observeStartCardReadout delegates to the use case with mode and now",
                    "observeStartCardMode maps the stored string to the domain mode",
                    "observeStartCardMode falls back to WEEK for an unknown stored value",
                    "setStartCardMode forwards the persistence encoding to the data store",
                    "the storage encoding and the WEEK default are pinned",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.domain.usecase.ObserveStartCardReadoutUseCaseTest",
                [
                    "WEEK with history wraps the week readout",
                    "WEEK with no session ever is the mode's own empty state even at count zero",
                    "DAYS_SINCE_LAST measures calendar days and anchors on the last session",
                    "DAYS_SINCE_LAST with no session ever is NoSessions",
                    "LAGGING_GROUPS maps idle stats to calendar days in repository order",
                    "LAGGING_GROUPS with no tagged history is NoTaggedHistory",
                    "FORGOTTEN_TRAINING carries days idle and composition",
                    "FORGOTTEN_TRAINING never run keeps daysIdle null — HD1's most forgotten thing",
                    "FORGOTTEN_TRAINING with no templates is NoTemplates",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.domain.usecase.ObserveWeekReadoutUseCaseTest",
                [
                    "queries the repository with the Monday-first week window around now",
                    "counts every session and collapses same-day sessions into one trained day",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.mvi.handler.ClickHandlerTest",
                [
                    "OnSettingsClick consumes OpenSettings navigation and emits HapticClick",
                    "OnActiveSessionClick is a no-op when no active session",
                    "OnActiveSessionClick consumes OpenLiveWorkoutResume with the session uuid",
                    "OnRecentSessionClick consumes OpenPastSession with the session uuid",
                    "OnPickerSeeAllClick hides picker and consumes OpenAllTrainings",
                    "OnModeLabelClick opens the mode sheet with a haptic",
                    "OnModeLabelClick while the training picker is up replaces the sheet",
                    "OnModeSelected hides the sheet and persists the MAPPED mode through the interactor",
                    "OnModeSheetDismiss hides the sheet without a haptic",
                    "OnPickerTrainingSelected hides the picker before resolving conflict",
                    "OnPickerTrainingSelected reports the persisted active session progress",
                    "OnStartActionClick under a Forgotten body starts THAT training no picker",
                    "OnStartActionClick under an Empty body opens the picker instead",
                    "OnStartActionClick before the body arrives opens the picker",
                    "OnConflictDismiss clears pendingConflict",
                    "OnConflictResume consumes OpenLiveWorkoutResume with the active session uuid",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.mvi.handler.CommonHandlerTest",
                [
                    "Init subscribes to the active session and NOT to the paged recent list",
                    "Init builds the start card pipeline from the persisted mode",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.mvi.handler.NavigationHandlerTest",
                [
                    "OpenLiveWorkoutResume navigates to LiveWorkout with the active session uuid",
                    "OpenLiveWorkoutFresh navigates to LiveWorkout with the training uuid",
                    "OpenPastSession navigates to PastSession with the session uuid",
                    "OpenSettings navigates to Settings",
                    "OpenAllTrainings navigates to AllTrainings bottom-bar destination",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapperTest",
                [
                    "recent mapper uses adhoc label and pluralized stats",
                    "recent mapper preserves template name and mixed count labels",
                    "picker mapper handles recently used and never used templates",
                    "week mapper renders count as digits resolves the unit plural fills trained days",
                    "week mapper with no sessions renders zero over seven unfilled pills",
                    "week mapper uses the singular unit for one session",
                    "days-since readout renders digits unit and the name-date anchor",
                    "days-since readout for an adhoc session anchors on the adhoc label",
                    "tag-idle readout scales bars against the longest idle and keeps bare counts",
                    "tag-idle readout with all groups trained today draws empty bars not NaN",
                    "forgotten readout joins days idle and composition",
                    "forgotten readout for a never-run template says so instead of a day count",
                    "each empty readout carries its own mode's copy",
                    "week readout wrapped in the sealed type maps through the week mapper",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.mvi.mapper.StartCardModeMapperTest",
                [
                    "every UI mode maps to its own domain twin",
                    "every domain mode maps to its own UI twin",
                    "the mapping round-trips over the whole catalog",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.mvi.store.HomeStartCardSeedTest",
                [
                    "init seeds no mode and no body",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.ui.components.HomeListSurfaceTest",
                [
                    "rowsWin",
                    "coldOpenIsLoading",
                    "coldOpenErrorIsItsOwnVerdict",
                    "settledAndEmpty",
                    "theEnumIsNarrowedNotCopied",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.ui.components.HomePagingTailKindTest",
                [
                    "appending",
                    "failedAppend",
                    "exhausted",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.home.ui.components.RecentMetaLineTest",
                [
                    "order",
                    "relativeLabelIsFirst",
                    "statsAreLast",
                    "emptyTokensAreDropped",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.home.ui.components.HomeStartCardModeLabelTest",
                "theHeadNamesNoModeUntilOneIsKnown",
            ),
            native(
                "io.github.stslex.workeeper.feature.home.HomeFeatureSceneIosTest",
                "resourcesReadoutsListPickerAndActionsRenderAndDispatch",
            ),
        ],
    },
    {
        "module": "feature:exercise-chart",
        "results_dir": "feature/exercise-chart/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.exercise_chart.domain.ChartFolderTest",
                [
                    "empty history returns empty points and null footer",
                    "single set produces single point with min equal max equal last",
                    "multiple sessions are sorted by completion time and use each session maximum",
                    "two same-day sessions remain separate for every metric",
                    "two different-day sessions remain chronological for every metric",
                    "setCount counts sets the chart cannot plot",
                    "setCount keeps the readout accurate when only one set is plottable",
                    "equal same-day values keep both sessions in completion order",
                    "weightless exercise plots reps regardless of metric",
                    "preset window filters out sets older than start",
                    "MONTH_1 filter tracks calendar days across a DST spring-forward",
                    "MONTH_1 filter tracks calendar days across a DST fall-back",
                    "ALL preset includes very old sets",
                    "volume metric multiplies weight by reps",
                    "volume tie across sessions preserves both in completion order",
                    "volume tie inside one session keeps the earlier position",
                    "session volume sums every eligible set of the session",
                    "session volume excludes ineligible sets from the sum but not from setCount",
                    "same-day session volume retains each session total",
                    "session total tie preserves both sessions in completion order",
                    "weightless session volume sums reps",
                    "session volume footer tracks session totals across days",
                    "single point case still produces footer with min equal max",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.ClickHandlerTest",
                [
                    "OnPresetSelect to current preset is no-op",
                    "OnPresetSelect changes state KEEPS emptyReason and delegates load",
                    "OnMetricSelect changes metric KEEPS emptyReason and delegates load",
                    "OnPickerOpen sets isPickerOpen true",
                    "OnPickerQueryChange stores the query and the window resets it both ways",
                    "OnPickerDismiss sets isPickerOpen false",
                    "OnPickerItemSelect to same exercise just dismisses picker",
                    "OnPickerItemSelect to different exercise clears emptyReason and triggers load",
                    "OnScrub to a new index moves the readout and ticks the haptic",
                    "OnScrub can read both sessions completed on the same day",
                    "OnScrub to the current index is a no-op — the haptic is a tick per CROSSED point",
                    "OnScrub outside the point range is ignored",
                    "OnEmptyCtaClick consumes OpenHome navigation",
                    "OnBack consumes PopBack navigation",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.CommonHandlerTest",
                [
                    "Init with null and no recents emits NO_FINISHED_SESSIONS and skips loadChart",
                    "Init with null and last-trained available loads its chart",
                    "Init with explicit uuid not in recents emits EXERCISE_NOT_FOUND and skips loadChart",
                    "Init with explicit uuid and empty recents prefers NO_FINISHED_SESSIONS",
                    "Init with explicit valid uuid wins over getLastTrainedExerciseUuid",
                    "a response whose metric no longer matches the live state is dropped",
                    "a response that still matches the live state is applied",
                    "loadChart with empty result sets NO_DATA_FOR_EXERCISE",
                    "loadChart with a single point is sub-threshold — empty state no readout",
                    "loadChart with two same-day sessions plots and scrubs the later session",
                    "default state preset is ALL",
                    "a throwing recents read resolves to LOAD_FAILED instead of loading forever",
                    "a throwing chart read resolves to LOAD_FAILED",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.NavigationHandlerTest",
                [
                    "PopBack triggers popBack",
                    "OpenHome navigates to BottomBar Home",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapperTest",
                [
                    "domain point keeps session identity in the UI model",
                    "readout for the record point carries the flag and the grouped whole-number value",
                    "readout off the record point has two caption parts and no flag",
                    "volume record indices keep the earliest point on a value tie",
                    "weight record index follows the PR reps tiebreak across sessions",
                    "weight record index keeps the earliest session when weight and reps tie",
                    "readout is null without an active index",
                    "weighted footer values are the readout's grouped form with one shared unit",
                    "weightless footer is a reps plural over the value with no separable unit",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ChartContentResolutionTest",
                [
                    "one-point exercise never resolves to Plot across metric and preset switches",
                    "zero-point exercise never resolves to Plot across metric and preset switches",
                    "two-point exercise does resolve to Plot",
                    "switching a resolved empty state never passes through Loading",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise_chart.ui.components.ChartPointsAnimationTest",
                [
                    "same-day sessions keep distinct animation targets and scrub positions",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise_chart.ui.components.MetricTabsMotionTest",
                [
                    "indicatorUsesTravel",
                    "durationIsUnchanged",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.exercise_chart.ExerciseChartFeatureSceneIosTest",
                "resourcesReadoutFooterStatesPickerAndActionsRenderAndDispatch",
            ),
        ],
    },
    {
        "module": "feature:single-training",
        "results_dir": "feature/single-training/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.single_training.mvi.handler.ClickHandlerTest",
                [
                    "OnSaveClick with blank name flips nameError — reachable",
                    "OnSaveClick with empty exercises emits ShowSaveError — reachable",
                    "OnSaveClick with a name and exercises raises neither error",
                    "OnEditClick flips into Edit mode",
                    "OnPickerConfirm opens the first inserted card only",
                    "OnExerciseCardToggle expands the tapped card",
                    "OnExerciseCardToggle on the open card collapses it",
                    "cards expand independently",
                    "OnExercisePlanAction adds a set to the addressed exercise and refreshes its summary",
                    "OnExercisePlanAction removing the last set normalizes to null",
                    "a plan edit flips hasChanges through the snapshot signature",
                    "OnExerciseRemove removes the exercise and reindexes positions",
                    "OnExerciseReorder swaps positions on a non-trivial move",
                    "OnExerciseReorder with from equal to to is a no-op",
                    "OnAddExerciseClick emits haptic",
                    "OnConflictDismiss clears the active session conflict dialog",
                    "OnStartSessionClick reports the persisted active session progress",
                    "OnConflictResume consumes OpenLiveWorkout with active session uuid",
                    "OnBackClick closes the dialog before propagating to navigation",
                    "entering the editor after a save collapses every card",
                    "a refreshed exercise name is not an unsaved change",
                    "OnConfirmDiscard restores an exercise the edit removed",
                    "OnConfirmDiscard restores the positions a reorder changed",
                    "OnConfirmDiscard restores the plan a card edited",
                    "remove emits the undo toast and the undo restores item position and expansion",
                    "minus set in a card emits the undo toast and the undo restores the row",
                    "a stale set undo after save flipped to read edits nothing",
                    "a stale set undo does not edit a re-entered draft",
                    "a stale exercise undo after save flipped to read edits nothing",
                    "a stale exercise undo does not edit a re-entered draft",
                    "an undo during the save's write edits nothing",
                    "a failed save re-arms the draft's undos",
                    "a cancel during the save's write raises nothing and reverts nothing",
                    "a confirmed discard during the save's write reverts nothing",
                    "Edit entry resets a stuck isSaving",
                    "createTag resolving to an already-selected tag does not duplicate the chip",
                    "a set undo whose card was already removed waits for the card",
                    "stashed set restores die with the draft",
                    "a picker re-add discards the stash for the returned card",
                    "an exercise undo that finds its card already back discards the stash",
                    "a late picker resolution does not duplicate a card the undo restored",
                    "the dashed add chip opens the tag picker sheet",
                    "dismissing the tag picker clears the query with the sheet",
                    "the topbar menu opens as the DetailMenu sheet",
                    "dismissing the menu hides it",
                    "archive closes the menu before its result lands",
                    "permanent delete replaces the menu with its confirm in one write",
                    "a menu dismissed before the confirm's copy lands stays dismissed",
                    "the save error carries the copy Init resolved",
                    "the undo toasts carry the copy Init resolved",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.single_training.mvi.handler.CommonHandlerTest",
                [
                    "a load that throws clears isLoading or the route is composed on nothing forever",
                    "a training that is not there clears isLoading too",
                    "the История count is the total not the visible page",
                    "a create route never loads so it is never withheld and it opens clean",
                    "Init resolves the editor's constant copy into State",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.single_training.mvi.handler.NavigationHandlerTest",
                [
                    "Back triggers popBack",
                    "OpenExerciseDetail navigates to Screen Exercise",
                    "OpenSession navigates to Screen PastSession",
                    "OpenLiveWorkout navigates to Screen LiveWorkout with both uuids",
                    "OpenLiveWorkout passes a null sessionUuid to LiveWorkout when blank",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStateRouteArgTest",
                [
                    "null uuid yields create-mode initial state with no loading",
                    "non-null uuid yields read-mode initial state and starts loading",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.single_training.SingleTrainingFeatureSceneIosTest",
                "resourcesDetailEditorPickerAndMenuRenderAndDispatch",
            ),
        ],
    },
    {
        "module": "feature:exercise",
        "results_dir": "feature/exercise/build/test-results/iosSimulatorArm64Test",
        "classname_prefix": KNOWN_SUITE_PREFIX,
        "identities": [
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ClickHandlerTest",
                [
                    "OnTypeToggle to WEIGHTLESS with weighted rows raises the confirm instead of switching",
                    "OnTypeToggle to WEIGHTLESS with no weights switches immediately and asks nothing",
                    "OnTypeChangeConfirm commits the switch and clears the weights it warned about",
                    "OnTypeChangeDismiss leaves both the type and the weights alone",
                    "OnSaveClick with blank name sets nameError without saving — reachable",
                    "OnSaveClick with a name does not set nameError and proceeds",
                    "OnEditClick flips mode to Edit and snapshots current state",
                    "OnDetailMenuClick opens the detail-menu sheet",
                    "OnSheetDismiss hides the detail-menu sheet",
                    "OnPlanInfoClick opens the plan-info sheet",
                    "OnEditClick from the sheet closes it in the same transition",
                    "OnPermanentDeleteMenuClick swaps the sheet for the confirm dialog",
                    "OnArchiveMenuClick closes the sheet before the archive result lands",
                    "confirm permanent delete defers - nothing runs until the commit",
                    "minus set emits the undo toast and the undo restores the row",
                    "a stale set undo after save flipped to read edits nothing",
                    "a stale set undo does not edit a re-entered draft",
                    "an undo restored across a type switch re-enters weightless",
                    "an undo during the save's write edits nothing",
                    "a duplicate-name save re-arms the draft's undos",
                    "a cancel during the save's write raises nothing and reverts nothing",
                    "a back during the save's write raises nothing",
                    "a confirmed discard during the save's write pops nothing",
                    "FlipToReadMode during the save's write flips nothing",
                    "Edit entry resets a stuck isSaving",
                    "createTag resolving to an already-selected tag does not duplicate the chip",
                    "a second create landing on a full draft chips nothing past the cap",
                    "a plan value edit emits no undo toast",
                    "the dashed add chip opens the tag picker sheet",
                    "dismissing the tag picker clears the query with the sheet",
                    "OnTagToggle adds tag when not selected",
                    "OnTagToggle blocks adding when 10 tags already selected",
                    "OnDismissArchiveBlocked is no-op",
                    "OnTrackNowClick emits ContextClick haptic",
                    "OnTrackNowClick reports the persisted active session progress",
                    "OnTrackNowResumeConfirm with no pending conflict is a no-op",
                    "OnTrackNowResumeConfirm consumes OpenLiveWorkout with active session uuid",
                    "OnTrackNowConflictDismiss clears the active session conflict dialog",
                    "OnCancelClick from clean Edit on existing flips to Read mode",
                    "OnCancelClick from clean create mode pops back",
                    "OnCancelClick from dirty Edit on existing shows FLIP_TO_READ discard dialog",
                    "OnConfirmDiscard with POP_SCREEN navigates back",
                    "OnConfirmDiscard with FLIP_TO_READ flips mode without popping",
                    "OnConfirmDiscard with FLIP_TO_READ restores the plan the edit changed",
                    "OnConfirmDiscard with FLIP_TO_READ restores a plan that was absent",
                    "OnBackClick in clean Edit on existing flips to Read mode",
                    "OnPermanentDeleteMenuClick is no-op when not eligible",
                    "OnPermanentDeleteMenuClick surfaces the PermanentDeleteConfirm dialog when eligible",
                    "OnEditImageClick opens the image source picker dialog",
                    "OnRemoveImageClick stages a RemoveExisting pending image",
                    "saving an image removal clears the persisted path before deleting the old file",
                    "a rejected image removal keeps the attachment file and the staged draft",
                    "cancelling an image removal restores the attachment without deleting its file",
                    "OnImageSourceDialogDismiss hides the source dialog",
                    "OnPermissionDeniedDialogDismiss hides the permission dialog",
                    "OnCameraPermissionDenied surfaces the permission denied dialog",
                    "OnPermissionDeniedSettingsClick emits NavigateOpenAppSettings",
                    "camera with the permission creates a capture target and launches the camera",
                    "camera without the permission asks for it and creates no capture target",
                    "gallery launches the photo picker and reads no permission",
                    "a granted permission launches the camera on a fresh capture target",
                    "OnImageThumbnailClick with committed path consumes OpenImageViewer with the path",
                    "Edit mode opens the viewer as editable",
                    "Read mode opens the viewer as NOT editable",
                    "OnImageThumbnailClick with no image is a no-op",
                    "OnAdhocPlanEditorAction OnAddSet appends a default set to the in-memory plan",
                    "OnAdhocPlanEditorAction OnSetRemove on the only row normalizes the plan back to null",
                    "OnAdhocPlanEditorAction OnSetWeightChange routes through the reducer",
                    "OnAdhocPlanEditorAction OnSetRepsChange routes through the reducer",
                    "OnAdhocPlanEditorAction OnSetTypeChange routes through the reducer",
                    "OnCancelClick from create-mode with edited plan surfaces POP_SCREEN discard dialog",
                    "OnBackClick from create-mode with edited plan surfaces POP_SCREEN discard dialog",
                    "OnBackClick in create mode with a typed name raises the discard sheet",
                    "a menu dismissed before the confirm's copy lands stays dismissed",
                    "the type-change confirm carries the copy Init resolved",
                    "the set-removed toast carries the copy Init resolved",
                    "both tag-limit toasts carry the copy Init resolved",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.CommonHandlerTest",
                [
                    "Init for create mode does not load exercise",
                    "Init for read mode kicks off observe + load",
                    "a load that throws clears isLoading or the route is composed on nothing forever",
                    "a create route never loads so it is never withheld",
                    "Init in create mode records the starting values as the baseline",
                    "a re-fired Init in create mode keeps the first baseline",
                    "a re-fired Init preserves a dirty draft and its staged image removal",
                    "ImagePicked sets pendingImage to NewFromUri and hides the source dialog",
                    "the load keys the image by its file's modification time",
                    "an exercise without an image reads no modification time",
                    "ImagePickCancelled hides the source dialog without staging a pending image",
                    "a REPLACE request reopens the image source picker",
                    "a REMOVE request stages the removal",
                    "an unrecognised request name is dropped rather than acted on",
                    "every declared request verb resolves to an action",
                    "Init resolves the click paths' constant copy into State",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseDeferredDeleteDbTest",
                [
                    "confirming deletes nothing while the snackbar lives",
                    "back is consumed only after the snackbar is queued with the real undo label",
                    "the window closing commits the delete",
                    "undo — or a process death inside the window — leaves the row",
                    "the commit outlives the screen that scheduled it",
                    "the queued delete carries the message and the undo label Init resolved",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.InputHandlerTest",
                [
                    "OnNameChange updates name and clears nameError",
                    "OnNameChange clears nameDuplicateError",
                    "OnDescriptionChange truncates to 2000 chars",
                    "OnTagSearchChange updates tagSearchQuery",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.NavigationHandlerTest",
                [
                    "Back triggers popBack",
                    "OpenSession navigates to Screen PastSession with the session uuid",
                    "OpenLiveWorkout navigates to Screen LiveWorkout with the resume session uuid",
                    "OpenImageViewer carries the model arg and the caller's own capability",
                    "OpenImageViewer forwards a non-editable caller as non-editable",
                    "OpenChart navigates to Screen ExerciseChart with the exercise uuid",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.PermanentDeleteUndoLabelRuTest",
                [
                    "the queued undo label resolves from the real russian catalog",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractorImplTest",
                [
                    "countSessions forwards to countSessionsUsing",
                    "saveExercise forwards the snapshot uuid to repository on Success",
                    "saveExercise propagates the lastAdhocSets payload",
                    "saveExercise propagates DuplicateName from repository",
                    "archive returns Blocked when active trainings exist",
                    "archive returns Success when no active trainings",
                    "getImageLastModified forwards to the image storage",
                    "canPermanentlyDelete delegates to repository",
                    "permanentlyDelete delegates to repository",
                    "observePersonalRecord forwards to repository and maps to domain",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapperTest",
                [
                    "weighted record splits into trimmed weight and reps with the medium date",
                    "fractional record weight trims trailing zeros",
                    "weightless record carries no weight label",
                    "history entry maps day-month date and the tight summary form",
                    "history summary caps at five sets and appends an ellipsis",
                    "weightless history sets summarize as bare reps",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseDirtyStateTest",
                [
                    "a freshly loaded exercise is clean",
                    "ONLY the snapshot term - a renamed exercise with no image and no plan edit",
                    "ONLY the image term - a staged picture the snapshot cannot see",
                    "ONLY the plan term - create mode where there is no snapshot to compare against",
                    "create mode with no plan and nothing typed is clean",
                    "a plan the baseline has seen reads clean",
                    "an inline plan edit the baseline has not seen reads dirty",
                    "an empty plan and a null plan are the same plan",
                    "a create draft with a typed name reads dirty",
                    "a create draft with a typed description reads dirty",
                    "a create draft with a switched type reads dirty",
                    "a create draft with a selected tag reads dirty",
                    "a create draft whose type is switched back reads clean",
                    "back is intercepted in create mode only once something is typed",
                ],
            ),
            *natives(
                "io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStateRouteArgTest",
                [
                    "null uuid yields create-mode initial state with no loading flag",
                    "non-null uuid yields read-mode initial state and starts loading",
                ],
            ),
            native(
                "io.github.stslex.workeeper.feature.exercise.ExerciseFeatureSceneIosTest",
                "resourcesDetailEditorSheetsAndActionsRenderAndDispatch",
            ),
        ],
    },
]


def main() -> None:
    run_gate("native gate", EXPECTED)


if __name__ == "__main__":
    main()

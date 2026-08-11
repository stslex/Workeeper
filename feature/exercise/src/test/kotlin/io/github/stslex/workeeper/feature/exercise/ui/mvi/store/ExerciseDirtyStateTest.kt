// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import android.net.Uri
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `hasChanges`, asserted directly — because the discard sheet is what reads it and no picture can
 * see a predicate.
 *
 * **Why a file of its own, and why one case per disjunct.** `hasChanges` is a three-way OR:
 *
 * ```
 * originalSnapshot?.matches(this) == false || isImageDirty || isAdhocPlanDirty
 * ```
 *
 * §27's own rule for multi-predicate coverage: **for each term, the fixture must contain a state
 * that ONLY that term makes true** — otherwise the suite measures the disjunction and reports it
 * as coverage of the parts. Three call sites branch on this predicate, so each term owes its own
 * isolating case in both directions.
 *
 * The consumer, named per §27: `ClickHandler.processCancelClick` and `processBackClick`, which
 * open `DialogState.DiscardConfirm` on it — and `State.interceptBack`, which arms the system
 * gesture from it. Not a test that reads the field.
 */
internal class ExerciseDirtyStateTest {

    private val loadedPlan = listOf(
        PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
    ).toImmutableList()

    /** An existing exercise as `applyLoaded` leaves it. */
    private fun loaded(): ExerciseStore.State = ExerciseStore.State
        .create(uuid = "uuid-1")
        .copy(
            mode = ExerciseStore.State.Mode.Edit(isCreate = false),
            isLoading = false,
            name = "Жим лёжа",
            description = "note",
            tags = listOf(AppTagItem(uuid = "t1", name = "грудь")).toImmutableList(),
            adhocPlan = loadedPlan,
            originalSnapshot = ExerciseStore.State.Snapshot(
                name = "Жим лёжа",
                type = ExerciseTypeUiModel.WEIGHTED,
                description = "note",
                tagUuids = listOf("t1"),
                adhocPlan = loadedPlan,
            ),
        )

    @Test
    fun `a freshly loaded exercise is clean`() {
        assertFalse(loaded().hasChanges)
    }

    @Test
    fun `ONLY the snapshot term - a renamed exercise with no image and no plan edit`() {
        val state = loaded().copy(name = "Жим лёжа узким хватом")

        assertTrue(state.hasChanges)
        // The other two terms are false, so this case measures the first one alone.
        assertFalse(state.isImageDirty)
        assertFalse(state.isAdhocPlanDirty)
    }

    @Test
    fun `ONLY the image term - a staged picture the snapshot cannot see`() {
        // `Snapshot` carries name / type / description / tags / plan and NOT the image, which is
        // why the image needs its own term at all. This is the case that proves it does.
        val state = loaded().copy(pendingImage = PendingImage.NewFromUri(mockk<Uri>(relaxed = true)))

        assertTrue(state.hasChanges)
        assertFalse(state.isAdhocPlanDirty)
        assertTrue(state.originalSnapshot?.matches(state) == true)
    }

    @Test
    fun `ONLY the plan term - create mode, where there is no snapshot to compare against`() {
        // `originalSnapshot` is null until the first save, so the first term is false by
        // construction (`null == false`). Without the plan term a create-flow plan edit would be
        // silently discarded by Cancel, which is what that term's KDoc says it exists to stop.
        val state = ExerciseStore.State
            .create(uuid = null)
            .copy(isLoading = false, adhocPlan = loadedPlan)

        assertTrue(state.hasChanges)
        assertTrue(state.isAdhocPlanDirty)
        assertFalse(state.isImageDirty)
    }

    @Test
    fun `create mode with no plan and nothing typed is clean`() {
        val state = ExerciseStore.State.create(uuid = null).copy(isLoading = false)

        assertFalse(state.hasChanges)
    }

    /**
     * **The §25 B39 case: `adhocPlan` has exactly ONE baseline, and it is `originalSnapshot`.**
     *
     * Only a load or a completed Save may write the baseline; the inline plan editor's
     * `OnAdhocPlanEditorAction` writes `adhocPlan` alone. A second baseline field would need
     * every baseline writer to keep both in step, and the failure of not doing so is silent
     * and permanent: the form reads dirty FOREVER after a save, so Cancel raises the discard
     * sheet over work already on disk and back is intercepted for the same reason.
     *
     * That is why `isAdhocPlanDirty` reads the snapshot rather than a companion field — create
     * mode falls out correctly because a null snapshot means "no plan yet". A pairing kept in step
     * by hand is a pairing that comes apart; one source cannot.
     */
    @Test
    fun `a plan the baseline has seen reads clean`() {
        val savedPlan = listOf(
            PlanSetUiModel(weight = 80.0, reps = 5, type = SetTypeUiModel.WORK),
        ).toImmutableList()
        // The shape a baseline writer (load, or a completed Save) leaves behind.
        val state = loaded().copy(
            adhocPlan = savedPlan,
            originalSnapshot = loaded().originalSnapshot?.copy(adhocPlan = savedPlan),
        )

        assertFalse(state.hasChanges)
        assertFalse(state.isAdhocPlanDirty)
    }

    /** The other direction: an inline plan edit touches no baseline and must read dirty. */
    @Test
    fun `an inline plan edit the baseline has not seen reads dirty`() {
        val draftPlan = listOf(
            PlanSetUiModel(weight = 80.0, reps = 5, type = SetTypeUiModel.WORK),
        ).toImmutableList()
        // What `OnAdhocPlanEditorAction` writes: the plan, and deliberately NOT the baseline.
        val state = loaded().copy(adhocPlan = draftPlan)

        assertTrue(state.hasChanges)
        assertTrue(state.isAdhocPlanDirty)
    }

    /**
     * `null` and an empty list both mean "no plan attached", and an in-flight edit that toggles
     * between them must not register as dirty — the normalisation `Snapshot` already documents,
     * asserted on the term that also relies on it.
     */
    @Test
    fun `an empty plan and a null plan are the same plan`() {
        val state = ExerciseStore.State
            .create(uuid = null)
            .copy(isLoading = false, adhocPlan = persistentListOf())

        assertFalse(state.isAdhocPlanDirty)
        assertFalse(state.hasChanges)
    }
}

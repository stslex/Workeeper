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
 * `hasChanges`, asserted directly: it is a three-way OR, and each term gets a case where only
 * that term is true (§27). The consumers are the discard sheet and `State.interceptBack`.
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
        // `Snapshot` does not carry the image, which is why the image needs its own term.
        val state = loaded().copy(pendingImage = PendingImage.NewFromUri(mockk<Uri>(relaxed = true)))

        assertTrue(state.hasChanges)
        assertFalse(state.isAdhocPlanDirty)
        assertTrue(state.originalSnapshot?.matches(state) == true)
    }

    @Test
    fun `ONLY the plan term - create mode, where there is no snapshot to compare against`() {
        // `originalSnapshot` is null until the first save, so the first term is false here.
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
     * B39: `adhocPlan` has exactly ONE baseline, `originalSnapshot` — a second one would need
     * every writer to keep both in step, and the form would read dirty forever after a save.
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

    /** `null` and an empty list both mean "no plan attached", so toggling between them is clean. */
    @Test
    fun `an empty plan and a null plan are the same plan`() {
        val state = ExerciseStore.State
            .create(uuid = null)
            .copy(isLoading = false, adhocPlan = persistentListOf())

        assertFalse(state.isAdhocPlanDirty)
        assertFalse(state.hasChanges)
    }
}

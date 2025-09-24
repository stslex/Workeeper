package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetUiType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ExerciseStoreStateTest {

    @Test
    fun `INITIAL state has correct default values`() {
        val state = ExerciseStore.State.INITIAL

        assertEquals(null, state.uuid)
        assertEquals(PropertyHolder.StringProperty(), state.name)
        assertEquals(persistentListOf(), state.sets)
        assertEquals(PropertyHolder.DateProperty(), state.dateProperty)
        assertEquals(DialogState.Closed, state.dialogState)
        assertEquals(false, state.isMenuOpen)
        assertEquals(persistentSetOf(), state.menuItems)
        assertEquals(null, state.trainingUuid)
        assertEquals(persistentListOf(), state.labels)
        assertEquals(0, state.initialHash)
    }

    @Test
    fun `calculateEqualsHash is stable for empty state`() {
        val state = ExerciseStore.State.INITIAL
        val hash1 = state.calculateEqualsHash
        val hash2 = ExerciseStore.State.INITIAL.calculateEqualsHash
        assertEquals(hash1, hash2)
    }

    @Test
    fun `calculateEqualsHash changes when relevant fields change`() {
        val set1 = SetsUiModel(
            uuid = Uuid.random().toString(),
            reps = PropertyHolder.IntProperty(initialValue = 10),
            weight = PropertyHolder.DoubleProperty(initialValue = 50.5),
            type = SetUiType.WORK,
        )
        val set2 = SetsUiModel(
            uuid = Uuid.random().toString(),
            reps = PropertyHolder.IntProperty(initialValue = 12),
            weight = PropertyHolder.DoubleProperty(initialValue = 45.0),
            type = SetUiType.WARM,
        )

        val base = ExerciseStore.State(
            uuid = "test-uuid",
            name = PropertyHolder.StringProperty(initialValue = "Test Exercise"),
            sets = persistentListOf(set1, set2),
            dateProperty = PropertyHolder.DateProperty(initialValue = 1234567890L),
            dialogState = DialogState.Closed,
            isMenuOpen = false,
            menuItems = persistentSetOf(),
            trainingUuid = null,
            labels = persistentListOf(),
            initialHash = 0,
        )

        val baseHash = base.calculateEqualsHash

        // uuid affects hash
        assertNotEquals(baseHash, base.copy(uuid = "changed-uuid").calculateEqualsHash)

        // name value affects hash
        assertNotEquals(
            baseHash,
            base.copy(name = PropertyHolder.StringProperty(initialValue = "Other")).calculateEqualsHash,
        )

        // date affects hash (by converted string)
        assertNotEquals(
            baseHash,
            base.copy(dateProperty = PropertyHolder.DateProperty(initialValue = 1L)).calculateEqualsHash,
        )

        // sets content affects hash
        val changedReps = set1.copy(reps = PropertyHolder.IntProperty(initialValue = 11))
        assertNotEquals(
            baseHash,
            base.copy(sets = persistentListOf(changedReps, set2)).calculateEqualsHash,
        )

        val changedWeight = set2.copy(weight = PropertyHolder.DoubleProperty(initialValue = 46.0))
        assertNotEquals(
            baseHash,
            base.copy(sets = persistentListOf(set1, changedWeight)).calculateEqualsHash,
        )

        val changedType = set1.copy(type = SetUiType.DROP)
        assertNotEquals(
            baseHash,
            base.copy(sets = persistentListOf(changedType, set2)).calculateEqualsHash,
        )
    }

    @Test
    fun `calculateEqualsHash trims name before calculating`() {
        val state1 = ExerciseStore.State.INITIAL.copy(
            name = PropertyHolder.StringProperty(initialValue = "Test Exercise"),
        )
        val state2 = ExerciseStore.State.INITIAL.copy(
            name = PropertyHolder.StringProperty(initialValue = "  Test Exercise  "),
        )

        val hash1 = state1.calculateEqualsHash
        val hash2 = state2.calculateEqualsHash

        assertEquals(hash1, hash2)
    }

    @Test
    fun `allowBack returns true for new exercise with empty fields`() {
        val state = ExerciseStore.State.INITIAL.copy(
            uuid = null,
            name = PropertyHolder.StringProperty(initialValue = ""),
            sets = persistentListOf(
                SetsUiModel(
                    uuid = Uuid.random().toString(),
                    reps = PropertyHolder.IntProperty(),
                    weight = PropertyHolder.DoubleProperty(),
                    type = SetUiType.WORK,
                ),
            ),
        )

        val allowBack = state.allowBack

        assertTrue(allowBack)
    }

    @Test
    fun `allowBack returns false for new exercise with filled name`() {
        val state = ExerciseStore.State.INITIAL.copy(
            uuid = null,
            name = PropertyHolder.StringProperty(initialValue = "Test Exercise"),
            sets = persistentListOf(),
        )

        val allowBack = state.allowBack

        assertFalse(allowBack)
    }

    @Test
    fun `allowBack returns false for new exercise with filled sets`() {
        val state = ExerciseStore.State.INITIAL.copy(
            uuid = null,
            name = PropertyHolder.StringProperty(initialValue = ""),
            sets = persistentListOf(
                SetsUiModel(
                    uuid = Uuid.random().toString(),
                    reps = PropertyHolder.IntProperty(initialValue = 10),
                    weight = PropertyHolder.DoubleProperty(),
                    type = SetUiType.WORK,
                ),
            ),
        )

        val allowBack = state.allowBack

        assertFalse(allowBack)
    }

    @Test
    fun `allowBack returns true for existing exercise when hash matches initial`() {
        val initialState = ExerciseStore.State.INITIAL.copy(
            uuid = "test-uuid",
            name = PropertyHolder.StringProperty(initialValue = "Test Exercise"),
            sets = persistentListOf(
                SetsUiModel(
                    uuid = Uuid.random().toString(),
                    reps = PropertyHolder.IntProperty(initialValue = 10),
                    weight = PropertyHolder.DoubleProperty(initialValue = 50.0),
                    type = SetUiType.WORK,
                ),
            ),
            initialHash = 12345,
        )

        val currentState = initialState.copy(
            initialHash = initialState.calculateEqualsHash,
        )

        val allowBack = currentState.allowBack

        assertTrue(allowBack)
    }

    @Test
    fun `allowBack returns false for existing exercise when hash does not match`() {
        val state = ExerciseStore.State.INITIAL.copy(
            uuid = "test-uuid",
            name = PropertyHolder.StringProperty(initialValue = "Test Exercise"),
            sets = persistentListOf(),
            initialHash = 99999, // Different from calculated hash
        )

        val allowBack = state.allowBack

        assertFalse(allowBack)
    }

    @Test
    fun `calculateEqualsHash ignores dialog state changes`() {
        val state1 = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Closed,
        )
        val state2 = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Calendar,
        )

        val hash1 = state1.calculateEqualsHash
        val hash2 = state2.calculateEqualsHash

        assertEquals(hash1, hash2)
    }

    @Test
    fun `calculateEqualsHash ignores menu state changes`() {
        val state1 = ExerciseStore.State.INITIAL.copy(
            isMenuOpen = false,
        )
        val state2 = ExerciseStore.State.INITIAL.copy(
            isMenuOpen = true,
        )

        val hash1 = state1.calculateEqualsHash
        val hash2 = state2.calculateEqualsHash

        assertEquals(hash1, hash2)
    }

    @Test
    fun `calculateEqualsHash unchanged when sets order changes`() {
        val set1 = SetsUiModel(
            uuid = "uuid1",
            reps = PropertyHolder.IntProperty(initialValue = 10),
            weight = PropertyHolder.DoubleProperty(initialValue = 50.0),
            type = SetUiType.WORK,
        )
        val set2 = SetsUiModel(
            uuid = "uuid2",
            reps = PropertyHolder.IntProperty(initialValue = 12),
            weight = PropertyHolder.DoubleProperty(initialValue = 45.0),
            type = SetUiType.WARM,
        )

        val state1 = ExerciseStore.State.INITIAL.copy(
            sets = persistentListOf(set1, set2),
        )
        val state2 = ExerciseStore.State.INITIAL.copy(
            sets = persistentListOf(set2, set1),
        )
        val hash1 = state1.calculateEqualsHash
        val hash2 = state2.calculateEqualsHash

        assertEquals(hash1, hash2)
    }

    @Test
    fun `DialogState Sets holds provided set`() {
        val set = SetsUiModel(
            uuid = "set-uuid",
            reps = PropertyHolder.IntProperty(initialValue = 10),
            weight = PropertyHolder.DoubleProperty(initialValue = 50.0),
            type = SetUiType.WORK,
        )

        val state = ExerciseStore.State.INITIAL.copy(
            dialogState = DialogState.Sets(set),
        )

        assertTrue(state.dialogState is DialogState.Sets)
        assertEquals(set, state.dialogState.set)
    }

    @Test
    fun `allowBack with blank spaces in sets values returns true for new exercise`() {
        val state = ExerciseStore.State.INITIAL.copy(
            uuid = null,
            name = PropertyHolder.StringProperty(initialValue = "   "), // Only spaces
            sets = persistentListOf(
                SetsUiModel(
                    uuid = Uuid.random().toString(),
                    reps = PropertyHolder.IntProperty(), // Empty for spaces test
                    weight = PropertyHolder.DoubleProperty(), // Empty for spaces test
                    type = SetUiType.WORK,
                ),
            ),
        )

        val allowBack = state.allowBack

        assertTrue(allowBack)
    }

    @Test
    fun `calculateEqualsHash ignores labels trainingUuid and menuItems`() {
        val base = ExerciseStore.State.INITIAL.copy(
            uuid = "u",
            name = PropertyHolder.StringProperty(initialValue = "N"),
            dateProperty = PropertyHolder.DateProperty(initialValue = 1L),
        )

        val hash = base.calculateEqualsHash

        // Labels do not affect hash
        assertEquals(hash, base.copy(labels = persistentListOf("a", "b")).calculateEqualsHash)

        // trainingUuid does not affect hash
        assertEquals(hash, base.copy(trainingUuid = "t").calculateEqualsHash)

        // menuItems do not affect hash
        assertEquals(hash, base.copy(menuItems = persistentSetOf()).calculateEqualsHash)
    }

    @Test
    fun `allowBack true for existing exercise when only ui state changed`() {
        val initial = ExerciseStore.State.INITIAL.copy(
            uuid = "id",
            name = PropertyHolder.StringProperty(initialValue = "X"),
            dateProperty = PropertyHolder.DateProperty(initialValue = 1L),
        )
        val withHash = initial.copy(initialHash = initial.calculateEqualsHash)

        val mutatedUiOnly = withHash.copy(
            dialogState = DialogState.Calendar,
            isMenuOpen = true,
            menuItems = persistentSetOf(),
            labels = persistentListOf("l1"),
            trainingUuid = "t",
        )

        assertTrue(mutatedUiOnly.allowBack)
    }
}

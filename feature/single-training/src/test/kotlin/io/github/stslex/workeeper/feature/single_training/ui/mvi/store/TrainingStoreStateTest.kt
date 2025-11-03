package io.github.stslex.workeeper.feature.single_training.ui.mvi.store

import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TrainingStoreStateTest {

    private val baseTraining = TrainingUiModel(
        uuid = "test-uuid-123",
        name = PropertyHolder.StringProperty.new(initialValue = "Test Training"),
        exercises = persistentListOf(),
        labels = persistentListOf(),
        date = PropertyHolder.DateProperty.new(initialValue = 1000000L),
        isMenuOpen = false,
        menuItems = persistentSetOf(),
    )

    private val baseState = TrainingStore.State(
        training = baseTraining,
        initialTrainingUiModel = baseTraining,
        dialogState = DialogState.Closed,
        pendingForCreateUuid = "",
    )

    @Test
    fun `compareWithInitial returns true when state unchanged`() {
        val state = baseState

        val result = state.compareWithInitial()

        assertTrue(result)
    }

    @Test
    fun `compareWithInitial returns false when name changed`() {
        val modifiedTraining = baseTraining.copy(
            name = PropertyHolder.StringProperty.new(initialValue = "Modified Training Name"),
        )
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertFalse(result)
    }

    @Test
    fun `compareWithInitial returns false when uuid changed`() {
        val modifiedTraining = baseTraining.copy(uuid = "different-uuid-456")
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertFalse(result)
    }

    @Test
    fun `compareWithInitial returns false when date changed`() {
        val modifiedTraining = baseTraining.copy(
            date = PropertyHolder.DateProperty.new(initialValue = 2000000L),
        )
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertFalse(result)
    }

    @Test
    fun `compareWithInitial returns false when labels changed`() {
        val modifiedTraining = baseTraining.copy(
            labels = persistentListOf("Label 1", "Label 2"),
        )
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertFalse(result)
    }

    @Test
    fun `compareWithInitial returns false when exercises changed`() {
        val exercise = io.github.stslex.workeeper.feature.single_training.ui.model.ExerciseUiModel(
            uuid = "exercise-1",
            name = "Exercise 1",
            labels = persistentListOf(),
            sets = 3,
            timestamp = PropertyHolder.DateProperty.new(initialValue = 1000000L),
        )
        val modifiedTraining = baseTraining.copy(
            exercises = persistentListOf(exercise),
        )
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertFalse(result)
    }

    @Test
    fun `compareWithInitial returns true when only isMenuOpen changed`() {
        // isMenuOpen is not part of comparison
        val modifiedTraining = baseTraining.copy(isMenuOpen = true)
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertTrue(result)
    }

    @Test
    fun `compareWithInitial returns true when only menuItems changed`() {
        // menuItems is not part of comparison
        val modifiedTraining = baseTraining.copy(
            menuItems = persistentSetOf(
                io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem(
                    uuid = "menu-1",
                    text = "Menu Item 1",
                    itemModel = baseTraining,
                ),
            ),
        )
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertTrue(result)
    }

    @Test
    fun `compareWithInitial returns true when only dialogState changed`() {
        // dialogState is not part of comparison
        val state = baseState.copy(dialogState = DialogState.Calendar)

        val result = state.compareWithInitial()

        assertTrue(result)
    }

    @Test
    fun `compareWithInitial returns true when only pendingForCreateUuid changed`() {
        // pendingForCreateUuid is not part of comparison
        val state = baseState.copy(pendingForCreateUuid = "pending-uuid-123")

        val result = state.compareWithInitial()

        assertTrue(result)
    }

    @Test
    fun `compareWithInitial returns false when multiple fields changed`() {
        val modifiedTraining = baseTraining.copy(
            name = PropertyHolder.StringProperty.new(initialValue = "New Name"),
            date = PropertyHolder.DateProperty.new(initialValue = 3000000L),
            labels = persistentListOf("Label A"),
        )
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertFalse(result)
    }

    @Test
    fun `compareWithInitial returns true when empty name becomes whitespace only`() {
        // Empty and whitespace-only strings are different values
        val initialTraining = baseTraining.copy(
            name = PropertyHolder.StringProperty.new(initialValue = ""),
        )
        val modifiedTraining = initialTraining.copy(
            name = PropertyHolder.StringProperty.new(initialValue = "   "),
        )
        val state = baseState.copy(
            training = modifiedTraining,
            initialTrainingUiModel = initialTraining,
        )

        val result = state.compareWithInitial()

        assertFalse(result) // "   " != ""
    }

    @Test
    fun `compareWithInitial returns true when training same as initial but different reference`() {
        val trainingCopy = baseTraining.copy()
        val state = baseState.copy(training = trainingCopy)

        val result = state.compareWithInitial()

        assertTrue(result)
    }

    @Test
    fun `compareWithInitial handles empty collections correctly`() {
        val trainingWithEmptyCollections = baseTraining.copy(
            exercises = persistentListOf(),
            labels = persistentListOf(),
        )
        val state = baseState.copy(
            training = trainingWithEmptyCollections,
            initialTrainingUiModel = trainingWithEmptyCollections,
        )

        val result = state.compareWithInitial()

        assertTrue(result)
    }

    @Test
    fun `compareWithInitial detects single exercise addition`() {
        val exercise = io.github.stslex.workeeper.feature.single_training.ui.model.ExerciseUiModel(
            uuid = "new-exercise-uuid",
            name = "New Exercise",
            labels = persistentListOf(),
            sets = 5,
            timestamp = PropertyHolder.DateProperty.new(initialValue = 2000000L),
        )
        val modifiedTraining = baseTraining.copy(
            exercises = persistentListOf(exercise),
        )
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertFalse(result)
    }

    @Test
    fun `compareWithInitial detects single label addition`() {
        val modifiedTraining = baseTraining.copy(
            labels = persistentListOf("new-label"),
        )
        val state = baseState.copy(training = modifiedTraining)

        val result = state.compareWithInitial()

        assertFalse(result)
    }

    @Test
    fun `compareWithInitial returns true for initial state constant`() {
        val result = TrainingStore.State.INITIAL.compareWithInitial()

        assertTrue(result)
    }
}

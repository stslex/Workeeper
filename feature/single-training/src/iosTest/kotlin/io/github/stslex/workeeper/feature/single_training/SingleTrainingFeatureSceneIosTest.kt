// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.single_training

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.PickerExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingEditCopy
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.resources.Res
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_archive
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_archive_blocked
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_archive_success_format
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_conflict_progress_format
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_conflict_unnamed
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_edit
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_exercises
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_more
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_past_sessions
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete_body
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete_confirm
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete_impact
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_permanent_delete_title
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_resume_session
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_session_count
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_detail_start_session
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_add_exercise
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_close
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_drag_handle
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_error_name_required
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_error_no_exercises
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_exercise_removed
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_label_description
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_label_exercises_format
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_label_name
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_label_tags
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_no_plan
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_placeholder_description
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_plan_set_removed
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_remove_exercise
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_title_create
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_edit_title_edit
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_picker_add_format
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_picker_cancel
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_picker_empty
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_picker_search_placeholder
import io.github.stslex.workeeper.feature.single_training.resources.feature_training_picker_title
import io.github.stslex.workeeper.feature.single_training.ui.TrainingDetailScreen
import io.github.stslex.workeeper.feature.single_training.ui.TrainingEditScreen
import io.github.stslex.workeeper.feature.single_training.ui.components.ExercisePickerSheet
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingDetailMenuSheetContent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SingleTrainingFeatureSceneIosTest {

    /** Every read outside composition goes through this one real resolver: no fake here. */
    private val resolver = ComposeStringResolver()

    @Test
    fun resourcesDetailEditorPickerAndMenuRenderAndDispatch() = runComposeUiTest {
        // 1. The private catalog in file order through the real resolver, each read as stored;
        // the plural is also read filled, its count as the one format argument.
        assertEquals(EXPECTED_STORED, storedCatalog())
        assertEquals(
            listOf("1 session", "2 sessions"),
            runBlocking {
                listOf(
                    resolver.getPluralString(Res.plurals.feature_training_detail_session_count, 1, 1),
                    resolver.getPluralString(Res.plurals.feature_training_detail_session_count, 2, 2),
                )
            },
        )

        // 2. The handlers' copy, with production's arguments.
        assertEquals(
            TrainingEditCopy(
                noExercisesError = "Add at least one exercise",
                exerciseRemoved = "Exercise removed from training",
                setRemoved = "Set removed",
            ),
            runBlocking {
                TrainingEditCopy(
                    noExercisesError = resolver.getString(Res.string.feature_training_edit_error_no_exercises),
                    exerciseRemoved = resolver.getString(Res.string.feature_training_edit_exercise_removed),
                    setRemoved = resolver.getString(Res.string.feature_training_edit_plan_set_removed),
                )
            },
        )
        assertEquals(
            listOf("Delete ‘Push Day’ permanently?", "‘Push Day’ archived", "1 of 2 exercises done"),
            runBlocking {
                listOf(
                    resolver.getString(Res.string.feature_training_detail_permanent_delete_title, "Push Day"),
                    resolver.getString(Res.string.feature_training_detail_archive_success_format, "Push Day"),
                    resolver.getString(Res.string.feature_training_detail_conflict_progress_format, 1, 2),
                )
            },
        )

        // 3–6. One content slot switches between the production screens, the picker and the menu.
        val shown = mutableStateOf<Shown>(Shown.Detail(d1))
        val actions = mutableListOf<Action>()
        val pickerCalls = mutableListOf<String>()
        setContent {
            AppTheme {
                when (val current = shown.value) {
                    is Shown.Detail -> TrainingDetailScreen(state = current.state, consume = actions::add)
                    is Shown.Edit -> TrainingEditScreen(state = current.state, consume = actions::add)
                    is Shown.Picker -> ExercisePickerSheet(
                        query = current.query,
                        results = current.results,
                        selectedUuids = current.selected,
                        onSearchChange = { pickerCalls.add("search:$it") },
                        onToggle = { pickerCalls.add("toggle:$it") },
                        onConfirm = { pickerCalls.add("confirm") },
                        onDismiss = { pickerCalls.add("dismiss") },
                    )

                    is Shown.Menu -> TrainingDetailMenuSheetContent(
                        canPermanentlyDelete = current.canPermanentlyDelete,
                        consume = actions::add,
                    )
                }
            }
        }

        // 3. The read screen: D1.
        settleUntilTag("TrainingDetailScreen")
        onNode(within("TrainingDetailTopBar", "Push Day"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("TrainingDetailBackButton").assertContentDescriptionEquals("Back")
        onNodeWithTag("TrainingDetailMenuButton").assertContentDescriptionEquals("More")
        onNodeWithTag("TrainingDetailTagMeta", useUnmergedTree = true).assertTextEquals("Push · Chest")
        onNode(hasText("EXERCISES"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("2"), useUnmergedTree = true).assertIsDisplayed()
        listOf("1.", "Bench press", "60×10").forEach { text ->
            onNode(within("TrainingExerciseReadCard_ex-1", text), useUnmergedTree = true).assertIsDisplayed()
        }
        listOf("2.", "Pull-up", "no plan yet").forEach { text ->
            onNode(within("TrainingExerciseReadCard_ex-2", text), useUnmergedTree = true).assertIsDisplayed()
        }
        onNode(hasText("DESCRIPTION"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("TrainingDescriptionText", useUnmergedTree = true).assertTextEquals("Heavy week.")
        onNode(hasText("PAST SESSIONS"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNode(hasText("2 SESSIONS"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("TrainingHistoryRow_s-1", "Jul 27, 2026"), useUnmergedTree = true).assertExists()
        onNode(within("TrainingHistoryRow_s-2", "Jul 20, 2026"), useUnmergedTree = true).assertExists()
        onNodeWithTag("TrainingDetailEditButton").assertTextEquals("Edit")
        onNodeWithTag("TrainingStartSessionButton").assertTextEquals("Start session").assertIsEnabled()
        onNodeWithTag("TrainingDetailBackButton").performClick()
        onNodeWithTag("TrainingDetailMenuButton").performClick()
        onNodeWithTag("TrainingExerciseReadCard_ex-1").performScrollTo().performClick()
        onNodeWithTag("TrainingHistoryRow_s-2").performScrollTo().performClick()
        onNodeWithTag("TrainingDetailEditButton").performClick()
        onNodeWithTag("TrainingStartSessionButton").performClick()

        // D2: the session the global state holds belongs to this training, so the dock resumes.
        shown.value = Shown.Detail(
            d1.copy(activeSession = ActiveSessionDomain(sessionUuid = "a-1", trainingUuid = "t-1", startedAt = 0L)),
        )
        settleUntil(hasText("Resume session"))
        onNodeWithTag("TrainingStartSessionButton").assertTextEquals("Resume session")

        // D3: no tag, no exercise, no description, one session.
        shown.value = Shown.Detail(
            d1.copy(
                tags = persistentListOf(),
                exercises = persistentListOf(),
                description = "",
                pastSessions = persistentListOf(s1),
                historyCount = 1,
            ),
        )
        settleUntil(hasText("1 SESSION"))
        onNodeWithTag("TrainingDetailTagMeta", useUnmergedTree = true).assertDoesNotExist()
        onNode(hasText("EXERCISES"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("0"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("DESCRIPTION"), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("TrainingDescriptionText", useUnmergedTree = true).assertDoesNotExist()
        onNode(hasText("PAST SESSIONS"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("1 SESSION"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("TrainingStartSessionButton").assertIsNotEnabled()

        // 4. The editor: E1.
        shown.value = Shown.Edit(e1)
        settleUntilTag("TrainingEditScreen")
        onNode(hasText("Push Day") and !hasTestTag("TrainingEditNameField"), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithTag("TrainingEditCloseButton").assertContentDescriptionEquals("Close")
        onNode(hasText("Name"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("TrainingEditNameField").assert(hasText("Push Day"))
        onNode(hasText("Exercises (2)"), useUnmergedTree = true).assertIsDisplayed()
        listOf("1.", "Bench press").forEach { text ->
            onNode(within("TrainingExerciseCard_ex-1", text), useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithTag("TrainingExerciseCardPlanLine_ex-1", useUnmergedTree = true).assertTextEquals("60×10")
        onNodeWithTag("TrainingExerciseCardRemove_ex-1").assertContentDescriptionEquals("Remove exercise")
        onNode(
            hasContentDescription("Reorder") and hasAnyAncestor(hasTestTag("TrainingExerciseCardDrag_ex-1")),
            useUnmergedTree = true,
        ).assertExists()
        onNodeWithTag("TrainingExerciseCardPlanLine_ex-2", useUnmergedTree = true).assertTextEquals("no plan yet")
        onNodeWithTag("TrainingEditAddExerciseButton").performScrollTo().assertTextEquals("Add exercise")
        onNode(hasText("TAGS"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNode(within("AppTagFormRowSelected_tag-1", "Push"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("AppTagFormRowAdd", "+ tag"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Description"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNode(hasText("Optional notes — focus, week, etc."), useUnmergedTree = true).assertExists()
        onNodeWithTag("TrainingEditCancelButton").assertTextEquals("Cancel")
        onNodeWithTag("TrainingEditSaveButton").assertTextEquals("Save")
        onNodeWithTag("PlanEditorBodyEmpty", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("TrainingEditCloseButton").performClick()
        // 7.11's S5: SetText goes through the semantics action. The fields are controlled, so the
        // test-held State takes each typed value, as the Store would; otherwise a field reports
        // its old text again when focus leaves it.
        onNodeWithTag("TrainingEditNameField").performScrollTo().performTextReplacement("Leg Day")
        assertEquals(Action.Input.OnNameChange("Leg Day"), actions.last())
        shown.value = Shown.Edit(e1.copy(name = "Leg Day"))
        settleScene()
        onNodeWithTag("TrainingExerciseCardHead_ex-1").performScrollTo().performClick()
        onNodeWithTag("TrainingExerciseCardRemove_ex-2").performScrollTo().performClick()
        onNodeWithTag("TrainingEditAddExerciseButton").performScrollTo().performClick()
        onNodeWithTag("AppTagFormRowAdd").performScrollTo().performClick()
        onNode(
            hasContentDescription("Remove tag") and hasAnyAncestor(hasTestTag("AppTagFormRowSelected_tag-1")),
            useUnmergedTree = true,
        ).performClick()
        onNodeWithTag("TrainingEditDescriptionField").performScrollTo().performTextReplacement("Light week.")
        assertEquals(Action.Input.OnDescriptionChange("Light week."), actions.last())
        shown.value = Shown.Edit(e1.copy(name = "Leg Day", description = "Light week."))
        settleScene()
        onNodeWithTag("TrainingEditCancelButton").performClick()
        onNodeWithTag("TrainingEditSaveButton").performClick()

        // E2: the second card open shows its empty plan body.
        shown.value = Shown.Edit(e1.copy(expandedExerciseUuids = persistentSetOf("ex-2")))
        settleUntilTag("PlanEditorBodyEmpty")
        onNodeWithTag("PlanEditorBodyEmpty", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        // E3: a create route with the name error raised.
        shown.value = Shown.Edit(State.create(uuid = null).copy(nameError = true))
        settleUntil(hasText("New training"))
        onNode(hasText("New training"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Name is required"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Exercises (0)"), useUnmergedTree = true).assertIsDisplayed()

        // E4: an edit whose name is blank falls back to the edit title.
        shown.value = Shown.Edit(e1.copy(name = ""))
        settleUntil(hasText("Edit training"))
        onNode(hasText("Edit training"), useUnmergedTree = true).assertIsDisplayed()

        // 5. The picker: P1, then P2 with nothing to show.
        shown.value = Shown.Picker(
            query = "",
            results = persistentListOf(benchRow, pullUpRow),
            selected = persistentListOf("ex-1"),
        )
        settleUntilTag("ExercisePickerSheet")
        onNode(hasText("Add exercises"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExercisePickerSearch", "Search exercises…"), useUnmergedTree = true).assertExists()
        listOf("Bench press", "Push · Chest").forEach { text ->
            onNode(within("ExercisePickerRow_ex-1", text), useUnmergedTree = true).assertIsDisplayed()
        }
        onNode(within("ExercisePickerRow_ex-2", "Pull-up"), useUnmergedTree = true).assertIsDisplayed()
        onAllNodes(hasAnyAncestor(hasTestTag("ExercisePickerRow_ex-2")) and hasText("·", substring = true), true)
            .fetchSemanticsNodes().let { assertEquals(0, it.size, "the tagless row draws no tag line") }
        onNodeWithTag("ExercisePickerCancel").assertTextEquals("Cancel")
        onNodeWithTag("ExercisePickerConfirm").assertTextEquals("Add (1)").assertIsEnabled()
        onNodeWithTag("ExercisePickerSearch").performTextReplacement("ben")
        assertEquals("search:ben", pickerCalls.last())
        shown.value = Shown.Picker(
            query = "ben",
            results = persistentListOf(benchRow, pullUpRow),
            selected = persistentListOf("ex-1"),
        )
        settleScene()
        onNodeWithTag("ExercisePickerRow_ex-2").performClick()
        onNodeWithTag("ExercisePickerConfirm").performClick()
        onNodeWithTag("ExercisePickerCancel").performClick()

        shown.value = Shown.Picker(query = "", results = persistentListOf(), selected = persistentListOf())
        settleUntilTag("ExercisePickerEmpty")
        onNodeWithTag("ExercisePickerEmpty", useUnmergedTree = true).assertTextEquals("No exercises match")
        onNodeWithTag("ExercisePickerRow_ex-1").assertDoesNotExist()
        onNodeWithTag("ExercisePickerRow_ex-2").assertDoesNotExist()
        onNodeWithTag("ExercisePickerConfirm").assertTextEquals("Add (0)").assertIsNotEnabled()

        // 6. The menu content, with and without the permanent delete.
        shown.value = Shown.Menu(canPermanentlyDelete = true)
        settleUntilTag("TrainingDetailArchiveMenuItem")
        onNode(within("TrainingDetailArchiveMenuItem", "Archive"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("TrainingDetailPermanentDeleteMenuItem", "Delete permanently"), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithTag("TrainingDetailArchiveMenuItem").performClick()
        onNodeWithTag("TrainingDetailPermanentDeleteMenuItem").performClick()

        shown.value = Shown.Menu(canPermanentlyDelete = false)
        settleUntil(hasText("Archive"))
        onNodeWithTag("TrainingDetailArchiveMenuItem").assertIsDisplayed()
        onNodeWithTag("TrainingDetailPermanentDeleteMenuItem").assertDoesNotExist()

        // 7. Dispatch: production's actions and the picker's callbacks, each as one exact list.
        assertEquals(
            listOf(
                Action.Click.OnBackClick,
                Action.Click.OnDetailMenuClick,
                Action.Click.OnExerciseRowClick("ex-1"),
                Action.Click.OnPastSessionClick("s-2"),
                Action.Click.OnEditClick,
                Action.Click.OnStartSessionClick,
                Action.Click.OnCancelClick,
                Action.Input.OnNameChange("Leg Day"),
                Action.Click.OnExerciseCardToggle("ex-1"),
                Action.Click.OnExerciseRemove("ex-2"),
                Action.Click.OnAddExerciseClick,
                Action.Click.OnTagAddClick,
                Action.Click.OnTagRemove("tag-1"),
                Action.Input.OnDescriptionChange("Light week."),
                Action.Click.OnCancelClick,
                Action.Click.OnSaveClick,
                Action.Click.OnArchiveClick,
                Action.Click.OnPermanentDeleteClick,
            ),
            actions,
        )
        assertEquals(listOf("search:ben", "toggle:ex-2", "confirm", "dismiss"), pickerCalls)
    }

    /** What the one content slot shows; the test holds the state and production renders it. */
    private sealed interface Shown {

        data class Detail(val state: State) : Shown

        data class Edit(val state: State) : Shown

        data class Picker(
            val query: String,
            val results: ImmutableList<PickerExerciseItem>,
            val selected: ImmutableList<String>,
        ) : Shown

        data class Menu(val canPermanentlyDelete: Boolean) : Shown
    }

    /** [text] inside the production [tag]. */
    private fun within(tag: String, text: String): SemanticsMatcher =
        hasText(text) and hasAnyAncestor(hasTestTag(tag))

    /** The catalog in file order, each read with no format argument (the template verbatim). */
    private fun storedCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(Res.string.feature_training_detail_more),
            resolver.getString(Res.string.feature_training_detail_exercises),
            resolver.getString(Res.string.feature_training_detail_past_sessions),
            resolver.getString(Res.string.feature_training_detail_start_session),
            resolver.getString(Res.string.feature_training_detail_resume_session),
            resolver.getString(Res.string.feature_training_detail_edit),
            resolver.getString(Res.string.feature_training_detail_archive),
            resolver.getString(Res.string.feature_training_detail_archive_blocked),
            resolver.getString(Res.string.feature_training_detail_archive_success_format),
            resolver.getString(Res.string.feature_training_detail_permanent_delete),
            resolver.getString(Res.string.feature_training_detail_permanent_delete_title),
            resolver.getString(Res.string.feature_training_detail_permanent_delete_body),
            resolver.getString(Res.string.feature_training_detail_permanent_delete_impact),
            resolver.getString(Res.string.feature_training_detail_permanent_delete_confirm),
            resolver.getString(Res.string.feature_training_detail_conflict_unnamed),
            resolver.getString(Res.string.feature_training_detail_conflict_progress_format),
            resolver.getPluralString(Res.plurals.feature_training_detail_session_count, 1),
            resolver.getPluralString(Res.plurals.feature_training_detail_session_count, 2),
            resolver.getString(Res.string.feature_training_edit_close),
            resolver.getString(Res.string.feature_training_edit_title_create),
            resolver.getString(Res.string.feature_training_edit_title_edit),
            resolver.getString(Res.string.feature_training_edit_label_name),
            resolver.getString(Res.string.feature_training_edit_label_description),
            resolver.getString(Res.string.feature_training_edit_placeholder_description),
            resolver.getString(Res.string.feature_training_edit_label_tags),
            resolver.getString(Res.string.feature_training_edit_label_exercises_format),
            resolver.getString(Res.string.feature_training_edit_add_exercise),
            resolver.getString(Res.string.feature_training_edit_exercise_removed),
            resolver.getString(Res.string.feature_training_edit_no_plan),
            resolver.getString(Res.string.feature_training_edit_plan_set_removed),
            resolver.getString(Res.string.feature_training_edit_remove_exercise),
            resolver.getString(Res.string.feature_training_edit_drag_handle),
            resolver.getString(Res.string.feature_training_edit_error_name_required),
            resolver.getString(Res.string.feature_training_edit_error_no_exercises),
            resolver.getString(Res.string.feature_training_picker_title),
            resolver.getString(Res.string.feature_training_picker_search_placeholder),
            resolver.getString(Res.string.feature_training_picker_add_format),
            resolver.getString(Res.string.feature_training_picker_cancel),
            resolver.getString(Res.string.feature_training_picker_empty),
        )
    }

    private fun ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    /** Settles in bounded steps until a node matching the branch exists. */
    private fun ComposeUiTest.settleUntil(matcher: SemanticsMatcher) {
        repeat(MAX_SETTLE_STEPS) {
            settleScene()
            if (onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
        }
    }

    private fun ComposeUiTest.settleUntilTag(tag: String) = settleUntil(hasTestTag(tag))

    private companion object {

        /** Ten virtual seconds: far beyond any transition the screens run. */
        const val MAX_SETTLE_STEPS = 10

        val bench = TrainingExerciseItem(
            exerciseUuid = "ex-1",
            exerciseName = "Bench press",
            exerciseType = ExerciseTypeUiModel.WEIGHTED,
            tags = persistentListOf("Push"),
            position = 0,
            planSets = persistentListOf(PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)),
            planSummary = "60×10",
        )

        val pullUp = TrainingExerciseItem(
            exerciseUuid = "ex-2",
            exerciseName = "Pull-up",
            exerciseType = ExerciseTypeUiModel.WEIGHTLESS,
            tags = persistentListOf(),
            position = 1,
            planSets = null,
            planSummary = "",
        )

        val push = AppTagItem(uuid = "tag-1", name = "Push")
        val chest = AppTagItem(uuid = "tag-2", name = "Chest")
        val s1 = HistorySessionItem(sessionUuid = "s-1", dateLabel = "Jul 27, 2026")
        val s2 = HistorySessionItem(sessionUuid = "s-2", dateLabel = "Jul 20, 2026")

        val benchRow = PickerExerciseItem(
            uuid = "ex-1",
            name = "Bench press",
            type = ExerciseTypeUiModel.WEIGHTED,
            tags = persistentListOf("Push", "Chest"),
        )
        val pullUpRow = PickerExerciseItem(
            uuid = "ex-2",
            name = "Pull-up",
            type = ExerciseTypeUiModel.WEIGHTLESS,
            tags = persistentListOf(),
        )

        /** D1: the loaded read screen. */
        val d1: State = State.create(uuid = "t-1").copy(
            isLoading = false,
            name = "Push Day",
            tags = persistentListOf(push, chest),
            exercises = persistentListOf(bench, pullUp),
            description = "Heavy week.",
            pastSessions = persistentListOf(s1, s2),
            historyCount = 2,
        )

        /** E1: D1's training in the editor, with the tag Push only and no description. */
        val e1: State = d1.copy(
            mode = State.Mode.Edit(isCreate = false),
            tags = persistentListOf(push),
            description = "",
        )

        /** SPEC 3.4's EN catalog in file order, as stored; the plural as its one, then its other. */
        val EXPECTED_STORED = listOf(
            "More",
            "Exercises",
            "Past sessions",
            "Start session",
            "Resume session",
            "Edit",
            "Archive",
            "Cannot archive — finish active session first",
            "‘%1\$s’ archived",
            "Delete permanently",
            "Delete ‘%1\$s’ permanently?",
            "This training has no history. This cannot be undone.",
            "No history affected",
            "Delete",
            "Workout",
            "%1\$d of %2\$d exercises done",
            "%1\$d session",
            "%1\$d sessions",
            "Close",
            "New training",
            "Edit training",
            "Name",
            "Description",
            "Optional notes — focus, week, etc.",
            "Tags",
            "Exercises (%1\$d)",
            "Add exercise",
            "Exercise removed from training",
            "no plan yet",
            "Set removed",
            "Remove exercise",
            "Reorder",
            "Name is required",
            "Add at least one exercise",
            "Add exercises",
            "Search exercises…",
            "Add (%1\$d)",
            "Cancel",
            "No exercises match",
        )
    }
}

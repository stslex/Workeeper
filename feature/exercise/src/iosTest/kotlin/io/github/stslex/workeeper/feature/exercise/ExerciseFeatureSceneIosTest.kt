// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.exercise

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_toast_undo
import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_action_more
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_archive
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_archive_blocked_next_step
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_archive_blocked_ok
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_archive_blocked_title
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_archive_blocked_used_in_format
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_archive_success_format
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_archive_undo
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_default_plan
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_edit
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_history_count
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_confirm_body
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_confirm_button
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_confirm_impact
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_confirm_title
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_success
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_recent
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_track_now
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_close_description
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_error_name_duplicate
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_error_name_required
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_label_description
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_label_name
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_label_tags
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_placeholder_description
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_info_body
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_info_description
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_set_removed
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_set_type_tooltip
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_body
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_confirm
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_impact
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_title
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_tag_limit
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_tags_counter
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_title_create
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_title_edit
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_error_decode_failed
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_error_load_failed
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_error_save_failed
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_permission_denied_action_settings
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_permission_denied_body
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_permission_denied_title
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_placeholder_description
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_source_camera
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_source_dialog_title
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_source_gallery
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_thumb_description
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_thumb_open_description
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_thumb_pick_description
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_track_now_conflict_progress_format
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_track_now_conflict_unnamed
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_track_now_default_training_name
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseDetailScreen
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseEditScreen
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseDetailMenuSheetContent
import io.github.stslex.workeeper.feature.exercise.ui.components.ImageSourceSheetContent
import io.github.stslex.workeeper.feature.exercise.ui.components.PlanInfoSheetContent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseClickCopy
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PersonalRecordUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.stslex.workeeper.feature.exercise.resources.Res as ExerciseRes

class ExerciseFeatureSceneIosTest {

    /** Every read outside composition goes through this one real resolver: no fake here. */
    private val resolver = ComposeStringResolver()

    @Test
    fun resourcesDetailEditorSheetsAndActionsRenderAndDispatch() = runComposeUiTest {
        // 1. The private catalog in file order through the real resolver, each read as stored;
        // the plural is also read filled, its count as the one format argument.
        val stored = storedCatalog()
        assertEquals(EXPECTED_STORED, stored)
        val planInfoBody = stored[PLAN_INFO_BODY_INDEX]
        assertTrue("\n\n" in planInfoBody && '\\' !in planInfoBody, "row 32 renders its escape: $planInfoBody")
        assertEquals(
            listOf("1 session", "2 sessions"),
            runBlocking {
                listOf(
                    resolver.getPluralString(ExerciseRes.plurals.feature_exercise_detail_history_count, 1, 1),
                    resolver.getPluralString(ExerciseRes.plurals.feature_exercise_detail_history_count, 2, 2),
                )
            },
        )

        // 2. The handlers' copy, with production's arguments.
        assertEquals(
            ExerciseClickCopy(
                typeChangeTitle = "Switch to weightless?",
                typeChangeBody = "Weight values from this exercise’s plans will be cleared. This cannot be undone.",
                typeChangeImpact = "All plan weights cleared",
                typeChangeConfirm = "Switch",
                setRemoved = "Set removed",
                tagLimit = "Maximum 10 tags per exercise",
                permanentDeleteSuccess = "Exercise deleted",
                undoLabel = "Undo",
            ),
            runBlocking {
                ExerciseClickCopy(
                    typeChangeTitle = resolver.getString(
                        ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_title,
                    ),
                    typeChangeBody = resolver.getString(
                        ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_body,
                    ),
                    typeChangeImpact = resolver.getString(
                        ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_impact,
                    ),
                    typeChangeConfirm = resolver.getString(
                        ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_confirm,
                    ),
                    setRemoved = resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_set_removed),
                    tagLimit = resolver.getString(ExerciseRes.string.feature_exercise_edit_tag_limit),
                    permanentDeleteSuccess = resolver.getString(
                        ExerciseRes.string.feature_exercise_detail_permanent_delete_success,
                    ),
                    undoLabel = resolver.getString(Res.string.core_ui_kit_toast_undo),
                )
            },
        )
        assertEquals(
            listOf(
                "Delete ‘Bench press’ permanently?",
                "‘Bench press’ archived",
                "used in Push Day, Leg Day",
                "1 of 2 exercises done",
            ),
            runBlocking {
                listOf(
                    resolver.getString(
                        ExerciseRes.string.feature_exercise_detail_permanent_delete_confirm_title,
                        "Bench press",
                    ),
                    resolver.getString(
                        ExerciseRes.string.feature_exercise_detail_archive_success_format,
                        "Bench press",
                    ),
                    resolver.getString(
                        ExerciseRes.string.feature_exercise_detail_archive_blocked_used_in_format,
                        "Push Day, Leg Day",
                    ),
                    resolver.getString(ExerciseRes.string.feature_exercise_track_now_conflict_progress_format, 1, 2),
                )
            },
        )

        // 3–6. One content slot switches between the production screens and the three sheets.
        val shown = mutableStateOf<Shown>(Shown.Detail(d1))
        val actions = mutableListOf<Action>()
        val sources = mutableListOf<String>()
        setContent {
            AppTheme {
                when (val current = shown.value) {
                    is Shown.Detail -> ExerciseDetailScreen(state = current.state, consume = actions::add)
                    is Shown.Edit -> ExerciseEditScreen(state = current.state, consume = actions::add)
                    is Shown.Menu -> ExerciseDetailMenuSheetContent(
                        canPermanentlyDelete = current.canPermanentlyDelete,
                        consume = actions::add,
                    )

                    Shown.PlanInfo -> PlanInfoSheetContent(consume = actions::add)
                    Shown.Source -> ImageSourceSheetContent(onSourceSelected = { sources.add("source:${it.name}") })
                }
            }
        }

        // 3. The read screen: D1.
        settleUntilTag("ExerciseDetailScreen")
        onNode(within("ExerciseDetailTopBar", "Bench press"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ExerciseDetailBackButton").assertContentDescriptionEquals("Back")
        onNode(hasContentDescription("More actions")).assertIsDisplayed()
        onNodeWithTag("ExerciseDetailTagMeta", useUnmergedTree = true).assertTextEquals("Push · Chest")
        onNodeWithTag("ExerciseDetailRecordHero").assertIsDisplayed()
        onNode(hasText("DEFAULT PLAN"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNode(hasText("WEIGHTED"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ExerciseDetailDefaultPlanCard").performScrollTo().assertIsDisplayed()
        onNode(hasText("DESCRIPTION"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("ExerciseDescriptionText", useUnmergedTree = true).assertTextEquals("Pause at the bottom.")
        onNodeWithTag("ExerciseDescriptionImage")
            .assertContentDescriptionEquals("No exercise photo attached")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        onNode(hasText("HISTORY"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNode(hasText("2 SESSIONS"), useUnmergedTree = true).assertIsDisplayed()
        listOf("27 Jul", "80×5").forEach { text ->
            onNode(within("ExerciseHistoryRow_s-1", text), useUnmergedTree = true).assertExists()
        }
        onNodeWithTag("ExerciseHistoryRow_s-2").assertExists()
        onNodeWithTag("ExerciseEditButton").assertTextEquals("Edit")
        onNodeWithTag("ExerciseTrackNowButton").assertTextEquals("Log now").assertIsEnabled()
        onNodeWithTag("ExerciseDetailBackButton").performClick()
        onNode(hasContentDescription("More actions")).performClick()
        onNodeWithTag("ExerciseDetailRecordHero").performScrollTo().performClick()
        onNodeWithTag("ExerciseHistoryRow_s-2").performScrollTo().performClick()
        onNodeWithTag("ExerciseEditButton").performClick()
        onNodeWithTag("ExerciseTrackNowButton").performClick()

        // D2: no tag, no plan, no description, no record and no history.
        val d2 = d1.copy(
            tags = persistentListOf(),
            adhocPlan = null,
            description = "",
            personalRecord = null,
            recentHistory = persistentListOf(),
            historyCount = 0,
        )
        shown.value = Shown.Detail(d2)
        settleUntilAbsent("ExerciseDetailTagMeta")
        onNodeWithTag("ExerciseDetailTagMeta", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("ExerciseDetailRecordHero").assertDoesNotExist()
        onNode(hasText("DEFAULT PLAN"), useUnmergedTree = true).assertDoesNotExist()
        onNode(hasText("DESCRIPTION"), useUnmergedTree = true).assertDoesNotExist()
        onNode(hasText("HISTORY"), useUnmergedTree = true).assertDoesNotExist()

        // D3: D2 with a picture, so DESCRIPTION is back and the thumb opens the viewer.
        shown.value = Shown.Detail(d2.copy(imagePath = "native-image-model", imageLastModified = 1))
        settleUntil(hasText("DESCRIPTION"))
        onNode(hasText("DESCRIPTION"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ExerciseDescriptionImage").assert(clickLabel("Open photo full screen"))
        onNodeWithTag("ExerciseDescriptionImage").performClick()

        // 4. The editor: E1.
        shown.value = Shown.Edit(e1)
        settleUntilTag("ExerciseEditScreen")
        onNode(hasText("Bench press") and !hasTestTag("ExerciseEditNameField"), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithTag("ExerciseEditCloseButton").assertContentDescriptionEquals("Close")
        onNode(hasText("Name"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ExerciseEditNameField").assert(hasText("Bench press"))
        onNode(hasText("DEFAULT PLAN"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("ExerciseEditPlanInfoButton").assertContentDescriptionEquals("What the default plan is for")
        onNode(hasText("TAGS"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNode(hasText("1 OF 10"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("AppTagFormRowSelected_tag-1", "Push"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("AppTagFormRowAdd", "+ tag"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("DESCRIPTION"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("ExerciseEditDescriptionField").performScrollTo().assertIsDisplayed()
        onNode(hasText("Optional notes — technique, gym-specific details"), useUnmergedTree = true).assertExists()
        onNodeWithTag("ExerciseDescriptionImage").assert(clickLabel("Add a photo"))
        onNodeWithTag("ExerciseEditCancelButton").assertTextEquals("Cancel")
        onNodeWithTag("ExerciseEditSaveButton").assertTextEquals("Save")
        onNodeWithTag("ExerciseEditCloseButton").performClick()
        // 7.11's S5: SetText goes through the semantics action. The fields are controlled, so the
        // test-held State takes each typed value, as the Store would; otherwise a field reports
        // its old text again when focus leaves it.
        onNodeWithTag("ExerciseEditNameField").performScrollTo().performTextReplacement("Squat")
        assertEquals(Action.Input.OnNameChange("Squat"), actions.last())
        shown.value = Shown.Edit(e1.copy(name = "Squat"))
        settleScene()
        onNodeWithTag("ExerciseEditPlanInfoButton").performScrollTo().performClick()
        onNodeWithTag("AppTagFormRowAdd").performScrollTo().performClick()
        onNode(
            hasContentDescription("Remove tag") and hasAnyAncestor(hasTestTag("AppTagFormRowSelected_tag-1")),
            useUnmergedTree = true,
        ).performClick()
        onNodeWithTag("ExerciseEditDescriptionField").performScrollTo().performTextReplacement("Slow.")
        assertEquals(Action.Input.OnDescriptionChange("Slow."), actions.last())
        shown.value = Shown.Edit(e1.copy(name = "Squat", description = "Slow."))
        settleScene()
        onNodeWithTag("ExerciseDescriptionImage").performScrollTo().performClick()
        onNodeWithTag("ExerciseEditCancelButton").performClick()
        onNodeWithTag("ExerciseEditSaveButton").performClick()

        // E2: a create route with the name error raised.
        shown.value = Shown.Edit(State.create(uuid = null).copy(isLoading = false, nameError = true))
        settleUntil(hasText("New exercise"))
        onNode(hasText("New exercise"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Name is required"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("0 OF 10"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        // E3: an edit whose name is blank falls back to the edit title, the duplicate error raised.
        shown.value = Shown.Edit(e1.copy(name = "", nameDuplicateError = true))
        settleUntil(hasText("Edit exercise"))
        onNode(hasText("Edit exercise"), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Exercise with this name already exists"), useUnmergedTree = true).assertIsDisplayed()

        // 5. The sheets: the menu with and without the permanent delete, the plan info, the source.
        shown.value = Shown.Menu(canPermanentlyDelete = true)
        settleUntilTag("ExerciseDetailMenu_Edit")
        onNode(within("ExerciseDetailMenu_Edit", "Edit"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExerciseDetailMenu_Archive", "Archive"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExerciseDetailMenu_PermanentDelete", "Delete permanently"), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithTag("ExerciseDetailMenu_Edit").performClick()
        onNodeWithTag("ExerciseDetailMenu_Archive").performClick()
        onNodeWithTag("ExerciseDetailMenu_PermanentDelete").performClick()

        shown.value = Shown.Menu(canPermanentlyDelete = false)
        settleUntilAbsent("ExerciseDetailMenu_PermanentDelete")
        onNodeWithTag("ExerciseDetailMenu_Archive").assertIsDisplayed()
        onNodeWithTag("ExerciseDetailMenu_PermanentDelete").assertDoesNotExist()

        shown.value = Shown.PlanInfo
        settleUntilTag("ExercisePlanInfoSheet")
        onNode(within("ExercisePlanInfoSheet", "Default plan"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExercisePlanInfoSheet", planInfoBody), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ExercisePlanInfoSheetClose").assertTextEquals("Close")
        onNodeWithTag("ExercisePlanInfoSheetClose").performClick()

        shown.value = Shown.Source
        settleUntilTag("ExerciseImageSourceSheet")
        onNode(within("ExerciseImageSourceSheet", "Choose source"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExerciseImageSourceCamera", "Take photo"), useUnmergedTree = true).assertIsDisplayed()
        onNode(within("ExerciseImageSourceGallery", "Choose from gallery"), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithTag("ExerciseImageSourceCamera").performClick()
        onNodeWithTag("ExerciseImageSourceGallery").performClick()

        // 6. Dispatch: production's actions and the source callbacks, each as one exact list.
        assertEquals(
            listOf(
                Action.Click.OnBackClick,
                Action.Click.OnDetailMenuClick,
                Action.Click.OnPrCardClick,
                Action.Click.OnHistoryRowClick("s-2"),
                Action.Click.OnEditClick,
                Action.Click.OnTrackNowClick,
                Action.Click.OnImageThumbnailClick,
                Action.Click.OnCancelClick,
                Action.Input.OnNameChange("Squat"),
                Action.Click.OnPlanInfoClick,
                Action.Click.OnTagAddClick,
                Action.Click.OnTagRemove("tag-1"),
                Action.Input.OnDescriptionChange("Slow."),
                Action.Click.OnEditImageClick,
                Action.Click.OnCancelClick,
                Action.Click.OnSaveClick,
                Action.Click.OnEditClick,
                Action.Click.OnArchiveMenuClick,
                Action.Click.OnPermanentDeleteMenuClick,
                Action.Click.OnSheetDismiss,
            ),
            actions,
        )
        assertEquals(listOf("source:Camera", "source:Gallery"), sources)
    }

    /** What the one content slot shows; the test holds the state and production renders it. */
    private sealed interface Shown {

        data class Detail(val state: State) : Shown

        data class Edit(val state: State) : Shown

        data class Menu(val canPermanentlyDelete: Boolean) : Shown

        data object PlanInfo : Shown

        data object Source : Shown
    }

    /** [text] inside the production [tag]. */
    private fun within(tag: String, text: String): SemanticsMatcher =
        hasText(text) and hasAnyAncestor(hasTestTag(tag))

    /** The node's click action carries [label] — the thumb's `onClickLabel`. */
    private fun clickLabel(label: String): SemanticsMatcher =
        SemanticsMatcher("click action labelled '$label'") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }

    /** The catalog in file order, each read with no format argument (the template verbatim). */
    private fun storedCatalog(): List<String> = runBlocking {
        listOf(
            resolver.getString(ExerciseRes.string.feature_exercise_detail_recent),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_track_now),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_default_plan),
            resolver.getString(ExerciseRes.string.feature_exercise_track_now_default_training_name),
            resolver.getString(ExerciseRes.string.feature_exercise_track_now_conflict_unnamed),
            resolver.getString(ExerciseRes.string.feature_exercise_track_now_conflict_progress_format),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_edit),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_action_more),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_archive),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_archive_success_format),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_archive_blocked_title),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_archive_blocked_used_in_format),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_archive_blocked_next_step),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_archive_blocked_ok),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_archive_undo),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_permanent_delete),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_permanent_delete_confirm_title),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_permanent_delete_confirm_body),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_permanent_delete_confirm_impact),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_permanent_delete_confirm_button),
            resolver.getString(ExerciseRes.string.feature_exercise_detail_permanent_delete_success),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_title_create),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_title_edit),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_close_description),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_label_name),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_label_tags),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_label_description),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_placeholder_description),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_tag_limit),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_error_name_required),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_error_name_duplicate),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_info_body),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_info_description),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_set_type_tooltip),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_title),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_body),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_impact),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_confirm),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_plan_set_removed),
            resolver.getString(ExerciseRes.string.feature_exercise_edit_tags_counter),
            resolver.getString(ExerciseRes.string.feature_exercise_image_thumb_description),
            resolver.getString(ExerciseRes.string.feature_exercise_image_thumb_open_description),
            resolver.getString(ExerciseRes.string.feature_exercise_image_thumb_pick_description),
            resolver.getString(ExerciseRes.string.feature_exercise_image_placeholder_description),
            resolver.getString(ExerciseRes.string.feature_exercise_image_source_dialog_title),
            resolver.getString(ExerciseRes.string.feature_exercise_image_source_camera),
            resolver.getString(ExerciseRes.string.feature_exercise_image_source_gallery),
            resolver.getString(ExerciseRes.string.feature_exercise_image_permission_denied_title),
            resolver.getString(ExerciseRes.string.feature_exercise_image_permission_denied_body),
            resolver.getString(ExerciseRes.string.feature_exercise_image_permission_denied_action_settings),
            resolver.getString(ExerciseRes.string.feature_exercise_image_error_save_failed),
            resolver.getString(ExerciseRes.string.feature_exercise_image_error_load_failed),
            resolver.getString(ExerciseRes.string.feature_exercise_image_error_decode_failed),
            resolver.getPluralString(ExerciseRes.plurals.feature_exercise_detail_history_count, 1),
            resolver.getPluralString(ExerciseRes.plurals.feature_exercise_detail_history_count, 2),
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

    /** Settles in bounded steps until no node carries [tag]. */
    private fun ComposeUiTest.settleUntilAbsent(tag: String) {
        repeat(MAX_SETTLE_STEPS) {
            settleScene()
            if (onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                return
            }
        }
    }

    private companion object {

        /** Ten virtual seconds: far beyond any transition the screens run. */
        const val MAX_SETTLE_STEPS = 10

        /** Row 32 of SPEC B3.4, `feature_exercise_edit_plan_info_body`, in the stored list. */
        const val PLAN_INFO_BODY_INDEX = 31

        val push = AppTagItem(uuid = "tag-1", name = "Push")
        val chest = AppTagItem(uuid = "tag-2", name = "Chest")

        /** D1: the loaded read screen. */
        val d1: State = State.create(uuid = "ex-1").copy(
            isLoading = false,
            name = "Bench press",
            tags = persistentListOf(push, chest),
            adhocPlan = persistentListOf(PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK)),
            description = "Pause at the bottom.",
            personalRecord = PersonalRecordUiModel("s-1", "80", "5", "Jul 27, 2026"),
            recentHistory = persistentListOf(
                HistoryUiModel("s-1", "27 Jul", "80×5"),
                HistoryUiModel("s-2", "20 Jul", "75×5"),
            ),
            historyCount = 2,
        )

        /** E1: D1's exercise in the editor, with the tag Push only, no description and no image. */
        val e1: State = d1.copy(
            mode = State.Mode.Edit(isCreate = false),
            tags = persistentListOf(push),
            description = "",
        )

        /** SPEC B3.4's EN catalog in file order, as stored; the plural as its one, then its other. */
        val EXPECTED_STORED = listOf(
            "History",
            "Log now",
            "Default plan",
            "Track now",
            "Workout",
            "%1\$d of %2\$d exercises done",
            "Edit",
            "More actions",
            "Archive",
            "‘%1\$s’ archived",
            "Cannot archive",
            "used in %1\$s",
            "Remove it from those trainings first, then archive.",
            "OK",
            "Undo",
            "Delete permanently",
            "Delete ‘%1\$s’ permanently?",
            "This exercise has no history. This cannot be undone.",
            "No history affected",
            "Delete",
            "Exercise deleted",
            "New exercise",
            "Edit exercise",
            "Close",
            "Name",
            "Tags",
            "Description",
            "Optional notes — technique, gym-specific details",
            "Maximum 10 tags per exercise",
            "Name is required",
            "Exercise with this name already exists",
            "This is the plan for when the exercise is performed on its own — from its card, with the “Log " +
                "now” button, outside any training.\n\nInside a training every exercise has a plan of its own: " +
                "it is set in the training editor and does not touch this one. If neither plan exists, sets " +
                "are added right in the session.",
            "What the default plan is for",
            "Tap to cycle: warmup → work → failure → drop",
            "Switch to weightless?",
            "Weight values from this exercise’s plans will be cleared. This cannot be undone.",
            "All plan weights cleared",
            "Switch",
            "Set removed",
            "%1\$d of %2\$d",
            "Attached exercise photo",
            "Open photo full screen",
            "Add a photo",
            "No exercise photo attached",
            "Choose source",
            "Take photo",
            "Choose from gallery",
            "Camera permission denied",
            "To take a photo, grant camera access in app settings. You can still choose an existing photo " +
                "from your gallery.",
            "Open settings",
            "Could not save image — try again",
            "Could not load image",
            "Image format not supported",
            "%1\$d session",
            "%1\$d sessions",
        )
    }
}

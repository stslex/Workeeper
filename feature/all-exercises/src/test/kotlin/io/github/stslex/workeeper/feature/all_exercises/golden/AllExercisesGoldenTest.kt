// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.golden

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppBlockedArchiveDialogContent
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialogContent
import io.github.stslex.workeeper.core.ui.kit.components.dialog.BlockedArchiveItem
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_exercises.ui.AllExercisesScreen
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExerciseRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExercisesEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TagFilterRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The all-exercises golden suite.
 *
 * The BASELINE commit recorded the pre-rebuild surface; this set is the rebuild, so the reviewer
 * reads one image diff per region rather than a hex diff at the end. An unexplained golden delta is
 * a review stop.
 *
 * ## Fixtures mirror the drawing
 *
 * Names, metas and tag names are lifted from `pass2d.html` `#s-list` so the element-by-element pass
 * can hold a golden beside the mockup with no mental renaming. They are fixture-side strings, so the
 * Cyrillic renders regardless of the harness's `en` resource locale — which is also why the **type**
 * token renders as "with weight" / "bodyweight" here and «с весом» / «без веса» on a Russian device:
 * the type is a *resource*, unlike everything else in these rows.
 *
 * ## Whole surface
 *
 * The top bar in both modes, the tag filter band, the list, the row's six states (weighted,
 * weightless, truncating, clamped, selected, and unselected-while-selecting), both paging tails, the
 * bulk-archive confirm dialog's content, the blocked-archive dialog's content, the empty state and
 * three whole-screen pictures — each in both themes.
 *
 * The third whole-screen picture is [screenNoRows], and it is **not** the empty state on a screen:
 * it is a blank screen, which is what this surface actually draws when the list is empty. See its
 * own KDoc. The empty state's coverage is the component golden.
 *
 * `AppBlockedArchiveDialogContent` became photographable *because* this commit split it out of
 * `Dialog {}`'s window, the same split `AppConfirmDialogContent` already had. It is the only surface
 * that reports a partially blocked bulk archive, and it had a drawn treatment and no visual gate at
 * all — the combination worth avoiding.
 *
 * **The holes**, both deliberate and both named: the two dialog *windows* — scrim and placement —
 * stay out of model and on manual verification (§10.4); and the permanent-delete dialog is not here
 * at all, because nothing in the repository can open it (B23). A golden of it would assert that a
 * picture nobody can see has not changed, while counting as coverage.
 *
 * ## Difference assertions
 *
 * §10.2 wants pairs, not lone pictures. Four carry it: [rowWeighted]/[rowWeightless] (the meta
 * line's first token, and nothing else, moves — which is the whole of what this screen's own drawn
 * region says); [rowWeighted]/[rowSelected] (the selection fill and the check);
 * [rowSelected]/[rowUnselectedInSelection] (the slot holds its width and empties rather than
 * collapsing); and [screenList]/[screenSelection] (the whole-surface mode change: top bar swapped
 * whole and gaining its archive action, FAB morphed shape and glyph while its fill stays put).
 */
internal class AllExercisesGoldenTest {

    // ---- fixtures ------------------------------------------------------------------------------

    private val weighted = ExerciseUiModel(
        uuid = "e1",
        name = "Отведение гантелей через стороны",
        type = ExerciseTypeUiModel.WEIGHTED,
        tags = persistentListOf("плечи", "верх тела"),
        sessionCount = 14,
        linkedTrainingsCount = 3,
        lastTrainedAt = null,
        footerLabel = "14 сессий · в 3 тренировках · последняя 9 июля",
        imagePath = null,
    )

    /**
     * The other type branch, and the **pair** that gates the screen's own drawn region.
     *
     * Identical to [weighted] in every field the row renders except `type` and the strings that
     * follow from the exercise being a different one. What the pair pins is that the type surfaces
     * at all, as a word, at the head of the line — the navnote's whole content.
     */
    private val weightless = weighted.copy(
        uuid = "e2",
        name = "Подтягивания широким хватом",
        type = ExerciseTypeUiModel.WEIGHTLESS,
        tags = persistentListOf("спина", "бицепс"),
        sessionCount = 9,
        linkedTrainingsCount = 2,
        footerLabel = "9 сессий · в 2 тренировках · последняя 2 июля",
    )

    /** The truncating-on-one-line case: the drawing's own long name (`#s-list`, skeleton frame). */
    private val longName = weighted.copy(
        uuid = "e3",
        name = "Тяга Т-грифа прямым широким хватом",
        tags = persistentListOf("спина"),
        footerLabel = "14 сессий · последняя 9 июля",
    )

    /**
     * The **clamp** case, and it has to be long enough to actually clamp.
     *
     * [longName] fits on one line at this width and truncates, so a golden of it proves the ellipsis
     * but says nothing about the second line. Proven on the sibling, not assumed: with only that
     * fixture, mutating `maxLines` from 2 to 1 left every golden byte-identical. This name reaches
     * two lines and then clamps, so the mutation is caught.
     */
    private val clamped = weighted.copy(
        uuid = "e4",
        name = "Тяга Т-грифа прямым широким хватом с паузой в нижней точке и медленным опусканием",
        tags = persistentListOf("спина", "бицепс", "трапеция", "предплечья"),
        footerLabel = "11 сессий · в 4 тренировках · последняя 2 июля",
    )

    private val tags = persistentListOf(
        TagUiModel(uuid = "g1", name = "грудь"),
        TagUiModel(uuid = "g2", name = "спина"),
        TagUiModel(uuid = "g3", name = "ноги"),
        TagUiModel(uuid = "g4", name = "плечи"),
        TagUiModel(uuid = "g5", name = "трицепс"),
        TagUiModel(uuid = "g6", name = "бицепс"),
    )

    private fun state(
        items: List<ExerciseUiModel>,
        selection: SelectionMode = SelectionMode.Off,
    ) = State(
        pagingUiState = PagingUiState { flowOf(PagingData.from(items)) },
        availableTags = tags,
        activeTagFilter = persistentSetOf("g1", "g2"),
        pendingPermanentDelete = null,
        selectionMode = selection,
        pendingBulkDelete = null,
        blockedArchiveDialog = null,
    )

    // ---- components ----------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowWeighted(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(
            item = weighted,
            isSelected = false,
            isSelecting = false,
            showDivider = true,
            onClick = {},
            onLongPress = {},
        )
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowWeightless(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(
            item = weightless,
            isSelected = false,
            isSelecting = false,
            showDivider = true,
            onClick = {},
            onLongPress = {},
        )
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowLongName(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(
            item = longName,
            isSelected = false,
            isSelecting = false,
            showDivider = true,
            onClick = {},
            onLongPress = {},
        )
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowClamped(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(
            item = clamped,
            isSelected = false,
            isSelecting = false,
            showDivider = true,
            onClick = {},
            onLongPress = {},
        )
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowSelected(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(
            item = weighted,
            isSelected = true,
            isSelecting = true,
            showDivider = true,
            onClick = {},
            onLongPress = {},
        )
    }

    /**
     * The row a selection is happening around but which is not itself selected: the chevron goes,
     * **the slot stays**. Collapsing the slot reflowed every row on entering the mode, which is what
     * the §26 "Selection mode" amendment records.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowUnselectedInSelection(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme) {
            ExerciseRow(
                item = weighted,
                isSelected = false,
                isSelecting = true,
                showDivider = true,
                onClick = {},
                onLongPress = {},
            )
        }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun tagFilterBand(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TagFilterRow(tags = tags, activeTagFilter = persistentSetOf("g1", "g2"), onToggle = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun emptyState(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExercisesEmptyState(onCreate = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun pagingLoading(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        PagingLoadingFooter()
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun pagingError(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        PagingErrorFooter(onRetry = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun confirmDialogContent(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier0 }) {
            AppConfirmDialogContent(
                title = "Archive selected?",
                body = "2 exercises will move to archive. Restore from Settings → Archive.",
                impactSummary = "Reversible · history preserved",
                confirmLabel = "Archive",
                onConfirm = {},
                onDismiss = {},
            )
        }

    /**
     * The partial-failure surface: some archived, some blocked by an active training. Photographable
     * only because this commit split the content out of `Dialog {}`'s window.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun blockedArchiveDialogContent(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier0 }) {
            AppBlockedArchiveDialogContent(
                title = "Some couldn’t be archived",
                archivedSummary = "1 exercise archived",
                items = persistentListOf(
                    BlockedArchiveItem("Отведение гантелей через стороны", "used in Верх, Плечи"),
                    BlockedArchiveItem("Подтягивания широким хватом", "used in Спина +2 more"),
                ),
                nextStep = "These are still in active trainings. " +
                    "Remove them from those trainings first, then archive.",
                confirmLabel = "Got it",
                onDismiss = {},
            )
        }

    // ---- whole surface -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenList(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(
            state = state(listOf(weighted, weightless, longName, clamped)),
            consume = {},
        )
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenSelection(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(
            state = state(
                items = listOf(weighted, weightless, longName, clamped),
                selection = SelectionMode.On(persistentSetOf("e1", "e3")),
            ),
            consume = {},
        )
    }

    /**
     * The screen with an empty paging source — **and it is blank.** Not the empty state: the top
     * bar, the tag band and the FAB over nothing at all.
     *
     * Named for what it photographs rather than for what it was meant to. `isEmptyAndIdle()`
     * requires `refresh`, `append` **and** `prepend` to be `NotLoading`, and in the one frame
     * Paparazzi renders, `refresh` has not settled — so the list has no rows and the empty state is
     * suppressed, which is the exact predicate and the exact outcome **B22** describes for a cold
     * open on device. This picture is consistent with B22 rather than proof of it (a single
     * unadvanced frame is its own explanation), but it is the shape, and it is worth having on
     * record: the previous name asserted coverage of a component that is not in the image.
     *
     * The empty state's own coverage is [emptyState], which renders the component directly.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenNoRows(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(state = state(emptyList()), consume = {})
    }
}

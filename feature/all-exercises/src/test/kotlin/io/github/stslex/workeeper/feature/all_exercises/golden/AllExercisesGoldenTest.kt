// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.golden

import androidx.paging.LoadState
import androidx.paging.LoadStates
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
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ColdOpenError
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ColdOpenLoading
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExerciseRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExercisesEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.FilteredEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.SelectionEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TagFilterRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The all-exercises golden suite: rows, band, tails, dialog contents, empty states and whole
 * screens, each in both themes. Fixtures mirror `pass2d.html` `#s-list`.
 */
internal class AllExercisesGoldenTest {

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

    /** A second exercise at the other type — the payload pair, not the token pair. */
    private val weightless = weighted.copy(
        uuid = "e2",
        name = "Подтягивания широким хватом",
        type = ExerciseTypeUiModel.WEIGHTLESS,
        tags = persistentListOf("спина", "бицепс"),
        sessionCount = 9,
        linkedTrainingsCount = 2,
        footerLabel = "9 сессий · в 2 тренировках · последняя 2 июля",
    )

    /** [weighted] with one field changed — the type. The pair that isolates the token (§10.2). */
    private val typeIsolated = weighted.copy(uuid = "e5", type = ExerciseTypeUiModel.WEIGHTLESS)

    /** The drawing's own long name: the meta line ellipsises, the name still fits one line. */
    private val longName = weighted.copy(
        uuid = "e3",
        name = "Тяга Т-грифа прямым широким хватом",
        tags = persistentListOf("спина"),
        footerLabel = "14 сессий · последняя 9 июля",
    )

    /**
     * The clamp case: long enough to reach two lines and then clamp, which catches a `maxLines`
     * 2 → 1 mutation that [longName] leaves invisible.
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

    /** [weighted] with the type flipped and nothing else — the pair that isolates the token. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowTypeIsolated(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(
            item = typeIsolated,
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

    /** Selection running around an unselected row: the chevron goes, the slot stays (spec §26). */
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

    /** The partial-failure surface: some archived, some blocked by an active training. */
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

    /** The first-run empty at screen level — no filter, which is what makes it first-run. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenFirstRunEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(
            state = pagingState(LoadState.NotLoading(true))
                .copy(activeTagFilter = persistentSetOf()),
            consume = {},
        )
    }

    /** No tile, by rule: a glyph tile means the screen is empty by itself (spec §26). */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun filteredEmpty(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        FilteredEmptyState(onClearFilter = {})
    }

    /** Selection running, list emptied by a filter — the recovery button is present. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun selectionEmptyFiltered(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme) { SelectionEmptyState(onClearFilter = {}) }

    /** The same state with no filter to undo — the button is gone, not disabled. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun selectionEmptyUnfiltered(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme) { SelectionEmptyState(onClearFilter = null) }

    /** The cold open. Not an empty state — the paging footer, where row 1 will land. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun coldOpenLoading(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ColdOpenLoading()
    }
    /** A failed first page: the same `.perr` as the append tail, moved to where row 1 would be. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun coldOpenError(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ColdOpenError(onRetry = {})
    }

    /**
     * A paging state stuck at a chosen refresh [LoadState]. `PagingData.from` never settles inside
     * one Paparazzi frame, so every settled empty state states its load states outright.
     */
    private fun pagingState(refresh: LoadState) = state(emptyList()).copy(
        pagingUiState = PagingUiState {
            flowOf(
                PagingData.empty(
                    sourceLoadStates = LoadStates(
                        refresh = refresh,
                        prepend = LoadState.NotLoading(false),
                        append = LoadState.NotLoading(false),
                    ),
                ),
            )
        },
    )

    /**
     * The cold open photographs nothing: the deferral withholds the spinner for 140 ms and
     * Paparazzi renders one frame at t=0. Delete the deferral and this reddens.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenColdOpen(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(state = pagingState(LoadState.Loading), consume = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenRefreshError(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(
            state = pagingState(LoadState.Error(IllegalStateException("no network"))),
            consume = {},
        )
    }

    /** A filter is on and matches nothing — the create CTA must not be what the user is offered. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenFilteredEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(state = pagingState(LoadState.NotLoading(true)), consume = {})
    }

    /** Selection running over an emptied list: the marks survive and the top bar still says so. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenSelectionEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(
            state = pagingState(LoadState.NotLoading(true))
                .copy(selectionMode = SelectionMode.On(persistentSetOf("x"))),
            consume = {},
        )
    }

    /** The same state with no filter to undo: the screen-level half of the conditional action. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenSelectionEmptyUnfiltered(theme: GoldenTheme, testInfo: TestInfo) =
        golden(testInfo, theme) {
            AllExercisesScreen(
                state = pagingState(LoadState.NotLoading(true)).copy(
                    activeTagFilter = persistentSetOf(),
                    selectionMode = SelectionMode.On(persistentSetOf("x")),
                ),
                consume = {},
            )
        }
}

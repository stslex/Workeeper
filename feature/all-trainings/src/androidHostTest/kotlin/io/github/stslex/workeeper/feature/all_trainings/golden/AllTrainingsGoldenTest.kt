// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.golden

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialogContent
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_trainings.ui.AllTrainingsScreen
import io.github.stslex.workeeper.feature.all_trainings.ui.components.ColdOpenError
import io.github.stslex.workeeper.feature.all_trainings.ui.components.ColdOpenLoading
import io.github.stslex.workeeper.feature.all_trainings.ui.components.FilteredEmptyState
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.SelectionEmptyState
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TagFilterRow
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingRow
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingsEmptyState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Golden suite for the all-trainings surface: rows, tag band, paging tails, empty states,
 * dialog content and whole-screen pictures, in both themes. See the v3 redesign spec §10.
 */
internal class AllTrainingsGoldenTest {

    private val plain = TrainingListItemUi(
        uuid = "t1",
        name = "Верх (с подтягиваниями)",
        tags = persistentListOf("грудь", "спина", "трицепс"),
        exerciseCount = 8,
        isActive = false,
        statusLabel = "вчера · 48 мин",
    )

    /** The clamp case: long enough to reach two lines and clamp, so a maxLines mutation reddens. */
    private val clamped = plain.copy(
        uuid = "t4",
        name = "Тяга Т-грифа прямым широким хватом с паузой в нижней точке и медленным опусканием",
        tags = persistentListOf("спина", "бицепс"),
        exerciseCount = 11,
        statusLabel = "9 сессий · последняя 2 июля",
    )

    /** The truncating-on-one-line case: the drawing's own long name (`#s-list`, skeleton frame). */
    private val longName = plain.copy(
        uuid = "t2",
        name = "Тяга Т-грифа прямым широким хватом",
        tags = persistentListOf("спина"),
        exerciseCount = 14,
        statusLabel = "14 сессий · последняя 9 июля",
    )

    /** The live row. The drawing carries its running-ness in the meta, not only in the surface. */
    private val active = plain.copy(
        uuid = "t3",
        name = "Ноги и плечи",
        tags = persistentListOf("ноги", "плечи"),
        exerciseCount = 6,
        isActive = true,
        statusLabel = "идёт сейчас · 12:04",
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
        items: List<TrainingListItemUi>,
        selection: SelectionMode = SelectionMode.Off,
    ) = State(
        pagingUiState = PagingUiState { flowOf(PagingData.from(items)) },
        availableTags = tags,
        activeTagFilter = persistentSetOf("g1", "g2"),
        selectionMode = selection,
        pendingBulkDelete = null,
        // Nothing running, so the empty state's drawn pair is whole; the withdrawn-CTA case is
        // StartBlankGateTest's.
        hasActiveSession = false,
    )

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowPlain(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TrainingRow(
            item = plain,
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
        TrainingRow(
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
        TrainingRow(
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
    fun rowActive(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TrainingRow(
            item = active,
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
        TrainingRow(
            item = plain,
            isSelected = true,
            isSelecting = true,
            showDivider = true,
            onClick = {},
            onLongPress = {},
        )
    }

    /** The live-and-selected row: running-ness is carried by the meta line. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowActiveSelected(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TrainingRow(
            item = active,
            isSelected = true,
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
        TrainingsEmptyState(onCreate = {}, onStartBlank = {})
    }

    /** Selecting, but this row unselected: the chevron goes, the slot keeps its width. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowUnselectedInSelection(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme) {
            TrainingRow(
                item = plain,
                isSelected = false,
                isSelecting = true,
                showDivider = true,
                onClick = {},
                onLongPress = {},
            )
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

    /** The dialog's content only: `Dialog {}` is its own window, outside Paparazzi's model. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun confirmDialogContent(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier0 }) {
            AppConfirmDialogContent(
                title = "Archive selected?",
                body = "2 trainings will move to archive. Restore from Settings → Archive.",
                impactSummary = "Reversible · history preserved",
                confirmLabel = "Archive",
                onConfirm = {},
                onDismiss = {},
            )
        }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenList(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllTrainingsScreen(state = state(listOf(active, plain, longName)), consume = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenSelection(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllTrainingsScreen(
            state = state(
                items = listOf(active, plain, longName),
                selection = SelectionMode.On(persistentSetOf("t1", "t3")),
            ),
            consume = {},
        )
    }

    /** First-run empty at screen level — no filter, which is what makes it first-run. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenFirstRunEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        // `pagingState(NotLoading)` and not `state(emptyList())`: `PagingData.from` never settles
        // inside one Paparazzi frame, so this would photograph the LOADING spinner.
        AllTrainingsScreen(
            state = pagingState(LoadState.NotLoading(true)).copy(activeTagFilter = persistentSetOf()),
            consume = {},
        )
    }

    /** No tile: a glyph tile means the screen is empty by itself. Pair with [emptyState]. */
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
    /** A failed first page: same treatment as the append tail, moved to where row 1 would be. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun coldOpenError(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ColdOpenError(onRetry = {})
    }

    /**
     * A paging state stuck at a chosen refresh [LoadState]. `PagingData.from` presents settled
     * `NotLoading` on every state, so it cannot express a cold open or a failed first page.
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

    /** GUARD: empty by construction — the 140 ms loading deferral withholds the spinner at t=0. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenColdOpen(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllTrainingsScreen(state = pagingState(LoadState.Loading), consume = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenRefreshError(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllTrainingsScreen(
            state = pagingState(LoadState.Error(IllegalStateException("no network"))),
            consume = {},
        )
    }

    /** A filter is on and matches nothing — the create CTA must not be what the user is offered. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenFilteredEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllTrainingsScreen(state = pagingState(LoadState.NotLoading(true)), consume = {})
    }

    /** Selection running over an emptied list: the marks survive and the top bar still says so. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenSelectionEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllTrainingsScreen(
            state = pagingState(LoadState.NotLoading(true))
                .copy(selectionMode = SelectionMode.On(persistentSetOf("x"))),
            consume = {},
        )
    }

    /** The same state with no filter to undo — the recovery button is gone, not disabled. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenSelectionEmptyUnfiltered(theme: GoldenTheme, testInfo: TestInfo) =
        golden(testInfo, theme) {
            AllTrainingsScreen(
                state = pagingState(LoadState.NotLoading(true)).copy(
                    activeTagFilter = persistentSetOf(),
                    selectionMode = SelectionMode.On(persistentSetOf("x")),
                ),
                consume = {},
            )
        }
}

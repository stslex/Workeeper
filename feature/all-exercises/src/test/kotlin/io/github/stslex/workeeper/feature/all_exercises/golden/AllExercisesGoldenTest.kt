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
 * weightless, type-isolated, truncating, clamped, selected, and unselected-while-selecting), both
 * paging tails, the
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
 * §10.2 wants pairs, not lone pictures. Four carry it: [rowWeighted]/[rowTypeIsolated] (the meta
 * line's first token, and **nothing else**, moves — which is the whole of what this screen's own
 * drawn region says, and it takes a fixture differing in one field: [rowWeightless] is a different
 * exercise and moves five); [rowWeighted]/[rowSelected] (the selection fill and the check);
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
     * A second exercise at the other type. It photographs the *payload* — different name length,
     * different counts, different tag count — where [typeIsolated] photographs the *token*.
     *
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

    /**
     * [weighted] with **one field changed** — the type — and nothing else.
     *
     * The pair this exists for. [weightless] is a different exercise: five rendered fields move
     * between it and [weighted], so diffing those two cannot attribute anything to the type token,
     * which is precisely what §10.2 asks a difference pair to carry. This one can: the only ink
     * that may move between [rowWeighted] and [rowTypeIsolated] is the meta line's first word and
     * the tail position that follows from its width.
     */
    private val typeIsolated = weighted.copy(uuid = "e5", type = ExerciseTypeUiModel.WEIGHTLESS)

    /**
     * The drawing's own long name (`#s-list`, skeleton frame). The **meta** line ellipsises; the
     * name itself fits on one line without truncating, so — corrected from the first draft — this
     * fixture does not prove the name's ellipsis. [clamped] does.
     */
    private val longName = weighted.copy(
        uuid = "e3",
        name = "Тяга Т-грифа прямым широким хватом",
        tags = persistentListOf("спина"),
        footerLabel = "14 сессий · последняя 9 июля",
    )

    /**
     * The **clamp** case, and it has to be long enough to actually clamp.
     *
     * [longName] fits on one line at this width without truncating, so it says nothing about the
     * name's ellipsis or about the second line. Proven on the sibling, not assumed: with only that
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
     * The first-run empty at screen level — **no filter**, which is what makes it first-run rather
     * than filtered.
     *
     * This golden used to be `screenEmpty`, and it photographed a **blank** screen: the old
     * predicate suppressed the rows and the empty state together whenever refresh had not settled,
     * so the picture named after the empty state contained everything except the empty state. It
     * was renamed `screenNoRows` when that was found, and now that `listSurface` distinguishes the
     * states there are no rows *and* no blank — so it is repointed at the case its name should
     * always have meant.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenFirstRunEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(
            state = pagingState(LoadState.NotLoading(true))
                .copy(activeTagFilter = persistentSetOf()),
            consume = {},
        )
    }

    // ---- states reached by an action ------------------------------------------------------------

    /**
     * No tile, by rule: §26's discriminator is that a glyph tile means the screen is empty by
     * itself. Pair it with [emptyState] — the tile is the only thing that should differ in kind.
     */
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

    /**
     * The same state with no filter to undo — the button is **gone**, not disabled.
     *
     * The difference pair for the conditional action. `AppEmptyState` renders a button only when
     * label and handler are both non-null, so this photographs that contract rather than trusting
     * its KDoc.
     */
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
    /**
     * The last member of B22's family: a failed **first** page. Same `.perr` as the append tail,
     * moved to where row 1 would be, and unruled because there is no row above it to separate from.
     * Pair it with [pagingError] — reason and rule are the only things that differ.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun coldOpenError(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ColdOpenError(onRetry = {})
    }

    /**
     * A paging state stuck at a chosen refresh [LoadState].
     *
     * `PagingData.from` presents `NotLoading` on every state, so it cannot express a cold open or a
     * failed first page. `PagingData.empty(sourceLoadStates = …)` can, and it is what makes the
     * screen's `when` branch gateable at all.
     */
    /**
     * **`PagingData.from` never settles inside one Paparazzi frame**, and that is not a detail.
     *
     * Measured, not assumed: `screenFilteredEmpty` built with `PagingData.from(emptyList())`
     * photographed the **loading** spinner, because `refresh` is still `Loading` at composition
     * time and `listSurface` — correctly — refuses to call an unsettled list empty. That is the
     * same mechanism as B22 itself, now showing up in the goldens written to prove B22 fixed.
     *
     * So every settled empty state is built here with the load states stated outright. A whole-
     * screen golden of an empty list that does *not* do this is a picture of the loading state
     * wearing another name — which is the exact failure `screenEmpty` was renamed for.
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

    // ---- whole-surface: every verdict the selector can return ------------------------------------

    /**
     * The screen-level wiring, and it took `PagingData.empty(sourceLoadStates = …)` to reach.
     *
     * `PagingData.from` always presents settled `NotLoading`, which is why the earlier screen
     * goldens could not enter a loading or error state at all — and why swapping which composable a
     * `when` branch dispatches used to leave every golden byte-identical. Proven: before these, the
     * mutation "refresh error renders nothing again" was GREEN. These five pictures are the gate on
     * the branch, not only on the treatments it chooses between.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    /**
     * **The cold open now photographs NOTHING, and that is what it gates.**
     *
     * It used to show the paging spinner where row 1 will land. The loading deferral
     * (`rememberLoadingVisible`) withholds it for 140 ms, and Paparazzi renders one frame with no
     * clock — so at t = 0 there is no spinner, and this image is the *absence*.
     *
     * That makes it more useful than before, not less: delete the deferral and the spinner returns
     * at t = 0, and these two images go red. It is the one picture-shaped gate on a change that is
     * otherwise entirely invisible to the visual suite, and it covers the residual
     * `LoadingVisibilityTest` names — that nothing proves the composable honours the delay it is
     * handed. The treatment itself is still photographed, by the paging-tail component golden.
     */
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

    /**
     * The same state with **no filter to undo** — the recovery button is gone, not disabled.
     *
     * The screen-level half of the conditional action. Its component pair
     * ([selectionEmptyFiltered]/[selectionEmptyUnfiltered]) proves `AppEmptyState`'s
     * label-without-handler contract; this proves the *screen* actually passes null. Measured: with
     * only the filtered picture, making `onClearFilter` unconditional left every golden identical.
     */
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

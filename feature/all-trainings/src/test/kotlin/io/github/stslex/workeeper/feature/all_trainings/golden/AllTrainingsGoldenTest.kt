// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.golden

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
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_trainings.ui.components.PagingLoadingFooter
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
 * The all-trainings golden suite.
 *
 * The BASELINE commit recorded the pre-rebuild surface; this set is the rebuild, so the reviewer
 * reads one image diff per region rather than a hex diff at the end. An unexplained golden delta is
 * a review stop.
 *
 * ## Fixtures mirror the drawing
 *
 * Names, meta strings and tag names are lifted from `pass2d.html` `#s-list` so the
 * element-by-element pass can hold a golden beside the mockup with no mental renaming. They are
 * fixture-side strings, so the Cyrillic renders regardless of the harness's `en` resource locale.
 *
 * ## Whole surface, and the one hole left in it
 *
 * The top bar in both modes, the tag filter band, the list, the row's **seven** states (plain,
 * long-name, clamped, active, selected, active-and-selected, and unselected-while-selecting), both
 * paging tails, the confirm dialog's content, the empty state and three whole-screen pictures —
 * each in both themes. (This sentence said "five" and enumerated five while the suite rendered
 * seven; an inventory that undercounts its own suite is how a golden goes missing unnoticed.)
 *
 * Two of those became photographable *because* this commit split them: `AppConfirmDialogContent`
 * out of `AppConfirmDialog`'s window, and the two paging footers out of the screen's
 * `LazyListScope` block. `Dialog {}` composes into its own window and Paparazzi models a single
 * one, so the content had a drawn treatment and no visual gate at all — the combination worth
 * avoiding.
 *
 * **The hole:** `AppConfirmDialog` itself — the window, its scrim and its placement — is still out
 * of model and stays on manual verification (§10.4). What is gated now is every pixel inside it.
 *
 * ## Difference assertions
 *
 * §10.2 wants pairs, not lone pictures. Four carry it: [rowPlain]/[rowActive] (one flag apart),
 * [rowPlain]/[rowSelected] (the selection fill and the check), [rowSelected]/[rowUnselectedInSelection]
 * (the amendment — the slot holds its width and empties rather than collapsing), and
 * [screenList]/[screenSelection] (the whole-surface mode change: top bar swapped whole, FAB morphed
 * shape and glyph while its fill stays put).
 */
internal class AllTrainingsGoldenTest {

    // ---- fixtures ----------------------------------------------------------------------------

    private val plain = TrainingListItemUi(
        uuid = "t1",
        name = "Верх (с подтягиваниями)",
        tags = persistentListOf("грудь", "спина", "трицепс"),
        exerciseCount = 8,
        isActive = false,
        statusLabel = "вчера · 48 мин",
    )

    /**
     * The **clamp** case, and it has to be long enough to actually clamp.
     *
     * The drawing's own long name — «Тяга Т-грифа прямым широким хватом» — fits on one line at this
     * width and truncates, so a golden of it proves the ellipsis but says nothing about the second
     * line. Proven, not assumed: with only that fixture, mutating `maxLines` from 2 to 1 left every
     * golden byte-identical. This name reaches two lines and then clamps, so the mutation is caught.
     */
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
    )

    // ---- components --------------------------------------------------------------------------

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

    /** The live-and-selected row: the one state D5 established is carried by the meta line. */
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

    /**
     * The row a selection is happening around but which is not itself selected: the chevron goes,
     * **the slot stays**. That is the amendment §26 "Selection mode" now carries — collapsing the
     * slot reflowed every row on entering the mode, so it holds its width and empties instead.
     */
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

    /**
     * The confirm dialog's **content**. `Dialog {}` is a separate window and out of Paparazzi's
     * model; its content is not, and it is where every colour, radius and rung lives. Without this
     * split the one destructive-confirmation surface on the screen had a drawn treatment and no
     * visual gate at all.
     */
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

    // ---- whole surface -----------------------------------------------------------------------

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

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllTrainingsScreen(state = state(emptyList()), consume = {})
    }
}

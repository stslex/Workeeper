// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.golden

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialogContent
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
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TagFilterRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The all-exercises golden suite — **the BASELINE recording.**
 *
 * This commit photographs the screen exactly as it is *before* the v3 rebuild: inset rounded cards,
 * a leading thumb-or-type-tile, in-row tag chips, a two-line body and an always-visible Material
 * chevron. Nothing here is a contract assertion. It exists so the rebuild's commit is one readable
 * image diff per region instead of a hex diff at the end, and so an unintended change anywhere else
 * on the surface shows up as a golden that moved when it had no reason to.
 *
 * ## Fixtures mirror the drawing
 *
 * Names and metas are lifted from `pass2d.html` `#s-list` — «Отведение гантелей через стороны»,
 * «Тяга Т-грифа прямым широким хватом» — so the element-by-element pass can hold a golden beside the
 * mockup with no mental renaming. They are fixture-side strings, so the Cyrillic renders regardless
 * of the harness's `en` resource locale.
 *
 * ## `imagePath` is null in every fixture, and that is not laziness
 *
 * The leading thumb is a Coil `AsyncImage` reading a `File` off disk. Under Paparazzi it would
 * render a placeholder that says nothing about the row and everything about the image loader — and
 * the drawing removes the leading slot entirely (`#s-list`, the type navnote: "миниатюра и иконка
 * типа остаются на детали"), so the thumb has no after-picture to be compared against. What the
 * baseline does capture is [rowWeighted] / [rowWeightless], the type **tile** — which is the branch
 * the rebuild actually replaces, with the type as the meta line's first word.
 *
 * ## What is deliberately absent
 *
 * No paging-tail goldens: `loadState.append` is never read on this screen today, so there is nothing
 * to photograph. No blocked-archive dialog: its content still lives inside `Dialog {}`, which
 * composes into its own window, and Paparazzi models one. Both arrive with the rebuild, together
 * with the split that makes the second photographable at all.
 *
 * And **no permanent-delete dialog**, which is a finding rather than an omission: its state is
 * written to non-null nowhere in the repository, so it cannot be opened (B23). A golden of it would
 * assert that a picture nobody can see has not changed, while counting as coverage.
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

    /** The other type branch — today a different tile glyph, after the rebuild a different word. */
    private val weightless = weighted.copy(
        uuid = "e2",
        name = "Подтягивания широким хватом",
        type = ExerciseTypeUiModel.WEIGHTLESS,
        tags = persistentListOf("спина", "бицепс"),
        sessionCount = 9,
        linkedTrainingsCount = 2,
        footerLabel = "9 сессий · в 2 тренировках · последняя 2 июля",
    )

    /** The drawing's own long name — one line at this width, so it truncates. */
    private val longName = weighted.copy(
        uuid = "e3",
        name = "Тяга Т-грифа прямым широким хватом",
        tags = persistentListOf("спина"),
        footerLabel = "14 сессий · последняя 9 июля",
    )

    /**
     * Long enough to actually reach a second line and clamp.
     *
     * The sibling proved this fixture is load-bearing rather than decorative: with only a
     * truncates-on-one-line name, mutating `maxLines` from 2 to 1 left every golden byte-identical.
     * The current row sets no `maxLines` at all, so this baseline shows the name running to three
     * lines — which is precisely the delta the rebuild has to close.
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
        ExerciseRow(item = weighted, isSelected = false, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowWeightless(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(item = weightless, isSelected = false, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowLongName(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(item = longName, isSelected = false, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowClamped(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(item = clamped, isSelected = false, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowSelected(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExerciseRow(item = weighted, isSelected = true, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun tagFilterBand(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TagFilterRow(tags = tags, activeTagFilter = persistentSetOf("g1", "g2"), onToggle = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun emptyState(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ExercisesEmptyState()
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

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmpty(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        AllExercisesScreen(state = state(emptyList()), consume = {})
    }
}

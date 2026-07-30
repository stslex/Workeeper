// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.golden

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_trainings.ui.AllTrainingsScreen
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
 * ## What this commit is
 *
 * **The BASELINE.** These images are the screen as it stands *before* the v3 rebuild, so every
 * later commit re-records exactly the region it changed and the reviewer reads an image diff
 * instead of a hex diff. They are disposable on purpose, and an unexplained golden delta is a
 * review stop.
 *
 * The module had no Paparazzi at all, so the plugin, the `golden-gate` apply and these recorded
 * PNGs land in **one commit** by necessity, not by preference: `golden-gate.gradle.kts` fails a
 * module with zero committed images and finalises every `verifyPaparazzi*` task, so a plugin-only
 * commit would turn the repo-wide verify red. `recordPaparazziDebug` runs first, which writes the
 * PNGs before the finalizer reads them.
 *
 * ## Fixtures mirror the drawing
 *
 * Names, meta strings and tag names are lifted from `pass2d.html` `#s-list` so the element-by-element
 * pass can hold a golden beside the mockup with no mental renaming. They are fixture-side strings, so
 * the Cyrillic renders regardless of the harness's `en` resource locale.
 *
 * ## Coverage, and the two holes in it
 *
 * Whole surface, not the body: the top bar in both modes, the tag filter band, the list, the row's
 * three payload states, the FAB in both states, and the empty state — each in both themes.
 *
 * **`AppConfirmDialog` is not here, and cannot be.** It renders in its own window and Paparazzi
 * models a single one; the harness KDoc lists that class of site as out of model by design. It stays
 * on manual verification (§10.4).
 *
 * **The paging tails are not here because they do not exist yet.** `loadState.append` is never read
 * by this screen, so there is no footer, spinner or retry to photograph. They arrive with the
 * rebuild, and the first golden of each is its own before-picture — recorded as absent, which is
 * exactly what the baseline should say.
 *
 * ## Difference assertions
 *
 * §10.2 wants pairs, not lone pictures. Three carry it here: [rowPlain]/[rowActive] (one flag
 * apart), [rowPlain]/[rowSelected] (the selection fill), and [screenList]/[screenSelection] (the
 * whole-surface mode change, including the top bar swap and the FAB's).
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

    /** The two-line clamp case: the drawing's own long name (`#s-list`, skeleton frame). */
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
        TrainingRow(item = plain, isSelected = false, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowLongName(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TrainingRow(item = longName, isSelected = false, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowActive(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TrainingRow(item = active, isSelected = false, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowSelected(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TrainingRow(item = plain, isSelected = true, onClick = {}, onLongPress = {})
    }

    /** The live-and-selected row: the one state D5 established is carried by the meta line. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowActiveSelected(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TrainingRow(item = active, isSelected = true, onClick = {}, onLongPress = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun tagFilterBand(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TagFilterRow(tags = tags, activeTagFilter = persistentSetOf("g1", "g2"), onToggle = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun emptyState(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        TrainingsEmptyState()
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

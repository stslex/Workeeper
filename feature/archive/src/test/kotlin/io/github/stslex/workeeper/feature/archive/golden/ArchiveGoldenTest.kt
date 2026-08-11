// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.golden

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Segment
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.archive.ui.ArchiveScreen
import io.github.stslex.workeeper.feature.archive.ui.components.ArchivedItemRow
import io.github.stslex.workeeper.feature.archive.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.archive.ui.components.PagingLoadingFooter
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The archive golden suite — the third and last of the list surfaces.
 *
 * ## Fixtures mirror the drawing
 *
 * The row fixtures are `pass2d.html` `#s-list`'s **fourth payload**, verbatim — «Румынская тяга» /
 * «упражнение · в архиве с 3 июля» — so the element-by-element pass can hold a golden beside the
 * mockup with no mental renaming. Meta lines are passed as fixture strings rather than composed
 * through `ResourceWrapper`, because the composition is `ArchiveMetaLineTest`'s subject and a
 * picture cannot check it either way; what the picture is for is the row's geometry and treatment.
 *
 * ## What is photographed
 *
 * The row in its two payloads (exercise and training), the two-line clamp, both paging tails, and
 * the whole screen in both segments. Each in both themes.
 *
 * ## The holes, named
 *
 * **The trailing affordances are photographed as they are, and that is deliberate.** `archive-delta`
 * §2.1 is unruled — two live verbs against a skeleton with one 20px slot — so these goldens lock in
 * the *current* arrangement, not the drawn one. Per §10.2 a golden locks in what **is**: when §2.1
 * is ruled, these images are expected to move, and that movement is the point of recording them now.
 *
 * **`DropdownMenu` is out of Paparazzi's model** (its own window, §10.4), so the overflow's single
 * item — permanent delete — is ungated by construction. That is an argument to weigh in §2.1, and it
 * is recorded there rather than worked around here.
 *
 * The permanent-delete dialog's own window is out of model for the same reason.
 */
internal class ArchiveGoldenTest {

    // ---- the row, both payloads ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowExercise(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ArchivedItemRow(
            item = exercise(),
            metaLine = "упражнение · в архиве с 3 июля · спина",
            showDivider = true,
            onRestore = {},
            onPermanentDelete = {},
        )
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowTraining(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ArchivedItemRow(
            item = training(),
            metaLine = "тренировка · в архиве с 9 июля",
            showDivider = true,
            onRestore = {},
            onPermanentDelete = {},
        )
    }

    /**
     * The two-line clamp and the non-wrapping meta line, on one row. `#s-list` holds every row to
     * one height whatever the name does; this is the picture that would catch it drifting.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowClamped(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        ArchivedItemRow(
            item = exercise(name = "Тяга Т-грифа прямым широким хватом сидя в наклоне"),
            metaLine = "упражнение · в архиве с 3 июля · спина · бицепс · верх тела · плечи",
            showDivider = false,
            onRestore = {},
            onPermanentDelete = {},
        )
    }

    // ---- the tails -------------------------------------------------------------------------------

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

    // ---- the screen ------------------------------------------------------------------------------

    /**
     * The settled empty tab — and it now photographs that rather than the loading spinner.
     *
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenExercisesNoRows(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        ArchiveScreen(state = state(Segment.EXERCISES), consume = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenTrainingsNoRows(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        ArchiveScreen(state = state(Segment.TRAININGS), consume = {})
    }

    // ---- fixtures --------------------------------------------------------------------------------

    private fun exercise(
        name: String = "Румынская тяга",
    ) = ArchivedItem.Exercise(
        uuid = "1",
        name = name,
        tags = listOf("спина"),
        archivedAt = ARCHIVED_AT,
        type = ExerciseTypeDomain.WEIGHTED,
    )

    private fun training() = ArchivedItem.Training(
        uuid = "2",
        name = "Верх (с подтягиваниями)",
        tags = emptyList(),
        archivedAt = ARCHIVED_AT,
        exerciseCount = 8,
    )

    private fun <T : Any> settledEmpty(): PagingData<T> = PagingData.empty(
        sourceLoadStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        ),
    )

    private fun state(segment: Segment) = State(
        selectedSegment = segment,
        exerciseCount = 12,
        trainingCount = 3,
        exerciseSegmentLabel = "Упражнения (12)",
        trainingSegmentLabel = "Тренировки (3)",
        // SETTLED, explicitly. `PagingData.empty()` with no `sourceLoadStates` presents
        // `refresh = Loading` forever inside a single Paparazzi frame, which is why the two
        // no-rows goldens used to be pictures of the spinner.
        archivedExercisesPaging = PagingUiState { flowOf(settledEmpty()) },
        archivedTrainingsPaging = PagingUiState { flowOf(settledEmpty()) },
        pendingDeleteImpact = null,
        pendingDeleteTarget = null,
        deleteImpactLoading = false,
    )

    private companion object {
        /** Fixed so a golden never depends on the clock. */
        const val ARCHIVED_AT = 1_720_000_000_000L
    }
}

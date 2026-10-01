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
 * The archive golden suite: the row in both payloads, the two-line clamp, both paging tails and the
 * screen in both segments, each theme. `DropdownMenu` and the dialog are out of Paparazzi's model.
 */
internal class ArchiveGoldenTest {

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

    /** The two-line clamp and the non-wrapping meta line; every row holds one height. */
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

    /** The settled empty tab, not the loading spinner. */
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
        // SETTLED explicitly: a bare `PagingData.empty()` stays `refresh = Loading` in one frame.
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

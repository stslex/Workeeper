// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.golden

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.components.toast.AppToast
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.DeleteExerciseCopyMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.ui.components.DeleteExerciseSheetContent
import io.github.stslex.workeeper.feature.live_workout.ui.components.ExerciseDescriptionSheetContent
import io.github.stslex.workeeper.feature.live_workout.ui.components.ExerciseMenuSheetContent
import io.github.stslex.workeeper.feature.live_workout.ui.components.SessionMenuSheetContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The four sheets' CONTENT (extraction §1.9) plus the undo toast, on the surfaces they sit
 * on. §10.4 puts the windows themselves on the device checklist, not here.
 */
internal class SessionSheetsGoldenTest {

    /** The window's screenEdge inset, so the golden composes like the shipped sheet. */
    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(horizontal = AppDimension.screenEdge),
        ) {
            content()
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sessionMenuSheet(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            SheetFrame {
                SessionMenuSheetContent(consume = {})
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseMenuSheet(theme: GoldenTheme, testInfo: TestInfo) {
        // A mid-session addition currently toggled ONE-OFF: the switch row is on.
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            SheetFrame {
                ExerciseMenuSheetContent(
                    exercise = sheetExercise(isPlanAttached = false),
                    showOneOffRow = true,
                    consume = {},
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseMenuSheetSkipped(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            SheetFrame {
                ExerciseMenuSheetContent(
                    exercise = sheetExercise(status = ExerciseStatusUiModel.SKIPPED),
                    showOneOffRow = false,
                    consume = {},
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun deleteExerciseSheetPlanned(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            SheetFrame {
                DeleteExerciseSheetContent(
                    exercise = sheetExercise(),
                    copy = DeleteExerciseCopyMapper.map(
                        isAdhocSession = false,
                        isMidSessionAdded = false,
                    ),
                    consume = {},
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun deleteExerciseSheetAdhoc(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            SheetFrame {
                DeleteExerciseSheetContent(
                    exercise = sheetExercise(),
                    copy = DeleteExerciseCopyMapper.map(
                        isAdhocSession = true,
                        isMidSessionAdded = false,
                    ),
                    consume = {},
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun descriptionSheet(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            SheetFrame {
                ExerciseDescriptionSheetContent(
                    exercise = sheetExercise(
                        description = "Лопатки вниз и назад до начала движения, корпус " +
                            "зафиксирован. Опускание медленнее подъёма.",
                    ),
                    consume = {},
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun undoToast(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            AppToast(
                message = "«жим лёжа» удалено из плана",
                actionLabel = "Отменить",
                onAction = {},
            )
        }
    }
}

private fun sheetExercise(
    status: ExerciseStatusUiModel = ExerciseStatusUiModel.CURRENT,
    isPlanAttached: Boolean = true,
    description: String? = null,
): LiveExerciseUiModel = LiveExerciseUiModel(
    performedExerciseUuid = "pe-1",
    exerciseUuid = "ex-1",
    exerciseName = "тяга верхнего блока",
    exerciseType = ExerciseTypeUiModel.WEIGHTED,
    position = 0,
    status = status,
    statusLabel = "",
    planSets = persistentListOf(),
    performedSets = persistentListOf(),
    isPlanAttached = isPlanAttached,
    description = description,
)

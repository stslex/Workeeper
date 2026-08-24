// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListRow
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListRowSlot
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.continuityAlphaSpec
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import kotlinx.collections.immutable.persistentListOf

/**
 * One row of the exercises list (`pass2d.html` `#s-list` `.row`). No leading slot: the type moves
 * into the meta line as its first token, so the row is a pure function of its model.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ExerciseRow(
    item: ExerciseUiModel,
    isSelected: Boolean,
    isSelecting: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppListRow(
        modifier = modifier,
        // GUARD: call `liftedSurface` unconditionally and let the flag drive it — branching here
        // rebuilds the modifier graph and kills the tween. The resting row has no fill of its own.
        rowModifier = Modifier
            .liftedSurface(
                shape = RectangleShape,
                lifted = isSelected,
                restingColor = Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .testTag("AllExercisesItem_${item.uuid}"),
        name = item.name,
        nameTestTag = "AllExercisesItemName_${item.uuid}",
        meta = item.metaLine(),
        metaTestTag = "AllExercisesItemMeta_${item.uuid}",
        showDivider = showDivider,
        content = {
            TrailingSlot(item = item, isSelected = isSelected, isSelecting = isSelecting)
        },
    )
}

/**
 * The fixed-width trailing slot: chevron at rest, check when selected, empty when another row is.
 * GUARD: keep `using null` — an animated container puts back the reflow the fixed slot removes.
 */
@Composable
private fun TrailingSlot(
    item: ExerciseUiModel,
    isSelected: Boolean,
    isSelecting: Boolean,
) {
    val spec = continuityAlphaSpec<Float>()
    AppListRowSlot {
        AnimatedContent(
            targetState = trailingSlotKind(isSelected = isSelected, isSelecting = isSelecting),
            transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) using null },
            contentAlignment = Alignment.Center,
            label = "row-trailing-slot",
        ) { kind ->
            when (kind) {
                TrailingSlotKind.CHECK -> Icon(
                    modifier = Modifier
                        .size(SLOT)
                        .testTag("AllExercisesItemCheck_${item.uuid}"),
                    imageVector = AppIcons.RowCheck,
                    contentDescription = null,
                    tint = AppUi.colors.textPrimary,
                )

                TrailingSlotKind.EMPTY -> Unit

                TrailingSlotKind.CHEVRON -> Icon(
                    modifier = Modifier.size(SLOT),
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = AppUi.colors.textTertiary,
                )
            }
        }
    }
}

/**
 * Meta line: information first, tags last (spec §26). The type heads it because the line does not
 * wrap, so the tail is what truncates.
 */
@Composable
private fun ExerciseUiModel.metaLine(): String {
    val separator = " ${stringResource(R.string.feature_all_exercises_footer_separator)} "
    val head = stringResource(
        when (type) {
            ExerciseTypeUiModel.WEIGHTED -> R.string.feature_all_exercises_type_weighted
            ExerciseTypeUiModel.WEIGHTLESS -> R.string.feature_all_exercises_type_weightless
        },
    )
    return (listOf(head, footerLabel).filter { it.isNotEmpty() } + tags).joinToString(separator)
}

/** The drawn 20px glyph, on the icon ladder — the slot's own width is `AppListRowSlot`'s. */
private val SLOT = AppDimension.iconSm

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ExerciseRowPreview() {
    AppTheme {
        Column {
            ExerciseRow(
                item = ExerciseUiModel(
                    uuid = "1",
                    name = "Отведение гантелей через стороны",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf("плечи"),
                    sessionCount = 14,
                    linkedTrainingsCount = 3,
                    lastTrainedAt = null,
                    footerLabel = "14 сессий · последняя 9 июля",
                    imagePath = null,
                ),
                isSelected = false,
                isSelecting = false,
                showDivider = true,
                onClick = {},
                onLongPress = {},
            )
            ExerciseRow(
                item = ExerciseUiModel(
                    uuid = "2",
                    name = "Подтягивания широким хватом",
                    type = ExerciseTypeUiModel.WEIGHTLESS,
                    tags = persistentListOf("спина"),
                    sessionCount = 9,
                    linkedTrainingsCount = 2,
                    lastTrainedAt = null,
                    footerLabel = "9 сессий · последняя 2 июля",
                    imagePath = null,
                ),
                isSelected = true,
                isSelecting = true,
                showDivider = false,
                onClick = {},
                onLongPress = {},
            )
        }
    }
}

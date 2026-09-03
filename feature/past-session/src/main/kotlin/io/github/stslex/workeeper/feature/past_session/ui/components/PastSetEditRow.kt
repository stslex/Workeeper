// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordTag
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetRowGeometry
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_reorderable_drag_handle
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_field_a11y_reps
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_field_a11y_weight
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import org.jetbrains.compose.resources.stringResource

/**
 * `.set` — one logged, editable set (extraction §2.6): `set-i · field(s) · tchip-or-prtag ·
 * drag handle`. The trailing slot holds the type chip OR the record tag, never both.
 */
@Composable
internal fun PastSetEditRow(
    set: PastSetUiModel,
    isWeighted: Boolean,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onPrTagClick: () -> Unit,
    modifier: Modifier = Modifier,
    indexColumnWidth: Dp = SetRowGeometry.indexMinWidth,
    dragHandleModifier: Modifier = Modifier,
    weightSlotProbe: ((slotWidthPx: Int, resolvedStyle: TextStyle) -> Unit)? = null,
    repsSlotProbe: ((slotWidthPx: Int, resolvedStyle: TextStyle) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimension.Space.xs,
                vertical = AppDimension.Space.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Text(
            modifier = Modifier.widthIn(min = indexColumnWidth),
            text = (set.position + 1).toString(),
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textDim,
            maxLines = 1,
        )
        if (isWeighted) {
            AppNumberInput(
                modifier = Modifier.weight(SetRowGeometry.WEIGHT_COLUMN_FLEX),
                value = set.weightInput,
                onValueChange = onWeightChange,
                decimals = 1,
                isError = set.weightError,
                isRecord = set.isPersonalRecord,
                isLogged = true,
                fieldInset = SetRowGeometry.compactFieldInset,
                accessibilityLabel = stringResource(Res.string.core_ui_kit_set_field_a11y_weight),
                valueSlotProbe = weightSlotProbe,
            )
            AppNumberInput(
                modifier = Modifier.weight(1f),
                value = set.repsInput,
                onValueChange = onRepsChange,
                decimals = 0,
                isError = set.repsError,
                isRecord = set.isPersonalRecord,
                isLogged = true,
                fieldInset = SetRowGeometry.compactFieldInset,
                accessibilityLabel = stringResource(Res.string.core_ui_kit_set_field_a11y_reps),
                valueSlotProbe = repsSlotProbe,
            )
        } else {
            // Bodyweight: ONE full-width field; the unit lives in the column header.
            AppNumberInput(
                modifier = Modifier.weight(1f),
                value = set.repsInput,
                onValueChange = onRepsChange,
                decimals = 0,
                isError = set.repsError,
                isRecord = set.isPersonalRecord,
                isLogged = true,
                fieldInset = SetRowGeometry.compactFieldInset,
                accessibilityLabel = stringResource(Res.string.core_ui_kit_set_field_a11y_reps),
                valueSlotProbe = repsSlotProbe,
            )
        }
        // GUARD: chip and tag share one slot width, or rows disagree with the column header.
        val trailingSlotWidth = SetRowGeometry.resolveTrailingSlotWidth()
        if (set.isPersonalRecord) {
            Box(
                modifier = Modifier
                    .width(trailingSlotWidth)
                    .clip(RoundedCornerShape(AppDimension.Radius.small))
                    .clickable(onClick = onPrTagClick),
                contentAlignment = Alignment.Center,
            ) {
                PersonalRecordTag()
            }
        } else {
            AppSetTypeChip(
                type = set.type.toUiKitType(),
                modifier = Modifier.width(trailingSlotWidth),
            )
        }
        Icon(
            modifier = dragHandleModifier.size(DragHandleSize),
            imageVector = Icons.Filled.DragHandle,
            contentDescription = stringResource(Res.string.core_ui_kit_reorderable_drag_handle),
            tint = AppUi.colors.textDim,
        )
    }
}

/** The drag slot; `CardBody` reads it to build the header's trailing gutter. */
internal val DragHandleSize: Dp = 24.dp

@Preview(name = "Weighted Light")
@Composable
private fun PastSetEditRowWeightedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PastSetEditRow(
            set = stubSet(),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onPrTagClick = {},
        )
    }
}

@Preview(name = "Record Dark")
@Composable
private fun PastSetEditRowRecordDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSetEditRow(
            set = stubSet().copy(weightInput = "77", isPersonalRecord = true),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onPrTagClick = {},
        )
    }
}

@Preview(name = "Weightless Dark")
@Composable
private fun PastSetEditRowWeightlessDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSetEditRow(
            set = stubSet().copy(weightInput = ""),
            isWeighted = false,
            onWeightChange = {},
            onRepsChange = {},
            onPrTagClick = {},
        )
    }
}

@Preview(name = "Error")
@Composable
private fun PastSetEditRowErrorPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSetEditRow(
            set = stubSet().copy(repsInput = "", repsError = true),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onPrTagClick = {},
        )
    }
}

private fun stubSet(): PastSetUiModel = PastSetUiModel(
    setUuid = "s-1",
    performedExerciseUuid = "pe-1",
    position = 0,
    type = SetTypeUiModel.WORK,
    weightInput = "49",
    repsInput = "15",
    weightError = false,
    repsError = false,
    isPersonalRecord = false,
)

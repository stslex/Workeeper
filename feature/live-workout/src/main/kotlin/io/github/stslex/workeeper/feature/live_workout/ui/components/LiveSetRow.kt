// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppCheckmarkButton
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.motion.rememberSetClosureVisuals
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordTag
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetRowGeometry
import io.github.stslex.workeeper.core.ui.kit.components.tooltip.AppTooltip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.core.ui.kit.R as KitR

/**
 * `.set.flash` peaks: dark `rgba(241,245,249,.13)`, light `rgba(13,17,20,.09)` — the wash is
 * `max` at these alphas (`--flash`, session-v3f.html:22,30), scaled by the automaton's
 * decaying `flashAlpha` envelope. Step 5 used 0.13 in both themes; light is 9% as drawn.
 */
private const val FLASH_PEAK_ALPHA_DARK = 0.13f
private const val FLASH_PEAK_ALPHA_LIGHT = 0.09f

/**
 * `.set` (extraction §1.6): `set-i · field(s) · tchip-or-prtag · mark`, on a **transparent**
 * row — blocker B7's fix. Step 5 washed the whole row `surfaceTier4` when done, which is what
 * pushed the unit label under its threshold; the done wash now lives on the *field*
 * (`AppNumberInput.isDone` → `donefill`) and the row paints nothing.
 *
 * Geometry per the mockup: 8dp vertical / 4dp horizontal padding, 8dp gaps, an 8dp radius
 * (the flash wash rounds to it), content-driven height (48dp fields; the old fixed 56dp row
 * is gone). The hairline between rows belongs to the card body, not the row.
 *
 * The trailing slot is the type chip **or** the PR tag — never both (`drawSets`,
 * session-v3f.html:386-390). The old 3dp record stripe and the 18dp PR pill are retired: the
 * record row's signal is the molten wash on its fields, the molten value (B1, 26sp — the size
 * that makes the tint legal), and the tag.
 *
 * [flashAlphaOverride] exists for the golden pair (§10.2): the flash is an animation the
 * gate cannot reach mid-flight, so the golden freezes its peak through this parameter.
 * Production never passes it.
 */
@Suppress("LongParameterList")
@Composable
internal fun LiveSetRow(
    set: LiveSetUiModel,
    isWeighted: Boolean,
    onWeightChange: (Double?) -> Unit,
    onRepsChange: (Int?) -> Unit,
    onTypeChange: (SetTypeUiModel) -> Unit,
    onMarkDone: () -> Unit,
    onUncheck: () -> Unit,
    editable: Boolean,
    modifier: Modifier = Modifier,
    indexColumnWidth: Dp = SetRowGeometry.indexMinWidth,
    testTagPrefix: String? = null,
    flashAlphaOverride: Float? = null,
    weightSlotProbe: ((slotWidthPx: Int, resolvedStyle: TextStyle) -> Unit)? = null,
    repsSlotProbe: ((slotWidthPx: Int, resolvedStyle: TextStyle) -> Unit)? = null,
    indexColumnProbe: ((widthPx: Int) -> Unit)? = null,
    weightLeftProbe: ((leftPx: Float) -> Unit)? = null,
    repsLeftProbe: ((leftPx: Float) -> Unit)? = null,
) {
    // §9's merged automaton, one instance for the whole row: the mark's morph, this row's
    // flash and the rail segment all resolve from the same closure, with `isRecord` selecting
    // `molten` over `max` rather than selecting a second animation.
    val closure = rememberSetClosureVisuals(isDone = set.isDone, isRecord = set.isPersonalRecord)
    val flashPeak = if (AppUi.colors.isDark) FLASH_PEAK_ALPHA_DARK else FLASH_PEAK_ALPHA_LIGHT
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimension.Radius.small))
            // The flash. A COLOUR value, so it is driven by `out` and never by the
            // overshooting `spring` — an alpha lerped past 1.0 clamps, and the wash would
            // read as a hard cut.
            .drawWithContent {
                drawContent()
                val alpha = flashAlphaOverride ?: closure.flashAlpha
                if (alpha > 0f) {
                    drawRect(color = closure.accent, alpha = alpha * flashPeak)
                }
            }
            .padding(
                horizontal = AppDimension.Space.xs,
                vertical = AppDimension.Space.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Text(
            // Min-width, not fixed, with `maxLines = 1`: at `mono.meta` a digit is ~7.5dp, so
            // a two-digit index needs ~15dp and a fixed 12dp box breaks it at a grapheme
            // boundary (1 over 0) instead of overflowing — silently, since the 48dp fields
            // set the row height. The minimum arrives from the container
            // (`SetRowGeometry.resolveIndexColumnWidth`) so the header above and every row
            // grow together past nine sets; a bare row keeps the drawn 12dp default.
            modifier = Modifier
                .widthIn(min = indexColumnWidth)
                // The R14 alignment gate's row-side capture (test-only, never passed in
                // production): the index column's LAID-OUT width, minimum and intrinsic
                // growth included — what the header's gutter must equal.
                .onSizeChanged { size -> indexColumnProbe?.invoke(size.width) },
            text = (set.position + 1).toString(),
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textDim,
            maxLines = 1,
        )
        if (isWeighted) {
            Box(modifier = Modifier.weight(SetRowGeometry.WEIGHT_COLUMN_FLEX)) {
                AppNumberInput(
                    value = set.weightLabel,
                    onValueChange = { input -> onWeightChange(input.toDoubleOrNull()) },
                    decimals = 2,
                    enabled = editable && !set.isDone,
                    isRecord = set.isPersonalRecord,
                    isDone = set.isDone,
                    fieldInset = SetRowGeometry.compactFieldInset,
                    accessibilityLabel = stringResource(
                        KitR.string.core_ui_kit_set_field_a11y_weight,
                    ),
                    valueSlotProbe = weightSlotProbe,
                    valueLeftProbe = weightLeftProbe,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AppNumberInput(
                    value = set.reps.takeIf { it > 0 }?.toString().orEmpty(),
                    onValueChange = { input -> onRepsChange(input.toIntOrNull()) },
                    decimals = 0,
                    enabled = editable && !set.isDone,
                    isRecord = set.isPersonalRecord,
                    isDone = set.isDone,
                    fieldInset = SetRowGeometry.compactFieldInset,
                    accessibilityLabel = stringResource(
                        KitR.string.core_ui_kit_set_field_a11y_reps,
                    ),
                    valueSlotProbe = repsSlotProbe,
                    valueLeftProbe = repsLeftProbe,
                )
            }
        } else {
            // Bodyweight: ONE field, full width; the unit lives in the column header
            // (`ПОВТОРЕНИЙ` — set-field-column-headers.md, locked decision 2).
            Box(modifier = Modifier.weight(1f)) {
                AppNumberInput(
                    value = set.reps.takeIf { it > 0 }?.toString().orEmpty(),
                    onValueChange = { input -> onRepsChange(input.toIntOrNull()) },
                    decimals = 0,
                    enabled = editable && !set.isDone,
                    isRecord = set.isPersonalRecord,
                    isDone = set.isDone,
                    fieldInset = SetRowGeometry.compactFieldInset,
                    accessibilityLabel = stringResource(
                        KitR.string.core_ui_kit_set_field_a11y_reps,
                    ),
                    valueSlotProbe = repsSlotProbe,
                )
            }
        }
        if (set.isPersonalRecord) {
            PersonalRecordTag(
                modifier = if (testTagPrefix != null) {
                    Modifier.testTag("${testTagPrefix}_PrTag")
                } else {
                    Modifier
                },
            )
        } else {
            AppTooltip(text = stringResource(R.string.feature_live_workout_set_type_tooltip)) {
                Box(
                    modifier = Modifier
                        .let { base ->
                            if (testTagPrefix != null) base.testTag("${testTagPrefix}_TypeChip") else base
                        }
                        .clickable(enabled = editable) { onTypeChange(set.type) },
                ) {
                    AppSetTypeChip(type = set.type.toUiKitType())
                }
            }
        }
        AppCheckmarkButton(
            modifier = if (testTagPrefix != null) {
                Modifier.testTag("${testTagPrefix}_Checkbox")
            } else {
                Modifier
            },
            isDone = set.isDone,
            isRecord = set.isPersonalRecord,
            enabled = true,
            onToggle = { if (set.isDone) onUncheck() else onMarkDone() },
        )
    }
}

@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun LiveSetRowPendingPreview() {
    AppTheme {
        LiveSetRow(
            set = LiveSetUiModel(0, 100.0, 5, SetTypeUiModel.WORK, isDone = false),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
            editable = true,
        )
    }
}

@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun LiveSetRowDonePreview() {
    AppTheme {
        LiveSetRow(
            set = LiveSetUiModel(1, 100.0, 5, SetTypeUiModel.WORK, isDone = true),
            isWeighted = true,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
            editable = true,
        )
    }
}

@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun LiveSetRowWeightlessPreview() {
    AppTheme {
        LiveSetRow(
            set = LiveSetUiModel(0, null, 12, SetTypeUiModel.WARMUP, isDone = false),
            isWeighted = false,
            onWeightChange = {},
            onRepsChange = {},
            onTypeChange = {},
            onMarkDone = {},
            onUncheck = {},
            editable = true,
        )
    }
}

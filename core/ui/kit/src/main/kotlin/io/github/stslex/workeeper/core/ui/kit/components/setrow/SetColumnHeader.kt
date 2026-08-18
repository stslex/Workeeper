// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.setrow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The set-row column header — the unit's new home after it left the field
 * (set-field-column-headers.md, locked decisions 1-4).
 *
 * ## Geometry mirrors the row, from one source
 *
 * `[index gutter] [weight header ×1.2] [reps header ×1] [trailing gutter]`, with the row's
 * own 4dp horizontal padding and 8dp gaps. The index gutter takes [indexColumnWidth] — the
 * SAME resolved value the container passes to every row ([SetRowGeometry], D3) — and the
 * flex split reads [SetRowGeometry.WEIGHT_COLUMN_FLEX]. [trailingWidth] is the consumer's
 * trailing cluster (chip slot + gap + checkmark / drag handle), computed by the feature
 * from its own components' widths, because the two rows deliberately differ there.
 * Each label is inset by the field's 12dp inner padding so it sits over the VALUE, not
 * over the field's rounded edge.
 *
 * ## Two-tone, one Text (D2)
 *
 * The NAME is `textSecondary`, the UNIT in parentheses a `SpanStyle` of `textDim` — no
 * dimmer: a caption-rung label owes 4.5:1 (locked decision 3; hierarchy comes from raising
 * the name, not sinking the unit). One `Text` with `TextOverflow.Ellipsis` is what makes
 * locked decision 4 structural: truncation eats the tail first, and the unit IS the tail —
 * `(КГ)` goes before `ВЕС` by construction, verified by `SetColumnHeaderTest`.
 *
 * Casing is the `AppLabel` move: stored lower-case, uppercased at the edge — a display
 * concern kept out of strings.xml. Style is `mono.caption`, never the numeric family
 * (Archivo has zero Cyrillic; `NumericFontFamilyOnLocalizedTextRule` guards the boundary).
 */
@Composable
fun SetColumnHeader(
    isWeighted: Boolean,
    indexColumnWidth: Dp,
    trailingWidth: Dp,
    modifier: Modifier = Modifier,
    indexGutterProbe: ((widthPx: Int) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.Space.xs)
            .padding(top = AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        // [indexGutterProbe] is the R14 alignment gate's capture point (the
        // `flashAlphaOverride` move — test-only, production never passes it): the gutter's
        // laid-out width is the ONE free variable in the label/value alignment; the gap and
        // the label inset are the same tokens the rows read.
        Spacer(
            modifier = Modifier
                .width(indexColumnWidth)
                .onSizeChanged { size -> indexGutterProbe?.invoke(size.width) },
        )
        if (isWeighted) {
            HeaderCell(
                label = buildSetColumnHeaderLabel(
                    name = stringResource(R.string.core_ui_kit_set_header_weight),
                    unit = stringResource(R.string.core_ui_kit_plan_editor_unit_kg),
                    unitColor = AppUi.colors.textDim,
                ),
                modifier = Modifier.weight(SetRowGeometry.WEIGHT_COLUMN_FLEX),
            )
            HeaderCell(
                label = buildSetColumnHeaderLabel(
                    name = stringResource(R.string.core_ui_kit_set_header_reps),
                    unit = null,
                    unitColor = AppUi.colors.textDim,
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            HeaderCell(
                label = buildSetColumnHeaderLabel(
                    name = stringResource(R.string.core_ui_kit_set_header_reps_full),
                    unit = null,
                    unitColor = AppUi.colors.textDim,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.width(trailingWidth))
    }
}

/**
 * One column's label, inset to sit over the field's VALUE rather than its edge — by the
 * same [SetRowGeometry.compactFieldInset] the rows pass to their fields, so label and
 * value cannot drift apart (R13 closed exactly that drift: the first lever left the label
 * at `Space.md` over fields that had moved to `Space.sm`).
 */
@Composable
private fun HeaderCell(
    label: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SetColumnHeaderLabel(
            label = label,
            modifier = Modifier.padding(start = SetRowGeometry.compactFieldInset),
        )
    }
}

/**
 * The header's single `Text`. Internal, with a test-only [onTextLayout] oracle, so
 * `SetColumnHeaderTest` can prove the ellipsis order on the REAL text parameters rather
 * than on a lookalike (`onTextLayout` is a test oracle only — D5's rule).
 */
@Composable
internal fun SetColumnHeaderLabel(
    label: AnnotatedString,
    modifier: Modifier = Modifier,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    Text(
        modifier = modifier,
        text = label,
        style = AppUi.typography.mono.caption,
        color = AppUi.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result -> onTextLayout?.invoke(result) },
    )
}

/**
 *`ВЕС (КГ)` from `вес` + `кг`: locale-aware uppercase at the edge (the `AppLabel` move),
 * parentheses added here — formatting is the component's, not the translation's. The unit
 * span is the string's TAIL, which is what hands locked decision 4 to `TextOverflow`.
 */
internal fun buildSetColumnHeaderLabel(
    name: String,
    unit: String?,
    unitColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(name.uppercase())
    if (unit != null) {
        append(' ')
        withStyle(SpanStyle(color = unitColor)) {
            append("(${unit.uppercase()})")
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SetColumnHeaderPreview() {
    AppTheme {
        Column {
            SetColumnHeader(
                isWeighted = true,
                indexColumnWidth = SetRowGeometry.indexMinWidth,
                trailingWidth = AppDimension.Space.xxl,
            )
            SetColumnHeader(
                isWeighted = false,
                indexColumnWidth = SetRowGeometry.indexMinWidth,
                trailingWidth = AppDimension.Space.xxl,
            )
        }
    }
}

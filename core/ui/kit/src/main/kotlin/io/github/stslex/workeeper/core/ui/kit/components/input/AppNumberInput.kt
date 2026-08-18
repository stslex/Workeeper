package io.github.stslex.workeeper.core.ui.kit.components.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The mockup's `.field` — a value and its unit on a recessed panel (extraction §1.6).
 *
 * ## The value is `dataValue` — blocker B1, landed
 *
 * `.data-l` is 25px Archivo `wdth 115 / wght 700` → the 26 rung, named
 * `AppTypography.dataValue`. At 26sp bold the WCAG threshold is **3:1**, which retires the
 * measurement that used to sit here: at the previous 19sp/600 the value owed 4.5:1 and molten
 * could not pay it in light (4.14). At this rung the mockup's colour ramp ships as drawn:
 *
 *  - resting: `textTertiary`. The mockup paints `--idle`, whose light value (#7C858F) sits at
 *    3.11:1 on the field — inside quantisation noise of the 3:1 line — and whose dark value IS
 *    `meta`'s hex. Same correction §2.4 applied to light `meta`: the mockup was drawn, not
 *    measured; the pending value uses the meta value in both themes. The brightness principle
 *    survives — pending is dimmer than done.
 *  - done ([isDone]): `textPrimary` — `.set.done .data-l{color:var(--max)}`.
 *  - logged ([isLogged]): `textPrimary` on the **plain** `surfaceTier3` field — the past
 *    session's inline override (`pass2d.html:306`, `style="color:var(--max)"` on every
 *    ordinary row). A past set is complete by construction, so its value takes full
 *    contrast, but it is *not* "done-during-a-session": the `donefill` wash marks the act
 *    of completing, and a finished record carries no such act. Colour without the wash.
 *  - record ([isRecord]): `record.textPrimary` — `.set.pr .data-l{color:var(--molten)}`,
 *    legal at TITLE. Record wins over done and logged, as in the stylesheet (`.pr` is
 *    declared after `.done`, and the past markup omits its inline override on the PR row).
 *
 * ## The washes replace the fill — blocker B7, landed
 *
 * `.set.done .field{background:var(--donefill)}` and `.set.pr .field{background:var(--molten-bg)}`
 * **replace** the field tier; the translucent wash composites over the card behind the row
 * (`sec`, or `slab` when the card is lifted). An earlier revision stacked the record wash on
 * `surfaceTier3` because the done ROW washed `surfaceTier4` behind it and the CSS-faithful
 * composite failed on that backdrop (3.99 dark). B7 removed the row wash — the backdrop that
 * failed no longer exists, and over the card tiers every pair clears its threshold
 * (`ContrastContract`, the `donefill` and `record.background` rows `over` tier1/tier2).
 *
 * No default border: the mockup's field is recessed by tier alone. The error outline remains.
 *
 * ## [suffix] is not the set rows' unit
 *
 * Set rows carry their unit in `SetColumnHeader`, never here: a suffix takes intrinsic width
 * ahead of the `weight(1f)` value, a priority inversion their measured budget cannot pay
 * (set-field-column-headers.md §5). [suffix] serves the roomier `PlanSetCard` rows (§4 D4).
 */
@Composable
fun AppNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    suffix: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    isRecord: Boolean = false,
    isDone: Boolean = false,
    isLogged: Boolean = false,
    fieldInset: Dp = AppDimension.Space.md,
    accessibilityLabel: String? = null,
    valueSlotProbe: ((slotWidthPx: Int, resolvedStyle: TextStyle) -> Unit)? = null,
) {
    val keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number
    val valueColor by animateColorAsState(
        targetValue = when {
            isRecord -> AppUi.colors.record.textPrimary
            isDone || isLogged -> AppUi.colors.textPrimary
            else -> AppUi.colors.textTertiary
        },
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "field-value",
    )
    val background by animateColorAsState(
        targetValue = when {
            isRecord -> AppUi.colors.record.background
            isDone -> AppUi.colors.donefill
            else -> AppUi.colors.surfaceTier3
        },
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "field-bg",
    )
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    // [fieldInset] is an EXPLICIT choice by the consumer, never a measured-width threshold:
    // a width trigger is calibrated to today's geometry and becomes a silent tripwire under
    // any future width change (set-field-column-headers.md §7a).
    // Set rows pass `SetRowGeometry.compactFieldInset`; everything else keeps Space.md.
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .let { base ->
                if (isError) {
                    base.border(
                        width = AppDimension.borderHairline,
                        color = AppUi.colors.status.error,
                        shape = shape,
                    )
                } else {
                    base
                }
            }
            // heightIn, not height: 48dp is the floor the mockup draws, but at fontScale
            // above ~1.5 the 26sp value's line height exceeds it and a hard height would
            // clip vertically what this component exists to stop clipping horizontally.
            .heightIn(min = AppDimension.heightMd)
            .padding(horizontal = fieldInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // BoxWithConstraints rather than Box: the slot width is a *measured input* — it feeds
        // the rung choice below, and [valueSlotProbe] reports it with the resolved style to the
        // overflow gate (test-only; production never passes it). Overflow cannot be read off the
        // rendered field instead: BasicTextField measures singleLine at infinite width and clips
        // inside its scroll layer (set-field-column-headers.md §6). Alignment gates read this
        // field's left edge from the semantics tree — do not add an `onGloballyPositioned` node
        // here for a test-only capture; it dispatches on every scroll frame of a live session.
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val slotWidthPx = constraints.maxWidth
            val valueStyle = resolveValueStyle(value = value, slotWidthPx = slotWidthPx)
            valueSlotProbe?.invoke(slotWidthPx, valueStyle)
            // Aliased before the semantics block: a bare `contentDescription` inside the
            // receiver is a self-assign (the AppExerciseThumb note). A field built on
            // BasicTextField owes its name explicitly — with the unit living in the column
            // header, this label is the only thing telling TalkBack WHICH field this is
            // (set-field-column-headers.md §4 D6).
            val fieldLabel = accessibilityLabel
            BasicTextField(
                modifier = Modifier.semantics {
                    if (fieldLabel != null) contentDescription = fieldLabel
                },
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = valueStyle.copy(color = valueColor),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(AppUi.colors.accent),
            )
        }
        suffix?.let {
            val unitColor by animateColorAsState(
                // The mockup's `.unit` is `--dim` in every state. Measured, that fails on the
                // washes: over a LIFTED card (slab) in dark, `textDim` lands at 4.40 (record
                // wash) / 4.45 (donefill) against the 4.5 an 11sp label owes — the same
                // failure B7 exists to close, one backdrop later. On washed fields the unit
                // promotes one step to `textSecondary`; a completed row brightening as a
                // whole is §1's principle, not a contradiction of it.
                targetValue = if (isRecord || isDone) {
                    AppUi.colors.textSecondary
                } else {
                    AppUi.colors.textDim
                },
                animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
                label = "field-unit",
            )
            Text(
                modifier = Modifier.padding(start = AppDimension.Space.xs),
                text = it,
                // Mono at the 11 rung — the drawn `.unit` family (was a text-family meta).
                style = AppUi.typography.mono.caption,
                color = unitColor,
            )
        }
    }
}

/**
 * The rung the value renders at, decided by MEASUREMENT ahead of layout
 * (set-field-column-headers.md §4 D5): the first ladder rung whose single-line advance
 * fits the slot, through the same text stack that will draw it. A glyph-count heuristic
 * does not belong here — it is open-loop in both directions.
 *
 * Acyclic by construction: [slotWidthPx] is the parent flex split's decision, so the
 * chosen style cannot feed back into the constraint — no reflow loop, no oscillation.
 * `onTextLayout` stays a test oracle only.
 *
 * The ladder floor is contrast-pinned at the 19sp section rung: below ~18.66sp bold a
 * value owes 4.5:1, which the record molten (light theme) and the pending
 * `textTertiary` colours cannot pay — no lower rung may be added. A value that exceeds
 * even the floor overflows into the field's scroll layer rather than shrinking into
 * illegibility; those cells are ledgered (§7).
 */
@Composable
private fun resolveValueStyle(value: String, slotWidthPx: Int): TextStyle {
    val measurer = rememberTextMeasurer()
    val ladder = listOf(AppUi.typography.dataValue, AppUi.typography.numeric.section)
    if (value.isEmpty()) return ladder.first()
    return ladder.firstOrNull { style ->
        val measured = measurer.measure(
            text = AnnotatedString(value),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
        )
        measured.size.width <= slotWidthPx
    } ?: ladder.last()
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppNumberInputPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg)
                .fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                AppDimension.Space.md,
            ),
        ) {
            AppNumberInput(value = "120", onValueChange = {}, suffix = "kg", decimals = 1)
            AppNumberInput(value = "8", onValueChange = {}, suffix = "reps", decimals = 0)
            AppNumberInput(value = "abc", onValueChange = {}, suffix = "kg", isError = true)
            AppNumberInput(value = "142.5", onValueChange = {}, suffix = "kg", isRecord = true)
            AppNumberInput(value = "100", onValueChange = {}, suffix = "kg", isDone = true)
        }
    }
}

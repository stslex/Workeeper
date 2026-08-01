// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The `#s-list` `.row` skeleton — **one drawing, four payloads.**
 *
 * §26 "List row" and `#s-list`'s own hint say it outright: *«Скелет строки один — 88px, линейка
 * снизу, имя и мета-строка, шеврон. Начинки разные: поля у четырёх экранов не совпадают.»* The four
 * are `all-trainings`, `all-exercises`, `archive` and `home`, and this is the skeleton they share:
 * a full-bleed ruled row of at least [AppDimension.rowHeight], a two-line clamped name over a
 * single-line meta, and a trailing region.
 *
 * ## What is shared, and what deliberately is not
 *
 * Everything here is identical in all four instances, byte for byte, before this extraction: the
 * `Column`/`Row` nesting, `heightIn`, the [AppDimension.screenEdge] gutter, the `Space.md` gap, the
 * text column's `weight(1f)` and `Space.xs`, both text styles and colours, both clamps, and the
 * divider. There is **no behaviour flag on this signature** — no `lifted`, no `selecting`, no
 * `clickable` — and that is the finding the extraction rests on rather than a simplification:
 * every difference between the four is a *modifier* or the *content of the trailing region*, so
 * they belong in [rowModifier] and [content], which is what those seams are for.
 *
 * ## Two modifier seams, because the drawn row has two boxes
 *
 * [modifier] reaches the outer `Column` — the row **plus** its rule, which is what a caller animates
 * (`Modifier.animateItem`). [rowModifier] reaches the inner `Row` — the ruled area **without** the
 * rule, which is what a caller lifts, clicks and tags. The distinction is not stylistic: a
 * `liftedSurface` on the outer box paints behind the divider, and a `combinedClickable` there makes
 * the hairline part of the touch target. [rowModifier] lands **before** `heightIn` and the gutter,
 * where every caller's chain sat before the extraction, so the painted region and the touch region
 * are unchanged.
 *
 * ## The meta line arrives formatted, and composing it stays with the screen
 *
 * [meta] is a `String`. Its four producers compose it in three different places — two inside a
 * composable (`pluralStringResource`, `stringResource` per exercise type), one as a pure extension
 * on the ui model (`home`), one in the **mapper** (`archive`, which is the only form assertable
 * without a composition). They differ in token *order* too, by decision: §26 fixes "information
 * first, tags last" and leaves each screen's tokens to that screen. Pulling any of that in here
 * would put display strings and per-feature resources in the kit, which is the boundary
 * `CLAUDE.md` draws for the mapper layer. So the row takes the formatted line and asserts nothing
 * about it; `RecentMetaLineTest` and its siblings stay where they are.
 *
 * ## The rule is drawn with a token the app does not have
 *
 * `#s-list` rules the row with `--hair-s`, and the slot that would take it (`borderSubtle`) is
 * `--hair` — a different value. The row rules with `borderSubtle` until the palette decision lands:
 * a known approximation, recorded as D3 and owed its own PR, not a transcription error. All four
 * instances shipped it; it is one line now instead of four.
 *
 * ## The trailing region is a slot, and archive is why it is not a fixed-width one
 *
 * Three of the four hold the drawn 20px slot ([AppListRowSlot]). `archive` holds two live verbs —
 * a restore button and an overflow — which is `archive-delta.md` §2.1, unresolved and explicitly
 * a §0.1 decision for the owner. Had this component owned the slot width, extraction would have
 * answered that question in a refactor. It owns the *gap* and the alignment; the caller owns what
 * sits in the region.
 *
 * @param name the row's title. Clamped to two lines, ellipsised.
 * @param meta the composed meta line. One line, ellipsised; see the note above on where it is built.
 * @param nameTestTag tag for the name text — per screen, per item.
 * @param metaTestTag tag for the meta text — per screen, per item.
 * @param showDivider the drawn rule. `#s-list` drops it on the last row
 *  (`.frame .row:last-of-type`), so the list does not end on a hairline into empty space.
 * @param modifier the outer box — row **and** rule.
 * @param rowModifier the inner box — lift, click, tag. Applied before the height floor and gutter.
 * @param content the **trailing region**. Named `content` because it is this component's only
 *  composable slot and `ComposableLambdaParameterNaming` requires that of a sole slot; the row's
 *  name and meta are parameters, not slots. Wrap it in [AppListRowSlot] for the drawn 20px slot.
 */
@Composable
fun AppListRow(
    name: String,
    meta: String,
    nameTestTag: String,
    metaTestTag: String,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(rowModifier)
                .heightIn(min = AppDimension.rowHeight)
                .padding(horizontal = AppDimension.screenEdge),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            ) {
                Text(
                    modifier = Modifier.testTag(nameTestTag),
                    text = name,
                    style = AppUi.typography.titleMedium,
                    color = AppUi.colors.textPrimary,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.testTag(metaTestTag),
                    text = meta,
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = AppDimension.borderHairline,
                color = AppUi.colors.borderSubtle,
            )
        }
    }
}

/**
 * The drawn 20px trailing slot, centred and **fixed width**.
 *
 * §26 "Selection mode", as amended: an unselected row in selection mode loses the chevron, not the
 * slot. As first drawn the slot followed its contents and the text column moved with it — measured
 * 338 / 336 / 370px for chevron, check and nothing — which reflowed every row on entering the mode
 * and one row on every toggle, against a two-line clamped name. Holding the width is the fix, so
 * a caller that puts its content straight into [AppListRow]'s trailing region without this box is
 * opting out of it.
 */
@Composable
fun AppListRowSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.width(AppDimension.iconSm),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Two lines, then ellipsis — the drawn `.row .name` clamp. */
private const val NAME_MAX_LINES = 2

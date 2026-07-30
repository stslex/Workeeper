// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import kotlinx.collections.immutable.persistentListOf

/**
 * One row of the trainings list — `pass2d.html` `#s-list` `.row`.
 *
 * ## One skeleton, four payloads
 *
 * §26 "List row": an 88dp ruled row, name clamped to two lines with ellipsis, a single meta line,
 * and a chevron. `min-height` holds every row to one size — neither a long name nor extra tags
 * moves it, which is what lets four different payloads share one skeleton.
 *
 * The row is **full-bleed and ruled**, not an inset card: the drawn `.row` carries
 * `padding:0 var(--gutter)` with no vertical padding and a `border-bottom`, and the list around it
 * has no horizontal padding of its own. The 20px gutter rounds to [AppDimension.screenEdge] on
 * §0.2's own worked example (mockup 20 → 16dp).
 *
 * ## The trailing slot is fixed width, and that is an amendment
 *
 * §26 "Selection mode": unselected rows lose the chevron, **not the slot**. As first drawn the slot
 * followed its contents and the text column moved with it — measured 338 / 336 / 370px for chevron,
 * check and nothing, which reflowed every row on entering selection mode and one row on every
 * toggle, against a two-line clamped name. The slot now holds [SLOT] whatever is in it. The drawn
 * 20px rounds to [AppDimension.iconSm]; the check keeps its heavier 2.2 stroke
 * ([AppIcons.RowCheck]), so the drawn weight difference survives the rounding.
 *
 * ## No leading media, and no chips
 *
 * §26 "Leading media in list rows" is a stated **absence** — `imagePath` and the type icon stay on
 * the detail screen. §26 "Meta-line order" rejects in-row tag chips: `.tag` as drawn does not sit
 * in an 88dp row, and the full set lives in the filter band above the list, so the row **confirms**
 * tags rather than enumerating them. Both shipped here before the extraction; both are gone.
 *
 * ## The divider
 *
 * `--hair-s` has no app token by design, and the slot that would take it (`borderSubtle`) is
 * `--hair`, a different value — recorded as D3 and owed its own palette PR. The row rules with
 * `borderSubtle` until then: a known approximation, not a transcription error.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TrainingRow(
    item: TrainingListItemUi,
    isSelected: Boolean,
    isSelecting: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selected and active share one surface — `--slab` + `--slabtop` — and that is the drawing's
    // own answer rather than a collision left unresolved: the live row carries its running-ness in
    // the meta line («идёт сейчас · 12:04»), which is content and survives selection mode, so a row
    // that is both is still legible as running. The accent ring this row used to keep under
    // selection was solving a problem the meta line already solves.
    val lifted = isSelected || item.isActive
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Called unconditionally and driven by the flag, per its own KDoc: branching at
                // the call site would rebuild the modifier graph on the flip and kill the tween.
                // The drawn resting row has no fill of its own — it sits on `--base` — so the
                // resting colour is transparent rather than the default `surfaceTier1`.
                .liftedSurface(
                    shape = RectangleShape,
                    lifted = lifted,
                    restingColor = Color.Transparent,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
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
                    modifier = Modifier.testTag("AllTrainingsItemName_${item.uuid}"),
                    text = item.name,
                    style = AppUi.typography.titleMedium,
                    color = AppUi.colors.textPrimary,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.testTag("AllTrainingsItemMeta_${item.uuid}"),
                    text = item.metaLine(),
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TrailingSlot(item = item, isSelected = isSelected, isSelecting = isSelecting)
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
 * The fixed-width trailing slot: chevron at rest, check when selected, and **empty** when selection
 * is on and this row is not — a row in that mode leads nowhere and has nothing to promise, so the
 * chevron goes and the slot stays.
 */
@Composable
private fun TrailingSlot(
    item: TrainingListItemUi,
    isSelected: Boolean,
    isSelecting: Boolean,
) {
    Box(
        modifier = Modifier.width(SLOT),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isSelected -> Icon(
                modifier = Modifier
                    .size(SLOT)
                    .testTag("AllTrainingsItemCheck_${item.uuid}"),
                imageVector = AppIcons.RowCheck,
                contentDescription = null,
                tint = AppUi.colors.textPrimary,
            )

            isSelecting -> Unit

            else -> Icon(
                modifier = Modifier.size(SLOT),
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = AppUi.colors.textTertiary,
            )
        }
    }
}

/**
 * §26 "Meta-line order": **information first, tags last.** The line does not wrap, so what
 * truncates is always the tail, and the tail is tags — which is why the order is fixed rather
 * than incidental.
 */
@Composable
private fun TrainingListItemUi.metaLine(): String {
    val count = pluralStringResource(
        R.plurals.feature_all_trainings_exercise_count,
        exerciseCount,
        exerciseCount,
    )
    return (listOf(statusLabel.trim(), count).filter { it.isNotEmpty() } + tags)
        .joinToString(META_SEPARATOR)
}

/** The interpunct the drawing joins meta tokens with. */
private const val META_SEPARATOR = " · "

private const val NAME_MAX_LINES = 2

/** The drawn 20px slot, on the icon ladder. Holds the check; the chevron centres in it. */
private val SLOT = AppDimension.iconSm

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrainingRowPreview() {
    AppTheme {
        Column {
            TrainingRow(
                item = TrainingListItemUi(
                    uuid = "1",
                    name = "Верх (с подтягиваниями)",
                    tags = persistentListOf("грудь", "спина"),
                    exerciseCount = 8,
                    isActive = false,
                    statusLabel = "вчера · 48 мин",
                ),
                isSelected = false,
                isSelecting = false,
                showDivider = true,
                onClick = {},
                onLongPress = {},
            )
            TrainingRow(
                item = TrainingListItemUi(
                    uuid = "2",
                    name = "Ноги и плечи",
                    tags = persistentListOf("ноги"),
                    exerciseCount = 6,
                    isActive = true,
                    statusLabel = "идёт сейчас · 12:04",
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

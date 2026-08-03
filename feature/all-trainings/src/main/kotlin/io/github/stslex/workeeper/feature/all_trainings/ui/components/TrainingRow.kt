// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListRow
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListRowSlot
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.continuityAlphaSpec
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import kotlinx.collections.immutable.persistentListOf

/**
 * One row of the trainings list — `pass2d.html` `#s-list` `.row`.
 *
 * The skeleton is [AppListRow] — 88dp, ruled, two-line name over one meta line, trailing region —
 * and its derivations live there: the gutter, the fixed-width slot's measured reflow, and the
 * `--hair-s` rule. What follows is this payload's own.
 *
 * ## No leading media, and no chips
 *
 * §26 "Leading media in list rows" is a stated **absence** — `imagePath` and the type icon stay on
 * the detail screen. §26 "Meta-line order" rejects in-row tag chips: `.tag` as drawn does not sit
 * in an 88dp row, and the full set lives in the filter band above the list, so the row **confirms**
 * tags rather than enumerating them. Both shipped here before the extraction; both are gone.
 *
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
    AppListRow(
        modifier = modifier,
        // Called unconditionally and driven by the flag, per its own KDoc: branching at the call
        // site would rebuild the modifier graph on the flip and kill the tween. The drawn resting
        // row has no fill of its own — it sits on `--base` — so the resting colour is transparent
        // rather than the default `surfaceTier1`.
        rowModifier = Modifier
            .liftedSurface(
                shape = RectangleShape,
                lifted = lifted,
                restingColor = Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        name = item.name,
        nameTestTag = "AllTrainingsItemName_${item.uuid}",
        meta = item.metaLine(),
        metaTestTag = "AllTrainingsItemMeta_${item.uuid}",
        showDivider = showDivider,
        content = {
            TrailingSlot(item = item, isSelected = isSelected, isSelecting = isSelecting)
        },
    )
}

/**
 * The fixed-width trailing slot: chevron at rest, check when selected, and **empty** when selection
 * is on and this row is not — a row in that mode leads nowhere and has nothing to promise, so the
 * chevron goes and the slot stays.
 *
 * ## The crossfade — §26, continuity motion
 *
 * The chevron and the check used to replace each other between two frames, with no path between
 * them: a glyph the user was looking at became a different glyph, instantly, on a gesture whose
 * whole point is that the row is still the same row. That is the definition of the continuity
 * class — remove the tween and something teleports — so it is not a third §9 moment and it carries
 * no expressive weight.
 *
 * Two properties are deliberate. `using null` **suppresses the size transform**: the slot is
 * already fixed at [SLOT] and an animated container would put back exactly the reflow the amended
 * ledger row removed. And the transition is a **pure alpha crossfade on one shared spec** — no
 * colour is interpolated anywhere in it, which is what keeps the `fadedOut` rule satisfied by
 * construction rather than by care, since the two glyphs carry different tints and lerping between
 * them is precisely the Oklab path §27 measured.
 *
 * Which kind is drawn is [trailingSlotKind]'s decision, not this function's — a picture of the
 * slot cannot see the choice, and now that the choice drives a 260ms transition it is worth less
 * than ever to leave unasserted.
 */
@Composable
private fun TrailingSlot(
    item: TrainingListItemUi,
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
                        .testTag("AllTrainingsItemCheck_${item.uuid}"),
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

/** The drawn 20px glyph, on the icon ladder — the slot's own width is `AppListRowSlot`'s. */
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

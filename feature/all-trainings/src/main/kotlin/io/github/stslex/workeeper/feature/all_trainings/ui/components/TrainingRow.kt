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
 * One row of the trainings list, on the [AppListRow] skeleton. No leading media and no in-row
 * tag chips — the filter band above the list carries the full set (§26).
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
    // Selected and active share one surface — the meta line keeps a live row legible either way.
    val lifted = isSelected || item.isActive
    AppListRow(
        modifier = modifier,
        // GUARD: call unconditionally and drive it by the flag — branching here rebuilds the
        // modifier graph on the flip and kills the tween. Resting colour is transparent here.
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
 * The fixed-width trailing slot: chevron at rest, check when selected, empty while selecting
 * another row. GUARD: `using null` suppresses the size transform, which would reflow every row.
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

/** §26 "Meta-line order": information first, tags last, since the tail is what truncates. */
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

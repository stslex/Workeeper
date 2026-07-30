// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import kotlinx.collections.immutable.persistentListOf

/**
 * One row of the exercises list — `pass2d.html` `#s-list` `.row`, the exercise payload.
 *
 * ## One skeleton, four payloads — this is the second of them
 *
 * `#s-list`'s hint states it outright: "Скелет строки один — 88px, линейка снизу, имя и мета-строка,
 * шеврон. Начинки разные". The skeleton — the 88dp ruled full-bleed row, the two-line clamp, the
 * single non-wrapping meta line and the fixed trailing slot — is `TrainingRow`'s, extracted there
 * and documented there. **It is deliberately not shared code yet**: the two payloads do not line up
 * on a single field (this one carries a type and two counts, that one an `isActive` flag and one), so a
 * shared component would take either a pre-joined string or a union of nullable fields. The
 * all-exercises delta mapping records the field-by-field comparison; extraction waits for a third
 * consumer to say what the shape actually is.
 *
 * ## What this payload does differently, and it is one thing
 *
 * **The type is the meta line's first token, as a word.** `#s-list`'s type frame draws
 * «со весом · 14 сессий · последняя 9 июля · плечи», and its navnote gives the reason rather than
 * leaving it to be inferred: "Ведущего слота у строки нет: миниатюра и иконка типа остаются на
 * детали. Тип поэтому идёт словом и **первым токеном** мета-строки — по тому же правилу «сведения,
 * потом теги», — и обрезается последним, а не первым."
 *
 * So the leading slot going is not a subtraction on its own — it is a move. `ExerciseLeading` drew
 * either a Coil thumb off `imagePath` or a 28dp type tile; both are named in that navnote as
 * belonging to the detail screen, and dropping them without putting the type into the line would
 * lose the type entirely. A side effect worth having: the row is now a pure function of its model
 * and photographs without an image loader.
 *
 * ## The chevron, and why the old comment is cited rather than deleted
 *
 * This row used to carry: *"Chevron is always visible — the filled-card selection visual replaces
 * the checkbox affordance entirely (spec C3)."* Its premise is false twice after this rebuild — the
 * card is gone, and `#s-list`'s selection frame draws an explicit check in the slot. The finding it
 * rests on is the all-trainings mapping's §3.1: the drawing's answer is that an unselected row in
 * selection mode leads nowhere, so it has nothing to promise and loses the chevron. The slot stays.
 *
 * ## `lifted` is selection alone
 *
 * `TrainingRow` lifts on `isSelected || item.isActive` because a training can be running. Nothing on
 * this screen can be, so there is no collision here and none of that reasoning transfers.
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Called unconditionally and driven by the flag, per its own KDoc: branching at the
                // call site would rebuild the modifier graph on the flip and kill the tween. The
                // drawn resting row has no fill of its own — it sits on `--base` — so the resting
                // colour is transparent rather than the default `surfaceTier1`.
                .liftedSurface(
                    shape = RectangleShape,
                    lifted = isSelected,
                    restingColor = Color.Transparent,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .heightIn(min = AppDimension.rowHeight)
                .padding(horizontal = AppDimension.screenEdge)
                .testTag("AllExercisesItem_${item.uuid}"),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            ) {
                Text(
                    modifier = Modifier.testTag("AllExercisesItemName_${item.uuid}"),
                    text = item.name,
                    style = AppUi.typography.titleMedium,
                    color = AppUi.colors.textPrimary,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.testTag("AllExercisesItemMeta_${item.uuid}"),
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
 * is on and this row is not. The width is held whatever is in it — a slot that follows its contents
 * reflows the text column on entering the mode and on every toggle (§26 "Selection mode", as
 * amended).
 */
@Composable
private fun TrailingSlot(
    item: ExerciseUiModel,
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
                    .testTag("AllExercisesItemCheck_${item.uuid}"),
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
 * §26 "Meta-line order": **information first, tags last** — and on this screen the type is the
 * information that goes first of all.
 *
 * `footerLabel` arrives pre-joined from `AllExercisesUiMapper.composeFooterLabel` (sessions, linked
 * trainings, last-trained). The type is prepended and the tags appended, so the composed order is
 * the drawn one. The line does not wrap, so what truncates is always the tail — which is why the
 * type, the one token that must survive, sits at the head.
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

private const val NAME_MAX_LINES = 2

/** The drawn 20px slot, on the icon ladder. Holds the check; the chevron centres in it. */
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

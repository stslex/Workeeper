// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import kotlinx.collections.immutable.persistentListOf
import java.io.File

private const val MAX_INLINE_TAGS = 3
private val LEADING_THUMB_SIZE = 28.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ExerciseRow(
    item: ExerciseUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selected card surfaces the accent-tinted background, replacing the explicit
    // checkbox affordance that v2.4 removes (spec C3). The unselected state uses
    // surfaceTier1 to keep contrast against tier0 page background.
    val targetColor = if (isSelected) {
        AppUi.colors.accentTintedBackground
    } else {
        AppUi.colors.surfaceTier1
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        label = "ExerciseRowBackground",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .testTag("AllExercisesItem_${item.uuid}")
            .padding(
                horizontal = AppDimension.cardPadding,
                vertical = AppDimension.cardPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        ExerciseLeading(item = item)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            Text(
                text = item.name,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
            )
            if (item.tags.isNotEmpty()) {
                ExerciseRowTags(tags = item.tags)
            }
            if (item.footerLabel.isNotEmpty()) {
                Text(
                    text = item.footerLabel,
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.textTertiary,
                )
            }
        }
        // Chevron is always visible — the filled-card selection visual replaces the
        // checkbox affordance entirely (spec C3).
        Icon(
            modifier = Modifier.size(AppDimension.iconSm),
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(
                R.string.feature_all_exercises_chevron_description,
            ),
            tint = AppUi.colors.textTertiary,
        )
    }
}

@Composable
private fun ExerciseLeading(
    item: ExerciseUiModel,
) {
    val path = item.imagePath
    if (path != null) {
        // Capture mtime once per recomposition for cache-busting; do not re-read the file
        // on every recompose. The library tab tolerates a small staleness window — the
        // edit screen does the more aggressive cache-busting.
        val lastModified = remember(path) { File(path).lastModified() }
        AsyncImage(
            modifier = Modifier
                .size(LEADING_THUMB_SIZE)
                .clip(AppUi.shapes.small),
            model = ImageRequest.Builder(LocalContext.current)
                .data("$path?v=$lastModified")
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    } else {
        ExerciseTypeIcon(type = item.type)
    }
}

@Composable
private fun ExerciseRowTags(
    tags: kotlinx.collections.immutable.ImmutableList<String>,
    modifier: Modifier = Modifier,
) {
    val visible = tags.take(MAX_INLINE_TAGS)
    val overflow = tags.size - visible.size
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        visible.forEach { tag -> AppTagChip.Static(label = tag) }
        if (overflow > 0) {
            AppTagChip.Static(
                label = stringResource(
                    R.string.feature_all_exercises_overflow_format,
                    overflow,
                ),
            )
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
private fun ExerciseRowPreview() {
    val sample = listOf(
        ExerciseUiModel(
            uuid = "1",
            name = "Bench press",
            type = ExerciseTypeUiModel.WEIGHTED,
            tags = persistentListOf("Push", "Chest"),
            sessionCount = 12,
            linkedTrainingsCount = 3,
            lastTrainedAt = null,
            footerLabel = "12 sessions · in 3 trainings · last 4d ago",
            imagePath = null,
        ),
        ExerciseUiModel(
            uuid = "2",
            name = "Pull-up",
            type = ExerciseTypeUiModel.WEIGHTLESS,
            tags = persistentListOf("Pull", "Back", "Calisthenics", "Upper"),
            sessionCount = 4,
            linkedTrainingsCount = 1,
            lastTrainedAt = null,
            footerLabel = "4 sessions · in 1 training",
            imagePath = null,
        ),
        ExerciseUiModel(
            uuid = "3",
            name = "Squat",
            type = ExerciseTypeUiModel.WEIGHTED,
            tags = persistentListOf(),
            sessionCount = 0,
            linkedTrainingsCount = 0,
            lastTrainedAt = null,
            footerLabel = "",
            imagePath = null,
        ),
    )
    AppTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppUi.colors.surfaceTier0),
            contentPadding = PaddingValues(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            items(items = sample, key = { it.uuid }) { item ->
                ExerciseRow(
                    item = item,
                    isSelected = item.uuid == "2",
                    onClick = {},
                    onLongPress = {},
                )
            }
        }
    }
}

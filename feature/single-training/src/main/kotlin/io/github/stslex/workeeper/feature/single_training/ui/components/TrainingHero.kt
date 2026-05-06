// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.single_training.mvi.model.TagUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Hero block under the LargeTopAppBar. The training name lives in the app bar (v2.4 E1)
 * so this composable only renders description + tag chips. Skipped entirely when neither
 * is present.
 */
@Composable
internal fun TrainingHero(
    description: String,
    tags: ImmutableList<TagUiModel>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty() && description.isBlank()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TrainingHero"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
            ) {
                tags.forEach { tag -> AppTagChip.Static(label = tag.name) }
            }
        }
        if (description.isNotBlank()) {
            AppCard {
                Text(
                    text = description,
                    style = AppUi.typography.bodyMedium,
                    color = AppUi.colors.textPrimary,
                )
            }
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
private fun TrainingHeroPreview() {
    AppTheme {
        TrainingHero(
            description = "Focus on bench progression. 4 weeks of linear bump.",
            tags = persistentListOf(
                TagUiModel("1", "Push"),
                TagUiModel("2", "Chest"),
                TagUiModel("3", "Triceps"),
            ),
        )
    }
}

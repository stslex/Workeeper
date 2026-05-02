// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.pr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * Personal-record card for the Exercise detail screen. Renders the heaviest set with a
 * relative date label. Becomes the v2.2 chart entry point when [onClick] is non-null —
 * the visual treatment is unchanged.
 */
@Composable
fun PersonalRecordCard(
    displayLabel: String,
    relativeDateLabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val palette = AppUi.colors.record
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(palette.background)
        .border(
            width = AppDimension.Border.small,
            color = palette.border,
            shape = shape,
        )
    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }
    Row(
        modifier = clickableModifier.padding(AppDimension.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        PersonalRecordBadge()
        Spacer(modifier = Modifier.width(AppDimension.Space.xs))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            Text(
                text = displayLabel,
                style = AppUi.typography.titleMedium,
                color = palette.textPrimary,
            )
            Text(
                text = relativeDateLabel,
                style = AppUi.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PersonalRecordCardPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            PersonalRecordCard(
                displayLabel = "105 × 5",
                relativeDateLabel = "12 апр",
            )
            PersonalRecordCard(
                displayLabel = "15 reps",
                relativeDateLabel = "вчера",
            )
        }
    }
}

package io.github.stslex.workeeper.core.ui.kit.components.segmented

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun AppSegmentedControl(
    items: ImmutableList<String>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(AppDimension.heightXs)
            .clip(AppUi.shapes.small)
            .background(AppUi.colors.surfaceTier1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.borderHairline),
    ) {
        items.forEachIndexed { index, label ->
            val isSelected = index == selected
            val background = if (isSelected) AppUi.colors.accentTintedBackground else AppUi.colors.surfaceTier1
            val foreground = if (isSelected) AppUi.colors.accentTintedForeground else AppUi.colors.textTertiary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(AppUi.shapes.small)
                    .background(background)
                    .clickable { onSelectedChange(index) }
                    .padding(horizontal = AppDimension.Space.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = AppUi.typography.labelMedium,
                    color = foreground,
                )
            }
            if (index != items.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(AppDimension.borderHairline)
                        .fillMaxHeight()
                        .background(AppUi.colors.borderSubtle),
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppSegmentedControlPreview() {
    AppTheme {
        var selected by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            AppSegmentedControl(
                items = persistentListOf("Trainings", "Exercises"),
                selected = selected,
                onSelectedChange = { selected = it },
            )
        }
    }
}

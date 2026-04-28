package io.github.stslex.workeeper.core.ui.kit.components.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

object AppTagChip {

    @Composable
    fun Static(
        label: String,
        modifier: Modifier = Modifier,
    ) {
        ChipShell(modifier = modifier) {
            ChipLabel(label)
        }
    }

    @Composable
    fun Selectable(
        label: String,
        selected: Boolean,
        onSelectedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        ChipShell(
            modifier = modifier.clickable { onSelectedChange(!selected) },
            selected = selected,
        ) {
            ChipLabel(label, selected = selected)
        }
    }

    @Composable
    fun Removable(
        label: String,
        onRemove: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        ChipShell(modifier = modifier) {
            ChipLabel(label)
            Icon(
                modifier = Modifier
                    .size(AppDimension.iconXs)
                    .clickable(onClick = onRemove),
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = AppUi.colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun ChipShell(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val background = if (selected) AppUi.colors.accentTintedBackground else AppUi.colors.surfaceTier4
    Row(
        modifier = modifier
            .clip(AppUi.shapes.small)
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        content()
    }
}

@Composable
internal fun ChipLabel(
    label: String,
    selected: Boolean = false,
) {
    val color = if (selected) AppUi.colors.accentTintedForeground else AppUi.colors.textSecondary
    Text(text = label, style = AppUi.typography.labelSmall, color = color)
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppTagChipPreview() {
    AppTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs)) {
                AppTagChip.Static(label = "Push")
                AppTagChip.Selectable(label = "Selected", selected = true, onSelectedChange = {})
                AppTagChip.Selectable(label = "Idle", selected = false, onSelectedChange = {})
                AppTagChip.Removable(label = "Pull", onRemove = {})
            }
        }
    }
}

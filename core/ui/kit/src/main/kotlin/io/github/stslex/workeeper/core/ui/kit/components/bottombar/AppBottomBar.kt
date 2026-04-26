package io.github.stslex.workeeper.core.ui.kit.components.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppBottomBar(
    currentDestination: AppBottomBarDestination?,
    onDestinationChange: (AppBottomBarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier
            .background(AppUi.colors.surfaceTier0)
            .border(width = AppDimension.borderHairline, color = AppUi.colors.borderSubtle),
        containerColor = AppUi.colors.surfaceTier0,
    ) {
        AppBottomBarDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination == destination,
                onClick = { onDestinationChange(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(text = destination.label, style = AppUi.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppUi.colors.accent,
                    selectedTextColor = AppUi.colors.accent,
                    indicatorColor = AppUi.colors.accentTintedBackground,
                    unselectedIconColor = AppUi.colors.textTertiary,
                    unselectedTextColor = AppUi.colors.textTertiary,
                ),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppBottomBarPreview() {
    AppTheme {
        AppBottomBar(
            currentDestination = AppBottomBarDestination.TRAININGS,
            onDestinationChange = {},
        )
    }
}

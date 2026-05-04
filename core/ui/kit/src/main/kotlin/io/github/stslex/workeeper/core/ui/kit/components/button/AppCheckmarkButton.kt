package io.github.stslex.workeeper.core.ui.kit.components.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppCheckmarkButton(
    isDone: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = AppUi.colors.accent
    val transparent = Color.Transparent
    val borderColor = if (enabled) accent else AppUi.colors.borderDefault
    val fillColor by animateColorAsState(
        targetValue = if (isDone && enabled) accent else transparent,
        label = "AppCheckmarkButtonFill",
    )
    val iconTint by animateColorAsState(
        targetValue = if (isDone) AppUi.colors.onAccent else transparent,
        label = "AppCheckmarkButtonIcon",
    )
    val state = if (isDone) ToggleableState.On else ToggleableState.Off

    Box(
        modifier = modifier
            .size(TOUCH_SIZE.dp)
            .padding(TOUCH_PADDING.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .semantics {
                toggleableState = state
                stateDescription = if (isDone) "completed" else "pending"
            }
            .background(fillColor, CircleShape)
            .border(width = BORDER_WIDTH.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ICON_SIZE.dp),
            imageVector = Icons.Filled.Check,
            tint = iconTint,
            contentDescription = null,
        )
    }
}

private const val TOUCH_SIZE = 48
private const val TOUCH_PADDING = 6
private const val BORDER_WIDTH = 2
private const val ICON_SIZE = 20

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppCheckmarkButtonPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier1)
                .padding(AppDimension.Space.lg),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppCheckmarkButton(isDone = false, enabled = true, onToggle = {})
            AppCheckmarkButton(isDone = true, enabled = true, onToggle = {})
            AppCheckmarkButton(isDone = false, enabled = false, onToggle = {})
            AppCheckmarkButton(isDone = true, enabled = false, onToggle = {})
        }
    }
}

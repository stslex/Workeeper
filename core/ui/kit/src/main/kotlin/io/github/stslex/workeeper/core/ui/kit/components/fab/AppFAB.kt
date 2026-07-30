package io.github.stslex.workeeper.core.ui.kit.components.fab

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppFAB(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppUi.colors.accent,
    contentColor: Color = AppUi.colors.onAccent,
    cornerRadius: Dp = AppDimension.Radius.medium,
) {
    // The morph is shape only. `#s-list` opens the squircle into a circle — 18px into 28px on a
    // 56px button, i.e. exactly half its size — over `260ms var(--e-spring)`, and changes nothing
    // else: the fill stays `--max` and the content `--base` through the whole transition. Colour
    // was carrying a claim the action does not make (§26, "FAB in selection mode"). The overshoot
    // is legal here because a corner radius encodes no value (§26, overshoot row).
    val animatedRadius by animateDpAsState(
        targetValue = cornerRadius,
        animationSpec = tween(
            durationMillis = AppUi.motion.base,
            easing = AppUi.motion.spring,
        ),
        label = "AppFABCorner",
    )
    val animatedContainer by animateColorAsState(
        targetValue = containerColor,
        label = "AppFABContainer",
    )
    val animatedContent by animateColorAsState(
        targetValue = contentColor,
        label = "AppFABContent",
    )
    FloatingActionButton(
        modifier = modifier.size(56.dp),
        onClick = onClick,
        containerColor = animatedContainer,
        contentColor = animatedContent,
        shape = RoundedCornerShape(animatedRadius),
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
    ) {
        Icon(
            modifier = Modifier.size(AppDimension.iconMd),
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppFABPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            AppFAB(
                icon = Icons.Default.Add,
                contentDescription = "Create",
                onClick = {},
            )
        }
    }
}

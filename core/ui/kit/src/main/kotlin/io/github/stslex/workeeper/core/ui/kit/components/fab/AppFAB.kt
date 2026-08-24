package io.github.stslex.workeeper.core.ui.kit.components.fab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.continuityAlphaSpec

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
    // The selection-mode morph is shape only: the corner radius opens the squircle into a circle.
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
    val glyphSpec = continuityAlphaSpec<Float>()
    FloatingActionButton(
        modifier = modifier
            .size(56.dp)
            // GUARD: the description belongs on the button, not the glyphs — the crossfade
            // composes two Icons at once and per-glyph descriptions would merge into one node.
            .semantics { contentDescription?.let { this.contentDescription = it } },
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
        // GUARD: `using null` suppresses AnimatedContent's size transform — both glyphs are
        // iconMd, and an animated container would reflow what the fixed size exists to prevent.
        AnimatedContent(
            targetState = icon,
            transitionSpec = { fadeIn(glyphSpec) togetherWith fadeOut(glyphSpec) using null },
            contentAlignment = Alignment.Center,
            label = "AppFABGlyph",
        ) { vector ->
            Icon(
                modifier = Modifier.size(AppDimension.iconMd),
                imageVector = vector,
                contentDescription = null,
            )
        }
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

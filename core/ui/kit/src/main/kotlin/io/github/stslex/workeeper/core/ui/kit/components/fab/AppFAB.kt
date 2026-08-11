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
    val glyphSpec = continuityAlphaSpec<Float>()
    FloatingActionButton(
        modifier = modifier
            .size(56.dp)
            // The description lives on the BUTTON, not on the glyph, and that is a consequence of
            // the crossfade below rather than a preference: for 260ms two `Icon`s are composed at
            // once, and a description on each would merge into a node announcing both. One stable
            // node whose label changes when the parameter does; the glyphs are decoration.
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
        // §26 continuity motion, and this site is the class's own definition failing inside one
        // component. The radius above interpolates across the full 260ms while the glyph changed
        // between two adjacent frames — measured on device, `+` at full opacity in frame N and the
        // archive box at full opacity in frame N+1, in both directions — so half of this button
        // travelled and half teleported, at the same instant, on the same gesture.
        //
        // It is a transit by the membership test (delete the animation and something jumps) and it
        // carries no character, so it takes the class's alpha spec and needs no ledger row of its
        // own. Alpha, therefore `continuityAlphaSpec` — the split is by what is interpolated, and
        // the radius above is the same component making the other choice for the other reason.
        // `using null` suppresses the size transform: both glyphs are [AppDimension.iconMd], and an
        // animated container would introduce a reflow the fixed size exists to prevent.
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

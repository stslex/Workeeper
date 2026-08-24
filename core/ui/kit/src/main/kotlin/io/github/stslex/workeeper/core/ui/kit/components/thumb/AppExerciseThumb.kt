// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.thumb

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.border.dashedBorder
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 `.thumb`: an exercise's picture at [AppDimension.iconXl], drawing the exercise type
 * mark when empty. A null [onClick] draws a box that is not a control. See the extraction spec.
 */
@Composable
fun AppExerciseThumb(
    isWeighted: Boolean,
    onClick: (() -> Unit)?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    val hasImage = content != null
    // GUARD: inside `semantics {}` a bare `contentDescription` is the receiver's own property,
    // so writing it straight from the parameter is a silent self-assign. Alias it first.
    val label = contentDescription
    // The drawn box IS the touch target: iconXl is the 48dp minimum, and `clickable` adds none.
    Box(
        modifier = modifier
            .size(AppDimension.iconXl)
            .clip(shape)
            .let { base ->
                if (hasImage) {
                    base
                        .background(
                            Brush.linearGradient(
                                listOf(AppUi.colors.surfaceTier4, AppUi.colors.surfaceTier1),
                            ),
                        )
                        .border(AppDimension.Border.small, AppUi.colors.borderDefault, shape)
                } else {
                    base
                        .background(AppUi.colors.surfaceTier3)
                        .dashedBorder(
                            color = AppUi.colors.borderDefault,
                            cornerRadius = AppDimension.Radius.small,
                        )
                }
            }
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClickLabel = contentDescription, onClick = onClick)
                        .semantics { role = Role.Button }
                } else {
                    Modifier.semantics { this.contentDescription = label }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Icon(
                modifier = Modifier.size(THUMB_GLYPH),
                imageVector = if (isWeighted) {
                    AppIcons.ExerciseWeighted
                } else {
                    AppIcons.ExerciseWeightless
                },
                // Decorative: the box is the control and carries the click label.
                contentDescription = null,
                tint = AppUi.colors.textDim,
            )
        }
    }
}

/** `.thumb svg{width:21px}` — the same literal `AppTopBar`'s own glyph keeps. */
private val THUMB_GLYPH: Dp = 21.dp

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppExerciseThumbPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            AppExerciseThumb(isWeighted = true, onClick = {}, contentDescription = "Add image")
            AppExerciseThumb(isWeighted = false, onClick = {}, contentDescription = "Add image")
            AppExerciseThumb(isWeighted = true, onClick = {}, contentDescription = "Open image") {}
        }
    }
}

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
 * The v3 `.thumb` — an exercise's picture, 48dp, in the pushed top bar beside the title
 * (`pass2d.html` `#s-editor` form 5; extraction §7.7). **NEW.**
 *
 * ```css
 * .thumb{width:44px;height:44px;border-radius:12px;border:1px solid var(--hair-s);
 *        background:var(--field)}
 * .thumb.has{background:linear-gradient(135deg,var(--raise),var(--sec))}
 * .thumb.none{border-style:dashed}
 * .thumb svg{width:21px;height:21px;stroke-width:1.7}
 * ```
 *
 * ## Why it is in the bar and not in the form
 *
 * It costs the form **no vertical space at all** and gives the record a face at the top of every
 * screen that shows it. The picture's two verbs are not here: §26 puts them where the picture is,
 * so with an image the tap opens the full-screen viewer and replace/remove live in it.
 *
 * ## The empty state draws the TYPE
 *
 * With no photo the box draws the exercise's `type` mark. **Rejected: a camera glyph** — it
 * promises one of the two things the picker sheet offers, and it erases the type. Nothing is
 * drawn inside the FILLED thumb either: a camera in a box that already holds a photo reads as
 * "take another".
 *
 * Geometry, derived (§0.2): the drawn 44px is `.icon-btn`'s own box, and it takes `.icon-btn`'s
 * rung — **48dp** ([AppDimension.iconXl]), by §0.5's `44 / 46 / 48 → 48dp` row. **The thumb is a
 * control in the bar, so it takes the bar's control size rather than one of its own**; landing on
 * `AppIconButton`'s box is the point, not a collision to avoid. That also makes the drawn box the
 * minimum interactive target, so the gesture sits on it directly and nothing is added around it.
 * Radius 12 → 8dp (`Radius.small`), the E7 rounding every site makes. Glyph 21 → `iconLg`'s
 * neighbour is 32 and `iconMd` is 24, so the literal is kept for the same reason `AppTopBar`'s own
 * 21dp glyph is.
 *
 * The border is `borderDefault`, not `borderSubtle`: dashed or solid, this outline **is** the
 * control — it is the only thing that draws the target at all — so WCAG 1.4.11 applies at 3:1.
 * The same discriminator `AppDashedAddButton` states, and the same one that keeps
 * `AppEmptyState`'s decorative tile on `borderSubtle`.
 *
 * The picture slot is the trailing lambda ([content]) rather than an `image:` parameter, so the
 * empty state is `null` and Compose's own naming rule is satisfied — the box is the component and
 * the picture is what goes in it.
 */
@Composable
fun AppExerciseThumb(
    isWeighted: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    val hasImage = content != null
    // The drawn box IS the touch target: [AppDimension.iconXl] is both `.icon-btn`'s rung and the
    // 48dp minimum, so a foundation `clickable` — which gets none of `IconButton`'s target
    // expansion — needs nothing around it here. Shrink this below the rung and the hit area
    // shrinks with it, on the only image affordance the screen has.
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
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .semantics { role = Role.Button },
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
                // Decorative: the box is the control and it carries [contentDescription] as its
                // click label. Describing the mark here too would announce the exercise's TYPE
                // where the user needs to hear the ACTION, and one control cannot say both.
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

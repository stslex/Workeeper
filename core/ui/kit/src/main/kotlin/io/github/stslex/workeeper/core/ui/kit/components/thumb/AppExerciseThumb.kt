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
 * The v3 `.thumb` — an exercise's picture, 46dp, in the pushed top bar beside the title
 * (`pass2d.html` `#s-editor` form 5; extraction §7.7). **NEW.**
 *
 * ```css
 * .thumb{width:46px;height:46px;border-radius:12px;border:1px solid var(--hair-s);
 *        background:var(--field)}
 * .thumb.has{background:linear-gradient(135deg,var(--raise),var(--sec))}
 * .thumb.none{border-style:dashed}
 * .thumb svg{width:21px;height:21px;stroke-width:1.7}
 * ```
 *
 * ## What it replaces, and why the form row goes
 *
 * The image used to be a 72dp thumb plus two buttons in a row of the form. Moving it into the bar
 * costs the form **no vertical space at all** and gives the record a face at the top of every
 * screen that shows it. What is lost is the two buttons, and §26 puts them where the picture is:
 * with an image, the tap opens the full-screen viewer and replace/remove live there.
 *
 * ## The empty state draws the TYPE, and that relationship is not invented here
 *
 * `ImageEditRow.ThumbPlaceholder` already took the exercise's `type` and drew it when there was no
 * photo. The ruling keeps that and moves the container. **Rejected: a camera glyph** — it promises
 * one of the two things the picker sheet offers, and it erases the type. Nothing is drawn inside
 * the FILLED thumb either: a camera in a box that already holds a photo reads as "take another".
 *
 * Geometry, derived (§0.2): 46px is kept **literal**, like the other component treatments in this
 * file. It is not on the `height*` ladder, and rounding it to 48 would make it exactly
 * `AppIconButton`'s box — two different objects at one size, in the same bar, is worse than a
 * 2dp irregularity. Radius 12 → 8dp (`Radius.small`), the E7 rounding every site makes. Glyph
 * 21 → `iconLg`'s neighbour is 32 and `iconMd` is 24, so the literal is kept for the same reason
 * `AppTopBar`'s own 21dp glyph is.
 *
 * The border is `borderDefault`, not `borderSubtle`: dashed or solid, this outline **is** the
 * control — it is the only thing bounding a tappable 46dp box — so WCAG 1.4.11 applies at 3:1.
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
    Box(
        modifier = modifier
            .size(THUMB_SIZE)
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
                // Labelled by the caller's own click target, not here: the mark says which TYPE
                // the exercise is, and the action is "choose a picture" — one description cannot
                // be both, so the caller owns the button's.
                contentDescription = null,
                tint = AppUi.colors.textDim,
            )
        }
    }
}

/** `.thumb{width:46px}` — kept literal; see the KDoc for why it is not rounded onto the ladder. */
private val THUMB_SIZE: Dp = 46.dp

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

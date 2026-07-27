// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.empty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The empty-state pattern: **glyph, title, one sentence, then nothing / one button / two.**
 *
 * ## The sentence says what to do next
 *
 * It does not narrate emptiness. "No exercises yet" is already the title; a body that repeats it in
 * longer words spends the one line the user will actually read on information they have. The body's
 * job is the next action — "Tap + to create your first exercise". Where there genuinely is no next
 * action (the archive: nothing to create, only things that arrive by being archived) the sentence
 * explains what the surface is *for*, which is still forward-looking. Copy is the caller's, but
 * this is the contract the component is shaped around.
 *
 * ## Two actions
 *
 * [actionLabel] is the primary action and [secondaryActionLabel] the alternative. They stack
 * vertically, primary first, because the buttons are full-width at this width and a side-by-side
 * pair would put the two at the same weight — which is the one thing a primary/secondary pair must
 * not do. The mockup stacks them for the same reason (`pass2d.html:183`, `.empty .btns` is
 * `flex-direction:column`).
 *
 * ## The label-without-handler behaviour is deliberate and load-bearing
 *
 * A button renders only when **both** its label and its handler are non-null. Two callers already
 * depend on this to switch an action off by nulling one side — `ChartEmptyState.kt:41` passes
 * `ctaLabel.takeIf { onCta != null }`, and `LiveWorkoutScreen.kt:313` passes both through
 * `.takeIf { isAddEnabled }`. Changing it to render a dead button, or to throw, breaks both.
 */
@Composable
fun AppEmptyState(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = AppDimension.Space.xxxl,
                horizontal = AppDimension.Space.xl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm, Alignment.CenterVertically),
    ) {
        icon?.let { image ->
            EmptyStateGlyph(icon = image)
            Spacer(modifier = Modifier.height(AppDimension.Space.xs))
        }
        Text(
            text = headline,
            style = AppUi.typography.text.section,
            color = AppUi.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        supportingText?.let { text ->
            Text(
                modifier = Modifier.widthIn(max = SENTENCE_MAX_WIDTH),
                text = text,
                style = AppUi.typography.text.body,
                color = AppUi.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        EmptyStateActions(
            actionLabel = actionLabel,
            onAction = onAction,
            secondaryActionLabel = secondaryActionLabel,
            onSecondaryAction = onSecondaryAction,
        )
    }
}

/**
 * The glyph, in its dashed tile.
 *
 * The tile is what stops a lone 22.dp icon reading as a mis-sized button on an otherwise empty
 * screen — the mockup draws it as a dashed-outline square (`pass2d.html:179`). The outline is
 * decorative reinforcement around an icon that is itself decorative, so it takes `borderSubtle`
 * and no contrast threshold; the glyph carries no meaning the title does not already state, which
 * is also why `contentDescription` is null.
 */
@Composable
private fun EmptyStateGlyph(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .size(GLYPH_TILE)
            .border(
                width = AppDimension.borderHairline,
                color = AppUi.colors.borderSubtle,
                shape = RoundedCornerShape(AppDimension.Radius.medium),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            modifier = Modifier.size(AppDimension.iconMd),
            imageVector = icon,
            contentDescription = null,
            tint = AppUi.colors.textTertiary,
        )
    }
}

/** Zero, one or two stacked actions. Renders nothing at all when there are none. */
@Composable
private fun EmptyStateActions(
    actionLabel: String?,
    onAction: (() -> Unit)?,
    secondaryActionLabel: String?,
    onSecondaryAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val primary = actionLabel?.let { label -> onAction?.let { handler -> label to handler } }
    val secondary = secondaryActionLabel
        ?.let { label -> onSecondaryAction?.let { handler -> label to handler } }
    if (primary == null && secondary == null) return

    Spacer(modifier = Modifier.height(AppDimension.Space.xs))
    Column(
        modifier = modifier.widthIn(max = ACTIONS_MAX_WIDTH),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        primary?.let { (label, handler) ->
            AppButton.Primary(
                modifier = Modifier.fillMaxWidth(),
                text = label,
                onClick = handler,
                size = AppButtonSize.MEDIUM,
            )
        }
        secondary?.let { (label, handler) ->
            AppButton.Tertiary(
                modifier = Modifier.fillMaxWidth(),
                text = label,
                onClick = handler,
                size = AppButtonSize.MEDIUM,
            )
        }
    }
}

/**
 * The sentence is capped so it breaks into two or three short lines rather than running the full
 * width of a tablet. The mockup caps it at 274px (`pass2d.html:182`); 272.dp is the same measure
 * rounded onto the 8.dp grid. A measurement, not a spacing token — there is no ladder step here
 * and pretending otherwise would put a fake name on an arbitrary number.
 */
private val SENTENCE_MAX_WIDTH = 272.dp

/** The action stack's cap. The mockup's `.empty .btns` is 288px (`pass2d.html:183`). */
private val ACTIONS_MAX_WIDTH = 288.dp

/** The tile around the glyph — 52px in the mockup (`pass2d.html:179`), [AppDimension.iconXl] here. */
private val GLYPH_TILE = AppDimension.iconXl

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppEmptyStatePreview() {
    AppTheme {
        Column(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            AppEmptyState(
                headline = "No exercises yet",
                supportingText = "Tap + to create your first exercise.",
                icon = Icons.Default.SearchOff,
                actionLabel = "Add exercise",
                onAction = {},
                secondaryActionLabel = "Browse the library",
                onSecondaryAction = {},
            )
        }
    }
}

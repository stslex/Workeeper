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
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES

/**
 * The empty-state pattern: glyph, title, one sentence, then nothing / one button / two.
 * GUARD: a button renders only when BOTH its label and handler are non-null; callers rely on it.
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
 * The glyph, in its tile; the tile stops a lone icon reading as a mis-sized button. The outline
 * is decorative, so it takes `borderSubtle` and no contrast threshold.
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

/** Caps the sentence to two or three short lines; a measurement, not a spacing token. */
private val SENTENCE_MAX_WIDTH = 272.dp

/** The action stack's cap. The mockup's `.empty .btns` is 288px (`pass2d.html:183`). */
private val ACTIONS_MAX_WIDTH = 288.dp

/** The tile around the glyph — 52px in the mockup, [AppDimension.iconXl] here. */
private val GLYPH_TILE = AppDimension.iconXl

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = PREVIEW_UI_MODE_NIGHT_YES,
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

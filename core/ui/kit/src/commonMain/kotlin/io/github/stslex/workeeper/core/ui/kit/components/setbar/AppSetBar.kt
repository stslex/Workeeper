// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.setbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES

/**
 * The v3 `.setbar`: add and remove a set in the foot of the card that holds them, so a set row
 * carries no delete of its own. See documentation/feature-specs/screen-extraction.md §7.5.
 */
@Composable
fun AppSetBar(
    addLabel: String,
    removeLabel: String,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    removeEnabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            SetBarAction(
                modifier = Modifier
                    .weight(1f)
                    .testTag("AppSetBarAdd"),
                label = addLabel,
                onClick = onAdd,
                enabled = true,
            )
            Box(
                modifier = Modifier
                    .width(AppDimension.Border.small)
                    .height(AppDimension.heightMd)
                    .background(AppUi.colors.borderSubtle),
            )
            SetBarAction(
                modifier = Modifier
                    .weight(1f)
                    .testTag("AppSetBarRemove"),
                label = removeLabel,
                onClick = onRemove,
                enabled = removeEnabled,
            )
        }
    }
}

@Composable
private fun SetBarAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(AppDimension.heightMd)
            // The role rides on the shared modifier so add and remove cannot drift apart on it.
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = AppUi.typography.mono.meta.copy(letterSpacing = SETBAR_TRACKING),
            color = AppUi.colors.textTertiary.let {
                if (enabled) it else it.copy(alpha = DISABLED_LABEL_ALPHA)
            },
        )
    }
}

/** `.setbar button{letter-spacing:.06em}` at the 12.5 rung — a component treatment. */
private val SETBAR_TRACKING = 0.06.em

/** `.setbar button:disabled{opacity:.35}` — the drawn number, not a role. */
private const val DISABLED_LABEL_ALPHA = 0.35f

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = PREVIEW_UI_MODE_NIGHT_YES)
@Composable
private fun AppSetBarPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier1)
                .padding(vertical = AppDimension.Space.lg),
        ) {
            AppSetBar(
                addLabel = "+ подход",
                removeLabel = "− подход",
                onAdd = {},
                onRemove = {},
            )
            AppSetBar(
                addLabel = "+ подход",
                removeLabel = "− подход",
                onAdd = {},
                onRemove = {},
                removeEnabled = false,
            )
        }
    }
}

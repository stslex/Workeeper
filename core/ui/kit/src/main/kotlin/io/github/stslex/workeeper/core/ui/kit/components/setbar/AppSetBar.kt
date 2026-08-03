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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 `.setbar` — add and remove a set, in the foot of the card that holds them
 * (`session-v3f.html` L137–141, copied into `pass2d.html` `#s-editor`; extraction §7.5).
 *
 * ```css
 * .setbar{display:flex;border-top:1px solid var(--hair)}
 * .setbar button{flex:1;color:var(--meta);font-family:var(--ff-mono);font-size:12px;
 *                letter-spacing:.06em;text-transform:uppercase;padding:15px 0 14px}
 * .setbar button+button{border-left:1px solid var(--hair)}
 * .setbar button:disabled{opacity:.35}
 * ```
 *
 * **One pair of opposite actions, in one place.** The foot puts add and remove side by side where
 * the list ends, which is where the list grows — a set row carries no `✕` of its own, and no host
 * draws an add button outside the body.
 *
 * Geometry, derived rather than transcribed (§0.2): the drawn `15px 0 14px` around a ~16px line
 * is **45px**, and each half takes [AppDimension.heightMd] (48dp) with its label centred — the
 * asymmetric pair is a 1px optical nudge that does not survive the ladder, and a rung is a better
 * thing to own than a rounding. The rules are `--hair` → `borderSubtle`: they separate, they carry
 * no state, and §3.1 puts them outside the contrast contract.
 *
 * The disabled label is the drawn `opacity:.35` applied to `textTertiary` rather than a palette
 * role, because there is no "disabled label" slot and inventing one for a value the drawing states
 * directly would be adding a role to avoid writing a number down. WCAG carves disabled controls
 * out, so nothing is owed here.
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
            .clickable(enabled = enabled, onClick = onClick),
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

/** `.setbar button{letter-spacing:.06em}` at the 12.5 rung — a component treatment, as B4 rules. */
private val SETBAR_TRACKING = 0.06.em

/** `.setbar button:disabled{opacity:.35}` — the drawn number, not a role. */
private const val DISABLED_LABEL_ALPHA = 0.35f

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
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

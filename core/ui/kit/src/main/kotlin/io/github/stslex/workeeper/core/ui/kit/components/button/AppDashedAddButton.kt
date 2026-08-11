// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.border.dashedBorder
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 `.addex` — a full-width dashed tile that adds a thing to the list above it
 * (`session-v3f.html` L143–145, copied into `pass2d.html` `#s-editor`; extraction §7.6).
 *
 * ```css
 * .addex{display:flex;align-items:center;justify-content:center;gap:9px;height:60px;
 *        margin:12px var(--gutter) 0;border:1px dashed var(--hair-s);border-radius:16px;
 *        color:var(--meta);font-family:var(--ff-ui);font-size:15px}
 * .addex svg{width:17px;height:17px;stroke-width:1.9}
 * ```
 *
 * Geometry, derived (§0.2): height 60 → **56dp** ([AppDimension.heightLg]) — the same rung
 * `.topbar` and `.nb` put it on, because one drawn value should not have several dp answers.
 * Radius 16 → `Radius.medium`, exactly. Label 15px
 * → the body rung. Gap 9 → `Space.sm`. Glyph 17 → `iconSm` (18), at [AppIcons.Plus], which already
 * carries this component's own 1.9 stroke — `ADDEX_STROKE` is named after it.
 *
 * **The border is `borderDefault`, not `borderSubtle`, and the discriminator is not thickness.**
 * `AppEmptyState`'s dashed tile takes `borderSubtle` because it is decorative reinforcement around
 * an icon that is itself decorative. Here the dashed outline **is the button** — remove it and
 * there is no control on the screen, only a label — so WCAG 1.4.11 applies at 3:1 and the slot is
 * the control-outline one, exactly as `AppTextField`'s. `--hair-s` cannot pay that (1.12–1.52),
 * which is the reroute B19 records.
 */
@Composable
fun AppDashedAddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimension.heightLg)
            .clip(shape)
            .dashedBorder(
                color = AppUi.colors.borderDefault,
                cornerRadius = AppDimension.Radius.medium,
            )
            // `Role.Button` is the control type, and a foundation `clickable` supplies none:
            // without it TalkBack announces the app's only add-exercise affordance as a generic
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(
            space = AppDimension.Space.sm,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(AppDimension.iconSm),
            imageVector = AppIcons.Plus,
            contentDescription = null,
            tint = AppUi.colors.textTertiary,
        )
        Text(
            text = text,
            style = AppUi.typography.text.body,
            color = AppUi.colors.textTertiary,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppDashedAddButtonPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.screenEdge),
        ) {
            AppDashedAddButton(text = "Добавить упражнение", onClick = {})
        }
    }
}

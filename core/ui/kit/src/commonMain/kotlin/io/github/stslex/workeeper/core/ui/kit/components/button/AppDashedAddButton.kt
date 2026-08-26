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
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES

/**
 * The v3 `.addex`: a full-width dashed tile that adds a thing to the list above it. The dashed
 * outline IS the control, so it takes `borderDefault` at 3:1, not `AppEmptyState`'s subtle one.
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
            // Role.Button: a foundation `clickable` supplies no control type to TalkBack.
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
@Preview(name = "Dark", showBackground = true, uiMode = PREVIEW_UI_MODE_NIGHT_YES)
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

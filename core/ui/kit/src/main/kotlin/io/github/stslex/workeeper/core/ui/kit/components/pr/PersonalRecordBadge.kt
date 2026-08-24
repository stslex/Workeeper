// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.pr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

private const val PR_LABEL = "PR"

/**
 * Compact pill flagging a personal record, sized to sit inside a row without adding height.
 * [onClick] makes it a tap target (typically the PR explainer); `null` leaves it decorative.
 */
@Composable
fun PersonalRecordBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val palette = AppUi.colors.record
    val baseModifier = modifier
        .height(18.dp)
        .clip(RoundedCornerShape(AppDimension.Radius.smallest))
    val tappableModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }
    Row(
        modifier = tappableModifier
            .background(palette.solid)
            .padding(horizontal = AppDimension.Space.sm, vertical = AppDimension.Space.xxs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = PR_LABEL,
            color = palette.onSolid,
            style = AppUi.typography.labelSmall.copy(letterSpacing = 0.6.sp),
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PersonalRecordBadgePreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            PersonalRecordBadge()
        }
    }
}

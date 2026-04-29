// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.pr

import androidx.compose.foundation.background
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
 * Compact amber pill that flags a personal record. Used in Live workout set rows, Past
 * session set rows, and the finish dialog header. Visual intentionally small enough to sit
 * inside a row without forcing extra height.
 */
@Composable
fun PersonalRecordBadge(modifier: Modifier = Modifier) {
    val palette = AppUi.colors.record
    Row(
        modifier = modifier
            .height(18.dp)
            .clip(RoundedCornerShape(AppDimension.Radius.smallest))
            .background(palette.border)
            .padding(horizontal = AppDimension.Space.sm, vertical = AppDimension.Space.xxs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = PR_LABEL,
            color = AppUi.colors.onAccent,
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

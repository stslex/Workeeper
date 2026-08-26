// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES

/**
 * The one surface that reads as "this is what is being done now"; a wrapper over [liftedSurface]
 * carrying the semantics. GUARD: `ActiveSurfaceSingleReaderRule` permits exactly one call site.
 */
@Composable
fun AppActiveSurface(
    active: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liftedSurface(shape = shape, lifted = active),
        content = content,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = PREVIEW_UI_MODE_NIGHT_YES,
)
@Composable
private fun AppActiveSurfacePreview() {
    AppTheme {
        AppActiveSurface(
            active = true,
            shape = AppUi.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimension.screenEdge),
        ) {
            Text(
                modifier = Modifier.padding(AppDimension.screenEdge),
                text = "Bench press",
                style = AppUi.typography.text.body,
                color = AppUi.colors.textPrimary,
            )
        }
    }
}

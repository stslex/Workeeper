// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 `.flabel` — a field's own label, **above** the box (extraction §7.2).
 *
 * ```css
 * .flabel{font-family:var(--ff-ui);font-size:13px;color:var(--dim);margin:0 0 6px 2px}
 * ```
 *
 * 13px → the **12.5 rung** (`text.meta`), `--dim` → `textDim`, and the 2px left offset is
 * `Space.xxs` exactly. The 6px gap below is **not** applied here — the caller's own
 * `Arrangement.spacedBy` owns the distance to the field, the way every form section in the app
 * already spaces its parts, and baking a margin into the label would give two owners one number.
 *
 * It exists because M3's floating label is drawn nowhere in either mockup. [AppTextField] used to
 * take a `label` parameter and render M3's; that parameter is gone, and this is where the drawn
 * treatment lives instead — one implementation, so the three editors and the finish sheet cannot
 * describe the same object four ways.
 */
@Composable
fun AppFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.padding(start = AppDimension.Space.xxs),
        text = text,
        style = AppUi.typography.text.meta,
        color = AppUi.colors.textDim,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppFieldLabelPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            AppFieldLabel(text = "Название")
            AppTextField(value = "Жим лёжа", onValueChange = {})
        }
    }
}

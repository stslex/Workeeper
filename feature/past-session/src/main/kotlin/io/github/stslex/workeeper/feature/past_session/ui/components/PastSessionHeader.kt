// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.section.AppLabel
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import kotlinx.collections.immutable.persistentListOf

/**
 * The v3 header block (extraction §2.3): `.label` eyebrow, `.data-hero` duration, `.meta`
 * summary line — three texts directly on the page under the gutter. **This is not a card**;
 * `pass2d.html:296-300` draws no container, and the v2.4 `AppCard` wrapper is exactly the
 * treatment the rebuild retires.
 *
 * - Eyebrow: `Завершена · 23 июля 2026` in the `.label` treatment ([AppLabel] — uppercase
 *   mono caption, `textDim`; casing is the style's job, not the string's).
 * - Duration: `.data-hero` is drawn at 44px, off the six-step scale; §0.2 says the scale
 *   wins, so it lands on `numeric.display` (34 rung) — digits and a colon only, which is
 *   what the C2 charset rule permits. `margin-top:10px` → 8dp.
 * - Summary: `.meta` — mono 12.5 in `textTertiary`, carrying the §11.1 tonnage as its third
 *   term when the session lifted anything. `margin-top:10px` → 8dp.
 */
@Composable
internal fun PastSessionHeader(
    detail: PastSessionUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppLabel(
            text = stringResource(
                R.string.feature_past_session_finished_at_label,
                detail.finishedAtAbsoluteLabel,
            ),
        )
        Text(
            modifier = Modifier.padding(top = AppDimension.Space.sm),
            text = detail.durationLabel,
            style = AppUi.typography.numeric.display,
            color = AppUi.colors.textPrimary,
        )
        Text(
            modifier = Modifier.padding(top = AppDimension.Space.sm),
            text = detail.totalsLabel,
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textTertiary,
        )
    }
}

@Preview(name = "Light")
@Composable
private fun PastSessionHeaderLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PastSessionHeader(detail = stubDetail())
    }
}

@Preview(name = "Dark")
@Composable
private fun PastSessionHeaderDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSessionHeader(detail = stubDetail())
    }
}

private fun stubDetail(): PastSessionUiModel = PastSessionUiModel(
    trainingName = "низ — 2",
    isAdhoc = false,
    finishedAtAbsoluteLabel = "23 July 2026",
    durationLabel = "56:08",
    totalsLabel = "5 exercises · 14 sets · 4,820 kg",
    exercises = persistentListOf(),
)

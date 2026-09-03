// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_back
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_save
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_active_session_conflict_delete_and_start
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_empty_hint
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_set_type_drop
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_set_type_failure
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_set_type_warmup
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_set_type_work
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_subtitle
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_title_default
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_pr_explainer_body
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_pr_explainer_title
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.jetbrains.compose.resources.stringResource
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Mechanical guard on the no-Cyrillic display face: real `values-ru` strings across every type
 * slot, so applying a face without Cyrillic glyphs turns them to tofu and moves the pixels.
 */
internal class CyrillicTextGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun cyrillicText(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) { CyrillicSpecimen() }
    }
}

@Composable
private fun CyrillicSpecimen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.core_ui_kit_plan_editor_title_default),
            style = AppUi.typography.headlineMedium,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.core_ui_kit_pr_explainer_title),
            style = AppUi.typography.titleLarge,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.core_ui_kit_plan_editor_subtitle),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
        Text(
            text = stringResource(Res.string.core_ui_kit_pr_explainer_body),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
        Text(
            text = stringResource(Res.string.core_ui_kit_plan_editor_empty_hint),
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textSecondary,
        )
        Text(
            text = listOf(
                stringResource(Res.string.core_ui_kit_plan_editor_set_type_warmup),
                stringResource(Res.string.core_ui_kit_plan_editor_set_type_work),
                stringResource(Res.string.core_ui_kit_plan_editor_set_type_failure),
                stringResource(Res.string.core_ui_kit_plan_editor_set_type_drop),
            ).joinToString(" · "),
            style = AppUi.typography.labelLarge,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = listOf(
                stringResource(Res.string.core_ui_kit_action_save),
                stringResource(Res.string.core_ui_kit_action_cancel),
                stringResource(Res.string.core_ui_kit_action_back),
            ).joinToString(" · "),
            style = AppUi.typography.labelMedium,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.core_ui_kit_active_session_conflict_delete_and_start),
            style = AppUi.typography.bodyLarge,
            color = AppUi.colors.textPrimary,
        )
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.setbar.AppSetBar
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The set-type marks in both languages, plus the set bar. GUARD: only the RU frame can fail on
 * a hardcoded set-type letter — at the default locale a literal and a resource render alike.
 */
internal class SetTypeMarkGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setTypeMarksEn(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_EN) { Marks() }
    }

    /** The frame the English one cannot stand in for: Р / О / Д, and the bar's own labels. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setTypeMarksRu(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Marks() }
    }
}

@Composable
private fun Marks() {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetType.entries.forEach { AppSetTypeChip(it) }
        }
        AppSetBar(
            addLabel = stringResource(R.string.core_ui_kit_setbar_add),
            removeLabel = stringResource(R.string.core_ui_kit_setbar_remove),
            onAdd = {},
            onRemove = {},
        )
        // The disabled half, beside the enabled pair — alone in a frame it would show no step.
        AppSetBar(
            addLabel = stringResource(R.string.core_ui_kit_setbar_add),
            removeLabel = stringResource(R.string.core_ui_kit_setbar_remove),
            onAdd = {},
            onRemove = {},
            removeEnabled = false,
        )
    }
}

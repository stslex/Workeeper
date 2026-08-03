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
 * The four `.tchip` marks, **in both languages**, and the `.setbar` beneath them.
 *
 * §26 "Set types take their first letter" rules the marks as **the first letter of each type's own
 * name in the current language** — Р / О / Д against W / F / D — and that is a claim no
 * single-locale picture can make. These four marks shipped as HARDCODED ENGLISH LITERALS inside
 * `AppSetTypeChip`, so a Russian build drew `W` for разминка and nothing in the repo could see it:
 * every golden renders at the harness's default `en`, where the literal and the resource agree.
 *
 * So the Russian frame is not a nicety, it is the only frame that can fail on the defect the
 * ruling names, and the English one beside it is what makes the pair a comparison rather than two
 * pictures. `CyrillicTextGoldenTest` establishes the `LOCALE_RU` idiom; this uses it for a
 * behaviour rather than for a script.
 *
 * The bar is in the same frames because its labels localise too («+ подход» / «+ set») and because
 * the drawn `opacity:.35` disabled half has no other instrument — a handler test cannot see an
 * alpha.
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
        // The empty-draft foot: «− подход» at the drawn `opacity:.35`, beside the enabled pair
        // above it, because a disabled control alone in a frame shows no step.
        AppSetBar(
            addLabel = stringResource(R.string.core_ui_kit_setbar_add),
            removeLabel = stringResource(R.string.core_ui_kit_setbar_remove),
            onAdd = {},
            onRemove = {},
            removeEnabled = false,
        )
    }
}

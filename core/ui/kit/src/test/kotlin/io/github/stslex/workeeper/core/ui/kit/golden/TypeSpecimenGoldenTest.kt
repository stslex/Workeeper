// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppTypeStyles
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The whole scale on one page: six steps, three families.
 *
 * A type scale reviewed as a diff of numbers is not reviewed. This makes it a picture, so the
 * six rungs can be judged against each other — and against the next step's mockups — at a
 * glance. It is also the golden most likely to expose render nondeterminism, since it carries
 * more glyphs than anything else in the set.
 *
 * **The Archivo column is digits only.** That is O2, not an aesthetic choice: the family has
 * no Cyrillic coverage, so a specimen that fed it words would be modelling a violation.
 */
internal class TypeSpecimenGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun typeSpecimen(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) { Specimen() }
    }
}

@Composable
private fun Specimen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FamilyBlock("IBM Plex Sans — text", AppUi.typography.text, WORDS_AND_DIGITS)
        FamilyBlock("Archivo Expanded — numeric", AppUi.typography.numeric, DIGITS_ONLY)
        FamilyBlock("IBM Plex Mono — mono", AppUi.typography.mono, WORDS_AND_DIGITS)
    }
}

@Composable
private fun FamilyBlock(
    heading: String,
    styles: AppTypeStyles,
    sample: String,
) {
    Text(
        text = heading,
        style = AppUi.typography.labelMedium,
        color = AppUi.colors.textSecondary,
        modifier = Modifier.padding(top = 12.dp),
    )
    styles.rungs().forEach { (name, style) ->
        Text(
            text = "$name  $sample",
            style = style,
            color = AppUi.colors.textPrimary,
        )
    }
}

private fun AppTypeStyles.rungs(): List<Pair<String, TextStyle>> = listOf(
    "34" to display,
    "26" to title,
    "19" to section,
    "15" to body,
    "12.5" to meta,
    "11" to caption,
)

private const val WORDS_AND_DIGITS = "Squat 120"
private const val DIGITS_ONLY = "0123456789"

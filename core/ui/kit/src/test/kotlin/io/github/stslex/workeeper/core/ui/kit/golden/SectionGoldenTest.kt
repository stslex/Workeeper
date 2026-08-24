// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSection
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionRow
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The section structure, in three goldens rather than one so a regression in the header, the
 * two-label head or the row heights cannot hide inside another's noise.
 */
internal class SectionGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sectionWithHeader(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            AppSection(label = "Appearance") {
                row { AppSectionRow(title = "Theme", supporting = "System") }
                row { AppSectionRow(title = "Units", supporting = "Kilograms") }
                row { AppSectionRow(title = "Language", supporting = "English") }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sectionWithBothLabels(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            AppSection(label = "History", trailingLabel = "4 sessions") {
                row { AppSectionRow(title = "Upper body", supporting = "23 July - 48 min") }
                row { AppSectionRow(title = "Legs", supporting = "21 July - 55 min") }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun rowHeights(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { RowHeightSpecimen() }
    }
}

/**
 * The row's three heights stacked: single-line, title plus supporting, and a title long enough
 * to wrap - the wrapped row must be exactly as tall as the two-line row above it.
 */
@Composable
private fun RowHeightSpecimen() {
    Column(modifier = Modifier) {
        AppSection {
            row { AppSectionRow(title = "Single line, no supporting text") }
            row { AppSectionRow(title = "Bench press", supporting = "5 x 80 kg - 3 days ago") }
            row {
                AppSectionRow(
                    title = "A title long enough that it has to wrap onto a second line",
                    supporting = "12 sessions",
                )
            }
        }
    }
}

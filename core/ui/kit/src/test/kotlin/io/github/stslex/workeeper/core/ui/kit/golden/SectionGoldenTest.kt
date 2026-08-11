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
 * The section, which is the structure the whole step rests on.
 *
 * Three separate goldens rather than one, because they answer three different questions and a
 * single combined image would let a regression in one hide inside the noise of another:
 *
 *  - [sectionWithHeader] — air above, a single label, rows, hairlines **between** rows only. The
 *    thing to look for is the absence of a rule above the first row and below the last: section
 *    separation is carried by the gutter and the label, not by a line.
 *  - [sectionWithBothLabels] — the two-label head. Left and right are the same rung, baseline
 *    aligned, with the right one carrying a count.
 *  - [rowHeights] — the two row heights side by side, so the 88.dp / 64.dp split is visible as a
 *    geometric fact rather than asserted in a comment.
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
 * The row's three states of height, stacked: single-line (64.dp), title plus supporting (88.dp),
 * and a title long enough to wrap to the second line the 88.dp was derived for.
 *
 * The third is the one that matters. If the arithmetic in `AppDimension.rowHeight` is right, the
 * wrapped row is exactly as tall as the two-line row above it — the height was chosen to hold two
 * lines, so reaching two lines must not grow it.
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

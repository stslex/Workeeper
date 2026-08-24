// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagFormRow
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagPickerSheetContent
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The tag picker's two surfaces: the in-form row and the sheet content. RU locale throughout —
 * the default `en` frame cannot fail on a Russian-only defect.
 */
internal class TagPickerGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun formRowEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            Padded {
                AppTagFormRow(
                    selectedTags = persistentListOf(),
                    onTagRemove = {},
                    onAddClick = {},
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun formRowWithTags(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            Padded {
                AppTagFormRow(
                    selectedTags = persistentListOf(
                        AppTagItem(uuid = "t1", name = "верх"),
                        AppTagItem(uuid = "t2", name = "спина"),
                        AppTagItem(uuid = "t3", name = "грудь"),
                    ),
                    onTagRemove = {},
                    onAddClick = {},
                )
            }
        }
    }

    /** «бицепс» prefixed by the query: chips filter, and no create row — an exact match exists. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetQueryMatching(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU, surface = { AppUi.colors.surfaceTier3 }) {
            Padded {
                AppTagPickerSheetContent(
                    selectedTagUuids = persistentSetOf("t1"),
                    availableTags = dictionary(),
                    searchQuery = "бицепс",
                    onSearchQueryChange = {},
                    onTagToggle = {},
                    onTagCreate = {},
                    onDone = {},
                )
            }
        }
    }

    /** No match at all: the dictionary row disappears and «+ Создать «кардио»» appears. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetQueryNotMatching(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU, surface = { AppUi.colors.surfaceTier3 }) {
            Padded {
                AppTagPickerSheetContent(
                    selectedTagUuids = persistentSetOf(),
                    availableTags = dictionary(),
                    searchQuery = "кардио",
                    onSearchQueryChange = {},
                    onTagToggle = {},
                    onTagCreate = {},
                    onDone = {},
                )
            }
        }
    }

    private fun dictionary() = persistentListOf(
        AppTagItem(uuid = "t1", name = "бицепс"),
        AppTagItem(uuid = "t2", name = "спина"),
        AppTagItem(uuid = "t3", name = "грудь"),
    )
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.padding(AppDimension.Space.lg),
    ) {
        content()
    }
}

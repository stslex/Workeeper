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
 * ED7's two surfaces — the ONE tag picker the two editors used to duplicate:
 *
 *  - [formRowEmpty] / [formRowWithTags] — the in-form row: selected chips, each with `✕`,
 *    and the dashed «+ тег» chip. The dash is D-OPEN-5's kept outline (the label identifies
 *    the control; the dash owes no threshold), painted `borderDefault` — `--hair-s`'s
 *    control-outline reroute (B19), matching `.addex`. Empty is not a decoration: it is what
 *    a fresh record's form shows, and the dashed chip standing alone is the whole affordance.
 *  - [sheetQueryMatching] / [sheetQueryNotMatching] — the sheet's drawing (the window is
 *    `AppBottomSheet` at the call site, outside Paparazzi's one-window model): search over
 *    the dictionary as selectable chips, and the «+ Создать «X»» row appearing EXACTLY when
 *    no exact match exists — the pair is the difference assertion.
 *
 * Russian, deliberately: «+ тег», «Поиск тегов…», «+ Создать «X»» and «Готово» are the
 * given RU copy verbatim, and the default `en` frame cannot fail on a Russian-only defect.
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

/** Air only — the backdrop is `goldenSubject`'s own `surface` (`tier0` form, `tier3` sheet). */
@Composable
private fun Padded(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.padding(AppDimension.Space.lg),
    ) {
        content()
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

/**
 * Gate G2 of the Wear controller redesign spec §7: no Wear source references
 * `dynamicColorScheme`, and the colour values reaching the composition are the fixed palette of
 * spec §3, asserted against the spec's literal values so palette drift cannot self-certify.
 *
 * Red when `dynamicColorScheme` is reintroduced in `WearAppTheme`.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@OptIn(ExperimentalTestApi::class)
internal class WearThemeGateTest {

    @Test
    @DisplayName("no dynamic theming in main sources, and the §3 palette reaches the composition")
    fun fixedPaletteReachesTheCompositionAndNoDynamicThemingRemains() {
        assertNoMainSourceReferencesDynamicColorScheme()

        var scheme: ColorScheme? = null
        var contentColor: Color? = null
        runComposeUiTest {
            setContent {
                WearAppTheme {
                    scheme = MaterialTheme.colorScheme
                    contentColor = LocalContentColor.current
                }
            }
            waitForIdle()
        }
        val captured = requireNotNull(scheme) { "The theme content never composed." }

        assertEquals(Color(SCREEN), captured.background, "background must be §3 `screen`")
        assertEquals(Color(TEXT_PRIMARY), captured.onBackground, "onBackground must be §3 `textPrimary`")
        assertEquals(Color(TEXT_PRIMARY), captured.primary, "primary must be §3 `textPrimary` (D-C)")
        assertEquals(Color(ON_ACCENT), captured.onPrimary, "onPrimary must be §3 `onAccent`")
        assertEquals(Color(CARD), captured.surfaceContainer, "surfaceContainer must be §3 `card`")
        assertEquals(
            Color(CARD_INACTIVE),
            captured.surfaceContainerLow,
            "surfaceContainerLow must be §3 `cardInactive`",
        )
        assertEquals(
            Color(PILL_PENDING),
            captured.surfaceContainerHigh,
            "surfaceContainerHigh must be §3 `pillPending`",
        )
        assertEquals(Color(TEXT_PRIMARY), captured.onSurface, "onSurface must be §3 `textPrimary`")
        assertEquals(Color(TEXT_SECONDARY), captured.onSurfaceVariant, "onSurfaceVariant must be §3 `textSecondary`")
        assertEquals(Color(STROKE), captured.outline, "outline must be §3 `stroke`")
        assertEquals(Color(ERROR), captured.error, "error must be §3 `error`")
        assertEquals(Color(TEXT_PRIMARY), contentColor, "the default content colour must be §3 `textPrimary`")

        val palette = setOf(
            Color(SCREEN), Color(CARD), Color(CARD_INACTIVE), Color(PILL_PENDING),
            Color(TEXT_PRIMARY), Color(TEXT_SECONDARY), Color(TEXT_MUTED), Color(STROKE),
            Color(ON_ACCENT), Color(ERROR),
        )
        val strays = captured.allSlots().filterValues { it !in palette }
        assertTrue(
            strays.isEmpty(),
            "Colour slot(s) outside the ten-role §3 palette reach the composition: $strays",
        )
    }

    /**
     * The scan half of the gate: the composition capture cannot see a `dynamicColorScheme`
     * call, because off a watch face it returns null and falls back to exactly the fixed
     * scheme being asserted. Reading the source is the honest oracle for "no reference".
     */
    private fun assertNoMainSourceReferencesDynamicColorScheme() {
        val mainSources = File("src/main/kotlin")
        assertTrue(
            mainSources.isDirectory,
            "Expected the Wear module's main sources at ${mainSources.absolutePath}; " +
                "the gate must fail rather than pass over nothing.",
        )
        val offenders = mainSources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("dynamicColorScheme") }
            .map { it.path }
            .toList()
        assertTrue(offenders.isEmpty(), "dynamicColorScheme is referenced by: $offenders")
    }

    private fun ColorScheme.allSlots(): Map<String, Color> = mapOf(
        "primary" to primary,
        "primaryDim" to primaryDim,
        "primaryContainer" to primaryContainer,
        "onPrimary" to onPrimary,
        "onPrimaryContainer" to onPrimaryContainer,
        "secondary" to secondary,
        "secondaryDim" to secondaryDim,
        "secondaryContainer" to secondaryContainer,
        "onSecondary" to onSecondary,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "tertiaryDim" to tertiaryDim,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiary" to onTertiary,
        "onTertiaryContainer" to onTertiaryContainer,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "onSurface" to onSurface,
        "onSurfaceVariant" to onSurfaceVariant,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "background" to background,
        "onBackground" to onBackground,
        "error" to error,
        "errorDim" to errorDim,
        "errorContainer" to errorContainer,
        "onError" to onError,
        "onErrorContainer" to onErrorContainer,
    )

    private companion object {
        const val SCREEN: Long = 0xFF000000
        const val CARD: Long = 0xFF1E242A
        const val CARD_INACTIVE: Long = 0xFF0B0D0F
        const val PILL_PENDING: Long = 0xFF242B32
        const val TEXT_PRIMARY: Long = 0xFFF1F5F9
        const val TEXT_SECONDARY: Long = 0xFFB7C0CA
        const val TEXT_MUTED: Long = 0xFF8B95A1
        const val STROKE: Long = 0xFF627587
        const val ON_ACCENT: Long = 0xFF0B0D0F
        const val ERROR: Long = 0xFFDF714B
    }
}

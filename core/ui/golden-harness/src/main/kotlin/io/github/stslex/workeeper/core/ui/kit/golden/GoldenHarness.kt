// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import com.android.ide.common.rendering.api.SessionParams
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.jupiter.api.TestInfo

/*
 * Screenshot-golden harness for the v3 redesign, driven from Jupiter (no junit-vintage engine).
 * Dialogs, sheets, menus and tooltips are out of model. See documentation/testing.md.
 */

/** Default resource locale. The Cyrillic golden overrides it to [LOCALE_RU]. */
const val LOCALE_EN: String = "en"

/** Resolves `values-ru`, so the Cyrillic golden renders shipped translations, not literals. */
const val LOCALE_RU: String = "ru"

/**
 * Pixel 5, measured: 1080x2340 at 440 dpi, i.e. 2.75 physical pixels per dp. `fontScale` is
 * pinned rather than inherited — another default would re-flow every string in every golden.
 */
val GOLDEN_DEVICE: DeviceConfig = DeviceConfig.PIXEL_5.copy(
    fontScale = 1.0f,
    locale = LOCALE_EN,
    softButtons = false,
)

/**
 * Records or verifies one golden. `maxPercentDifference = 0.0` is load-bearing: a flake is a
 * finding about render nondeterminism, never a reason to raise it. See documentation/testing.md.
 */
fun golden(
    testInfo: TestInfo,
    theme: GoldenTheme,
    locale: String = LOCALE_EN,
    content: @Composable () -> Unit,
) {
    val paparazzi = Paparazzi(
        deviceConfig = GOLDEN_DEVICE.copy(locale = locale),
        theme = theme.windowTheme,
        maxPercentDifference = 0.0,
        // Native resolution: layoutlib snaps hairlines to whole pixels, so no resampling blur.
        useDeviceResolution = true,
    )
    paparazzi.setup(testInfo.toTestName())
    registerComposeResourcesContext(paparazzi.context)
    try {
        // The theme rides in the snapshot name, so one @ParameterizedTest covers both.
        paparazzi.snapshot(name = theme.suffix) {
            AppTheme(themeMode = theme.themeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppUi.colors.surfaceTier0),
                ) {
                    content()
                }
            }
        }
    } finally {
        paparazzi.teardown()
    }
}

/**
 * Records or verifies one golden on a canvas cut to the subject (`RenderingMode.SHRINK`), pinned
 * to [SUBJECT_WIDTH] so wrapping and trailing-slot alignment still mean something. [surface] is
 * the tier the subject is supposed to sit on.
 */
fun goldenSubject(
    testInfo: TestInfo,
    theme: GoldenTheme,
    locale: String = LOCALE_EN,
    surface: @Composable () -> Color = { AppUi.colors.surfaceTier0 },
    content: @Composable () -> Unit,
) {
    val paparazzi = Paparazzi(
        deviceConfig = GOLDEN_DEVICE.copy(locale = locale),
        theme = theme.windowTheme,
        maxPercentDifference = 0.0,
        renderingMode = SessionParams.RenderingMode.SHRINK,
        useDeviceResolution = true,
    )
    paparazzi.setup(testInfo.toTestName())
    registerComposeResourcesContext(paparazzi.context)
    try {
        paparazzi.snapshot(name = theme.suffix) {
            AppTheme(themeMode = theme.themeMode) {
                Box(
                    modifier = Modifier
                        .width(SUBJECT_WIDTH)
                        .wrapContentHeight()
                        .background(surface()),
                ) {
                    content()
                }
            }
        }
    } finally {
        paparazzi.teardown()
    }
}

/**
 * Hands the layoutlib `Context` to Compose Multiplatform resources. GUARD: without it a KMP
 * module's `Res.string` read falls through to the instrumented-test reader and dies with
 * "No instrumentation registered" — the app wires this through a content provider, which
 * Paparazzi never runs. Idempotent; harmless for classic modules, whose Android `R` strings
 * do not consult it. Call after every Paparazzi/PaparazziSdk setup.
 */
@OptIn(ExperimentalResourceApi::class)
internal fun registerComposeResourcesContext(context: android.content.Context) {
    setResourceReaderAndroidContext(context)
}

/**
 * The width a subject-sized golden renders at: the nearest dp landing on a whole physical pixel
 * of the golden device, so no sub-pixel column flakes at `maxPercentDifference = 0.0`.
 */
private val SUBJECT_WIDTH: Dp = 392.dp

/**
 * Golden file names are `<package>_<Class>_<method>_<snapshot name>.png`.
 * GUARD: `Class.getPackage()`, not the API-31 `getPackageName()` — Lint scopes this at minSdk 28.
 */
private fun TestInfo.toTestName(): TestName {
    val testClass = testClass.orElseThrow { IllegalStateException("golden() needs a test class") }
    val testMethod = testMethod.orElseThrow { IllegalStateException("golden() needs a test method") }
    return TestName(
        packageName = testClass.`package`?.name.orEmpty(),
        className = testClass.simpleName,
        methodName = testMethod.name,
    )
}

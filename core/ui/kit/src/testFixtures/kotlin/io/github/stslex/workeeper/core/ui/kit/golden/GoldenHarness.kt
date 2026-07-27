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
import org.junit.jupiter.api.TestInfo

/*
 * Screenshot-golden harness for the v3 redesign.
 *
 * ## Why this exists
 *
 * Goldens recorded here are the *before* picture. Every later commit on the redesign branch
 * re-records them, and the reviewer reads the image diff rather than a hundred-file hex diff.
 * They are disposable on purpose. Re-record with:
 *
 *     ./gradlew :core:ui:kit:recordPaparazziDebug
 *
 * and commit the result. A golden change must be intentional and explained in the commit body;
 * an unexplained golden delta is a review stop.
 *
 * ## JUnit path: Jupiter, not Vintage
 *
 * Paparazzi 2.0.0-alpha05 exposes `setup(TestName)` / `teardown()` as public members that do
 * not touch a single JUnit 4 type — `setup` builds the `PaparazziSdk`, calls its `setup()` and
 * `prepare()`, and stores the test name; `teardown()` tears the SDK down and closes the
 * snapshot handler. The `TestRule` implementation on the same class is simply another caller
 * of those two. Driving them from Jupiter's `@Test` therefore needs no `junit-vintage-engine`,
 * and none is on the classpath.
 *
 * That is not only tidiness. On the JUnit 4 path a missing Vintage engine let
 * `verifyPaparazziDebug` exit 0 having executed zero screenshot tests. Dropping the Jupiter
 * engine instead fails loudly ("Cannot create Launcher without at least one TestEngine"),
 * because it is the only engine present. It does not close the hole in general — a task-level
 * test filter still produces a silent zero-test pass — which is why
 * `:core:ui:kit:assertGoldenLiveness` exists in the build script.
 *
 * ## Backgrounds
 *
 * Every golden paints `AppUi.colors.surfaceTier0` across the whole frame. Without that paint
 * the visible background comes from Paparazzi's `theme` parameter, not from `AppTheme` — and a
 * dark and a light golden would then share a background, leaving the actual theme difference
 * unverified. Deliberate, not decoration.
 *
 * ## Out of model — do not snapshot
 *
 * `Dialog`, `ModalBottomSheet`, `DropdownMenu`, `DatePickerDialog` and `TooltipBox` render in
 * their own windows; Paparazzi models a single window. Those sites stay on manual
 * verification by design.
 */

/** Default resource locale. The Cyrillic golden overrides it to [LOCALE_RU]. */
const val LOCALE_EN: String = "en"

/** Resolves `values-ru`, so the Cyrillic golden renders shipped translations, not literals. */
const val LOCALE_RU: String = "ru"

/**
 * Pixel 5 — measured, not assumed: 1080×2340, 440 dpi (`xdpi = 442`, `ydpi = 444`),
 * `fontScale = 1.0`, no soft buttons. 440 dpi means **2.75 physical pixels per dp**, so a
 * `1.dp` rule is 2.75 px and a `0.5.dp` rule is 1.375 px — neither lands on a pixel boundary,
 * which is what makes the hairline canary worth having.
 *
 * `fontScale` is pinned rather than inherited: a machine defaulting to anything else would
 * silently re-flow every string in every golden.
 */
val GOLDEN_DEVICE: DeviceConfig = DeviceConfig.PIXEL_5.copy(
    fontScale = 1.0f,
    locale = LOCALE_EN,
    softButtons = false,
)

/**
 * Records or verifies one golden.
 *
 * `maxPercentDifference` is set to `0.0` explicitly even though that is the library default.
 * The number is load-bearing: adding a whole glyph to a golden moved 0.031% of the frame, so
 * the commonly copied `0.1` would have waved that mutation through. If a golden ever flakes,
 * that is a finding about render nondeterminism to be reported — never a reason to raise this.
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
        // Record at native 1080×2340 instead of letting Paparazzi write a scaled image.
        // Measured difference, on the hairline canary: scaled, a 1.dp rule landed across 2–4
        // rows at four different intensities (#E3E3DF, #EFEFEC, …) — resampling blur, not
        // rendering. At device resolution the same rule is 3 rows of one flat #E0E0DC and the
        // 0.5.dp rule is exactly 1 row. Layoutlib snaps hairlines to whole pixels; the blur
        // was entirely an artefact of the downscale. Keeping it native removes a whole class
        // of resampling noise from every future golden, which matters at a 0.0 tolerance.
        // Cost is 216 KB for the six goldens.
        useDeviceResolution = true,
    )
    paparazzi.setup(testInfo.toTestName())
    try {
        // The theme rides in the snapshot name rather than the method name, so one
        // `@ParameterizedTest` covers both and neither can overwrite the other's golden.
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
 * Records or verifies one golden on a canvas cut to the **subject**, not to a phone.
 *
 * [golden] paints `fillMaxSize()`, so every image it writes is the full 1080x2340 frame. For a
 * whole screen that is the right frame. For a single row it is 97% flat background — bytes that go
 * into git on every re-record and that a reviewer has to scroll past to find the 88.dp under
 * review.
 *
 * `RenderingMode.SHRINK` makes layoutlib size the canvas to the content instead. Width is pinned
 * to [SUBJECT_WIDTH] rather than left to shrink too, because these are full-bleed components whose
 * text wrapping and trailing-slot alignment only mean anything at a realistic phone width — a row
 * shrunk to its own intrinsic width would prove nothing about either.
 *
 * The trade is that the subject no longer sits on a painted `surfaceTier0` frame, so the
 * background arrives from the theme. That is why the content is wrapped in a background of the
 * surface it is *supposed* to sit on, passed by the caller as [surface]: a section on the page and
 * a sheet layout on the sheet's own tier are different pictures, and the golden should say which.
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
 * The width a subject-sized golden is rendered at.
 *
 * The golden device is 1080 px at 440 dpi, i.e. 2.75 px/dp, i.e. 392.7 dp wide. 392.dp is the
 * nearest value that lands on a **whole physical pixel** (1078 px) — within half a dp of the real
 * device, and free of the sub-pixel column a fractional width would introduce at the right edge.
 * At `maxPercentDifference = 0.0` that column would be a permanent flake risk for no benefit.
 */
private val SUBJECT_WIDTH: Dp = 392.dp

/**
 * Golden file names are `<package>_<Class>_<method>_<snapshot name>.png`.
 *
 * Reads the package via `Class.getPackage()` rather than `Class.getPackageName()`. The latter
 * is API 31 and this file lives in a `testFixtures` source set, which Android Lint scopes at
 * the module's minSdk of 28 — even though the code only ever runs on the host JVM under
 * Paparazzi. Using the API-1 call is a fix rather than a suppression, and the two return the
 * same string for any class loaded from a named package.
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

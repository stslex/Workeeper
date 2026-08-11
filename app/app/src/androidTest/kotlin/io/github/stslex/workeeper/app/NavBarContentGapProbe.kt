package io.github.stslex.workeeper.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.harness.MetroTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The controlled pair §24 says is the only thing that has ever settled the content/bar seam.
 *
 * `AppNavigationHost` pads every bottom-bar destination by the bare
 * `AppDimension.BottomNavBar.height` and then applies `systemBarsPadding()`; the bar itself takes
 * `heightWithInsets`. Whether those two land flush is a **modifier-order** claim, and modifier-order
 * claims are the class this arc has been wrong about seven for seven (§0.3) — so it is instrumented
 * rather than reasoned about, and it is re-run after every change to that height.
 *
 * The pair discriminates by varying **one** thing: the navigation mode. If the two paddings ever
 * stop tracking each other, the gap differs between the two rows; a single reading cannot tell a
 * flush seam from two errors that happen to cancel at one inset size.
 *
 * The tagged destination node sits at the **end** of the host's modifier chain, so its bounds *are*
 * the padded content region. Run it once per mode:
 *
 * ```
 * adb shell cmd overlay enable  com.android.internal.systemui.navbar.gestural
 * ./gradlew :app:app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.NavBarContentGapProbe
 * adb shell cmd overlay enable  com.android.internal.systemui.navbar.threebutton
 * # …and again
 * ```
 *
 * It asserts nothing about the gap on purpose — it is an instrument, and the verdict is a number a
 * human compares across two runs. Making it assert `gap == 0` would turn a measurement into a
 * tautology the first time someone "fixed" a failing run by changing the expectation.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class NavBarContentGapProbe {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun contentBottomAgainstBarTop() {
        composeRule.waitForIdle()

        val content = composeRule.onNodeWithTag("HomeGraph").fetchSemanticsNode()
        val bar = composeRule.onNodeWithTag("WorkeeperBottomAppBar").fetchSemanticsNode()

        val density = composeRule.activity.resources.displayMetrics.density
        val insets = InstrumentationRegistry.getInstrumentation().uiAutomation.let {
            composeRule.activity.window.decorView.rootWindowInsets
        }
        val navInsetPx = insets?.systemWindowInsetBottom ?: -1

        val contentBottom = content.boundsInRoot.bottom / density
        val barTop = bar.boundsInRoot.top / density
        val barHeight = (bar.boundsInRoot.bottom - bar.boundsInRoot.top) / density

        // Printed, not asserted. `println` in an instrumentation test reaches logcat and the
        // connectedAndroidTest console output; the harness that produced §24's original table was
        // ad-hoc and discarded, which is why the numbers could not be re-derived and this file
        // exists.
        println(
            "NAVGAP navInsetDp=${navInsetPx / density} barHeight=$barHeight " +
                "contentBottom=$contentBottom barTop=$barTop gap=${barTop - contentBottom}",
        )
    }
}

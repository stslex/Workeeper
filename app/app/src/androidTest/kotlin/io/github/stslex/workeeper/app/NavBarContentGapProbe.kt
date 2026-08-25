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
 * Instrument for the content/bottom-bar seam: prints the gap for one navigation mode; the verdict
 * is the pair of readings. See documentation/feature-specs/v3-redesign-spec.md §24.
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

        println(
            "NAVGAP navInsetDp=${navInsetPx / density} barHeight=$barHeight " +
                "contentBottom=$contentBottom barTop=$barTop gap=${barTop - contentBottom}",
        )
    }
}

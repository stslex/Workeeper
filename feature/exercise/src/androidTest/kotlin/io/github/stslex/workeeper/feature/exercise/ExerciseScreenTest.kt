// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class ExerciseScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // TODO(feature-rewrite-tests): cover ExerciseDetailScreen, ExerciseEditScreen, mode flip,
    // archive overflow, tag picker create+toggle, discard dialog and snackbar paths once Smoke
    // harness wiring for the Exercise feature lands.

    @Test
    @Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")
    fun pendingFeatureRewrite() {
        // Placeholder so AndroidJUnit4 has at least one @Test to discover.
        // Remove when real tests are added.
    }
}

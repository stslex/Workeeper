// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class ArchiveScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // TODO(feature-rewrite-tests): exercise segment switch + paged restore + delete dialog after Stage 5.1 stabilises.

    @Test
    @Ignore("Awaiting feature rewrite — see GH issue #93 for coverage scope.")
    fun pendingFeatureRewrite() {
        // Placeholder so AndroidJUnit4 has at least one @Test to discover.
        // Remove when real tests are added.
    }
}

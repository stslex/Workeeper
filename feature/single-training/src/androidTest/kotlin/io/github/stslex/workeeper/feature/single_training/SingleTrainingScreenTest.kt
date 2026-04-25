package io.github.stslex.workeeper.feature.single_training

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import org.junit.Rule
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class SingleTrainingScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // TODO(feature-rewrite): rewrite UI tests after feature redesign per documentation/testing.md.
}

package io.github.stslex.workeeper.core.ui.test

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * Base class for Compose UI tests providing common functionality
 */
@VisibleForTesting
abstract class BaseComposeTest {

    /**
     * Wraps content with SharedTransitionScope for testing
     * Use this with ComposeTestRule.setContent { ... }
     */
    @SuppressLint("UnusedContentLambdaTargetStateParameter")
    protected fun ComposeContentTestRule.setTransitionContent(
        content: @Composable SharedTransitionScope.(
            animatedContentScope: AnimatedContentScope,
            modifier: Modifier,
        ) -> Unit,
    ) {
        setContent {
            AnimatedContent(
                targetState = Unit,
                label = "test_animation",
            ) { _ ->
                SharedTransitionScope { modifier ->
                    content(this@AnimatedContent, modifier)
                }
            }
        }
    }

    /**
     * Captures actions for verification in tests
     */
    protected class ActionCapture<T> {
        private val capturedActions = mutableListOf<T>()

        val consume: (T) -> Unit = { action ->
            capturedActions.add(action)
        }

        fun assertCaptured(predicate: (T) -> Boolean): T? {
            return capturedActions.firstOrNull(predicate)
        }

        fun assertCapturedExactly(expectedAction: T) {
            require(capturedActions.contains(expectedAction)) {
                "Expected action $expectedAction not found. Captured: $capturedActions"
            }
        }

        fun assertCapturedCount(count: Int) {
            require(capturedActions.size == count) {
                "Expected $count actions but captured ${capturedActions.size}"
            }
        }

        fun clear() {
            capturedActions.clear()
        }

        fun getAll(): List<T> = capturedActions.toList()
    }
}

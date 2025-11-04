package io.github.stslex.workeeper.core.ui.test

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.stslex.workeeper.core.ui.mvi.Store

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

    protected fun <T : Store.Action> createActionCapture(): ActionCapture<T> = ActionCapture()

    /**
     * Captures actions for verification in tests
     */
    protected class ActionCapture<T : Store.Action> internal constructor() : (T) -> Unit {
        val capturedActions = mutableListOf<T>()

        override operator fun invoke(action: T) {
            capturedActions.add(action)
        }

        inline fun <reified A : T> assertCaptured(
            errorMsg: () -> String = { "No captured action Filter of type ${A::class.java} found." },
        ): List<A> = capturedActions.filterIsInstance<A>()
            .ifEmpty { error(errorMsg()) }

        inline fun <reified A : T> captured(): List<A> = capturedActions.filterIsInstance<A>()

        inline fun <reified A : T> capturedFirst(
            errorMsg: () -> String = { "No captured action of type ${A::class.java} found." },
        ): A = captured<A>().firstOrNull() ?: error(errorMsg())

        inline fun <reified A : T> capturedLast(
            errorMsg: () -> String = { "No captured action of type ${A::class.java} found." },
        ): A = captured<A>().lastOrNull() ?: error(errorMsg())

        fun assertCapturedExactly(expectedAction: T) {
            require(capturedActions.contains(expectedAction)) {
                "Expected action $expectedAction not found. Captured: $capturedActions"
            }
        }

        inline fun <reified A : T> assertCapturedCount(
            count: Int,
            errorMsg: () -> String = { "Expected $count actions but captured ${capturedActions.size}" },
        ) {
            require(captured<A>().size == count) {
                errorMsg()
            }
        }

        inline fun <reified A : T> assertCapturedOnce(
            errorMsg: () -> String = { "Expected single action but captured ${capturedActions.size}" },
        ) {
            require(captured<A>().size == 1) {
                errorMsg()
            }
        }

        fun clear() {
            capturedActions.clear()
        }

        fun getAll(): List<T> = capturedActions.toList()
    }
}

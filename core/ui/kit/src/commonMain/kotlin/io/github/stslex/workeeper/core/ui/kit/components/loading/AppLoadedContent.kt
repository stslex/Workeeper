// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.loading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.theme.continuityAlphaSpec

/**
 * A route's content, withheld until it knows what it is showing, and faded in when it does.
 * See documentation/feature-specs/v3-redesign-spec.md §26.
 *
 * GUARD: a load behind [isLoaded] must resolve on failure too; a latched flag is an empty screen.
 * GUARD: compose this while still loading, or `AnimatedVisibility` skips the fade entirely.
 */
@Composable
fun AppLoadedContent(
    isLoaded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = isLoaded,
        modifier = modifier,
        enter = fadeIn(animationSpec = continuityAlphaSpec()),
        // Nothing to animate away: `isLoaded` goes false only when a route re-enters loading.
        exit = ExitTransition.None,
        label = "AppLoadedContent",
    ) {
        content()
    }
}

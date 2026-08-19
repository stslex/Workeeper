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
 *
 * `AnimatedVisibility` composes [content] only when [isLoaded], so a route using this cannot show
 * a seeded value that later flips, cannot let a load overwrite something the user touched, and
 * runs no side effect inside [content] early. Nothing is drawn while it waits: no mockup draws a
 * loading surface, and the nav host paints the background under every destination. The fade rides
 * [continuityAlphaSpec] — an element appearing where there was none, with no path between the two
 * frames — so §26.1's rule that alpha carries no character applies unchanged.
 *
 * GUARD: **every load behind [isLoaded] must resolve on failure as well as on success.**
 * `HandlerStore.launch` defaults `onError` to `{}` (B17, B21), so a throw that leaves the flag
 * latched is a permanently empty screen — this composable is what gives that failure its cost.
 * Write the `onError` arm out; an empty one is the latched flag.
 *
 * GUARD: the wrapper must be composed **while the route is still loading**. `AnimatedVisibility`
 * does not animate a composable that enters composition already visible, so hoisting this below an
 * early return silently drops the fade.
 *
 * The decision and its ledger rows are in `documentation/feature-specs/v3-redesign-spec.md` §26,
 * "A route does not compose until it has loaded" and its two amendments.
 *
 * @param isLoaded whether the state behind [content] has resolved. `false` composes nothing.
 * @param content the route's content, composed only once [isLoaded].
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
        // Nothing to animate away: `isLoaded` goes false only when a route re-enters loading,
        // and holding a stale screen mid-fade there would be showing the previous record's data
        // over the next one's load.
        exit = ExitTransition.None,
        label = "AppLoadedContent",
    ) {
        content()
    }
}

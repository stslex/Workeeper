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
 * ## What this replaces, and why the replacement is not cosmetic
 *
 * §26's route gate — "a route does not compose until it has loaded" — was written as
 * `if (state.isLoading) return` at the top of a graph. That is exactly right about *what* to
 * show while loading (nothing: no mockup draws a loading surface, and the nav host paints the
 * background under every destination) and silent about *how the content arrives*. It arrives in
 * one frame, at full opacity, out of an empty screen — which reads as a snap, and reads worse the
 * shorter the wait is, because a shorter wait puts the blank frame and the snap closer together.
 *
 * This composable keeps the rule and adds the arrival. `AnimatedVisibility` composes the content
 * only when [isLoaded], so every guarantee the bare `return` bought is unchanged: seeded values
 * cannot visibly flip, a load that overwrites a draft cannot overwrite something the user touched,
 * and no side effect inside [content] runs early. What changes is that the first frame is at alpha
 * 0 rather than alpha 1.
 *
 * The fade rides [continuityAlphaSpec] — `AppMotion.linear` at `AppMotion.base` — because that is
 * the class this belongs to: an element appearing where there was none, with no path between the
 * two frames. §26.1's rule that alpha carries no character applies unchanged.
 *
 * ## The precondition it inherits, restated because it is easy to lose
 *
 * **Every load path behind [isLoaded] must resolve on failure as well as on success.**
 * `HandlerStore.launch` defaults `onError` to `{}` (B17, B21), so a throw that leaves the flag
 * latched is a permanently empty screen — this composable, like the `return` before it, is what
 * gives that failure its cost. Write the `onError` arm out; an empty one is the latched flag.
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

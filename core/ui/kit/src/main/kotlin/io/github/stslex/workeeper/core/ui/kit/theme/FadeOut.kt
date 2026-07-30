// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.ui.graphics.Color

/**
 * The same colour, invisible — **the only correct endpoint for a fade-out.**
 *
 * ## `Color.Transparent` is transparent *black*, and a cross-fade carries hue
 *
 * `animateColorAsState` interpolates in Oklab, so a tween between a visible colour and
 * `Color.Transparent` does not fade the colour *out*: it travels toward black while its alpha
 * drops, and the mid-frames composite darker than **both** endpoints. On a dark page the excursion
 * is invisible; on a light page it is a grey flash where nothing should move at all.
 *
 * Measured with Compose's own `lerp` against the shipped palette, at the tween's midpoint,
 * composited over the surface each site actually sits on. `dip` is how far the mid-frame falls
 * below the darker of the two endpoints — positive means an excursion neither endpoint explains:
 *
 * ```
 * LIGHT                                     endpoints         →Transparent      dip     →fadedOut
 *   list row lift            (tier2/tier0)  #F6F7F9..#FFFFFF  #ACADAE         +0.290    #FBFBFC
 *   top-bar icon press       (tier1/tier0)  #F6F7F9..#EFF1F4  #A9AAAB         +0.275    #F2F4F6
 *   settings row press       (tier1/tier0)  #F6F7F9..#EFF1F4  #A9AAAB         +0.275    #F2F4F6
 *   set-mark record fill    (molten/tier2)  #FFFFFF..#F97316  #B09381         +0.286    #FCB98A
 *   mini icon press    (borderSubtle/tier2) #FFFFFF..#EEEEEE  #F6F6F6         -0.031    #F6F7F7
 *   set-mark fill           (accent/tier2)  #FFFFFF..#0D1114  #808081         -0.451    #868889
 * DARK — every site between -0.125 and +0.020, i.e. nothing visible.
 * ```
 *
 * Four light-theme sites flash; two do not, because their targets are dark enough that the path to
 * black stays inside the endpoints. That asymmetry is the trap: the defect is invisible in dark
 * theme, invisible in a screenshot of either endpoint, and present in four places at once.
 *
 * ## The rule
 *
 * A fade-out ends at **the same colour with zero alpha**, never at `Color.Transparent`. Only alpha
 * moves, so no mid-frame can be a colour neither endpoint contains — and unlike "fade to the colour
 * behind it", this needs no knowledge of what is behind, which is what makes it safe in a component
 * that cannot know.
 *
 * `FadeToTransparentRule` enforces it; this is the function it tells you to call.
 */
fun Color.fadedOut(): Color = copy(alpha = 0f)

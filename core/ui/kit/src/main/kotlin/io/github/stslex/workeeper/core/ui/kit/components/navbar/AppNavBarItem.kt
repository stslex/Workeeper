// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.navbar

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One destination in [AppNavBar] — **presentation only, and deliberately so.**
 *
 * This type carries an already-resolved [ImageVector], an already-resolved
 * [contentDescription] string and an already-chosen [testTag]. It carries **no route, no
 * `Screen`, no `@StringRes`**, and that restraint is the whole architectural point of this
 * component rather than a stylistic preference.
 *
 * The bar it replaces put the *destination model* in the kit — `AppBottomBarDestination`, with
 * `label: String` — and the consequence was on the screen: it shipped `"Home"`, `"Trainings"`,
 * `"Exercises"` as **English literals in a Russian-language app**. That was not carelessness. The
 * kit module cannot reach `app/app`'s `R.string.bottom_bar_label_*` (different module, different
 * resources) and does not depend on `core:ui:navigation` (checked in `build.gradle.kts`, not
 * assumed), so a kit-resident destination enum had nowhere to get a localised label *or* a route
 * from — and a hardcoded literal was the only thing left that compiled.
 *
 * So the boundary is drawn where the compiler already draws it: **the kit owns the treatment, the
 * app owns the destinations.** `BottomBarItem` stays in `app/app` because it carries
 * `screen: Screen.BottomBar` and `getByRoute`, which is routing; it resolves its own strings with
 * `stringResource` and hands the result down. The kit's own rule agrees
 * (`architecture.md`: "pure Compose primitives … domain-agnostic … do **not** put it in
 * `core/ui/kit` if it has any domain coupling"), but the rule is not what makes this safe — the
 * missing dependency edge is.
 */
@Immutable
data class AppNavBarItem(
    val icon: ImageVector,
    val contentDescription: String,
    val testTag: String,
)

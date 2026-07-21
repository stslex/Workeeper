// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

/**
 * Navigation spine: the single app-scope [Navigator] accessor, read by ~every nav feature.
 *
 * Part of the [AppGraphContract][io.github.stslex.workeeper.core.di.AppGraphContract] split
 * (variant A, spine variant γ). Kept separate from
 * [StoreCoreDeps][io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps] so a store-infra-only
 * consumer (app-dialogs) need not depend on `core:ui:navigation`. The accessor signature is copied
 * verbatim from `AppGraphContract` so `AppGraph`'s existing override satisfies it with no new provision.
 *
 * `Navigator` is owned by `core:ui:navigation` — same-module, no dependency change.
 */
interface NavigatorDeps {
    val navigator: Navigator
}

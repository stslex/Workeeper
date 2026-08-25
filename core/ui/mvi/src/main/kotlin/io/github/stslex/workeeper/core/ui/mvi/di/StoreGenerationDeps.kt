// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime

/** Minimal graph surface exposing the current generation lifetime to Store construction. */
interface StoreGenerationDeps {

    val appScopeLifetime: AppScopeLifetime
}

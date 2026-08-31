// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.common.di

import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerGraph
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorGraph
import io.github.stslex.workeeper.navigation.NavigatorEventBus

/**
 * `app:common`'s dep interface — the exact app-scope types the composition root reads.
 * This module sits below the app graph, so it names a contract and `:app:app` satisfies it.
 */
interface AppRootDeps {

    /** Backs the theme-mode flow `AppRootViewModel` exposes to `AppTheme`. */
    val commonDataStore: CommonDataStore

    /**
     * The one `@SingleIn(AppScope)` navigator, exposed as its CONCRETE type: the root uses all
     * three of its faces at once, and three members could be satisfied by three objects.
     */
    val navigatorEventBus: NavigatorEventBus

    /** Generation-owned factory for the image-viewer graph extension. */
    val imageViewerGraphFactory: ImageViewerGraph.Factory

    /** Generation-owned factory for the plan-editor graph extension. */
    val planEditorGraphFactory: PlanEditorGraph.Factory
}

/**
 * Held-instance seam for [AppRootDeps]: the process `Application` exposes the app-scope graph
 * typed as [AppRootDeps], so the cast in `App()` is safe by construction.
 */
interface AppRootDepsHolder {

    fun appRootDeps(): AppRootDeps
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractorImpl
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStoreImpl

/**
 * feature/plan-editor's Metro graph, a contributed [GraphExtension] of [PlanEditorScope] merged
 * into the app graph. The `Screen.PlanEditor` route arg is a `@Provides` bound instance (shape B).
 */
@GraphExtension(PlanEditorScope::class)
interface PlanEditorGraph {

    /** Root accessor: the retained Store. */
    val planEditorStore: PlanEditorStoreImpl

    @Binds
    val PlanEditorInteractorImpl.bindInteractor: PlanEditorInteractor

    @Binds
    val PlanEditorHandlerStoreImpl.bindHandlerStore: PlanEditorHandlerStore

    /**
     * GUARD: the creator name must be unique across all contributed factories — every one is
     * merged into `AppGraph`. See documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createPlanEditorGraph(@Provides screen: Screen.PlanEditor): PlanEditorGraph
    }
}

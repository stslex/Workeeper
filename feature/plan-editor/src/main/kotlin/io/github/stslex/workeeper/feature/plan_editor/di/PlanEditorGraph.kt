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
 * feature/plan-editor's Metro graph as a CONTRIBUTED [GraphExtension] of [PlanEditorScope]. The factory
 * carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in `:app` and
 * inherits ALL of its app-scoped bindings — the 8 formerly hand-threaded bound-instance `@Provides` are
 * gone. The two `@Binds` (interactor, handler store) stay.
 *
 * ROUTE ARG (shape B, second application after image-viewer): the `Screen.PlanEditor` route arg enters as
 * a `@Provides` bound instance on the extension factory rather than as an `@Assisted` store param, so the
 * accessor is the Store itself and the feature carries no assisted machinery at all — `@AssistedInject`,
 * `@Assisted`, the nested `@AssistedFactory` and its `StoreFactory` supertype are all deleted here.
 *
 * The route arg is a SEALED PARENT (`Screen.PlanEditor`, with `Existing` / `Draft` subtypes), unlike
 * image-viewer's flat `Screen.ExerciseImage`. That difference is the point of porting this feature
 * seventh: `ScreenInjectionRule` had only ever been proven against a flat 2-level route type, and a guard
 * that is silent on a second shape is a hole in the guarantee it replaces. Proven on real code before the
 * remaining features are batched — see the arc HANDOFF.
 *
 * The arg is an ordinary binding in this scope, so it COULD be injected anywhere in the extension;
 * `ScreenInjectionRule` (detekt) forbids that outside the Store's primary constructor — state must flow
 * through the Store, not be read from DI.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [PlanEditorScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(PlanEditorScope::class)
interface PlanEditorGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val planEditorStore: PlanEditorStoreImpl

    @Binds
    val PlanEditorInteractorImpl.bindInteractor: PlanEditorInteractor

    @Binds
    val PlanEditorHandlerStoreImpl.bindHandlerStore: PlanEditorHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createPlanEditorGraph(@Provides screen: Screen.PlanEditor): PlanEditorGraph
    }
}

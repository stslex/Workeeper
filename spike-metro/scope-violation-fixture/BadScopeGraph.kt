// SPDX-License-Identifier: GPL-3.0-only
//
// Phase B.2 — gate (2b) COMPILE-TIME SCOPE ENFORCEMENT FIXTURE.
//
// This file lives OUTSIDE any Kotlin source set (it is under
// spike-metro/scope-violation-fixture/, not spike-metro/src/...), so it is NEVER
// compiled by the normal build and does not break it. It documents the deliberate
// scope violation and the exact compiler error it produced when temporarily placed in
// commonMain — the exact property Koin was rejected for (Koin only fails at runtime).
//
// To reproduce: copy the `interface BadScopeGraph { ... }` below into
// spike-metro/src/commonMain/kotlin/.../topology/ and run either:
//   ./gradlew :spike-metro:assembleAndroidMain
//   ./gradlew :spike-metro:compileKotlinIosSimulatorArm64
// Both fail with ExitCode 1 and the identical [Metro/IncompatiblyScopedBindings]
// diagnostic. Metro 1.1.1, Kotlin 2.3.20.
//
// ─────────────────────────────────────────────────────────────────────────────
// CAPTURED COMPILER ERROR (identical on android AND iosSimulatorArm64):
//
// e: _BadScopeGraph.kt:10:11 [Metro/IncompatiblyScopedBindings]
//   io.github.stslex.workeeper.spike_metro.topology.BadScopeGraph
//   (scopes '@SingleIn(AppScope::class)') may not reference bindings from different scopes:
//     io.github.stslex.workeeper.spike_metro.topology.ChartInteractor
//       (scoped to '@SingleIn(FeatureScope::class)')
//         [.BadScopeGraph] .BadScopeGraph.interactor
// > Compilation finished with errors  (BUILD FAILED, ExitCode 1)
// ─────────────────────────────────────────────────────────────────────────────

package io.github.stslex.workeeper.spike_metro.topology

import dev.zacsweers.metro.DependencyGraph

// DELIBERATE VIOLATION: an AppScope-only @DependencyGraph that exposes an accessor for
// ChartInteractor, which is @SingleIn(FeatureScope::class). Only AppScope is visible in
// this graph, so requesting a FeatureScope-scoped binding is caught at COMPILE.
@DependencyGraph(scope = AppScope::class)
interface BadScopeGraph {
    val interactor: ChartInteractor
}

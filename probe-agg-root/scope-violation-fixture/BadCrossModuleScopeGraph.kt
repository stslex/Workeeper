// SPDX-License-Identifier: GPL-3.0-only
//
// P2.a — CROSS-MODULE compile-time scope-enforcement fixture (documented, NON-COMPILED:
// lives outside any src/ source set, so it never breaks the build).
//
// It proves Metro's scope enforcement holds ACROSS a module boundary: an AppScope-only
// @DependencyGraph in :probe-agg-root that references FeatureServiceImpl — which is
// @SingleIn(FeatureScope) in a DIFFERENT module (:probe-agg-y) — fails at COMPILE on BOTH
// android AND iosSimulatorArm64. Metro 1.1.1, Kotlin 2.3.20.
//
// To reproduce: drop the graph below into
// probe-agg-root/src/commonMain/kotlin/.../probe_agg_root/ and run either
//   ./gradlew :probe-agg-root:assembleAndroidMain
//   ./gradlew :probe-agg-root:compileKotlinIosSimulatorArm64
//
// ─────────────────────────────────────────────────────────────────────────────
// CAPTURED COMPILER ERROR (identical on android AND iosSimulatorArm64):
//
// e: _BadCrossModuleScope.kt:11:11 [Metro/IncompatiblyScopedBindings]
//   ...probe_agg_root.BadCrossModuleScopeGraph (scopes '@SingleIn(AppScope::class)')
//   may not reference bindings from different scopes:
//     ...probe_agg_y.FeatureServiceImpl (scoped to '@SingleIn(FeatureScope::class)')
//         [...BadCrossModuleScopeGraph] ...BadCrossModuleScopeGraph.impl
// > Compilation finished with errors (BUILD FAILED, ExitCode 1)
//
// Note the cross-module reference: the graph is in :probe-agg-root, the offending binding
// (FeatureServiceImpl) is defined and scoped in :probe-agg-y — enforcement is not weakened
// across the boundary. This is the exact property Koin was rejected for.
// ─────────────────────────────────────────────────────────────────────────────

package io.github.stslex.workeeper.probe_agg_root

import dev.zacsweers.metro.DependencyGraph
import io.github.stslex.workeeper.probe_agg_x.AppScope
import io.github.stslex.workeeper.probe_agg_y.FeatureServiceImpl

@DependencyGraph(scope = AppScope::class)
interface BadCrossModuleScopeGraph {
    val impl: FeatureServiceImpl
}

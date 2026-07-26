// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

/**
 * Metro feature-scope marker for feature/live-workout — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(LiveWorkoutScope::class)`.
 */
internal abstract class LiveWorkoutScope private constructor()

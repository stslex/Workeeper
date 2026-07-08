// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

/**
 * Metro feature-scope marker for feature/all-exercises — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(AllExercisesScope::class)`.
 */
internal abstract class AllExercisesScope private constructor()

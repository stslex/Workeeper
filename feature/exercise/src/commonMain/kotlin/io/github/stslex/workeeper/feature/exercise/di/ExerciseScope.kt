// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

/**
 * Metro feature-scope marker for feature/exercise — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed exercise node is `@SingleIn(ExerciseScope::class)`,
 * so one [ExerciseGraph] == one retained `ExerciseStoreImpl` ViewModel == one `NavBackStackEntry`.
 * Same token form as archive's `ArchiveScope` / settings' `SettingsScope`.
 */
internal abstract class ExerciseScope private constructor()

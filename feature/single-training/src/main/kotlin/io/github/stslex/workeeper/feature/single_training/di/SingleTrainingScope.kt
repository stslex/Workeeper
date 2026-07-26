// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

/**
 * Metro feature-scope marker for feature/single-training — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(SingleTrainingScope::class)`, so
 * one [SingleTrainingGraph] == one retained `SingleTrainingStoreImpl` == one `NavBackStackEntry`.
 */
internal abstract class SingleTrainingScope private constructor()

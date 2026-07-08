// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

/**
 * Metro feature-scope marker for feature/app-dialogs:impl — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(AppDialogsScope::class)`.
 *
 * NOTE: the `AppDialogStore` is mounted via `AppFeature` (root-mounted, Activity-scoped
 * `ViewModelStore`), not a `NavBackStackEntry`. That is a mount-site concern owned by the caller
 * (`AppDialogHost`); the graph scope and `rememberMetroStoreProcessor` retention are unchanged by it.
 */
internal abstract class AppDialogsScope private constructor()

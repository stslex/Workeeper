// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl

/**
 * Feature-internal seam: the process `Application` exposes app-dialogs/impl's own app-scoped singletons
 * ([AppDialogRepository], [AppDialogObserverImpl]) through this interface.
 *
 * These are `feature/app-dialogs/impl` concrete types; no module can name them. The one consumer — this
 * feature's own `AppDialogFeature` — reads them via this impl-owned holder+accessor rather than the Metro graph.
 * `BaseApplication` implements this as one-line `get()`s.
 */
interface AppDialogInternalsHolder {
    val appDialogRepository: AppDialogRepository
    val appDialogObserverImpl: AppDialogObserverImpl
}

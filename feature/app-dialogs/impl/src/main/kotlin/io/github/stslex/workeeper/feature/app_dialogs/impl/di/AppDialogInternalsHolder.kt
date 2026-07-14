// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl

/**
 * Feature-internal seam: the process `Application` exposes app-dialogs/impl's own app-scoped singletons
 * ([AppDialogRepository], [AppDialogObserverImpl]) through this interface. App-Scope Collapse Step 6 (cut).
 *
 * These are `feature/app-dialogs/impl` concrete types; no module can name them. The one consumer — this
 * feature's own `AppDialogFeature` — reads them Hilt-free via this impl-owned holder+accessor.
 * `BaseApplication` implements this as one-line `get()`s.
 */
interface AppDialogInternalsHolder {
    val appDialogRepository: AppDialogRepository
    val appDialogObserverImpl: AppDialogObserverImpl
}

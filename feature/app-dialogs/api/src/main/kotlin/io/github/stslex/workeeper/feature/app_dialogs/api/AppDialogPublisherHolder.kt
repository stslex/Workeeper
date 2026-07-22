// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api

import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher

/**
 * Feature-tier seam: the process `Application` exposes the app-scoped [AppDialogPublisher] through this
 * interface.
 *
 * `AppDialogPublisher` is a `feature/app-dialogs/api` type, so the app-scope dep interfaces cannot name
 * it. The library consumer that needs it — `feature/settings` — DOES depend on this module, so this
 * parallel holder+accessor (the `AppDepsHolder` shape, homed here) lets
 * settings read it. `BaseApplication` implements this as a one-line `get()`.
 */
interface AppDialogPublisherHolder {
    val appDialogPublisher: AppDialogPublisher
}

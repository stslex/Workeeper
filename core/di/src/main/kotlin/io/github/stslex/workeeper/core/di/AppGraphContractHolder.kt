// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.di

/**
 * Held-instance seam: the process [android.app.Application] exposes the app-scope [AppGraphContract]
 * through this interface. App-Scope Collapse Step 6 (P-CONTRACT).
 *
 * The public analogue of app/app's `internal AppGraphOwner` (which core modules cannot see). prod
 * `BaseApplication` implements it as a one-line `get() = appGraph` — since app/app's
 * `AppGraph : AppGraphContract`, the held graph IS an [AppGraphContract], and the getter reads the
 * `by lazy` graph on access (never eagerly, so it does not force graph construction at Application
 * construction — where Hilt/EntryPoints are not yet ready).
 *
 * Read via [appGraphContract]. Add-only (P-CONTRACT): no consumer calls it yet.
 */
interface AppGraphContractHolder {
    val appGraphContract: AppGraphContract
}

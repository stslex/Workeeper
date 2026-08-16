// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.dataStore.core

import okio.Path

/**
 * Where a named Preferences store lives on this platform.
 *
 * An interface rather than an `expect fun`, because the Android implementation needs a `Context`
 * and the iOS one needs nothing: an `expect`/`actual` pair must agree on their constructor
 * signature, so the Android side would have to reach for a global `Context` holder to satisfy a
 * shape iOS could also satisfy. As an interface each side declares its own dependencies and Metro
 * resolves them — the same interface-shaped seam `ResourceWrapper` and `ImageStorage` use.
 *
 * `okio.Path`, not `java.io.File`, because the common half of the DataStore API is
 * `PreferenceDataStoreFactory.createWithPath(produceFile: () -> okio.Path)`; the `() -> File`
 * overload exists only in `datastore-preferences`' `jvmAndroidMain`.
 *
 * **The returned path is a storage contract, not an implementation detail.** Every name passed here
 * already has user data on disk under it on Android. An implementation that resolves a different
 * path silently orphans that data — which is why the Android one calls
 * `Context.preferencesDataStoreFile` rather than rebuilding the path from its parts, and why
 * `app/app` androidTest `AppScopeDataStoreSingletonTest` pins name to relative file for every store
 * this repo owns.
 */
interface DataStorePathResolver {

    /**
     * The absolute path of the Preferences file backing [name]. Must be stable for the lifetime of
     * the installation and must return the same path every time it is called with the same [name] —
     * `DataStoreProvider` memoizes by name, and DataStore itself rejects two live instances over one
     * file.
     */
    fun resolve(name: String): Path
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.dataStore.core

import okio.Path

/**
 * Where a named Preferences store lives on this platform. The returned path is a storage contract:
 * resolving elsewhere silently orphans data already on disk. See the Phase-6 data-layer spec.
 */
interface DataStorePathResolver {

    /**
     * The absolute path of the Preferences file backing [name]. Must return the same path every
     * time and stay stable for the lifetime of the installation.
     */
    fun resolve(name: String): Path
}

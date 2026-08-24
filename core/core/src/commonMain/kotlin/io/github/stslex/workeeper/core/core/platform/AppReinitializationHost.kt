// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/** Root-bound request to rebuild the current runtime generation in process. */
interface AppReinitializationHost {

    /** Fire-and-forget reinitialization request. */
    fun requestReinitialize()
}

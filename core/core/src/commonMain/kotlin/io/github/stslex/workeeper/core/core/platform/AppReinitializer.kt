// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/** Platform seam for reinitializing after a database-file swap. */
expect class AppReinitializer {

    fun reinitialize()
}

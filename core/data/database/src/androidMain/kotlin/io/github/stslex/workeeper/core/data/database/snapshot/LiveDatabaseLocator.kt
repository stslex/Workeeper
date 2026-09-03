// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import java.io.File

/** Path of the live Room database file, as a seam that exposes no Android `Context`. */
interface LiveDatabaseLocator {

    /** The live database file on disk. May not exist yet (fresh install). */
    fun liveDatabaseFile(): File
}

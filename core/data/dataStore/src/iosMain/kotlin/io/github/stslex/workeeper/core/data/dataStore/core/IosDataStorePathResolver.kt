// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.dataStore.core

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS's [DataStorePathResolver]: `<Documents>/datastore/<name>.preferences_pb`.
 *
 * `NSDocumentDirectory` is the iOS analogue of Android's `filesDir` — app-private, included in
 * backups, and surviving app updates — which is the lifetime these preferences need. The
 * `datastore/` subdirectory and the `.preferences_pb` suffix mirror the Android layout: nothing
 * requires that, but one naming scheme across platforms keeps "where does the user's theme
 * preference live?" answerable once. The suffix itself is not decorative — DataStore documents that
 * the file must carry the `preferences_pb` extension.
 *
 * NOT bound into a graph here. Metro is applied to this module for the Android compilation; the iOS
 * composition root does not exist yet and arrives with the iosApp in phase 7, which will construct
 * this or bind it in whatever graph it stands up. Until then it is a compiled, unreferenced
 * implementation — the same shape as `core:core`'s iOS platform seams.
 */
class IosDataStorePathResolver : DataStorePathResolver {

    override fun resolve(name: String): Path {
        // URLsForDirectory, not URLForDirectory: the single-URL overload takes an NSError out-param,
        // whose CPointer type would drag @ExperimentalForeignApi into this file for no benefit. The
        // list form is pure Foundation and its first element is the same directory.
        val documents = NSFileManager.defaultManager
            .URLsForDirectory(directory = NSDocumentDirectory, inDomains = NSUserDomainMask)
            .firstOrNull() as? NSURL
        val documentsPath = requireNotNull(documents?.path) {
            "NSDocumentDirectory is unavailable — cannot resolve a DataStore path for $name"
        }
        return documentsPath.toPath().resolve(DATASTORE_DIRECTORY).resolve("$name.$FILE_SUFFIX")
    }

    private companion object {

        const val DATASTORE_DIRECTORY = "datastore"
        const val FILE_SUFFIX = "preferences_pb"
    }
}

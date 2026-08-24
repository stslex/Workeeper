// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.dataStore.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * Android's [DataStorePathResolver]: the same `Context.preferencesDataStoreFile(name)` every store
 * in this repo resolved through before the KMP split.
 *
 * **Calling that extension is the point, not a convenience.** The path it produces —
 * `File(applicationContext.filesDir, "datastore/$name.preferences_pb")`, read from the
 * `datastore` 1.2.1 sources — is where every existing installation's data already sits. Rebuilding
 * it from `filesDir` and a string here would be a second, independently-drifting definition of a
 * storage location, and the first typo would orphan user data with no compile error and no crash.
 * Delegating keeps one definition, owned by the library.
 *
 * `Context` is a PLAIN, unqualified param: Metro constructs this and resolves `Context` from the app
 * graph's `create(applicationContext)` bound instance, which carries no qualifier.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidDataStorePathResolver(
    private val context: Context,
) : DataStorePathResolver {

    override fun resolve(name: String): Path = context.preferencesDataStoreFile(name).toOkioPath()
}

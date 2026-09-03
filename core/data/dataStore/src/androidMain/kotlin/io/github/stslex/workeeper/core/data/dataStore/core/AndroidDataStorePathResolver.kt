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
 * Android's [DataStorePathResolver]. Delegates to `Context.preferencesDataStoreFile(name)` so the
 * library keeps the single definition of where an existing installation's data already sits.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidDataStorePathResolver(
    private val context: Context,
) : DataStorePathResolver {

    override fun resolve(name: String): Path = context.preferencesDataStoreFile(name).toOkioPath()
}

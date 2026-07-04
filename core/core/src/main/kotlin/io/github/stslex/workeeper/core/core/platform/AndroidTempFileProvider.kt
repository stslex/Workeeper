// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidTempFileProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : TempFileProvider {

    override fun createTempFile(prefix: String, suffix: String): File =
        File.createTempFile(prefix, suffix, context.cacheDir)
}

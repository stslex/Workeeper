// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Production construction of the [ImageStorage] `create()` bound-instance root. A plain factory,
 * not a Metro binding: a binding here would duplicate the bound instance. See architecture.md.
 */
fun buildImageStorage(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
): ImageStorage = ImageStorageImpl(context, ioDispatcher)

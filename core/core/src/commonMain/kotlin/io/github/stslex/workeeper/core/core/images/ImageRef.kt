// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import kotlin.jvm.JvmInline

/**
 * Opaque, platform-neutral handle to an image source or destination — the string form of the
 * underlying platform reference (an `android.net.Uri` string on Android).
 */
@JvmInline
value class ImageRef(val value: String)

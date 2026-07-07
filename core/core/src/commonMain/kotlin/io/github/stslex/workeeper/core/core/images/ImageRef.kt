// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import kotlin.jvm.JvmInline

/**
 * Opaque, platform-neutral handle to an image source or destination — the string form
 * of the underlying platform reference (an `android.net.Uri` string on Android).
 *
 * Lives at the [ImageStorage] boundary so the domain layer can pass image references
 * without importing Android SDK types; the Android [ImageStorage] implementation parses
 * it back to a `Uri`, and UI callers convert to/from `Uri` at the platform edge.
 */
@JvmInline
value class ImageRef(val value: String)

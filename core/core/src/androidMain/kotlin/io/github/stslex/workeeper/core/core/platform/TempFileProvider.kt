// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import java.io.File

/**
 * Creates temporary files for transient work without the domain layer importing Android SDK types.
 *
 * TODO(tech-debt): the returned [File] is JVM-typed and deliberately temporary. This
 * seam is a tactical decoupling so the domain layer stops importing `Context.cacheDir`
 * while temp-file orchestration still lives inside interactors. When that orchestration
 * moves to the data layer, this interface is expected to be **removed**, not reshaped
 * into a neutral path abstraction.
 */
interface TempFileProvider {

    /** Creates an empty temp file; the caller owns it and must delete it when done. */
    fun createTempFile(prefix: String, suffix: String): File
}

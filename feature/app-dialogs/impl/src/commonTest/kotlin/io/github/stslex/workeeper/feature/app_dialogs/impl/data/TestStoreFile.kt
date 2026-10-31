// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import okio.Path

/**
 * D5: removes one test's store file. Each platform deletes through its own `FileSystem.SYSTEM`
 * member, which every okio 3.x has; the common spelling needs okio 3.9.0, so this does not depend
 * on the okio version DataStore brings.
 */
internal expect fun deleteStoreFile(path: Path)

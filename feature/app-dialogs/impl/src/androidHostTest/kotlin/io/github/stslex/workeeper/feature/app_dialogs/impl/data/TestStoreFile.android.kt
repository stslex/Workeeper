// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import okio.FileSystem
import okio.Path

internal actual fun deleteStoreFile(path: Path) {
    FileSystem.SYSTEM.delete(path)
}

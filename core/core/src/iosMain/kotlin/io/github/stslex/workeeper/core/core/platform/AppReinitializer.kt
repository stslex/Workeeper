// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/** iOS delegates reinitialization to a required composition-root host. */
actual class AppReinitializer(
    private val host: AppReinitializationHost,
) {

    actual fun reinitialize() {
        host.requestReinitialize()
    }
}

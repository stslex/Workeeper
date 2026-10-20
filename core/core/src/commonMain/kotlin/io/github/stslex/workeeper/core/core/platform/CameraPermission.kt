// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

/** Whether the app may use the camera right now. Synchronous: a click path reads it. */
interface CameraPermission {

    fun isGranted(): Boolean
}

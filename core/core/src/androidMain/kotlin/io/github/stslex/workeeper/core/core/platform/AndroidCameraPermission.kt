// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope

/**
 * Android [CameraPermission]: the runtime `CAMERA` grant, read through `ContextCompat`. Metro
 * resolves `Context` from the graph's `create(applicationContext)`.
 * GUARD: must stay public — an internal contribution does not aggregate into `AppGraph`.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidCameraPermission(
    private val context: Context,
) : CameraPermission {

    override fun isGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
}

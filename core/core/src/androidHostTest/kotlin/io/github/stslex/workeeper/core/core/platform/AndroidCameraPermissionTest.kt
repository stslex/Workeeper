// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class AndroidCameraPermissionTest {

    private lateinit var application: Application
    private lateinit var permission: AndroidCameraPermission

    @BeforeEach
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        permission = AndroidCameraPermission(context = application)
    }

    @Test
    fun `isGranted is true once the camera permission is granted`() {
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

        assertTrue(permission.isGranted())
    }

    @Test
    fun `isGranted is false while the camera permission is denied`() {
        shadowOf(application).denyPermissions(Manifest.permission.CAMERA)

        assertFalse(permission.isGranted())
    }
}

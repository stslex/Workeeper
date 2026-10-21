// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.media

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The Android launcher under a recording [ActivityResultRegistry]: each verb launches its contract
 * with the right input, and each result reaches the right callback. The system camera and
 * photo-picker apps stay a device check (kmp-phase-7-15-exercise-feature.md §12.2).
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs (see `AccessibilitySemanticsTest`).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class ImageSourceLauncherTest {

    @Test
    fun launchesEachContractAndRoutesItsResult() = runComposeUiTest {
        val registry = RecordingRegistry()
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val picked = mutableListOf<String>()
        var cancels = 0
        val permissionResults = mutableListOf<Boolean>()
        lateinit var launcher: ImageSourceLauncher

        setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalContext provides context,
            ) {
                launcher = rememberImageSourceLauncher(
                    onImagePicked = { ref -> picked += ref },
                    onImagePickCancelled = { cancels += 1 },
                    onCameraPermissionResult = { granted -> permissionResults += granted },
                )
            }
        }

        // dispatchResult reaches a callback only for a launched key, so every answer needs its launch.
        val delivered = mutableListOf<Boolean>()
        fun launchAndAnswer(verb: ImageSourceLauncher.() -> Unit, result: Any?): Launch? {
            val before = registry.launches.size
            runOnIdle { launcher.verb() }
            val launch = registry.launches.getOrNull(before) ?: return null
            runOnIdle { delivered += registry.dispatchResult(launch.requestCode, result) }
            return launch
        }

        val capture = launchAndAnswer({ launchCamera(CAPTURE_1) }, result = true)
        val pickedAfterCapture = picked.toList()
        val cancelsAfterCapture = cancels

        val failedCapture = launchAndAnswer({ launchCamera(CAPTURE_2) }, result = false)
        val pickedAfterFailedCapture = picked.toList()
        val cancelsAfterFailedCapture = cancels

        val gallery = launchAndAnswer({ launchGallery() }, result = Uri.parse(PICKED))
        val pickedAfterGallery = picked.toList()

        val emptyGallery = launchAndAnswer({ launchGallery() }, result = null)
        val cancelsAfterEmptyGallery = cancels

        val grant = launchAndAnswer({ requestCameraPermission() }, result = true)
        val permissionAfterGrant = permissionResults.toList()

        val denial = launchAndAnswer({ requestCameraPermission() }, result = false)
        val permissionAfterDenial = permissionResults.toList()

        val launchesBeforeSettings = registry.launches.size
        runOnIdle { launcher.openAppSettings() }

        assertAll(
            // A capture that succeeds reports the target it was launched with.
            { assertTrue(capture?.contract is ActivityResultContracts.TakePicture, "capture contract") },
            { assertEquals(Uri.parse(CAPTURE_1), capture?.input, "capture input") },
            { assertEquals(listOf(CAPTURE_1), pickedAfterCapture, "capture picked") },
            { assertEquals(0, cancelsAfterCapture, "capture cancels") },
            // A capture that fails reports a cancel, never the target.
            {
                assertTrue(
                    failedCapture?.contract is ActivityResultContracts.TakePicture,
                    "failed capture contract",
                )
            },
            { assertEquals(listOf(CAPTURE_1), pickedAfterFailedCapture, "failed capture picked") },
            { assertEquals(1, cancelsAfterFailedCapture, "failed capture cancels") },
            // The picker asks for images only and reports the picked reference.
            { assertTrue(gallery?.contract is ActivityResultContracts.PickVisualMedia, "gallery contract") },
            {
                assertEquals(
                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                    (gallery?.input as? PickVisualMediaRequest)?.mediaType,
                    "gallery media type",
                )
            },
            { assertEquals(listOf(CAPTURE_1, PICKED), pickedAfterGallery, "gallery picked") },
            // A picker closed without a photo reports a cancel.
            {
                assertTrue(
                    emptyGallery?.contract is ActivityResultContracts.PickVisualMedia,
                    "empty gallery contract",
                )
            },
            { assertEquals(2, cancelsAfterEmptyGallery, "empty gallery cancels") },
            // The camera permission request and both of its answers.
            {
                assertTrue(
                    grant?.contract is ActivityResultContracts.RequestPermission,
                    "permission contract",
                )
            },
            { assertEquals(Manifest.permission.CAMERA, grant?.input, "permission input") },
            { assertEquals(listOf(true), permissionAfterGrant, "permission granted") },
            {
                assertTrue(
                    denial?.contract is ActivityResultContracts.RequestPermission,
                    "denied permission contract",
                )
            },
            { assertEquals(listOf(true, false), permissionAfterDenial, "permission denied") },
            // The settings link is an intent, not a contract.
            { assertEquals(launchesBeforeSettings, registry.launches.size, "settings launches") },
            { assertEquals(1, context.started.size, "settings started intents") },
            {
                assertEquals(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    context.started.singleOrNull()?.action,
                    "settings action",
                )
            },
            {
                assertEquals(
                    Uri.parse("package:" + context.packageName),
                    context.started.singleOrNull()?.data,
                    "settings data",
                )
            },
            {
                val flags = context.started.singleOrNull()?.flags ?: 0
                assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0, "settings new-task flag")
            },
            { assertEquals(SIX_LAUNCHES, registry.launches.size, "launches in all") },
            { assertEquals(List(SIX_LAUNCHES) { true }, delivered, "every result delivered") },
        )
    }

    private data class Launch(
        val requestCode: Int,
        val contract: ActivityResultContract<*, *>,
        val input: Any?,
    )

    /** Records every launch instead of starting an activity; results arrive by `dispatchResult`. */
    private class RecordingRegistry : ActivityResultRegistry() {

        val launches = mutableListOf<Launch>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launches += Launch(requestCode, contract, input)
        }
    }

    /** Records every started intent instead of starting it. */
    private class RecordingContext(base: Context) : ContextWrapper(base) {

        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
        }
    }

    private companion object {

        const val CAPTURE_1 = "content://test/capture-1.jpg"
        const val CAPTURE_2 = "content://test/capture-2.jpg"
        const val PICKED = "content://picker/7"
        const val SIX_LAUNCHES = 6
    }
}

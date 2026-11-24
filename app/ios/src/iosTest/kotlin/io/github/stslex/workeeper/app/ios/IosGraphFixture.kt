// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.stslex.workeeper.app.ios

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiGenerationsHolder
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.app.common.runtime.UiAdmissionGate
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.AppReinitializationHost
import io.github.stslex.workeeper.core.core.platform.CameraPermission
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpochSource
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.database.buildIosAppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class IosGraphFixture {
    private val manager = NSFileManager.defaultManager
    private val lifetimes = mutableListOf<AppScopeLifetime>()
    private val support = manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
    private val ownedDirectory = "${checkNotNull(support.path)}/Workeeper"
    val restartHost = RecordingRestartHost()

    init {
        val environment = NSProcessInfo.processInfo.environment
        val expected = checkNotNull(environment["WORKEEPER_DATABASE_TEST_SIMULATOR"] as? String)
        assertEquals(expected, environment["SIMULATOR_UDID"])
        assertTrue(NSHomeDirectory().contains("/$expected/data"), "Expected the newly owned simulator sandbox")
        assertTrue(checkNotNull(support.path).startsWith(NSHomeDirectory() + "/"))
        assertFalse(manager.fileExistsAtPath(ownedDirectory), "Refuse a pre-existing default database directory")
        NSUserDefaults.standardUserDefaults.setObject(listOf("en"), forKey = "AppleLanguages")
        NSUserDefaults.standardUserDefaults.setObject("en_US", forKey = "AppleLocale")
    }

    val database = runBlocking(Dispatchers.IO) { buildIosAppDatabase() }

    fun graph(
        lifetime: AppScopeLifetime = AppScopeLifetime(),
        installEpochSource: InstallEpochSource = object : InstallEpochSource {
            override suspend fun installEpoch() = InstallEpoch(
                RestoreOwnerId("00000000-0000-4000-8000-000000007020"),
            )
        },
    ): IosAppGraph {
        if (lifetime !in lifetimes) lifetimes += lifetime
        return buildIosAppGraph(
            database = database,
            lifetime = lifetime,
            imageStorage = UnusedImages,
            cameraPermission = object : CameraPermission {
                override fun isGranted() = false
            },
            resources = buildIosResourceWrapper(),
            installEpochSource = installEpochSource,
            reinitializationHost = restartHost,
        ) as IosAppGraph
    }

    suspend fun close() {
        lifetimes.forEach { it.cancelAndJoin() }
        withContext(Dispatchers.IO) {
            database.close()
            check(manager.removeItemAtPath(ownedDirectory, null))
        }
        // DataStore owns its live preference files until this test process exits.
    }
}

internal class RecordingRestartHost : AppReinitializationHost {
    var requests = 0
        private set

    override fun requestReinitialize() {
        requests++
    }
}

internal class GraphTestOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}

internal class GraphTestGenerations(owner: GraphTestOwner) : AppUiGenerationsHolder {
    override val appUiPhases = MutableStateFlow<AppUiPhase>(AppUiPhase.Generation(1, owner))
    val gate = UiAdmissionGate(Log.tag("GraphTestGenerations"))
    val admitted = mutableListOf<AppUiAdmissionToken>()
    val released = mutableListOf<AppUiAdmissionToken>()

    override fun admitUiGeneration(id: Int): AppUiAdmissionToken? =
        gate.admit(id)?.also(admitted::add)

    override fun releaseUiGeneration(token: AppUiAdmissionToken) {
        gate.release(token)
        released += token
    }
}

private object UnusedImages : ImageStorage {
    override suspend fun saveImage(sourceRef: ImageRef, exerciseUuid: String): Nothing = error("Unused image save")
    override suspend fun createTempCaptureRef(): Nothing = error("Unused camera capture")
    override suspend fun deleteImage(path: String): Nothing = error("Unused image deletion")
    override suspend fun lastModified(path: String): Nothing = error("Unused image metadata")
    override suspend fun cleanupTempFiles(): Nothing = error("Unused image cleanup")
}

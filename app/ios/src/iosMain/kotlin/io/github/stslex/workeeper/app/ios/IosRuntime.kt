// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.ios

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiGenerationsHolder
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.app.common.runtime.UiAdmissionGate
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.AppReinitializationHost
import io.github.stslex.workeeper.core.core.platform.CameraPermission
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.buildIosAppDatabase
import io.github.stslex.workeeper.core.data.database.common.DatabaseAdmission
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarGenerationTransition
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.Uuid

@OptIn(SnackbarGenerationTransition::class)
internal class IosRuntime(
    private val images: ImageStorage,
    private val cameraGranted: () -> Boolean,
    private val openDatabase: () -> AppDatabase = ::buildIosAppDatabase,
    private val deadlineMillis: Long = 10_000,
) : AppUiGenerationsHolder {

    private class Generation(val id: Int) : ViewModelStoreOwner {
        val lifetime = AppScopeLifetime()
        override val viewModelStore = ViewModelStore()
        lateinit var graph: AppRootDeps
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = UiAdmissionGate(Log.tag("IosUiAdmission"))
    private val users = UiAdmissionGate(Log.tag("IosDatabaseAdmission"))
    private val phases = MutableStateFlow<AppUiPhase>(AppUiPhase.Transitioning)
    override val appUiPhases = phases.asStateFlow()
    val problem = MutableStateFlow<IosHostProblem?>(null)
    private var database: AppDatabase? = null
    private var opening: Deferred<AppDatabase>? = null
    private var generation: Generation? = null
    private var sequence = 0
    private val working = MutableStateFlow(false)
    private var closing = false
    var isDisposed = false
        private set

    fun currentId(): Int? = (phases.value as? AppUiPhase.Generation)?.id

    fun resolve(): AppRootDeps {
        val owner = checkNotNull(generation)
        check(currentId() == owner.id && gate.admittedCount(owner.id) > 0)
        return owner.graph
    }

    override fun admitUiGeneration(id: Int): AppUiAdmissionToken? =
        if (currentId() == id) gate.admit(id) else null

    override fun releaseUiGeneration(token: AppUiAdmissionToken) = gate.release(token)

    private suspend fun <T> useDatabase(id: Int, block: suspend () -> T): T {
        val grant = users.admit(id) ?: throw CancellationException("Retired database user")
        try {
            if (id != currentId()) throw CancellationException("Retired database user")
            return block()
        } finally {
            users.release(grant)
        }
    }

    fun start() {
        if (database == null && problem.value == null) submit()
    }

    fun openRecovery(id: Int?) {
        if (id == null || currentId() != id) return
        problem.value = IosHostProblem.RecoveryRequested
        phases.value = AppUiPhase.Transitioning
    }

    fun retry() = submit()

    private fun submit(requester: Int? = null, close: Boolean = false) = scope.launch {
        if (close) closing = true
        if (isDisposed || requester != null && requester != currentId()) return@launch
        if (working.value && !close) return@launch
        if (working.value) {
            withTimeoutOrNull(deadlineMillis) { working.first { !it } } ?: return@launch
        }
        if (isDisposed) return@launch
        working.value = true
        val verifyExisting = problem.value != null
        phases.value = AppUiPhase.Transitioning
        problem.value = null
        val result = runCatching {
            generation?.let { retire(it) }
            if (!closing) initialize(verifyExisting)
            if (closing) {
                generation?.let { retire(it) }
                closeDatabase()
                isDisposed = true
            }
        }
        working.value = false
        result.onFailure { failure ->
            if (failure !is Exception) throw failure
            Log.tag("IosRuntime").e(failure, "Could not complete the application transition")
            problem.value = if (verifyExisting) IosHostProblem.RetryFailed else IosHostProblem.TransitionFailed
        }
    }

    private suspend fun initialize(verifyExisting: Boolean) {
        val existing = database
        val db = existing ?: awaitOpen().also { database = it }
        if (existing != null && verifyExisting) {
            withTimeout(deadlineMillis) { withContext(Dispatchers.IO) { db.trainingDao.getById(Uuid.NIL) } }
        }
        if (closing) return
        runCatching { publish(db) }.onFailure {
            generation?.let { retire(it) }
            if (existing == null) closeDatabase()
        }.getOrThrow()
    }

    private suspend fun closeDatabase() {
        val db = database ?: opening?.let { awaitOpen() } ?: return
        withContext(Dispatchers.IO) { db.close() }
        database = null
    }

    private suspend fun awaitOpen(): AppDatabase {
        val candidate = opening ?: scope.async(Dispatchers.IO) { open() }.also { opening = it }
        return runCatching { withTimeout(deadlineMillis) { candidate.await() } }
            .onSuccess { opening = null }
            .onFailure { if (candidate.isCancelled) opening = null }
            .getOrThrow()
    }

    private suspend fun open(): AppDatabase = withContext(Dispatchers.IO) {
        val db = openDatabase()
        runCatching {
            withTimeout(deadlineMillis) { db.trainingDao.getById(Uuid.NIL) }
            images.cleanupTempFiles()
            db
        }.onFailure { failure ->
            runCatching { db.close() }.exceptionOrNull()?.let(failure::addSuppressed)
        }.getOrThrow()
    }

    private fun publish(db: AppDatabase) {
        val next = Generation(++sequence)
        generation = next
        val id = next.id
        val restart = object : AppReinitializationHost, DatabaseAdmission {
            override fun requestReinitialize() {
                submit(id)
            }
            override suspend fun <T> use(block: suspend () -> T): T = useDatabase(id, block)
        }
        next.graph = buildIosAppGraph(
            database = db,
            lifetime = next.lifetime,
            imageStorage = images,
            cameraPermission = object : CameraPermission {
                override fun isGranted() = cameraGranted()
            },
            resources = buildIosResourceWrapper(),
            installEpochSource = buildIosInstallEpochSource(),
            reinitializationHost = restart,
        )
        SnackbarManager.unfenceResolves()
        phases.value = AppUiPhase.Generation(next.id, next)
    }

    private suspend fun retire(old: Generation) {
        check(gate.awaitRetired(old.id, deadlineMillis)) { "UI admission drain timed out" }
        check(users.awaitRetired(old.id, deadlineMillis)) { "Database admission drain timed out" }
        withTimeout(deadlineMillis) { SnackbarManager.fenceResolves() }
        old.viewModelStore.clear()
        withTimeout(deadlineMillis) { old.lifetime.cancelAndJoin() }
        SnackbarManager.advanceGenerationEpoch()
        generation = null
    }

    fun dispose() = submit(close = true)
}

internal enum class IosHostProblem { RecoveryRequested, TransitionFailed, RetryFailed }

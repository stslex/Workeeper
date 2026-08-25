// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStoreOwner
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.runtime.AppRuntime
import io.github.stslex.workeeper.runtime.clearStoreOnHostTeardown
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide resettable slot for the per-test Metro [AppGraph] and its published UI generation.
 * Lives in `:app:app` androidTest because only here can it name the module-`internal` [AppGraph].
 */
internal object MetroTestGraphHolder {

    @Volatile
    private var current: AppGraph? = null

    @Volatile
    private var currentStore: ViewModelStore? = null

    private val nextGenerationId = java.util.concurrent.atomic.AtomicInteger(1)

    private val uiPhaseFlow = MutableStateFlow<AppUiPhase>(AppUiPhase.Transitioning)

    /**
     * When set, [TestApplication] delegates the phase stream and the attach/dispose gate to this
     * real runtime; the default static mode serves [uiPhaseFlow] and counts [staticAttachments].
     */
    @Volatile
    var runtimeDelegate: AppRuntime? = null

    /** Static-mode per-id attachment counts — assertable by tests, mirroring the runtime's gate. */
    val staticAttachments = ConcurrentHashMap<Int, AtomicInteger>()

    /** What [TestApplication] serves as `appUiPhases` in static mode. */
    val uiPhases: StateFlow<AppUiPhase> = uiPhaseFlow.asStateFlow()

    fun effectiveUiPhases(): StateFlow<AppUiPhase> = runtimeDelegate?.uiPhases ?: uiPhases

    /** Ids the harness has retired; their admission must be refused. */
    private val retiredIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /**
     * Times a generation region resolved its app-scope deps. `App()` is the only caller, once per
     * region, so zero means "this region reached the graph zero times".
     */
    val appRootDepsResolutions = AtomicInteger()

    /** Static-mode admission token — real accounting, assertable by the harness tests. */
    private class StaticToken(val id: Int) : AppUiAdmissionToken

    /** Retires [id]: every later admission request for it is refused, as the runtime does. */
    fun retireUiGeneration(id: Int) {
        retiredIds.add(id)
    }

    /** Tokens currently outstanding for [id] — a leaked grant shows up here as a non-zero count. */
    fun outstandingAdmissions(id: Int): Int = staticAttachments[id]?.get() ?: 0

    /** The published generation's id, or `null` while the harness is between generations. */
    val currentGenerationId: Int?
        get() = (effectiveUiPhases().value as? AppUiPhase.Generation)?.id

    fun admitUiGeneration(id: Int): AppUiAdmissionToken? {
        if (retiredIds.contains(id)) return null
        // A real gate's REFUSAL is an answer, not an absence: returning a StaticToken here would
        // manufacture the grant the invariant exists to withhold, and would also miscount the
        // harness's own outstanding admissions.
        runtimeDelegate?.let { delegate -> return delegate.admitUiGeneration(id) }
        return StaticToken(id).also {
            staticAttachments.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
        }
    }

    fun releaseUiGeneration(token: AppUiAdmissionToken) {
        val delegate = runtimeDelegate
        if (delegate != null) {
            delegate.releaseUiGeneration(token)
            return
        }
        (token as? StaticToken)?.let {
            staticAttachments.computeIfAbsent(it.id) { AtomicInteger() }.decrementAndGet()
        }
    }

    /** The graph installed for the currently-running test. Throws if read before [MetroTestRule] sets it. */
    val graph: AppGraph
        get() = current ?: error(
            "No test AppGraph installed. A test that reads the app graph must run with MetroTestRule " +
                "(the rule builds and installs the graph in @Before).",
        )

    /**
     * Installs a graph as the current test generation; re-installing within one test is the
     * harness's generation swap. Ids increment per install so no swap reuses a saveable slot.
     */
    fun install(graph: AppGraph) {
        uiPhaseFlow.value = AppUiPhase.Transitioning
        // GUARD: clear on the main thread — disposal detaches a Lifecycle observer, which
        // Lifecycle enforces to the main thread. Production teardown does the same.
        currentStore?.let { store ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        }
        current = graph
        val store = ViewModelStore()
        currentStore = store
        uiPhaseFlow.value = AppUiPhase.Generation(
            id = nextGenerationId.getAndIncrement(),
            viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = store
            },
        )
    }

    /** The production host-teardown clear, routed to whichever generation source is installed. */
    fun onUiHostDestroyed() {
        val delegate = runtimeDelegate
        if (delegate != null) {
            delegate.clearStoreOnHostTeardown()
            return
        }
        // Already on the main thread: Activity lifecycle callbacks are dispatched there.
        currentStore?.clear()
    }

    fun reset() {
        runtimeDelegate = null
        retiredIds.clear()
        appRootDepsResolutions.set(0)
        staticAttachments.clear()
        uiPhaseFlow.value = AppUiPhase.Transitioning
        currentStore?.let { store ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        }
        currentStore = null
        current = null
    }

}

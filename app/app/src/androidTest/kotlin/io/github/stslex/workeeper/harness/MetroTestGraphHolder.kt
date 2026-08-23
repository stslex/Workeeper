// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStoreOwner
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.runtime.AppRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide, resettable slot for the per-test Metro [AppGraph] (App-Scope Collapse Step 6, Phase 3.3).
 *
 * Android instrumentation runs the whole suite in ONE process and creates the [TestApplication] exactly
 * once, so the app-scope graph cannot be a construction-time constant — fail-fast-DB and in-memory-DB
 * tests would collide on a single shared graph. [MetroTestRule] rebuilds a fresh graph per test and
 * publishes it here in `@Before`; [TestApplication.appGraph] reads it; `@After` clears it.
 *
 * Phase 5 adds the UI-generation face: the rule also publishes [AppUiPhase.Generation] `(1, a
 * fresh per-test ViewModelStore)` so `App()` composes — with no published generation the shell
 * shows only the Transitioning interstitial. The per-test store gives `AppRootViewModel` and the
 * app-dialog Store the same fresh-per-test lifecycle the per-test graph gives everything else,
 * and [reset] clears it so nothing retains a dead graph's deps across tests.
 *
 * Lives in `:app:app` androidTest so it can name the module-`internal` [AppGraph] — the entire reason the
 * harness consolidates here rather than in a shared upstream infra module (which cannot see it).
 */
internal object MetroTestGraphHolder {

    @Volatile
    private var current: AppGraph? = null

    @Volatile
    private var currentStore: ViewModelStore? = null

    private val nextGenerationId = java.util.concurrent.atomic.AtomicInteger(1)

    private val uiPhaseFlow = MutableStateFlow<AppUiPhase>(AppUiPhase.Transitioning)

    /**
     * RUNTIME MODE (Phase 5 handshake tests): when set, [TestApplication] delegates the UI
     * phase stream AND the attach/dispose gate to this real [AppRuntime] — the production
     * handshake, not a harness stand-in. Static mode (the default) serves [uiPhaseFlow] and
     * counts attachments in [staticAttachments] so the callbacks are REAL accounting either way
     * (the interface has no silent no-op defaults by design).
     */
    @Volatile
    var runtimeDelegate: AppRuntime? = null

    /** Static-mode per-id attachment counts — assertable by tests, mirroring the runtime's gate. */
    val staticAttachments = ConcurrentHashMap<Int, AtomicInteger>()

    /** What [TestApplication] serves as `appUiPhases` in static mode. */
    val uiPhases: StateFlow<AppUiPhase> = uiPhaseFlow.asStateFlow()

    fun effectiveUiPhases(): StateFlow<AppUiPhase> = runtimeDelegate?.uiPhases ?: uiPhases

    /**
     * Ids the harness has RETIRED (Phase 5 R3, spec §8.4 step 1). A retired generation's admission
     * must be refused, which is what `App()`'s region turns into "render nothing and resolve
     * nothing". `UiAdmissionRaceTest` drives this; [reset] clears it.
     */
    private val retiredIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /**
     * How many times `App()`'s generation region resolved its app-scope dependencies — counted at
     * [TestApplication.appRootDeps], which `App()` calls exactly once per composed region and
     * nothing else in the app calls at all. Zero is the assertable meaning of "a stale region
     * resolved nothing".
     */
    val appRootDepsResolutions = AtomicInteger()

    /** Static-mode admission token — real accounting, assertable by the harness tests. */
    private class StaticToken(val id: Int) : AppUiAdmissionToken

    /**
     * Retires [id]: every later admission request for it is REFUSED, exactly as the runtime's own
     * gate refuses a generation it has already handed over.
     */
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
        return runtimeDelegate?.admitUiGeneration(id)
            ?: StaticToken(id).also {
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
     * Installs a graph as the CURRENT test generation. Re-installing within one test is the
     * harness's generation swap (the UI-swap suite uses it): the previous generation's
     * ViewModelStore is cleared deterministically (mirroring the runtime's disposal) and a fresh
     * id is published, which is what re-keys `App()`'s generation region. Ids increment per
     * install — stable WITHIN a test (Activity recreation restores against the same id), fresh
     * ACROSS installs (a swap must never reuse the old saveable slot).
     */
    fun install(graph: AppGraph) {
        uiPhaseFlow.value = AppUiPhase.Transitioning
        // On the MAIN thread, as the production teardown does (`AppRuntime.tearDownOutgoing`
        // wraps its clear in `policy.mainDispatcher`): since R3 a cleared Store actually
        // disposes, and disposal detaches a `LifecycleRegistry` observer, which Lifecycle
        // enforces to the main thread.
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

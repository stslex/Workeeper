// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.test.TestActivity
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies the screen-less [AppFeature] entry resolves its Store at the host Activity's
 * `ViewModelStore`. See `documentation/feature-specs/app-dialogs.md` → "AppDialogHost mounting".
 */
@Smoke
@RunWith(AndroidJUnit4::class)
internal class AppFeatureScopeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    // The lifetime the probe Store is constructed with — the same object the assertion below
    // cancels. No Context seam is involved any more; injection is direct.
    private val appScopeLifetime = AppScopeLifetime()
    private val probeFeature = AppRootProbeFeature(appScopeLifetime)

    @Test
    fun appFeatureProcessorResolvesAtActivityScope() {
        var capturedOwner: ViewModelStoreOwner? = null
        var capturedLifecycle: LifecycleOwner? = null
        var capturedStore: AppRootProbeStoreImpl? = null

        composeRule.setContent {
            // At App-root depth, so LocalViewModelStoreOwner must be the host Activity.
            probeFeature.processor()

            // The retained instance, read back through the same `viewModel<T>()` path.
            val directStore: AppRootProbeStoreImpl = viewModel()

            // Owner snapshots from the composition AppFeature is composed in.
            val owner = LocalViewModelStoreOwner.current
            val lifecycleOwner = LocalLifecycleOwner.current

            SideEffect {
                capturedOwner = owner
                capturedLifecycle = lifecycleOwner
                capturedStore = directStore
            }
        }

        composeRule.waitForIdle()

        val activity = composeRule.activity
        val owner = checkNotNull(capturedOwner) { "LocalViewModelStoreOwner not resolved" }
        val parentLifecycle = checkNotNull(capturedLifecycle) { "LifecycleOwner not resolved" }
        val directStore = checkNotNull(capturedStore) { "Probe Store not resolved in composition" }

        // (1) Activity owns the ViewModelStore the Store was scoped to.
        assertSame(
            "AppFeature must resolve to the host Activity's ViewModelStore",
            activity.viewModelStore,
            owner.viewModelStore,
        )

        // (2) The retained instance IS the entry in the Activity's ViewModelStore; a silent
        // rescope to another owner would surface here as a different instance.
        val viaActivity = ViewModelProvider(activity)[AppRootProbeStoreImpl::class.java]
        assertSame(
            "Store fetched from the Activity must be the same instance AppFeature wired up",
            directStore,
            viaActivity,
        )

        // (3) The parent composition is Activity-owned, but rememberLifecycleOwner creates the
        // child that production passes to BaseStore.init.
        assertSame(
            "the App-root parent lifecycle must be the host Activity",
            activity,
            parentLifecycle,
        )
        val initializedOwner = directStore.initializedLifecycleOwner()
        assertNotSame(
            "BaseStore.init must receive rememberLifecycleOwner's child, not the parent Activity",
            activity,
            initializedOwner,
        )
        assertLifecycleFollowsActivity(activity, initializedOwner, Lifecycle.State.RESUMED)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        assertLifecycleFollowsActivity(activity, initializedOwner, Lifecycle.State.CREATED)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertLifecycleFollowsActivity(activity, initializedOwner, Lifecycle.State.RESUMED)
    }

    /**
     * The generation seam on-device: a Store job started via `launchDefault` must be a descendant
     * of the exact lifetime the Store was CONSTRUCTED with, so `cancelAndJoin` waits for its
     * `finally`. Direct injected identity, not a Context lookup.
     */
    @Test
    fun storeJobsAreDescendantsOfTheGenerationInjectedIntoTheStore() {
        var capturedStore: AppRootProbeStoreImpl? = null

        composeRule.setContent {
            probeFeature.processor()
            val directStore: AppRootProbeStoreImpl = viewModel()
            SideEffect { capturedStore = directStore }
        }

        composeRule.waitForIdle()
        val store = checkNotNull(capturedStore) { "Probe Store not resolved in composition" }

        val started = CountDownLatch(1)
        val finallyRan = AtomicBoolean(false)
        store.launchDefault(
            onError = {},
            onSuccess = {},
            action = {
                try {
                    started.countDown()
                    awaitCancellation()
                } finally {
                    // Stands in for cleanup that touches the generation's database.
                    finallyRan.set(true)
                }
            },
        )
        check(started.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "probe job never started" }

        runBlocking { appScopeLifetime.cancelAndJoin() }

        assertTrue(
            "cancelAndJoin on the generation lifetime returned while a Store job's finally had " +
                "not run — the Store's jobs are not parented to the generation it was " +
                "constructed with, so the runtime would close the database under them",
            finallyRan.get(),
        )
    }

    private companion object {
        const val WAIT_SECONDS = 5L
    }

    private fun assertLifecycleFollowsActivity(
        activity: TestActivity,
        initializedOwner: LifecycleOwner,
        expectedState: Lifecycle.State,
    ) {
        composeRule.runOnIdle {
            assertEquals(
                "host Activity did not reach $expectedState",
                expectedState,
                activity.lifecycle.currentState,
            )
            assertEquals(
                "the lifecycle retained by the Store did not follow its Activity parent to " +
                    expectedState,
                activity.lifecycle.currentState,
                initializedOwner.lifecycle.currentState,
            )
        }
    }
}

/** Reads the owner retained by the production scope so the assertion cannot inspect a sibling. */
private fun AppRootProbeStoreImpl.initializedLifecycleOwner(): LifecycleOwner {
    val scopeField = BaseStore::class.java.getDeclaredField("_scope").apply {
        isAccessible = true
    }
    val scope = checkNotNull(scopeField.get(this)) { "Store scope was not initialized" }
    val ownerField = scope.javaClass.getDeclaredField("lifecycleOwner").apply {
        isAccessible = true
    }
    return checkNotNull(ownerField.get(scope) as? LifecycleOwner) {
        "Store scope did not retain a LifecycleOwner"
    }
}

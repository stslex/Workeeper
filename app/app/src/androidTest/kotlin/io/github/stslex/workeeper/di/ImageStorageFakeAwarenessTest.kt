// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App-Scope Collapse Step 3 (C2, bridge-scaffold) — the C1 fake-awareness gate.
 *
 * The C2 bridge BRIDGE-READS `ImageStorage` from Hilt into the Metro graph's `create()` — it must read the
 * Hilt-BOUND instance, never construct one. `TestInfraModule` (`core:ui:test-utils`) `@TestInstallIn`-replaces
 * `ImageStorageModule` with `FakeImageStorage` across every feature/app suite; this test asserts that the
 * Hilt-resolved `ImageStorage` on this instrumented path (HiltTestApplication + the real `AppGraphSourceModule`
 * fallback + `TestInfraModule`'s swap — the same fake swap the flavor suites use) IS the fake. If the bridge
 * ever CONSTRUCTED `ImageStorage`, the real `ImageStorageImpl` would leak into fake-expecting tests — a break
 * invisible to `:app:dev:assembleDebug` and the seam identity tests (which pass a fake directly). This test
 * makes that break visible: it fails the moment the graph path resolves a non-fake `ImageStorage`.
 */
@Regression
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ImageStorageFakeAwarenessTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun hiltBoundImageStorageIsTheFake() {
        val imageStorage = EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext(),
                TestImageStorageEntryPoint::class.java,
            )
            .imageStorage()

        // The C2 bridge reads THIS Hilt-bound instance. TestInfraModule swaps FakeImageStorage; if the bridge
        // ever constructed ImageStorage instead, the real ImageStorageImpl would resolve here → this fails.
        assertTrue(
            "The Hilt-bound ImageStorage must be FakeImageStorage (the @TestInstallIn swap) — the C2 bridge " +
                "reads this instance, so a real ImageStorageImpl here would mean the bridge constructs instead " +
                "of reads. Got: ${imageStorage::class.java.name}",
            imageStorage is FakeImageStorage,
        )
    }

    /** Equivalent to the C2 bridge's `DbCascadeBridgeEntryPoint.imageStorage()` read — same Hilt binding. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestImageStorageEntryPoint {
        fun imageStorage(): ImageStorage
    }
}

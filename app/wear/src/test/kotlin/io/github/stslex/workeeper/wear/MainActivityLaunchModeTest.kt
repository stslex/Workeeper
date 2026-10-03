// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * wear-live-sync.md §7.5 (D5): a Tile tap carries no intent flags, so only the manifest's
 * `singleTop` keeps it from stacking a second controller over a launcher-started one.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class MainActivityLaunchModeTest {

    @Test
    fun `the controller activity is single top`() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode)
    }
}

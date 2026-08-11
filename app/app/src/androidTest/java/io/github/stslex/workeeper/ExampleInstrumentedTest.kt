package io.github.stslex.workeeper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 *
 * App-Scope Collapse Step 6 (Phase 3.3): de-Hilt'd — no graph needed (asserts only the target package),
 * so it does not use `MetroTestRule`. Boots under the consolidated [io.github.stslex.workeeper.harness.MetroTestRunner].
 */
@Regression
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.stslex.workeeper.app.app.test", appContext.packageName)
    }
}

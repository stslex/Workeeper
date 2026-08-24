package io.github.stslex.workeeper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented smoke test: asserts the target package. Needs no graph, so no `MetroTestRule`. */
@Regression
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.stslex.workeeper.app.app.test", appContext.packageName)
    }
}

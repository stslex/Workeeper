// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.feature.settings.mvi.mapper.SettingsAuthMapper
import io.github.stslex.workeeper.feature.settings.ui.platform.SettingsAuthLauncher
import io.github.stslex.workeeper.feature.settings.ui.platform.SettingsLinkLauncher
import io.github.stslex.workeeper.feature.settings.ui.platform.rememberSettingsAuthLauncher
import io.github.stslex.workeeper.feature.settings.ui.platform.rememberSettingsLinkLauncher
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.Executable
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class SettingsLaunchersTest {
    @Test
    fun launchesAndroidContractsAndRoutesResultsToCurrentCallbacks() = runComposeUiTest {
        val registry = RecordingRegistry()
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val revision = mutableStateOf(0)
        val results = mutableListOf<Pair<Int, Intent?>>()
        lateinit var auth: SettingsAuthLauncher
        lateinit var links: SettingsLinkLauncher
        setContent {
            val callbackRevision = revision.value
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalContext provides context,
            ) {
                auth = rememberSettingsAuthLauncher { result ->
                    results += callbackRevision to SettingsAuthMapper.toIntent(result)
                }
                links = rememberSettingsLinkLauncher()
            }
        }
        val sender = PendingIntent.getActivity(
            context,
            364,
            Intent("workeeper.settings.auth"),
            PendingIntent.FLAG_IMMUTABLE,
        ).intentSender
        val response = Intent("workeeper.settings.response")
        runOnIdle { auth.launch(SettingsAuthMapper.toRequest(sender)) }
        val first = registry.launches.single()
        assertTrue(first.contract is ActivityResultContracts.StartIntentSenderForResult)
        assertSame(sender, (first.input as IntentSenderRequest).intentSender)
        runOnIdle {
            assertTrue(
                registry.dispatchResult(first.requestCode, ActivityResult(Activity.RESULT_OK, response)),
            )
        }
        assertEquals(1, results.size, "successful auth result must reach its callback")
        assertEquals(0, results.single().first)
        assertSame(response, results.single().second)

        runOnIdle { revision.value++ }
        waitForIdle()
        runOnIdle { auth.launch(SettingsAuthMapper.toRequest(sender)) }
        assertEquals(2, registry.launches.size)
        runOnIdle {
            assertTrue(
                registry.dispatchResult(
                    registry.launches.last().requestCode,
                    ActivityResult(Activity.RESULT_CANCELED, null),
                ),
            )
        }
        assertEquals(2, results.size)
        assertEquals(1, results.last().first, "auth must use the current callback")
        assertNull(results.last().second)

        val cancelledData = Intent("workeeper.settings.cancelled-response")
        runOnIdle { auth.launch(SettingsAuthMapper.toRequest(sender)) }
        runOnIdle {
            assertTrue(
                registry.dispatchResult(
                    registry.launches.last().requestCode,
                    ActivityResult(Activity.RESULT_CANCELED, cancelledData),
                ),
            )
        }
        assertEquals(3, results.size)
        assertEquals(1, results.last().first, "auth must use the current callback")
        assertSame(cancelledData, results.last().second, "cancelled auth keeps its non-null payload")

        val url = "https://example.com/workeeper?source=settings#privacy"
        runOnIdle { links.launch(url) }
        val opened = context.started.single()
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals(Uri.parse(url), opened.data)
        assertTrue(opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0, "settings links require NEW_TASK")
        context.rejectLaunch = true
        runOnIdle { assertDoesNotThrow(Executable { links.launch(url) }, "missing link activities are handled") }
        assertEquals(2, context.started.size)
    }

    private data class Launch(
        val requestCode: Int,
        val contract: ActivityResultContract<*, *>,
        val input: Any?,
    )

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

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        var rejectLaunch = false

        override fun startActivity(intent: Intent) {
            started += intent
            if (rejectLaunch) throw ActivityNotFoundException("No activity for the controlled request")
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.app.Application
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Tasks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = DriveTokenInvalidatorTest.TestApplication::class, sdk = [33])
internal class DriveTokenInvalidatorTest {

    class TestApplication : Application()

    private val accountStore = mockk<AccountDataStore>(relaxed = true)
    private val authorizationClient = mockk<AuthorizationClient>()

    private fun newInvalidator() = DriveTokenInvalidator(
        authorizationClient = authorizationClient,
        accountStore = accountStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `invalidate with cached token clears DataStore then calls GMS clearToken`() = runTest {
        coEvery { accountStore.token() } returns TokenSnapshot(
            token = "bad-token",
            expiresAtEpochMs = Long.MAX_VALUE,
        )
        val captured = slot<ClearTokenRequest>()
        every { authorizationClient.clearToken(capture(captured)) } returns Tasks.forResult(null)

        newInvalidator().invalidate()

        coVerify(exactly = 1) { accountStore.clearToken() }
        assertEquals("bad-token", captured.captured.token)
    }

    @Test
    fun `invalidate without cached token still clears DataStore and skips GMS clearToken`() =
        runTest {
            coEvery { accountStore.token() } returns null

            newInvalidator().invalidate()

            coVerify(exactly = 1) { accountStore.clearToken() }
            coVerify(exactly = 0) { authorizationClient.clearToken(any()) }
        }

    @Test
    fun `invalidate swallows GMS clearToken failure as best-effort`() = runTest {
        coEvery { accountStore.token() } returns TokenSnapshot(
            token = "bad-token",
            expiresAtEpochMs = Long.MAX_VALUE,
        )
        every { authorizationClient.clearToken(any()) } returns
            Tasks.forException(ApiException(Status.RESULT_INTERNAL_ERROR))

        newInvalidator().invalidate()

        coVerify(exactly = 1) { accountStore.clearToken() }
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.app.Application
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Tasks
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = DriveAuthTokenProviderTest.TestApplication::class, sdk = [33])
internal class DriveAuthTokenProviderTest {

    class TestApplication : Application()

    private val accountFlow =
        MutableStateFlow<Account?>(Account(email = "user@example.com", displayName = null))
    private val accountStore = mockk<AccountDataStore>(relaxed = true).also {
        every { it.observeAccount() } returns accountFlow
    }
    private val authorizationClient = mockk<AuthorizationClient>()

    private fun newProvider() = DriveAuthTokenProvider(
        authorizationClient = authorizationClient,
        accountStore = accountStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `returns cached token when not expired and never calls authorize`() = runTest {
        coEvery { accountStore.token() } returns TokenSnapshot(
            token = "cached-token",
            expiresAtEpochMs = Long.MAX_VALUE,
        )

        val token = newProvider().currentToken()

        assertEquals("cached-token", token)
        coVerify(exactly = 0) { authorizationClient.authorize(any()) }
        coVerify(exactly = 0) { accountStore.setToken(any(), any()) }
    }

    @Test
    fun `expired cached token triggers authorize and writes refreshed value to cache`() =
        runTest {
            coEvery { accountStore.token() } returns TokenSnapshot(
                token = "stale-token",
                expiresAtEpochMs = 0L,
            )
            val authResult = mockk<AuthorizationResult> {
                every { hasResolution() } returns false
                every { accessToken } returns "fresh-token"
                every { grantedScopes } returns emptyList()
            }
            every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

            val token = newProvider().currentToken()

            assertEquals("fresh-token", token)
            val capturedToken = slot<String>()
            val capturedExpiry = slot<Long>()
            coVerify(exactly = 1) {
                accountStore.setToken(capture(capturedToken), capture(capturedExpiry))
            }
            assertEquals("fresh-token", capturedToken.captured)
            assertTrue(
                capturedExpiry.captured > System.currentTimeMillis(),
                "expiry must be in the future, got ${capturedExpiry.captured}",
            )
        }

    @Test
    fun `authorize returning null token does not crash and returns null`() = runTest {
        coEvery { accountStore.token() } returns null
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns true
            every { accessToken } returns null
            every { grantedScopes } returns emptyList()
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        val token = newProvider().currentToken()

        assertNull(token)
        coVerify(exactly = 0) { accountStore.setToken(any(), any()) }
    }

    @Test
    fun `authorize throwing does not crash and returns null`() = runTest {
        coEvery { accountStore.token() } returns null
        every { authorizationClient.authorize(any()) } returns
            Tasks.forException(ApiException(Status.RESULT_INTERNAL_ERROR))

        val token = newProvider().currentToken()

        assertNull(token)
        coVerify(exactly = 0) { accountStore.setToken(any(), any()) }
    }

    @Test
    fun `no account skips cache and authorize and returns null`() = runTest {
        accountFlow.value = null

        val token = newProvider().currentToken()

        assertNull(token)
        coVerify(exactly = 0) { accountStore.token() }
        coVerify(exactly = 0) { authorizationClient.authorize(any()) }
    }

    @Test
    fun `no cached token falls through to authorize`() = runTest {
        coEvery { accountStore.token() } returns null
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { accessToken } returns "first-token"
            every { grantedScopes } returns emptyList()
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        val token = newProvider().currentToken()

        assertEquals("first-token", token)
        coVerify(exactly = 1) { accountStore.setToken("first-token", any()) }
    }
}

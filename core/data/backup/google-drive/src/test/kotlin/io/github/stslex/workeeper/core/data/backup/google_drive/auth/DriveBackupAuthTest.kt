// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Tasks
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = DriveBackupAuthTest.TestApplication::class, sdk = [33])
internal class DriveBackupAuthTest {

    class TestApplication : Application()

    private val accountFlow = MutableStateFlow<Account?>(null)
    private val accountStore = mockk<AccountDataStore>(relaxed = true).also {
        every { it.observeAccount() } returns accountFlow
        coEvery { it.setAccount(any()) } coAnswers {
            accountFlow.value = firstArg()
        }
        coEvery { it.clear() } coAnswers { accountFlow.value = null }
    }

    private val authorizationClient = mockk<AuthorizationClient>()

    private fun newAuth() = DriveBackupAuth(
        authorizationClient = authorizationClient,
        accountStore = accountStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `signIn happy path returns Success and persists account`() = runTest {
        val gsa = mockk<GoogleSignInAccount> {
            every { email } returns "user@example.com"
            every { displayName } returns "User One"
        }
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { toGoogleSignInAccount() } returns gsa
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        val driveAuth = newAuth()
        val result = driveAuth.signIn()

        assertTrue(result is SignInResult.Success, "expected Success, got $result")
        assertEquals(
            Account(email = "user@example.com", displayName = "User One"),
            (result as SignInResult.Success).account,
        )
        coVerify { accountStore.setAccount(result.account) }
    }

    @Test
    fun `signIn returns NeedsResolution when AuthorizationResult demands resolution`() = runTest {
        val intentSender = mockk<IntentSender>()
        val pendingIntent = mockk<PendingIntent> {
            every { this@mockk.intentSender } returns intentSender
        }
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns true
            every { this@mockk.pendingIntent } returns pendingIntent
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        val driveAuth = newAuth()
        val result = driveAuth.signIn()

        assertTrue(result is SignInResult.NeedsResolution, "expected NeedsResolution, got $result")
        assertEquals(intentSender, (result as SignInResult.NeedsResolution).intentSender)
        coVerify(exactly = 0) { accountStore.setAccount(any()) }
    }

    @Test
    fun `signIn returns Failure when AuthorizationClient throws ApiException`() = runTest {
        every { authorizationClient.authorize(any()) } returns
            Tasks.forException(ApiException(Status.RESULT_INTERNAL_ERROR))

        val driveAuth = newAuth()
        val result = driveAuth.signIn()

        assertTrue(result is SignInResult.Failure, "expected Failure, got $result")
        // ApiException is mapped to BackupError.Unknown via DriveErrorMapper (not a typed Drive case).
        assertNotNull((result as SignInResult.Failure).error)
    }

    @Test
    fun `completeSignIn with valid Intent returns Success and persists account`() = runTest {
        val intent = mockk<Intent>()
        val gsa = mockk<GoogleSignInAccount> {
            every { email } returns "resolved@example.com"
            every { displayName } returns null
        }
        val authResult = mockk<AuthorizationResult> {
            every { toGoogleSignInAccount() } returns gsa
        }
        every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns authResult

        val driveAuth = newAuth()
        val result = driveAuth.completeSignIn(intent)

        assertTrue(result is BackupResult.Success, "expected Success, got $result")
        assertEquals(
            Account(email = "resolved@example.com", displayName = null),
            (result as BackupResult.Success).data,
        )
        coVerify { accountStore.setAccount(result.data) }
    }

    @Test
    fun `completeSignIn with null Intent returns Failure`() = runTest {
        val driveAuth = newAuth()
        val result = driveAuth.completeSignIn(null)

        assertTrue(result is BackupResult.Failure)
        assertTrue((result as BackupResult.Failure).error is BackupError.Unknown)
        coVerify(exactly = 0) { accountStore.setAccount(any()) }
    }

    @Test
    fun `signOut clears accountStore and returns Success`() = runTest {
        accountFlow.value = Account(email = "x@y.com", displayName = null)
        val driveAuth = newAuth()

        val result = driveAuth.signOut()

        assertEquals(BackupResult.Success(Unit), result)
        coVerify { accountStore.clear() }
    }

    @Test
    fun `state emits SignedOut then SignedIn after setAccount`() = runTest {
        val driveAuth = newAuth()
        advanceUntilIdle()
        assertEquals(AuthState.SignedOut, driveAuth.state.value)

        val account = Account(email = "newuser@example.com", displayName = "New User")
        accountStore.setAccount(account)
        advanceUntilIdle()

        assertEquals(AuthState.SignedIn(account), driveAuth.state.value)
    }
}

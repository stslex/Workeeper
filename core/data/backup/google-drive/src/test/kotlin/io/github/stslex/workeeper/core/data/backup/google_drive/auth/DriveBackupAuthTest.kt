// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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
import io.mockk.slot
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
        coEvery { it.token() } returns null
    }

    private val authorizationClient = mockk<AuthorizationClient>()
    private val userInfoFetcher = mockk<UserInfoFetcher> {
        coEvery { fetch(any()) } returns null
    }

    private fun newAuth() = DriveBackupAuth(
        authorizationClient = authorizationClient,
        accountStore = accountStore,
        userInfoFetcher = userInfoFetcher,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `signIn happy path returns Success with GSA-derived email when userinfo null`() = runTest {
        val gsa = mockk<GoogleSignInAccount> {
            every { email } returns "user@example.com"
            every { displayName } returns "User One"
        }
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { toGoogleSignInAccount() } returns gsa
            every { accessToken } returns null
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        val result = newAuth().signIn()

        assertTrue(result is SignInResult.Success, "expected Success, got $result")
        assertEquals(
            Account(email = "user@example.com", displayName = "User One"),
            (result as SignInResult.Success).account,
        )
        coVerify { accountStore.setAccount(result.account) }
    }

    @Test
    fun `signIn requests all three scopes (drive_appdata + userinfo email + userinfo profile)`() =
        runTest {
            val authResult = mockk<AuthorizationResult> {
                every { hasResolution() } returns false
                every { toGoogleSignInAccount() } returns null
                every { accessToken } returns null
            }
            val captured = slot<com.google.android.gms.auth.api.identity.AuthorizationRequest>()
            every { authorizationClient.authorize(capture(captured)) } returns
                Tasks.forResult(authResult)

            newAuth().signIn()

            val scopes = captured.captured.requestedScopes.map(Scope::getScopeUri).toSet()
            assertEquals(
                setOf(
                    DriveAuthScopes.DRIVE_APPDATA,
                    DriveAuthScopes.USERINFO_EMAIL,
                    DriveAuthScopes.USERINFO_PROFILE,
                ),
                scopes,
            )
        }

    @Test
    fun `signIn success with access token persists token via setToken`() = runTest {
        val gsa = mockk<GoogleSignInAccount> {
            every { email } returns "user@example.com"
            every { displayName } returns null
        }
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { toGoogleSignInAccount() } returns gsa
            every { accessToken } returns "live-token-abc"
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        newAuth().signIn()

        coVerify(exactly = 1) {
            accountStore.setToken(
                token = "live-token-abc",
                expiresAtEpochMs = any(),
            )
        }
    }

    @Test
    fun `signIn fetches userinfo when access token present and prefers its email + name`() =
        runTest {
            val gsa = mockk<GoogleSignInAccount> {
                every { email } returns "stale@example.com"
                every { displayName } returns "Stale Name"
            }
            val authResult = mockk<AuthorizationResult> {
                every { hasResolution() } returns false
                every { toGoogleSignInAccount() } returns gsa
                every { accessToken } returns "live-token"
            }
            every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)
            coEvery { userInfoFetcher.fetch("live-token") } returns UserInfo(
                email = "real@example.com",
                name = "Real Name",
            )

            val result = newAuth().signIn()

            assertTrue(result is SignInResult.Success)
            assertEquals(
                Account(email = "real@example.com", displayName = "Real Name"),
                (result as SignInResult.Success).account,
            )
        }

    @Test
    fun `signIn does not fetch userinfo when access token is null`() = runTest {
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { toGoogleSignInAccount() } returns mockk {
                every { email } returns "x@y.com"
                every { displayName } returns null
            }
            every { accessToken } returns null
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        newAuth().signIn()

        coVerify(exactly = 0) { userInfoFetcher.fetch(any()) }
    }

    @Test
    fun `signIn falls back to placeholder when neither userinfo nor GSA has email`() = runTest {
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { toGoogleSignInAccount() } returns null
            every { accessToken } returns null
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        val result = newAuth().signIn()

        assertTrue(result is SignInResult.Success)
        assertEquals(
            "drive_account",
            (result as SignInResult.Success).account.email,
        )
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

        val result = newAuth().signIn()

        assertTrue(result is SignInResult.NeedsResolution, "expected NeedsResolution, got $result")
        assertEquals(intentSender, (result as SignInResult.NeedsResolution).intentSender)
        coVerify(exactly = 0) { accountStore.setAccount(any()) }
    }

    @Test
    fun `signIn returns Failure when AuthorizationClient throws ApiException`() = runTest {
        every { authorizationClient.authorize(any()) } returns
            Tasks.forException(ApiException(Status.RESULT_INTERNAL_ERROR))

        val result = newAuth().signIn()

        assertTrue(result is SignInResult.Failure, "expected Failure, got $result")
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
            every { accessToken } returns null
        }
        every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns authResult

        val result = newAuth().completeSignIn(intent)

        assertTrue(result is BackupResult.Success, "expected Success, got $result")
        assertEquals(
            Account(email = "resolved@example.com", displayName = null),
            (result as BackupResult.Success).data,
        )
        coVerify { accountStore.setAccount(result.data) }
    }

    @Test
    fun `completeSignIn with access token persists token via setToken`() = runTest {
        val intent = mockk<Intent>()
        val gsa = mockk<GoogleSignInAccount> {
            every { email } returns "resolved@example.com"
            every { displayName } returns null
        }
        val authResult = mockk<AuthorizationResult> {
            every { toGoogleSignInAccount() } returns gsa
            every { accessToken } returns "resolved-token-xyz"
        }
        every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns authResult

        newAuth().completeSignIn(intent)

        coVerify(exactly = 1) {
            accountStore.setToken(
                token = "resolved-token-xyz",
                expiresAtEpochMs = any(),
            )
        }
    }

    @Test
    fun `completeSignIn uses userinfo email when fetcher succeeds`() = runTest {
        val intent = mockk<Intent>()
        val authResult = mockk<AuthorizationResult> {
            every { toGoogleSignInAccount() } returns null
            every { accessToken } returns "resolved-token"
        }
        every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns authResult
        coEvery { userInfoFetcher.fetch("resolved-token") } returns UserInfo(
            email = "userinfo@example.com",
            name = "From Userinfo",
        )

        val result = newAuth().completeSignIn(intent)

        assertTrue(result is BackupResult.Success)
        assertEquals(
            Account(email = "userinfo@example.com", displayName = "From Userinfo"),
            (result as BackupResult.Success).data,
        )
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
    fun `signOut calls authorizationClient revokeAccess with all scopes`() = runTest {
        accountFlow.value = Account(email = "x@y.com", displayName = null)
        val captured = slot<RevokeAccessRequest>()
        every { authorizationClient.revokeAccess(capture(captured)) } returns
            Tasks.forResult(null)

        val result = newAuth().signOut()

        assertEquals(BackupResult.Success(Unit), result)
        val scopes = captured.captured.scopes.map(Scope::getScopeUri).toSet()
        assertEquals(
            setOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            ),
            scopes,
        )
        coVerify { accountStore.clearToken() }
        coVerify { accountStore.clear() }
    }

    @Test
    fun `signOut clears local store even when revokeAccess fails`() = runTest {
        accountFlow.value = Account(email = "x@y.com", displayName = null)
        every { authorizationClient.revokeAccess(any()) } returns
            Tasks.forException(ApiException(Status.RESULT_INTERNAL_ERROR))

        val result = newAuth().signOut()

        assertEquals(BackupResult.Success(Unit), result)
        coVerify { accountStore.clearToken() }
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

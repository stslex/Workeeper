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
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthResolutionOutcome
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
        coEvery { it.account() } coAnswers { accountFlow.value }
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
        lifetime = AppScopeLifetime(),
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
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
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
                every { grantedScopes } returns listOf(
                    DriveAuthScopes.DRIVE_APPDATA,
                    DriveAuthScopes.USERINFO_EMAIL,
                    DriveAuthScopes.USERINFO_PROFILE,
                )
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
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
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
                every { grantedScopes } returns listOf(
                    DriveAuthScopes.DRIVE_APPDATA,
                    DriveAuthScopes.USERINFO_EMAIL,
                    DriveAuthScopes.USERINFO_PROFILE,
                )
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
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
            every { toGoogleSignInAccount() } returns mockk {
                every { email } returns "x@example.com"
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
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
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
        assertEquals(intentSender, (result as SignInResult.NeedsResolution).resolution.platform)
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
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
            every { toGoogleSignInAccount() } returns gsa
            every { accessToken } returns null
        }
        every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns authResult

        val result = newAuth().completeSignIn(AuthResolutionOutcome(intent))

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
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
            every { toGoogleSignInAccount() } returns gsa
            every { accessToken } returns "resolved-token-xyz"
        }
        every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns authResult

        newAuth().completeSignIn(AuthResolutionOutcome(intent))

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
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
            every { toGoogleSignInAccount() } returns null
            every { accessToken } returns "resolved-token"
        }
        every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns authResult
        coEvery { userInfoFetcher.fetch("resolved-token") } returns UserInfo(
            email = "userinfo@example.com",
            name = "From Userinfo",
        )

        val result = newAuth().completeSignIn(AuthResolutionOutcome(intent))

        assertTrue(result is BackupResult.Success)
        assertEquals(
            Account(email = "userinfo@example.com", displayName = "From Userinfo"),
            (result as BackupResult.Success).data,
        )
    }

    @Test
    fun `completeSignIn with null Intent returns Failure`() = runTest {
        val driveAuth = newAuth()
        val result = driveAuth.completeSignIn(AuthResolutionOutcome(null))

        assertTrue(result is BackupResult.Failure)
        assertTrue((result as BackupResult.Failure).error is BackupError.Unknown)
        coVerify(exactly = 0) { accountStore.setAccount(any()) }
    }

    @Test
    fun `signOut calls authorizationClient revokeAccess with all scopes`() = runTest {
        accountFlow.value = Account(email = "x@example.com", displayName = null)
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
                DriveAuthScopes.DRIVE_FILE,
            ),
            scopes,
        )
        coVerify { accountStore.clearToken() }
        coVerify { accountStore.clear() }
    }

    @Test
    fun `signOut clears local store even when revokeAccess fails`() = runTest {
        accountFlow.value = Account(email = "x@example.com", displayName = null)
        every { authorizationClient.revokeAccess(any()) } returns
            Tasks.forException(ApiException(Status.RESULT_INTERNAL_ERROR))

        val result = newAuth().signOut()

        assertEquals(BackupResult.Success(Unit), result)
        coVerify { accountStore.clearToken() }
        coVerify { accountStore.clear() }
    }

    @Test
    fun `signIn returns PartialGrant when grantedScopes excludes drive_appdata`() = runTest {
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { grantedScopes } returns listOf(
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
            every { accessToken } returns "partial-grant-token"
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)
        every { authorizationClient.clearToken(any()) } returns Tasks.forResult(null)

        val result = newAuth().signIn()

        assertTrue(result is SignInResult.PartialGrant, "expected PartialGrant, got $result")
        assertEquals(
            listOf(DriveAuthScopes.DRIVE_APPDATA),
            (result as SignInResult.PartialGrant).missingScopes,
        )
        coVerify(exactly = 0) { accountStore.setAccount(any()) }
        coVerify(exactly = 0) { accountStore.setToken(any(), any()) }
        val cleared = slot<com.google.android.gms.auth.api.identity.ClearTokenRequest>()
        coVerify(exactly = 1) { authorizationClient.clearToken(capture(cleared)) }
        assertEquals("partial-grant-token", cleared.captured.token)
    }

    @Test
    fun `signIn returns Success when only drive_appdata granted (userinfo soft-required)`() =
        runTest {
            val authResult = mockk<AuthorizationResult> {
                every { hasResolution() } returns false
                every { grantedScopes } returns listOf(DriveAuthScopes.DRIVE_APPDATA)
                every { toGoogleSignInAccount() } returns null
                every { accessToken } returns "live-token"
            }
            every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

            val result = newAuth().signIn()

            assertTrue(result is SignInResult.Success, "expected Success, got $result")
            assertEquals(
                "drive_account",
                (result as SignInResult.Success).account.email,
            )
            coVerify(exactly = 1) { accountStore.setAccount(result.account) }
        }

    @Test
    fun `completeSignIn returns MissingRequiredScope when grantedScopes excludes drive_appdata`() =
        runTest {
            val intent = mockk<Intent>()
            val authResult = mockk<AuthorizationResult> {
                every { grantedScopes } returns listOf(DriveAuthScopes.USERINFO_EMAIL)
                every { accessToken } returns "partial-token-resolved"
            }
            every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns
                authResult
            every { authorizationClient.clearToken(any()) } returns Tasks.forResult(null)

            val result = newAuth().completeSignIn(AuthResolutionOutcome(intent))

            assertTrue(result is BackupResult.Failure, "expected Failure, got $result")
            assertEquals(
                BackupError.MissingRequiredScope,
                (result as BackupResult.Failure).error,
            )
            coVerify(exactly = 0) { accountStore.setAccount(any()) }
            coVerify(exactly = 0) { accountStore.setToken(any(), any()) }
            val cleared = slot<com.google.android.gms.auth.api.identity.ClearTokenRequest>()
            coVerify(exactly = 1) { authorizationClient.clearToken(capture(cleared)) }
            assertEquals("partial-token-resolved", cleared.captured.token)
        }

    @Test
    fun `partial-grant clearToken failure does not block PartialGrant propagation`() = runTest {
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { grantedScopes } returns emptyList()
            every { accessToken } returns "partial-grant-token"
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)
        every { authorizationClient.clearToken(any()) } returns
            Tasks.forException(ApiException(Status.RESULT_INTERNAL_ERROR))

        val result = newAuth().signIn()

        assertTrue(result is SignInResult.PartialGrant, "expected PartialGrant, got $result")
        coVerify(exactly = 0) { accountStore.setAccount(any()) }
        coVerify(exactly = 0) { accountStore.setToken(any(), any()) }
    }

    @Test
    fun `signIn persists driveFileGranted false when drive_file not granted`() = runTest {
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
            )
            every { toGoogleSignInAccount() } returns null
            every { accessToken } returns "tok"
        }
        every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

        newAuth().signIn()

        coVerify { accountStore.setDriveFileGranted(false) }
    }

    @Test
    fun `requestDriveFileAccess requests drive_file and persists the grant on success`() = runTest {
        val authResult = mockk<AuthorizationResult> {
            every { hasResolution() } returns false
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
                DriveAuthScopes.DRIVE_FILE,
            )
            every { toGoogleSignInAccount() } returns null
            every { accessToken } returns "tok"
        }
        val captured = slot<com.google.android.gms.auth.api.identity.AuthorizationRequest>()
        every { authorizationClient.authorize(capture(captured)) } returns Tasks.forResult(authResult)

        val result = newAuth().requestDriveFileAccess()

        assertTrue(result is SignInResult.Success, "expected Success, got $result")
        val scopes = captured.captured.requestedScopes.map(Scope::getScopeUri).toSet()
        assertTrue(scopes.contains(DriveAuthScopes.DRIVE_FILE), "expected drive.file requested, got $scopes")
        coVerify { accountStore.setDriveFileGranted(true) }
        // Fresh token present -> cache it, never clear.
        coVerify(exactly = 1) { accountStore.setToken(token = "tok", expiresAtEpochMs = any()) }
        coVerify(exactly = 0) { accountStore.clearToken() }
    }

    @Test
    fun `requestDriveFileAccess clears stale cached token when drive_file granted with no fresh token`() =
        runTest {
            // A grant can arrive with no access token. The cached one predates it and may be
            // appdata-only, so drop it to force a drive.file-capable refresh instead of a 403.
            val authResult = mockk<AuthorizationResult> {
                every { hasResolution() } returns false
                every { grantedScopes } returns listOf(
                    DriveAuthScopes.DRIVE_APPDATA,
                    DriveAuthScopes.USERINFO_EMAIL,
                    DriveAuthScopes.USERINFO_PROFILE,
                    DriveAuthScopes.DRIVE_FILE,
                )
                every { toGoogleSignInAccount() } returns null
                every { accessToken } returns null
            }
            every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

            val result = newAuth().requestDriveFileAccess()

            assertTrue(result is SignInResult.Success, "expected Success, got $result")
            coVerify { accountStore.setDriveFileGranted(true) }
            coVerify(exactly = 1) { accountStore.clearToken() }
            coVerify(exactly = 0) { accountStore.setToken(any(), any()) }
        }

    @Test
    fun `requestDriveFileAccess preserves existing account when grant carries no fresh identity`() =
        runTest {
            // GMS can satisfy an incremental grant from a cached credential: success, but no
            // fresh token and no GoogleSignInAccount. The placeholder must not clobber identity.
            accountFlow.value = Account(email = "real@example.com", displayName = "Real User")
            val authResult = mockk<AuthorizationResult> {
                every { hasResolution() } returns false
                every { grantedScopes } returns listOf(
                    DriveAuthScopes.DRIVE_APPDATA,
                    DriveAuthScopes.USERINFO_EMAIL,
                    DriveAuthScopes.USERINFO_PROFILE,
                    DriveAuthScopes.DRIVE_FILE,
                )
                every { toGoogleSignInAccount() } returns null
                every { accessToken } returns null
            }
            every { authorizationClient.authorize(any()) } returns Tasks.forResult(authResult)

            val result = newAuth().requestDriveFileAccess()

            assertTrue(result is SignInResult.Success, "expected Success, got $result")
            assertEquals(
                Account(email = "real@example.com", displayName = "Real User"),
                (result as SignInResult.Success).account,
            )
            coVerify { accountStore.setDriveFileGranted(true) }
            coVerify(exactly = 0) {
                accountStore.setAccount(Account(email = "drive_account", displayName = null))
            }
        }

    @Test
    fun `completeSignIn persists driveFileGranted true when drive_file granted`() = runTest {
        val intent = mockk<Intent>()
        val authResult = mockk<AuthorizationResult> {
            every { grantedScopes } returns listOf(
                DriveAuthScopes.DRIVE_APPDATA,
                DriveAuthScopes.USERINFO_EMAIL,
                DriveAuthScopes.USERINFO_PROFILE,
                DriveAuthScopes.DRIVE_FILE,
            )
            every { toGoogleSignInAccount() } returns null
            every { accessToken } returns "tok"
        }
        every { authorizationClient.getAuthorizationResultFromIntent(intent) } returns authResult

        newAuth().completeSignIn(AuthResolutionOutcome(intent))

        coVerify { accountStore.setDriveFileGranted(true) }
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

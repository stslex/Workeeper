package io.github.stslex.workeeper.feature.settings.domain.mapper

import io.github.stslex.workeeper.core.data.backup.api.model.Account
import io.github.stslex.workeeper.core.data.backup.api.model.AuthState
import io.github.stslex.workeeper.core.data.backup.api.model.BackupRef
import io.github.stslex.workeeper.core.data.backup.api.model.SignInResult
import io.github.stslex.workeeper.feature.settings.domain.model.AccountDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupAuthDomain
import io.github.stslex.workeeper.feature.settings.domain.model.BackupSummaryDomain
import io.github.stslex.workeeper.feature.settings.domain.model.SignInOutcomeDomain

internal object BackupDomainMapper {

    fun Account.toDomain(): AccountDomain = AccountDomain(
        email = email,
        displayName = displayName,
    )

    fun AuthState.toDomain(): BackupAuthDomain = when (this) {
        AuthState.SignedOut -> BackupAuthDomain.NotAuthenticated
        is AuthState.SignedIn -> BackupAuthDomain.Authenticated(account.toDomain())
    }

    fun BackupRef.toSummary(): BackupSummaryDomain = BackupSummaryDomain(
        createdAtEpochMs = manifest.createdAtEpochMs,
        sizeBytes = manifest.dbFileSizeBytes,
        appVersion = manifest.appVersion,
        schemaVersion = manifest.dbSchemaVersion,
    )

    fun SignInResult.toDomain(): SignInOutcomeDomain = when (this) {
        is SignInResult.Success -> SignInOutcomeDomain.Success
        is SignInResult.NeedsResolution -> SignInOutcomeDomain.NeedsResolution(intentSender)
        is SignInResult.Failure -> SignInOutcomeDomain.Failure(error)
    }
}

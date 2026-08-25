// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.di

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.Identity
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope

/**
 * Provides the GMS `AuthorizationClient` into the app graph without app/app naming the GMS type.
 * GUARD: an `internal` container silently fails cross-module aggregation — keep it public.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object AuthProvidersBindingContainer {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthorizationClient(
        context: Context,
    ): AuthorizationClient = Identity.getAuthorizationClient(context)
}

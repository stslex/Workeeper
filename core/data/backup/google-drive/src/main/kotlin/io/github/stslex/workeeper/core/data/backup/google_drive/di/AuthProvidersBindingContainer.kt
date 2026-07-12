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
 * App-Scope Collapse Step 3 (Phase PF.3). `AuthorizationClient` (Google Play Services) moved out of Hilt's
 * `AuthProvidersModule` into a Metro provides-factory container — the `DispatchersBindingContainer` /
 * `ResourceWrapperBindingContainer` template.
 *
 * HOME-A: the GMS type stays inside google-drive's compilation unit. A public `@BindingContainer`
 * `@ContributesTo(AppScope)` aggregates this into app/app's `AppGraph` by scope hint WITHOUT app/app ever
 * naming `AuthorizationClient` — proven by the PF.3 spike (an app-graph-owned impl with a GMS ctor param
 * compiles + seals with no GMS on app/app's classpath). The three gd-internal consumers
 * (`DriveAuthTokenProvider` / `DriveBackupAuth` / `DriveTokenInvalidator`) resolve it via graph aggregation.
 *
 * PUBLIC container + func (an `internal` container silently fails cross-module aggregation, guarded by
 * `ContributesToScopeRule`); the `Context` dep resolves from the graph's `create(applicationContext)`.
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

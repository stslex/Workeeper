package io.github.stslex.workeeper.core.data.database.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.buildAppDatabase
import javax.inject.Singleton

/**
 * Hilt provider for the [AppDatabase] **`create()` bound-instance root** (App-Scope Collapse Step 5, 5a).
 *
 * `AppDatabase` stays Hilt-constructed here (the single Room instance) and is threaded into the Metro
 * app-graph as the `create(appDatabase = ...)` root by `BaseApplication`. It is deliberately NOT
 * `@ContributesBinding`-flipped: it is a test-override I/O boundary (§Test-override root) the seam swaps for
 * an in-memory DB. The 9 DAOs + `DbTransitionRunner` that DERIVE from it moved to the Metro-owned
 * `DbCascadeBindingContainer`; the 3 `AppDatabase`-derived interface bindings moved to `@ContributesBinding`
 * on their impls. This module now provides only the root.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreDatabaseModule {

    // App-Scope Collapse Step 6 (P-DBROOT): construction delegates to the non-Hilt `buildAppDatabase`
    // factory (staged in-module), so the single source of construction truth is exercised in prod while
    // Hilt still OWNS the binding + bridge-feeds `create()`. The atomic cut deletes this @Provides and
    // calls `buildAppDatabase` directly at the graph seam. No `fallbackToDestructiveMigration` (migration
    // failure routes to the recovery flows; never a silent wipe) — that policy lives in the factory.
    @Provides
    @Singleton
    internal fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = buildAppDatabase(context)
}

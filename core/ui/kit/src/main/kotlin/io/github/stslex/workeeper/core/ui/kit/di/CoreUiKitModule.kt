package io.github.stslex.workeeper.core.ui.kit.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface CoreUiKitModule {

    // ActivityHolder + ActivityHolderProducer: App-Scope Collapse Step 3 — migrated to Metro
    // (@ContributesBinding x2 on ActivityHolderImpl). Their @Binds were removed here; still-Hilt readers
    // (MainActivity for ActivityHolderProducer) resolve via the adopt-back @Provides in
    // AppGraphAdoptBackModule.

    // ResourceManager: App-Scope Collapse Step 3 (L-tail) — DELETED, not migrated. It was a dead binding
    // (its only consumer, NumUiUtilsImpl reading resourceManager.locale, was removed in e37f74f5 when
    // rounding went pure-numeric). Interface + impl + this @Binds all removed; no Metro binding created.

    // NumUiUtils: App-Scope Collapse Step 3 (SB1) — migrated to Metro (AppGraph owns it). Its @Binds was
    // removed here; NumUiUtils has no app-scope Hilt consumer (clean migration, no adopt-back @Provides).
}

package io.github.stslex.workeeper.core.ui.kit.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.kit.utils.resource.ResourceManager
import io.github.stslex.workeeper.core.ui.kit.utils.resource.ResourceManagerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface CoreUiKitModule {

    // ActivityHolder + ActivityHolderProducer: App-Scope Collapse Step 3 — migrated to Metro
    // (@ContributesBinding x2 on ActivityHolderImpl). Their @Binds were removed here; still-Hilt readers
    // (ResourceManagerImpl for ActivityHolder, MainActivity for ActivityHolderProducer) resolve via the
    // adopt-back @Provides in AppGraphAdoptBackModule.

    @Binds
    @Singleton
    fun bindResourceManager(impl: ResourceManagerImpl): ResourceManager
    // NumUiUtils: App-Scope Collapse Step 3 (SB1) — migrated to Metro (AppGraph owns it). Its @Binds was
    // removed here; NumUiUtils has no app-scope Hilt consumer (clean migration, no adopt-back @Provides).
}

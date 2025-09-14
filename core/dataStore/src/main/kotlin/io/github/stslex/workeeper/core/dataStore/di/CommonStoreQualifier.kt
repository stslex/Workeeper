package io.github.stslex.workeeper.core.dataStore.di

import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier

@Named(CommonDataStore.Companion.NAME)
@Qualifier
internal annotation class CommonStoreQualifier
package io.github.stslex.workeeper.core.store.di

import io.github.stslex.workeeper.core.store.store.CommonDataStore
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier

@Named(CommonDataStore.Companion.NAME)
@Qualifier
internal annotation class CommonStoreQualifier
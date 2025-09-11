package io.github.stslex.workeeper.core.store.di

import io.github.stslex.workeeper.core.store.store.CommonStore
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier

@Named(CommonStore.Companion.NAME)
@Qualifier
internal annotation class CommonStoreQualifier
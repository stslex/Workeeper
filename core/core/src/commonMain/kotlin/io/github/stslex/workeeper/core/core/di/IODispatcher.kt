package io.github.stslex.workeeper.core.core.di

/**
 * DI qualifier for the IODispatcher seam. Modeled as an `expect annotation class` so the 44
 * cross-module call sites import it unchanged from `core.core.di`; the Android actual
 * carries `@javax.inject.Qualifier` so Hilt (running in `core:core-android` and the app
 * modules) recognises it, while the iOS actual is a plain annotation.
 */
expect annotation class IODispatcher()

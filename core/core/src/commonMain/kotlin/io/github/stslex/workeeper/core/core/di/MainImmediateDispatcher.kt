package io.github.stslex.workeeper.core.core.di

/**
 * DI qualifier for the MainImmediateDispatcher seam. Modeled as an `expect annotation class` so
 * the cross-module call sites import it unchanged from `core.core.di`; the Android actual carries
 * `@javax.inject.Qualifier`, which Metro reads through the `metro { interop { includeJavax() } }`
 * setting each consuming module declares, while the iOS actual is a plain annotation.
 */
expect annotation class MainImmediateDispatcher()

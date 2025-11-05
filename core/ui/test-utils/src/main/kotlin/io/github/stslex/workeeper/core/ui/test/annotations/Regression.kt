package io.github.stslex.workeeper.core.ui.test.annotations

/**
 * Marks a UI test as a regression test.
 *
 * Regression tests are comprehensive integration tests that require
 * full application deployment with real DI container, databases, APIs, and other dependencies.
 *
 * These tests run on:
 * - Pull requests targeting master branch
 * - Pushes to master branch
 * - Manual workflow dispatch with 'regression' option
 *
 * Examples:
 * - Full application navigation flows with real dependencies
 * - End-to-end user scenarios
 * - Integration tests with real database and DI
 * - Tests annotated with @HiltAndroidTest
 *
 * Typical execution time: 30-40 minutes for all regression tests
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Regression

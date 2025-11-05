package io.github.stslex.workeeper.core.ui.test.annotations

/**
 * Marks a UI test as a smoke test.
 *
 * Smoke tests are fast, critical tests that verify basic functionality
 * without requiring full application deployment with real DI, databases, or APIs.
 *
 * These tests run on:
 * - All pull requests
 * - All branches
 * - Manual workflow dispatch with 'smoke' option
 *
 * Examples:
 * - Basic UI element visibility checks
 * - Simple navigation flows
 * - Component interactions with mocked data
 *
 * Typical execution time: 5-10 minutes for all smoke tests
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Smoke

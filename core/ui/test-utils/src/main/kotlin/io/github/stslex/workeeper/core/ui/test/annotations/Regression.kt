package io.github.stslex.workeeper.core.ui.test.annotations

/**
 * Marks a UI test as a regression test.
 *
 * Regression tests are full-integration tests that exercise a real Hilt graph and database.
 * They typically use `@HiltAndroidTest` plus `createAndroidComposeRule<MainActivity>()` and
 * drive end-to-end user flows.
 *
 * Test execution: the `ui_tests.yml` workflow is `workflow_dispatch`-only with a
 * `test_suite` selector that picks `smoke`, `regression`, or `all`. UI tests do not
 * gate PRs — see `documentation/ci-cd.md`.
 *
 * Examples:
 * - Full application navigation flows with real dependencies
 * - Multi-feature user scenarios
 * - Integration tests with real database and DI
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Regression

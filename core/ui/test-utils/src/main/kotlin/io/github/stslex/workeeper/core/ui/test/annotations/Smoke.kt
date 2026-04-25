package io.github.stslex.workeeper.core.ui.test.annotations

/**
 * Marks a UI test as a smoke test.
 *
 * Smoke tests are fast, mocked-data tests that verify component-level behavior without
 * requiring a real DI container, database, or full activity. They typically use
 * `createComposeRule()` and pass mocked state directly into a widget.
 *
 * Test execution: the `ui_tests.yml` workflow is `workflow_dispatch`-only with a
 * `test_suite` selector that picks `smoke`, `regression`, or `all`. UI tests do not
 * gate PRs — see `documentation/ci-cd.md`.
 *
 * Examples:
 * - Basic UI element visibility checks
 * - Component interactions with mocked data
 * - Edge-case input handling
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Smoke

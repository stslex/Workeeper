package io.github.stslex.workeeper.core.ui.test.annotations

/**
 * Marks a fast, mocked-data UI test: `createComposeRule()` with state passed into a widget.
 * Selected by `ui_tests.yml`'s `test_suite`; UI tests do not gate PRs. See documentation/ci-cd.md.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Smoke

package io.github.stslex.workeeper.core.ui.test.annotations

/**
 * Marks a full-integration UI test: real Metro app graph, real database, end-to-end flows.
 * Selected by `ui_tests.yml`'s `test_suite`; UI tests do not gate PRs. See documentation/ci-cd.md.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Regression

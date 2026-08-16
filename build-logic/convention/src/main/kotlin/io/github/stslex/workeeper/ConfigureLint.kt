package io.github.stslex.workeeper

import com.android.build.api.dsl.Lint
import org.gradle.api.Project

/**
 * The repo-wide Android Lint option block. Every convention that reaches a `Lint` DSL
 * object must apply THIS block and never inline its own copy: the classic Android
 * conventions reach lint through `CommonExtension`, the KMP convention through
 * `KotlinMultiplatformAndroidLibraryExtension.lint` (the KMP android DSL is not a
 * `CommonExtension`, so [LintConventionPlugin]'s lookup finds nothing there), and a
 * per-convention copy is exactly how the two surfaces would drift apart.
 */
internal fun Project.configureLintOptions(lint: Lint) {
    lint.apply {
        // Main lint configuration (includes centralized suppressions)
        lintConfig = rootProject.file("lint-rules/lint.xml")

        // Report configuration
        htmlReport = true
        xmlReport = true
        sarifReport = true
        textReport = false

        // Analysis configuration
        checkDependencies = true
        abortOnError = true
        ignoreWarnings = false
        checkAllWarnings = true
        warningsAsErrors = true
        checkGeneratedSources = false
        explainIssues = true
        noLines = false
        quiet = false
        checkReleaseBuilds = true
        ignoreTestSources = true

        // Single centralized baseline file for all modules
        baseline = rootProject.file("lint-rules/lint-baseline.xml")

        // Output directories
        htmlOutput = file("build/reports/lint-results.html")
        xmlOutput = file("build/reports/lint-results.xml")
        sarifOutput = file("build/reports/lint-results.sarif")
    }
}

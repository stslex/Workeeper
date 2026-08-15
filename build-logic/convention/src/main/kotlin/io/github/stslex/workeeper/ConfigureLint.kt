package io.github.stslex.workeeper

import com.android.build.api.dsl.Lint
import org.gradle.api.Project

/**
 * The repo-wide Android Lint option block, shared by every convention that reaches a `Lint`
 * DSL object. Extracted from [LintConventionPlugin] unchanged so the classic Android
 * conventions (which reach lint through `CommonExtension`) and the KMP convention (which
 * reaches it through `KotlinMultiplatformAndroidLibraryExtension.lint` — the KMP android DSL
 * is not a `CommonExtension`, so [LintConventionPlugin]'s lookup finds nothing there) cannot
 * drift apart.
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

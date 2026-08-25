package io.github.stslex.workeeper

import com.android.build.api.dsl.Lint
import org.gradle.api.Project

/**
 * The repo-wide Android Lint option block. GUARD: every convention reaching a `Lint` DSL object
 * applies THIS block — the KMP android DSL is not a `CommonExtension` and needs it too.
 */
internal fun Project.configureLintOptions(lint: Lint) {
    lint.apply {
        lintConfig = rootProject.file("lint-rules/lint.xml")

        htmlReport = true
        xmlReport = true
        sarifReport = true
        textReport = false

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

        baseline = rootProject.file("lint-rules/lint-baseline.xml")

        htmlOutput = file("build/reports/lint-results.html")
        xmlOutput = file("build/reports/lint-results.xml")
        sarifOutput = file("build/reports/lint-results.sarif")
    }
}

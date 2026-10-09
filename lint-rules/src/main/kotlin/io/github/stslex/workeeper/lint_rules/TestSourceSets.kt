// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

/**
 * Test-source predicate shared by `DomainLayerPurityRule` and `UiLayerNoDataRule`: a file is test
 * code when the directory right after the last `/src/` segment names a test source set. See
 * lint-rules.md.
 */
internal object TestSourceSets {

    private const val SOURCE_ROOT = "src"

    private const val TEST = "test"

    private const val TEST_MARKER = "Test"

    /**
     * True when [filePath] sits in a test source set: `src/test/`, `src/test<Variant>/` (`testDebug`,
     * `testRelease`) or a `src/<name>/` whose name contains `Test` (`androidTest`, `commonTest`,
     * `iosTest`, `androidHostTest`, `androidDeviceTest`). Only directories count, never the file name.
     * Only the last `src` directory counts: detekt passes the absolute path, and a directory above the
     * checkout (`~/src/testProjects/`) must not exempt the whole repository.
     */
    fun isTestFile(filePath: String): Boolean {
        val directories = filePath.split('/').dropLast(1)
        val sourceRoot = directories.lastIndexOf(SOURCE_ROOT)
        return sourceRoot != -1 && directories.getOrNull(sourceRoot + 1)?.isTestSourceSetName() == true
    }

    private fun String.isTestSourceSetName(): Boolean =
        this == TEST ||
            startsWith(TEST) && getOrNull(TEST.length)?.isUpperCase() == true ||
            contains(TEST_MARKER)
}

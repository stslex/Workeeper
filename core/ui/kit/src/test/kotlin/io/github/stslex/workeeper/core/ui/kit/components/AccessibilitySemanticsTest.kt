// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.thumb.AppExerciseThumb
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The two accessibility properties this arc's rebuilds could drop without any other instrument
 * noticing, asserted where they can be read.
 *
 * **Why here and not in a golden or a handler test.** Both are semantics, not pixels and not state:
 * Paparazzi photographs a frame and never sees them, and a handler test never composes. §27's
 * standing rule is that anything whose evidence needs something other than one static frame owes a
 * direct assertion — this is that assertion for the two of them.
 *
 * ## Why it is a JVM test and not an instrumented one
 *
 * **An assertion no job runs is not a weaker gate, it is not a gate**, and `src/androidTest/` is
 * where that happens in this repository. Nothing in the PR-gating workflow compiles instrumented
 * sources, and the only workflow that runs them (`ui_tests.yml`) is `workflow_dispatch`-only **and**
 * selects by the runner's `annotation` argument in both jobs — so an un-annotated device test is
 * skipped even on a manual dispatch of `all`. `core/data/database/build.gradle.kts` already says so.
 *
 * Nothing here needs a device: these read the semantics tree and touch no pixel, no gesture and no
 * frame timing. They run under Robolectric on the JUnit 5 platform — the
 * `@ExtendWith(RobolectricExtension::class)` shape `core/data/database`'s real-DB suites already
 * use — so `./gradlew testDebugUnitTest` executes them on every PR.
 *
 * **`runComposeUiTest` rather than `createComposeRule`, and that is not a style choice.**
 * `createComposeRule()` returns a JUnit 4 `TestRule`, and this repo's test tasks are
 * `useJUnitPlatform()` with `failOnNoDiscoveredTests.set(false)` (`KotlinAndroid.kt`), with no
 * vintage engine in the catalog. A JUnit 4-shaped class in `src/test` is therefore not discovered,
 * not run and **not reported** — the task goes green having executed zero of it, which is worse
 * than the instrumented suite it replaces, because it looks like a gate.
 *
 * ## ONE `@Test`, ONE composition — do not split this into three
 *
 * **A second `runComposeUiTest` in the same Robolectric sandbox never returns.** Measured, not
 * feared: each of these three assertions passes alone in 4–12s, and any two in one class hang
 * indefinitely in `RobolectricIdlingStrategy.runUntilIdle` → `Espresso.onIdle()`, reached from
 * `AndroidComposeUiTestEnvironment.runTest` — the environment's own synchronisation, with the
 * previous environment's idling resource still registered. So the three subjects share one
 * composition and one environment, and [assertAll] keeps all three reported rather than stopping at
 * the first. Splitting them back into a `@Test` each does not fail the build, it **hangs** it.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class AccessibilitySemanticsTest {

    @Test
    fun accessibilityPropertiesTheRebuildsCouldDrop() = runComposeUiTest {
        setContent {
            AppTheme {
                Column {
                    AppTextField(
                        modifier = Modifier.testTag(FIELD_ERRORED),
                        value = "",
                        onValueChange = {},
                        isError = true,
                    )
                    AppTextField(
                        modifier = Modifier.testTag(FIELD_CLEAN),
                        value = "Жим лёжа",
                        onValueChange = {},
                    )
                    AppExerciseThumb(
                        modifier = Modifier.testTag(THUMB),
                        isWeighted = true,
                        onClick = {},
                        contentDescription = ADD_A_PHOTO,
                    )
                }
            }
        }
        waitForIdle()

        assertAll(
            // An outline is the sighted half of "this field is wrong". `OutlinedTextField` set the
            // other half from `isError` itself; a field built on `BasicTextField` owes it
            // explicitly, or TalkBack announces an invalid box as an ordinary one while the reason
            // sits visibly underneath it.
            {
                onNodeWithTag(FIELD_ERRORED).assert(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Error),
                )
            },
            // The other direction, so the property is a signal and not a constant.
            {
                onNodeWithTag(FIELD_CLEAN).assert(
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.Error),
                )
            },
            // The empty thumb is a 46dp box whose only child is a decorative mark, and it replaced
            // a separately labelled button. Without a click label it is a control a screen-reader
            // user cannot discover, let alone identify — the mark says which TYPE the exercise is,
            // which is not what the tap does.
            {
                onNodeWithTag(THUMB).assert(
                    SemanticsMatcher("has the click label «$ADD_A_PHOTO»") { node ->
                        node.config.getOrNull(SemanticsActions.OnClick)?.label == ADD_A_PHOTO
                    },
                )
            },
        )
    }

    private companion object {

        const val FIELD_ERRORED = "field-errored"
        const val FIELD_CLEAN = "field-clean"
        const val THUMB = "thumb"
        const val ADD_A_PHOTO = "Add a photo"
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.key
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
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.rememberReorderableColumnState
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.reorderableColumnItem
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
                    AppTextField(
                        modifier = Modifier.testTag(FIELD_LABELLED),
                        value = "Подтягивания",
                        onValueChange = {},
                        accessibilityLabel = NAME_LABEL,
                    )
                    AppExerciseThumb(
                        modifier = Modifier.testTag(THUMB),
                        isWeighted = true,
                        onClick = {},
                        contentDescription = ADD_A_PHOTO,
                    )
                    // Three rows, so first / middle / last are all present in one frame — the
                    // boundary claim cannot be made by a single row.
                    val reorderState = rememberReorderableColumnState { _, _ -> }
                    ROWS.forEachIndexed { index, tag ->
                        key(tag) {
                            Box(
                                modifier = Modifier
                                    .testTag(tag)
                                    .reorderableColumnItem(
                                        state = reorderState,
                                        key = tag,
                                        index = index,
                                        lastIndex = ROWS.lastIndex,
                                    ),
                            )
                        }
                    }
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
            // The drawn `.flabel` is a sibling node, so without this the field announces its
            // value and role and never says WHICH field it is.
            {
                onNodeWithTag(FIELD_LABELLED).assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.ContentDescription,
                        listOf(NAME_LABEL),
                    ),
                )
            },
            {
                onNodeWithTag(THUMB).assert(
                    SemanticsMatcher("has the click label «$ADD_A_PHOTO»") { node ->
                        node.config.getOrNull(SemanticsActions.OnClick)?.label == ADD_A_PHOTO
                    },
                )
            },
            // A reorder action that cannot happen must not be offered, and must certainly not
            // report success. The arrow buttons this replaced were disabled at their boundaries.
            { onNodeWithTag(ROWS.first()).assert(hasCustomActions(MOVE_DOWN)) },
            { onNodeWithTag(ROWS.first()).assert(hasNoCustomAction(MOVE_UP)) },
            { onNodeWithTag(ROWS.last()).assert(hasCustomActions(MOVE_UP)) },
            { onNodeWithTag(ROWS.last()).assert(hasNoCustomAction(MOVE_DOWN)) },
            // The middle row keeps both, so the filter is a boundary rule and not a blanket one.
            { onNodeWithTag(ROWS[1]).assert(hasCustomActions(MOVE_UP)) },
            { onNodeWithTag(ROWS[1]).assert(hasCustomActions(MOVE_DOWN)) },
        )
    }

    private fun hasCustomActions(label: String) = SemanticsMatcher("offers «$label»") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
    }

    private fun hasNoCustomAction(label: String) = SemanticsMatcher("does NOT offer «$label»") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().none { it.label == label }
    }

    private companion object {

        const val FIELD_ERRORED = "field-errored"
        const val FIELD_CLEAN = "field-clean"
        const val THUMB = "thumb"
        const val ADD_A_PHOTO = "Add a photo"
        const val FIELD_LABELLED = "field-labelled"
        const val NAME_LABEL = "Название"
        const val MOVE_UP = "Move up"
        const val MOVE_DOWN = "Move down"
        val ROWS = listOf("row-first", "row-middle", "row-last")
    }
}

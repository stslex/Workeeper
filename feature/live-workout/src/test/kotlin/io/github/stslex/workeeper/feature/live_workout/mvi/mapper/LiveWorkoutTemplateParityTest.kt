// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import android.content.Context
import io.github.stslex.workeeper.core.ui.kit.strings.fillTemplate
import io.github.stslex.workeeper.feature.live_workout.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * SP1: every template the module fills from its copy, read verbatim and filled by kit's
 * `fillTemplate`, equals Android's own `getString(id, args)`, with the argument types its call
 * site passes. The Russian half is [LiveWorkoutTemplateParityRuTest]: the Robolectric JUnit5
 * extension forbids method-level `@Config`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class LiveWorkoutTemplateParityTest {

    @Test
    fun `every filled template matches the platform format in English`() {
        assertFilledTemplatesMatchThePlatformFormat()
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru")
internal class LiveWorkoutTemplateParityRuTest {

    @Test
    fun `every filled template matches the platform format in Russian`() {
        assertFilledTemplatesMatchThePlatformFormat()
    }
}

/** The ten templates of Section B2.1, each with the argument types its call site passes. */
private val TEMPLATES: List<Pair<Int, Array<Any>>> = listOf(
    R.string.feature_live_workout_meta_format to arrayOf<Any>(2, 3, 5, 9),
    R.string.feature_live_workout_meta_skipped_format to arrayOf<Any>("2 of 3 exercises · 5 of 9 sets", 1),
    R.string.feature_live_workout_delete_session_progress_format to arrayOf<Any>(1, 4, 6),
    R.string.feature_live_workout_finish_stat_sets_count to arrayOf<Any>(12),
    R.string.feature_live_workout_finish_stat_exercises_format to arrayOf<Any>(3, 4),
    R.string.feature_live_workout_finish_stat_exercises_with_skipped_format to arrayOf<Any>(2, 4, 1),
    R.string.feature_live_workout_finish_pr_weighted_format to arrayOf<Any>("102.5", 5),
    R.string.feature_live_workout_finish_pr_weightless_format to arrayOf<Any>(18),
    R.string.feature_live_workout_toast_exercise_removed to arrayOf<Any>("Bench Press"),
    R.string.feature_live_workout_toast_exercise_removed_from_workout to arrayOf<Any>("Bench Press"),
)

private fun assertFilledTemplatesMatchThePlatformFormat() {
    val context: Context = RuntimeEnvironment.getApplication()
    assertAll(
        TEMPLATES.map { (id, args) ->
            {
                assertEquals(
                    context.getString(id, *args),
                    context.getString(id).fillTemplate(*args),
                    context.resources.getResourceEntryName(id),
                )
            }
        },
    )
}

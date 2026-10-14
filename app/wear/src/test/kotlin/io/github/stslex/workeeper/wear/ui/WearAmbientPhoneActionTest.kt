// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Locale
import java.util.TimeZone

/**
 * The phone-action surfaces carry the training name for the Tile only (wear-phase-2-exercise-list.md
 * §7.6): their ambient summary stays what it was when the surface had no training name.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class WearAmbientPhoneActionTest {

    // The Robolectric application exists only once a test runs, never at construction.
    private val resources get() = RuntimeEnvironment.getApplication().resources

    @Test
    fun `a phone action without an exercise name keeps the generic workout context`() {
        listOf(
            WearSurfaceModel(kind = WearSurfaceKind.PHONE_ACTION_NO_SETS, selectedLocale = Locale.ENGLISH),
            WearSurfaceModel(
                kind = WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
                fieldError = NumericField.WEIGHT,
                selectedLocale = Locale.ENGLISH,
            ),
        ).forEach { today ->
            val withTraining = content(today.copy(trainingName = TRAINING))

            assertEquals(content(today), withTraining, today.kind.name)
            assertEquals(
                resources.getString(R.string.workout_generic),
                withTraining.lines.single { it.role == AmbientLineRole.CONTEXT }.text,
                today.kind.name,
            )
        }
    }

    private fun content(model: WearSurfaceModel) = ambientSummaryContent(
        resources = resources,
        model = model,
        timestampMillis = TIMESTAMP_MS,
        hasUnsubmittedValues = false,
        timeZone = TimeZone.getTimeZone("UTC"),
    )

    private companion object {
        const val TRAINING = "Leg day"
        const val TIMESTAMP_MS = 1_000_000L
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.ambient.ambientOffset
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearAmbientTimePreferenceTest {

    @Test
    fun systemTimePreferenceControlsSpokenAndDrawnClockAtSmallScreenBoundaries() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val previousPreference = Settings.System.getString(resolver, Settings.System.TIME_12_24)
        val previousTimeZone = TimeZone.getDefault()
        try {
            runComposeUiTest { assertAmbientTimePreferences() }
        } finally {
            Settings.System.putString(resolver, Settings.System.TIME_12_24, previousPreference)
            TimeZone.setDefault(previousTimeZone)
        }
    }
}

private data class AmbientClockCase(
    val locale: Locale,
    val use24Hour: Boolean,
    val timeZone: TimeZone,
    val timestampMillis: Long,
)

private fun ambientClockCases(): List<AmbientClockCase> {
    val preferences = listOf(Locale.ENGLISH, Locale.forLanguageTag("ru")).flatMap { locale ->
        listOf(false, true).map { use24Hour -> locale to use24Hour }
    }
    val times = listOf("UTC", "GMT+03:00").flatMap { zone ->
        listOf(0L, 43_200_000L, 86_340_000L).map { timestamp -> TimeZone.getTimeZone(zone) to timestamp }
    }
    return preferences.flatMap { (locale, use24Hour) ->
        times.map { (timeZone, timestamp) -> AmbientClockCase(locale, use24Hour, timeZone, timestamp) }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertAmbientTimePreferences() {
    val resolver = RuntimeEnvironment.getApplication().contentResolver
    val base = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)).copy(
        reps = 999,
        weightHundredthsKg = 99_999,
        setOrdinal = Int.MAX_VALUE,
        totalSets = Int.MAX_VALUE,
        hasUnsubmittedDraft = true,
    )
    var model by mutableStateOf(base.copy(selectedLocale = Locale.ENGLISH))
    var ambient by mutableStateOf(WearAmbientState(isAmbient = true))
    setContent {
        WearGateHost(WearScreen.SMALL_ROUND, LARGEST_WEAR_FONT_SCALE) {
            WearAmbientSummary(model, ambient, model.hasUnsubmittedDraft)
        }
    }
    ambientClockCases().forEach { candidate ->
        Settings.System.putString(resolver, Settings.System.TIME_12_24, if (candidate.use24Hour) "24" else "12")
        TimeZone.setDefault(candidate.timeZone)
        model = base.copy(selectedLocale = candidate.locale)
        ambient = WearAmbientState(
            isAmbient = true,
            timestampMillis = candidate.timestampMillis,
            deviceHasLowBitAmbient = true,
            burnInProtectionRequired = true,
            offset = ambientOffset(candidate.timestampMillis, true),
        )
        waitForIdle()
        assertAmbientClockCase(candidate, model, ambient)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertAmbientClockCase(
    candidate: AmbientClockCase,
    model: WearSurfaceModel,
    ambient: WearAmbientState,
) {
    val app = RuntimeEnvironment.getApplication()
    val configuration = Configuration(app.resources.configuration).apply { setLocale(candidate.locale) }
    val resources = app.createConfigurationContext(configuration).resources
    val skeleton = if (candidate.use24Hour) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(candidate.locale, skeleton)
    val expected = SimpleDateFormat(pattern, candidate.locale).apply {
        timeZone = candidate.timeZone
    }.format(Date(candidate.timestampMillis))
    val node = onNodeWithTag("ambient_summary").fetchSemanticsNode()
    val description = node.config[SemanticsProperties.ContentDescription].single()
    assertTrue(
        description.startsWith("$expected."),
        "$candidate system time preference must produce '$expected', actual '$description'",
    )
    val image = onNodeWithTag("ambient_summary").captureToImage()
    val directory = File("build/reports/wear-ambient-clock").apply { mkdirs() }
    val name = "${candidate.locale}-${candidate.use24Hour}-${candidate.timeZone.rawOffset}-${candidate.timestampMillis}"
    File(directory, "$name.png").outputStream().use { output ->
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    assertAmbientPixels(image, lowBit = true, where = candidate.toString())
    val content = ambientSummaryContent(resources, model, candidate.timestampMillis, true, candidate.timeZone)
    val independentTime = content.copy(
        lines = content.lines.map { line ->
            if (line.role == AmbientLineRole.TIME) line.copy(text = expected) else line
        },
    )
    val layout = ambientSummaryLayout(
        independentTime,
        image.width.toFloat(),
        image.height.toFloat(),
        node.layoutInfo.density,
        lowBit = true,
    )
    val timeRow = layout.single { it.role == AmbientLineRole.TIME }
    assertTrue(
        timeRow.paint.measureText(expected) <= timeRow.availableWidthPx,
        "$candidate full localized clock must fit its circular chord",
    )
    assertAmbientNumericRaster(
        image,
        layout,
        mapOf(AmbientLineRole.TIME to expected),
        ambient.offset,
        candidate.toString(),
    )
}

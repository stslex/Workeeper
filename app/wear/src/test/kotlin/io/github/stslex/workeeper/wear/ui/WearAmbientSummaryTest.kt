// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ambient.WearAmbientOffset
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.ambient.ambientOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.floor

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearAmbientSummaryTest {

    @Test
    fun englishSummaryFitsAndHasNoIndependentClock() = runComposeUiTest {
        assertAmbientSummaryMatrix(Locale.ENGLISH)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertAmbientSummaryMatrix(locale: Locale) {
    val resolver = RuntimeEnvironment.getApplication().contentResolver
    val previous = Settings.System.getString(resolver, Settings.System.TIME_12_24)
    try {
        Settings.System.putString(resolver, Settings.System.TIME_12_24, "24")
        assertAmbientSummaryMatrixWithFixedTimeFormat(locale)
    } finally {
        Settings.System.putString(resolver, Settings.System.TIME_12_24, previous)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertAmbientSummaryMatrixWithFixedTimeFormat(locale: Locale) {
    val app = RuntimeEnvironment.getApplication()
    val configuration = Configuration(app.resources.configuration).apply { setLocale(locale) }
    val resources = app.createConfigurationContext(configuration).resources
    val cases = ambientCases(locale)
    var selected by mutableStateOf(cases.first())
    var screen by mutableStateOf(WearScreen.SMALL_ROUND)
    var fontScale by mutableStateOf(1f)
    var ambient by mutableStateOf(WearAmbientState(isAmbient = true, timestampMillis = 0L))
    setContent {
        WearGateHost(screen, fontScale) {
            WearAmbientSummary(selected.model, ambient, selected.hasDraft)
        }
    }

    ambientProfiles().forEach { profile ->
        screen = profile.screen
        fontScale = profile.fontScale
        ambient = WearAmbientState(
            isAmbient = true,
            timestampMillis = 0L,
            deviceHasLowBitAmbient = profile.lowBit,
            burnInProtectionRequired = profile.burnIn,
            offset = ambientOffset(0L, profile.burnIn),
        )
        cases.forEach { candidate ->
            selected = candidate
            waitForIdle()
            val where = "$locale/$profile ${candidate.model.kind}"
            val node = onNodeWithTag("ambient_summary").fetchSemanticsNode()
            val description = node.config[SemanticsProperties.ContentDescription].single()
            assertEquals(
                resources.getString(R.string.ambient_read_only),
                node.config[SemanticsProperties.StateDescription],
            )
            assertTrue(description.contains(requireNotNull(candidate.model.exerciseName)), where)
            val availability = if (candidate.model.completeEnabled) {
                R.string.complete_set_enabled_description
            } else {
                R.string.complete_set_disabled_description
            }
            assertTrue(description.contains(resources.getString(availability)), where)
            assertEquals(
                candidate.hasDraft,
                description.contains(resources.getString(R.string.ambient_not_sent)),
                where,
            )
            val image = onNodeWithTag("ambient_summary").captureToImage()
            saveAmbientCapture(image, locale, profile, candidate.name)
            assertAmbientPixels(image, profile.lowBit, where)
            val content = ambientSummaryContent(
                resources,
                candidate.model,
                0L,
                candidate.hasDraft,
                TimeZone.getDefault(),
            )
            val layout = ambientSummaryLayout(
                content,
                image.width.toFloat(),
                image.height.toFloat(),
                node.layoutInfo.density,
                profile.lowBit,
            )
            layout.forEach { line ->
                assertTrue(
                    line.paint.measureText(line.text) <= line.availableWidthPx,
                    "$where ${line.role} exceeds its circular chord: ${line.text}",
                )
            }
            candidate.model.formattedValues.reps?.let { expected ->
                assertTrue(
                    layout.single { it.role == AmbientLineRole.REPS }.text.contains(expected),
                    where,
                )
                assertTrue(description.contains(expected), where)
            }
            if (candidate.model.weighted && candidate.model.formattedValues.weight != null) {
                val expected = requireNotNull(candidate.model.formattedValues.weight)
                assertTrue(
                    layout.single { it.role == AmbientLineRole.WEIGHT }.text.contains(expected),
                    where,
                )
                assertTrue(description.contains(expected), where)
            }
            assertAmbientNumericRaster(
                image = image,
                layout = layout,
                expectedRows = expectedAmbientNumericRows(resources, candidate.model),
                offset = ambient.offset,
                where = where,
            )
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), true).assertCountEquals(0)
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy), true).assertCountEquals(0)
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus), true).assertCountEquals(0)
        }
    }
    selected = cases.first()
    waitForIdle()
    val stable = onNodeWithTag("ambient_summary").captureToImage()
    mainClock.advanceTimeBy(60_000L)
    waitForIdle()
    assertEquals(0, ambientPixelDelta(stable, onNodeWithTag("ambient_summary").captureToImage()))
    val before = onNodeWithTag("ambient_summary").fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription]
    ambient = ambient.copy(timestampMillis = 60_000L, offset = ambientOffset(60_000L, true))
    waitForIdle()
    val after = onNodeWithTag("ambient_summary").fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription]
    assertNotEquals(before, after, "Only an event timestamp should advance the displayed time")
    assertTrue(ambientPixelDelta(stable, onNodeWithTag("ambient_summary").captureToImage()) > 0)
}

private data class AmbientProfile(
    val screen: WearScreen,
    val fontScale: Float,
    val lowBit: Boolean,
    val burnIn: Boolean,
)

private fun ambientProfiles(): List<AmbientProfile> {
    val sizes = WearScreen.entries.flatMap { screen ->
        listOf(1f, LARGEST_WEAR_FONT_SCALE).map { scale -> screen to scale }
    }
    return sizes.flatMap { (screen, scale) ->
        listOf(false, true).flatMap { lowBit ->
            listOf(false, true).map { burnIn -> AmbientProfile(screen, scale, lowBit, burnIn) }
        }
    }
}

private fun saveAmbientCapture(image: ImageBitmap, locale: Locale, profile: AmbientProfile, caseName: String) {
    val directory = File("build/reports/wear-ambient").apply { mkdirs() }
    val name = listOf(
        locale.toLanguageTag(),
        profile.screen,
        profile.fontScale,
        profile.lowBit,
        profile.burnIn,
        caseName,
    ).joinToString("-")
    File(directory, "$name.png").outputStream().use { output ->
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
    }
}

private data class AmbientCase(val name: String, val model: WearSurfaceModel, val hasDraft: Boolean)

private fun ambientCases(locale: Locale): List<AmbientCase> {
    val active = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)).copy(
        selectedLocale = locale,
        exerciseName = "An intentionally long exercise name that remains complete in the spoken ambient description",
        reps = 999,
        weightHundredthsKg = 99_999,
        setOrdinal = Int.MAX_VALUE,
        totalSets = Int.MAX_VALUE,
    )
    return listOf(
        AmbientCase("weighted", active, hasDraft = true),
        AmbientCase(
            "unset_weight",
            active.copy(
                weightHundredthsKg = null,
                completeEnabled = false,
                completionUnavailableReason = CompletionUnavailableReason.INVALID_WEIGHT,
            ),
            hasDraft = true,
        ),
        AmbientCase("weightless", active.copy(weighted = false, weightHundredthsKg = null), hasDraft = false),
        AmbientCase(
            "protocol_mismatch",
            WearSurfaceModel(
                kind = WearSurfaceKind.PROTOCOL_MISMATCH,
                exerciseName = "Обновление приложения на часах и телефоне",
                selectedLocale = locale,
            ),
            hasDraft = false,
        ),
    )
}

private fun expectedAmbientNumericRows(
    resources: Resources,
    model: WearSurfaceModel,
): Map<AmbientLineRole, String> = buildMap {
    model.formattedValues.reps?.let { reps ->
        put(AmbientLineRole.REPS, resources.getString(R.string.ambient_reps, reps))
    }
    if (model.reps != null) {
        val weight = if (!model.weighted) {
            resources.getString(R.string.ambient_weightless)
        } else {
            model.formattedValues.weight?.let { resources.getString(R.string.weight_value, it) }
                ?: resources.getString(R.string.weight_unset)
        }
        put(AmbientLineRole.WEIGHT, weight)
    }
}

internal fun assertAmbientNumericRaster(
    image: ImageBitmap,
    layout: List<AmbientDrawLine>,
    expectedRows: Map<AmbientLineRole, String>,
    offset: WearAmbientOffset,
    where: String,
) {
    val actualPixels = image.toPixelMap()
    expectedRows.forEach { (role, text) ->
        val row = layout.single { it.role == role }
        val metrics = row.paint.fontMetrics
        val top = floor(row.baselinePx + metrics.top + offset.yPx).toInt()
        val bottom = ceil(row.baselinePx + metrics.bottom + offset.yPx).toInt()
        assertTrue(top >= 0 && bottom <= image.height && top < bottom, "$where $role row is clipped")
        val expected = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(expected)
            canvas.drawColor(Color.BLACK)
            // Use the model/resource string, never row.text: layout alone cannot prove drawn values.
            canvas.drawText(
                text,
                (image.width - row.paint.measureText(text)) / 2f + offset.xPx,
                row.baselinePx + offset.yPx,
                row.paint,
            )
            var expectedInk = 0
            var actualInk = 0
            var mismatchedPixels = 0
            for (y in top until bottom) {
                repeat(image.width) { x ->
                    val expectedPixel = expected.getPixel(x, y)
                    val actualPixel = actualPixels[x, y].toArgb()
                    if (expectedPixel != Color.BLACK) expectedInk++
                    if (actualPixel != Color.BLACK) actualInk++
                    if (expectedPixel != actualPixel) mismatchedPixels++
                }
            }
            assertTrue(expectedInk > 0, "$where $role expected raster is blank")
            assertEquals(
                0,
                mismatchedPixels,
                "$where $role actual row must draw full '$text': expected ink=$expectedInk, actual ink=$actualInk",
            )
        } finally {
            expected.recycle()
        }
    }
}

internal fun assertAmbientPixels(image: ImageBitmap, lowBit: Boolean, where: String) {
    val pixels = image.toPixelMap()
    val centerX = image.width / 2f
    val centerY = image.height / 2f
    val radius = minOf(centerX, centerY)
    var screenPixels = 0
    var litPixels = 0
    repeat(image.height) { y ->
        repeat(image.width) { x ->
            val dx = x + 0.5f - centerX
            val dy = y + 0.5f - centerY
            val squaredDistance = dx * dx + dy * dy
            if (squaredDistance <= radius * radius) screenPixels++
            val color = pixels[x, y].toArgb()
            if (color != android.graphics.Color.BLACK) {
                litPixels++
                assertTrue(squaredDistance <= (radius - 10f) * (radius - 10f), "$where unsafe ink at $x,$y")
                if (lowBit) assertEquals(android.graphics.Color.WHITE, color, "$where low-bit pixel at $x,$y")
            }
        }
    }
    assertTrue(litPixels > 0, "$where blank captures cannot prove a summary")
    assertTrue(litPixels <= screenPixels * 0.15f, "$where lit=$litPixels screen=$screenPixels")
}

private fun ambientPixelDelta(before: ImageBitmap, after: ImageBitmap): Int {
    assertEquals(before.width, after.width)
    assertEquals(before.height, after.height)
    val first = before.toPixelMap()
    val second = after.toPixelMap()
    var changes = 0
    repeat(before.height) { y ->
        repeat(before.width) { x ->
            if (first[x, y].toArgb() != second[x, y].toArgb()) changes++
        }
    }
    return changes
}

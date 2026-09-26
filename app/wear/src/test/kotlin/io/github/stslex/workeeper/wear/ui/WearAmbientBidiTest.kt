package io.github.stslex.workeeper.wear.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.provider.Settings
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.format.DateFormat
import android.text.style.TypefaceSpan
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.design.workeeperNativeFonts
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import org.junit.jupiter.api.Assertions.assertEquals
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
@Config(sdk = [33], qualifiers = "w192dp-h192dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearAmbientBidiTest {
    @Test
    fun arabicTwelveHourClockMatchesThePlatformParagraphOrder() = runComposeUiTest {
        val app = RuntimeEnvironment.getApplication()
        val previousFormat = Settings.System.getString(app.contentResolver, Settings.System.TIME_12_24)
        val previousZone = TimeZone.getDefault()
        try {
            Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "12")
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val locale = Locale.forLanguageTag("ar-EG")
            val model = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
                .copy(selectedLocale = locale, exerciseName = "אימון 12 kg")
            val ambient = WearAmbientState(isAmbient = true, timestampMillis = 0L)
            setContent {
                WearGateHost(WearScreen.SMALL_ROUND, LARGEST_WEAR_FONT_SCALE) {
                    WearAmbientSummary(model, ambient, hasUnsubmittedValues = false)
                }
            }
            val node = onNodeWithTag("ambient_summary").fetchSemanticsNode()
            val actual = onNodeWithTag("ambient_summary").captureToImage().asAndroidBitmap()
            val fonts = workeeperNativeFonts(app)
            val time = ambientTime(0L, locale, TimeZone.getDefault(), DateFormat.getBestDateTimePattern(locale, "hm"))
            assertTrue(time.endsWith("ص"), "The regression must exercise an Arabic day period")
            val content = ambientSummaryContent(
                app.resources,
                model,
                0L,
                false,
                TimeZone.getDefault(),
                DateFormat.getBestDateTimePattern(locale, "hm"),
            )
            val rows = ambientSummaryLayout(
                content,
                actual.width.toFloat(),
                actual.height.toFloat(),
                node.layoutInfo.density,
                false,
                fonts,
            )
            val text = SpannableString(time).apply {
                val numberEnd = time.indexOfFirst(Char::isWhitespace)
                assertTrue(numberEnd > 0)
                setSpan(TypefaceSpan(fonts.numeric), 0, numberEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(TypefaceSpan(fonts.mono), numberEnd, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            assertParagraphPixels(actual, rows.first(), text, "clock")
            val context = SpannableString(requireNotNull(model.exerciseName)).apply {
                setSpan(TypefaceSpan(fonts.text), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            assertParagraphPixels(actual, rows.single { it.role == AmbientLineRole.CONTEXT }, context, "context")
        } finally {
            Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, previousFormat)
            TimeZone.setDefault(previousZone)
        }
    }

    private fun assertParagraphPixels(actual: Bitmap, row: AmbientDrawLine, text: SpannableString, label: String) {
        val paragraph = StaticLayout.Builder.obtain(text, 0, text.length, row.paint, actual.width)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        assertEquals(-1, paragraph.getParagraphDirection(0), "The first strong character sets RTL order")
        val expected = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expected)
        canvas.drawColor(Color.BLACK)
        canvas.translate(
            (actual.width - paragraph.getLineWidth(0)) / 2f - paragraph.getLineLeft(0),
            row.baselinePx - paragraph.getLineBaseline(0),
        )
        paragraph.draw(canvas)
        val output = File("build/reports/wear-ambient-bidi").apply { mkdirs() }
        listOf("actual" to actual, "expected" to expected).forEach { (name, bitmap) ->
            File(output, "$label-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        var expectedInk = 0
        var mismatches = 0
        for (y in floor(row.baselinePx + row.ascentPx).toInt() until ceil(row.baselinePx + row.descentPx).toInt()) {
            repeat(actual.width) { x ->
                if (expected.getPixel(x, y) != Color.BLACK) expectedInk++
                if (actual.getPixel(x, y) != expected.getPixel(x, y)) mismatches++
            }
        }
        assertTrue(expectedInk > 0)
        assertEquals(0, mismatches, "Ambient $label pixels must retain paragraph bidi order")
        expected.recycle()
    }
}

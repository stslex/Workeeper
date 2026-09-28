package io.github.stslex.workeeper.wear.ui

import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.ui.unit.Density
import io.github.stslex.workeeper.core.ui.design.resources.Res
import io.github.stslex.workeeper.core.ui.design.workeeperNativeFonts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Locale

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class WearAmbientFontsTest {
    @Test
    fun numericFragmentsAndRussianWordsUseTheirBundledRoles() {
        val context = RuntimeEnvironment.getApplication()
        fun font(name: String): Typeface = Typeface.createFromAsset(
            context.assets,
            Res.getUri("font/$name").removePrefix("file:///android_asset/"),
        )
        val numeric = font("archivo_bold_wdth116.ttf")
        val text = font("ibm_plex_sans_regular.ttf")
        val mono = font("ibm_plex_mono_regular.ttf")
        val content = AmbientSummaryContent(
            listOf(
                AmbientSummaryLine(AmbientLineRole.CONTEXT, "Тренировка 42"),
                AmbientSummaryLine(AmbientLineRole.WEIGHT, "999,99 кг"),
                AmbientSummaryLine(AmbientLineRole.REPS, "12 повторов"),
            ),
            description = "Synthetic font contract",
        )
        val rows = ambientSummaryLayout(content, 192f, 192f, Density(1f, 1.24f), true, workeeperNativeFonts(context))
        assertEquals(text, rows.first().runs.single().paint.typeface)
        rows.drop(1).forEach { row ->
            assertEquals(2, row.runs.size)
            assertEquals(numeric, row.runs.first().paint.typeface)
            assertTrue(row.runs.first().text.none(Char::isLetter))
            assertEquals(mono, row.runs.last().paint.typeface)
            assertTrue(row.runs.last().text.any(Char::isLetter))
        }
    }

    @Test
    fun localizedNumbersKeepTheirNumericFontAndSeparators() {
        val fonts = workeeperNativeFonts(RuntimeEnvironment.getApplication())
        val values = WearValueFormatter.format(128, 7_253, Locale.forLanguageTag("ar-EG"))
        val numbers = listOf(requireNotNull(values.reps), requireNotNull(values.weight), "۱۲۸", "۷۲٫۵۳", "١٬٢٣٤٫٥٦")
        numbers.forEach { number ->
            val line = AmbientSummaryLine(AmbientLineRole.WEIGHT, "$number كغ")
            val paint = TextPaint().apply {
                textSize = 18f
                typeface = fonts.text
            }
            val runs = ambientTextRuns(line, paint, fonts)
            assertEquals(2, runs.size, "$number must keep one numeric fragment and one unit run")
            assertEquals(number, runs.first().text, "localized separators belong to the number")
            assertEquals(fonts.numeric, runs.first().paint.typeface, "localized digits use the numeric role")
            assertEquals(" كغ", runs.last().text)
            assertEquals(fonts.mono, runs.last().paint.typeface)
        }
    }
}

package io.github.stslex.workeeper.wear.ui

import android.graphics.Typeface
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
}

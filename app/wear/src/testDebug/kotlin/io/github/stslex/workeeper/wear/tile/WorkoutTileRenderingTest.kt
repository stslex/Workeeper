// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.renderer.TileRenderer
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
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
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w192dp-h192dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class WorkoutTileRenderingTest {
    @Test
    fun localizedStaleStatusIsFullyDrawnInsideTheRoundTile() {
        listOf(192, 240).forEach { diameter ->
            listOf(1f, 1.24f).forEach { scale ->
                listOf("en", "ru").forEach { language ->
                    assertRenderedStatus(diameter, scale, language)
                }
            }
        }
    }

    private fun assertRenderedStatus(diameter: Int, scale: Float, language: String) {
        RuntimeEnvironment.setFontScale(scale)
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply {
            fontScale = scale
            screenWidthDp = diameter
            screenHeightDp = diameter
            setLocale(Locale.forLanguageTag(language))
        }
        val context = app.createConfigurationContext(configuration)
        val frame = FrameLayout(context)
        val model = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
            .copy(
                kind = WearSurfaceKind.REFRESH_REQUIRED,
                trainingName = if (language == "ru") {
                    "Продолжительная тренировка мышц всего тела"
                } else {
                    "Full body strength and conditioning programme"
                },
            )
        val tile = WorkoutTileRenderer(context).render(model, diameter)
        val layout = requireNotNull(tile.tileTimeline).timelineEntries.single().layout!!
        val resources = ResourceBuilders.Resources.Builder().setVersion(tile.resourcesVersion).build()
        val rendered = TileRenderer(context, Runnable::run) { }.inflateAsync(layout, resources, frame)
            .get(5, TimeUnit.SECONDS)
        if (rendered.parent == null) frame.addView(rendered)
        val size = (diameter * context.resources.displayMetrics.density).roundToInt()
        val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        frame.measure(spec, spec)
        frame.layout(0, 0, size, size)
        frame.textViews().forEach { text -> assertVisibleLinesInsideCircle(frame, text, size) }
        val status = frame.textViews().single { it.text.toString() == context.getString(R.string.refresh_required) }
        val where = "$diameter/$scale/$language"
        assertEquals(12f * context.resources.displayMetrics.density * scale, status.textSize, 0.01f, where)
        val textLayout = requireNotNull(status.layout)
        assertEquals(status.text.length, textLayout.getLineEnd(textLayout.lineCount - 1), where)
        repeat(textLayout.lineCount) { line ->
            assertEquals(0, textLayout.getEllipsisCount(line), "$where full status must be visible")
        }
        val bounds = Rect(0, 0, status.width, status.height).also { frame.offsetDescendantRectToMyCoords(status, it) }
        val center = size / 2f
        listOf(bounds.left.toFloat(), bounds.right.toFloat()).forEach { x ->
            listOf(bounds.top.toFloat(), bounds.bottom.toFloat()).forEach { y ->
                assertTrue(
                    (x - center) * (x - center) + (y - center) * (y - center) < center * center,
                    "$where $bounds",
                )
            }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bitmap))
        val directory = File("build/reports/wear-tile-style").apply { mkdirs() }
        File(directory, "$diameter-$scale-$language.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun assertVisibleLinesInsideCircle(frame: FrameLayout, text: TextView, size: Int) {
        val layout = requireNotNull(text.layout)
        val bounds = Rect(0, 0, text.width, text.height).also { frame.offsetDescendantRectToMyCoords(text, it) }
        val center = size / 2f
        repeat(layout.lineCount) { line ->
            val left = bounds.left + text.totalPaddingLeft + layout.getLineLeft(line)
            val right = bounds.left + text.totalPaddingLeft + layout.getLineRight(line)
            val top = bounds.top + text.totalPaddingTop + layout.getLineTop(line).toFloat()
            val bottom = bounds.top + text.totalPaddingTop + layout.getLineBottom(line).toFloat()
            listOf(left, right).forEach { x ->
                listOf(top, bottom).forEach { y ->
                    assertTrue(
                        (x - center) * (x - center) + (y - center) * (y - center) <= center * center,
                        "Tile line crosses the round edge: '${text.text}' line $line at $x,$y",
                    )
                }
            }
        }
    }

    private fun View.textViews(): List<TextView> = when (this) {
        is TextView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).textViews() }
        else -> emptyList()
    }
}

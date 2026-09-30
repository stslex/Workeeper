package io.github.stslex.workeeper.wear.ui

import android.graphics.Canvas
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.TypefaceSpan
import androidx.core.graphics.withSave
import io.github.stslex.workeeper.wear.ambient.WearAmbientOffset
import java.text.Bidi
import kotlin.math.ceil

internal fun ambientParagraph(runs: List<AmbientTextRun>, paint: TextPaint): StaticLayout? {
    val value = runs.joinToString("") { it.text }
    if (!Bidi.requiresBidi(value.toCharArray(), 0, value.length)) return null
    // Keep font spans in one paragraph so numeric fragments retain the surrounding bidi order.
    val styled = SpannableString(value)
    var start = 0
    runs.forEach { run ->
        val end = start + run.text.length
        styled.setSpan(TypefaceSpan(run.paint.typeface), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        start = end
    }
    val width = ceil(Layout.getDesiredWidth(styled, paint)).toInt().coerceAtLeast(1)
    return StaticLayout.Builder.obtain(styled, 0, styled.length, paint, width)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setMaxLines(1)
        .build()
}

internal fun Canvas.drawAmbientLine(line: AmbientDrawLine, offset: WearAmbientOffset) {
    val paragraph = line.paragraph
    val baseline = line.baselinePx + offset.yPx
    if (paragraph != null) {
        withSave {
            translate(line.xPx + offset.xPx - paragraph.getLineLeft(0), baseline - paragraph.getLineBaseline(0))
            paragraph.draw(this)
        }
    } else {
        var x = line.xPx + offset.xPx
        line.runs.forEach { run ->
            drawText(run.text, x, baseline, run.paint)
            x += run.widthPx
        }
    }
}

package io.github.stslex.workeeper.wear.ui

import android.text.TextPaint
import io.github.stslex.workeeper.core.ui.design.AppNativeFonts

internal data class AmbientTextRun(val text: String, val paint: TextPaint) {
    val widthPx: Float = paint.measureText(text)
}

internal fun fitAmbientNumericPaint(
    line: AmbientSummaryLine,
    paint: TextPaint,
    fonts: AppNativeFonts,
    availableWidth: Float,
): TextPaint {
    var lower = 0f
    var upper = paint.textSize
    var fitted = TextPaint(paint).apply { textSize = lower }
    repeat(FONT_FIT_ITERATIONS) {
        val candidate = TextPaint(paint).apply { textSize = (lower + upper) / 2f }
        val width = ambientTextRuns(line, candidate, fonts).sumOf { it.widthPx.toDouble() }
        if (width <= availableWidth) {
            lower = candidate.textSize
            fitted = candidate
        } else {
            upper = candidate.textSize
        }
    }
    return fitted
}

internal fun ambientTextRuns(
    line: AmbientSummaryLine,
    paint: TextPaint,
    fonts: AppNativeFonts,
): List<AmbientTextRun> {
    val numeric = if (line.role in NUMERIC_ROLES) {
        NUMERIC_FRAGMENT.findAll(line.text).filter { it.value.any(Char::isDigit) }.toList()
    } else {
        emptyList()
    }
    if (numeric.isEmpty()) return listOf(AmbientTextRun(line.text, paint))
    val numericPaint = TextPaint(paint).apply { typeface = fonts.numeric }
    val unitPaint = TextPaint(paint).apply { typeface = fonts.mono }
    return buildList {
        var cursor = 0
        numeric.forEach { match ->
            if (cursor < match.range.first) {
                add(AmbientTextRun(line.text.substring(cursor, match.range.first), unitPaint))
            }
            add(AmbientTextRun(match.value, numericPaint))
            cursor = match.range.last + 1
        }
        if (cursor < line.text.length) add(AmbientTextRun(line.text.substring(cursor), unitPaint))
    }
}

private val NUMERIC_FRAGMENT = Regex("""[\p{Nd}.,٫٬:/+–−-]+""")
private const val FONT_FIT_ITERATIONS = 10
private val NUMERIC_ROLES = setOf(
    AmbientLineRole.TIME,
    AmbientLineRole.PROGRESS,
    AmbientLineRole.WEIGHT,
    AmbientLineRole.REPS,
)

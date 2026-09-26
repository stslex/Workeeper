// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.design.AppNativeFonts
import io.github.stslex.workeeper.core.ui.design.workeeperNativeFonts
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
internal fun WearAmbientSummary(
    model: WearSurfaceModel,
    ambient: WearAmbientState,
    hasUnsubmittedValues: Boolean,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration, model.selectedLocale) {
        val localized = Configuration(configuration).apply { setLocale(model.selectedLocale) }
        context.createConfigurationContext(localized).resources
    }
    val timeZone = TimeZone.getDefault()
    val use24HourFormat = DateFormat.is24HourFormat(context)
    val timePattern = remember(model.selectedLocale, use24HourFormat) {
        DateFormat.getBestDateTimePattern(model.selectedLocale, if (use24HourFormat) "Hm" else "hm")
    }
    val content = remember(resources, model, ambient.timestampMillis, hasUnsubmittedValues, timeZone, timePattern) {
        ambientSummaryContent(resources, model, ambient.timestampMillis, hasUnsubmittedValues, timeZone, timePattern)
    }
    val density = LocalDensity.current
    val fonts = remember(context) { workeeperNativeFonts(context) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = remember(content, constraints, density, ambient.deviceHasLowBitAmbient, fonts) {
            ambientSummaryLayout(
                content = content,
                widthPx = constraints.maxWidth.toFloat(),
                heightPx = constraints.maxHeight.toFloat(),
                density = density,
                lowBit = ambient.deviceHasLowBitAmbient,
                fonts = fonts,
            )
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("ambient_summary")
                .clearAndSetSemantics {
                    contentDescription = content.description
                    stateDescription = resources.getString(R.string.ambient_read_only)
                },
        ) {
            val native = drawContext.canvas.nativeCanvas
            native.drawColor(Color.BLACK)
            layout.forEach { line ->
                var x = line.xPx + ambient.offset.xPx
                line.runs.forEach { run ->
                    native.drawText(run.text, x, line.baselinePx + ambient.offset.yPx, run.paint)
                    x += run.widthPx
                }
            }
        }
    }
}

internal enum class AmbientLineRole(val sizeSp: Int) {
    TIME(AMBIENT_VALUE_SIZE_SP),
    CONTEXT(AMBIENT_CONTEXT_SIZE_SP),
    PROGRESS(AMBIENT_CONTEXT_SIZE_SP),
    WEIGHT(AMBIENT_VALUE_SIZE_SP),
    REPS(AMBIENT_VALUE_SIZE_SP),
    STATUS(AMBIENT_CONTEXT_SIZE_SP),
    DRAFT(AMBIENT_CONTEXT_SIZE_SP),
}

internal data class AmbientSummaryLine(val role: AmbientLineRole, val text: String)

internal data class AmbientSummaryContent(val lines: List<AmbientSummaryLine>, val description: String)

internal data class AmbientDrawLine(
    val role: AmbientLineRole,
    val text: String,
    val xPx: Float,
    val baselinePx: Float,
    val availableWidthPx: Float,
    val paint: TextPaint,
    val runs: List<AmbientTextRun>,
    val ascentPx: Float,
    val descentPx: Float,
    val fonts: AppNativeFonts,
) {
    val widthPx: Float = runs.sumOf { it.widthPx.toDouble() }.toFloat()
}

internal fun ambientSummaryContent(
    resources: Resources,
    model: WearSurfaceModel,
    timestampMillis: Long?,
    hasUnsubmittedValues: Boolean,
    timeZone: TimeZone,
    timePattern: String = "HH:mm",
): AmbientSummaryContent {
    val time = ambientTime(timestampMillis, model.selectedLocale, timeZone, timePattern)
    val context = model.exerciseName ?: model.trainingName ?: resources.getString(R.string.workout_generic)
    val progress = model.setOrdinal?.let { current ->
        model.totalSets?.let { total ->
            String.format(model.selectedLocale, "%d/%d", current, total)
        }
    }
    val fullProgress = if (model.setOrdinal != null && model.totalSets != null) {
        resources.getString(R.string.set_progress, model.setOrdinal, model.totalSets)
    } else {
        null
    }
    val weight = when {
        model.reps == null -> null
        !model.weighted -> resources.getString(R.string.ambient_weightless)
        model.formattedValues.weight == null -> resources.getString(R.string.weight_unset)
        else -> resources.getString(R.string.weight_value, model.formattedValues.weight)
    }
    val spokenWeight = weight?.let {
        if (model.weighted) "${resources.getString(R.string.weight_label)}: $it" else it
    }
    val reps = model.formattedValues.reps?.let { resources.getString(R.string.ambient_reps, it) }
    val status = resources.getString(ambientStatusResource(model))
    val draft = resources.getString(R.string.ambient_not_sent).takeIf { hasUnsubmittedValues }
    val lines = buildList {
        add(AmbientSummaryLine(AmbientLineRole.TIME, time))
        add(AmbientSummaryLine(AmbientLineRole.CONTEXT, context))
        progress?.let { add(AmbientSummaryLine(AmbientLineRole.PROGRESS, it)) }
        weight?.let { add(AmbientSummaryLine(AmbientLineRole.WEIGHT, it)) }
        reps?.let { add(AmbientSummaryLine(AmbientLineRole.REPS, it)) }
        add(AmbientSummaryLine(AmbientLineRole.STATUS, status))
        draft?.let { add(AmbientSummaryLine(AmbientLineRole.DRAFT, it)) }
    }
    val availability = resources.getString(
        if (model.completeEnabled) {
            R.string.complete_set_enabled_description
        } else {
            R.string.complete_set_disabled_description
        },
    )
    return AmbientSummaryContent(
        lines = lines,
        description = listOfNotNull(time, context, fullProgress, spokenWeight, reps, status, availability, draft)
            .joinToString(". "),
    )
}

private fun ambientStatusResource(model: WearSurfaceModel): Int = when (model.completionUnavailableReason) {
    CompletionUnavailableReason.DISCONNECTED -> R.string.complete_reason_disconnected
    CompletionUnavailableReason.REFRESH_REQUIRED -> R.string.complete_reason_refresh
    CompletionUnavailableReason.COMMAND_IN_FLIGHT -> R.string.complete_reason_sending
    CompletionUnavailableReason.INVALID_REPS -> R.string.complete_reason_reps
    CompletionUnavailableReason.INVALID_WEIGHT -> R.string.complete_reason_weight
    null -> when (model.kind) {
        WearSurfaceKind.LOADING -> R.string.loading
        WearSurfaceKind.NO_SESSION -> R.string.ambient_no_workout
        WearSurfaceKind.ACTIVE -> R.string.ready
        WearSurfaceKind.PHONE_ACTION_NO_SETS -> R.string.ambient_add_set
        WearSurfaceKind.PHONE_ACTION_UNSUPPORTED -> R.string.ambient_edit_phone
        WearSurfaceKind.PAYLOAD_TOO_LARGE -> R.string.ambient_open_phone
        WearSurfaceKind.WORKOUT_COMPLETE -> R.string.workout_complete
        WearSurfaceKind.REFRESH_REQUIRED -> R.string.complete_reason_refresh
        WearSurfaceKind.DISCONNECTED -> R.string.complete_reason_disconnected
        WearSurfaceKind.RETRYABLE_ERROR -> R.string.ambient_retry_phone
        WearSurfaceKind.PROTOCOL_MISMATCH -> R.string.ambient_update_apps
    }
}

internal fun ambientTime(
    timestampMillis: Long?,
    locale: Locale,
    timeZone: TimeZone,
    timePattern: String = "HH:mm",
): String = timestampMillis?.let {
    SimpleDateFormat(timePattern, locale).apply { this.timeZone = timeZone }.format(Date(it))
} ?: "--:--"

internal fun ambientSummaryLayout(
    content: AmbientSummaryContent,
    widthPx: Float,
    heightPx: Float,
    density: Density,
    lowBit: Boolean,
    fonts: AppNativeFonts,
): List<AmbientDrawLine> {
    val paints = content.lines.map { line ->
        TextPaint().apply {
            color = Color.WHITE
            textSize = with(density) { line.role.sizeSp.sp.toPx() }
            isAntiAlias = !lowBit
            isSubpixelText = false
            isDither = false
            textAlign = Paint.Align.LEFT
            typeface = fonts.text
        }
    }
    val runs = content.lines.mapIndexed { index, line -> ambientTextRuns(line, paints[index], fonts) }
    val tops = runs.map { line -> line.minOf { it.paint.fontMetrics.ascent } }
    val bottoms = runs.map { line -> line.maxOf { it.paint.fontMetrics.descent } }
    val gap = with(density) { 1.dp.toPx() }
    val totalHeight = tops.indices.sumOf { (bottoms[it] - tops[it]).toDouble() }.toFloat() +
        gap * (content.lines.size - 1)
    val radius = min(widthPx, heightPx) / 2f - AMBIENT_CONTENT_INSET_PX
    var top = (heightPx - totalHeight) / 2f
    return content.lines.mapIndexed { index, line ->
        val height = bottoms[index] - tops[index]
        val furthestY = max(abs(top - heightPx / 2f), abs(top + height - heightPx / 2f))
        val chord = 2f * sqrt(max(0f, radius * radius - furthestY * furthestY))
        val availableWidth = max(0f, chord - AMBIENT_INK_GUARD_PX)
        val rowWidth = runs[index].sumOf { it.widthPx.toDouble() }.toFloat()
        val fitNumericRow = line.role == AmbientLineRole.PROGRESS || line.role == AmbientLineRole.TIME
        val paint = if (fitNumericRow && rowWidth > availableWidth) {
            fitAmbientNumericPaint(line, paints[index], fonts, availableWidth)
        } else {
            paints[index]
        }
        val drawn = if (line.role == AmbientLineRole.CONTEXT) {
            TextUtils.ellipsize(
                line.text,
                paint,
                max(0f, availableWidth - AMBIENT_INK_GUARD_PX),
                TextUtils.TruncateAt.END,
            ).toString()
        } else {
            line.text
        }
        val drawnRuns = ambientTextRuns(line.copy(text = drawn), paint, fonts)
        val drawnWidth = drawnRuns.sumOf { it.widthPx.toDouble() }.toFloat()
        AmbientDrawLine(
            role = line.role,
            text = drawn,
            xPx = (widthPx - drawnWidth) / 2f,
            baselinePx = top - tops[index],
            availableWidthPx = availableWidth,
            paint = paint,
            runs = drawnRuns,
            ascentPx = tops[index],
            descentPx = bottoms[index],
            fonts = fonts,
        ).also { top += height + gap }
    }
}

private const val AMBIENT_CONTENT_INSET_PX = 12f
private const val AMBIENT_INK_GUARD_PX = 2f

private const val AMBIENT_VALUE_SIZE_SP = 14
private const val AMBIENT_CONTEXT_SIZE_SP = 10

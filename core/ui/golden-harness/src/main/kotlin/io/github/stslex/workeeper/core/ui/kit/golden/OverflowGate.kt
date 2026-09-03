// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import app.cash.paparazzi.PaparazziSdk
import com.android.ide.common.rendering.api.SessionParams
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

/*
 * The set-field overflow gate's render harness — a measurement gate, not a snapshot gate: the SDK
 * is built with a discarding frame consumer, so no PNG is written (set-field-column-headers §6).
 */
class OverflowGateSdk(private val theme: GoldenTheme = GoldenTheme.LIGHT) {

    private var frameHook: (() -> Unit)? = null

    private val sdk = PaparazziSdk(
        deviceConfig = GOLDEN_DEVICE,
        theme = theme.windowTheme,
        renderingMode = SessionParams.RenderingMode.SHRINK,
        useDeviceResolution = true,
        onNewFrame = {
            // [frameHook] runs here: the frame is the one window where semantics are live.
            frameHook?.invoke()
        },
    )

    /** The render session's Android context, for building caller-owned host views. */
    val context: android.content.Context get() = sdk.context

    fun setup() {
        sdk.setup()
        sdk.prepare()
        registerComposeResourcesContext(sdk.context)
    }

    fun teardown() {
        sdk.teardown()
    }

    /** Rebuilds the render session on [GOLDEN_DEVICE] at [scale] — dp geometry is unchanged. */
    fun setFontScale(scale: Float) {
        sdk.unsafeUpdateConfig(deviceConfig = GOLDEN_DEVICE.copy(fontScale = scale))
    }

    /** Composes [content] under [AppTheme] in the current session. Frames are discarded. */
    fun render(content: @Composable () -> Unit) {
        sdk.snapshot {
            AppTheme(themeMode = theme.themeMode) {
                content()
            }
        }
    }

    /**
     * Renders a caller-owned [view], invoking [onFrame] while it is attached and its composition
     * is live — the only window in which a semantics-tree read is valid.
     */
    fun renderView(view: android.view.View, onFrame: () -> Unit) {
        frameHook = onFrame
        try {
            sdk.snapshot(view)
        } finally {
            frameHook = null
        }
    }

    /**
     * True single-line advance of [text] at [style], in px, on the current session's device.
     * Fails loudly if layout never ran — a gate that measures nothing must not report a pass.
     */
    fun measureTextWidthPx(text: String, style: TextStyle): Float {
        var measured = Float.NaN
        render {
            BasicText(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { measured = it.multiParagraph.width },
            )
        }
        check(!measured.isNaN()) { "measurement pass produced no text layout for \"$text\"" }
        return measured
    }
}

/**
 * One matrix cell: the slot captured from `AppNumberInput.valueSlotProbe` against the measured
 * text extent, both in device px.
 */
data class GateCell(
    val row: String,
    val column: String,
    val glyphs: Int,
    val fontScale: Float,
    val slotWidthPx: Int,
    val textWidthPx: Float,
) {

    val overflows: Boolean get() = textWidthPx > slotWidthPx.toFloat()

    fun describe(): String =
        "$row.$column glyphs=$glyphs fontScale=$fontScale: " +
            "text ${textWidthPx}px vs slot ${slotWidthPx}px " +
            if (overflows) "(OVERFLOW by ${textWidthPx - slotWidthPx}px)" else "(fits)"
}

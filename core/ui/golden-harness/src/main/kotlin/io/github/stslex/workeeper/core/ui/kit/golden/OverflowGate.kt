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
 * The set-field overflow gate's render harness (set-field-column-headers.md §6).
 *
 * ## A measurement gate, not a snapshot gate
 *
 * `Paparazzi` is `PaparazziSdk` plus a `SnapshotHandler` (verify / record / HTML report).
 * This harness constructs the SDK directly with a frame consumer that discards every frame,
 * so there is no handler to verify against, nothing is recorded, and no PNG is written —
 * regardless of which gradle task runs the test. `verifyPaparazziDebug` therefore cannot
 * fail a gate test on a missing golden, and `assertGoldenLiveness` never sees it: the gate
 * classes live outside `*.golden.*` packages and contribute zero snapshot files.
 *
 * (`PaparazziSdk.snapshot(composable)` shares a name with the golden-semantics
 * `Paparazzi.snapshot()` but is only "compose this and hand me the frames" — with a
 * discarding consumer it carries no comparison semantics at all.)
 *
 * ## The slot width is captured, not computed
 *
 * The consumer renders the production row and captures the slot through
 * `AppNumberInput.valueSlotProbe`; recomputing it from the same paddings and flex constants
 * the row uses would make the gate agree with the row by construction, including when both
 * are wrong. Mutating any width inside the row — a chip minimum, a gap, a flex weight —
 * moves the captured number.
 *
 * ## The text extent comes from a proxy, not from the field
 *
 * `BasicTextField(singleLine = true)` measures its text at `maxWidth = Infinity` and hides
 * overflow in its horizontal-scroll layer, so its own `TextLayoutResult` reports
 * `hasVisualOverflow = false` while glyphs are visibly lost (spec §6). The faithful extent
 * is a single-line, `softWrap = false` [BasicText] laid out unconstrained:
 * `multiParagraph.width` is then the true advance of the string at the style under test.
 */
class OverflowGateSdk(private val theme: GoldenTheme = GoldenTheme.LIGHT) {

    private var frameHook: (() -> Unit)? = null

    private val sdk = PaparazziSdk(
        deviceConfig = GOLDEN_DEVICE,
        theme = theme.windowTheme,
        renderingMode = SessionParams.RenderingMode.SHRINK,
        useDeviceResolution = true,
        onNewFrame = {
            // The image is discarded on purpose: a frame consumer that stored or compared
            // images would re-introduce exactly the snapshot semantics this harness exists
            // to avoid. [frameHook] runs here because the frame is the one moment the
            // caller's view is attached and composed — the window where a semantics-tree
            // read is guaranteed live.
            frameHook?.invoke()
        },
    )

    /** The render session's Android context, for building caller-owned host views. */
    val context: android.content.Context get() = sdk.context

    fun setup() {
        sdk.setup()
        sdk.prepare()
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
     * Renders a caller-owned [view], invoking [onFrame] while it is attached and its
     * composition is live — the only window in which a read of the rendered semantics tree
     * (`ViewRootForTest.semanticsOwner`, the public access path Paparazzi's own
     * accessibility extension uses under layoutlib) is guaranteed valid. The caller wraps
     * its content in [AppTheme] itself.
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
     * The true single-line advance of [text] at [style], in px, on the current session's
     * device (so the current font scale applies). Fails loudly if layout never ran — a gate
     * that silently measures nothing must never report a pass.
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
 * One matrix cell: the captured slot vs the measured text extent, both in device px.
 * [slotWidthPx] arrives from `AppNumberInput.valueSlotProbe` (the field's incoming max
 * width constraint); [textWidthPx] from [OverflowGateSdk.measureTextWidthPx] at the style
 * the field actually resolved for this value.
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

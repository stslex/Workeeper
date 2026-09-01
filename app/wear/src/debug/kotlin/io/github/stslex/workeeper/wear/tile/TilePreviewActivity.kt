// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.renderer.TileRenderer
import io.github.stslex.workeeper.wear.state.WatchProcessState
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures

/** Debug-only platform renderer harness for fresh Tile screenshots. */
class TilePreviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parent = FrameLayout(this)
        setContentView(parent)
        val synthetic = SyntheticSurfaceFixtures.find(
            intent.getStringExtra(SyntheticSurfaceFixtures.EXTRA_ID),
        )
        val tile = WorkoutTileRenderer(this).render(
            synthetic ?: WatchProcessState.currentSurface(),
        )
        val timeline = requireNotNull(tile.tileTimeline)
        val layout = requireNotNull(timeline.timelineEntries.single().layout)
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(tile.resourcesVersion)
            .build()
        val renderer = TileRenderer(this, mainExecutor) { }
        val rendered = renderer.inflateAsync(layout, resources, parent)
        rendered.addListener(
            {
                val view = rendered.get()
                if (view.parent == null) parent.addView(view)
            },
            mainExecutor,
        )
    }
}

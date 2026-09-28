package io.github.stslex.workeeper.wear.mvi.mapper

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.wear.BuildConfig
import io.github.stslex.workeeper.wear.di.WearScope
import io.github.stslex.workeeper.wear.mvi.store.WearPlatformState
import io.github.stslex.workeeper.wear.mvi.store.WearPresentation
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import io.github.stslex.workeeper.wear.ui.ongoingNotice

@Inject
@SingleIn(WearScope::class)
internal class WearPresentationMapper {
    private var lastInput: Pair<WatchRuntimeSnapshot, String?>? = null
    private var lastModel: WearSurfaceModel? = null

    fun map(snapshot: WatchRuntimeSnapshot, platform: WearPlatformState): WearPresentation {
        val previewId = platform.previewId.takeIf { BuildConfig.DEBUG }
        val input = snapshot.copy(ongoing = OngoingStatus.Inactive) to previewId
        val model = if (input == lastInput) {
            requireNotNull(lastModel)
        } else {
            val preview = previewId?.let(SyntheticSurfaceFixtures::find)
            (preview?.copy(selectedLocale = snapshot.locale) ?: WearSurfaceMapper.map(snapshot)).also {
                lastInput = input
                lastModel = it
            }
        }
        return WearPresentation(
            model = model,
            ambient = platform.ambient,
            notice = if (previewId == null) ongoingNotice(
                model,
                snapshot.ongoing,
                platform.notificationsEnabled,
            ) else null,
            preview = previewId != null,
        )
    }
}

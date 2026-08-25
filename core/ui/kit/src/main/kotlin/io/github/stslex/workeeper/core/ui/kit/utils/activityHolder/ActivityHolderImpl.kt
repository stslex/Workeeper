package io.github.stslex.workeeper.core.ui.kit.utils.activityHolder

import android.app.Activity
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import java.lang.ref.WeakReference

/**
 * Process-lifetime holder of the current `Activity`; resolve [ActivityHolderProducer] via DI.
 * GUARD: must stay public — a merged `AppGraph` cannot extend an internal contribution.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ActivityHolderImpl : ActivityHolderProducer {

    private var _activity: WeakReference<Activity>? = null

    val activity: Activity?
        get() = _activity?.get()

    override fun produce(activity: Activity?) {
        if (activity == null) {
            _activity?.clear()
            _activity = null
        } else {
            _activity = WeakReference(activity)
        }
    }
}

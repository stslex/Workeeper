package io.github.stslex.workeeper.core.ui.kit.utils.activityHolder

import android.app.Activity
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import java.lang.ref.WeakReference

/**
 * Metro-owned via `@ContributesBinding(AppScope)`, which the app-scope `AppGraph` auto-aggregates.
 * `@SingleIn(AppScope)` makes this a process-lifetime single-owner — one holder retains the current
 * `Activity` across the process.
 *
 * ONE bound supertype ([ActivityHolderProducer]) → the plain `@ContributesBinding(AppScope::class)`
 * form, with no `binding<>()` argument: Metro infers the bound type when there is no ambiguity.
 * `AppGraph` exposes it as `activityHolderProducer`, and `MainActivity` calls [produce] from its
 * lifecycle.
 *
 * `public` (already public here): required for cross-module Metro aggregation — the merged `AppGraph` in
 * `:app:app` cannot extend an internal contribution from another module (D1). Never hand-construct;
 * resolve `ActivityHolderProducer` via DI.
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

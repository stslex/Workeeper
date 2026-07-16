package io.github.stslex.workeeper.core.ui.kit.utils.activityHolder

import android.app.Activity
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.stslex.workeeper.core.core.di.AppScope
import java.lang.ref.WeakReference

/**
 * Metro-owned via `@ContributesBinding(AppScope)`, which the app-scope `AppGraph` auto-aggregates.
 * `@SingleIn(AppScope)` makes this a process-lifetime single-owner — one holder retains the current
 * `Activity` across the process.
 *
 * TWO supertypes ([ActivityHolder] + [ActivityHolderProducer]) → `@ContributesBinding` is `@Repeatable`,
 * applied once per bound type with an explicit `binding<>()` (the ambiguity-resolving form Metro requires
 * for a multi-supertype impl). Both contributions MUST use the same scope.
 *
 * `public` (already public here): required for cross-module Metro aggregation — the merged `AppGraph` in
 * `:app:app` cannot extend an internal contribution from another module (D1). Never hand-construct; resolve
 * `ActivityHolder`/`ActivityHolderProducer` via DI.
 */
@ContributesBinding(AppScope::class, binding = binding<ActivityHolder>())
@ContributesBinding(AppScope::class, binding = binding<ActivityHolderProducer>())
@SingleIn(AppScope::class)
@Inject
class ActivityHolderImpl : ActivityHolder, ActivityHolderProducer {

    private var _activity: WeakReference<Activity>? = null

    override val activity: Activity?
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

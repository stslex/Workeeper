package io.github.stslex.workeeper.core.ui.kit.utils.activityHolder

import android.app.Activity
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityHolderImpl @Inject constructor() : ActivityHolder, ActivityHolderProducer {

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

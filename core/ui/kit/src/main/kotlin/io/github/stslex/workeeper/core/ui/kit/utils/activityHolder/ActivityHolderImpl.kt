package io.github.stslex.workeeper.core.ui.kit.utils.activityHolder

import android.app.Activity
import org.koin.core.annotation.Single
import java.lang.ref.WeakReference

@Single
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
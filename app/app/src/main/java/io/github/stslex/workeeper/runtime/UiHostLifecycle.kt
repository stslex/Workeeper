// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.stslex.workeeper.core.core.logger.Log

/**
 * Live-host accounting by identity, not by count: a missed `attached` degrades to a no-op
 * `detached` instead of biasing the baseline into clearing while a host still composes.
 * Main-thread confined — every Activity lifecycle callback is dispatched there.
 */
internal class UiHostAttachments {

    private val live = mutableSetOf<Any>()

    fun attached(host: Any) {
        live.add(host)
    }

    /** True when this destroy leaves the process with no host that will come back. */
    fun detached(host: Any, changingConfigurations: Boolean): Boolean {
        live.remove(host)
        return live.isEmpty() && !changingConfigurations
    }
}

/**
 * Clears the generation-owned store the way `ComponentActivity` cleared its own before the §8.7
 * owner re-parenting. A transition owns its own clear; both are idempotent, and both run on the
 * main thread so they cannot interleave.
 */
internal fun AppRuntime.clearStoreOnHostTeardown() {
    val store = servingViewModelStore ?: return
    runCatching { store.clear() }.onFailure { error ->
        Log.tag("UiHostLifecycle").e(error, "host-teardown ViewModelStore clear failed")
    }
}

/**
 * Reports the one Activity destroy that is permanent. The predicate is `ComponentActivity`'s own —
 * `!isChangingConfigurations` — restored for the generation-owned store. See spec §8.7.
 */
internal class UiHostLifecycleTracker(
    private val attachments: UiHostAttachments = UiHostAttachments(),
    private val onHostGone: () -> Unit,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        attachments.attached(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (attachments.detached(activity, activity.isChangingConfigurations)) onHostGone()
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

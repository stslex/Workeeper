package io.github.stslex.workeeper.core.core.logger

/**
 * Platform seam for the analytics sink. The Android actual routes to Firebase
 * Analytics; the iOS actual is a no-op until a native analytics pipeline is wired.
 */
expect object FirebaseAnalyticsHolder {

    fun log(event: FirebaseEvent)
}

package io.github.stslex.workeeper.core.core.logger

/**
 * iOS no-op analytics sink. Replaced by a real native analytics pipeline when the iOS
 * app target lands.
 */
actual object FirebaseAnalyticsHolder {

    actual fun log(event: FirebaseEvent) = Unit
}

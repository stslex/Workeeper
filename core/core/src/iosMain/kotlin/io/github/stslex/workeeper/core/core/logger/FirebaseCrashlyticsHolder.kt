package io.github.stslex.workeeper.core.core.logger

/**
 * iOS no-op crash-reporting sink. There is no native crash reporter wired yet, so every
 * method is a no-op — the same behaviour the Android actual falls back to when Firebase
 * is uninitialised. Replaced by a real native reporter when the iOS app target lands.
 */
actual object FirebaseCrashlyticsHolder {

    actual fun log(message: String) = Unit

    actual fun recordException(throwable: Throwable, tag: String) = Unit

    actual fun setCustomKey(key: String, value: String) = Unit

    actual fun setCustomKey(key: String, value: Int) = Unit

    actual fun setCustomKey(key: String, value: Long) = Unit

    actual fun setCustomKey(key: String, value: Boolean) = Unit

    actual fun setScreenName(name: String) = Unit

    actual fun clearScreenName() = Unit

    actual fun initialize() = Unit
}

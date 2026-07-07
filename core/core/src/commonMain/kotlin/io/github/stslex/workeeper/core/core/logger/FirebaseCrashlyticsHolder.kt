package io.github.stslex.workeeper.core.core.logger

/**
 * Platform seam for the crash-reporting sink. The Android actual routes to Firebase
 * Crashlytics; the iOS actual is a no-op until a native crash reporter is wired.
 *
 * The full public surface (`log`, `recordException`, the `setCustomKey` overloads,
 * screen-name helpers, `initialize`) is preserved from the pre-KMP Android object so
 * every existing call site — `Log`, `core/ui/mvi`, `feature/recovery` diagnostics —
 * compiles unchanged.
 */
expect object FirebaseCrashlyticsHolder {

    fun log(message: String)

    fun recordException(throwable: Throwable, tag: String)

    fun setCustomKey(key: String, value: String)

    fun setCustomKey(key: String, value: Int)

    fun setCustomKey(key: String, value: Long)

    fun setCustomKey(key: String, value: Boolean)

    fun setScreenName(name: String)

    fun clearScreenName()

    fun initialize()
}

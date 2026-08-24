# Core utils — invariants

Properties of the small shared helpers in `core/core` that the implementation no longer shows:
what each replaced, what breaks when it is "simplified" back, and the test that pins it. One
section per helper.

## `NumUiUtils.roundThousand`

`core/core/src/commonMain/.../utils/NumUiUtils.kt`. Must stay a pure-math implementation. The
previous version round-tripped through `String.format(locale, "%.1f", value).toDouble()`, which
on comma-decimal locales (e.g. Russian) produced `"1,2"` and then **threw** on `toDouble()`. Two
tests in `NumUiUtilsTest` pin the property by temporarily calling
`java.util.Locale.setDefault(java.util.Locale("ru", "RU"))` and asserting
`roundThousand(1234.0) == 1.2` (1e-9) and equality with the `Locale.US` result for the same
input. A re-introduced `String.format` passes on every US/CI locale and throws on a
comma-decimal device.

## `Iterable.asyncAll`

`core/core/src/commonMain/.../coroutine/CoroutineExt.kt`. Must fold the awaited results —
`asyncMap { predicate(it) }.all { it }`. A previous implementation used an early
`return@asyncMap false`, which only short-circuited the inner map-lambda while the outer function
ignored the result and always returned `true`; `ExerciseRepositoryImpl.canBulkPermanentDelete`
read the helper at the time, so that wrong `true` greenlit permanent deletion of exercises that
still had finished-session history. Pinned by `asyncAll returns false when any predicate fails`
in `AsyncAssociateTest`, asserting `(1..5).asyncAll { it < 4 } == false`. There is no production
caller today — `canBulkPermanentDelete` uses the sequential `uuids.all { ... }`.

## `weekWindowOf` / `weekdayIndexOf`

`core/core/src/commonMain/.../time/WeekWindow.kt`. Both bounds are computed date-side via
`atStartOfDayIn(timeZone)` on `monday` and `nextMonday` — never `startMillis + 7 * 24h`: a DST
transition inside the week makes those two different instants, and no UTC-running test catches
the drift. The window is Monday-first ISO 8601 (`today.dayOfWeek.isoDayNumber - 1`, also the
Russian convention this app's copy is written in) and half-open `[startMillis, endMillis)`.
`weekdayIndexOf` returns the same Monday-first index: 0 = Monday … 6 = Sunday.

## `EventsFilter`

`core/core/src/commonMain/.../logger/EventsFilter.kt`. Debounces duplicate log/analytics events
per key within `LAST_EVENT_TIME_DIFF = 2_000L.milliseconds`, measured with a monotonic
`TimeSource` rather than wall clock, so the window is immune to clock adjustments.

It holds **no lock of its own** and is thread-safe only by its callers: it is called only from
the platform Firebase-holder actuals (`FirebaseCrashlyticsHolder`, `FirebaseAnalyticsHolder` in
`androidMain`), whose public methods are already `@Synchronized` on Android; the iOS holders are
no-ops. `kotlin.concurrent.Volatile` on `lastTrackedEvent` is what keeps the last-event read
visible across those callers. Adding a caller from anywhere else silently breaks the argument.

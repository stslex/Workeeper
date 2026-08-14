# P2.c — Room-KMP on iosSimulatorArm64 (direction-gating). Result: PASS (genuine)

Compile-only probe (no query exec, no device). Kotlin 2.3.20, AGP 9.1.0, host Linux.
Modules: `:probe-room28` (Room `androidx.room` 2.8.4) and `:probe-room3`
(Room `androidx.room3` 3.0.0). Both: 1 `@Entity` + 1 `@Dao` + 1 `@Database` with the
KMP `@ConstructedBy` + `expect object … : RoomDatabaseConstructor` in commonMain,
`androidx.sqlite:sqlite-bundled:2.7.0` (BundledSQLiteDriver).

## Verdict
Both Room 2.8.x AND Room 3.0 compile and run Room's KSP codegen on `iosSimulatorArm64`
at Kotlin 2.3.20 — **PROVIDED KSP = 2.3.9** (native codegen). Proven: the KSP-generated
K/N actual is real and the iOS compile is green WITH ZERO `@Suppress`:

```kotlin
// build/generated/ksp/iosSimulatorArm64/.../ProbeDatabaseConstructor.kt
public actual object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
  actual override fun initialize(): ProbeDatabase = ProbeDatabase_Impl()
}
```

## CRITICAL migration gotcha (green-means-proven catch)
The repo's current **KSP 2.3.6 silently SKIPS native KSP codegen** —
`kspKotlinIosSimulatorArm64` runs as `SKIPPED`, so Room's iOS actual is never generated.
The Room-KMP official pattern's `@Suppress("NO_ACTUAL_FOR_EXPECT")` on the `expect object`
then **masks the resulting error, yielding a FALSE GREEN iOS compile.** Removing the
suppress under KSP 2.3.6 exposes the truth:

```
e: ProbeDatabase.kt:42:1 Expected ProbeDatabaseConstructor has no actual declaration
   in module <commonMain> for Native
> Compilation finished with errors
```

Under KSP 2.3.9 the ksp native task runs, the actual is generated, and the compile is
green even with the suppress removed. **Takeaway for the real migration:** pin KSP ≥ 2.3.9
for any KMP module using Room on K/N, and do NOT trust a green iOS Room compile that still
carries `@Suppress("NO_ACTUAL_FOR_EXPECT")` — verify the generated `actual` exists.

The KSP 2.3.6 → 2.3.9 bump keeps the existing Android build green (assembleDebug + detekt
repo-wide pass). It does not bump Kotlin, so it does not cascade into the custom Detekt
rules / embeddable-Kotlin.

## Residual (benign)
`-Xexpect-actual-classes` Beta warning (KT-61573) on the `expect object` — a warning, not
an error; the standard Room-KMP `-Xexpect-actual-classes` compiler flag silences it.

## Room 2.8.x vs 3.0
Both compile identically on K/N here. Room 2.8.x is what Google's KMP setup guide still
documents (last-updated 2026-07-01); Room 3.0 (`androidx.room3`, stable 2026-07-01) is the
KMP-modern forward path (2.x now maintenance-mode). Migration between them is "mostly
import-reference updates." Neither states a Kotlin floor; both empirically work at 2.3.20.

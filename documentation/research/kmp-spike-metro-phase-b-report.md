# Phase B — Metro DI Spike Report

**Instrument:** go/no-go probe for the KMP direction. **Isolation:** all work in the
disposable `:spike-metro` module on branch `spike/metro-di`, NOT wired into `:app`,
NOT on the PR branch, not pushed. Revert = delete the module + one convention plugin +
additive catalog/settings entries.

**Toolchain:** Kotlin 2.3.20, AGP 9.1.0, Gradle 9.5.0, Metro **1.1.1**, host = Linux
x86_64 (no macOS/Xcode).

---

## VERDICT: **GO** (recommended) — both locked gates pass

| Pass criterion (locked) | Result |
|---|---|
| **(2a)** Metro graph compiles for androidMain AND iosSimulatorArm64 | ✅ **YES** (with version-aligned Metro 1.1.1) |
| **(2b)** compile-time scope enforcement holds (violation fails at COMPILE, not runtime) | ✅ **YES** — `[Metro/IncompatiblyScopedBindings]` on BOTH targets |

GO is conditional on two findings the maintainer must fold into the cutover plan
(neither is a blocker): **Metro↔Kotlin version alignment** and the **BaseStore
ViewModel-coupling restructure**. Details below.

---

## Per-sub-phase status

| Phase | Status | Outcome |
|---|---|---|
| **B.0** KMP scaffolding (android only) | ✅ GREEN | Full repo green (assembleDebug/detekt/lintDebug/testDebugUnitTest); zero existing-module edits. |
| **B.1** trivial Metro graph, both targets | ✅ GREEN | Compiles for android + iosSimulatorArm64. Surfaced the Metro-version/ABI finding. |
| **B.2** canonical topology + scope enforcement | ✅ GREEN | 2a + 2b both pass. One design finding (ViewModel coupling). |
| **B.3** build-time measurement | ✅ recorded | Directional only; see caveat. |

---

## Gate 2a — both-target compile: **PASS**

The canonical topology (`CanonicalTopology.kt`, commonMain) reproduces the app's Hilt
shape 1:1 and compiles for **both** targets:

| App (Hilt) | Spike (Metro) |
|---|---|
| `@Singleton` (SingletonComponent) | `ExerciseRepository @SingleIn(AppScope)` |
| `@ViewModelScoped` (ViewModelComponent) | `ChartInteractor @SingleIn(FeatureScope)` (consumes the app singleton) |
| `@HiltViewModel(assistedFactory=…)` + `@AssistedInject` | `ChartStore @AssistedInject(@Assisted screenId, interactor, name = DEFAULT_NAME)` + `@AssistedFactory` |
| `BaseStore(initialActions = emptyList(), …)` default-arg seams | `ChartStore(name: String = DEFAULT_NAME)` — supported |
| SingletonComponent → ViewModelComponent hierarchy | `AppGraph @DependencyGraph(AppScope)` → `FeatureGraph @GraphExtension(FeatureScope)` |

Metro codegen verified **real** (not a silent no-op): generated classes present in the
android output — `AppGraph$Impl$FeatureGraphImpl` (the parent→child extension impl),
`ChartStore$Factory$Impl` (assisted factory), `*$MetroFactory`. On iOS, a deliberately
unsatisfiable graph failed at compile with `[Metro/MissingBinding]`, proving the
IR-backend validation runs on Kotlin/Native too.

## Gate 2b — compile-time scope enforcement: **PASS**

A deliberate violation — an `AppScope`-only `@DependencyGraph` exposing an accessor for
the `FeatureScope`-scoped `ChartInteractor` — **fails at COMPILE on BOTH targets**:

```
e: [Metro/IncompatiblyScopedBindings] BadScopeGraph (scopes '@SingleIn(AppScope::class)')
   may not reference bindings from different scopes:
     ChartInteractor (scoped to '@SingleIn(FeatureScope::class)')
```

This is the exact property Koin was rejected for (Koin fails only at runtime). Fixture +
captured error preserved, non-compiled, in `spike-metro/scope-violation-fixture/`.

---

## The Store-across-KMP finding (design finding, NOT a no-go)

A clean Store/ViewModel-across-KMP shape **exists** and compiles for both targets:
`FeatureGraph` exposes `ChartStore.Factory`; **androidMain** wraps it in a thin
`androidx.lifecycle.ViewModel` for retention (the `@HiltViewModel` role); **iosMain**
calls the factory directly, zero Android APIs.

**But** it requires the Store to be **platform-neutral**. Today `BaseStore` extends
`androidx.lifecycle.ViewModel` directly. The real migration must pick one of:
- **(a)** adopt the KMP `lifecycle-viewmodel` artifact so `ViewModel` lives in commonMain
  (BaseStore stays a ViewModel), or
- **(b)** invert to a platform-neutral Store + a thin androidMain ViewModel shim
  (demonstrated in the spike).

This is a "thin platform shim" restructure of one base class, not a per-feature cost.

---

## B.3 — build-time numbers (INFORMATIONAL, not a gate)

Wall-clock, warm daemon. **These are dominated by Gradle orchestration overhead
(~0.5s/invocation) and are a single tiny module — they DO NOT extrapolate to 33
modules / ~84K LOC. Real build-time verdict is deferred to a full-graph measurement.**

| Task (Metro 1.1.1) | Time |
|---|---|
| clean android compile (`compileAndroidMain`) | ~0.6–2.0 s (warm ~0.6 s) |
| clean iOS klib compile (`compileKotlinIosSimulatorArm64`) | ~0.65–0.9 s |
| clean commonMain metadata compile | ~1.8 s |
| incremental android (1 commonMain file changed) | ~0.6 s |

Loose reference (**NOT a fair 1:1** — much larger, Compose + Hilt-KSP, android-only):
`:feature:recovery` clean `compileDebugKotlin` ≈ **15 s**.

Directional note only: Metro is a single compiler-plugin pass (no separate KSP/kapt
round-trip that Hilt requires). At this scale that shows up as sub-second compiles, but
the real signal needs the full graph. External data (Vinted, "a few hundred modules")
reported −10% to −26% vs Dagger/Anvil — cite as context, not as this spike's result.

---

## Key findings that shape the Gate-2 cutover decision

1. **AGP 9.0 forbids `com.android.library` + `org.jetbrains.kotlin.multiplatform`.** A
   KMP module MUST use `com.android.kotlin.multiplatform.library` (different plugin +
   `kotlin { androidLibrary {} }` DSL). Every existing Android module converted to KMP
   swaps its android plugin — a mechanical per-module change beyond "add Metro."

2. **Metro↔Kotlin/Native ABI pinning is strict.** Metro 1.3.0 (latest) ships a native
   runtime klib built with Kotlin 2.4.0 (ABI 2.4.0), which the project's Kotlin 2.3.20
   K/N compiler **rejects**. On JVM/android this is invisible (bytecode compat). The
   spike pinned **Metro 1.1.1** (native runtime built with Kotlin 2.3.21 → ABI 2.3.0,
   consumable; `compiler-compat-k2320` shim present). Verified against the published
   klib manifests. Cutover choice: pin a Metro whose native runtime matches the project
   Kotlin, OR bump Kotlin to Metro's build version to ride the latest Metro.

3. **Metro 1.1.x graph-extension API** (learned empirically): parent/child scoping is
   `@GraphExtension` / `@GraphExtension.Factory` — NOT `@DependencyGraph(isExtendable)` /
   `@Extends` (those don't exist). Expose the `@AssistedFactory`, never the assisted type
   directly (`[Metro/InvalidBinding]`); don't `@SingleIn` an assisted type.

4. **iOS klib compilation ran on this Linux host** (K/N downloaded the linux-x86_64-2.3.20
   prebuilt). The B.1/B.2 gates are *compile*, which was fully evaluatable here. Final
   framework *linking* (and running iOS tests) would still require macOS — worth a
   one-time macOS-CI confirmation before the real migration, but not needed for go/no-go.

---

## Gate 2 — decision inputs (Ilya-only)

- **GO** → the DI cutover-mechanism decision (atomic branch vs transient scaffolding) can
  now be made knowing: Hilt→Metro is annotation-shaped and mechanical per module; the two
  real seams are the AGP-KMP-plugin swap (per module) and the BaseStore ViewModel
  restructure (once). Metro's compile-time scope enforcement is confirmed — the property
  that made Koin unacceptable.
- **NO-GO** → Android stays on Hilt; the Phase A seams keep their standalone value.

## Disposal

Spike lives only on local `spike/metro-di` (3 commits atop `1183cb16`). NOT merged, NOT
pushed. On NO-GO: delete the module dir, the `KmpLibraryConventionPlugin`, the
`kmpLibrary`/`androidKmpLibrary`/`metro` catalog entries, and the settings `include`.
Even on GO, this spike is expected to be discarded and redone cleanly for the real
migration (per the Phase B charter).

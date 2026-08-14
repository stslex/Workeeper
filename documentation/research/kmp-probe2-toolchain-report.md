# Probe-2 — Toolchain audit + KMP compile probes (consolidated report)

**TERMINAL STOP.** Await human decision. Do NOT merge any spike. The cutover-mechanism
choice is Ilya's call on the P2.b data — not decided here.

**Where:** disposable branch `probe2/kmp-toolchain` (3 commits atop `origin/dev`), NOT wired
into `:app`, NOT pushed. `spike/metro-di` (frozen B.2 proof) untouched at `00654952`.
**How:** compile-only (no runtime/device), bisect-green + full Detekt (zero suppressions,
custom MVI rules) per commit. Final full-repo `assembleDebug + detekt` + all 5 probe iOS
compiles: **green**. Host = Linux (K/N *klib* compile runs here; framework *linking* would
need macOS — out of scope for compile probes).

---

## Phase 1 — Audit (primary sources, dated 2026-07-05)

K/N (`iosSimulatorArm64`) Kotlin support, one read-only subagent per component:

| Component | Latest | Works on Kotlin **2.3.20** / K/N? | Pin @ 2.3.20 | Evidence |
|---|---|---|---|---|
| Metro | 1.3.0 (=Kotlin 2.4.0, klib ABI 2.4.0) | ✅ via **1.1.1** (klib ABI 2.3.0) | **1.1.1** | published klib manifests; ABI jumps at 1.2.0 |
| KSP2 | 2.3.9 | ✅ via **2.3.7–2.3.9** (repo's 2.3.6 → base 2.3.0) | **2.3.9** | per-tag `gradle.properties` |
| Compose Multiplatform | 1.11.1 | ⚠️ should-work (native floor 2.3.10; **no 2.4.0 support documented**) | 1.11.1 | 1.11.0 release notes |
| Room-KMP | 2.8.4 / **3.0.0** (androidx.room3, 2026-07-01) | ⚠️→✅ empirically (P2.c) | 2.8.4 or 3.0.0 + KSP 2.3.9 | no stated Kotlin floor |
| kotlinx-serialization | 1.11.0 | ✅ (floor 2.2.0; plugin ships w/ Kotlin) | 1.9.0 | CHANGELOG |
| kotlinx-datetime | 0.8.0 | ✅ (floor 2.1.20) | 0.7.1 | README |

**AGP** 9.1.0 ≥ 9.0 → **YES**; KMP module-plugin swap `com.android.library` →
`com.android.kotlin.multiplatform.library` **required** (AGP rejects the old combo — proven).

### (A)/(B) + GATE A
- **(A) MIGRATION-CONSISTENT KOTLIN = `2.3.20`.** Every component has a K/N release at 2.3.20.
  **Ceiling = Compose Multiplatform** — no CMP release documents Kotlin **2.4.0**, so 2.4.0 is
  not a common solution; the migration is capped at the 2.3.x line, which equals the repo's
  current Kotlin. (Metro's ABI-2.3.0 cap at 1.1.1 independently agrees.)
- **(B) LATEST-ANDROID-SAFE KOTLIN ≥ `2.3.20`** (repo proven green here; 2.4.0-on-Android not
  audited). **Divergence:** adopting KMP pins the whole repo to 2.3.x via CMP, forgoing any
  2.4.0 an Android-only build might reach.
- **GATE A = CONTINUE** (A == 2.3.20 via Metro 1.1.1; no Kotlin bump → no cascade into the
  custom Detekt rules / embeddable-Kotlin).

---

## Phase 2 — Probes

### P2.c — Room-KMP on iosSimulatorArm64 @ Kotlin 2.3.20 — **PASS (GATE C)**
Room **2.8.x** AND Room **3.0** (`androidx.room3`) + BundledSQLiteDriver (androidx.sqlite
2.7.0) both compile and run Room's KSP codegen on K/N — proven genuine (generated `actual
object … RoomDatabaseConstructor` referencing `ProbeDatabase_Impl`, green with **zero**
`@Suppress`). See `probe-room28/P2C_FINDINGS.md`.

**⚠️ Direction-critical gotcha:** the repo's **KSP 2.3.6 silently SKIPS native codegen**
(`kspKotlinIosSimulatorArm64` = SKIPPED); the standard Room-KMP
`@Suppress("NO_ACTUAL_FOR_EXPECT")` then **masks the missing actual as a FALSE GREEN**.
Captured (suppress removed, KSP 2.3.6):
```
e: Expected ProbeDatabaseConstructor has no actual declaration in module <commonMain> for Native
```
**Fix: KSP ≥ 2.3.9.** The 2.3.6→2.3.9 bump keeps the existing Android build green and doesn't
bump Kotlin. Room 2.x is maintenance-mode; Room 3.0 is the KMP-modern forward path — both work.

### P2.b — Hilt ↔ Metro coexistence — **coexist-green → transient scaffolding VIABLE**
`:probe-hilt` (Hilt) + `:probe-metro` (Metro, Hilt-free) + `:probe-di-root` (applies BOTH,
wires a Hilt-provided + a Metro-provided dep). Both processors generated code in the same
module compilation, no collision: Hilt-KSP → `RootWiring_Factory.java`; Metro → `RootMetroGraph$Impl.class`.
Disjoint annotations (`javax.inject.@Inject` vs `dev.zacsweers.metro.@Inject`; Metro javax
interop is opt-in). **⇒ a module-by-module Hilt→Metro migration is possible; atomic-branch
big-bang is NOT forced.** (Mechanism choice = Ilya's call.)

### P2.a — Multi-module aggregation + cross-module scope enforcement — **PASS, both targets**
3 KMP modules: app-scoped contribution (X) + feature-scoped contribution (Y) + aggregating
graphs (root). Verified with the compiler (not assumed): `@ContributesBinding(scope)`,
`@DependencyGraph(scope)` auto-merge, `@GraphExtension(scope)` child.
- Cross-module aggregation compiles on **android AND iosSimulatorArm64** at Kotlin 2.3.20 /
  Metro 1.1.1 — the native+aggregation combo the audit flagged as historically broken (needs
  Kotlin 2.3.20 FIR hint-generation). It works.
- Genuine (negative proof): removing `@ContributesBinding` → `[Metro/MissingBinding]` for the
  app service, at both the accessor and inside the feature graph.
- **Cross-module scope enforcement holds AT COMPILE on both targets** — an AppScope graph in
  root referencing `FeatureServiceImpl` (`@SingleIn(FeatureScope)` in module Y) fails with
  `[Metro/IncompatiblyScopedBindings]` naming the other module's class. Not weakened across the
  boundary. Fixture: `probe-agg-root/scope-violation-fixture/`.

---

## Toolchain deltas the real migration needs (at Kotlin 2.3.20)
1. **Metro `1.1.1`** (K/N ABI-2.3.0-matched; 1.2.0+ needs Kotlin 2.4.0).
2. **KSP `≥ 2.3.9`** (native codegen; 2.3.6 silently skips native).
3. **AGP KMP plugin swap** per converted module (`com.android.kotlin.multiplatform.library`).
4. Kotlin **stays 2.3.20** — CMP caps the KMP world at 2.3.x (no 2.4.0 yet).

## Open items / gaps (not papered over)
- CMP native ↔ 2.3.20 is "should-work" (floor 2.3.10), not a tested pin; CMP was not compiled
  in Probe-2 (out of scope). It is the component most likely to gate a future Kotlin bump.
- (B) 2.4.0-on-Android was not audited (would need Hilt-2.59.2 / detekt-embeddable-Kotlin check).
- iOS framework *linking* / on-device run not tested (Linux host; compile-only by design).

## Decision inputs for Ilya (Gate)
- **GO** signal is strong: Metro compiles + enforces scope on both targets (Phase B), Room-KMP
  compiles on iOS (P2.c), Hilt↔Metro coexist (P2.b), multi-module aggregation + cross-module
  scope enforcement hold on both targets (P2.a).
- **Cutover mechanism** (transient scaffolding vs atomic branch) — P2.b shows scaffolding is
  *viable*; the choice is yours.
- Disposal: delete the 6 probe modules + `KmpLibraryConventionPlugin` + the additive catalog/
  settings entries; revert the KSP 2.3.6→2.3.9 bump if not adopting. One-shot.

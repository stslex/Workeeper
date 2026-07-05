# Workeeper → Kotlin Multiplatform / Compose Multiplatform — Migration Assessment

**Migration feasibility study — read-only investigation.**

Cost, effort, and risk assessment for taking Workeeper from Android-only to Android + iOS via Kotlin Multiplatform (KMP) / Compose Multiplatform (CMP). Prepared for a solo, part-time senior Android engineer with no stated iOS-native experience.

- **Repo:** stslex/Workeeper, branch `dev`
- **Date:** 2026-07-04
- **Method:** 5 parallel code-search agents + direct shell verification for the Phase 0 inventory; targeted re-verification file reads, precise repo-wide grep counts, and 6 independent live web-research passes (each cross-checking claims against primary sources with dates) for Phases 1–5. Every finding below is grounded in a file that was actually opened, a command that was actually run, or a source that was actually fetched — cited inline as `path:line`. Anything that could not be verified live is marked **UNVERIFIED** rather than asserted from memory.
- **Status:** Complete — all 5 phases.

This document is self-contained: it does not assume any other conversation, chat, or artifact is available to the reader.

## How to read this document

Workeeper is a modular Android app — Kotlin, Jetpack Compose, MVI, Hilt, Room, Ktor, Google Drive backup — bigger and more architecturally disciplined than a typical solo project: 33 Gradle modules, ~84K lines of Kotlin, custom Detekt rules enforcing MVI and domain-layer purity. The five phases build on each other:

- **Phase 0 — Inventory.** The factual foundation: module graph, dependency catalog, LOC, and every Android-framework touchpoint found by a repo-wide sweep.
- **Phase 1 — Layer portability.** Classifies domain / data / UI / DI into COMMON-READY / COMMON-WITH-CHANGES / EXPECT-ACTUAL NEEDED / ANDROID-ONLY, with the untangling costed as explicit line items.
- **Phase 2 — Dependency migration matrix.** Every dependency's verified current KMP/iOS status, target replacement, effort, and risk.
- **Phase 3 — Platform-specific work & prerequisites.** Hard prerequisites, iOS host app, Google auth on iOS, Firebase on iOS, CI/CD, testing strategy.
- **Phase 4 — Effort / cost / risk rollup.** Workstream-by-workstream days (low/expected/high) for two milestones — T1 (compiles and runs on iOS) and T2 (App Store submission-ready) — plus money, ongoing cost, and a risk register.
- **Phase 5 — Recommendation.** Big-bang vs. incremental, sequencing, what to defer, a go/no-go framing, and the smallest de-risking spike.

---

## Table of contents

**Phase 0 — Inventory**
[Overview](#overview) · [Module graph](#module-graph) · [Build configuration](#build-configuration--convention-plugins) · [Lines of code](#lines-of-code) · [Dependency catalog](#dependency-catalog) · [Android-coupling touchpoints](#android-coupling-touchpoints) · [Key findings → Phase 1](#key-findings-carried-into-phase-1)

**Phase 1 — Layer portability**
[Domain layer](#domain-layer) · [Data layer](#data-layer) · [UI layer](#ui-layer-compose--cmp) · [DI (Hilt footprint)](#dependency-injection-hilt)

**Phase 2 — Dependency migration matrix**
[Migration matrix](#migration-matrix) · [DI alternatives compared](#di-alternatives-compared-and-ranked) · [Detekt rule survival](#custom-detekt-rule-survival)

**Phase 3 — Platform prerequisites**
[Hard prerequisites](#hard-prerequisites) · [iOS host app](#ios-host-app) · [Google auth on iOS](#google-auth-on-ios) · [Firebase on iOS](#firebase-on-ios) · [CI/CD for iOS](#cicd-for-ios) · [Testing strategy](#testing-strategy) · [feature/recovery: defer vs. rebuild](#featurerecovery-defer-vs-rebuild-costed-both-ways)

**Phase 4 — Effort / cost / risk**
[Workstream rollup (T1/T2)](#workstream-rollup--ideal-engineering-days) · [Critical path](#critical-path--inter-workstream-dependencies) · [Money](#money) · [Ongoing cost](#ongoing-cost-post-launch) · [Risk register](#risk-register--ranked-by-probability--impact)

**Phase 5 — Recommendation**
[Big-bang vs. incremental](#big-bang-vs-incremental--incremental-domain-first) · [Sequencing](#sequencing) · [What to defer](#what-to-defer-or-keep-android-only) · [Go/no-go](#go--no-go-framing) · [De-risking spike](#smallest-de-risking-spike)

---

# Phase 0 — Inventory

## Overview

This inventory is the factual foundation the rest of the estimate builds on — every number below traces to a file that was actually opened or a command that was actually run.

| | |
|---|---|
| Gradle modules | **33** |
| Kotlin files | **946** |
| Total LOC | **84,416** |
| Pure Kotlin/JVM modules | **1** (`lint-rules`) |
| `domain/` files importing `android.*` | **8** |
| Independent raw DataStore construction sites | **4** |

**Build/tooling versions:**

| Layer | Version | Source |
|---|---|---|
| Kotlin | `2.3.20` | `gradle/libs.versions.toml:3` |
| Android Gradle Plugin | `9.1.0` | `gradle/libs.versions.toml:5` |
| Gradle wrapper | `9.5.0` | `gradle/wrapper/gradle-wrapper.properties:4` |
| KSP | `2.3.6` | `gradle/libs.versions.toml:4` |
| compileSdk / targetSdk / minSdk | `36 / 37 / 28` | `gradle/libs.versions.toml:8-10` |
| Compose BOM | `2026.03.00` (⚠ alpha channel) | `gradle/libs.versions.toml:21` |
| Hilt | `2.59.2` | `gradle/libs.versions.toml:31` |
| Room | `2.8.4` | `gradle/libs.versions.toml:47` |
| Ktor | `3.4.3` | `gradle/libs.versions.toml:59` |
| App version | `1.47.0` (code 48) | `gradle/libs.versions.toml:11-12` |

Compose BOM `2026.03.00` resolves against group `compose-bom-alpha` (`libs.versions.toml:91`) — the app is deliberately tracking a pre-release Compose channel, not a stable BOM. Relevant to CMP-parity risk (see Phase 2).

## Module graph

All 33 modules declared in `settings.gradle.kts` were opened and classified. **32 of 33 are Android-library or Android-application Gradle modules; exactly one — `lint-rules` — is pure Kotlin/JVM** (applies only `kotlin("jvm")` + `java-library`, confirmed zero Android/Compose/Room/Hilt dependency of any kind).

**How module type is decided here.** No leaf module applies a raw AGP/Kotlin/Hilt/KSP plugin directly. Every module applies exactly one of two convention-plugin aliases — `convention.androidLibrary` or `convention.composeLibrary` — defined in `build-logic/convention/src/main/kotlin/`. Those two plugins are what actually apply `com.android.library`, KSP, and **Hilt, unconditionally, to every single Android module in the repo** regardless of whether that module declares Hilt itself (see [Build configuration](#build-configuration--convention-plugins)). This matters directly for Phase 2: a DI swap away from Hilt touches the shared convention plugin, not 33 individual build files — the blast radius is centralized, not distributed.

### Module classification

**`app/`**

| Module | Type | Compose | Room | Notable direct deps |
|---|---|---|---|---|
| `app:app` | android-library¹ | ✓ | – | WorkManager+Hilt-work (api), Firebase (analytics/crashlytics/perf) |
| `app:dev` | android-application | ✓ | – | Firebase bom (own client block, same project) |
| `app:store` | android-application | ✓ | – | Firebase bom only, else just `project(":app:app")` |

**`core/`**

| Module | Type | Compose | Room | Notable direct deps |
|---|---|---|---|---|
| `core:core` | android-library | – | – | Kermit logging, kotlinx-datetime, Firebase (Crashlytics/Analytics/Perf holders) |
| `core:ui:kit` | android-library | ✓ | – | Haze (blur, Android/Compose-only), Google-Fonts provider (Android-only) |
| `core:ui:navigation` | android-library | ✓ | – | Compose Navigation (api) |
| `core:ui:mvi` | android-library | ✓ | – | Firebase Perf (screen-render recorder) |
| `core:ui:test-utils` | android-library | ✓ | – | Hilt-test, paging-testing, compose-test (all api) |
| `core:ui:plan-editor` | android-library | ✓ | – | – |
| `core:data:database` | android-library | – | **✓ Room plugin** | testFixtures enabled, kotlinx-serialization |
| `core:data:database-test` | android-library | – | Room runtime (no plugin) | Test fixture module for in-memory Room DB |
| `core:data:exercise` | android-library | –² | Room-ktx (no plugin) | Paging runtime |
| `core:data:dataStore` | android-library | – | – | DataStore preferences+core |
| `core:data:backup:api` | android-library | – | – | Pure contracts module — no third-party deps |
| `core:data:backup:google-drive` | android-library | – | – | Ktor (android engine), **Play Services Auth**, coroutines-play-services, DataStore |
| `core:data:backup:scheduling` | android-library | – | – | DataStore (raw, own construction — see touchpoints) |
| `core:data:backup:worker` | android-library | – | – | **WorkManager**, Hilt-work |

**`feature/`** (15 modules — all android-library, all Compose except `app-dialogs:api`)

| Module | Type | Compose | Room | Notable direct deps |
|---|---|---|---|---|
| `feature:exercise` | android-library | ✓ | – | kotlinx-serialization; camera/gallery permission flow |
| `feature:exercise-chart` | android-library | ✓ | – | None — charts are 100% hand-drawn Compose Canvas; **uses `java.time.*`** (see findings) |
| `feature:all-exercises` | android-library | ✓ | – | – |
| `feature:all-trainings` | android-library | ✓ | – | – |
| `feature:single-training` | android-library | ✓ | – | – |
| `feature:settings` | android-library | ✓ | – | No direct GMS/WorkManager — reaches them only via `core:data:backup:api` |
| `feature:archive` | android-library | ✓ | – | – |
| `feature:home` | android-library | ✓ | – | – |
| `feature:live-workout` | android-library | ✓ | – | Largest feature module by LOC |
| `feature:past-session` | android-library | ✓ | – | – |
| `feature:image-viewer` | android-library | ✓ | – | Coil3 (hidden dep via shared compose bundle) — **no `domain/`, no Uri/File code — cleanest feature module** |
| `feature:plan-editor` | android-library | ✓ | – | kotlinx-serialization; zero test source sets |
| `feature:app-dialogs:api` | android-library | – | – | **Pure models + Observer/Publisher interfaces, zero DI annotations actually used** — best CMP-`commonMain` candidate in the repo |
| `feature:app-dialogs:impl` | android-library | ✓ | – | DataStore (raw, own construction), Hilt-navigation-compose |
| `feature:recovery` | android-library | ✓ | Room (androidTest-only, no KSP) | **No `di/`, `ui/`, or `mvi/` package — a raw Activity, not a screen feature** (see structural anomalies) |

**Tooling**

| Module | Type | Compose | Room | Notable direct deps |
|---|---|---|---|---|
| `lint-rules` | **pure-kotlin-jvm** | – | – | Detekt API (compileOnly), kotlin-compiler-embeddable (compileOnly) |

Notes:
1. `app:app` applies `convention.composeLibrary` → it is a Gradle *library* even though it hosts the Compose root, MainActivity, and all navigation wiring; `app:dev`/`app:store` are the two real `com.android.application` modules and each just does `implementation(project(":app:app"))`.
2. `core:data:exercise` doesn't apply the Compose compiler but directly declares the Compose BOM + `compose-runtime` anyway.

### Module dependency edges

Project-to-project `implementation` dependencies only (no module in the repo uses `api(...)` for a project dependency — module-graph boundaries are explicit throughout).

| Module | Depends on |
|---|---|
| `app:app` | `core:core`, `core:ui:{kit,navigation,mvi}`, `core:data:{database,exercise,dataStore}`, `core:data:backup:{api,google-drive,scheduling,worker}`, all 15 `feature:*` modules |
| `app:dev` / `app:store` | `app:app` only |
| `core:ui:kit` | `core:core` |
| `core:ui:navigation` | `core:core`, `core:ui:plan-editor` |
| `core:ui:mvi` | `core:core`, `core:ui:{navigation,kit}` |
| `core:ui:plan-editor` | `core:core`, `core:ui:kit`, `core:data:exercise` |
| `core:data:database` | `core:core`, `core:data:backup:api` |
| `core:data:exercise` | `core:core`, `core:data:database` |
| `core:data:backup:google-drive` | `core:core`, `core:data:backup:api`, `core:data:database`, `core:data:dataStore` (dataStore dep is dead — see touchpoints) |
| `core:data:backup:scheduling` | `core:core`, `core:data:backup:api` |
| `core:data:backup:worker` | `core:core`, `core:data:backup:{api,scheduling}`, `core:data:database` |
| `feature:*` (12 of 15) | `core:core`, `core:ui:{kit,mvi,navigation}`, `core:data:exercise`, (+`core:ui:plan-editor` / `core:data:database` for editor-owning features) |
| `feature:settings` | …as above, + `core:data:dataStore`, `core:data:backup:api`, `feature:app-dialogs:api` |
| `feature:recovery` | `core:core`, `core:ui:kit`, `core:ui:navigation`, `core:data:database`, `core:data:backup:api`, `feature:app-dialogs:api` — **no `core:ui:mvi`, no `core:data:exercise`** |
| `feature:app-dialogs:impl` | `core:core`, `core:ui:{kit,mvi}`, `core:data:backup:api`, `feature:app-dialogs:api` |

Feature-to-feature coupling is minimal: only `settings`, `recovery`, and `app-dialogs:impl` depend on `feature:app-dialogs:api`; there are no other cross-feature edges. That's a favorable shape for an incremental migration — features can convert largely independently.

### Structural anomalies worth flagging

- **`feature/recovery`** (high) has no `di/`, `ui/`, or `mvi/` package and no Store/MVI contract at all. It exposes a raw `RecoveryActivity.kt` (an Android `Activity`, declared directly in `app/app/src/main/AndroidManifest.xml:40`) plus Hilt boot-time entry points (`boot/RecoveryBootstrap*.kt`) and diagnostics exporters. It's an app-startup/crash-recovery orchestrator wired to the Android `Activity`/`Application` lifecycle, not a screen feature — the single highest-structural-risk module for CMP since iOS has no `Activity` concept.
- **`feature/app-dialogs/api`** (low) is confirmed as thin as expected: 5 files, pure models + Observer/Publisher interfaces, zero Compose, zero DI annotations actually exercised (Hilt/KSP apply only because the convention plugin applies them universally). Best `commonMain` candidate in the repo.
- **`feature/plan-editor`** (medium) has zero test source sets (no `src/test`, no `src/androidTest`) despite being a full domain+ui+mvi screen. Test infrastructure needs building from scratch regardless of KMP.
- Three modules (`feature/home`, `feature/past-session`, `feature/image-viewer`) declare `androidTestImplementation` deps with no corresponding `src/androidTest` directory — dead declarations, harmless but worth pruning.
- **`feature/exercise-chart`** (medium) is the only module using `java.time.*` directly (`ChartCanvas.kt`, `PointPixelMap.kt`, 8 files total). JSR-310 has no Kotlin/Native implementation — likely the single largest per-module portability blocker of any feature module, needing a genuine port to `kotlinx-datetime` before it compiles for iOS. No other feature module imports `java.time`.

## Build configuration — convention plugins

All Gradle configuration is centralized in `build-logic/convention/src/main/kotlin/`. This is what actually determines every module's shape.

| File | What it does |
|---|---|
| `AndroidLibraryConventionPlugin.kt` | Applies `com.android.library` + KSP + the lint convention; runs `configureKotlinAndroid` only. No Compose, no serialization. This is the plain `convention.androidLibrary` alias (only `feature:app-dialogs:api` uses it). |
| `AndroidLibraryComposeConventionPlugin.kt` | Everything above, plus the Kotlin Compose-compiler plugin, `kotlinx-serialization`, and `configureAndroidCompose`. This is `convention.composeLibrary` — used by all other 31 Android-library modules. |
| `io/.../KotlinAndroid.kt` | Shared base for every Android convention: sets compileSdk/minSdk, derives namespace from module path, **unconditionally applies Robolectric-JUnit5 and Hilt Android** (`com.google.dagger.hilt.android`), enables core-library desugaring, JVM target 21, and unconditionally adds `hilt-android` + `ksp(hilt-compiler)` + coroutines/immutable-collections/core-ktx + JUnit5/MockK bundles. **Hilt and KSP are wired into every Android module regardless of that module's own build file.** |
| `io/.../ComposeAndroid.kt` | `configureAndroidCompose`: turns on `buildFeatures.compose`, Compose BOM, debug-only tooling, accompanist/compose/lifecycle bundles. Contains a **dead/commented-out VKompose extension block** "pending Kotlin 2.2.21 support." |
| `io/.../ConfigureApplication.kt` | Applies `com.android.application`, Compose compiler, VKompose, serialization, Google Services, Firebase Crashlytics + Perf, KSP, lint. Sets targetSdk/versionName/versionCode + `AppType` postfix, wires release signing/Proguard/Crashlytics-mapping upload. Sets KSP arg `KOIN_CONFIG_CHECK=true` — notable: the rest of DI here is Hilt, so this looks like a leftover from a prior Koin evaluation. |
| `RoomLibraryConventionPlugin.kt` | Applies the AndroidX Room Gradle plugin + KSP, points `schemaDirectory` at `$projectDir/schemas`. Applied by exactly one module: `core:data:database`. |
| `LintConventionPlugin.kt` | Applies Detekt + centralized `lint-rules/lint.xml`/baseline to every module (via the other convention plugins, never aliased directly by a leaf module). |

**Why this matters for Phase 2:** Because Hilt is wired at the convention-plugin level rather than per-module, a DI replacement (Koin / kotlin-inject / Metro) is a small number of centralized edits to `KotlinAndroid.kt` + `ConfigureApplication.kt` for the Gradle wiring — the expensive part of a DI swap is rewriting the hundreds of `@Inject`/`@ViewModelScoped`/`@HiltViewModel` call sites across features, not the build config.

## Lines of code

Counted directly via `find … -name "*.kt" | xargs cat | wc -l` per module and per source set, excluding `build/`, `.gradle/`, `.idea/`.

| Module | main | test | androidTest |
|---|---:|---:|---:|
| **app/** | | | |
| `app:app` | 1,115 | 382 | 55 |
| `app:dev` | 10 | 0 | 338 |
| `app:store` | 11 | 0 | 0 |
| **core/** | | | |
| `core:core` | 1,323 | 322 | 0 |
| `core:ui:kit` | 4,829 | 0 | 122 |
| `core:ui:navigation` | 241 | 0 | 0 |
| `core:ui:mvi` | 1,297 | 136 | 185 |
| `core:ui:test-utils` | 492 | 0 | 0 |
| `core:ui:plan-editor` | 714 | 295 | 0 |
| `core:data:database` | 3,112 | 4,869 | 619 |
| `core:data:database-test` | 132 | 0 | 0 |
| `core:data:exercise` | 3,186 | 4,974 | 0 |
| `core:data:dataStore` | 165 | 0 | 0 |
| `core:data:backup:api` | 729 | 0 | 0 |
| `core:data:backup:google-drive` | 1,883 | 2,331 | 0 |
| `core:data:backup:scheduling` | 252 | 255 | 0 |
| `core:data:backup:worker` | 369 | 496 | 0 |
| **feature/** | | | |
| `feature:exercise` | 4,260 | 1,069 | 238 |
| `feature:exercise-chart` | 2,870 | 1,257 | 234 |
| `feature:all-exercises` | 1,512 | 533 | 29 |
| `feature:all-trainings` | 1,296 | 246 | 30 |
| `feature:single-training` | 3,229 | 393 | 30 |
| `feature:settings` | 3,289 | 2,051 | 63 |
| `feature:archive` | 1,093 | 172 | 28 |
| `feature:home` | 1,690 | 473 | 0 |
| `feature:live-workout` | 5,269 | 4,954 | 196 |
| `feature:past-session` | 1,886 | 1,108 | 0 |
| `feature:image-viewer` | 580 | 154 | 0 |
| `feature:plan-editor` | 1,593 | 689 | 0 |
| `feature:app-dialogs:api` | 212 | 0 | 0 |
| `feature:app-dialogs:impl` | 1,117 | 675 | 204 |
| `feature:recovery` | 1,406 | 549 | 206 |
| **tooling** | | | |
| `lint-rules` | 995 | 460 | 0 |
| **Sum of buckets above** | **52,157** | **28,843** | **2,577** |

Buckets above sum to ~83.6K; the repo-wide count (`find . -name "*.kt"`, 84,416 across 946 files) is ~0.8K higher — the delta is `testFixtures` sources (enabled on `core:data:database`) and other non-standard source sets not captured by the three buckets counted per module.

### Layer breakdown — feature modules

LOC under each subpackage of `src/main`, where present. This is the empirical basis for the domain-purity discussion in Phase 1: several features carry substantial `domain/` weight (exercise 631, live-workout 775), others have none at all.

| Module | domain | ui | mvi | di | mapper | usecase |
|---|---:|---:|---:|---:|---:|---:|
| `feature:exercise` | 631 | 3,564 | 1,718 | 65 | 274 | 124 |
| `feature:exercise-chart` | 360 | 1,725 | 716 | 69 | 198 | – |
| `feature:all-exercises` | 262 | 602 | 584 | 64 | 162 | – |
| `feature:all-trainings` | 136 | 597 | 499 | 64 | 114 | – |
| `feature:single-training` | 483 | 1,410 | 1,268 | 68 | 169 | – |
| `feature:settings` | 420 | 1,568 | 1,222 | 70 | 214 | – |
| `feature:archive` | 181 | 473 | 375 | 64 | 44 | – |
| `feature:home` | 201 | 811 | 614 | 64 | 142 | – |
| `feature:live-workout` | 775 | 1,750 | 2,676 | 68 | 1,018 | – |
| `feature:past-session` | 230 | 851 | 736 | 69 | 197 | – |
| `feature:image-viewer` | – | 289 | 228 | 63 | – | – |
| `feature:plan-editor` | 189 | 1,336 | 749 | 68 | 96 | – |
| `feature:app-dialogs:impl` | 68 | 421 | 245 | 100 | – | – |
| `feature:recovery` | 456 | – | – | – | – | – |

## Dependency catalog

Every `libs.*` alias actually referenced across all 35 `build.gradle.kts` files was cross-checked against the declared catalog (`gradle/libs.versions.toml`) by direct grep — this is the full third-party surface, with dead entries flagged rather than silently included.

**UI / Compose**

| Dependency | Version | Notes |
|---|---|---|
| Compose BOM (`compose-bom-alpha`) | 2026.03.00 | ⚠ pre-release channel |
| JetBrains Compose Gradle plugin artifact (`compose-gradle-plugin`) | 1.10.3 | ⚠ unverified — present on build-logic classpath; exact usage site not confirmed. Likely just for typed Kotlin access to Compose extension APIs while authoring convention plugins, not evidence of an existing CMP target |
| Compose Activity (`activity-compose`) | 1.13.0 | – |
| Compose Navigation (`navigation-compose`) | 2.9.8 | – |
| Accompanist (`placeholder`, `systemuicontroller`) | 0.36.0 | ⚠ both modules are in Google's maintenance-only bucket, independent of KMP |
| Coil (`coil3` compose + network-ktor3) | 3.4.0 | ✓ already Kotlin-Multiplatform-ready |
| Haze (`dev.chrisbanes.haze`) | 1.7.1 | Blur/glassmorphism — Android/Compose-only today; verify CMP support (see Phase 1) |
| kotlinx-collections-immutable | 0.4.0 | Portable |
| AndroidX Lifecycle (viewmodel-ktx, viewmodel-compose) | 2.10.0 | KMP status verified in Phase 1/2 |

**Dependency injection**

| Dependency | Version | Notes |
|---|---|---|
| Hilt (hilt-android, hilt-compiler) | 2.59.2 | Wired into every module via convention plugin — no KMP story; single largest DI item |
| Hilt navigation compose | 1.3.0 | 2 call sites in whole repo (see touchpoints) |
| Hilt-work | 1.3.0 | – |

**Persistence**

| Dependency | Version | Notes |
|---|---|---|
| Room (room-runtime/ktx/paging/compiler) | 2.8.4 | Confined to `core:data:database` |
| DataStore (datastore-preferences, datastore-core) | 1.2.0 | Nominally centralized in `core:data:dataStore` — 4 independent raw construction sites elsewhere (see touchpoints) |
| WorkManager (work-runtime-ktx) | 2.10.0 | Cleanly seamed behind `core/data/backup/api` — no iOS equivalent exists |

**Network / serialization**

| Dependency | Version | Notes |
|---|---|---|
| Ktor (client-core/android/logging/content-negotiation/serialization-json/mock) | 3.4.3 | Android engine only today; Darwin engine needed for iOS |
| kotlinx-serialization-json | 1.9.0 | Portable |
| kotlinx-datetime | 0.7.1 | Portable — already used almost everywhere except `feature:exercise-chart`'s `java.time` usage |
| slf4j | 2.0.17 | ⚠ unverified — version declared in catalog, no `[libraries]` entry references it; likely a transitive resolution pin, not a direct dependency |

**Google / Firebase**

| Dependency | Version | Notes |
|---|---|---|
| Play Services Auth (play-services-auth) | 21.5.1 | Used via GMS **Identity `AuthorizationClient`**, not legacy GoogleSignIn (see Drive auth notes below) |
| Firebase BOM | 34.12.0 | One Firebase project (`workeeper-fb593`) shared by dev+store via applicationId-keyed client blocks, not two projects |
| Firebase Crashlytics (gradle plugin) | 3.0.7 | – |
| Firebase Performance (plugin) | 2.0.2 | Custom TTID/frame-metrics code uses **non-public** `com.google.firebase.perf.*` internals (`FrameMetricsRecorder`, `Constants`, `ScreenTraceUtil`) — confirmed, no iOS equivalent |

**Testing**

| Dependency | Version | Notes |
|---|---|---|
| JUnit5 (Jupiter) | 5.13.4 | – |
| MockK | 1.14.7 | – |
| Robolectric (+ JUnit5 ext) | 4.16 / 0.9.0 | Android-only test runner — needs an iOS-side replacement strategy |
| Espresso / AndroidX Test | 3.7.0 / 1.7.0 | Android-only instrumented tests |

**Declared but confirmed unused (dead catalog entries)**

| Dependency | Version | Notes |
|---|---|---|
| essenty (Arkadii Ivanov's KMP lifecycle/state lib, Decompose ecosystem) | 2.5.0 | Zero usage anywhere |
| parcelize (decompose companion) | 0.2.4 | Zero usage anywhere |
| androidx-junit-ktx (`junitKtx`) | 1.3.0 | Zero usage — only plain `androidx-junit` is used |
| composeCharts | 0.1.11 | Zero usage — exercise-chart hand-rolls Canvas charts instead |
| vkompose (`com.vk.vkompose`) | 0.7.2 | Present, not active — applied `apply false` at root; the one code reference is a commented-out block in `ComposeAndroid.kt` "pending Kotlin 2.2.21 support" |

The `essenty`/`parcelize` pairing is specifically the toolkit behind Arkadii Ivanov's **Decompose** — a KMP-first navigation/lifecycle library. Their presence in the catalog with zero usage anywhere in source suggested a prior evaluation of Decompose as a KMP navigation strategy that was not carried forward — **this was directly confirmed via git history**; see [Navigation](#navigation--a-compound-decision-with-a-data-point-the-catalog-only-hinted-at) under Phase 1.

## Android-coupling touchpoints

Every subsection below is a repo-wide sweep for one category of Android-framework coupling, cited file:line. Read the domain-layer finding first — it changes the framing of the whole migration.

### Headline finding — the domain layer is not actually pure

The repo has two custom Detekt rules policing `domain/` packages — `DomainLayerPurityRule` (blocks `core.data.*` model imports) and `DomainLayerNoUiRule` (blocks Compose/`R`/UI leaks). **Neither rule checks for raw `android.*` SDK imports** — that blind spot is why 8 production files under a `domain/` path segment import Android framework types directly, unopposed by tooling:

| File | Android imports | Stored as field? |
|---|---|---|
| `feature/recovery/.../domain/RestoreRecoveryCoordinator.kt` | Activity, Context, Intent, PackageInfo, PackageManager, Build | **Yes** — Context field L57; `context is Activity` check L184 |
| `feature/recovery/.../domain/StartupMigrationCoordinator.kt` | Context | **Yes** — field L102 |
| `feature/settings/.../domain/BackupInteractorImpl.kt` | Context, Intent, PackageInfo, PackageManager, Build | **Yes** — Context field L42 |
| `feature/settings/.../domain/SettingsInteractorImpl.kt` | Context, PackageInfo, PackageManager, Build | **Yes** — field L21 |
| `feature/settings/.../domain/BackupInteractor.kt` (interface) | Intent | param type, `completeSignIn(resultIntent: Intent?)` L32 |
| `feature/settings/.../domain/model/SignInOutcomeDomain.kt` | IntentSender | **Yes** — baked into the domain **model itself**: `NeedsResolution(val intentSender: IntentSender)` L11 |
| `feature/exercise/.../domain/ExerciseInteractor.kt` (interface) | Uri | param/return type only |
| `feature/exercise/.../domain/ExerciseInteractorImpl.kt` | Uri | param/return type only |

`RestoreRecoveryCoordinator.restartApp()` (L172–186) is a near line-for-line duplicate of the sanctioned `app/app/.../navigation/NavigatorExt.kt` restart logic — the documented rule ("only `NavigatorExt.kt` may touch Context for restart") is already violated by a second, independent implementation living in the domain layer.

**Consequence for the estimate:** 3 features (`recovery`, `settings`, `exercise`) have Android types woven into their domain *contracts*, not just impls — `expect`/`actual`-ing these for iOS is a re-authoring job, not a mechanical lift. Sized in Phase 1/4.

**Detection method — re-verified exhaustively, not just asserted.** The finding above came from Explore agents running a repo-wide grep for `import android.content.Context` and every bare `import android.*` (non-androidx) line, then cross-referencing hits against path segments containing `domain` — not manual eyeballing, but not scoped to `domain/` directories specifically either. To close that gap, a follow-up pass enumerated **every** `domain/` directory in the repo directly (`find . -type d -name domain` → **26 directories across 14 feature modules + `core/ui/plan-editor`**) and grepped each one, exhaustively, for `import android\.`, `import androidx\.`, and fully-qualified inline usage with no import line.

**Result: exactly 8 production files, confirmed by two independent methods — not a floor.** The only additions this second pass found were 4 *test* files with `android.*` imports (expected — Robolectric-backed domain tests may legitimately construct `PackageManager`/`Context` mocks) and 3 modules with `androidx.*` in `domain/`: `androidx.paging.PagingData` (archive, all-exercises, all-trainings) and `androidx.datastore.preferences.core.Preferences` (app-dialogs/impl) — neither is a leak in the same sense, since Paging and DataStore-Preferences both have confirmed KMP stories (see [Data layer](#data-layer)); they're COMMON-WITH-CHANGES, not ANDROID-ONLY.

### Context usage — 50 files

12 are stored-field violations of the project's own stated convention (2 also domain-layer, tabled above). 5 more are constructor params not retained but still API-level coupling. The rest are transient params, DI providers, or test-only.

| Module | File:Line | Note |
|---|---|---|
| `app/app` | `navigation/NavigatorExt.kt:5,46,118,127` | Sanctioned — the one documented exception holder |
| `core/core` | `di/CoreModule.kt:43` | @Provides param |
| `core/core` | `images/ImageStorageImpl.kt:30` | **Field**, @Singleton |
| `core/core` | `resources/AndroidResourceWrapper.kt:12` | **Field**, @Singleton |
| `core/data/dataStore` | `core/DataStoreProvider.kt:15` | param, not retained |
| `core/data/database` | `di/CoreDatabaseModule.kt:36` | @Provides param |
| `core/data/database` | `snapshot/DatabaseSnapshotProviderImpl.kt:25` | **Field**, @Singleton |
| `core/data/database-test` | `di/TestDatabaseModule.kt:49`; `InMemoryDatabaseProvider.kt:19` | @Provides param / plain function param |
| `core/data/backup/worker` | `BackupWorker.kt:47` | Expected — `@Assisted appContext: Context` on a CoroutineWorker, required by WorkManager API |
| `core/data/backup/worker` | `notification/BackupNotificationHelper.kt:30` | **Field**, @Singleton |
| `core/data/backup/worker` | `scheduler/BackupScheduler.kt:30` | param, not retained (used once for `WorkManager.getInstance`) |
| `core/data/backup/google-drive` | `di/AuthProvidersModule.kt:21` | @Provides param |
| `core/data/backup/google-drive` | `auth/AccountDataStoreImpl.kt:23` | **Field**, @Singleton |
| `core/data/backup/google-drive` | `SnapshotExportRunnerImpl.kt:41` | **Field**, @Singleton |
| `core/data/backup/scheduling` | `BackupPreferencesRepositoryImpl.kt:25`; `RestoreStateRepositoryImpl.kt:43` | **Field** ×2, @Singleton |
| `core/ui/kit` | `utils/resource/ResourceManagerImpl.kt:12` | **Field** (`fallbackContext`), @Singleton |
| `core/ui/test-utils` | `runner/WorkeeperTestRunner.kt:24` | test, `newApplication()` override |
| `feature/app-dialogs/impl` | `data/AppDialogRepository.kt:45` | secondary-ctor param, converted to a DataStore not retained itself; class is @Singleton |
| `feature/exercise` | `ui/mvi/handler/ClickHandler.kt:53-54` | **Field** (`@ApplicationContext`), @ViewModelScoped — "Handler" named literally in the stated no-Context convention |
| `feature/recovery` | `RestoreDialogChoiceObserver.kt:76`; `diagnostics/RecoveryDiagnosticsExporter.kt:45`; `diagnostics/StartupMigrationReporter.kt:31` | **Field** ×3, all @Singleton |
| `feature/recovery` (domain) | `domain/RestoreRecoveryCoordinator.kt:57`; `domain/StartupMigrationCoordinator.kt:102` | **Field + domain** — tabled above |
| `feature/settings` (domain) | `domain/BackupInteractorImpl.kt:42`; `domain/SettingsInteractorImpl.kt:21` | **Field + domain** — tabled above |
| `feature/settings` | `mvi/handler/BackupClickHandler.kt:49` | **Field**, @ViewModelScoped — "Handler" named literally |
| `feature/settings` | `mvi/mapper/BackupDateMapper.kt:14,23,40`; `BackupUiMapper.kt:25` | function params on `object` mappers — not stored, but Context-coupled API surface |

Plus ~15 test-only occurrences across the same modules (standard `Application`/`Context` test scaffolding), omitted here for brevity.

### Raw `android.*` imports (non-androidx)

172 import-line hits repo-wide. Heaviest: `feature/settings` (35), `feature/recovery` (27), `core/data/backup/google-drive` (18, mostly test-only `Application`). **Zero hits** in: `app/store`, `core/ui/plan-editor`, `core/ui/navigation` (one KDoc mention only), `feature/all-exercises`, `feature/all-trainings`, `feature/archive`, `feature/exercise-chart`, `feature/image-viewer`, `feature/past-session`, `feature/single-training`, `lint-rules`.

| Module | Representative files:lines | Imports |
|---|---|---|
| `app/app` | `BaseApplication.kt:4`; `MainActivity.kt:4-6`; `navigation/NavigatorExt.kt:4-6` | app.Application, Intent, graphics.Color, os.Bundle, app.Activity, content.Context/Intent |
| `core/core` | `images/ImageStorage(Impl).kt`; `resources/AndroidResourceWrapper.kt`; `di/CoreModule.kt`; `time/RelativeTimeFormat.kt` | net.Uri, content.Context, graphics.Bitmap/ImageDecoder, text.format.DateUtils |
| `core/data/dataStore` | `core/DataStoreProvider.kt:3` | content.Context |
| `core/data/database` | `snapshot/DatabaseSnapshotProviderImpl.kt:4-6`; `di/CoreDatabaseModule.kt:3` | content.Context, database.sqlite.SQLiteDatabase/SQLiteException |
| `core/data/exercise` | `exercise/ExerciseRepositoryImpl.kt:3` | database.sqlite.SQLiteConstraintException |
| `core/data/backup/worker` | `BackupWorker.kt:4-6`; `notification/BackupNotificationHelper.kt:4-8`; `scheduler/BackupScheduler.kt:4` | content.Context, content.pm.PackageManager, os.Build, app.NotificationChannel/Manager/PendingIntent, content.Intent |
| `core/data/backup/google-drive` | `di/AuthProvidersModule.kt:4`; `SnapshotExportRunnerImpl.kt:4-6`; `auth/AccountDataStoreImpl.kt:4`; `auth/DriveBackupAuth.kt:4` | content.Context, content.pm.PackageManager, os.Build, content.Intent |
| `core/data/backup/scheduling` | `BackupPreferencesRepositoryImpl.kt:4`; `RestoreStateRepositoryImpl.kt:4` | content.Context |
| `core/data/backup/api` | `model/SignInResult.kt:4`; `BackupAuth.kt:4` | content.IntentSender, content.Intent |
| `core/ui/kit` | `activityHolder/ActivityHolder*.kt:3`; `utils/CommonExt.kt:3`; `components/Noise.kt:3-4`; `utils/resource/ResourceManagerImpl.kt:3` | app.Activity, view.ViewTreeObserver, graphics.RuntimeShader, os.Build, content.Context |
| `core/ui/mvi` | `performance/FirebaseScreenRenderRecorder.kt:3`; `processor/StoreProcessor.kt:4` | app.Activity |
| `feature/app-dialogs/impl` | `data/AppDialogRepository.kt:4`; `ui/*Dialog.kt:4` ×4 | content.Context, content.res.Configuration (Preview dark-mode) |
| `feature/exercise` | `domain/ExerciseInteractor(Impl).kt:4`; `ui/ExerciseGraph.kt:4-6`; `ui/mvi/model/*.kt`; `ui/mvi/handler/ClickHandler.kt:4-6` | net.Uri, content.Intent, provider.Settings, Manifest, content.Context/pm.PackageManager |
| `feature/recovery` | `RecoveryActivity.kt:4-7`; `RestoreDialogChoiceObserver.kt:4-9`; `diagnostics/*.kt`; `domain/RestoreRecoveryCoordinator.kt:4-9`; `domain/StartupMigrationCoordinator.kt:4` | content.ActivityNotFoundException, content.Intent, net.Uri, os.Bundle, content.pm.PackageInfo/Manager, os.Build, app.Activity |
| `feature/settings` | `domain/*.kt` (4 files); `ui/SettingsGraph.kt:4`; `mvi/store/SettingsStore.kt:4-5`; `mvi/mapper/BackupDateMapper.kt`/`BackupUiMapper.kt`/`BackupPreferencesUiMapper.kt`; `mvi/handler/BackupClickHandler.kt:4` | content.Context/Intent/IntentSender/pm.PackageInfo/pm.PackageManager, os.Build, text.format.DateUtils/Formatter |

Supplementary: 44 low-severity `@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_*)` inline FQNs (Compose dark-mode preview boilerplate, dev-tooling only, mostly in `core/ui/kit`) plus 2 production FQN param types and a handful of test-only FQNs.

### Activity / lifecycle framework hooks

| File:Line | Pattern | Assessment |
|---|---|---|
| `app/app/.../MainActivity.kt:22` | `class MainActivity : ComponentActivity()`, `@AndroidEntryPoint` | Expected — the app launcher Activity |
| `feature/recovery/.../RecoveryActivity.kt:66` | `class RecoveryActivity : ComponentActivity()`, declared in app/app manifest:40 | A second, independent Activity living inside a feature module — no iOS Activity concept exists |
| `core/ui/test-utils/.../TestActivity.kt:16` | `class TestActivity : ComponentActivity()` | Test scaffolding |
| `core/ui/kit/.../activityHolder/ActivityHolderImpl.kt:8,11,13,16-21` | @Singleton retains `WeakReference<Activity>` as a field, mutated from MainActivity.onCreate/onDestroy | Singleton storing Activity — the convention violation, softened only by WeakReference |
| `core/ui/kit/.../utils/resource/ResourceManagerImpl.kt:13,17` | @Singleton resolves `activityHolder.activity ?: fallbackContext` | Singleton indirectly depending on live Activity |
| `core/ui/mvi/.../performance/FirebaseScreenRenderRecorder.kt:11,18-30` | object takes `Activity?` param → `FrameMetricsRecorder(activity)` | Not retained beyond the call — perf/telemetry util |
| `core/ui/mvi/.../processor/StoreProcessor.kt:87,98` | `LocalActivity.current` fed into screen-trace recording | Baked into the shared MVI plumbing used by every screen, though Compose-local not stored |
| `core/core/.../coroutine/scope/AppCoroutineScopeImpl.kt:3-4,24,29-31,92-98` | `private val lifecycleOwner: LifecycleOwner` stored as ctor field | Raw LifecycleOwner retained outside ordinary ViewModel usage |
| `core/ui/mvi/.../BaseStore.kt:5-6,92-108,115` | Every Store extends this; manually threads a raw LifecycleOwner + registers a LifecycleEventObserver | Single highest-leverage Lifecycle-coupling point — base class of every Store in the app |
| `core/ui/mvi/.../processor/EffectsProcessor.kt:6-8,18-20` | `repeatOnLifecycle(STARTED)` | Standard Compose lifecycle idiom, lower risk |
| `feature/recovery/.../domain/RestoreRecoveryCoordinator.kt:183-184` | `if (context is Activity) context.finishAffinity()` | Domain-layer Activity coupling — duplicate of sanctioned restart logic |

No `androidx.appcompat.app.AppCompatActivity` usage found anywhere.

### Runtime permissions

| File:Line | API |
|---|---|
| `feature/exercise/.../ui/mvi/handler/ClickHandler.kt:685-688` | `ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)` |
| `feature/exercise/.../ui/ExerciseGraph.kt:96-97, 107-108, 116-117, 155` | `rememberLauncherForActivityResult(TakePicture / PickVisualMedia / RequestPermission)` |
| `feature/settings/.../ui/SettingsGraph.kt:38-39` | `rememberLauncherForActivityResult(StartIntentSenderForResult)` — Drive consent resolution |

`AndroidManifest.xml` permissions: `app/app/src/main/AndroidManifest.xml:5` → `CAMERA`; `core/data/backup/worker/src/main/AndroidManifest.xml:4` → `POST_NOTIFICATIONS`. Classic (non-Compose) `ActivityCompat.requestPermissions`/`registerForActivityResult` — not found anywhere; all permission plumbing goes through the modern Compose wrapper.

### Play Services & Firebase

**Play Services** — sign-in flows through GMS **Identity's `AuthorizationClient`** (not legacy `GoogleSignIn`), concentrated entirely in `core/data/backup/google-drive`: `auth/DriveBackupAuth.kt:5-10`, `auth/DriveAuthScopes.kt:4`, `auth/DriveAuthTokenProvider.kt:4-6`, `auth/DriveTokenInvalidator.kt:4-5`, `di/AuthProvidersModule.kt:5-6,22`. Verified **zero** GMS imports anywhere else in the repo, including `feature/settings` — that module reaches Drive auth only through the `core:data:backup:api` contract (`IntentSender`-typed), a clean module boundary.

**Firebase** — Crashlytics + Analytics are wrapped in two `core/core` holders (`logger/FirebaseCrashlyticsHolder.kt`, `logger/FirebaseAnalyticsHolder.kt`), consumed app-wide only through those; no other module imports `com.google.firebase.*` directly. Both app variants (`app/dev`, `app/store`) share **one Firebase project** (`workeeper-fb593`) with per-applicationId client blocks inside the same `google-services.json` family — not two separate projects. CI re-provisions these files from base64 secrets before every build (multiple workflow files) — they're live, not placeholders.

**TTID / frame-metrics instrumentation — confirmed precisely.** Matches `documentation/performance.md`: `core/ui/mvi/.../performance/{RecordAction,PerformanceRecorder,PerformanceMetricsRecorder,FirebaseScreenRenderRecorder}.kt` implement a custom TTID/AppCreate/ActivityCreate trace pipeline that calls **non-public** `com.google.firebase.perf.*` internals (`FrameMetricsRecorder`, `Constants`, `ScreenTraceUtil` — `FirebaseScreenRenderRecorder.kt:4-7,30,31,71`). Hooked from `app/app/BaseApplication.kt:45`, `MainActivity.kt:62,74`, `navigation/NavigatorExt.kt:66,105`, and a `Modifier.reportScreenPlace<S>()` applied across all 12 screen graphs in `host/AppNavigationHost.kt`.

Explicitly verified absent (worth stating since the original brief hypothesized them): no `reportFullyDrawn` anywhere (this app's "TTID" is a custom Firebase Trace, not the platform API of the same name); no `Choreographer` usage; no reflection (`kotlin.reflect.KClass` is used only as a type token for trace naming, not runtime introspection). This has **no iOS equivalent** — flagged for the Phase 3 monitoring-parity gap.

### Drive auth flow — detailed notes

Sign-in: `Identity.getAuthorizationClient(context)` (`di/AuthProvidersModule.kt:22`) → `DriveBackupAuth.signIn()` (L77) requests `DriveAuthScopes.ALL` = `drive.appdata` + `userinfo.email` + `userinfo.profile` (`DriveAuthScopes.kt:27,39-43`) via `AuthorizationRequest` + `authorizationClient.authorize(request).await()` (L89-94). A second additive scope set, `ALL_WITH_DRIVE_FILE` (L46), backs an incremental-grant flow for a separate "AI-readable Drive snapshot export" feature (`documentation/feature-specs/drive-ai-export.md` — not `backup.md`) sharing the same `DriveBackupAuth`/`AccountDataStore`.

Consent resolution: `SignInResult.NeedsResolution(pendingIntent.intentSender)` (L190-198) surfaces through `SettingsGraph.kt:39,57` (`StartIntentSenderForResult`) back to `completeSignIn` → `getAuthorizationResultFromIntent` (L113). Identity (email) resolved via userinfo endpoint, falling back to `toGoogleSignInAccount()`, then a placeholder.

Token caching: `DriveAuthTokenProvider.currentToken()` (L37-46) — null if no account in `AccountDataStore`; else cached `TokenSnapshot` if `expiresAtEpochMs > now` (50-minute TTL, a 10-minute margin under Google's ~60-minute token life); else silent re-`authorize()`. No refresh tokens exist — every refresh is a fresh silent authorization call.

Transport: REST via Ktor, `ktor-client-android` engine, base URL `googleapis.com`. `network/DriveAuthPlugin.kt:17-34` attaches Bearer auth per request, throws on HTTP 401; `DriveApiImpl` hand-builds `multipart/related` uploads against `/upload/drive/v3/files`; `DriveBackupStorage.withTokenRefreshOn401` invalidates caches and retries once.

Sign-out (`BackupClickHandler.confirmSignOut`, L340-363): cancels periodic work + deletes AI-export snapshots, then `DriveBackupAuth.signOut()` (L145-157) calls `revokeAccess()` then unconditionally clears local state (never the HTTP revoke endpoint).

WorkManager: `BackupScheduler` runs two independent unique work names — periodic `"auto_backup"` and one-time `"one_time_backup"` (docs still call this `manual_backup` — confirmed stale-doc drift, now only `LEGACY_ONE_TIME_NAME`). On `AuthRevoked`, `BackupWorker.handleFailure` cancels periodic work and shows the "auto-backup paused" notification.

### WorkManager / DataStore / File & URI access

**WorkManager — the cleanest seam in the app.** Confined to `app/app` (bootstrap/config only — `BaseApplication.kt:33-36` builds a `Configuration` with `HiltWorkerFactory`; the manifest explicitly removes the default `WorkManagerInitializer`) and `core/data/backup/worker` (the only implementation: `worker/BackupWorker.kt`, `worker/scheduler/BackupScheduler.kt`). The sole consumer, `feature/settings`'s `BackupClickHandler`, never imports `androidx.work.*` — it calls only the provider-neutral `AutoBackupController`/`AutoBackupWorkInfo` interface defined in `core/data/backup/api`, whose KDoc explicitly states the boundary is intentional (`AutoBackupController.kt:10,41`). Zero stray usage elsewhere.

**DataStore — not actually one abstraction.** `core/data/dataStore`'s `BaseDataStore`/`DataStoreProvider(Factory)` classes are all `internal` — invisible outside their own Gradle module. The only export is a narrow `CommonDataStore` interface (3 keys: 2 home-date-range longs + theme string), consumed by just `feature/settings` and `app/app/.../AppRootViewModel.kt`.

Meanwhile **four separate production classes across three other modules** each independently hand-roll their own `PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(name) }`:

- `core/data/backup/google-drive/.../auth/AccountDataStoreImpl.kt:26-30` — `"backup_account_prefs"`, 6 keys (account/token/snapshotFolderId/driveFileGranted)
- `core/data/backup/scheduling/.../BackupPreferencesRepositoryImpl.kt:28-32` — `"backup_scheduling_prefs"`, 7 keys
- `core/data/backup/scheduling/.../RestoreStateRepositoryImpl.kt:46-50` — `"restore_state_prefs"`, 7 keys
- `feature/app-dialogs/impl/.../data/AppDialogRepository.kt:40-49` — `"app_dialogs_prefs"`, 8 keys

⚠ Surprise: `core/data/backup/google-drive/build.gradle.kts` declares a project dependency on `:core:data:dataStore` that is **never actually imported** — a dead Gradle edge; the module separately pulls raw `androidx.datastore` libraries instead. Net effect for KMP: DataStore-Preferences 1.2.0 is itself already multiplatform-capable, but the migration touches **four independent construction sites**, not one.

**AlarmManager** and **SharedPreferences**: both confirmed at **zero occurrences** repo-wide (case-insensitive sweep). Nothing to migrate.

**File / URI access:**

| Module | What |
|---|---|
| `core/core` | `images/ImageStorageImpl.kt` — the central image save/decode abstraction: `FileProvider`, `ImageDecoder.createSource(context.contentResolver, uri)`, atomic temp-file+rename writes under `context.filesDir`/`cacheDir` (L37-123) |
| `feature/exercise` | camera/gallery picking UI (`ExerciseGraph.kt:94-151`) feeding `Uri`-typed domain params; Coil cache-busting via `File(path).lastModified()` |
| `core/data/database` | `snapshot/DatabaseSnapshotProviderImpl.kt` — WAL checkpoint + raw `File` copy/rename via `context.getDatabasePath`/`cacheDir` for backup/restore/migration snapshots (L29-224) |
| `core/data/backup/worker` | `BackupWorker.kt` — `File.createTempFile(..., applicationContext.cacheDir)` |
| `core/data/backup/google-drive` | `network/DriveApi(Impl).kt`, `storage/DriveBackupStorage.kt` — binary backup upload/download over `File` via Ktor multipart. (By contrast, the newer AI-export snapshot path is entirely in-memory `ByteArray` + Ktor, zero File/Uri usage — one of the more KMP-friendly corners already.) |
| `feature/settings` | `domain/BackupInteractorImpl.kt` — `File.createTempFile` under `cacheDir` for manual backup/restore |
| `feature/recovery` | `diagnostics/RecoveryDiagnosticsExporter.kt`, `RecoveryActivity.kt` — diagnostics export + `FileProvider` sharing via `Intent.ACTION_SEND` |
| `app/app` | Manifest `<provider android:name="androidx.core.content.FileProvider">` wiring + `res/xml/file_provider_paths.xml` |

`DocumentFile`, `MediaStore`, `openInputStream`/`openOutputStream` — confirmed zero occurrences. `feature/image-viewer` is Uri/File-free — it takes a plain `String` model into Coil3's `AsyncImage`; all URI resolution happens upstream. Coil3 is already KMP-ready.

**Non-common AndroidX — scoping check.** Room confined to `core/data/database` (no surprises). Paging used at 3 layers, terminating in exactly 3 list-screen features (all-exercises, all-trainings, archive). Compose Navigation spreads across all feature `*Graph.kt` files as expected (each registers its own `NavGraphBuilder.composable()`), but back-stack ownership stays centralized in `core/ui/navigation` + `app/app`. `hiltViewModel()` is called directly at exactly **2 sites** in the whole repo (`App.kt:63` for the root ViewModel, plus one test) — every feature screen instead resolves through a per-feature `Feature`/`FeatureAssisted` object (13 of them) delegating to the shared `StoreProcessor.rememberStoreProcessor()`. 100%-consistent convention, zero stray direct calls.

## Key findings carried into Phase 1

1. **Domain layer has real Android leaks** — 8 files across `recovery`/`settings`/`exercise`, 2 storing Context as Singleton fields, one baking `IntentSender` into a domain model. The custom Detekt rules don't catch this class of leak. Changes the "domain is a cheap win" framing.
2. **`feature/recovery` is architecturally a standalone Activity**, not an MVI screen feature — highest structural risk for CMP among all 15 feature modules.
3. **`feature/exercise-chart` uses `java.time.*`** (JSR-310, no Kotlin/Native target) — a concrete compile blocker, isolated to 8 files in one module.
4. **DataStore has 4 independent raw construction sites**, not the 1 the architecture doc implies — touches `core/data/backup/google-drive`, `core/data/backup/scheduling` (×2), `feature/app-dialogs/impl`.
5. **WorkManager and the Play-Services/Drive-auth boundary are both cleanly seamed** behind neutral interfaces (`AutoBackupController`, `core:data:backup:api`) — genuinely good news for an incremental migration. (Note: Phase 1 partially revises this for the Drive-auth boundary specifically — see below.)
6. **Firebase Performance's custom TTID pipeline uses non-public `firebase-perf` internals** with no iOS equivalent — confirmed monitoring-parity gap for Phase 3.
7. **Hilt is wired at the convention-plugin level**, not per-module — a DI swap's Gradle-wiring cost is small; the cost is in the hundreds of `@Inject`/`@ViewModelScoped` call sites Phase 2 needed to count precisely.
8. **Coil3 and kotlinx-serialization/-datetime are already KMP-ready**; Ktor is portable pending a Darwin engine; Compose BOM is on a pre-release (`-alpha`) channel worth a stability caveat.
9. **Several catalog dependencies are dead** (essenty/parcelize/composeCharts/junit-ktx) — `essenty`+`parcelize` specifically point at a prior, abandoned evaluation of Decompose (a KMP-first nav/lifecycle library) — later confirmed via git history (see Phase 1).
10. **Module coupling is favorably shallow** — no `api(...)` project dependencies anywhere, minimal feature-to-feature edges. Supports an incremental, feature-by-feature migration sequencing.

---

# Phase 1 — Layer portability

Classifying each layer as **COMMON-READY** / **COMMON-WITH-CHANGES** / **EXPECT-ACTUAL NEEDED** / **ANDROID-ONLY**, with file evidence.

## Domain layer

### Costing the untangling — five line items, not "domain mostly ports"

None of these are large individually, but they're qualitatively different from "domain is already pure Kotlin" — item 3 is an open architecture question and item 5 is a contract redesign touching 3 layers.

**1. AppVersionProvider extraction.** `SettingsInteractorImpl.kt:26-36`, `BackupInteractorImpl.kt:176-187`, and `RestoreRecoveryCoordinator.kt:160-171` each independently read `context.packageManager.getPackageInfo(...)` for `versionName`/`versionCode` — **identical code duplicated 3×**. Fix: an `expect`/`actual` `AppVersionProvider` (Android: `PackageManager`; iOS: `Bundle.main.infoDictionary`). A single fix collapses all 3 leaks at once. Effort: **trivial**.

**2. Platform path resolution.** `BackupInteractorImpl.kt:76,148` uses `context.cacheDir` for temp backup/restore files; `StartupMigrationCoordinator.kt:126` uses `context.getDatabasePath(name)` to peek the live DB's `PRAGMA user_version` without opening Room. Fix: `expect`/`actual` path resolution — the same pattern already required for DataStore's `producePath` (see Data layer) and Room-KMP's driver setup. Not new scope; do it once, reuse everywhere. Effort: **small** (rides along with the data-layer work).

**3. AppRestarter redesign.** `RestoreRecoveryCoordinator.restartApp():179-186` — `getLaunchIntentForPackage` + `startActivity` + `context is Activity` + `Runtime.exit(0)`. This is a **process-kill-and-relaunch primitive with no iOS equivalent** — iOS apps cannot programmatically relaunch themselves. It's also a near-duplicate of the sanctioned restart logic in `app/app/.../NavigatorExt.kt`. This is a real design question, not a port: does iOS even need "restart"? The reason for restart today is a stale in-process Room/DAO singleton graph after a live DB-file swap — a DI container that can be torn down and rebuilt *in place* (Metro/Koin both support this) might sidestep the need entirely. Consolidate the duplicate logic into one `Navigator.restartApp()` call while at it (free cleanup). Effort: **medium** — flagged as a Phase 5 design decision, not just an estimate line.

#### DECISION RECORD — A.3 implemented (item 3 resolved for Phase A)

The restart primitive is now a platform-neutral seam: `AppReinitializer { fun reinitialize() }` in `core/core/platform/` (beside `PlatformInfoProvider`/`TempFileProvider`), with the single Android actual `AndroidAppReinitializer` (`@Binds @Singleton` in `PlatformModule`) carrying the one remaining process-restart body. Both previously byte-identical bodies are collapsed into it: `RestoreRecoveryCoordinator.restartApp()` now delegates to `appReinitializer.reinitialize()` (dropping its `@ApplicationContext` field and **all** `android.*` imports — this closes the **last A.2 domain leak**, so all `feature/*/domain/` is now `android.*`-free and A.5 is unblocked), and the Settings post-restore path resolves the seam by **constructor injection into `NavigatorEventBus`** (the `@Singleton` `Navigator` impl): `Navigator.restartApp()` calls `appReinitializer.reinitialize()` directly.

Routing restart through `NavigatorEventBus` rather than a Hilt `@EntryPoint` was a deliberate choice for the future Hilt→Metro swap. `@EntryPoint` sites are the most framework-variable piece of that swap (the assessment counts 6, each needing a bespoke replacement); constructor injection is portable as-is. So restart is **removed** from the `replay=0` nav-command bus (which also eliminates the same drop-with-no-mounted-subscriber hazard the `OpenRecovery` contract documents — a latent robustness win) and the now-dead `NavCommand.RestartApp` variant is deleted. Net: **no new `@EntryPoint` is introduced — the DI-swap `@EntryPoint` footprint stays at 6, not 7.** `NavigatorExt` remains entirely Hilt-free.

Findings from the A.3a audit that shaped this (root cause is **not** what the code comments claim):

- **The captured `@Singleton` DB/DAO/repository graph does NOT go stale after the DB-file swap.** The swap reuses the *same* `AppDatabase` object (`DatabaseSnapshotProviderImpl` does `appDatabase.close()` + atomic rename); Room 2.8.4 DAOs hold only a `RoomDatabase` reference and take a fresh pooled connection per query/`createFlow`, so the ~22–24 captured references follow the reopen for free. The process restart is a **sledgehammer for a clean in-memory/UI/nav slate**, not a fix for stale captured handles.
- **HARD GATE verdict:** in-place reinit needs **~0–1** `@Singleton` classes to grow swappability indirection *under the reuse-same-object model* (PASS) — but **~22–24** if reinit ever *rebuilds* `AppDatabase` (STOP). Keep the reuse-same-object model. No `@Singleton` subscribes to a Room `Flow` in `init{}`; DB-derived UI state lives only in `ViewModel`-scoped Stores (12 backstack + 2 activity, none `@Singleton`); every `@Singleton` in-memory holder is DB-independent (DataStore/GMS/transport).

Two decisions recorded:

1. **The Android process-restart actual is behavior-preserving FINAL for Android-with-restart — not interim debt.** Consolidating on an `@ApplicationContext` actual is exactly equivalent: the old `if (context is Activity) finishAffinity()` branch only ever ran on the Settings (nav) call site; on that site `FLAG_ACTIVITY_CLEAR_TASK` finishes the task's activities and `Runtime.exit(0)` supersedes the per-Activity teardown, so no dangling Activity results. A **restart-free** in-place reinit is a *separate future actual*, not a later "fix" of this one.
2. **Future restart-free actual (iOS, or an Android UX-wart optimization) — designed now, implemented later.** Correct reinit order: **reopen Room → `NavCommand.ResetToRoot` → clear `NavController` saved-states** (which transitively frees the 12 backstack feature Stores so they re-subscribe to the reopened DB). **`AppDialogRepository` MUST be preserved, NOT reset** — the `RestoreSuccess`/`UndoRestoreSuccess` dialogs are persisted to DataStore *before* the restart precisely so they survive it; an in-place reinit that resets it would regress the undo flow that currently works only because it survives the kill.
   - This is gated on a **timeboxed Room-reopen spike** (NOT Phase A; does NOT gate A.3b/A.5). The spike must prove Room 2.8.4 reopens its connection pool **in-process** after `close()`+swap. Traps: a naive "close, rename, run a query, see if it throws" gives a **FALSE PASS** — a stale handle may return a cached connection pointing at the OLD inode (the replaced file's inode stays alive while an open fd references it), so the query "succeeds" reading pre-swap data → silent corruption, the worst failure class. Test **data readability** instead: write a sentinel row into the snapshot DB *before* the swap, swap, then assert the reopened DB reads the sentinel from the NEW file. Close+reopen via the **same `AppDatabase` object** (the model the code uses), on a **real device/emulator** with the production `BundledSQLiteDriver` — **not** JVM/Robolectric (inode/file-handle semantics are OS-level).

**4. ImageUri wrapper.** `ExerciseInteractor(Impl).kt` — `saveImage(uri: Uri, ...)`, `createTempCaptureUri(): Uri`. `Uri` here is just an opaque handle to picked-image bytes. Fix: `expect`/`actual` `ImageUri` wrapper (Android: wraps `android.net.Uri`; iOS: wraps a file path / `PHAsset` identifier). Genuinely expect/actual-able — both platforms have "a handle to a picked image." Effort: **small**, mechanical.

**5. BackupAuth/SignInResult contract redesign.** `SignInOutcomeDomain.NeedsResolution(intentSender: IntentSender)` (feature/settings) **and** `SignInResult.NeedsResolution(intentSender: IntentSender)` (core/data/backup/api) — near-identical shapes, both leak `IntentSender`. Also `BackupAuth.completeSignIn(intentData: Intent?)` takes `Intent?` directly in its **public interface signature**. `IntentSender` cannot become `expect`/`actual` — Android's model is two-phase ("get a token, launch it elsewhere, get a result back"); GoogleSignIn-iOS's model is single-phase (call a Swift function with a presenting view controller, await a completion handler — see Phase 3). The resolution-UI round-trip must move *below* the domain/api boundary entirely, into the platform-specific `BackupAuth` impl, so `Intent`/`IntentSender` never cross into `domain/` or `core/data/backup/api` at all. Touches the Android impl, the api contract, and the Settings UI's `StartIntentSenderForResult` launcher. Effort: **medium** — real design work, not mechanical.

> **Note — this is ONE workstream, not two.** Items 5 here (the domain-layer `IntentSender` leak) and the `core/data/backup/api`-module leak found in the touchpoints sweep (`BackupAuth.kt`, `SignInResult.kt`) are **the same underlying two-phase Google Sign-In consent model** baked into the domain/api surface. They are consolidated into a single workstream — "de-Android the auth domain/api surface" — in the Phase 4 rollup, counted once, and placed on the **critical path** because it gates domain purity, the data-layer auth port, *and* the iOS auth implementation. The iOS auth **implementation** itself (GoogleSignIn-iOS / OAuth+Keychain, appDataFolder, silent refresh) is a separate, downstream workstream (see Phase 3/4) — not double-counted with the redesign.

#### DECISION RECORD — A.2b implemented (item 5 resolved for Phase A)

Item 5 is closed for the three domain/api leaks (`SignInResult.NeedsResolution`, `BackupAuth.completeSignIn`, `SignInOutcomeDomain.NeedsResolution`, plus `BackupInteractor(Impl).completeSignIn`). `Intent`/`IntentSender` no longer appear in `feature/settings/domain/` or `core/data/backup/api/`. The resolution round-trip now crosses the boundary as two opaque, platform-neutral handles introduced in `core/data/backup/api/model/`:

- `AuthResolution(val platform: Any?)` — data→UI, wraps the Android `IntentSender`.
- `AuthResolutionOutcome(val platform: Any?)` — UI→data, wraps the Android result `Intent?`.

Both are the **same shape** (single `platform` field). Conversion to/from the concrete Android types happens only at the mvi-handler edge (`BackupClickHandler`, where `android.*` is allowed) and inside the Android `BackupAuth` impl (`DriveBackupAuth`); the domain and api layers pass the handles straight through and never unpack `.platform`.

Three decisions recorded with this implementation:

1. **Accepted ASYMMETRIC contract — final with rationale, not interim debt.** Android is two-phase (UI-mediated `ActivityResult`): it emits `NeedsResolution` and later has `completeSignIn` called. iOS will implement a **subset** — single-phase, never emits `NeedsResolution`, `completeSignIn` is never called on iOS, and the presenting view controller is obtained via a root-VC holder. A *symmetric single-phase* contract was **rejected** because it fights the app's MVI/Compose-`ActivityResult` flow. **Do not symmetrize this later.**
2. **`AuthResolution` / `AuthResolutionOutcome` are OPAQUE-PER-PLATFORM by design.** At the KMP split each becomes an `expect value class` with per-platform `actual` (Android → `IntentSender` / `Intent?`; iOS → presenting-context ref). The Phase-A `.platform as IntentSender` downcast in `BackupClickHandler` (`:206`, `:308`) is the **precursor** to the Android actual's unpacking and migrates verbatim into `androidMain`. It is **not** a "temporary `Any` that later becomes neutral."

   > **KMP TODO (do not act in Phase A).** That `.platform as IntentSender` is an **unsafe** cast. It is safe *today* only because the Android impl (`DriveBackupAuth`) is the **sole producer** of `AuthResolution` and always puts an `IntentSender` in it — a guarantee held by **contract, not by type** (iOS never emits `NeedsResolution`). When `AuthResolution` becomes `expect`/`actual` at the KMP split, this unpacking is **Android-actual logic** and must move into the Android `actual` as the **single unpack point** in `androidMain`. It must **not** remain a scattered unsafe cast in the shared (`commonMain`) handler. There are currently two such casts (`:206`, `:308`); both collapse into that one Android-actual accessor.
3. **`platform` is `Any?`, not the `Any` first drafted.** The `AuthResolutionOutcome` handle must carry the cancelled-resolution case (Android's `ActivityResult` yields a null `Intent` on cancel; `DriveBackupAuth.completeSignIn(null)` returning `Failure` is an existing, tested contract). A non-null `Any` cannot be constructed from `resultIntent: Intent?` at the handler wrap site without a crash-on-cancel `!!` or a behaviour change. `Any?` on **both** types preserves the null-cancel path and keeps them symmetric; it maps cleanly onto a future `expect value class` wrapping a nullable payload.

Still open in this workstream (tracked elsewhere, NOT part of A.2b): the mvi/ui surface deliberately stays Android-typed for Phase A — `SettingsStore.Action.HandleAuthResult(Intent?)`, `SettingsStore.Event.AuthResolutionRequested(IntentSender)`, and `SettingsGraph`'s `StartIntentSenderForResult` launcher. Neutralizing those is a settings-screen KMP prerequisite deferred past Phase A. `RestoreRecoveryCoordinator`'s `Activity`/`Context`/`Intent` leak is Phase A.3.

### Detection method, stated plainly (re-verification)

See [Phase 0 → Headline finding](#headline-finding--the-domain-layer-is-not-actually-pure) for the full exhaustive re-scan: 26 `domain/` directories checked directly, 8 production files confirmed by two independent methods — **not a floor**.

## Data layer

| Component | Verified status | Classification |
|---|---|---|
| **Room** | KMP support **stable since Room 2.7.0** (Apr 9 2025) — the app's 2.8.4 is already on that branch, so this is new source sets, not a version bump. Driver: `BundledSQLiteDriver` (commonMain-usable, no linker config) recommended over `NativeSQLiteDriver`. In-memory DBs work identically via `:memory:` on iOS. **Gap:** the app's JVM tests use Android's `Room.inMemoryDatabaseBuilder` (4,869 test LOC in `core/data/database` alone) — needs re-plumbing onto the driver-based builder for commonTest/iosTest parity. Known Android-only gaps: `setQueryCallback`, `setAutoCloseTimeout`, prepackaged-DB creation, `enableMultiInstanceInvalidation` (none currently used). A ground-up Room 3.0 (new `androidx.room3` group, KSP-only) went stable **2026-07-01** — days ago — but 2.x is safer to build on today. | COMMON-WITH-CHANGES |
| **Room migration testing** | Real gap, stated plainly. `MigrationTestHelper` is **Android-instrumentation-only through Room 2.8.4**, with no documented KMP/iOS story. This matters because the recovery feature explicitly removed the destructive-migration fallback *on the strength of* a CI-enforced `MigrationsRegistryTest` using this exact helper. Room 3.0 may eventually close this gap (unconfirmed in its docs as of today) — until then, migration tests stay Android-only regardless of how much other logic moves to commonMain. Not a blocker (Android already has full coverage; iOS starts fresh with no legacy schema history to migrate from) but a permanent asymmetry to document. | ANDROID-ONLY (for now) |
| **Ktor** | Darwin engine (`ktor-client-darwin`) is the standard iOS choice, same 3.4.3 release train. The app's pinned 3.4.3 (Apr 22 2026) is already past KTOR-9497 (a Darwin SIGABRT race, fixed that release) — no action needed there. Two open risk flags: background uploads on Darwin are limited (relevant to Drive backup uploads surviving app backgrounding) and gzip request-body compression is unimplemented on Darwin (multipart backup uploads go over the wire uncompressed on iOS vs. Android/OkHttp — bandwidth nuance, not a blocker). | COMMON-WITH-CHANGES (swap engine only) |
| **DataStore Preferences** | KMP-supported since 1.1.0-alpha02; current stable 1.2.1 (app is on 1.2.0, a trivial patch behind). Standard pattern: `expect fun createDataStore()` + iOS `producePath` resolving via `NSFileManager`. This is the **exact fix needed for the "4 independent raw construction sites"** finding (`AccountDataStoreImpl`, `BackupPreferencesRepositoryImpl`, `RestoreStateRepositoryImpl`, `AppDialogRepository`) — one small pattern repeated 4×, not 4 different problems. | COMMON-READY (pattern), needs 4 applications |
| **kotlinx.serialization / kotlinx.datetime / coroutines** | All confirmed portable — already the dependency backbone of the data layer. | COMMON-READY |
| **Google Drive auth (iOS re-implementation)** | See Phase 3 → Google auth on iOS for the full sizing — headline: GoogleSignIn-iOS's incremental-authorization API (`GIDGoogleUser.addScopes`) matches the app's existing two-tier scope pattern conceptually, and token acquisition can be driven **directly from Kotlin/Native via cinterop** (confirmed working precedent), lowering the estimate versus "rewrite the whole flow in Swift." | EXPECT-ACTUAL / re-implementation |

## UI layer (Compose → CMP)

| Surface | Verified status | Classification |
|---|---|---|
| **Resources** | **541** `R.string`/`drawable`/`array`/`font` references, **74** `stringResource(` call sites, **19** per-module `strings.xml` files (1,538 lines total across EN+RU). Only **3** files reference `painterResource`/`ImageVector` — a small drawable surface. CMP's resource system (`org.jetbrains.compose.resources`) is the de-facto-stable standard path; migration is manual but ~80% scriptable (move `strings.xml` verbatim into `commonMain/composeResources/`, regenerate, rename `R.string.x`→`Res.string.x` at each of the 74 call sites). No conversion tooling exists — plurals and any locale-qualified resources need manual attention. | COMMON-WITH-CHANGES (mechanical, ~80% scriptable) |
| **Google Fonts downloadable provider** | `core/ui/kit`'s `AppTypography.kt:34-35` uses the GMS downloadable-font provider — confirmed **Android-only, no iOS equivalent**, and CMP's own feature request for it is still open/unimplemented. Standard substitute: bundle the actual static font files under `composeResources/font`. | ANDROID-ONLY → small asset-prep fix |
| **AndroidX Lifecycle / ViewModel** | Core APIs KMP-compatible since 2.8.0; **full Compose-integration parity on iOS landed in 2.11.0 (June 2026) — weeks old as of this report**, not battle-tested. Since Hilt has no KMP story, `hiltViewModel()` must be replaced by whatever the new DI framework's ViewModel-scoping helper is (`koinViewModel()`, etc.) — **and** Google's own KMP guide requires *hand-writing* an iOS-side `ObservableObject`-backed `ViewModelStoreOwner` wrapper, since Compose's automatic Activity/NavBackStackEntry-tied wiring is Android-specific. This is real glue code per app, not a drop-in swap. | EXPECT-ACTUAL NEEDED (freshly-stable, real glue) |
| **Navigation** | See callout below — a compound decision (library choice + a real historical data point) worth its own treatment. | COMMON-WITH-CHANGES |
| **Window insets / haptics** | Both framework-handled in CMP (insets mirror Jetpack's API; `LocalHapticFeedback` bridged to `UIFeedbackGenerator` since CMP 1.6.10). Low expected cost; full `HapticFeedbackType` parity unverified but low-risk. | COMMON-READY |
| **LocalActivity / LocalContext usage** | No common CMP equivalent exists. Directly relevant to Phase 0's touchpoint findings: `ActivityHolder`/`ActivityHolderImpl`, `ResourceManagerImpl`, and `StoreProcessor`'s `LocalActivity.current` read (used by *every* screen's performance-trace hookup) all need a per-call-site expect/actual audit. | EXPECT-ACTUAL NEEDED |
| **Haze (blur)** | Fully supports Android + iOS on its stable 1.6.x line (the app is on 1.7.1). A 2.0.0-alpha rewrite is mid-flight — don't adopt it expecting stability yet, but the stable line already works. | COMMON-READY |
| **Coil3** | Already confirmed KMP-ready. | COMMON-READY |

### Navigation — a compound decision, with a data point the catalog only hinted at

Phase 0 flagged `essenty`/`parcelize` as dead catalog entries suggesting "a prior, abandoned evaluation of Decompose." **Direct git-history inspection (`git log`/`git show`) confirms something more specific: this app actually had a working Decompose implementation** — `DefaultRootComponent.kt`, `RootComponent.kt`, `Router.kt`, `Config.kt`, `Component.kt`, committed `ebe43325` (2025-08-31) — and it was **deliberately deleted 4 days later** in `8865d14` (2025-09-04, "migrate to jetpack navigation"), which introduced the `Navigator`/`NavigatorEventBus`/`Screen` design used today. Git records no rationale beyond the commit subject.

That rejection happened in an Android-only context and is **not treated as binding** now that iOS is in scope — but it's worth surfacing directly: **do you recall why Decompose was dropped?** If it was ergonomics/DX, that reasoning likely still holds. If it was "we don't need iOS-first tooling," that calculus has now changed.

**Current maturity (verified 2026-07-04):** Navigation-Compose Multiplatform (the family already in use) — CMP-iOS overall has been "stable and production-ready" since CMP 1.8.0 (May 2025); Navigation-Compose's own multiplatform-ness traces to 2.9.0-alpha07 (Feb 2025), current stable 2.9.8. A third-party (non-Google) source claims full stability as of CMP 1.8.0 — flagged as secondary-sourced, not confirmed first-party. ~14-17 months of iOS track record. Decompose — actively maintained (releases through 2026-07-01), iOS-first by design predating CMP-iOS itself, but named production-iOS case evidence is anecdotal/unverified.

**Seam analysis, grounded in this repo's actual code** (not the architecture doc's slightly-stale description — direct read found `Navigator` has **5** methods including `openRecovery()`, not the 4 documented in `architecture.md`, and `NavCommand` has 5 variants including `OpenRecovery`): for **either** candidate, the `Navigator`/`NavCommand`/`NavigationHandler`/`Action.Navigation.*` code stays untouched.

- **Compose Navigation Multiplatform:** bridge-only change — rewrite the ~17 files that touch the nav library directly (`NavigationEventBusSetup`, `NavigatorHolder`, `AppNavigationHost`, 12 per-feature `*Graph.kt` files, `BottomBarNavigationListener`, `Screen.kt` helpers). `RestartApp`/`OpenRecovery` need expect/actual regardless of nav-library choice (they're OS-level operations, not navigation). **Zero reach into DI.**
- **Decompose:** the narrow Handler-level seam holds, but "bridge-only" does **not**. Two things reach past the bridge into every feature: (1) **DI/lifecycle anchor** — today's `hiltViewModel()`-per-destination scoping has no Decompose equivalent; each Store needs constructing inside a component factory wired to a `ComponentContext`/`InstanceKeeper`, touching every feature's construction path (though this cost *partially overlaps* with the Hilt→new-DI swap already required); (2) **`popBack`'s result-passing** (today: `SavedStateHandle`, e.g. `Screen.PlanEditor.planEditorSavedAttr`) has no Decompose drop-in and needs a different mechanism on both producer and consumer sides.

**Recommendation, non-binding: default to Compose Navigation Multiplatform** — narrower blast radius, continues the current design, doesn't compound with the concurrent DI swap. **Decompose is a bounded fallback, not a hedge** — reconsider it only if the DI swap ends up requiring significant per-screen construction rework anyway (in which case Decompose's component-tree model might not add much marginal cost on top and buys a more iOS-proven foundation), or if a de-risking spike concretely shows Compose Navigation Multiplatform's iOS maturity is inadequate. This is a Phase 5 sequencing call.

## Dependency injection (Hilt)

Precisely counted, repo-wide:

| Annotation | Count |
|---|---:|
| `@Inject` sites | 165 |
| `@ViewModelScoped` | 116 |
| `@Singleton` | 150 |
| `@HiltViewModel` | 22 |
| `@Module` / `@InstallIn` | 35 |
| `@Provides` / `@Binds` | 105 |
| `@AssistedInject` / `@AssistedFactory` | 9 / 8 |
| `@EntryPoint` | 6 |
| Custom `@Qualifier` annotations | 4 (`@MainDispatcher`, `@IODispatcher`, `@DefaultDispatcher`, `@MainImmediateDispatcher`, all in `core/core/.../di/`) |

Most `@Inject` constructor sites port near-mechanically to a new DI framework's own constructor-injection model (Metro and kotlin-inject both use constructor injection too — often a 1:1 annotation rename plus scope-annotation remapping). The **22 `@HiltViewModel` sites** need the most rework, tied directly to the ViewModel-scoping-on-iOS question above. The **9 `@AssistedInject`/8 `@AssistedFactory` sites** correspond to the Stores taking route arguments — `ExerciseStoreImpl`, and similarly `LiveWorkout`, `PastSession`, `PlanEditor`, `ExerciseChart`, `ExerciseImage`, `Training` — per the `FeatureAssisted` pattern; these are the sites most likely to need bespoke handling since assisted-injection patterns vary most between DI frameworks. The **6 `@EntryPoint` sites** are notable: EntryPoints are Hilt's mechanism for reaching into the graph from non-Hilt-managed code (e.g. `BaseApplication.onCreate` calling into `RestoreRecoveryCoordinator`) — each DI framework needs a distinct replacement pattern (Koin's `GlobalContext`, or an explicit bootstrap-held root-component reference for Metro/kotlin-inject). Since Hilt is wired at the convention-plugin level (Phase 0), the Gradle-wiring cost is small; the real cost is these few hundred call sites.

---

# Phase 2 — Dependency migration matrix

Every dependency status below was verified live (2026-07-04) via web research, not recalled from training data. **UNVERIFIED** marks anything a research pass could not confirm from a live source.

## Migration matrix

| Dependency | Current | Verified KMP/iOS status | Target | Effort | Risk |
|---|---|---|---|---|---|
| Compose (BOM) | 2026.03.00-alpha | CMP-iOS "stable, production-ready" since 1.8.0 (May 2025) | Compose Multiplatform BOM | M | Medium — app tracks an alpha channel today |
| Room | 2.8.4 | KMP stable since 2.7.0 (Apr 2025); `BundledSQLiteDriver` recommended | Room 2.8.x KMP (not 3.0-alpha yet) | M | High — MigrationTestHelper has no KMP story yet |
| Ktor engines | 3.4.3 (android) | Darwin engine same release train; past the KTOR-9497 SIGABRT fix | + `ktor-client-darwin` | S | Low — 2 open UNVERIFIED risk items (bg uploads, gzip) |
| AndroidX Lifecycle/ViewModel | 2.10.0 | Core KMP since 2.8.0; full Compose-iOS parity only since 2.11.0 (Jun 2026 — weeks old) | 2.11.x + hand-written iOS ViewModelStoreOwner glue | M | Medium — freshly stabilized |
| Navigation | 2.9.8 (compose) | Multiplatform since 2.9.0-alpha07 (Feb 2025); Decompose viable alternative | Navigation-Compose Multiplatform (default) or Decompose | M–L | Medium — decision, not just migration |
| DataStore Preferences | 1.2.0 | KMP since 1.1.0-alpha02; current stable 1.2.1 | 1.2.1 + `producePath` ×4 sites | S | Low |
| Hilt → replacement DI | 2.59.2 | Hilt has zero KMP story, none planned | **Metro** (recommended), kotlin-inject, or Koin | L | High — largest single item, ~400 call sites |
| Firebase Crashlytics | gradle plugin 3.0.7 | GitLive Firebase-Kotlin-SDK v2.5.0 covers Crashlytics iOS via real CocoaPods passthrough | GitLive SDK + native GoogleService-Info.plist/dSYM setup (unavoidable regardless) | S–M | Low |
| Firebase Performance | plugin 2.0.2 | GitLive covers only generic named traces (no HTTPMetric, no frame-metrics internals); native iOS SDK auto-instruments screen + app-start traces but exposes no finer public hook — same ceiling as today's non-public-API workaround | GitLive generic traces + accept the automatic-trace ceiling | M | High — genuine monitoring-parity gap |
| Google Sign-In / Drive auth | play-services-auth 21.5.1 | GoogleSignIn-iOS 9.2.0 supports incremental auth (`addScopes`); token acquisition callable directly from Kotlin/Native via cinterop (confirmed precedent) | GoogleSignIn-iOS + BackupAuth contract redesign (Phase 1 item 5) | L | Medium |
| kotlinx.serialization / datetime / coroutines | 1.9.0 / 0.7.1 / 1.10.2 | Fully portable, no changes needed | unchanged | none | Low |
| Detekt + custom MVI rules | 1.23.8, 12 custom rules | See rule survival table below | subset rewritten, subset extended | M | Medium |
| Robolectric | 4.16 | JVM/Android-only by design, no KMP target planned. Logic with zero remaining Android references runs on plain `commonTest`/JUnit5 with no Robolectric needed | Robolectric survives only for genuinely Context-coupled Android tests | M | Low — mostly a net simplification |
| MockK → commonTest mocking | 1.14.7 | JVM-only. **Mokkery** 3.4.2 is the current KMP option (compiler-plugin-based, MockK-like DSL) but single-maintainer with real breaking-change churn | Mokkery for shared code, or hand-written fakes (common real-world choice) | M | Medium — bus-factor risk on Mokkery |
| JUnit5 assertions → commonTest | 5.13.4 | `kotlin.test` is a thin facade, not Jupiter-equivalent. Kotest-assertions (6.1.5) + Turbine (1.2.1, Flow testing, fully KMP) are the standard additions | kotlin.test + Kotest-assertions + Turbine | S | Low |
| Espresso/AndroidX Test → iOS UI tests | 3.7.0 / 1.7.0 | JetBrains' `runComposeUiTest` (v2 surface since CMP 1.11.0, May 2026) is the current shared-UI-test answer; still `@ExperimentalTestApi`. XCUITest keeps a narrow role for OS-chrome (permission dialogs, StoreKit) | `runComposeUiTest` for shared assertions + narrow XCUITest for OS chrome | M | Medium — experimental API, expect churn |

## DI alternatives, compared and ranked

Researched via 5 parallel search threads + 3 independent adversarial fact-check passes cross-verified against GitHub's release API directly (not just summarized page fetches — this caught at least two hallucinated release-year errors along the way).

**Ranking criteria for THIS codebase, stated explicitly and weighted in this order:**
1. **Preservation of compile-time DI safety** — this app's whole culture is compile-time-enforced conventions (custom Detekt MVI rules, sealed `Action`/`Event`/`State`, `HiltScopeRule` itself).
2. **Project maturity/stability appropriate for a solo maintainer** — bus-factor risk matters more here than for a team that can absorb a stalled dependency.
3. **commonMain/KMP readiness** — deliberately ranked *below* the first two, since this is a long-term solo project where "will this dependency still be alive and debuggable alone in 2 years" matters more than "does it have every feature today."

| Candidate | Model | Current status | Fit for this team |
|---|---|---|---|
| **Metro** (recommended) | Compiler plugin (FIR+IR), not KSP. Architecturally closest to Dagger/Hilt. | 1.0.0 stable 2026-04-27, now 1.3.0 (2026-07-01) — active, frequent releases. Production-hardened by Square, Cash App, Vinted (all Android-only usage so far, driven by build-speed not KMP). Hilt-specific interop shipped 2026-06-10, marked *experimental*. Native/iOS supported; cross-module aggregation blocked by an open upstream Kotlin compiler bug on Native/Wasm. | Best paradigm match for a team coming from Hilt's compile-time model. **Lean on its mature Dagger interop, not the 3-week-old Hilt interop** — expect to hand-migrate `@HiltViewModel`/`@InstallIn` sites manually on the first pass. |
| **kotlin-inject** (+ anvil) | KSP-based compile-time codegen — the truest paradigm match to Hilt. | v0.9.0 (2026-01-07). **~6 months of commit silence** on both the core repo and kotlin-inject-anvil (which also transferred from Amazon's org to a personal account) — a real bus-factor risk. No built-in ViewModel-scope equivalent. | Technically closest match, but solo-maintainer stagnation is a real risk to inherit for a solo dev's own long-term project. |
| Koin | **Runtime** service locator (a new compiler plugin adds compile-time *validation*, not codegen resolution). | Core 4.2.2 (2026-06-15). Official Hilt→Koin migration guide exists with a phased coexistence path; JetBrains' own KMP migration docs recommend it first. | Easiest migration path, but a real paradigm shift away from compile-time safety — a meaningful trade-off for a codebase this disciplined about compile-time-enforced conventions. |

**Architectural-invariant enforceability under each candidate, stated explicitly:** `HiltScopeRule` today statically flags a class named `*Handler`/`*Interactor`/`*Mapper` that isn't `@ViewModelScoped`, and `*Repository`/`*DataStore`/`*Database`/`*StoreDispatchers` that isn't `@Singleton` — enforcement lives *at the class declaration*. Metro and kotlin-inject, being compile-time/annotation-driven like Hilt, **can** preserve an equivalent rule by retargeting the annotation names Detekt looks for — the enforcement point stays at the class. **Koin's runtime model changes this structurally, not just cosmetically**: scope is declared in a separate module-registration DSL block (`single { ... }` vs `factory { ... }`), not on the class itself — a Detekt rule could theoretically grep Koin module files for scope-DSL patterns, but the enforcement point moves away from the class declaration to a separate registration file, and nothing stops a developer from defining a class correctly but simply forgetting to register it with the right scope in the Koin module (a class of bug `HiltScopeRule` makes structurally impossible today). This is a real, structural regression in enforceability under Koin specifically — worth weighing against Koin's easier migration path.

## Custom Detekt rule survival

12 custom rules found in `lint-rules/`. Assessed against a Hilt→(chosen DI) swap and a KMP source-set restructure:

| Rule | Verdict |
|---|---|
| `HiltScopeRule` | **Dies outright** — literally checks for `@ViewModelScoped`/`@Singleton`. Needs a full rewrite targeting the new DI framework's scope annotations. If Koin is chosen, this entire rule category may not even be meaningful anymore — a concrete side-effect of the DI choice worth weighing. |
| `MviHandlerConstructorRule` | **Survives conceptually** — checks for an `@Inject`-annotated primary constructor; needs its target annotation updated to whatever the new DI framework uses. |
| `DomainLayerPurityRule` | **Survives as-is — and should be extended now** to also flag raw `android.*` imports in `domain/`, not just data-model leaks. This single change would have caught the 8-file leak automatically. Recommend doing this before the KMP work even starts. |
| `DomainLayerNoUiRule` | Survives as-is. |
| `MviStoreStateRule`, `MviStateImmutabilityRule`, `MviActionNamingRule`, `MviEventNamingRule`, `MviHandlerNamingRule`, `MviStoreExtensionRule`, `ComposableStateRule`, `UiLayerNoDataRule` | **Survive largely unchanged** — these police Kotlin/Compose code shape, not infra wiring, so they're platform-independent conventions. **Open question**: do they need reconfiguration to run against a new `commonMain` source set once modules split into commonMain/androidMain/iosMain? Detekt's own multiplatform-source-set support wasn't researched this pass — flag for follow-up before finalizing rule-by-rule effort. |

---

# Phase 3 — Platform-specific work & prerequisites

## Hard prerequisites

**Gating, not optional.** iOS builds are **impossible** without:

- A **macOS machine** (Apple Silicon strongly preferred for build speed — Simulator + on-device builds both require Xcode, which is macOS-only).
- **Xcode** (free, but macOS-only, large download, frequent forced updates tracking Apple's OS releases).
- An **Apple Developer Program membership** ($99/year, required for device testing beyond 7-day free provisioning and mandatory for any App Store distribution).
- **Code signing / provisioning profiles** (certificates + provisioning profiles, historically the single most common iOS CI/local-setup failure point).

None of this is optional even for the T1 technical-foundation milestone — flag prominently as an open question if you don't already have macOS hardware.

## iOS host app

Standard current CMP pattern (well-established, not fast-moving like library-status research): a thin native SwiftUI `App` entry point hosting a `UIViewControllerRepresentable` that wraps `ComposeUIViewController { App() }` — the actual UI tree stays 100% shared Kotlin/Compose. iOS lifecycle glue needed: mapping `UIApplicationDelegate`/`SceneDelegate` callbacks (app launch, background/foreground, URL-scheme handling for the Google Sign-In OAuth redirect) to whatever the shared bootstrap logic expects (today's `BaseApplication.onCreate` equivalent). This is genuinely small, mechanical work once the DI graph and navigation bridge are in place — the CMP iOS host-app template is mature and well-documented.

## Google auth on iOS

**GoogleSignIn-iOS** is actively maintained (current 9.2.0, released 2026-06-15), distributed via SPM (CocoaPods sunsets Dec 2, 2026 — target SPM directly, don't set up CocoaPods for new work). **Incremental authorization is supported** and matches this app's existing two-tier pattern: sign in with base scopes, then call `currentUser.addScopes(_:presentingViewController:completion:)` on `GIDGoogleUser` for the additional `drive.file` scope — a direct conceptual match to Android's `AuthorizationClient.authorize()` two-tier flow (`ALL` then `ALL_WITH_DRIVE_FILE`).

**Token acquisition does not require a parallel Swift implementation.** Verified directly against source (not a blog summary): the open-source **KMPAuth** library's `iosMain` `GoogleAuthUiProviderImpl.kt` imports `cocoapods.GoogleSignIn.GIDSignIn` via cinterop and calls `GIDSignIn.sharedInstance.signInWithPresentingViewController(...)` directly from Kotlin inside a `suspendCoroutine`, extracting tokens in Kotlin. This means the bulk of `BackupAuth`'s iOS `actual` can likely be written once in Kotlin (cinterop calling into GoogleSignIn-iOS), not duplicated in Swift with a thin bridge — meaningfully lowers this line item versus a naive "rewrite the whole flow in Swift" assumption. The one piece that stays native glue regardless: the `AppDelegate.application(_:open:options:)` → `GIDSignIn.sharedInstance.handle(url)` callback tied to the custom URL scheme (small, one-time, unavoidable).

This composes directly with the Phase 1 item 5 contract redesign (moving `IntentSender`-shaped resolution out of the domain/api layer) — the iOS `BackupAuth` implementation's presentation-driven, completion-handler-based flow is exactly the shape that redesign needs to accommodate.

## Firebase on iOS

**GitLive's Firebase-Kotlin-SDK** (v2.5.0, 2026-05-21, actively maintained) covers Crashlytics, Performance, and Analytics for iOS as thin Kotlin call-throughs to the *real* native CocoaPods (`pod("FirebaseCrashlytics")` etc.) — not reimplementations. **Native iOS Firebase setup is unavoidable regardless**: `GoogleService-Info.plist`, CocoaPods/SPM integration (target SPM given the CocoaPods sunset), DWARF-with-dSYM build setting, and dSYM upload for crash symbolication all still apply exactly as they would without a KMP wrapper.

**Crashlytics:** full coverage — `recordException`, `log`, `setUserId`, `setCustomKey(s)`, collection toggling. Straightforward port.

**Performance — the nuance is more precise than Phase 0's framing.** GitLive covers only generic named custom traces (`newTrace(name)` → start/stop/putMetric) — no `HTTPMetric`, no frame-metrics internals. Separately, native iOS Firebase Performance *automatically* instruments per-`UIViewController` screen-rendering traces and an app-start trace with **no public hook for finer-grained access** — the same ceiling as this app's existing non-public-API workaround on Android, just arrived at from the other direction. **Net effect:** a coarser custom named-trace can approximate this app's current cold-start/screen-render measurements, but the rich `FrameMetricsRecorder`-based instrumentation genuinely has no equivalent. This is a real, permanent monitoring-parity gap — carry it into Phase 4 as deferred debt, not a solved problem.

## CI/CD for iOS

Contrasted with today's Android-only GitHub Actions pipeline: iOS CI needs **macOS runners** (materially more expensive per-minute than Linux runners on GitHub Actions — confirm the exact current multiplier at commit time, pricing shifts), an **XCFramework build step** (packaging the shared Kotlin framework for Xcode consumption), **fastlane** (or an equivalent) for build/signing/upload orchestration, **App Store Connect API** access (API key generation, App Store Connect app record setup), **signing-in-CI** (fastlane `match` or an equivalent certificate/provisioning-profile management scheme — historically the highest-friction part of iOS CI setup), and **TestFlight** distribution for beta testing. None of this exists today; it's entirely new pipeline construction, not an extension of the existing Android workflows.

## Testing strategy

| Concern | Verified current answer |
|---|---|
| commonTest assertions | `kotlin.test` alone is a real ergonomics regression from JUnit5 Jupiter — standard practice layers Kotest-assertions (6.1.5) and/or Turbine (1.2.1, Flow testing, fully multiplatform incl. iOS, maintained by Cash App) on top. |
| MockK replacement | MockK is JVM-bytecode-only, unusable in commonTest for iOS. **Mokkery** (3.4.2, compiler-plugin-based, MockK-like `every{}`/`verify{}` DSL) is the most viable current option, but single-maintainer with real breaking-change churn (a full 3.0 rewrite already happened). Most real KMP migrations hand-write fake implementations for commonTest instead of reaching for a mocking library at all — a reasonable default given Mokkery's bus-factor risk. |
| Robolectric's remaining scope | JVM/Android-only by design, no Native target planned or possible. Logic with zero remaining Android-framework references (i.e., after the Phase 1 domain-leak fixes) runs directly on plain `commonTest`/JUnit5 — **faster than today, no Robolectric needed**. What legitimately stays Robolectric-dependent: tests touching real Context-coupled framework behavior (ContentResolver, WorkManager integration, Parcelable, Compose-Android interop) — a permanently Android-only test surface, but a shrinking one as the domain leaks get fixed. |
| iOS UI testing | JetBrains' own `runComposeUiTest` (refreshed v2 surface since CMP 1.11.0, May 2026) is the current answer — written once in commonTest, executed per target (`connectedAndroidTest` / `iosSimulatorArm64Test`), no separate XCUITest code needed for shared-UI assertions. Still `@ExperimentalTestApi` — expect some churn. XCUITest keeps a narrow, permanent role for OS-level chrome CMP can't reach (permission dialogs, StoreKit payment sheets) — same gap Espresso has for Android system UI today. |
| Room in-memory / migration testing on iOS | In-memory DB works identically via `BundledSQLiteDriver`. `MigrationTestHelper`'s KMP story is **unconfirmed** (see Data layer) — the single biggest unresolved variable in the testing strategy. If it lands soon, existing migration tests port with a driver swap; if not, migration testing stays Android-instrumented indefinitely alongside the shared commonMain schema. |

> **Important addition on the `exercise-chart` java.time port specifically:** the DST/day-boundary test coverage for the `kotlinx-datetime` port (see Phase 4/5) **must pin a specific non-UTC, DST-observing timezone** (e.g. `America/New_York` or `Europe/Berlin`) **and specific real DST-transition dates** (that zone's actual 2026 spring-forward/fall-back dates) — a test suite running in UTC (the default in many CI environments) would never exercise the epoch-millis↔local-calendar-day conversion bug class this port risks, since UTC has no DST transitions to expose the bug.

## feature/recovery: defer vs. rebuild, costed both ways

`feature/recovery` is **not one thing** — it bundles a rare crash-safety-net screen with a real, user-facing Settings feature. They cost very differently.

| Component | What it is | User-facing frequency | Recommendation |
|---|---|---|---|
| **Scenario 2 — `RecoveryActivity`** | A standalone `ComponentActivity` (not an MVI screen) with 4 buttons: Update app (Play Store deep link), Export raw data (FileProvider share), Report issue (GitHub URL), Export diagnostics (FileProvider share). Triggered when Room can't migrate the on-disk DB at startup. | **Explicitly a "developer error" class per the spec** — a schema bump shipped without a matching migration, which a CI-enforced `MigrationsRegistryTest` should catch pre-merge. A correctly-shipped app should never reach this in production. | **Defer — keep Android-only** even for T2. All 4 actions are Android-specific plumbing (Play Store intents, FileProvider, ACTION_SEND) needing full iOS reinvention (App Store deep link, `UIActivityViewController`) for a screen most users will statistically never see. If a minimal T2 fallback is wanted, a static "something went wrong, contact support" screen costs ~0.5-1 day; full parity is not worth it given the rarity and the CI guard already in place. |
| **Scenario 1 + 3 — `RestoreRecoveryCoordinator`** | Headless orchestration: automatic rollback after a failed post-restore migration (Scenario 1), plus the user-facing Settings row "Revert last restore" (Scenario 3) — a real, discoverable, intentional feature reachable any time a restore has happened. | Scenario 3 is reachable by any user who has restored a backup — not rare at all for an active Drive-backup user. | **Include in mainline domain-layer work** — not a separate "rebuild as Composable" job, since there's no distinct screen here (it publishes through the existing cross-feature `AppDialog` system, already in scope elsewhere). Once the Context leaks are fixed (Phase 1 items 1-3), this coordinator's core logic is already close to portable pure Kotlin. The one real per-platform reimplementation: `StartupMigrationCoordinator`'s Room-free `PRAGMA user_version` peek uses Android's raw `SQLiteDatabase.openDatabase` — on iOS this becomes a raw pragma read via Room-KMP's own `BundledSQLiteDriver` (same driver Room itself uses), a small but genuine per-platform reimplementation, not an expect/actual copy-paste. |

Net: don't cost "feature/recovery" as one lump. Scenario 2's Activity is cheap to defer; Scenario 1/3's coordinator logic rides mostly for free on top of work already required elsewhere (Context removal, Room-KMP driver setup) — see the explicit audit trail in Phase 4.

---

# Phase 4 — Effort / cost / risk rollup

The auth-boundary redesign is counted **once** (not split across the domain leak and the api-seam leak), `restartApp()` is modeled as a reinit-state redesign rather than a doomed port, the DI estimate carries explicit ranking criteria and an enforceability caveat for Koin, navigation is a bounded either/or with Decompose as a conditional delta, and `feature/recovery`'s near-zero cost is shown as an audit trail against the domain-leak table, not asserted.

## Workstream rollup — ideal engineering days

**T1 (technical foundation — compiles and runs on iOS simulator/device, core flows functional, no App Store, no iOS monitoring, no iOS CI):**

| # | Workstream | T1 low | T1 exp | T1 high | Confidence |
|---|---|---:|---:|---:|---|
| 1 | Domain de-Androidification — AppVersionProvider dedup (3→1 sites), ImageUri wrapper, extend `DomainLayerPurityRule` for android.* imports now | 1 | 1.5 | 2.5 | High |
| 2 | **Auth domain/api redesign** — ONE workstream covering both the `SignInOutcomeDomain` leak and the `BackupAuth`/`SignInResult` api-seam leak (same underlying two-phase consent model). **Critical path.** | 3 | 4.5 | 6 | Medium |
| 3 | `restartApp()` → reinit-app-state redesign (3 call sites, 1 root cause, 1 fix — benefits Android immediately) | 4 | 6 | 9 | Medium-low |
| 4 | `feature/recovery` integration — see audit trail below; mostly already counted in #1/#2/#3 | 1 | 1 | 2 | High |
| 5 | Remaining ~13 leak-free feature `domain/` modules → commonMain (mechanical relocation) | 6.5 | 10 | 13 | Medium |
| 6 | Room + DataStore + Ktor → KMP (driver setup, 4× `producePath` sites, Darwin engine, JVM-test re-plumb off `inMemoryDatabaseBuilder`) | 9 | 13 | 20 | Medium |
| 7 | `feature/exercise-chart` java.time → kotlinx-datetime port + DST-pinned tests | 2 | 3 | 4 | High |
| 8 | **Hilt → Metro DI swap** — ~400 sites incl. 22 @HiltViewModel, 9 @AssistedInject/8 @AssistedFactory, 6 @EntryPoint, 4 custom @Qualifier; convention-plugin rewiring + per-module migration + HiltScopeRule replacement | 25 | 38 | 55 | Low — upper-bound driver |
| 9 | Navigation — Compose Navigation Multiplatform, default path (behind existing `Navigator` seam) | 4 | 5 | 6 | Medium |
| 10 | UI layer — Compose resources (541 refs/74 call sites), Google Fonts replacement, hand-written iOS ViewModelStoreOwner glue, LocalActivity/LocalContext audit | 9 | 13 | 18 | Medium |
| 11 | Google Drive auth — iOS **implementation** (downstream of #2; GoogleSignIn-iOS + cinterop, incremental scopes, token cache) | 7 | 10 | 12 | Medium |
| 12 | iOS host app (SwiftUI shell + `ComposeUIViewController` + lifecycle glue) | 2 | 3 | 4 | High |
| 13 | Minimal testing to validate T1 (light-touch subset — full rebuild is a T2 item, #15 below) | 3 | 5 | 8 | Medium |
| | **T1 total** | **≈77** | **≈113** | **≈160** | |

**T2 additions (production launch only, beyond T1):**

| # | Workstream | low | exp | high | Confidence |
|---|---|---:|---:|---:|---|
| 14 | Firebase on iOS — GitLive SDK (Crashlytics + generic Performance traces), native GoogleService-Info.plist/dSYM/SPM setup | 2 | 3 | 4 | Medium-high |
| 15 | Full testing infrastructure (remainder beyond T1's light-touch subset — Kotest/Turbine, Mokkery-or-fakes, `runComposeUiTest` across all modules) | 7 | 11 | 13 | Low-medium |
| 16 | iOS CI/CD — macOS runners, XCFramework build, fastlane + match, App Store Connect API, TestFlight | 5 | 6 | 8 | Medium |
| 17 | App Store submission overhead (assets, privacy nutrition labels, review-response buffer) | 2 | 3 | 4 | Medium |
| | **T2 additions subtotal** | **≈16** | **≈23** | **≈29** | |
| | **T2 total (T1 + additions)** | **≈93** | **≈136** | **≈189** | |

**Conditional, not included above:** Decompose fallback delta (only if the de-risking spike shows Compose Navigation Multiplatform's iOS maturity is inadequate) — net of DI-workstream overlap, add **+7 to +13 days** to whichever total applies.

### Solo part-time calendar translation — assumption stated explicitly

**ASSUMPTION:** 8-10 focused hours/week (evenings/weekends around a separate full-time commitment — a common real-world default, not a floor). One "ideal engineering day" ≈ 6 focused hours, so this pace yields **≈1.3 ideal days of progress per calendar week**. **This breaks down if actual availability is lower** — at 5h/week, every figure below roughly doubles; at 15h/week, they shrink by roughly 40%. Recalibrate against your own real availability, not this default.

| Milestone | low | expected | high |
|---|---|---|---|
| **T1** (compiles & runs on iOS, core flows work) | ≈14 months | **≈20 months** | ≈28 months |
| **T2** (App Store submission-ready) | ≈17 months | **≈24 months** | ≈33 months |

These are large numbers. They are the single most important input to the go/no-go call in Phase 5 — a genuinely multi-year commitment at part-time pace, not a side project measured in weeks.

## Critical path & inter-workstream dependencies

- **#2 (auth redesign) gates #11 (iOS auth impl)** directly — you cannot build an iOS `BackupAuth` implementation against a contract that still requires `Intent`/`IntentSender` in its signature. It also gates full completion of #1's domain-purity goal (feature/settings' domain can't be honestly called "leak-free" until this lands) and touches #6 (the Drive-auth data-layer port shares the same contract).
- **#3 (reinit redesign) is NOT KMP-gated** — it's valuable and shippable on Android alone, independent of everything else. Recommend landing it first specifically because it's cheap to change course on early and removes a documented Android UX wart (the "two restarts in quick succession" tech-debt item) as a side effect.
- **#8 (DI swap) is the pacing item for almost everything downstream** — #10's ViewModel-scoping glue, #9's navigation bridge (if Decompose is chosen), and #13's testing all assume the DI story is settled. Sequence the de-risking spike (Phase 5) to validate #8's feasibility before committing further resources.
- **#9 (navigation) and #8 (DI) entangle if Decompose is chosen** — both rework how Stores are constructed. If Compose Navigation Multiplatform is used instead (the default recommendation), this entanglement doesn't exist and #9 is a clean, independent bridge-only change.
- **#6 (Room/DataStore/Ktor KMP) has no hard blocker** but shares small path-resolution patterns with #1/#4 (do the platform-path-resolution work once, reuse across DataStore, cache-dir, and the Room-KMP driver).

### feature/recovery — the audit trail for "rides free"

Nothing in `feature/recovery` gets a new, additional line item beyond workstream #4's small residual. Precisely:

- `RestoreRecoveryCoordinator`'s `readVersionName()` Context usage = **workstream #1** (AppVersionProvider), already counted.
- `RestoreRecoveryCoordinator.restartApp()`'s Context usage = **workstream #3** (reinit redesign) — and direct grep confirms this is one of exactly **3 call sites** for "restart" logic in the whole repo (`BaseApplication.kt:81`, `RestoreDialogChoiceObserver.kt:163`, `SettingsNavigationHandler.kt:20`→`NavigatorEventBus`→`NavigatorExt.kt:118`), backed by exactly **2 duplicate Context-based implementations** (`NavigatorExt.kt:118` and `RestoreRecoveryCoordinator.kt:179`) that workstream #3 collapses into one. Already counted there, not added again here.
- `StartupMigrationCoordinator`'s `context.getDatabasePath(...)` usage = the same platform-path-resolution pattern already required by **workstream #6** (Room-KMP driver setup) — one more application of a fix already budgeted, not a new one.
- The one genuinely new item: `StartupMigrationCoordinator`'s Room-free `PRAGMA user_version` peek uses Android's raw `SQLiteDatabase.openDatabase` — the iOS equivalent reimplements this via Room-KMP's own `BundledSQLiteDriver` rather than an expect/actual copy-paste. Small, real, ~1 day — this is the residual in workstream #4.
- `RecoveryActivity` itself (Scenario 2's UI): deferred, Android-only, $0 in the base estimate. See Phase 3 for the reasoning.

## Money

| Item | Cost | Type | Notes |
|---|---|---|---|
| Apple Developer Program | $99 | Recurring, annual | Required for device testing beyond 7-day free provisioning and mandatory for any App Store distribution. |
| macOS hardware | **Open question** | One-time (if needed) | Not knowable from the codebase — **flagging explicitly: is a Mac already available?** If not, a current entry-level Apple Silicon Mac mini runs roughly $600-900 depending on configuration (approximate — confirm current Apple pricing before budgeting). |
| CI macOS runner-minutes | **Unverified this session** | Recurring, monthly | GitHub Actions macOS runners have historically been priced at a materially higher per-minute multiplier than Linux runners (commonly cited as ~10×). Two live-fetch attempts against GitHub's billing docs failed (socket errors) during this assessment — **confirm the current multiplier against GitHub's Actions billing page before finalizing this budget line**, and consider minimizing cost by running iOS CI only on-demand/pre-release rather than on every commit. |
| Paid tooling | $0 expected | — | Metro, kotlin-inject, Koin, GitLive Firebase SDK, and fastlane are all free/OSS. No new paid tooling is strictly required by this migration. |

## Ongoing cost (post-launch)

- **Dual-platform maintenance multiplier** — reasoned estimate ~1.3-1.6× total engineering time per future feature, *not* a flat 2×, since domain/data logic is now shared; the multiplier concentrates in platform-specific UI polish, platform-specific bug triage, and dual release management.
- **Dual CI cost** — the recurring macOS-runner-minutes line above, indefinitely.
- **iOS-specific bug surface** — real risk given no stated prior iOS experience; expect a slower initial pace on platform-specific issues (rendering quirks, App Store review rejections) until iOS expertise builds up.
- **Monitoring-parity debt** — the Firebase Performance frame-metrics gap (Phase 3) persists indefinitely unless Apple/Firebase expose a richer public API; reduced iOS performance-regression visibility is a standing, accepted cost.
- **Roadmap-freeze cost** — `documentation/tech-debt.md` documents a real, itemized v2.4/v2.7-tagged backlog (drag-to-reorder, snackbar-undo, templates-picker route, and others). That backlog stays frozen or radically slowed for the ~20-24 month expected timeline above — a concrete opportunity cost specific to this project's own documented queue, not a generic warning.

## Risk register — ranked by probability × impact

| Risk | Likelihood | Impact | Mitigation | Early signal |
|---|---|---|---|---|
| **Hilt→DI blast-radius underestimate** | High | High | Prototype the DI swap on one feature module first (the spike); keep Hilt+Metro coexisting via strangler-fig so a stalled swap doesn't block the app. | Spike's single-module swap takes meaningfully longer than the ~1.5-day/module average budgeted. |
| **Solo bandwidth / partial-completion** | Medium-high | High | Sequence so every intermediate milestone leaves Android fully working — never a big-bang cutover that could strand the app mid-migration. | Progress stalls >2-3 months at any point — treat as a signal to resource up, descope, or formally pause with a documented resumption point. |
| **restartApp→reinit redesign uncovers hidden state coupling** | Medium | Medium-high | Prototype in isolation on Android first, independent of KMP (it's valuable there regardless). | Audit finds more than ~5-6 `@Singleton` classes needing swappability indirection. |
| **Auth domain/api redesign scope creep** | Medium | Medium | Timebox the design phase to 2 days before committing to the full estimate. | Design isn't converging by day 2 — escalate or accept a less-clean interim boundary. |
| **Freshly-stable CMP/Lifecycle/Room-iOS edge cases** | Medium | Medium | The de-risking spike directly tests this; pin exact versions, watch changelogs closely. | Spike surfaces a crash/bug in Lifecycle 2.11.0's iOS Compose integration (weeks old as of this report). |
| **Single-maintainer OSS dependency risk** (Mokkery, kotlin-inject-anvil if chosen) | Medium | Medium | Prefer Metro (most active, real production backers) over kotlin-inject-anvil (6-month commit silence); default to hand-written fakes over Mokkery for mocking. | A relied-upon library goes 3+ months without a release during active use. |
| **App Store review** | Low-medium | Medium | Read Apple's Review Guidelines early. Specifically confirm whether guideline 4.8 (apps offering third-party sign-in must also offer Sign in with Apple) applies to this app's Google Sign-In flow — a well-established, stable Apple policy worth checking against this specific app before submission. | First TestFlight/App Store Connect submission attempt. |
| **Firebase Performance iOS monitoring gap** | Certain (already confirmed) | Medium | Accept as permanent, documented deferred debt; revisit only if Apple/Firebase expose a richer public API. | N/A — already known, not a future trigger. |
| **Room `MigrationTestHelper` KMP gap persists** | Medium | Low-medium | Keep migration tests Android-instrumented-only; re-check Room 3.0's testing artifact periodically. | Room 3.0 ships a documented KMP `MigrationTestHelper` equivalent (currently unconfirmed). |

---

# Phase 5 — Recommendation

## Big-bang vs. incremental — incremental, domain-first

Justified by three findings from this study, not a generic default:

1. Phase 0's module graph is favorably shallow — no `api(...)` project dependencies anywhere, minimal cross-feature coupling, so feature-by-feature conversion is structurally possible, not just theoretically nice.
2. Hilt's convention-plugin-level wiring does **not** force a big-bang DI cutover, because Metro's Dagger-interop supports a strangler-fig coexistence — Hilt-managed and Metro-managed code can live side by side during the transition.
3. Several fixes (the domain Context leaks, the restart→reinit redesign) are valuable on Android *alone* and can land independently of any iOS commitment, de-risking the early phases and delivering value even if the migration ultimately stops at T1 or pauses indefinitely.

## Sequencing

1. **Domain-layer Android-leak fixes** (workstreams 1, partial-2, partial-3) — no KMP commitment required yet; also extend `DomainLayerPurityRule` to catch this class of leak going forward.
2. **`restartApp`→reinit redesign** (workstream 3) — de-risks the single riskiest estimate early, while it's cheap to change course, and ships value on Android immediately.
3. **De-risking spike** (below) — before committing to the full data/DI/UI buildout.
4. **Data layer** (Room/DataStore/Ktor KMP, workstream 6).
5. **DI swap, feature-by-feature strangler-fig** (workstream 8) — the largest, riskiest workstream; sequence after the spike has proven the coexistence pattern.
6. **Navigation** (workstream 9) — default Compose Navigation Multiplatform unless the spike says otherwise.
7. **CMP UI conversion** (resources, ViewModel/Lifecycle glue, workstream 10).
8. **iOS host app** (workstream 12).
9. **Platform integrations** — Google auth iOS impl (11), Firebase iOS (14).
10. **Testing infra + CI/CD** (13/15/16) — light-touch for T1, full build-out only if proceeding to T2.

## What to defer or keep Android-only

- `RecoveryActivity` (Scenario 2's crash-safety screen) — Android-only indefinitely, per Phase 3's reasoning.
- Firebase Performance's rich frame-metrics instrumentation — accept the gap, ship generic named traces via GitLive instead.
- **Decompose** — not a pre-emptive hedge. Adopt only if the spike concretely shows Compose Navigation Multiplatform's iOS maturity is inadequate (broken back-stack restoration, incompatibility with this app's `SharedTransitionLayout`-based shared-element transitions per `documentation/features.md`, or outright crashes) — not "just in case."

## Go / no-go framing

The honest headline number from Phase 4 is **≈20 months expected calendar time to T1, ≈24 months to T2**, at an assumed 8-10h/week solo pace — a genuine multi-year commitment, not a side project measured in weeks. During that window, `documentation/tech-debt.md`'s real, itemized v2.x backlog stays frozen or radically slowed, and — distinct from every library-maturity risk itemized above — the developer needs to build net-new Swift/Xcode/App-Store-process fluency from a standing start.

**This is worth it if:** there is already-evidenced user or business demand for iOS specifically (something only you can weigh — nothing in the codebase itself indicates this) that outweighs 1.5+ years of paused Android momentum; or the KMP/iOS learning experience is itself a goal independent of ROI; or the actual target is **T1 only** as a bounded architecture-proof / portfolio exercise, not a full production launch — in which case the ≈14-28 month range (not the T2 range) is the honest number to weigh.

**It is not clearly worth it if** the motivation is speculative ("might be nice to have iOS someday") without a concrete pull, given the opportunity cost is large and the single biggest risk in the register above — a stalled, half-migrated app — is a real and plausible failure mode for a solo part-time project at this scale.

## Smallest de-risking spike

Extract `feature/exercise-chart`'s `domain/` + data path into a KMP module target — chosen specifically because Phase 0 confirmed it has **zero** Context/Activity/Firebase/GMS touchpoints of any kind (the cleanest feature module in the repo) and its data access goes through `core/data/exercise`'s repository rather than raw Room, narrowing the spike's own risk surface further. Concretely:

1. Move its `domain/` package into a `commonMain` source set within a restructured module.
2. Swap its `java.time` usage to kotlinx-datetime (workstream 7 — small, already well-understood).
3. Swap its DI from Hilt to **Metro**, for this module only, using Metro's Dagger-interop to coexist with the still-Hilt-based surrounding app — directly testing the strangler-fig sequencing plan (workstream 8's biggest open question) on a small, bounded surface.
4. Add an iOS target; get it compiling for `iosSimulatorArm64`/`iosArm64`.
5. Port its existing 1,257 test LOC to `commonTest`, running on both the JVM/Android target *and* the iOS simulator target — including new DST-pinned date tests (a real, non-UTC, DST-observing zone such as `America/New_York` or `Europe/Berlin`, at that zone's actual 2026 spring-forward/fall-back transition dates — a UTC-only test suite would never exercise the epoch-millis↔local-calendar-day bug class this port risks).
6. Deliberately **defer** wiring up the full CMP UI/Compose-Navigation-Multiplatform integration in this spike — the business-logic/data/DI portability trio is where the real unknowns concentrate; UI/nav feasibility is comparatively well-established per Phases 1-2's research.

**Estimated cost:** ~5-8 ideal engineering days — small and bounded relative to the ≈113-day T1 expected total.

**A green spike proves:** this app's specific data-access shape compiles and runs correctly for iOS; the Hilt+Metro strangler-fig coexistence pattern actually works inside this convention-plugin-centralized build (not just "Metro supports KMP" in the abstract); the existing test suite behaves identically across JVM/Android and iOS targets, including the DST-sensitive dates.

**A red spike reveals — cheaply — which specific assumption breaks first:** if Metro's Dagger-interop can't cleanly coexist with this app's convention-plugin-wired Hilt setup (demanding a faster big-bang DI cutover instead of strangler-fig), if kotlinx-datetime's DST handling surfaces a genuine behavioral difference from java.time requiring more than a mechanical swap, or if the iOS target won't build against this app's specific Gradle convention-plugin setup at all (revealing that convention-plugin work must happen *before* any feature-level spike, not alongside it).

---

## Methodology note

Phase 0 was gathered via 5 parallel code-search agents (module graph classification ×2, Android-touchpoint sweeps ×3) plus direct shell verification (LOC counts, dependency-catalog cross-checks). Phases 1-3 added: an independent exhaustive re-scan of every `domain/` directory in the repo (26 directories, not a sample); direct reads of all 8 leaking domain files, the 3 neutral-seam contract files (`AutoBackupController.kt`, `BackupAuth.kt`, `SignInResult.kt`), `RecoveryActivity.kt` and the full `backup-recovery.md` spec, and all 8 `java.time`-using files in `feature/exercise-chart`; a precise repo-wide Hilt-annotation count; and 6 independent live web-research passes (navigation, Room/DataStore/Ktor, Compose resources/Lifecycle, DI alternatives, Firebase/Google-Sign-In-iOS, testing strategy) each cross-checking claims against primary sources with dates. Phase 4-5 added a precise `restartApp()` call-site/implementation count via direct grep (which surfaced a discrepancy between the `backup-recovery.md` spec's prose description and the actual call path in `BaseApplication.kt:81`, noted rather than silently reconciled) and a custom-`@Qualifier` count, then rolled every workstream into T1/T2 ranges with an explicitly-stated part-time calendar assumption.

Every current-status claim is cited; anything that could not be verified live is marked **UNVERIFIED** rather than asserted from memory — including two failed live-fetch attempts on GitHub's Actions billing page during this assessment, flagged rather than papered over.

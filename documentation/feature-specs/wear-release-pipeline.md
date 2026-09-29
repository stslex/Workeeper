# Wear release through the Android release pipeline

Status: decisions locked by the owner on 2026-09-28. Implementation is authorized for PR-A, PR-B
and PR-C (§11). Wear production distribution is out of scope (§12).
Governs: `fastlane/Fastfile`, `.github/workflows/android_deploy_prod.yml`, the new
`.github/workflows/android_deploy_wear.yml`, `ConfigureWearApplication.kt`, and store metadata
under `fastlane/`. This work amends [release-flow.md](../release-flow.md). The
[Phase 1 specification](wear-phase-1-active-workout-tile.md) keeps governing privacy, transport
and the read-only release runtime; nothing here relaxes it.

## 1. Outcome

Every release or hotfix cut ships from one pinned commit:

1. the phone bundle to the production track, exactly as today, then
2. the Wear bundle to the Wear OS internal testing track,

with no manual step per release beyond the existing `production` approval.

| Id | Success criterion |
|---|---|
| S1 | The phone path up to tag and merge keeps its behaviour and failure modes. The only additions are gates that fail before any Play mutation (§6, §8). |
| S2 | The Wear bundle has a unique, monotonic versionCode derived from the same TOML, and a versionName that identifies the watch. |
| S3 | The Wear upload is idempotent and retryable on its own. Its failure never affects the phone release. |
| S4 | Wear release crashes are deobfuscated. Watch and phone are separable in Crashlytics and Analytics. |
| S5 | A deploy never silently overwrites store-listing changes made in Play Console. It stops and hands them over for reconciliation. |

## 2. Locked decisions (owner, 2026-09-28)

| Id | Decision |
|---|---|
| D1 | The Wear bundle ships with every release to the Wear OS internal testing track. The first combined run is release 1.52.0; the owner verifies it on his own watch. |
| D2 | Wear `versionCode = 1_000_000 + TOML versionCode`. Configuration fails if the TOML versionCode is `>= 1_000_000`. Irreversible from the first Wear upload: that code becomes the floor. |
| D3 | The Wear upload is a separate reusable workflow that runs after the phone `deploy` job (`needs: deploy`) on the same pinned SHA. It is never a step inside `deploy`. |
| D4 | Wear `versionName = TOML versionName + "-wear"`. |
| D5 | The store listing is repository-owned. Play Console edits are detected before any upload and are pulled into the repository (§8). |
| D6 | Wear listing text is drafted now (Appendix A) and applied only together with the Wear production decision. |
| D7 | Package name and signing stay identical to the phone app (Phase 1 STOP condition; Data Layer requirement). |
| — | Rejected: routing the first release through `android_deploy_beta.yml`. It is disabled (`if: false`, Issue #102), targets the public open-testing track `beta`, cannot hand the same versionCode to production (the production lane re-uploads and Play rejects a used code), pushes to `github.ref`, and is not the code path production uses. |

## 3. Verified facts

Verified on `dev` at `83d45ccb`. Re-verify at the implementation base before changing code.

| Id | Fact | Evidence |
|---|---|---|
| F1 | The production lane builds and uploads only the phone: `gradle(task: "clean :app:store:bundle")`, then `upload_to_play_store` with the default production track. | `fastlane/Fastfile:22-24`; `android_deploy_prod.yml:232-233` |
| F2 | `:app:store:bundle` builds every store variant. With more than one AAB and no `aab:`, `upload_to_play_store` uploads `GRADLE_AAB_OUTPUT_PATH`: the newest AAB by mtime anywhere under the project. It works today only because the release bundle finishes last. | fastlane 2.228.0 `fastlane/lib/fastlane/actions/upload_to_play_store.rb:22-28`, `gradle.rb:70-99` |
| F3 | Wear identity equals the phone store identity: `applicationId = APP_PREFIX`; versionName and versionCode come straight from the TOML. | `ConfigureWearApplication.kt:29-32`; `ConfigureApplication.kt:46-49` |
| F4 | The Wear store flavor reads `app/store/google-services.json`. The deploy job already provisions that file. | `app/wear/build.gradle.kts:54-55`; `android_deploy_prod.yml` step "Create Google Services Config file" |
| F5 | Wear release mapping upload is opt-in through `-PcrashlyticsMappingUpload=true` (ci-cd.md F07). The phone release uploads its mapping unconditionally. | `app/wear/build.gradle.kts:31-32`; `ConfigureApplication.kt:55-60` |
| F6 | Wear manifest: `uses-feature android.hardware.type.watch` (required) and `com.google.android.wearable.standalone = false`. | `app/wear/src/main/AndroidManifest.xml:8,22-23` |
| F7 | The release Wear runtime is read-only until the privacy/transport gate is approved. This spec does not change it. | `app/wear/src/release/.../WatchRuntimeFactory.kt:12,26,46` |
| F8 | No main source in `app/wear`, `core/wear-protocol` or `feature/wear-bridge` reads versionCode or versionName at runtime. | grep: 0 matches |
| F9 | Crashlytics custom key `platform` is `phone` or `watch`, set at application start. | `BaseApplication.kt:122`; `WearApplication.kt:18` |
| F10 | supply overwrites every text field present locally (title, short description, full description, video) and replaces every image and screenshot type present locally. It does so inside the same edit as the bundle, before the commit. The repository holds all four text files, the icon, the feature graphic, and phone, 7" and 10" screenshots. | fastlane 2.228.0 `supply/lib/supply/uploader.rb:41-48,531-533`; `fastlane/metadata/android/en-US/` |
| F11 | supply skips screenshot types that have no local files. `wearScreenshots` is a supported type in supply and in the Play API. | `supply/lib/supply/uploader.rb` `upload_screenshots` (`next unless paths.count > 0`); `supply/lib/supply.rb` `SCREENSHOT_TYPES`; API `AppImageType` |
| F12 | `google_play_track_version_codes` returns `[]` for a nonexistent track, because supply swallows the 404 "Track not found". | `supply/lib/supply/client.rb:475-489` |
| F13 | `workflow_dispatch` runs only if the workflow file exists on the default branch (`master`). A local `uses: ./.github/workflows/…` resolves at the caller's ref. | GitHub Docs, events that trigger workflows |
| F14 | Committing an edit, or any Console change, invalidates every other open edit of the app. | Play Developer API, Edits |
| F15 | `prepare_avd.py` creates isolated round Wear AVDs at 192dp or 240dp with density 320, i.e. 384 or 480 px. | `documentation/wear-emulator-acceptance/prepare_avd.py:23,48` |
| F16 | The owner added the Wear OS form factor in Play Console on 2026-09-28. | owner statement |

Assumptions that the first release run confirms (§11):

| Id | Assumption | If wrong |
|---|---|---|
| ASM-1 | `Image.sha256` returned by the Play API is the hash of the uploaded bytes. The API text only says "a sha256 hash of the image"; supply's `sync_image_upload` relies on it. | Every untouched image reports DRIFT. Stop and restrict §8 to text fields plus image counts. |
| ASM-2 | The Wear internal track id is `wear:internal`, not `wear:qa` (§4). | §7.2 fails before upload and prints the real ids. |
| ASM-3 | Play accepts `wearScreenshots` through the API now that the form factor exists. | The Wear job fails after the phone is live; the phone release is unaffected. |

## 4. Play constraints

- Wear bundles are released only on dedicated Wear OS tracks. Wear releases left on mobile tracks cannot be updated since September 2023.
- A versionCode is unique across all form factors of the app.
- Wear internal track id: the API reference names it `wear:qa`, while practitioners report `wear:qa` → "Track not found" and `wear:internal` working. The pipeline resolves this at run time (§7.2), never by assumption.
- Wear screenshots: 1:1 aspect ratio, at least 384×384 px, no device frames, no transparent backgrounds or masking, only the app interface. A Tiles screenshot is recommended.
- Wear production (out of scope) needs a test-track release, opt-in to Wear OS and review against the Wear quality guidelines. The listing must then mention "Wear OS" and Tiles.
- Target API for Wear OS is at least 35 from 2026-08-31 (targetSdk is 37). Apps with native code must ship 64-bit libraries from 2026-09-15.
- Internal testers can be managed through the API only as Google Groups. Email lists are Console-only.

## 5. Version identity (PR-A)

In `ConfigureWearApplication.kt`:

- `WEAR_VERSION_CODE_OFFSET = 1_000_000` and `versionCode = WEAR_VERSION_CODE_OFFSET + toml`.
- Configuration fails, with a message that names this spec, when `toml >= WEAR_VERSION_CODE_OFFSET`.
- `versionName = "$toml-wear"`. The dev flavor keeps `versionNameSuffix = "-dev"`, which gives `X.Y.Z-wear-dev`.

The phone identity does not change. Monotonicity comes from the existing deploy guard (release
versionCode greater than master's), because the offset is constant.
Example: release 1.52.0 with code 53 gives phone `1.52.0 (53)` and watch `1.52.0-wear (1000053)`.

## 6. Bundle identity gate (PR-A)

`.github/scripts/assert_play_bundle.py`: stdlib Python plus a pinned bundletool for the manifest
dump, with `--self-test`. Arguments: `--aab <path> --role phone|wear --toml gradle/libs.versions.toml`.
Every check prints what it read.

| Check | Rule |
|---|---|
| G1 | Exactly one existing, non-empty file at the given path. |
| G2 | The package is `io.github.stslex.workeeper`. |
| G3 | versionCode equals the TOML value (phone) or `1_000_000 +` the TOML value (wear). |
| G4 | versionName equals the TOML value (phone) or the TOML value plus `-wear` (wear). |
| G5 | Phone: no `uses-feature android.hardware.type.watch`. Wear: present and not `required="false"`. |
| G6 | Wear: meta-data `com.google.android.wearable.standalone` is `false`. |
| G7 | ABI parity: every `lib/armeabi-v7a/*.so` has the same file under `lib/arm64-v8a/`, in every module. Counts per ABI are printed; zero native libraries is a valid, reported result. |

The script exits non-zero on any failure, before anything talks to Play. bundletool is pinned in
the version catalog and resolved reproducibly, with no unpinned download. Record the mechanism in
ci-cd.md.

Wiring:

- `deploy` lane: `clean :app:store:bundleRelease`, then the gate (role phone), then
  `upload_to_play_store(aab: <that path>)`, all in the same lane run. The upload never relies on
  lane-context AAB selection (F2).
- Wear workflow: build, then the gate (role wear), then the Play steps (§7.2).
- Pull-request CI builds both release bundles, runs the gate on both real AABs, and runs the swap
  control: the Wear AAB with `--role phone` must fail. Measure the added minutes. If the `build`
  job approaches its 60-minute timeout, move this to a separate job and report it.
- Pull-request CI must not upload mapping files (the F07 rule). The phone release uploads its
  mapping unconditionally (F5), so the PR step excludes every `uploadCrashlyticsMappingFile*` task
  and proves it with a dry run in which no such task is scheduled.

Named mutations, each executed RED and then restored GREEN:

| Id | Mutation | Expected |
|---|---|---|
| M-A1 | TOML versionCode set to `1000000` | Wear configuration fails |
| M-A2 | Wear AAB checked with `--role phone` | G5 RED |
| M-A3 | Phone AAB checked with `--role wear` | G3 to G6 RED |
| M-A4 | Expected versionCode off by one | G3 RED |
| M-A5 | Self-test fixture with an `armeabi-v7a` library that has no `arm64-v8a` twin | G7 RED |
| M-A6 | PR bundle step without the mapping-upload exclusion | the dry-run assertion RED |

## 7. Wear delivery (PR-B)

### 7.1 Workflow `.github/workflows/android_deploy_wear.yml`

- `workflow_call` inputs: `ref` (pinned SHA), `version_name`, `version_code`,
  `allow_listing_overwrite` (boolean, default false).
- `workflow_dispatch` inputs: `tag` (`release-v.X.Y.Z`) and `allow_listing_overwrite`. Guard: the
  tag exists, and the TOML versionName at the tag equals the tag's version. Per F13 this path works
  only once the file is on `master`; it is the recovery path after the release PR has merged.
- Steps: checkout with full history and tags; JDK 21; Ruby 3.3 and bundle; keystore,
  `keystore.properties`, `play_config.json` and `app/store/google-services.json`; assert that each
  provisioned file is non-empty (an environment-scoped secret arrives empty and must fail loudly
  here); CI Gradle properties; `clean :app:wear:bundleStoreRelease -PcrashlyticsMappingUpload=true`,
  exactly like the phone lane builds; the bundle gate (role wear); the Play steps (§7.2).
- No `environment:` unless the Play, keystore or Firebase secrets turn out to be
  environment-scoped. The owner's approval of the phone deploy is the GO for the Wear upload of the
  same version (D1).
- At most one Wear run per version at a time; never cancel an in-progress run.

### 7.2 Play steps

Lane `deploy_wear`, plus a stdlib Python decision helper with `--self-test`.

1. Read Play state in a read-only edit that is always deleted, including on error
   (`Supply::Client#begin_edit`, `#tracks`, `#track_version_codes`, `#abort_current_edit`;
   fastlane 2.228.0 `client.rb:143,152,448,475`). Print every track id and the version codes on the
   configured Wear track.
2. Decide:
    - configured track absent: FAIL and print all track ids, because a wrong id must never pass
      silently (F12);
    - expected versionCode already on the configured track: SKIP (success, no upload);
    - otherwise: UPLOAD.
3. Run the listing drift check for Wear-owned metadata (§8).
4. `upload_to_play_store(track:, aab: <explicit path>, metadata_path: "fastlane/metadata-wear/android",
   skip_upload_metadata: true, skip_upload_images: true, skip_upload_changelogs: true,
   skip_upload_screenshots: false)`. This opens its own edit. The Wear lane never shares an edit
   with the phone lane.

The configured track id lives in one place in the repository, with the initial value
`wear:internal`. If step 2 fails on the first run, correct the id from the printed list and re-run
the Wear job.

Named mutations for the helper: M-B1 track absent → FAIL with the list; M-B2 code present → SKIP;
M-B3 code absent → UPLOAD; M-B4 empty track list → FAIL.

### 7.3 Wiring into `android_deploy_prod.yml`

- New dispatch input `allow_listing_overwrite` (boolean, default false).
- New job `deploy_wear`: `needs: [guard, deploy]`;
  `if: ${{ always() && needs.deploy.result == 'success' }}` (the same reason `deploy` uses
  `always()`: a skipped `build` or `ui_tests` must not skip it);
  `uses: ./.github/workflows/android_deploy_wear.yml`; inputs from the `guard` outputs;
  `secrets: inherit`.
- The `deploy` job keeps its order: phone upload, tag, merge. The Wear job starts after the merge,
  so a Wear failure leaves the phone release complete (§10).

### 7.4 Wear screenshots

- Location: `fastlane/metadata-wear/android/en-US/images/wearScreenshots/`, not the phone tree. A
  rejected image inside the phone edit would fail the phone release (F10).
- Capture from a 240dp API 36 round AVD created by `prepare_avd.py` in an isolated AVD home
  (480×480 px), using the store-flavored debug build driven by the existing synthetic acceptance
  scenarios. At least: the active controller, one editor or completion state, and the Tile.
  Synthetic data only; `documentation/personal_data_gate.py` must pass.
- Files: RGB PNG without alpha, square, at least 384 px, no frames or overlays, named
  `1_en-US.png`, `2_en-US.png` and so on.
- Validator `.github/scripts/assert_store_screenshots.py` (stdlib, reads the PNG IHDR) with
  `--self-test`. Pull-request CI runs it over the directory and prints the file count; zero files
  fails.
- Named mutations: M-B5 383×383 → RED; M-B6 non-square → RED; M-B7 colour type 6 (RGBA) → RED;
  M-B8 empty directory → RED.

## 8. Listing drift guard (PR-C)

Owner requirement: after Console-side setup such as the Wear form factor, fastlane must pull the
Console changes instead of overwriting them. By F10, every deploy silently reverts Console edits to
anything the repository also holds.

Three-way rule:

- base: metadata files at the latest `release-v.*` tag reachable from the deployed commit,
  excluding the tag of the version being deployed (that tag already exists when the Wear job runs).
  A path absent at base is an empty set.
- local: metadata files at the deployed commit.
- remote: Play, read in a read-only edit that is always deleted. Text comes from
  `Supply::Client#listing_for_language`; images and screenshots come from `#fetch_images` as ordered
  sha256 lists (fastlane 2.228.0 `client.rb:234,548`).
- Compared set: exactly what the lane uploads. Phone: the four text fields, the icon, the feature
  graphic, and phone, 7" and 10" screenshots, for each local language. Wear: `wearScreenshots`.
- Text normalisation: CRLF to LF, strip trailing whitespace on every line, drop trailing empty lines.
- Verdict per item: remote equals base → OK (a repository change, or none); remote equals local →
  OK; anything else → DRIFT.
- DRIFT fails before any upload. It prints text diffs and image hash lists, and publishes the remote
  state as a workflow artifact laid out like the metadata tree, so reconciliation is a copy and a
  commit.
- Override: `allow_listing_overwrite: true` turns DRIFT into a logged warning. It is never the
  default. Reader errors always fail.
- Decision logic is stdlib Python with `--self-test`. Ruby only reads Play state into JSON.
- Phone: the check runs in the `deploy` job after the bundle gate and before the upload.

First run (1.52.0): base is `release-v.1.51.0`, which has no Wear metadata. If screenshots were
uploaded in the Console during form-factor setup, the Wear job reports DRIFT after the phone is
live. Reconcile by adopting them into the repository, or dispatch the Wear workflow on the tag with
the override. A normalisation-only difference on the phone listing also surfaces once; reconcile by
adopting Play's text.

Named mutations: M-C1 remote differs from both base and local → DRIFT; M-C2 remote equals base and
local changed → OK; M-C3 remote equals local → OK; M-C4 screenshot order changed → DRIFT; M-C5
override → warning, exit 0; M-C6 zero compared items → FAIL.

## 9. Watch and phone separation in Firebase

- Crashlytics: the `platform` custom key (F9) is searchable and filterable in the console.
  versionCode (53 against 1000053) and versionName (`1.52.0` against `1.52.0-wear`) separate the
  builds.
- Analytics: App version is the versionName, so D4 separates the watch. Device category does not.
- Deferred: an Analytics `platform` user property (open item in [wear-style-mvi.md](wear-style-mvi.md)).

## 10. Recovery (to be added to release-flow.md §8)

| State | Recovery |
|---|---|
| Phone live, Wear job failed (track id, permission, drift, transient error) | Fix the cause, then use "Re-run failed jobs" in the same run. After the release PR has merged, dispatching `android_deploy_wear.yml` with the tag also works. Idempotent: a Wear code already on the track is skipped. |
| Phone deploy stopped on DRIFT (nothing uploaded) | Commit the artifact's content to the release branch and to `dev`, then re-run; or re-dispatch with `allow_listing_overwrite: true`. |
| 403 on the Wear upload | The service account lacks permission to release to testing tracks. The owner grants it, then re-runs the Wear job. |
| Wear upload rejected because the version code is already used, but not on the configured track | Stop and inspect the App bundle explorer. Never bump the TOML to get around it. |

## 11. Delivery and acceptance

PR-A → PR-B → PR-C, stacked, all merged into `dev` before the 1.52.0 cut. Every PR has bisect-green
commits, zero new detekt suppressions, gates that print their input counts, and every named mutation
executed RED and then restored GREEN. Gradle measurements use
`--rerun-tasks --no-build-cache --no-daemon`. Each PR updates the documentation for the behaviour it
changes: release-flow.md (§1 non-goals, §4, §5, §6.1, §7.2, §8, §9) and ci-cd.md (lanes, workflows,
gates).

Owner actions: add his account to the testers of the Wear internal track (Console; email lists have
no API); review and merge; cut 1.52.0; approve `production`; install the build from Play on his
watch.

Acceptance of the first run, recorded in §14:

| Id | Evidence |
|---|---|
| A1 | Phone: the gate printed the identity; the upload used the explicit path; the drift check passed. |
| A2 | Wear: the printed track list and the resolved id; code 1000053 uploaded; the mapping upload task executed. |
| A3 | Play Console shows `1.52.0-wear (1000053)` on the Wear internal track. |
| A4 | The owner installed it from Play on his watch. |
| A5 | Re-running the Wear job ends in SKIP. |
| A6 | Analytics lists App version `1.52.0-wear` (GA4 Tech details) after the first watch launch, allowing for reporting latency. |
| A7 | ASM-1 to ASM-3 confirmed or resolved. |

## 12. Out of scope

Wear production (closed track, opt-in, review, Appendix A, listing mentions) and the Phase 1
privacy gates 1 and 2 it depends on; the 12-tester requirement check; a Wear minSdk at the tested
floor (API 30); a Wear APK in GitHub Releases (a sideloaded pair must share one signature for the
Data Layer); an independent Wear version counter; the Analytics `platform` property; Wear release
notes; `android_deploy_beta.yml`, which stays disabled (Issue #102).

## 13. Sources

- Package and distribute Wear OS apps: https://developer.android.com/training/wearables/packaging
- Manage form factor releases on dedicated tracks: https://support.google.com/googleplay/android-developer/answer/13295490
- APKs and Tracks: https://developers.google.com/android-publisher/tracks
- Edits: https://developers.google.com/android-publisher/edits
- edits.tracks.create: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks/create
- edits.testers: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.testers
- AppImageType: https://developers.google.com/android-publisher/api-ref/rest/v3/AppImageType
- Add preview assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Target API level requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Wear OS 64-bit requirement: https://android-developers.googleblog.com/2026/04/get-your-wear-os-apps-ready-for-64-bit-requirement.html
- GitHub Actions events: https://docs.github.com/en/actions/writing-workflows/choosing-when-your-workflow-runs/events-that-trigger-workflows
- Crashlytics custom keys: https://firebase.google.com/docs/crashlytics/android/customize-crash-reports
- GA4 tech overview (App version): https://support.google.com/analytics/answer/13820344
- fastlane discussion #21323 (Wear track id): https://github.com/fastlane/fastlane/discussions/21323
- fastlane 2.228.0 sources: https://github.com/fastlane/fastlane/tree/2.228.0

## 14. Implementation ledger

Append-only. One row per PR and one per acceptance item.

| Date | Item | Commit or run | Evidence |
|---|---|---|---|

## Appendix A. Wear listing text (apply with the Wear production decision)

Add to `fastlane/metadata/android/en-US/full_description.txt`:

```text
⌚ Wear OS:
• Follow your active workout on the watch: exercise, set, weight and reps.
• Edit weight and reps and complete sets from your wrist.
• A Tile shows the current workout at a glance.
• Works together with the phone app.
```

Before applying it, replace the line "All data is stored locally on your device." with wording
approved under Phase 1 privacy gate 1: once transport is enabled, workout data moves between the
phone and the paired watch, and the watch keeps a bounded cache. That wording is not approved here.
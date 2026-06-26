# Feature spec — AI-readable Drive snapshot export

**Status:** locked design. Decisions below are fixed; this spec is the authoritative target
for the implementation prompt. Source-of-record for the current backup internals:
`documentation/feature-specs/drive-ai-export-audit.md`.

**One-line:** alongside the existing binary backup, write a human/LLM-readable JSON snapshot of the
full training graph to a **user-visible** Google Drive folder, refreshed on the same triggers as the
binary backup, so an external LLM (with the user's Drive access) can read and reason over the data.

---

## 1. Purpose & non-goals

**Purpose.** Produce a plaintext, nested JSON projection of the workout database in a visible Drive
folder (`Workeeper/`), so the user can point any LLM/agent at their Drive and get help with training
(analysis, programming, progression). The snapshot is a **read-only projection**, not a backup.

**Non-goals (v1):**
- **Not a recovery artifact.** Source of truth for restore stays the binary `.db` in `appDataFolder`.
  The app never reads the JSON back. Losing/rotating a JSON snapshot loses nothing recoverable.
- **No incremental/diff export.** Full current-state snapshot every run. Diff is incompatible with
  rotation and only safe as a never-rotated append-only log (same unbounded growth + reconstruction
  cost, zero benefit). Rejected.
- **No summary-tier in v1.** Full snapshot is small; an LLM computes aggregates itself. A summary
  artifact earns its keep only when `full.json` stops fitting context — revisit then (§9, §11).
- No CSV/multi-format, no per-exercise files, no snapshot encryption, no sharing UI, no reading the
  snapshot into the app.

---

## 2. Locked decisions

| # | Decision |
|---|---|
| D1 | **Scope:** `drive.file` is OPTIONAL (`appdata` stays the only `REQUIRED` gate). The requested set is **dynamic / granted-aware** (revised from the original "static" intent — see rationale): regular sign-in + silent refresh request only the *already-granted* set (base, plus `drive.file` only if previously granted); `drive.file` is requested solely via `BackupAuth.requestDriveFileAccess()` (the explicit toggle grant). **Rationale:** GMS `AuthorizationClient.authorize()` raises a resolution (no silent token) on *any* ungranted requested scope, so a static `ALL`-includes-`drive.file` set would break silent token refresh for every appdata-only user (all existing users + anyone who declines the optional scope). Export is gated on the actual grant — `driveFileGranted`, re-derived from `AuthorizationResult.getGrantedScopes()` on **every** authorize — AND the user toggle. |
| D2 | **Coupling:** the exporter is fully independent and **best-effort**. It must never block, delay, or fail the binary backup. All exporter/upload errors are swallowed (logged + Crashlytics non-fatal). |
| D3 | **Drive client:** make `DriveApi` **space-aware** (parameterize `spaces` on list, `parents` on upload). Add a sibling `DriveSnapshotStorage` next to `DriveBackupStorage`. The binary path keeps calling with `appDataFolder` — zero behavior change on the path that actually protects user data. |
| D4 | **Placement:** JSON production lives in `core/data/database` (`DatabaseJsonExporter`, reads the 9 DAOs → encodes). Upload lives in `core/data/backup/google-drive`. Export DTOs (`@Serializable`) live in the **data layer**, never domain (Detekt `DomainLayerPurityRule`). The exporter must **not** be added to `feature/recovery` (it reads the DB → would trip `RecoveryActivityDbFreeTest`). |
| D5 | **JSON shape:** nested-denormalized — `trainings[] → exercises[] (plan) + sessions[] → performedExercises[] → sets[]`; a flat `exercises[]` library to avoid wholesale duplication, with denormalized `exerciseName` on references for LLM convenience. |
| D6 | **Inclusion-set:** **everything, full fidelity** — archived **and** adhoc included, no filters. (Adhoc inclusion is also required for referential integrity: sessions reference adhoc exercises.) |
| D7 | **Export schema version is independent of Room version.** Envelope carries its own `schemaVersion` (start at `1`); it is bumped when the JSON contract changes, not when `APP_DATABASE_VERSION` bumps. |
| D8 | **Folder:** create-or-lookup a visible `Workeeper/` folder (by name, `trashed=false`), cache its id, dedup if multiple exist (pick oldest by `createdTime`), recreate if the cached id 404s. |
| D9 | **Rotation:** reuse the existing `RotationPolicy.refsToDelete`, cap at `MAX_BACKUPS = 3` (mirror the binary path), scoped to the visible folder. Upload-then-rotate (never a zero-file window). No temp→rename needed on Drive — `files.create` is atomic for readers. |
| D10 | **Triggers:** run on the **same two trigger points** as the binary backup — auto (`BackupWorker`: periodic + bootstrap one-time) and manual ("Create backup"). Always-fresh with the binary backup, no second schedule. Gated by toggle + `drive.file` grant, best-effort. |

---

## 3. Data contract — the snapshot JSON

Envelope (timestamps are UTC ISO-8601, converted from the DB's epoch-millis; the LLM gets unambiguous
UTC):

```jsonc
{
  "schemaVersion": 1,                       // export-own version (D7), independent of Room v6
  "exportedAt": "2026-06-26T10:42:23Z",
  "source": {
    "appVersion": "…",                      // reuse manifest fields (ManifestPropertiesMapper)
    "dbSchemaVersion": 6,
    "deviceModel": "…"                      // truncated to 100 chars, as in the binary manifest
  },
  "exercises": [                            // library — flat, canonical metadata lives once here
    {
      "uuid": "…",
      "name": "…",
      "type": "WEIGHTED",                   // WEIGHTED | WEIGHTLESS
      "description": "…",                   // nullable → omit when null
      "isAdhoc": false,
      "archived": false,
      "createdAt": "2026-01-04T08:00:00Z",
      "archivedAt": null,                   // omit when null
      "tags": ["push", "barbell"]           // denormalized tag names
      // imagePath intentionally OMITTED (device-local path, no value to an LLM; §7)
      // lastAdhocSets decoded under the exercise only if present (adhoc scaffolding)
    }
  ],
  "trainings": [
    {
      "uuid": "…",
      "name": "…",
      "description": "…",                   // omit when null
      "isAdhoc": false,
      "archived": false,
      "createdAt": "2026-01-04T08:00:00Z",
      "archivedAt": null,                   // omit when null
      "tags": ["legs"],
      "plan": [                             // from training_exercise_table, ordered by position
        {
          "exerciseUuid": "…",
          "exerciseName": "Back Squat",     // denormalized for convenience
          "position": 0,
          "planSets": [                     // decoded from the stored List<PlanSetDataModel> JSON
            { "reps": 5, "weight": 100.0, "type": "WORK" }
          ]
        }
      ],
      "sessions": [                         // workout history for this training
        {
          "uuid": "…",
          "state": "FINISHED",              // IN_PROGRESS | FINISHED
          "startedAt": "2026-01-06T18:30:00Z",
          "finishedAt": "2026-01-06T19:25:00Z",  // omit when null
          "performedExercises": [
            {
              "exerciseUuid": "…",
              "exerciseName": "Back Squat",
              "position": 0,
              "skipped": false,
              "sets": [                     // ordered by position
                { "position": 0, "reps": 5, "weight": 100.0, "type": "WORK" }
                // type ∈ WARM | WORK | FAIL | DROP ; weight nullable (WEIGHTLESS) → omit when null
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

Contract rules:
- **UUIDs kept** on all rows (canonical join key) even though names are denormalized — unambiguous.
- **Nullable fields omitted** when null (smaller, less noisy) — `encodeDefaults`/explicit-null policy
  to be set on the `Json` instance; document the chosen convention in code.
- **planSets / lastAdhocSets** are decoded from their stored column JSON into structured objects, not
  passed through as raw strings (reuse `PlanSetDataModel` / `SetTypeDataModel` to decode, re-map to
  export DTOs).
- **Ordering** is explicit by `position` for plan/performed/sets; sessions by `startedAt`.

---

## 4. Architecture & components

### 4.1 `core/data/database` — JSON production
- **Export DTOs** (`@Serializable`), package `…/export/model/`: `WorkoutExportDto` (envelope),
  `ExerciseExportDto`, `TrainingExportDto`, `PlanExerciseExportDto`, `PlanSetExportDto`,
  `SessionExportDto`, `PerformedExerciseExportDto`, `SetExportDto`. Data-layer only (Detekt).
- **`DatabaseJsonExporter` / `…Impl`** (`@Singleton`), package `…/export/`: reads all 9 DAOs, assembles
  the nested DTO graph, encodes via `kotlinx.serialization.json`. Returns the JSON as `String`/bytes;
  it does **not** know about Drive.
- **Mapper** object(s), package `…/export/mapper/`: entity/DataModel → export DTO. No inline mapping
  (repo convention). Timestamp epochMs→UTC ISO conversion lives here.
- **Unfiltered reads — CORRECTNESS TRAP (hard requirement).** Existing exercise list queries filter
  `is_adhoc = 0`. Reusing them would silently drop adhoc rows, violating D6. The exporter MUST use
  unfiltered `SELECT *` reads per entity (add new DAO methods if absent). A unit test must assert
  adhoc + archived rows are present in the output.

### 4.2 `core/data/backup/google-drive` — visible-folder upload
- **`DriveApi` made space-aware:** list takes a `spaces` arg; upload takes a `parents` arg. Binary
  callers pass `appDataFolder` (unchanged); snapshot caller passes the `Workeeper/` folder id and
  `spaces=drive`.
- **`DriveSnapshotStorage`** (`@Singleton`, sibling of `DriveBackupStorage`):
  - **Folder management (D8):** `files.list` with
    `q = "mimeType='application/vnd.google-apps.folder' and name='Workeeper' and trashed=false"`,
    `spaces=drive`. With `drive.file` the app sees only files it created, so it finds/creates its own
    folder. Create if absent; cache id in a new DataStore key; on duplicate, pick oldest by
    `createdTime`; on cached-id 404, recreate and retry once.
  - **Upload:** JSON bytes, `mimeType = "application/json"`, `parents = [folderId]`,
    name `workeeper_export_<epochMs>.json`.
  - **Rotation (D9):** reuse `RotationPolicy.refsToDelete` (cap 3), run after a successful upload,
    best-effort.
- **Scope (D1) — dynamic, granted-aware** (the requested set is NOT static; see D1 rationale):
  `DriveAuthScopes.ALL` is the base set (`drive.appdata` + the two `userinfo` scopes, unchanged from
  v1); `ALL_WITH_DRIVE_FILE` = base + `drive.file`.
  - Regular `signIn()` requests `ALL` only (no `drive.file` prompt at first sign-in).
  - Silent refresh (`DriveAuthTokenProvider`) requests only the **already-granted** set: `ALL`, plus
    `drive.file` only when the persisted `driveFileGranted` flag is set. An appdata-only user therefore
    requests exactly `ALL` — byte-identical to v1 — so `authorize()` never raises a resolution for them
    (the no-regression invariant, by construction). An empty/unset flag defaults to `false` ⇒ base set.
  - `drive.file` is requested **solely** by `BackupAuth.requestDriveFileAccess()` (`ALL_WITH_DRIVE_FILE`),
    used by the explicit AI-export toggle grant.
  - `driveFileGranted` is re-derived from `getGrantedScopes()` on **every** authorize (sign-in, explicit
    grant, silent refresh) and persisted in `AccountDataStore`, so a later revocation flips it off; the
    next silent refresh requests `drive.file` once, gets no token (resolution — never surfaced to the UI
    on this background path), re-derives the flag to `false`, and silently retries with base scopes so
    the binary token survives. `BackupAuth.observeDriveFileGranted()` exposes the flag to the toggle/runner.

### 4.3 `core/data/backup/api` — contracts & constants
- New constants: folder name `"Workeeper"`, snapshot prefix `"workeeper_export_"`, suffix `".json"`,
  snapshot cap (reuse `MAX_BACKUPS = 3`). **Do not repurpose** the dead `MANIFEST_FILE_SUFFIX`
  constant — different concern; leave/remove separately.
- New api interface `SnapshotStorage` (mirrors `BackupStorage`) so orchestration depends on api, not
  the Drive impl (api/impl split per project convention).

### 4.4 Orchestration seam — `SnapshotExportRunner`
- **`SnapshotExportRunner` / `…Impl`** (`@Singleton`, data layer), one suspend method
  `runIfEligible()`:
  1. toggle on? else no-op.
  2. `drive.file` granted? else no-op (and, if toggle is on but grant missing, set a one-time
     "grant needed" signal — see §5 decline handling).
  3. `DatabaseJsonExporter` → JSON bytes.
  4. `DriveSnapshotStorage` → upload → rotate.
  - **Entire body wrapped so it never throws to the caller.** Failures → log + Crashlytics non-fatal,
    return `Unit` (D2).
- **Wiring (D10):** both `BackupInteractorImpl` (manual) and `BackupWorker` (auto) call
  `snapshotExportRunner.runIfEligible()` **after** the binary backup step. Invocation is independent of
  binary outcome but internally no-ops without auth/toggle. The binary `Result`/error path is
  unaffected regardless of what the runner does.

### 4.5 Hilt & Detekt compliance
- All new data-layer singletons `@InstallIn(SingletonComponent::class)` `@Singleton`, matching the
  existing Drive graph. `HiltScopeRule` forces `@Singleton` for `*Storage`; confirm it does not reject
  the `Exporter`/`Runner` suffixes (unlisted suffixes are unconstrained) — if it does, rename to a
  compliant suffix.
- New `*Handler` (if any, §5) needs `@Inject` primary ctor with ≥1 param implementing `Handler`
  (`MviHandlerConstructorRule`). No suppressions anywhere; empirical `detekt` run required per phase.

---

## 5. Settings / MVI surface

**Canonical navigation pattern (invariant — stated per project rule):** navigation uses
`Action.Navigation` consumed by a dedicated `NavigationHandler` with `Navigator` injected via Hilt;
graph composables consume only UI events (Haptic, ShowExternalLink, BackHandler). Canonical reference:
`feature/home` `NavigationHandler` + graph. **This feature needs no new navigation** — no screen
change, no `NavCommand`. The consent step reuses the existing auth-resolution **Event** plumbing, not
navigation.

- **Toggle:** "Export AI-readable snapshot to Google Drive" in `feature/settings`, off by default.
- **Persistence:** `BackupPreferencesRepository` gains `aiExportEnabled: Boolean` (DataStore).
- **State:** a `Boolean` field on the settings State; `@Stable`/`@Immutable` UI model; toggle row is a
  stateless kit composable; any display strings pre-formatted in mapper/handler.
- **Actions / consent reuse:** add `Action.Backup.ToggleAiExport(enabled)`. On enable, if `drive.file`
  not granted → trigger incremental `authorize()` requesting the static `ALL` set; if `NeedsResolution`
  → emit the **existing** `Event.AuthResolutionRequested(intentSender)` → `SettingsGraph` launches via
  the existing `StartIntentSenderForResult` launcher → result fed back as the existing
  `Action.Backup.HandleAuthResult`. Minimal new surface.
- **Handler:** fold the new action into the existing `BackupClickHandler` (it already owns backup +
  auth-result actions; action surface here is small). Split only if it grows.
- **Decline handling:** if the incremental grant is declined → revert the toggle to **off** and show a
  snackbar ("Google Drive access is needed for AI export"). Never leave a toggle that reads "on" but
  does nothing.

---

## 6. Failure modes & edge cases

- **Grant declined** → toggle reverts off + snackbar (§5).
- **`drive.file` later revoked** (in Google account) → snapshot upload 401 → `DriveAuthPlugin` maps to
  `AuthRevoked` → swallowed for the snapshot (best-effort). The binary path's existing `AuthRevoked`
  handling (cancel periodic + notification) governs user-facing behavior; **do not double-notify** from
  the snapshot path.
- **Folder trashed/deleted by user** → cached id 404 on upload → recreate folder, retry once.
- **Two devices, same account** → device B's `drive.file` list sees A's app-created folder (same OAuth
  client) → dedup by oldest `createdTime`; tolerate pre-existing duplicates (optionally clean extras
  best-effort).
- **Partial/corrupt upload** → Drive multipart `files.create` is atomic for readers (object appears
  only on success); rotation runs only after success → no half-file ever visible. No temp→rename
  needed.
- **Concurrent manual + auto** → snapshot files are `epochMs`-named, so two near-simultaneous runs make
  two files; rotation trims to 3. Acceptable.
- **Empty DB** (new user, toggle on) → emit a valid envelope with empty arrays; never crash.
- **Large-DB tail** → size grows unbounded with history; the deferred summary-tier is the lever.
  Revisit when `full.json` exceeds ~1–2 MB or causes context pressure (§9).
- **Schema drift** → bump the export `schemaVersion` (D7) only when the JSON contract changes.

## 7. Security & privacy

- **The snapshot is plaintext workout data in the user's *visible* Drive** — shareable, and readable by
  any app/integration the user authorizes on their Drive. This is the intended behavior, but the toggle
  copy MUST disclose it plainly (it is **not** the hidden `appDataFolder`; it is visible and shareable).
- **`drive.file` is a "sensitive" (not "restricted") scope** — lighter Google verification than full
  `drive`, but the OAuth consent screen must list it; unverified/testing apps show the unverified-app
  warning. **Verify the OAuth consent-screen / `google-services.json` config supports adding
  `drive.file` before shipping** (the audit did not inspect the client config — open item §10).
- **`imagePath` omitted** from the export — a device-local path string, useless to an LLM and a minor
  leak; drop it.
- No new `INTERNET` permission needed (already transitively merged — audit-confirmed).

## 8. Testing

Per repo conventions (pure JVM JUnit5 where possible; in-memory Room for repository/DB tests;
real read-then-assert, not mock-only; all custom Detekt rules green with no suppressions; each commit
independently green incl Detekt — bisect property).

- **Exporter (in-memory Room):** seed a full graph — archived + adhoc rows, multiple sessions/sets/tags,
  planSets, WEIGHTLESS (null weight). Assert JSON structure **and** that archived + adhoc rows are
  present (regression guard against the `is_adhoc = 0` trap, §4.1).
- **Serialization round-trip:** encode export DTO → decode → equals, covering all four enums
  (`ExerciseType`, `SessionState`, `SetType`, plan-set type) to catch serialName/R8 issues.
- **`DriveSnapshotStorage` (mock `DriveApi`):** folder absent→creates; present→reuses id; cached-id
  404→recreates; duplicates→picks oldest. Rotation keeps 3 and runs after upload.
- **Decoupling invariant (critical):** `SnapshotExportRunner` swallows exporter/upload exceptions and
  never propagates — assert the binary backup `Result` is unaffected when the runner throws (D2).
- **Toggle/handler (JVM):** enable with grant missing → incremental-auth path (`NeedsResolution` →
  Event emitted); decline → toggle off + snackbar.
- **Placement guard:** the exporter is not referenced from `feature/recovery` (keep
  `RecoveryActivityDbFreeTest` green).

### Manual release gates (cannot be unit-tested; must pass before ship)

1. **OAuth consent screen / verification (§7, §10.3).** `drive.file` is added to the app's OAuth
   consent screen in Google Cloud Console; verification posture is acceptable for a "sensitive" scope.
   Not determinable from source.
2. **Appdata-only silent refresh stays resolution-free.** On a device signed in *without* `drive.file`
   (the existing-user / v1-migration state), confirm the binary backup's silent token refresh succeeds
   with no resolution prompt after the scope addition. The granted-aware design makes this safe by
   construction (the request is byte-identical to v1), but verify empirically — the binary backup must
   not regress for existing users.
3. **Snapshot smoke.** Enable the toggle → grant `drive.file` → trigger a backup → find
   `Workeeper/workeeper_export_<epochMs>.json` in visible Drive, open it, and verify the JSON structure.
4. **R8 release build (§10.4).** Confirm the `@Serializable` export DTOs + enums survive minification
   (no obfuscated-`serialName` decode failures) in a `store` release build.

## 9. Out of scope (v1) / deferred

Summary-tier (`summary.json` aggregates) · diff/incremental export · reading the snapshot back into the
app · CSV/other formats · per-exercise files · snapshot encryption · sharing UI. Summary-tier is the
first thing to revisit when `full.json` stops fitting an LLM context.

**Size levers for the large-DB tail (§6), in order of cheapness.** (1) Drop `prettyPrint`: the
exporter pretty-prints for human-readability, so compacting roughly halves the whitespace overhead at
zero data loss — the cheapest first lever. (2) Then introduce the deferred summary-tier.

## 10. Open items to confirm in discovery (CC, Phase 0)

1. Unfiltered per-entity DAO read methods exist (or must be added) — required for D6 (§4.1).
2. Exact shape of `PlanSetDataModel` / `SetTypeDataModel` for decoding `planSets` / `lastAdhocSets`.
3. OAuth consent-screen / `google-services.json` supports adding `drive.file`; app verification posture
   (§7).
4. ProGuard/R8 keep rules: location + add a keep for `@Serializable` export DTOs and the four enums
   (known repo failure mode — obfuscated serialNames). Hard requirement before release.
5. `AuthorizationResult.getGrantedScopes()` available in the GMS version on the classpath (to gate on
   the actual grant, D1).
6. `HiltScopeRule` behavior for `Exporter` / `Runner` suffixes (rename to a compliant suffix if the
   rule constrains them).

## 11. Phasing (STOP gates between phases; bisectable; Detekt green per phase)

- **Phase 0 — discovery + commit plan (no production code).** Resolve §10 items against source; map
  every break/insertion point; produce the commit plan. STOP, report, wait for approval.
- **Phase 1 — data layer only.** Export DTOs + `DatabaseJsonExporter` + mapper + unfiltered DAO reads +
  unit/round-trip tests. No Drive, no toggle. Green.
- **Phase 2 — Drive plumbing.** `DriveApi` space-aware + `DriveSnapshotStorage` (folder mgmt +
  rotation) + tests (mock `DriveApi`). Binary path untouched + regression-tested. Green.
- **Phase 3 — scope + seam.** `drive.file` optional + grant-gating + `SnapshotExportRunner` wired into
  `BackupInteractorImpl` + `BackupWorker`, best-effort + decoupling test. Green.
- **Phase 4 — settings + release-readiness.** Toggle (MVI surface, handler, consent copy) + ProGuard
  keep rules + final Detekt + end-to-end check. Green.

## 12. References

- Audit (source-of-record): `documentation/feature-specs/drive-ai-export-audit.md`
- Binary storage + rotation: `core/data/backup/google-drive/.../storage/DriveBackupStorage.kt`,
  `…/storage/RotationPolicy.kt`
- Drive REST client: `core/data/backup/google-drive/.../network/DriveApiImpl.kt`,
  `…/network/DriveDtos.kt`
- Scopes + auth: `core/data/backup/google-drive/.../auth/DriveAuthScopes.kt`,
  `…/auth/DriveBackupAuth.kt`, `…/auth/DriveAuthTokenProvider.kt`
- DB snapshot (binary) + entities: `core/data/database/.../snapshot/DatabaseSnapshotProviderImpl.kt`,
  `core/data/database/.../AppDatabase.kt`, `…/migration/MigrationsRegistry.kt`
- Manifest fields: `core/data/backup/google-drive/.../manifest/ManifestPropertiesMapper.kt`
- Triggers: `feature/settings/.../mvi/handler/BackupClickHandler.kt`,
  `feature/settings/.../domain/BackupInteractorImpl.kt`,
  `core/data/backup/worker/.../BackupWorker.kt`
- Canonical MVI/NavigationHandler reference: `feature/home/.../mvi/handler/NavigationHandler.kt`
- Constants: `core/data/backup/api/.../BackupConstants.kt`
- Detekt rules: `lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/`

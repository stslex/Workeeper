# Data needs

This document is the synthesis of what the v1 feature set requires
from the data layer: which entities exist, which queries each screen
issues, which indexes are mandatory, and which denormalizations save
the application from naïve N+1 access patterns.

It is the input to `db-redesign.md` (next stage). Where details are
missing here, the redesign document fills them in with concrete
schema, types, and migration plan.

For the conceptual domain, see [product.md](product.md). For where
each query is consumed, see [ux-architecture.md](ux-architecture.md).

## Entities required for v1

Eight entities. The current schema (Room version 2) has four entities
and conflates several roles; the redesign in v3 will introduce the
missing ones and split the existing ones along the lines below.

| Entity | Purpose | Relation summary |
|---|---|---|
| `training` | Both reusable templates and ad-hoc trainings | one-to-many sessions; many-to-many tags |
| `exercise` | Reusable exercise template | many-to-many tags; many-to-many trainings (via training_exercise) |
| `training_exercise` | Ordered membership of exercises in a training | join table with `position` |
| `session` | A performed workout instance | belongs-to training; one-to-many performed_exercise |
| `performed_exercise` | Per-session record of one exercise | belongs-to session; refers-to exercise |
| `set` | Single set within a performed exercise | belongs-to performed_exercise |
| `tag` | Free-form label, shared pool | many-to-many exercises; many-to-many trainings |
| `exercise_tag` / `training_tag` | Join tables for tags | — |

**Key change vs current schema:** `training` no longer stores a
`List<Uuid>` of exercise ids; `exercise` no longer stores a JSON blob
of sets and a `List<String>` of labels. All collections are
normalized into proper tables with foreign keys and join tables.

### New columns required by closed product decisions

- `training.archived: Boolean` (default false) — supports the archive
  system (v1 feature 13).
- `training.is_adhoc: Boolean` (default false) — separates library
  templates from on-the-fly trainings.
- `training.description: String?` — v1 feature 11.
- `exercise.archived: Boolean` (default false) — archive system.
- `exercise.description: String?` — v1 feature 11.
- `exercise.image_path: String?` — reserved for v1.5 feature 14.
- `session.state: enum (in_progress, finished)` — supports drafts /
  resume, plus prev-set lookup filter.
- `session.started_at: Long`, `session.finished_at: Long?` —
  timestamps for ordering and history.
- `performed_exercise.skipped: Boolean` (default false) — supports
  three-state UX (pending / done / skipped).
- `performed_exercise.position: Int` — preserves the order from the
  parent training at session start.
- `set.position: Int` — preserves order within a performed exercise.

## Per-feature data needs

Thirteen v1 features. For each: what it reads, what it writes, what
is critical for performance.

### 1. Trainings library

- Reads: paged list of `training` where `is_adhoc = false AND
  archived = false`, optionally filtered by tag set, with name and
  exercise count.
- Writes: insert / update / archive (set `archived = true`) on
  `training` and its `training_exercise` rows.
- Critical: filter-by-tag must not load all trainings then filter in
  memory — SQL-level join through `training_tag`.

### 2. Exercises library

- Reads: paged list of `exercise` where `archived = false`,
  optionally filtered by tag set.
- Writes: insert / update / archive on `exercise`. **Archive blocked**
  when the exercise is used in any non-archived training — requires
  a validation query (`SELECT count(*) FROM training_exercise te JOIN
  training t ON te.training_id = t.id WHERE te.exercise_id = ? AND
  t.archived = false AND t.is_adhoc = false`).

### 3. Live workout

The most data-intensive screen.

- Reads on entry:
  - `session` row (the in-progress one being resumed, or the new one
    just created).
  - For a new session: snapshot of the parent training's exercise
    list (from `training_exercise` ordered by `position`) — copied
    into newly-created `performed_exercise` rows.
  - For all exercises in the session: **previous-set values** from
    the most recent finished set of that exercise.
- Writes during workout:
  - On session creation (Start training / Track now): insert
    `session` plus N `performed_exercise` rows (one per training
    exercise, all with `skipped = false`, `position` from training
    order).
  - Insert / update / delete `set` rows as the user enters them.
  - Update `performed_exercise.skipped = true` on explicit skip.
  - Update `session.state = 'finished'` and stamp `finished_at` on
    finish.
- Critical:
  - **Prev-set lookup** uses the SQL approach (decision: SQL with
    indexes, no denormalized cache):
    ```
    SELECT s.weight, s.reps
    FROM set s
    JOIN performed_exercise pe ON s.performed_exercise_id = pe.id
    JOIN session sn ON pe.session_id = sn.id
    WHERE pe.exercise_id = :exerciseId
      AND sn.state = 'finished'
    ORDER BY sn.finished_at DESC, s.position DESC
    LIMIT 1
    ```
    Required indexes are listed below.
  - Set writes should not block the UI (suspend functions on IO
    dispatcher).

### 4. Past session detail

- Reads: full `session` with all `performed_exercise` rows joined to
  `exercise` (for current name) and all `set` rows.
- Writes (edit case): update / delete `set` rows on a finished
  session. Edit must not change `session.state`.
- Critical: one query with a proper join to avoid N+1.

### 5. Home dashboard

- Reads:
  - At most one `session` with `state = 'in_progress'` — the active
    session card.
  - Top N (5–10) finished sessions ordered by `finished_at DESC`,
    with parent training name pre-joined.
- Writes: none.
- Critical: in-progress session lookup is the most frequent query in
  the app — needs a partial / filtered index on `session.state`.

### 6. Settings (basic)

No DB needs in v1. Theme preference goes to DataStore (already wired
in `core/dataStore`).

### 7. Previous-set hints

Covered under Live workout (3).

### 8. Tags

- Reads:
  - Full tag list (T1) for picker / autocomplete.
  - `tag` rows by `name LIKE '?%'` for typeahead (T2).
- Writes:
  - Insert tag (inline creation when user types a new name).
  - Insert / delete `exercise_tag` and `training_tag` rows on
    edit.
- Critical: `tag.name` must be unique (case-insensitive) so inline
  creation is idempotent.

### 9. Resume in-progress session

Covered under Home dashboard (5) for surfacing, and Live workout (3)
for resuming.

### 10. Theme

DataStore key only.

### 11. Description fields

`description: String?` columns on `training` and `exercise`. No query
implications.

### 12. Edit past session

Covered under (4).

### 13. Archive system

- Reads:
  - Archive screen lists `training WHERE archived = true` and
    `exercise WHERE archived = true`.
- Writes:
  - Archive: `UPDATE training SET archived = true WHERE id = ?` (or
    same on exercise).
  - Restore: `UPDATE training SET archived = false WHERE id = ?`
    (same on exercise).
  - Permanent delete: `DELETE FROM training WHERE id = ?` (cascades
    by FK rules to history). Same for exercise.
- Critical:
  - Archive validation query for exercises (see feature 2).
  - All library queries must include `archived = false` in their
    `WHERE`.
  - Permanent delete must cascade through all dependent rows
    (`session`, `performed_exercise`, `set`, join tables) without
    leaving orphans. Cascade rules detailed in db-redesign.

## Query catalog

Aggregated across features. Intended as the input list for DAOs.

### List queries (paged)

- L1. `exercise` paged where `archived = false`, ordered by name.
- L2. `exercise` paged with `archived = false AND tag_id IN (?)`.
- L3. `training` paged where `is_adhoc = false AND archived = false`,
  ordered by name.
- L4. `training` paged, same filter as L3 plus `tag_id IN (?)`.
- L5. `session` where `state = 'finished'`, ordered by
  `finished_at DESC` (limit for Home, paged for Training detail).
- L6. `training` where `archived = true` (Archive screen).
- L7. `exercise` where `archived = true` (Archive screen).

### Single-row lookups

- S1. `session` where `state = 'in_progress'` LIMIT 1.
- S2. `training` by id (with `training_exercise` joined for ordered
  exercise list).
- S3. `exercise` by id.
- S4. `session` by id with `performed_exercise` (joined to
  `exercise` for current name) and `set` rows.
- S5. `session` rows where `training_id = ?` and
  `state = 'finished'`, ordered by `finished_at DESC`.

### Tag queries

- T1. All `tag` rows.
- T2. `tag` rows by `name LIKE '?%'`.
- T3. `tag` rows applied to a given exercise / training (typically
  fetched via join in S2 / S3).

### Performance-critical

- P1. **Prev-set lookup** (SQL approach, see feature 3).
- P2. **In-progress session lookup** (S1).
- P3. **Archive-blocked check** for exercise (feature 2 validation
  query).

### Mutations

- M1. CRUD on `training`, `exercise`, `tag`.
- M2. CRUD on `training_exercise`, `exercise_tag`, `training_tag`.
- M3. Session state transition `in_progress → finished` (one row
  update with `finished_at` stamp).
- M4. Set CRUD within `performed_exercise`.
- M5. Performed-exercise skip flag toggle.
- M6. Archive flag toggle (archive / restore).
- M7. Permanent delete (with cascade) on archived rows.

## Required indexes

Mandatory for v1 to perform acceptably with a few hundred sessions.
Final names and column orders in db-redesign.

- `session(state)` — partial / filtered if Room/SQLite supports it,
  otherwise plain. Supports S1, P2.
- `session(finished_at DESC)` — supports L5, Home recent.
- `session(training_id, finished_at DESC)` — supports S5.
- `performed_exercise(session_id)` — FK index, supports S4 join.
- `performed_exercise(exercise_id)` — supports prev-set lookup
  (P1) and per-exercise history.
- `set(performed_exercise_id)` — FK index, supports S4 join and
  set listing.
- `set(performed_exercise_id, position)` — supports ordered set
  retrieval and "last set" lookups.
- `training(is_adhoc, archived, name)` — supports L3 with name-ordered
  pagination after the ad-hoc and archived filters.
- `exercise(archived, name)` — supports L1.
- `tag(name)` — UNIQUE, case-insensitive, supports T2 and inline
  tag creation idempotency.
- `exercise_tag(exercise_id, tag_id)` and
  `training_tag(training_id, tag_id)` — composite primary keys
  on the join tables.
- `exercise_tag(tag_id, exercise_id)` and
  `training_tag(tag_id, training_id)` — reverse-direction index for
  tag-filter queries (L2, L4).
- `training_exercise(exercise_id)` — supports archive-blocked check
  (P3) and "which trainings use this exercise" lookups.

## Cross-cutting performance considerations

- **No N+1 reads.** Library lists, session detail, and
  prev-set-hint must each resolve in O(1) round trips. The
  ORM-friendly path is `@Relation` in Room or explicit `JOIN` in
  `@Query`.
- **Paging-friendly queries.** Library lists must be `PagingSource`
  / `PagingData`-compatible. Filter-by-tag must compose with paging
  at SQL level.
- **Mutation-heavy live workout.** Set inserts / updates during a
  live workout must not block the UI. The repository / DAO layer
  exposes suspend functions backed by IO dispatchers.
- **Tag autocomplete.** Inline tag creation typing path needs T2 to
  be sub-100ms.
- **Migration data preservation.** Existing users' data
  (exercises, trainings, sets) must survive the v3 migration.
  Schema is being restructured; the migration cannot use
  `fallbackToDestructiveMigration`. Migration plan in
  db-redesign.md.

## Closed decisions (reference)

These were resolved during data-needs synthesis. They drive the
schema in db-redesign.

- **Prev-set lookup approach.** SQL with composite indexes (no
  denormalized cache).
- **Session ↔ training link.** Hybrid: snapshot for the exercise
  list (`performed_exercise` rows fixed at session start), live link
  for the parent training reference and for each
  `performed_exercise.exercise_id` reference (current name and
  type follow the template).
- **Performed-exercise creation timing.** Upfront — created at
  session start, one row per training exercise, with `skipped =
  false` initially. Three-state UX (pending / done / skipped) is
  derived from `(skipped, count(sets))`.
- **Delete semantics.** Two-stage. Library "Delete" toggles
  `archived = true` (soft delete). Permanent delete is only from
  the Archive screen, with cascade through all dependent rows.
- **Archive blocked for exercises in active templates.** Validation
  query before archive.

## Open questions for db-redesign

- **Cascade rules** at every foreign key: `performed_exercise →
  session`, `set → performed_exercise`, join tables → parents.
  Default: cascade where the child has no independent meaning.
- **At-most-one-active-session enforcement.** Partial unique index on
  `state` vs application-level invariant in repository, or both.
  Default: partial unique index (where supported by Room) plus
  repository invariant as defence in depth.
- **Tag name case-insensitivity.** SQLite collation or normalization
  on insert. Default: store as the user typed, compare via `COLLATE
  NOCASE` index.
- **Schema migration strategy.** Step-by-step plan to migrate
  existing v2 data into the new structure: split sets out of
  `ExerciseEntity`, normalize labels into `tag` plus join tables,
  promote each historic `ExerciseEntity` row to either a
  `performed_exercise` row in a synthetic session or to a session
  itself.

## Summary of changes vs current schema

| Concern | Current (v2) | New (v3) |
|---|---|---|
| Sets storage | JSON blob in `ExerciseEntity` | Normalized `set` rows with FK to `performed_exercise` |
| Tag storage | `List<String>` JSON column on `ExerciseEntity` and `TrainingEntity`; separate `training_labels_table` not FK-linked | Normalized `tag` table + `exercise_tag` / `training_tag` join tables with FKs |
| Training → exercise link | `List<Uuid>` JSON column on `TrainingEntity` | Normalized `training_exercise` join table with `position` |
| Session concept | Implicit (each `ExerciseEntity` row is essentially a log) | Explicit `session` entity with state and `performed_exercise` children |
| Standalone tracking | `ExerciseEntity` with nullable `training_uuid` | Always has a parent training; standalone is an `is_adhoc = true` training with one exercise |
| Foreign keys | None on entities | Full FK declarations with cascade rules per the open question |
| Indexes | Effectively absent | As listed in the index section |
| Soft delete | Absent | `archived: Boolean` on `training` and `exercise` |
| Description | Absent | `description: String?` on `training` and `exercise` |
| Image path | Absent | Reserved for v1.5: `image_path: String?` on `exercise` |
| Skipped exercises | Absent | `performed_exercise.skipped: Boolean` |

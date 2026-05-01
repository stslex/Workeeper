# Workeeper v2 — Stage decomposition

This document captures the staged plan for the v2 release cycle. Stages are
ordered by dependency graph: foundational changes first, features that
generate data before features that surface it, tech debt ratchet last.

Each stage corresponds to a single PR (or a tightly grouped pair if PR size
forces split). Stages are independent unless explicitly noted.

## Status legend

- **SHIPPED** — merged to `master`, on Play Store.
- **IN REVIEW** — PR open, under review.
- **PLANNED** — spec exists or in draft.
- **TENTATIVE** — included in plan, not yet specced.

---

## v2.0 — Foundations *(SHIPPED)*

Single-PR foundation enabling subsequent feature work without rework.

- `finishSession` single-transaction fix.
- Aggregation infrastructure: PR query, volume query, history-by-exercise
  paged query — shared DAO surface for v2.1 and v2.4 consumers.
- `NavigationHandler` canonical migration in `feature/exercise` and
  `feature/all-exercises`.
- All v2 high-priority open product questions locked.

---

## v2.1 — PR tracking *(SHIPPED)*

Personal record visibility across the app.

- Per-exercise PR block on Exercise detail screen.
- In-moment PR detection + highlight (left-accent-strip + badge) in Live
  workout.
- Session summary: "New PRs achieved during the session" extending finish
  dialog.
- PR metric: heaviest set (`weight DESC, reps DESC, finished_at ASC`;
  weightless: `reps DESC`).
- PR snapshot frozen at session load via `firstOrNull()` on
  `observePersonalRecords`.
- Past session badge for retroactive PR display.

---

## v2.2 — Charts *(IN REVIEW)*

Progress visualization.

- Mini progress chart on Exercise detail screen.
- Full-screen Exercise progress chart screen.
- Date filtering UX.

---

## v2.3 — Quick start workout *(PLANNED)*

Closes the cold-start gap: a freshly installed app can start a workout
immediately without preconfigured trainings or exercises.

- Home `StartWorkoutWidget` picker: new "Start blank" entry above library
  trainings.
- Live workout: empty initial state, editable training name header (save
  on blur), placeholder "Untitled" until named.
- Inline exercise picker (bottom sheet) with search-or-create UX, explicit
  no-match indicator before "Create" CTA.
- Mid-session add exercise capability — applies to **all** sessions
  (ad-hoc and library). Mid-session additions mutate the plan permanently
  (rule H1: "user does not lose what they create").
- Empty-finish confirm dialog: Discard or Continue editing (no save-anyway).
- `ExerciseEntity.isAdhoc` boolean flag with create/graduate/delete
  lifecycle and defence-in-depth predicate (flag AND join via current
  training).
- Migration v5 → v6 (non-destructive `ALTER TABLE`).
- Migration retroactively deletes orphan ad-hoc Training rows from prior
  Track Now cancel flows.
- Track Now's existing cancel logic refactored to use the shared
  `discardAdhocSession` interactor helper, fixing the prior orphan
  Training bug as a side effect.

Spec: `documentation/specs/v2.3-quick-start-workout.md`.

---

## v2.4 — Home progress surfacing *(TENTATIVE)*

Surface user progress on the Home screen.

- Achievement block on Home.
- Stats dashboard sub-screen.
- Settings → Stats entry point (decision deferred to spec).

---

## v2.5 — Library chrome *(TENTATIVE)*

List screen ergonomics and small Settings additions.

- Multi-select bulk operations in Trainings and Exercises lists.
- Manage tags screen.
- Crash reporting toggle in Settings.

---

## v2.6 — Quick notes *(TENTATIVE)*

Capture-first note-taking flow with conversion to live workout.

- Capture screen.
- Inbox screen + Home `TopAppBar` badge.
- Edit and structure note.
- Convert-to-Live-workout flow.

---

## v2.7 — Tech debt ratchet *(TENTATIVE)*

Dedicated ratchet pass — not distributed across feature PRs per the
"tech-debt is a living ratchet" policy.

- UI Mapping Boundary cleanup (per `documentation/tech-debt.md` register).
- `@Preview` coverage fill-in.
- `androidTest` stubs converted to real tests.
- DAO unit tests for v2 queries (and retroactively for v1 queries flagged
  in tech-debt).
- Live workout feature module decomposition consideration (feature became
  heavy after v2.1 PR detection + v2.2 chart hook + v2.3 mid-session
  capabilities — sub-handler split via `PlanEditAction`-style wrapper if
  warranted).
- Track Now / Quick start UI unification consideration.

---

## Decomposition rationale

Foundations-first (v2.0 as gate stage) was chosen over feature-first to
avoid touching `core/exercise` aggregation surface twice. Quick start
(v2.3) is positioned **before** Home progress (v2.4) because cold-start
gap blocks first-session experience for new users, while progress
surfacing has nothing to surface for users without history. Quick start
also generates the data on which Home progress and Quick notes
conversion later depend.

Tech debt ratchet (v2.7) is last because it's a ratchet pass, not a
feature; distributing it across feature PRs degrades both feature scope
clarity and ratchet effectiveness.

---

## Out of scope for v2

Features explicitly deferred to v3+:

- Programmable training plans / mesocycle planning.
- Social / sharing features.
- Wearable integration.
- Cross-device sync.

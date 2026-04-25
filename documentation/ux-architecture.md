# UX Architecture

This document describes the screen-level architecture of Workeeper:
what screens exist, how navigation flows between them, and what each
screen is responsible for. It is the textual counterpart of the
FigJam diagram and is the source of truth when the two disagree.

For domain entities mentioned here, see [product.md](product.md). For
release scope of each screen, see the **Release scope** section in
[product.md](product.md). Some screens are deferred to releases
beyond v1 — they are marked with `(v2)` in the section heading where
applicable. Sections without a marker are part of v1.

## Top-level navigation

The app exposes three bottom-bar tabs:

- **Home** — dashboard, default tab on launch.
- **Trainings** — library of training templates.
- **Exercises** — library of exercise templates.

There is no Charts tab. Per-exercise progress charts are reached from
Exercise detail or from the Stats dashboard (a sub-screen of Home,
v2). Settings are reached from a TopAppBar icon on Home.

## Screens

### Home

The default tab and primary entry point. Answers the question *"what
should I do now?"*. Sections, top to bottom:

1. **Achievement block (v2).** Surfaces the most recent meaningful
   accomplishment. Tapping opens the Stats dashboard. In v1 the
   entire achievement block is omitted; the dashboard starts with
   the active session card / start button.
2. **Active session card** — shown only when a session is in the
   `in-progress` state. Displays the parent training name and progress
   ("Push Day — 2 of 5 exercises done"). Tapping resumes the session
   in **Live workout**.
3. **Start training button** — primary call to action when there is no
   active session. Opens the **Training picker** bottom sheet.
4. **Recent sessions list** — finished sessions, most recent first.
   Tapping a row opens **Past session detail**.

TopAppBar holds two icons in v2: **Settings** and **Quick notes inbox**
(badge with unprocessed count). In v1 only the Settings icon is
present.

### Trainings tab

Library of training templates. **Ad-hoc and archived trainings are
not shown here**.

- List of templates with name, tags, exercise count.
- FAB → **Create training**.
- Tag-based filter in the TopAppBar.
- Long-press on a row → **Multi-select mode** *(v2)*.
- Tap a row → **Training detail**.
- Row swipe / overflow → **Delete** action (moves to archive — see
  Archive screen below).

### Training detail

Shown for a specific training template. Two roles in one screen:
*template definition* and *its history*.

- Header: name, description (collapsible), tags.
- Exercise list — the exercises composing this training, in order.
- "Start session" CTA → **Live workout** (creates a new session linked
  to this template, with snapshot of current exercise list).
- Past sessions list — finished sessions of this template, most
  recent first. Tapping → **Past session detail**.
- Edit / Delete (move to archive) actions in TopAppBar overflow.

Tapping an exercise in the exercise list → **Exercise detail**.

### Edit training

Form to create or edit a training template. Reachable from Trainings
tab FAB (create) or from Training detail (edit). Fields:

- Name.
- Description (optional, plain text).
- Tags (multi-select chip input, with inline creation).
- Exercise list — pickable from the Exercises library, reorderable.

Cannot select archived exercises in the picker.

### Exercises tab

Library of exercise templates. **Archived exercises are not shown
here**.

- List of exercises with thumbnail (image is v1.5; placeholder until
  then), name, tags.
- FAB → **Create exercise**.
- Tag-based filter in the TopAppBar.
- Long-press on a row → **Multi-select mode** *(v2)*.
- Tap a row → **Exercise detail**.
- Row swipe / overflow → **Delete** action. If the exercise is used
  in any non-archived training, the action is blocked with a message
  listing the trainings; the user must remove it from those
  trainings first. Otherwise it moves to archive.

### Exercise detail

Shown for a specific exercise template. Combines reference card and
progress overview.

- Hero image *(v1.5)* — placeholder shown until v1.5.
- Name, type indicator (weighted / weightless), tag chips.
- Description (collapsible).
- **PR block (v2)** — best records for this exercise.
- **Mini progress chart (v2)** — compact preview.
- Recent history — sessions where this exercise was performed.
- Primary CTA: **Track now** → **Live workout** in ad-hoc mode (an
  ad-hoc training is created with this single exercise).
- Secondary actions: **View full chart (v2)**, Edit, Delete (with
  the same archive-vs-block rules as the Exercises tab row action).

### Edit exercise

Form to create or edit an exercise template. Reachable from Exercises
tab FAB (create) or from Exercise detail (edit). Fields:

- Name.
- Type — weighted / weightless toggle.
- Tags (multi-select chip input, with inline creation).
- Description (optional, plain text).
- Image *(v1.5)* — camera or gallery picker.

### Live workout

The single tracking screen for all session types. The most
performance-critical screen — the lifter is in the gym, often holding
a phone with sweaty hands. UX priorities: minimum taps, large tap
targets, in-the-moment feedback.

Entry points:

- Home → Start training → Training picker → select template.
- Home → tap active session card (resume).
- Training detail → Start session.
- Exercise detail → Track now (ad-hoc).
- *(v2)* Quick note inbox → Edit / structure note → Convert.

Screen content:

- Current exercise focus area — name, set entry row.
- **Previous-set hints** next to each set entry — "last time you did
  100kg × 5 reps" — pulled from the most recent finished set of this
  exercise.
- Set list for the current exercise — rows of entered sets with type
  badges (warmup / work / fail / drop).
- Carousel of remaining exercises — quickly switch between exercises
  in the session. Each exercise shows one of three states: pending,
  done (has sets), or skipped (explicitly marked).
- Skip exercise action — marks the current performed_exercise as
  skipped, advances to the next.
- **PR detection (v2)** — when a saved set establishes a new PR, the
  row is visually highlighted in the moment.
- Top-bar: session name, finish button, exit-without-finish action.
- Exit without finish → session remains `in-progress`, returns to
  Home where it surfaces as the active session card.

Finish action commits the session, transitions to **Session summary**.

The session's exercise list is fixed at start (snapshot from the
parent training at that moment). Editing the parent training
afterwards does not change this session.

### Session summary

Shown immediately after finishing a session.

- Session name, duration, total exercises / sets / volume.
- **New PRs achieved during the session (v2).**
- Per-exercise summary — done, skipped, sets count, volume.
- Action: continue to Home.

### Past session detail

View of a finished session. Reachable from Home (recent sessions list)
and from Training detail (past sessions list).

- Same layout as Session summary, but framed as historical record.
- Actions: **edit** (correct mistakes — v1), delete.
- Editing a set in a finished session is allowed; finished state
  cannot be reverted to in-progress.

### Stats dashboard *(v2)*

Aggregate progress overview, reached from the Home achievement block.
Entire screen deferred to v2.

### Exercise progress chart *(v2)*

Per-exercise full chart with date filtering. Deferred to v2.

### Training picker (bottom sheet)

Modal bottom sheet shown when starting a session from Home. Lists
recently-used training templates and a "see all" link.

- Recent templates (top 3–5).
- "See all templates" → switches to **Trainings tab**.
- Tap a template → **Live workout**.

Archived templates are not listed.

### Quick notes inbox *(v2)*

Reached from Home TopAppBar quick-notes icon. Deferred to v2.

### Edit / structure note *(v2)*

Form to convert a quick note into a structured session. Deferred to
v2.

### Quick note capture *(v2)*

Minimal screen with a text input plus "save". Deferred to v2.

### Settings

Reached from Home TopAppBar settings icon. Sections in v1:

- About — app version, license, link to GitHub repository.
- Theme — system / light / dark.
- **Archive** → **Archive screen**.

Sections deferred to v2:

- Crash reporting toggle.
- Manage tags → Manage tags screen.

### Archive

Sub-screen of Settings. Lists archived templates with two segments
(or two tabs): **Archived trainings** and **Archived exercises**.

Per-row actions:

- **Restore** — clears the archived flag; the template returns to
  its library tab. For exercises, restore is always allowed. For
  trainings, restore is always allowed.
- **Permanently delete** — hard delete with cascade to history.
  Confirmation dialog spells out the impact: *"Permanently delete
  the training 'Push Day' and 47 sessions of history? This cannot
  be undone."* Two-tap confirmation (the dialog button is not the
  default focus).

Empty state when nothing is archived.

### Manage tags *(v2)*

Sub-screen of Settings. Deferred to v2.

## Cross-cutting concerns

### Active session visibility

An `in-progress` session is reflected in two places:

- Home — active session card replaces the "Start training" button.
- Trainings tab — the parent training (if a template) shows a marker
  on its row that a session is in progress for it.

There is exactly one active session at a time. Starting a new session
while one is in progress prompts the user to either resume or finish
the existing one first (exact UX in feature spec — open question in
[product.md](product.md)).

### Session lifecycle

```
not-started ─Start session─▶ in-progress ─Finish─▶ finished
                                  │
                                  └─Exit without finish─▶ in-progress (persisted)
```

A finished session can be edited (sets corrected) via Past session
detail in v1; cannot be re-opened to `in-progress`.

### Session composition (snapshot semantics)

When a session starts, `performed_exercise` rows are created upfront
— one per exercise in the parent training at that moment. The list
is fixed thereafter; editing the parent training does not change
existing sessions.

What does follow the parent template (live link) is the **identity**
of each performed exercise: its `exercise_id` reference. This means
if the user renames "Bench" to "Bench Press", historic sessions show
the new name; but if the user removes "Bench" from the Push Day
template, historic Push Day sessions still contain the Bench
performed_exercise row.

### Performed exercise states

Each `performed_exercise` row in a session has one of three states,
derived from data:

- **Pending** — `skipped = false` AND no sets logged.
- **Done** — at least one set logged.
- **Skipped** — `skipped = true`.

Users transition between states implicitly (logging a set →
pending → done) or explicitly (skip exercise action → skipped).

### Ad-hoc trainings

Created automatically by:

- Exercise detail → Track now.
- *(v2)* Quick note → Convert (when no template is matched).

Ad-hoc trainings are persisted in the same table as templates but
flagged. They are excluded from:

- Trainings tab list.
- Training picker.
- Tag-based filter results in Trainings tab.
- Archive screen (ad-hoc trainings are not surfaced separately).

They are visible only via the parent of a session (e.g. Past session
detail names them).

### Archive behaviour

"Delete" actions in the library never hard-delete. They flip the
`archived` flag, moving the row to the Archive screen. The Archive
screen is the only place where permanent (cascading) deletion is
possible, and only behind explicit two-tap confirmation.

Archiving an exercise is **blocked** if the exercise is referenced by
any non-archived training. The user is told which trainings reference
it and must edit those trainings first.

Archiving a training has no comparable block — trainings are leaves
of the dependency graph (exercises don't reference trainings).

### Tag-based filtering

Both Trainings tab and Exercises tab support filtering by tags.
Because the tag pool is shared, a tag selected in one tab keeps
working when the user switches to the other tab. Exact filter
semantics (AND vs OR across multiple tags) are decided in feature
spec.

### Empty states

Each list-based screen has an empty state with an inline call to
action. Exact copy and visual treatment in feature spec.

## Decisions log

Decisions made during IA design that may not be obvious from the
diagram alone:

1. **3 tabs, not 4.** Charts tab removed; per-exercise progress is
   reachable through Exercise detail and Stats dashboard.
2. **Live workout is one screen for all tracking scenarios.** Used
   for template-based sessions, ad-hoc single-exercise sessions, and
   converted quick notes (v2).
3. **Ad-hoc trainings are an internal mechanism.** They unify the
   data model so that every session always has a parent training.
4. **Standalone exercise tracking does not exist as a distinct flow.**
   Exercise detail "Track now" creates an ad-hoc training under the
   hood and routes to Live workout.
5. **Settings is a sub-screen, not a tab.** Reached via TopAppBar
   icon on Home.
6. **Quick notes are first-class but deferred to v2.**
7. **History is split by tab.** Trainings tab shows training history
   per template (in Training detail). Exercises tab shows per-exercise
   history (in Exercise detail). Home shows recent sessions across
   everything.
8. **Drafts use session state, not a separate entity.** A draft is a
   session in `in-progress` state.
9. **PR tracking is deferred to v2** despite being conceptually part
   of the loop. Half-implementation gives bad UX.
10. **Two-stage delete via archive.** "Delete" in library moves to
    archive (soft delete). Permanent deletion happens only from the
    Archive screen with explicit confirmation. Defends against
    accidental data loss.
11. **Hybrid snapshot/live-link for session composition.** Session
    fixes which exercises were in it (snapshot of `exercise_id`
    references), but each reference follows the live state of the
    exercise template (current name, type, tags).
12. **Performed exercises created upfront with skipped flag.**
    Distinguishes "didn't reach" from "intentionally skipped". The
    alternative (lazy creation) loses this distinction.
13. **Archive exercise blocked if used in active templates.** Forces
    explicit cleanup, prevents ghost references in active trainings.

## Open IA questions deferred to feature specs

- Long-press vs visible-toggle for multi-select (Trainings / Exercises
  lists) — relevant when multi-select arrives in v2.
- Tag-filter semantics — AND vs OR across multi-tag selections.
- Active session conflict — what happens when the user tries to start
  a new session with one already in progress.
- Add-during-session — whether the live workout allows adding an
  exercise that is not in the parent template.
- Empty-state copy and visuals across all list screens.
- Achievement-block selection logic (v2) — which PR or progress
  moment surfaces, and the rotation policy.
- Quick note capture entry point (v2) — FAB-on-Home vs TopAppBar
  icon.

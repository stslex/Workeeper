# Product

This document captures the product thesis for Workeeper: who it is for,
what it does, and — equally important — what it explicitly does not do.
It is the foundation for [features.md](features.md),
[ux-architecture.md](ux-architecture.md), and the data layer described
in [data-needs.md](data-needs.md). When in doubt about whether to add
a feature, return to the **Non-goals** section first.

## Mission

Workeeper is an open-source, fully offline, free-forever weight training
tracker for Android. It exists to let lifters record their workouts and
see their progress without subscriptions, accounts, or cloud sync.

## Positioning

Workeeper differentiates from Hevy, Strong, JEFIT, Fitbod, and similar
trackers along the **values** axis, not the feature axis:

- **Open source.** Code is auditable; users and contributors can verify
  what the app does with their data.
- **Free forever.** No subscription, no premium tier, no advertising,
  no in-app purchases.
- **Fully offline.** All data lives on the device. There is no account,
  no server, no cloud sync, no telemetry beyond crash reporting.
- **No social layer.** No followers, no shared workouts, no feed.

The intended audience is privacy-conscious lifters who reject
subscription-based fitness software and want their training data to stay
on their own device.

## Core loop

The user's primary loop, from setup to repeated use:

1. Create an **exercise template** (e.g. "Bench Press") in the personal
   library.
2. Optionally create a **training template** as a reusable workout plan
   (e.g. "Push Day").
3. **Track** a session: pick a training template (or start an ad-hoc
   one with a single exercise) and log sets for each exercise.
4. Review **history** and **progress** to see how the lifter is
   trending over time.

The tracking step (3) is the most frequent operation and must feel
friction-free. The dedicated live workout screen (described in
[ux-architecture.md](ux-architecture.md)) prioritizes minimum taps and
in-the-moment feedback (previous-set hints, real-time PR detection)
over feature completeness.

## Domain model (conceptual)

These are the conceptual entities the product reasons about. Concrete
schema, columns, and relationships are in [data-needs.md](data-needs.md)
and the upcoming `db-redesign.md`.

### Training template

A named, reusable workout plan. Owns:

- Name (required).
- Optional description (free-form text — e.g. focus, technique notes,
  weekly placement).
- Any number of tags (from the shared tag pool).
- Ordered list of exercise template references (which exercises and in
  what order).
- Archived flag — when archived, the template is hidden from the
  Trainings library but its history of sessions is preserved.

Templates live in the Trainings library and are reusable across many
sessions.

### Ad-hoc training

A training created on the fly when the user wants to track a single
exercise without picking an existing template. Carries the same shape
as a template, but is marked as ad-hoc and is **excluded from the
Trainings library** view (it would clutter the library otherwise).
Ad-hoc trainings exist only to give every session a parent.

### Exercise template

A named, reusable exercise definition. Owns:

- Name (required).
- Type — `weighted` or `weightless` (required).
- Any number of tags (from the shared tag pool).
- Optional description (free-form text — technique notes, gym-specific
  details).
- Optional image (release v1.5) — a photo from the camera or gallery,
  intended to help the user identify the right machine or movement in
  the gym. Stored locally on the device; no cloud upload.
- Archived flag — when archived, the exercise is hidden from the
  Exercises library but its history is preserved.

### Session

A performed workout instance on a specific date. **Always** belongs to
a training (either a template or an ad-hoc one). Carries:

- Date / time of the session.
- Reference to its parent training (live link — name, tags, etc. follow
  the parent's current state).
- State — `in-progress` or `finished`. An `in-progress` session
  represents a draft / unfinished workout that can be resumed.
- A list of **performed exercises** that is fixed at session start
  (snapshot semantics — see [ux-architecture.md](ux-architecture.md)
  decisions log).

Sessions are the unit of history: the recent-trainings list on Home
shows sessions, the per-exercise progress chart aggregates over
sessions, etc.

### Performed exercise

Within a session, a per-session record of doing one exercise. Owns:

- Reference to an exercise template (live link — current name and
  type are followed).
- Ordered list of sets that were logged for it.
- Skipped flag — set true when the user explicitly marks the exercise
  as skipped during the workout. Distinguishes "didn't reach yet"
  from "skipped on purpose".

A session's performed exercises are created upfront when the session
starts (one per training_exercise membership). The session's
composition is a snapshot — editing the parent training afterwards
does not retroactively change finished sessions.

### Set

A single set within a performed exercise. Owns:

- Reps (required).
- Weight (required for weighted exercises, absent for weightless).
- Set type — `warmup` / `work` / `fail` / `drop`.

### Tag

A free-form label. Owns a name. Many-to-many on both sides — applied to
exercise templates and to training templates from a **shared pool**: a
tag named "Lower body" applied to the exercise "Squat" is the same tag
entity as the one applied to the training "Leg day". This enables
cross-navigation: filtering by a tag surfaces both exercises and
trainings carrying it.

## Exercise types (v1)

- **Weighted** — sets carry weight and reps. Bench Press, Squat,
  Bicep Curl, Lat Pulldown.
- **Weightless** — sets carry only reps. Pull-ups, Push-ups, Dips.

Time-based exercises (plank, holds) and cardio (running, cycling) are
explicitly out of scope for v1.

## Release scope

The product is delivered incrementally. This section is the source of
truth for what ships when — referenced from feature specs and the
implementation plan.

### v1 — initial release

Thirteen features, organized by the role they play in making the core
loop functional and pleasant. **Anything not listed here is deferred,
even if discussed elsewhere in this document.**

**Must (the core loop cannot work without these):**

1. **Trainings library** — list of training templates, create / edit
   / archive.
2. **Exercises library** — list of exercise templates, create / edit
   / archive (basic form: name, type).
3. **Live workout** — set entry, exercise navigation within a session,
   skip-exercise action, finish action.
4. **Past session detail** — view of a finished session.
5. **Home dashboard (basic)** — Start training button, recent sessions
   list, active session card. **Without** achievement block.
6. **Settings (basic)** — about, license, link to GitHub repository.

**Should (the loop works without these but degrades meaningfully):**

7. **Previous-set hints in Live workout** — "last time 100kg × 5".
   Without this, live mode loses half its purpose.
8. **Tags** — create and assign tags on templates; filter library
   lists by tag. Tag creation is inline only at this stage; the
   dedicated Manage tags screen is v2.
9. **Resume in-progress session** — exiting Live workout without
   finishing persists the session; Home surfaces it as the active
   session card; tap to continue.
10. **Theme** — system / light / dark switch in Settings.

**Selected nice-to-have:**

11. **Description fields** on exercise templates and training
    templates — plain-text free-form input.
12. **Edit past session** — corrections to a finished session
    (mistyped weight, missed set).
13. **Archive system** — soft delete for trainings and exercises with
    restore and permanent-delete actions in a dedicated Archive screen
    under Settings. "Delete" in library always moves to archive (no
    direct hard delete in library). Archiving an exercise that is
    used in any active (non-archived) training is blocked with a
    message; the user must remove the exercise from those trainings
    first.

### v1.5 — fast follow-up

14. **Exercise image attachment** — photo from camera or gallery,
    stored locally. Requires camera/storage permissions, image
    compression strategy, and lifecycle handling. Worth shipping
    separately to keep v1 simpler.

### v2 — second wave

These are deferred deliberately. They depend either on accumulated
session data (PR detection, stats), on the v1 product shape having
stabilized (achievement block, manage tags), or on representing
features sizable enough to warrant their own development cycle (quick
notes).

- **Personal records (PR) tracking.**
  - In-the-moment: highlight when a set establishes a new PR during a
    live workout.
  - Historical: per-exercise PR display in Exercise detail.
- **Stats dashboard** — aggregate progress overview reached from the
  Home achievement block.
- **Exercise progress chart** (full screen) — per-exercise chart with
  date filtering.
- **Mini progress chart** on Exercise detail.
- **Achievement block on Home** — the top "what did I just achieve"
  card.
- **Quick notes** — capture, inbox, structure / convert flow.
- **Manage tags screen** — rename, delete, merge tags.
- **Multi-select bulk operations** in Trainings and Exercises lists.
- **Crash reporting toggle** in Settings.

## Non-goals

These are explicitly **not** part of the product, now or in foreseeable
future versions. Decisions to add anything below need a deliberate
re-read of this section first.

- **No accounts or authentication.** The app does not know who the user
  is.
- **No cloud sync.** Data does not leave the device. Manual export /
  import may be added later, but is not a v1 commitment.
- **No social features.** No feed, no following, no shared workouts,
  no comments, no likes.
- **No subscriptions, premium tiers, or in-app purchases.**
- **No advertising.**
- **No prebuilt training programs** (5/3/1, StrongLifts, Greyskull,
  PPL templates). The user composes their own trainings.
- **No coaching or AI recommendations** for what to lift next.
- **No scheduled / planned workouts.** The Home dashboard reflects
  *active or recent* sessions, not "scheduled today". Scheduling is a
  larger feature deferred indefinitely.
- **No time-based or cardio exercises** in v1 (plank, running,
  cycling).
- **No workout timer / rest timer** as a core feature in v1 — may be
  reconsidered later.
- **No nutrition or body-measurement tracking.**
- **No wearable integration** (Wear OS, Health Connect) in v1.
- **No analytics or telemetry** beyond crash reporting.

## Open questions (resolved in later stages)

These are deliberately deferred. They are listed here so they are not
forgotten and so future decisions cite this document.

### For feature specs

- **Tag filter semantics.** When the user picks multiple tags, does the
  filter return items matching ALL of them (intersection) or ANY of
  them (union)?
- **PR metric set** (v2). Exactly which records to track: 1RM
  estimate, best set at fixed reps (3RM, 5RM, 10RM), best total
  volume in a session?
- **Chart metrics** (v2). What goes on the per-exercise progress chart
  by default: heaviest set, estimated 1RM, total volume?
- **Multi-select trigger** (v2). Long-press vs visible toggle for
  entering multi-select mode in Trainings / Exercises lists.
- **Empty states.** What Home, Trainings tab, and Exercises tab show
  when the user has nothing yet.
- **Quick-note structure** (v2). Whether a quick note is plain text
  only or has lightweight structure.
- **Session-vs-template divergence.** During a live workout, can the
  user add or skip exercises that are not in the parent training? The
  skip case is in scope for v1 (see Performed exercise / Live
  workout). The add case is open.
- **Image storage strategy** (v1.5). Compression target, format,
  thumbnail generation, max dimensions.
- **Active session conflict.** What happens when the user tries to
  start a new session while one is `in-progress`.

### For DB redesign

- **Cascade rules.** Exact `ON DELETE` behaviour at every foreign key:
  performed_exercise → session, set → performed_exercise, join tables
  → parents.
- **At-most-one-active-session enforcement.** Partial unique index on
  `state` vs application-level invariant in repository, or both.

### Deferred indefinitely

- **Backup / export.** Eventually expected, but not committed for v1.
- **Tag pool split.** Whether to migrate from a shared tag pool to
  separate exercise-tag and training-tag pools if the shared pool
  becomes noisy in practice. Re-evaluated after v1 usage.
- **Active session timeout.** When does an `in-progress` session stop
  being "active" and become "abandoned draft"? Decided once usage
  patterns are observable.

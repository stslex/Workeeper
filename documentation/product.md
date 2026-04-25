# Product

This document captures the product thesis for Workeeper: who it is for,
what it does, and — equally important — what it explicitly does not do.
It is the foundation for [features.md](features.md) and the upcoming
information architecture and data model decisions. When in doubt about
whether to add a feature, return to the **Non-goals** section first.

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
2. Optionally create a **training** as a container of exercise templates
   (e.g. "Push Day").
3. **Track** a training session by logging sets (weight × reps, or just
   reps for weightless exercises) for each exercise in the training.
4. Alternatively, **track a standalone exercise** outside of any
   training (e.g. a quick set of pull-ups not part of a planned session).
5. Review **history** and **progress charts** to see how the lifter is
   trending over time.

This loop runs many times per week. Steps 3 and 4 are the most frequent
operations and must feel friction-free.

## Domain model (conceptual)

These are the conceptual entities the product reasons about. The
database layer will be redesigned in a later stage to reflect this
model — current schema does not yet match.

- **Exercise template** — a named, reusable definition of an exercise.
  Owns its type and any number of tags. Examples: "Bench Press"
  (weighted), "Pull-ups" (weightless).
- **Training** — a named container that groups exercise templates into
  a workout plan. Owns any number of tags. Example: "Push Day"
  containing Bench Press, Overhead Press, Triceps Pushdown.
- **Exercise log** — a record of a performed exercise on a specific
  date. Belongs either to a training session or stands alone. Carries
  the actual sets performed.
- **Set** — a single set within an exercise log. Has reps, optionally
  weight (for weighted exercises), and a set type (warmup / work / fail
  / drop).
- **Tag** — a free-form label applied to exercise templates and/or
  trainings. Many-to-many on both sides. **The pool is shared**: a tag
  named "Lower body" applied to the exercise "Squat" is the same tag
  entity as the one applied to the training "Leg day". This enables
  cross-navigation: filtering by a tag surfaces both the exercises and
  the trainings carrying it.

A clear separation between **template** (definition) and **log**
(performance) is required. The current codebase conflates them; this
will be reworked.

## Exercise types (v1)

- **Weighted** — sets carry weight and reps. Bench Press, Squat,
  Bicep Curl, Lat Pulldown.
- **Weightless** — sets carry only reps. Pull-ups, Push-ups, Dips.

Time-based exercises (plank, holds) and cardio (running, cycling) are
explicitly out of scope for v1.

## Feature scope (v1)

- Create / edit / delete exercise templates.
- Create / edit / delete trainings (compositions of templates).
- Track a training session (log sets for each exercise in order).
- Track a standalone exercise (without a parent training).
- View history of past trainings and standalone exercises, visually
  separated.
- View per-exercise progress chart (max weight, 1RM estimate, volume —
  exact metrics decided in feature spec).
- **Personal records (PR) tracking.**
  - In-the-moment: highlight when a set establishes a new PR.
  - Historical: per-exercise PR display (best 1RM, best 5RM, best set
    volume — exact set decided in feature spec).
- **Drafts / quick-notes.**
  - In-gym scenario: capture a workout fast and roughly, finish
    formatting later. Drafts are clearly marked as unfinished.
- Tag management for organizing exercise templates and trainings under
  a shared tag pool.

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
- **No time-based or cardio exercises** in v1 (plank, running, cycling).
- **No workout timer / rest timer** as a core feature in v1 — may be
  reconsidered later.
- **No nutrition or body-measurement tracking.**
- **No wearable integration** (Wear OS, Health Connect) in v1.
- **No analytics or telemetry** beyond crash reporting.

## Open questions (resolved in later stages)

These are deliberately deferred. They are listed here so they are not
forgotten and so future decisions cite this document.

- **Tag filter semantics.** When the user picks multiple tags, does the
  filter return items matching ALL of them (intersection) or ANY of
  them (union)? Resolved in feature spec.
- **PR metric set.** Exactly which records to track: 1RM estimate,
  best set at fixed reps (3RM, 5RM, 10RM), best total volume in a
  session? Resolved in feature spec.
- **Chart metrics.** What goes on the per-exercise progress chart by
  default: heaviest set, estimated 1RM, total volume? Resolved in
  feature spec.
- **Standalone exercise UX.** Whether standalone exercises share the
  same screen as in-training exercises or have a distinct entry path.
  Resolved in IA.
- **Drafts mechanics.** Whether a draft is a separate entity, or just
  a state flag on a training, or an in-progress training that hasn't
  been "finished". Resolved in IA + data needs.
- **Backup / export.** Eventually expected, deferred to a post-v1
  stage.
- **Tag pool split.** Whether to migrate from a shared tag pool to
  separate exercise-tag and training-tag pools if the shared pool
  becomes noisy in practice. Re-evaluated after v1 usage.

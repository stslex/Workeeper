# Changelog

All notable changes to Workeeper will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres loosely to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Conventions:
- Pre-release builds going through Play Store internal review are versioned
  but may not appear in the public changelog until promoted.
- "Stages" (e.g. Stage 5.4) refer to internal v1 development milestones.
- For implementation specifics, see `documentation/feature-specs/`.

## [Unreleased]

### Added
- Track now CTA opens Live workout with an ad-hoc training (was a stub).
- Default plan surface in Exercise detail read mode.
- Active session conflict modal across all Live-workout entry points.
- Delete session option in Live workout overflow with confirm dialog.

### Changed
- finishSession now runs as a single SQL transaction; manual rollback removed.

### Tests
- Smoke UI tests for all v1 list and detail screens.
- DAO unit tests for new aggregation queries (PR, best volume, history-by-exercise) and pre-existing untested queries (`pagedActiveWithStats`, `pagedActiveWithStatsByTags`, `observeAnyActiveSession`).

## [1.5.0] — Image attachment — TBD

### Added
- Exercise image attachment: pick from gallery (Photo Picker) or capture
  via camera. JPEG-compressed, stored locally under `filesDir/exercise_images/`.
- Full-screen image viewer with pinch-to-zoom, pan, and double-tap-to-toggle
  (1× ↔ 2.5×). Reachable from Exercise detail hero and Edit screen thumbnail.
- Read-only image surfaces in Exercise detail (hero) and Exercises tab row
  thumbnail.

### Technical
- New `core/core/images/ImageStorage` utility — single source of truth for
  image lifecycle. Atomic writes, automatic cleanup on permanent delete.
- New `feature/image-viewer` module, generic over file paths or content URIs.
- New `Screen.ExerciseImage(model)` route.

## [1.0.0] — v1 release — 2026-04-28

First feature-complete release. Bottom-bar navigation: Home, Exercises, Trainings.

### Added (Stage 5.5)
- Home dashboard expansion: Start CTA, recent sessions list, training picker
  bottom sheet. Active session banner from Stage 5.4 retained.
- Past session detail screen with view + edit + delete. Set-level edits
  (weight, reps, type) only.
- Live workout finish flow lands on Past session detail (replaces previous
  popBack-to-Training behaviour).
- `Navigator.replaceTo(screen)` — new navigator method for forward
  redirects without leaving stale screens on the back stack.

### Added (Stage 5.4)
- Live workout module: real-time session execution with live exercise
  cards, set logging, finish/cancel flows.
- Minimal Home banner showing the active session, if any.

### Added (Stage 5.3)
- Trainings tab and Single training detail screen.
- Training templates with linked exercises and tags.

### Added (Stage 5.2)
- Exercises tab and Exercise detail screen.
- Exercise CRUD, archive, and bulk operations.

### Added (Stage 5.1)
- Settings and Archive screens. Archive restoration flows.

### Removed
- Charts tab and `feature/charts` module — superseded by Home dashboard;
  Stats dashboard deferred to v2.

## [Pre-1.0] — Foundational stages

Pre-v1 architectural and infrastructure work prior to feature delivery.
For history before this point, see `git log` and the per-Stage feature
specs in `documentation/feature-specs/`.

[Unreleased]: https://github.com/stslex/Workeeper/compare/v1.5.0...HEAD
[1.5.0]: https://github.com/stslex/Workeeper/releases/tag/v1.5.0
[1.0.0]: https://github.com/stslex/Workeeper/releases/tag/v1.0.0

# Wear shared style and presentation MVI

## Contract

The controller shares design tokens and bundled fonts with the phone while adapting geometry
to round 192dp and 240dp screens. IBM Plex Sans owns words, Archivo owns numeric fragments with
tabular figures, and IBM Plex Mono owns auxiliary roles. Archivo must never style Russian text.
Wear keeps its deliberate black background. Phone typography, colours and goldens stay unchanged.

The existing project Store/Handler/StoreProcessor owns screen/editor state and ambient/notice
presentation. An interactor exposes coherent runtime snapshots; the UI mapper formats each
snapshot once for its locale. Activity owns platform callbacks and system effects; Compose owns
scroll, focus, swipe and rotary pixel accumulation. There is no second MVI implementation.

The process runtime retains cache, draft, reducer, authority, ongoing activity and Tile ownership.
Expiry is checked synchronously before exposing interactive content after ambient. Relative draft
steps apply serially to the latest draft, preserving the existing bounds and nullable-weight policy.
Protocol, database and mutation-authority contracts are unchanged.

Firebase uses the existing phone SDKs and matching distribution configuration. Crashlytics gets
the custom key `platform=phone` or `platform=watch` at application startup, before graph/runtime
work. Wear telemetry must not serialize workout payloads through action/event descriptions.
The real-phone-payload privacy gate remains closed; validation uses synthetic/debug sources only.

## Delivery and evidence

Four stacked changes cover shared resources and dependencies, presentation/rotary, final-font
geometry, then system Tile and complete emulator acceptance. The Tile freshness defect remains
open until the real system Tile updates following persisted state changes. Updating a preview
does not close it. ProtoLayout uses supported system fonts with the shared colour roles.

Required matrix: API30/36 × round192/240dp × EN/RU × font1.0/1.24. Values, primary action and
blocking reason must be visible before scrolling; details remain reachable after scrolling.
Back/swipe/focus, ambient, authority and lifecycle contracts remain mandatory.

New guards require named negative controls through the mutation harness. Compiler and
infrastructure failures are INVALID, not RED. Gates run serially with `--rerun-tasks`,
`--no-build-cache`, `--no-configuration-cache`, and Detekt separately. Fresh XML and the exact
`N actionable tasks: N executed` summary are required. Previous acceptance is historical evidence,
not verification of this change. Performance observations must name their build type.

## Geometry

Information screens use a centred scroll viewport inscribed in the round display. For a display
diameter `d`, a square of side `d / sqrt(2)` has its four corners on the circle; subtracting 4dp
keeps an inset for the visible content. The viewport guarantees a safe width at every visible
height, while long instructions remain scrollable. Progress and completion instructions are
centre-aligned. Editor signs use symmetric vector paths centred by an IconButton, with a 48dp
touch target and no position offsets.

Wear's typography is an adapter over the shared families. Card values use the compact numeric
role; editor values use larger numeric spans with separate auxiliary spans for units. Missing
weight is a text role. Actual render/layout acceptance remains mandatory after these changes.

### Card sizing provenance

The unequal card allocation reserves more space for a six-character weight than for
three-digit reps. Units and the full unset-weight wording remain available in accessibility
and the editor. The round-screen matrix must verify the actual bundled-font result.

For historical context, the controller at `1af93d0b`, before shared Wear fonts, recorded
65dp/36dp value widths at font scale 1.24, a 92dp:60dp card split with 76dp/44dp content,
and 60dp content per card under an equal split. Its inline derivation also recorded
`108 + 52 + 8 > 160` for a weight including its unit and 88dp for Russian unset copy.
These are preserved source-recorded measurements of the earlier typography; they are
not measurements or acceptance evidence for the new font roles.

## Implementation ledger

2026-09-26 foundation: shared design tokens and font assets, the minimal existing MVI dependency
boundary, and the complete phone Firebase SDK set are implemented. Phone and watch set their
Crashlytics platform key before application graph work. The remaining stacked changes implement
presentation MVI/relative input, adapted Wear typography/geometry, and Tile refresh/acceptance.
The real-phone-payload privacy gate remains closed. Physical watches, reconnect, energy use and
hardware ambient behavior remain separate acceptance.

2026-09-26 presentation: the existing retained project Store and typed handlers now own
screen/editor decisions. Runtime snapshots keep process cache, authority and ongoing ownership
outside the Store. The mapper prepares locale-aware values once; relative rotary batches apply
to the latest draft. The following PR adapts Wear font roles and round geometry.

2026-09-26 style: Wear consumes the shared font families and shape/spacing roles. Vector signs
are centred inside explicit 48dp rounded buttons. Information content scrolls inside a round-safe
viewport; ambient uses the same bundled typefaces with measured glyph fitting. New device guards
cover whole Russian words, sign centring, complete instructions and multi-step rotary events.
Tile refresh and the frozen final acceptance cohort follow in the last stacked PR.

# Wear controller — bottom-band rebudget (PR-A)

Status: implemented by [PR #285](https://github.com/stslex/Workeeper/pull/285), merged
into `dev` as `20215640`. The original comparison baseline was `254e075f`.
Decision: **C4L** — move the unavailability word into the disabled `EdgeButton`, omit
its glyph, and remove the separate word reservation.

This document separates the merged implementation from historical measurements and
mockup predictions. Device measurements below are recorded in the PR-A merge commit;
they have not been repeated by this documentation update. Rows marked ᴹ are predictions
from `documentation/mockups/wear-bottom-band.html`, whose fitted font-scale multipliers
were ×1.28 for 13/14sp and ×1.20 for 16sp. They are not a current device acceptance result.
The remaining delivery sequence is in [Wear UI completion](wear-ui-completion.md).

## §1 Problem and implemented correction

Before PR-A, disabled surfaces reserved `UNAVAILABLE_WORD_CLEARANCE = 24dp` in addition
to `MEDIUM_EDGE_CLEARANCE = 76dp`. Padding before `verticalScroll` shrank the content
viewport to **184px / 92dp** on the 192dp profile. A separate `UnavailableWord` sibling
painted the disabled word above the button, outside the scroll flow.

Historical pre-change measurements at 192dp:

| Surface | Effective card height | Against 48dp minimum |
| --- | --- | --- |
| ACTIVE, enabled | 47dp | under |
| ACTIVE-kind error fixtures (`field_error`, `weight_error`) | 23dp | under |
| Worded kinds (`refresh_required`, `disconnected`) | 14.85dp | under |

`WearSurfaceMapper.active` in `WearSurfaceModel.kt` computes `completeEnabled` as
`available && commandIdle && numericValid`, independently of kind. The disabled branch
therefore includes invalid values on ACTIVE as well as stale/disconnected snapshots.
A label that says only “No connection” would be incorrect for that entire branch.

PR-A removes the extra reservation and the sibling overlay. The content viewport is
**232px / 116dp** at 192dp for enabled and disabled controller surfaces. Content order
and the separate retry screen's `SMALL_EDGE_CLEARANCE = 62dp` remain unchanged.

## §2 Decision and accepted copy

- Both completion states use `EdgeButtonSize.Medium` and the same bottom clearance.
- Enabled completion draws its existing check glyph.
- Disabled completion draws only `control_disabled`: **“Disabled” / “Отключено”**.
  The label uses `maxLines = 1` and `TextOverflow.Ellipsis`.
- The full action and unavailable state remain in the button's content description.
  The label retains the `complete_unavailable` tag; the button retains `complete_set`.
- Exactly **RU × 192dp × font scale 1.24** is accepted to ellipsize on one line.
  Other locale/profile/scale cells have no completion-label overflow exception.

The original mockup used “Недоступно” and predicted 15.3px margin per side against the
*drawn arc*. That was an outer bound, not the usable content lane. The measured lane in
[§7.1](#71-arc-content-lane) invalidated the blanket “fits every cell” prediction.
“Отключено” is the accepted copy for both connection and numeric-validation disables;
it does not change the user's font scale to obtain a fit.

## §3 Implemented change surface

| Component | Implemented change |
| --- | --- |
| `ActiveScaffold` | One `MEDIUM_EDGE_CLEARANCE.dp` inset on every active-family surface |
| `UnavailableWord` / `UNAVAILABLE_WORD_CLEARANCE` | Removed |
| `CompleteSetButton` | Disabled label replaces glyph; enabled glyph retained |
| Russian `control_disabled` | “Отключено” |
| `WearOverflowAssertions` | Locale-aware exact-cell exception with a positive assertion |

The content children, their order, scaffold scroll state, and styling were not
restructured by PR-A. The approved later compact-screen work is a separate increment.

## §4 Behaviour contract

The disabled state has drawn text before scrolling, and its complete meaning remains
accessible. Degraded kinds still draw their status word under the existing redesign
contract. The disabled button has an outline rather than a fill, as well as text, so
availability is not represented by colour alone.

Enabled and disabled completion retain equal dimensions. The historical 192dp device
record gives `[0,238][384,378]` px for both Medium buttons; this is a recorded observation,
not a replacement for G7 on a changed implementation.

## §5 Recorded outcome and limits

All numbers below are at rest, before scrolling. `v` means clipped value-row pixels.
The unmarked numeric rows were recorded during PR-A; ᴹ rows remain mockup forecasts.
The table's numeric value-clip budget is not a complete EN/RU device matrix.

| Cell | Before | C4L record / forecast | Russian coverage limit |
| --- | --- | --- | --- |
| `field_error` / `weight_error`, 1.0 | v39 | v0 recorded | Full first-screen matrix still required |
| `field_error` / `weight_error`, 1.24ᴹ | v54 | v6ᴹ | Not a new device measurement |
| `refresh_required` / `disconnected`, 1.0 | v55.8 | v7.8 recorded | Does not establish every localized text bound |
| `refresh_required`, 1.24ᴹ | v80 | v32ᴹ | Worded RU state remains a known failure |
| `disconnected`, 1.24ᴹ | v120.9; pills 19.9 | v72.9; pills 0ᴹ | Worded RU state remains a known failure |
| `active_boundary`, 1.24ᴹ | v6 | v6ᴹ, unchanged | ACTIVE does not bound worded-state height |
| Disabled viewport, 192dp | 184px | 232px recorded | Same reservation in both locales |

The merge record reports effective card height rising to 47dp on error fixtures and
38.5dp on worded kinds. Both are still below 48dp. It also records the single accepted
RU completion-label ellipsis; [§7.1](#71-arc-content-lane) lists its actual content budget.
Neither this table nor a passing text-overflow gate proves complete initial visibility.

The completion plan replaces these partial improvements with an explicit acceptance
matrix: 192/240dp × EN/RU × 1.0/1.24, values, primary action, and the concrete disabling
reason all visible before scrolling. PR-A alone does not satisfy that criterion.

## §6 Remaining defects and ownership

These remain open until the completion plan's compact-screen increment and its gates
prove otherwise:

1. The error line on `field_error` / `weight_error` is below the fold. The original
   record/model reported e75.8 at 1.0 / e108.9ᴹ at 1.24. PR-A did not reorder it.
2. The model predicts a universal 6px value clip at 1.24, including enabled ACTIVE.
   That forecast is not interchangeable with the earlier card-fill measurements.
3. Worded RU states can wrap their status and leave values outside the initial
   accessibility viewport; EN “Refresh required” at 192dp/1.24 also requires a check
   against the circular screen boundary. A rectangular text-overflow check is insufficient.
4. Effective visible card height remains below 48dp on the small profile.

The earlier 2dp/10dp card-fill residual described ACTIVE screenshots only. It is not an
acceptance waiver for these errors, worded states, languages, or accessibility failures.
PR-C defines the visibility instrument; PR-D changes the layout and enables its mandatory
acceptance checks. Removing or replacing the drawn status word requires updating the
redesign contract and G3 together.

## §7 Confirmation ledger

### 7.1 Arc content lane

The PR-A merge record reports these measurements on a density-2 device/emulator profile:

| Profile | Lane | RU label at 1.0 | RU label at 1.24 |
| --- | --- | --- | --- |
| 192dp / 384px | **159px / 79.5dp** | 150px, fits | 188px, one-line ellipsis |
| 240dp / 480px | **211px / 105.5dp** | 150px, fits | 188px, fits |

The content lane, not the drawn arc boundary, constrains the label. Calling the small
lane “159dp” doubled the available budget; commit `9d6e4edb` corrected that unit error.
The accepted copy and one-cell residual resolve the original fit question. They do not
prove other content fits above the fold.

### 7.2 Pressed-state behaviour

**Host check executed on 2026-09-22:** `WearDisabledPressGateTest`, Robolectric native
SDK 33, debug test variant, RU × 192/240dp × font scales 1.0/1.24. The scaffold's
transient scroll indicator first settles for 5s of test-clock time. After pointer
down, the Compose clock advances one frame plus 500ms. Each capture is restricted
to the `complete_set` region, excluding the clock.

All four disabled before/held comparisons report **0 changed pixels**; a disabled
pointer click emits no action. The positive controls distinguish enabled and disabled
captures (nonzero pixel difference), and an enabled pointer click emits exactly one
`CompleteSet`. Captures are in `app/wear/build/reports/wear-press/*-before.png` and
`*-held.png`. The local run log is `/private/tmp/wear-ui-controls-green-final.log` and reports
`37 actionable tasks: 37 executed`.

**Limit:** this host does not render a pixel change for an enabled held ripple, so the
original enabled-hold visual control was RED and cannot validate native press-animation
rendering. The successful controls establish input delivery and a working capture
comparison, not sensitivity to every platform animation. The pinned `EdgeButton`
implementation uses a ripple rather than a press-size morph, and its disabled
`clickable` does not dispatch press interactions. That source observation is separate
from the host result. Physical-watch pressed appearance remains unmeasured; repeat the
held enabled/disabled pixel comparison there before claiming device confirmation.
The [PR-B negative-control ledger](wear-ui-completion.md#5-pr-b-implementation-and-evidence)
also records a pressed-alpha mutation caught by the same pixel comparison.

### 7.3 Text-only disabled affordance

The accepted C4L implementation uses the drawn disabled word and outlined button while
preserving the full content description. The redesign [layout contract](wear-controller-redesign.md#4-layout--active-state)
now states that form explicitly. G4 protects the label; the completion plan will replace
generic unavailable copy with a concrete reason in the primary content area.

## §8 Alternatives considered

| Candidate | Recorded reason not selected for PR-A |
| --- | --- |
| C0: keep the overlay | Retains the viewport defect |
| C1: move the word into the scroll column | The word itself falls below the fold |
| C2: size the reservation from the word | Smaller gain; does not remove the tight-cell failure |
| C3: remove the word | Loses drawn unavailable text |
| C4: glyph and label in the arc | The glyph consumes lane space; RU does not fit the binding cell |
| C5: collapse the degraded status row | Requires changing the drawn-word contract and G3 |
| C6: collapse with a shape-distinguished dot | Same contract change as C5; shape does not satisfy a drawn-word assertion |

C5/C6 were outside PR-A. The approved completion plan reopens the content-order and
status-contract decision for PR-D; this table is not a permanent prohibition on that work.

## §9 Gates and review classification

| Gate | Current contract and limitation |
| --- | --- |
| G1 touch targets | PR-C adds ancestor-clipped rectangular bounds: anchored action visible initially, cards at least 48dp after scrolling; complete initial card/text visibility remains PR-D |
| G3 kind distinction | Degraded kinds retain drawn status text; PR-C protects exact spoken-only ACTIVE and set progress, including absence of separate sibling labels |
| G4 disabled-not-colour-alone | The disabled button has drawn unavailable text |
| G6 text overflow | Only disabled `complete_set` at RU / SMALL_ROUND / 1.24 is excepted; it must report exactly one line **and** `hasVisualOverflow` |
| G7 primary hierarchy | Enabled primary dimensions are not smaller than disabled dimensions |

G6's exception is a positive check, not a skip. If shorter copy or a wider lane removes
that overflow, the exception must fail and be deleted. Enabled completion and every
other disabled cell take the strict `!hasVisualOverflow` branch.

PR-C's oracle and matrix are recorded in the
[measurement contract](wear-ui-completion.md#6-pr-c-measurement-contract-and-evidence).
Rectangular ancestor clips do not establish shape/raster visibility or sibling occlusion;
the G1 update does not close the initial-fold defects in §6. PR-A's measurement and
press-hold evidence above remain scoped to their recorded runs.

Historical negative controls recorded in commit `eda2e7f5` (not rerun for this document):

| Mutation | Recorded outcome |
| --- | --- |
| A: replace enabled glyph with a long one-line action label | EN and RU RED in the strict branch |
| B: allow disabled text two lines | RU RED at the accepted cell; EN unchanged |
| C: make EN disabled copy “Disabled until reconnected” | EN RED outside the accepted cell; RU unchanged |
| D: shorten RU disabled copy to “Откл” | RU RED because the expected residual disappears; EN unchanged |

Review findings and their recorded resolution:

| Finding | Classification | Resolution |
| --- | --- | --- |
| State-wide G6 exemption admits new non-binding-cell overflow | correct | Reproduced and narrowed to one cell with a positive assertion in `eda2e7f5` |
| 159px lane described as 159dp | correct | Unit corrected in `9d6e4edb`; derivation retained in §7.1 |
| Experiment history in the G6 comment | correct | `eda2e7f5` moved the history to its commit record; invariant stays beside the assertion |

This ledger does not claim that remote review threads have been resolved. Fresh Gradle
runs must execute serially with `--rerun-tasks --no-build-cache`; retain the exact
`N actionable tasks: N executed` summary, XML results, and named mutation evidence.
Use the mutation harness for every source mutation and byte-exact restoration.

## §10 Sequencing

PR-A is merged. Documentation closure and its outstanding press probe precede completion
claims. **PR-B follows PR-A** because both change the same controller scroll columns.
PR-B binds controller rotary input and restores hierarchy focus; the numeric editor
keeps value-stepping behaviour. PR-C follows PR-A and establishes the visibility
instrument. PR-D ships the compact layout and mandatory visibility checks together.
See [Wear UI completion](wear-ui-completion.md) for ambient, ongoing, and privacy limits.

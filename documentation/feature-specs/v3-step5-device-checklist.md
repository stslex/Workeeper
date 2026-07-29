# v3 step 5 — device checklist

> **SUPERSEDED** by [v3-device-regression.md](v3-device-regression.md) — the consolidated
> one-pass regression covering #177–#191. Do not execute this file; it is kept for the
> record of what step 5 owed.

Everything in this step that no gate can see. Paparazzi renders **one frame of one window**,
so both wow moments, every sheet and dialog, and anything time-based are outside it (§10.4).
This list is the verification for those, and it is a required deliverable of the step rather
than a nice-to-have.

Run it in one pass on a real device. Each item states **what to do** and **what correct looks
like**. Where an item can fail in a way that looks like success, that is called out.

**Build:** `./gradlew :app:dev:installDebug`

---

## A. The progress rail — geometry and degradation

The rail's two thresholds are **unverified numbers taken from a desktop browser**. They have
never been checked on a display. This section is what they are waiting on; until it is done,
`MIN_SEGMENT_WIDTH` and `MIN_GROUP_WIDTH` are guesses with decimal points on them.

Reference levels, computed at the golden's 392dp width — **the four mockup presets reach only
two of the three levels**, so the ladder cannot be walked by exercise count alone on a normal
phone:

| exercises × sets | segments | expected level |
|---|---|---|
| 2 × 4 | 8 | sets |
| 5 × 4 | 20 | sets |
| 8 × 4 | 32 | exercises |
| 16 × 5 | 80 | exercises |
| 16 × 5, narrow rail | 80 | overall |

- [ ] **A1 — sets level, few exercises.** Start a session with **2 exercises × 4 sets**.
      *Correct:* one segment per set, 8 in total, in 2 visibly separate groups. The gap between
      groups is clearly wider than the gap between sets inside a group. Every segment reads as
      a discrete pill, not a line.

- [ ] **A2 — sets level, at the busier end.** A session with **5 exercises × 4 sets**.
      *Correct:* still one segment per set (20), still grouped. This is the case closest to the
      `sets → exercises` boundary, so it is the one to judge: if any segment reads as a sliver
      rather than a pill, `MIN_SEGMENT_WIDTH` (9dp) is **too low** and must be raised. Record
      the verdict either way — "looked fine" is a measurement here.

- [ ] **A3 — the sets → exercises boundary.** A session with **8 exercises × 4 sets**.
      *Correct:* the rail collapses to **one segment per exercise** (8 segments), not 32.
      *Failure mode that looks like success:* if it still shows 32 slivers, the threshold is too
      low. If it shows a single bar, it has over-degraded and `MIN_GROUP_WIDTH` (11dp) is too high.

- [ ] **A4 — many exercises.** A session with **16 exercises × 5 sets**.
      *Correct:* one segment per exercise (16). It should **not** be a single bar at normal
      width — that is expected only in A5.

- [ ] **A5 — the exercises → overall boundary.** Reproduce a narrow rail: rotate to landscape
      on a small device, or use split-screen / freeform to squeeze the app to roughly a third of
      the screen, with the 16 × 5 session open.
      *Correct:* the rail becomes a **single continuous bar** whose filled fraction matches the
      done-sets fraction. Note the width at which it flips — that number is the real
      `MIN_GROUP_WIDTH` evidence.

- [ ] **A6 — band legibility.** At every level above, look at the rail's **height** (9dp) and
      the gap above it (22dp).
      *Correct:* the band reads as a deliberate element, not as a hairline or a divider. Both
      values are off the `AppDimension` ladder deliberately (§0.1 would round them to 8dp and
      24dp); if 9dp reads as too thin on hardware, 8dp is the fallback and this is where that
      is decided.

- [ ] **A7 — skipped vs unfilled must not look alike.** In a session, skip one exercise.
      *Correct:* its rail group renders as **outlines** — visibly a different treatment from an
      unfilled (not-yet-done) segment, which is a solid track. If the two read the same, the
      rail is lying about what is excluded from the denominator.

- [ ] **A8 — the rail agrees with the cards.** Count the segments and compare with the sets
      visible on the cards below.
      *Correct:* identical totals. The rail is projected from `visibleSets`, so any disagreement
      is a real defect, not a rounding artefact.

---

## B. fontScale — the reason the rule is not `WindowSizeClass`

`MainActivity` declares `fontScale` among its 17 absorbed `configChanges`, so **the Activity is
never recreated** and nothing is re-created from scratch. That is precisely why the degradation
rule is computed at the layout point. These items prove that choice was necessary.

- [ ] **B1 — fontScale 1.0 baseline.** Settings → Display → Font size at default, 8 × 4 session
      open. Note the rail level.

- [ ] **B2 — fontScale 2.0, changed WHILE THE SCREEN IS OPEN.** Do not restart the app. Change
      the system font size to maximum with the session screen in the foreground.
      *Correct:* the rail re-evaluates and may drop a level as the surrounding text grows and
      the available width shrinks. It must never freeze at the level from B1.
      *This is the item that catches a screen-level computation* — a `WindowSizeClass` read once
      would go stale here and the failure would be invisible in every screenshot.

- [ ] **B3 — fontScale 2.0, everything else.** With the large font still set, check the header,
      the set rows and the finish dock.
      *Correct:* no clipped text, no overlapping, the rail still aligned to the screen gutters.

---

## C. Set closure — the merged motion (§9)

Not gated at all: time-based, and the goldens capture only resting states.

- [ ] **C1 — ordinary closure.** Tap the mark on a set with reps entered.
      *Correct:* the circle morphs to a filled plate with a slight overshoot-and-settle (it
      should look physical, not linear), the row flashes once and fades, and the matching rail
      segment fills. All three read as **one** event, not three.

- [ ] **C2 — timing.** The same tap, watched for duration.
      *Correct:* the morph is ~260 ms and the flash decays over ~520 ms. It should feel
      immediate. If it feels like waiting, the merge has regressed into a sequence.

- [ ] **C3 — a record is not a second animation.** Log a set that beats the exercise's previous
      best, so it is both a closure and a PR.
      *Correct:* **exactly one** animation, the same shape and duration as C1, resolving to
      **molten** instead of max on the mark, the flash and the rail segment.
      *Failure mode:* two animations playing in sequence, or a visibly longer one. §9 forbids
      queueing precisely because a record almost always *is* a closure.

- [ ] **C4 — no colour artifact from the overshooting curve.** Watch the PR closure closely,
      several times, ideally in both themes.
      *Correct:* the molten tint fades cleanly. §5's `spring` easing peaks past 1.0 and is
      applied to **geometry only**; a colour driven by it would clamp or flicker at the peak.
      Any flash of white, black or a wrong hue at the moment of peak is this bug.

- [ ] **C5 — repeat closure.** Uncheck and re-check the same set a few times quickly.
      *Correct:* the flash re-fires each time from full strength; no stuck overlay, no
      accumulating tint.

- [ ] **C6 — reduced motion.** Enable "Remove animations" in accessibility settings.
      *Correct:* the app remains usable and set closure still visibly registers. (The mockup
      honours `prefers-reduced-motion`; this build's behaviour under it is **unverified** and
      is a finding to record either way.)

---

## D. Disclosure (§7) — the two halves no gate can reach

The transition table itself is unit-tested (`DisclosureAutomatonTest`, 14 cases). What is not
testable is stickiness across real lifecycle events.

- [ ] **D1 — auto-expansion on entry.** Open a session with several exercises, none started.
      *Correct:* exactly **one** card is expanded — the **first by position** among unfinished.

- [ ] **D2 — progress keeps a card open.** Log one set in the second exercise.
      *Correct:* it stays expanded, and the first card also stays open (it still holds the auto
      slot). Manual expansions are **additive**; this is not a single-selection screen.

- [ ] **D3 — auto-collapse on completion, before any manual action.** From a fresh entry with
      no taps on any card header, complete every set of one exercise.
      *Correct:* it collapses by itself.

- [ ] **D4 — the mute.** Now tap any card header once (this is the first manual action). Then
      complete a different exercise.
      *Correct:* the newly completed exercise **does not** auto-collapse. After the first manual
      action the auto rule stops collapsing anything for the rest of the screen session.

- [ ] **D5 — a completed card can be opened.** Tap a collapsed, completed exercise.
      *Correct:* it expands and its add/remove-set buttons are reachable. This is the only route
      to those buttons.

- [ ] **D6 — manual expansion survives rotation.** Expand a card the automaton would have left
      closed, then rotate the device.
      *Correct:* it is still expanded. The Activity is not recreated, so this must hold.

- [ ] **D7 — manual expansion survives the plan editor.** With a manual expansion in place, open
      an exercise's plan editor (full-screen route) and come back.
      *Correct:* still expanded. A push-and-return is **not** "leaving the screen session" — the
      Store survives it, and `Init`/`Reload` now carry the intent across their State rebuild.
      *This is the specific gap this step fixed; before it, the expansion reset here.*

- [ ] **D8 — reset on navigating away.** Back out of the session entirely, then re-enter it.
      *Correct:* disclosure is back to the D1 state — one card, the first unfinished. Manual
      intent is gone. The Store dies with its back-stack entry, which is what ends stickiness.

---

## E. One-off exercises (§6.2) — the plan-attached axis

- [ ] **E1 — the toggle appears only where it should.** Open the exercise menu on an exercise
      that came **from the training plan**.
      *Correct:* **no** "only for today" affordance. The axis does not apply to it.

- [ ] **E2 — the toggle appears on a mid-session addition.** Add an exercise mid-session, then
      open its menu.
      *Correct:* the "only for today" control is present and **off by default**.

- [ ] **E3 — a one-off does not edit the template.** Add an exercise mid-session, mark it
      one-off, log a set, finish the session. Then open the training's plan.
      *Correct:* the exercise is **absent** from the saved plan. This is the whole point of the
      step: before it, every mid-session addition permanently edited the template.

- [ ] **E4 — a normal addition still does edit the template.** Repeat E3 **without** the one-off
      toggle.
      *Correct:* the exercise **is** in the plan afterwards. Both directions must hold, or the
      flag is being ignored.

- [ ] **E5 — a one-off still remembers its sets.** After E3, start a fresh session and add the
      same exercise again.
      *Correct:* it is seeded with the sets logged last time. A one-off's plan is written to the
      exercise rather than to the training, so the baseline survives even with no plan row.

- [ ] **E6 — an inline-created one-off is not stranded.** Create a **new** exercise inline from
      the picker, mark it one-off, log a set, finish. Then look for it in the exercise library.
      *Correct:* it is **there**. It graduated. *This is the regression the DAO join change
      exists to prevent* — with the old plan-table join it would have stayed invisible forever,
      and nothing on screen would have said so.

- [ ] **E7 — cancel still cleans up.** Quick start a session, create an exercise inline, mark it
      one-off, then **cancel** the session. Check the library.
      *Correct:* the inline exercise is **gone**, and any library exercise you also picked into
      that session is **still there**.

---

## F. The unfilled-set line (§6.1)

- [ ] **F1 — the line appears, with a real unfilled row present.** Open a session, leave at
      least one set row with an **empty reps field**, and tap Finish.
      *Correct:* the confirm dialog states how many empty sets will not be saved, with the right
      count, above the personal-records block.

- [ ] **F2 — the line is absent when it should be.** Fill in every visible row, then Finish.
      *Correct:* **no** such line. A zero must hide it, not print "0 empty sets".

- [ ] **F3 — Russian locale.** Repeat F1 with the device in Russian.
      *Correct:* correct plural form for 1, 2 and 5 empty sets (`один` / `два` / `пять` take
      three different forms). Both locales ship all four plural categories.

- [ ] **F4 — the count is honest.** Compare the number in the dialog with the empty rows you can
      actually see, and remember that **skipped** exercises are excluded on purpose.
      *Correct:* they match.

---

## G. Regression sweep — things this step moved that it did not intend to change

- [ ] **G1 — the header no longer has its own progress bar.** The old
      `LinearProgressIndicator` was removed; the rail replaces it.
      *Correct:* exactly **one** progress indicator on the screen.

- [ ] **G2 — finish still works end to end.** Finish a normal session.
      *Correct:* saved-snackbar, navigation to the past session, and the past-session summary's
      set count matches what was logged.

- [ ] **G3 — skip and unskip.** Skip an exercise, then return it to the session.
      *Correct:* reversible in place, no snackbar, and the rail updates in both directions.

- [ ] **G4 — both themes.** Walk the screen in light and dark.
      *Correct:* the rail's track, fill and molten record all read correctly in both; the
      signature inversion means light is not simply dark with swapped values.

---

## Recording the outcome

For **A2, A3, A5 and A6** write down the verdict and any width at which a level flipped, even
when nothing looked wrong. Those four are the evidence `MIN_SEGMENT_WIDTH`, `MIN_GROUP_WIDTH`
and `RAIL_HEIGHT` are waiting on, and their KDoc points here by name. A pass with no number
recorded leaves them exactly as unverified as they are today.

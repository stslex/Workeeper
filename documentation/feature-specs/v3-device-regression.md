# v3 — Consolidated Device Regression

**Build:** dev after #189 → #190 → #191 (retargeted) are merged. One pass, one build.
**Sources merged:** every unexecuted device checklist from #177 through #191, deduplicated.
**How to read:** each item is *do → correct looks like*. Items marked ⚑ close or feed a registry entry.

---

## 0. Read this first — expected findings, do NOT file as bugs

These will look wrong. They are known, registered, and out of this regression's scope:

| What you'll see | Why | Registry |
|---|---|---|
| Weightless exercises render oddly anywhere (chart shows «МАКСИМАЛЬНЫЙ ВЕС» over rep values; any 0-weight artifacts) | whole cluster deferred to its own arc; captured in goldens as-is | B11 |
| Weighted exercise with unfilled weight shows "0kg / 0×N" | production defect, predates the arc | B11 |
| Exercise-detail hero shows date only, no training name | PR flow carries no training name; needs the #178 parity surface | B13 |
| No way to jump from a chart point to its past session | the tooltip (only path) died with the mockup's scrub redesign | B14 |
| Settings → Архив row has no counts sub-line | no data source exists; hardcode would lie | B15 |
| Fractional weight "102.5" downsteps one type rung in the set row | measured interim; real resolution pending | open |
| Non-rebuilt screens (home, trainings list, exercises list, archive, editors, backup detail) look "new palette on old structure" | they received tokens globally but are stage-5 rebuilds | plan |
| Chart topbar has no ⋮ menu | mockup's ⋮ has no handler even in the mockup | errata |
| Chart exhead title-to-swap gap is 12dp, not the extraction ladder's 8dp | ships per spec §0.2 nearest-rung rounding; the extraction's 8dp is superseded — not a finding | resolved |

---

## 1. Global sweeps (run once, revisit per screen only if odd)

- [ ] **Status bar** — API 28, 34, 35+ · light + dark: no purple, no pre-Compose flash; window background matches the first composed frame. *(#177 debt, oldest unexecuted item in the arc)*
- [ ] **RU rendering** — no tofu anywhere; headings render at real 600 (visibly heavier than body), not synthesized. *(#177/#185)*
- [ ] **fontScale 1.0 → 2.0** (MainActivity absorbs the change — no Activity recreation): every rebuilt screen reflows live; rail degrades correctly; nothing clips. Toggle it once mid-session.
- [ ] **Theme toggle live** (Settings → mseg): every screen re-skins without restart; both themes checked on each screen below at least once.
- [ ] **Tracking** — session/topbar headings visibly tighter than body text. *(#185 C3)*

---

## 2. Session (live workout) — #186 + amendments

**Header / frame**
- [ ] Header is three texts on the page — NOT a card.
- [ ] Timer: Archivo at the top rung, tnum — digits don't wobble as it ticks. ⚑ *Archivo wdth 116 first light on hardware.*

**Rail**
- [ ] Degradation boundaries at both thresholds (9dp / 11dp constants): grow a session past each boundary, the rail switches sets → exercises → overall; `railmeta` label always agrees with the drawn level.

**Cards / disclosure (four-rule contract)**
- [ ] First card expanded on entry — even if it is completed.
- [ ] Toggle is pure: expand touches nothing else; collapse touches nothing else.
- [ ] **Two cards open → both lift, both stay.**
- [ ] **Complete the last set of an open card → the card does NOT collapse.**
- [ ] Skipped cards toggle like any other.
- [ ] Plan-editor round trip → expansion survives.
- [ ] Light theme: lift shadow readable in the 8dp LazyColumn gaps — not clipped, not "tight".

**Set rows**
- [ ] Done-marker: tap → circle morphs to 13dp squircle, grows 38→42, tick strokes in after ~60ms; press-and-hold → scale(.9).
- [ ] Reopen a finished session → completed marks appear ALREADY DRAWN, no animation replay.
- [ ] PR set: both fields washed molten-bg, value molten at 26sp — the value reads as the dominant element of the row. No double animation when PR coincides with set closure; no colour artifact from the overshoot easing.
- [ ] Set deletion: "− set" removes the last row. Exercise deletion: via sheet, undo toast 5s, undo works, after 5s it is gone.
- [ ] Unfilled set present at finish → FinishConfirmDialog carries the count line.

**Sheets (out-of-window — the gate never saw these)**
- [ ] All four sheets + toast carry new palette/typography/structure: exercise menu (one-off toggle **only on mid-session additions**, off by default), plan-removal confirm, session menu, description sheet.
- [ ] «подход» wording everywhere (finish-dialog stat row, overflow items) — no truncation from the longer noun.

---

## 3. Past session — #187

- [ ] **Tonnage vs hand-computed** on your real data (weighted sets only, Σ weight×reps). ⚑ *the one figure no gate can check.*
- [ ] Header: eyebrow / hero duration / tonnage line — plain, not a card.
- [ ] Edit a set → persists across leave-and-return (round trip).
- [ ] Logged values read as logged (isLogged state), not as empty placeholders.
- [ ] Disclosure: first card open on entry; two open → both lifted.
- [ ] Record row carries the tag on the correct set (post-#178 canonical rule — badge may sit on a different set than v2.4 did for weightless-residue history; that movement is the FIX).
- [ ] Section head reads "Записано / можно править".
- [ ] Overflow ⋮ sheet replaced the old error-tinted delete icon; every action dispatches.

---

## 4. Exercise detail — #188

- [ ] Record hero vs known history: value matches the canonical rule's answer; date correct; **date-only sub-line is expected (B13)**.
- [ ] "9 × 12": the × comes from the mono span — renders cleanly at size, no baseline jump against Archivo digits.
- [ ] Default-plan card matches the saved plan (ord + val rows, ruled).
- [ ] History: 88dp rows, ruled N+1, count in the section head matches reality; PR tag sits on the record row.
- [ ] **PR explainer opens from the history record tag** (entry moved from the deleted badge — confirm the path survived).
- [ ] Overflow sheet: Изменить · В архив · Удалить навсегда — each dispatches; destructive styling correct.
- [ ] Dock: ghost "Изменить" + primary "Записать сейчас" overlay the scroll on the base gradient; insets correct.

---

## 5. Chart — #190

- [ ] Three metrics on a WEIGHTED exercise: Вес / Сессия / Подход — values sane, per-session volume sums correctly (spot-check one session by hand).
- [ ] Tab indicator slides (transient) — travel is smooth, lands exactly.
- [ ] Ranges 1М/3М/1Г/Всё as chips; active state correct.
- [ ] Canvas: ONE max-coloured line; plain points hollow; **record point molten-solid on hardware** — reads as the accent at arm's length. ⚑
- [ ] Scrub: drag → dim dashed line + point activation; record point STAYS molten under scrub; readout updates.
- [ ] Empty state with <2 sessions: "Пока нечего показать", no buttons. ⚑ *closes the #178 outstanding empty-chart case.*
- [ ] Empty variants that kept CTAs (undrawn in mockup, kept deliberately) — CTAs work.
- [ ] Exercise picker window (out-of-window) — new skin, selection works.
- [ ] exhead pressed state + TalkBack label.
- [ ] Plurals on the stat rows.

---

## 6. Settings — #191

- [ ] Group order per extraction §5.6 (the old fully-reversed order is dead); groups are label + rows on the page — NO bordered container anywhere.
- [ ] **Units row is absent** — not disabled, not stubbed, gone.
- [ ] Theme mseg: icon control travels correctly; theme applies live. ⚑ *transient pair on device.*
- [ ] AI-export row: switch has the spring feel; **caption = the consent-bearing wording (reverted in phase 1)**.
- [ ] Restore flow END-TO-END including the restart — the reskin was forbidden from touching the flow; prove it didn't. Both IntentSender round-trips (account pick, file ops).
- [ ] Both confirm dialogs (sign-out, revert) — new skin, correct destructive styling.
- [ ] Frequency sheet (out-of-window): structure untouched, controls in the new vocabulary.
- [ ] Rust rows: text-only on the page surface; sub-line "3 копии · последняя минуту назад" on the restore row.
- [ ] AuthPausedBanner untouched and functional.
- [ ] Every navigable row reaches its destination.

---

## 7. Cross-cutting close-out

- [ ] **Out-of-window inventory**: every sheet, dialog, dropdown and menu opened at least once across the five screens above — each carries new palette + typography. (18 sites repo-wide; the five rebuilt screens cover their share; derived screens keep old skins until stage 5 — expected.)
- [ ] Both wow moments witnessed on hardware at final quality: set closure (morph + flash + segment) and PR (molten resolve) — merged, never sequenced.
- [ ] One deliberate kill-test: background the app mid-undo-toast → deferred delete resolves safely (kill-safe claim from #186).

---

## After the pass

File findings in three buckets: **regression** (worked before the arc, broken now) · **design miss** (differs from mockup beyond the cited deviations in each PR's element table) · **already-registered** (§0 above — skip). Anything in bucket 1–2 gets a registry entry before it gets a fix.

# Home — the start card

Destination in the tree: `documentation/feature-specs/home-start-card.md`

Governing documents this one does not restate: `v3-redesign-spec.md` (palette, type, gates),
`design-system.md`. Drawing of record: `home-start-modes-v4.html`, marker **старт·4** — **not in
this repository**, same carve-out as the editors arc. Every "as drawn" below means that file.

---

## 0. Why

`HomeStartCard` is the app's primary action and the one surface v3 never touched. Four defects,
each measured against the shipped file, not judged by eye:

1. **It wears molten.** `Icon(Icons.Filled.PlayArrow, tint = AppUi.colors.accent)`. In v3 the
   accent is the personal record and nothing else. The app's only loud colour is spent on a routine
   action, which is why the card is the screen's most prominent object for no reason.
2. **The glyph is from another language.** A filled Material triangle against v3's 1.7–1.8 stroke
   set with round caps.
3. **It is centred.** Nothing in v3 is centred — rows, cards and docks are left-aligned with values
   at the right edge. An icon-over-title-over-subtitle column is the generic empty-state template.
4. **The subtitle describes the mechanism.** «Выберите шаблон тренировки» says what opens next, not
   what happens — and it is inaccurate, since a blank session starts from here too.

The card also does nothing but launch. This spec makes it a **readout plus an action**, with the
readout switchable, because no single readout is right for every user.

---

## 1. Locked decisions

| # | Decision |
|---|---|
| **HS1** | The card is one shell for all modes: **head** (mode label, tappable, carrying a caret), **body** (mode-specific readout), **action** (primary button, right of the body). The mode changes the body only; the button never moves. |
| **HS2** | Four modes ship: **Неделя**, **Дни без тренировки**, **Отставшие группы**, **Забытая тренировка**. |
| **HS3** | Default is **Неделя**. |
| **HS4** | The mode is switched by the **head label itself** — label plus caret, one target, opening a sheet. Not a `⋮` button: a right-edge control stacked over the right-edge primary button overweights the right column against the readout the card exists for. Referent: `.exhead` in the chart section, where the title names the current selection and carries the control that changes it. |
| **HS5** | The same sheet is reachable from **Settings**. Two entry points, one sheet. |
| **HS6** | The chosen mode is **persisted in DataStore** and survives process death. Without it the mode resets to Неделя on every cold start and the switch reads as broken. |
| **HS7** | Molten appears nowhere on this card in any mode. |
| **HS8** | No mode invents data. Every readout below names its query; if a mode has nothing to show, it says so in an empty state — it does not render an empty card. |

---

## 2. The shell

```
┌──────────────────────────────────────────┐
│ ЯРЛЫК РЕЖИМА ⌄                           │   head: .label + caret, one target
│                                          │
│ <показание>                  [ Начать ]  │   body + primary action
│                                          │
│ <продолжение показания, если есть>       │
├──────────────────────────────────────────┤
│ <подвал, только там где нужен>           │   .setbar
└──────────────────────────────────────────┘
```

Surface `--slab` + `--slabtop` (`AppCard` in its lifted form). The head is a button; its hit area
takes the platform minimum regardless of the label's 11px type. The action is the primary button,
compact (auto width), not full-bleed — the card is a readout with a control, not a banner.

---

## 3. The four modes

### 3.1 Неделя — default

Readout: sessions this week as a big Archivo numeral with its unit, then seven pills, one per
weekday, filled where a session finished, with weekday labels beneath.

Query: finished sessions in the current week. Trivial.

The pill-per-unit form is drawn — the session rail lays one pill per set and fills what is done;
here the unit is a day.

Known cost, recorded rather than hidden: seven cells silently imply a weekly target the user never
set. Accepted as the default anyway because it is a **log, not a grade** — it reports what
happened and asserts no number.

### 3.2 Дни без тренировки

Readout: days since the last finished session, big numeral plus unit, and beneath it the name and
date of that session so the number has an anchor.

Query: `MAX(finished_at)`. Trivial.

### 3.3 Отставшие группы

Readout: up to three tags, longest-idle first, each a name, a monochrome bar whose length is
proportional to days idle, and the day count at the right edge. The word «дней» appears **once**,
under the group, not three times.

Query: `performed_exercise → exercise → exercise_tag → tag`, max finished date per tag.

This is the only mode that depends on tags. Tags live on **exercises**, not trainings, so it needs
no global change — but where exercises are untagged it has nothing to show, and that is an empty
state (HS8), not an empty card.

### 3.4 Забытая тренировка

Readout: the training template whose last run is furthest in the past — its name, then days idle
and its composition. The primary button starts **that training** directly. A `.setbar` footer
carries «Другая тренировка».

Query: finished sessions grouped by `session.training_uuid`, max `finished_at` per training,
ordered ascending. `SessionEntity.trainingUuid` is non-null with a CASCADE FK to `training`, so the
link is direct — **no tags anywhere in this mode**.

**`is_adhoc` is the discriminator.** `TrainingEntity` carries `is_adhoc`; a free session mints an
ad-hoc training row through `LiveWorkoutInteractor.createAdhocSession`. Ad-hoc rows are **excluded**
from this metric — they are sessions, not templates, and including them would surface a row the
user cannot meaningfully "do again".

Also excluded: `archived` trainings.

---

## 4. Open decisions — mine to make, before the PR

| # | Question | Why it is not obvious |
|---|---|---|
| **HD1** | **Забытая тренировка: a template never run.** By the metric it is infinitely forgotten and belongs first. Either it ranks first, or never-run templates are excluded — and then the mode is silent about exactly the thing just created. | both readings are defensible |
| **HD2** | **Забытая тренировка with no templates at all** (fresh install, or everything archived). The card falls back to — an empty state with the plain button, or an automatic fallback to Неделя? An automatic fallback changes a mode the user chose. | |
| **HD3** | **Отставшие группы with no tagged exercises.** Same shape as HD2, same question. | |
| **HD4** | **Дни без тренировки / Неделя on a fresh install** — no sessions at all. One empty state serving every mode, or per-mode copy? | |
| **HD5** | **Settings entry (HS5)** — its own row, or inside an existing group? Needs a look at the settings screen as it stands. | |

---

## 5. What changes in the tree

- `feature/home/.../components/HomeStartCard.kt` — rebuilt as the shell plus four bodies.
- `feature_home_start_cta_title` / `feature_home_start_cta_subtitle` — the subtitle dies (defect 4);
  the title survives only if a mode still needs it.
- `CommonDataStore` gains the mode: it already carries `homeSelectedStartDate`,
  `homeSelectedEndDate` and `themePreference`, and the new key follows the same shape
  (`Flow<String>` plus a setter) rather than inventing a second mechanism.
- The home store gains the mode in state and the readouts it needs.
- `feature/settings` gains the entry (HS5, HD5).
- No migration. Every query above reads columns that already exist.

---

## 6. Gates

Unchanged from the arc: per commit — compile, detekt with zero suppressions, unit tests, Paparazzi
in both themes, cyclic-proof; every gate proven in both directions;
`--rerun-tasks --no-build-cache`; `--stop` before measuring; detekt and tests as separate
invocations; every new golden looked at, not merely recorded.

Goldens: four modes × 2 themes, the empty state of each mode that has one, and the mode sheet × 2.

The persistence rule (HS6) is a **behavioural** gate, not a golden: the mode survives process death.
Test it as such.

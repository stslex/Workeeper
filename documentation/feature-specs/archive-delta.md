# Archive — delta mapping (v3 stage 5, group 3)

**Status: BUILT, except §2.1 — which is now RULED and scheduled, not open.**
Everything else in this mapping is applied. Facts below are verified against the tree.

**§2.1 IS RULED (§24.2 group A, Ilya): an archived item OPENS — read-only detail.** So the drawn chevron is true, the row takes the drawn 20dp slot like its three siblings, and restore + permanent-delete come off it. The three readings §2.1 sets out below are **superseded** — reading (a), navigation, is the one taken — and they are kept because the argument against each is the working the ruling rests on. **Do not implement this screen from §2.1's unresolved framing:** it is the pre-ruling text. What is still open is *where the two verbs go* once off the row, and how an archived item reaches its chart (§24.2 group A, the two open items).

Read against `feature/all-exercises` as rebuilt, on `feature/v3-all-exercises`.

## What this is

[`all-trainings-extraction.md`](all-trainings-extraction.md) is the full extraction;
[`all-exercises-delta.md`](all-exercises-delta.md) is the first delta against it. This is the second
delta, and it cites both rather than re-deriving either. **Citations are by anchor and by symbol,
never by line.**

**Verdicts on behaviour say whether they are verified.** §27 now requires it: a behavioural MATCH
either cites a test covering both sides or is marked **UNVERIFIED**. The all-exercises mapping
recorded a long-press site as MATCH while that screen fired two haptics, and nothing could
contradict it because no test existed. Appearance rows are exempt only where a golden covers both
sides.

---

## 1. Archive is the **fourth payload**, and it is drawn

`#s-list`'s hint says the skeleton serves four screens — "Скелет строки один — 88px … **Начинки
разные: поля у четырёх экранов не совпадают**" — and its first frame draws all four, top to bottom:
recent session, training, exercise, **archive**. The fourth row is this screen's:

```
Румынская тяга
упражнение · в архиве с 3 июля
```

That answers the question this mapping was expected to open. **The kind is the meta line's first
token, as a word** — «упражнение» / «тренировка» — which is the same rule all-exercises applies to
the exercise type, for the same stated reason: the line does not wrap, the tail truncates, and the
token that must survive goes at the head.

So the kind does **not** need a badge, a leading glyph or a second line. It also does not rely on
the segmented control to be legible, which matters: the segment is chrome and the row is content,
and a row that only makes sense under its filter stops making sense the moment it is screenshotted,
searched or reused.

| Field | `ArchivedItem.Exercise` | `ArchivedItem.Training` | Meta line |
|---|---|---|---|
| kind word | «упражнение» | «тренировка» | **first token** |
| `archivedAt` | ✓ | ✓ | «в архиве с <date>» |
| `tags` | ✓ | ✓ | tail, per §26 "Meta-line order" |
| `type` | ✓ | — | *not drawn in the archive row* |
| `exerciseCount` | — | ✓ | *not drawn in the archive row* |

The two kind-specific fields are carried by the sealed model and are **absent from the drawn
archive row**. That is a finding, not an omission to fix: the drawn row spends its one meta line on
kind and archive date, and §0.1 gives the drawing the decision.

---

## 2. The row — `ArchivedItemRow` against `ExerciseRow` as rebuilt

| Region | `ArchivedItemRow` today | After | Same as `ExerciseRow`? |
|---|---|---|---|
| Container | `clip(shapes.medium)` + `surfaceTier1` + `cardPadding`, inset by the list's `screenEdge` | full-bleed, `RectangleShape`, hairline rule | **yes** — the same card→row change both siblings made |
| Name | `bodyMedium`, no `maxLines` | `titleMedium`, `maxLines = 2`, ellipsis | yes |
| Tags | `LazyRow` of `AppTagChip.Static` | appended to the meta line as text | yes — §26 "Meta-line order" rejects in-row chips |
| Meta | `archivedAtLabel` in `bodySmall`, on its own line | one `mono.meta` line: `kind · archived-since · tags` | **no** — the kind token is this screen's own region (1). **BUILT**, composed in `ArchiveUiMapper` rather than in the row: archive already had a `ResourceWrapper` mapper, and only the mapper form is assertable without a composition — `ArchiveMetaLineTest` covers kind-first, tags-last, no dangling separator, and the date fallback. **The formatter changed with the phrasing and had to**: «в архиве с …» cannot take a relative span («since 2 days ago» is not a sentence), so `getAbbreviatedRelativeTime` gives way to the existing `formatDayMonth`, which already renders «3 июля» / «July 3» and orders the two per locale. No new `ResourceWrapper` API. |
| Trailing | **two affordances**: a `Restore` text button *and* a `MoreVert` → `DropdownMenu` | see 2.1 | **no — the open question, and UNTOUCHED by this build.** Extracted into `TrailingAffordances` with a comment naming §2.1, so the next reader cannot collapse it into the drawn slot without meeting the ruling first. |
| Selection | none | none | n/a — no selection mode here |

### 2.1 The trailing slot is the screen's real question — SUPERSEDED, see the status note above

`#s-list` gives a row **one** trailing slot, 20px wide, holding a chevron, a check, or nothing. This
screen's row carries two affordances inside the row body: a `Restore` button and an overflow menu
whose single item is permanent delete.

Neither fits the drawn slot, and the drawing does not answer it — the archive row it draws carries a
**chevron**, i.e. it navigates, and says nothing about restore or delete. Three readings, and this
mapping does not pick one:

1. **The row navigates** (chevron, as drawn) and restore/delete live on the destination. Cheapest
   against the contract; costs a tap for the primary action of the screen.
2. **The row keeps a menu**, and the drawn chevron is the shell's generic filler rather than a
   ruling about archive. Then the trailing slot holds an overflow glyph — but `.mseg`-style overflow
   is drawn nowhere in a list row, so it is a new mark.
3. **Swipe actions**, which nothing in the shell draws at all.

**Both verbs are live — checked before weighing the readings.** B23 found `all-exercises`'
permanent-delete dialog dead (read three times, written non-null nowhere), which would have
collapsed this question from two verbs to one. Archive's is **not** that shape:
`ArchiveClickHandler.processDeleteRequest` writes `pendingDeleteTarget = item` from
`Action.Click.OnPermanentDeleteClick`, that action is dispatched by the row's `DropdownMenuItem`,
and the handler then fetches an impact count before showing the dialog. Restore is equally live.
So the slot really is contested, and the three readings stand.

Worth carrying to B23: two screens ship a permanent-delete dialog, one fully wired and one with no
producer at all. That makes the dead one an **omission** rather than a deliberate design, which is
what the registry entry left open.

**This is a §0.1 question for the owner, not a delta to apply.** It is the one region where this
screen genuinely differs in kind from its two siblings: their rows have a single destination and a
selection mode, and this row has two competing verbs and no selection mode to put them in.

**Left exactly as it was, and the surrounding rebuild landed around it.** One consequence is worth
stating rather than discovering on a device: while both affordances stay in the row, **the row is
taller than the drawn 88dp in practice** and its trailing region is not the drawn 20px slot. The
`heightIn` minimum is the drawn one, so the row is *bounded* correctly and is not yet *the* drawn
row — and it cannot be until this is ruled. The goldens recorded in §4 photograph the current
arrangement deliberately: per §10.2 a golden locks in what **is**, so when §2.1 is ruled these
images are expected to move, and that movement is the reason for recording them now.

Concretely, `ArchivedItemRow` is the one consumer of `AppListRow` that does not use
`AppListRowSlot` — home's `RecentSessionRow`, all-trainings' `TrainingRow` and all-exercises'
`ExerciseRow` all do. It stays un-slotted **by schedule, not by exception**: do not wrap its
`TrailingAffordances` in `AppListRowSlot` piecemeal — the slot arrives together with the row
click and its destination, or not at all.

**`DropdownMenu` is out of Paparazzi's model** (its own window, §10.4), so whatever survives here is
partly ungated by construction — which is an argument against reading 2 that is worth weighing
alongside the drawing.

---

## 3. Chrome

| Region | Today | Referent | Verdict |
|---|---|---|---|
| Segmented control | `AppSegmentedControl`, already `liftedSurface`-based (track + lifted thumb) | `.mseg` in `#s-set` | **likely MATCH — appearance, to be confirmed by measurement against `.mseg`'s declared properties** |
| Segment labels | `stringResource(…, count)` formatted **in the UI**, with a `TODO(tech-debt-localization)` in place | — | **DELTA — BUILT.** `exerciseSegmentLabel` / `trainingSegmentLabel` are formatted in `ArchivePagingHandler.segmentLabel` beside the counts they read, and arrive on `State` pre-joined. The TODO is discharged, not carried. |
| Tag filter band | none | none — archive is not drawn with one | **MATCH** (absence, drawn — appearance, covered both sides by the screen goldens) |
| Selection mode | none | `#s-list`'s selection frame is titled "режим выбора — **тренировки и упражнения**" | **MATCH — UNVERIFIED (behaviour).** The drawing names which screens have it and this one is not among them, so the absence is contract; but no test asserts that a long press here does nothing, and §27 says a behavioural MATCH without a test on both sides says so. Cheap to close if it ever matters; recorded rather than claimed. |
| FAB | none | `#s-list`'s clearance navnote: "Запас нужен только тем экранам, где кнопка есть" | **MATCH** — and therefore **no 88dp clearance is owed**. Built as an absence: both `LazyColumn`s carry no `contentPadding` at all, with the navnote quoted at the site so the 88 is not added later by symmetry with the siblings. |
| List padding | `screenEdge` horizontal + `Space.sm` vertical + `spacedBy(Space.sm)` | full-bleed rows own their gutter | **DELTA — BUILT.** All three removed; the row owns its gutter and its rule, and the last row's rule is dropped (`showDivider = index < itemCount - 1`) per `.frame .row:last-of-type`. |
| Empty region | `archiveListSurface` — four verdicts | §26 "List states reached by an action" | **DONE this session** (B22) |
| Paging tails | **none** — `loadState.append` is read nowhere | `#s-list`'s pagination navnote | **BUILT.** `pagingTailKind` + `PagingTails`, dispatched from both lists; `feature_archive_paging_error` exists and is used, so the string comment that explained its absence is now the note explaining its arrival. `PagingTailKindTest` covers all four branches including the absence — third copy, per §27's MATCH rule. |

### 3.1 The pagination "contradiction" is not one — the screen is behind, the drawing is right

Flagged as a possible third instance of the contract disagreeing with itself, after variant B and
D6. It is not. **Checked rather than picked**, and the two sides say different things:

The navnote's claim is «Пагинация **уже есть** в тренировках, упражнениях и архиве» — pagination
already *exists* on those three. That is a statement about the **data layer**, and it is true here:

- `ExerciseRepositoryImpl.pagedArchived()` is a real `Pager(pagingSourceFactory = dao::pagedArchived)`;
- `ArchiveInteractor.pagedArchivedExercises()` / `pagedArchivedTrainings()` return
  `Flow<PagingData<…>>`;
- `ArchivePagingHandler` collects both and the screen presents them as `LazyPagingItems`.

Archive pages. What it does not do is **render the tails** — `loadState.append` is read nowhere, so
the drawn loading and error footers were never built. The navnote sits in the section whose whole
subject is the tails, and names those three screens in order to say the tails apply to them. It
described the data layer accurately and the screen simply never caught up.

**So no drawing correction is owed. It is a screen change.** Unlike variant B and D6 — where the
contract drew one component two ways — nothing here disagrees with anything. This mapping's earlier
framing of it as a self-contradiction was wrong.

### 3.2 "Pagination exists" and "the tails are built" are different claims, and the app has never conflated them in only one place

Three for three, identically, and that is what makes it a finding rather than three defects.

The app has **exactly three** paged screens — `collectAsLazyPagingItems` has three call sites:
`all-trainings`, `all-exercises`, `archive`. (The data layer has 14 `Pager(` call sites, so it pages
a good deal more than the UI ever presents.) At `origin/dev`, every one of the three read
`loadState.append` **exactly once**, and in all three it was the same line:

```kotlin
private fun LazyPagingItems<*>.isEmptyAndIdle(): Boolean =
    itemCount == 0 &&
        loadState.refresh is LoadState.NotLoading &&
        loadState.append is LoadState.NotLoading &&   // ← the only read, on all three
        loadState.prepend is LoadState.NotLoading
```

So it is sharper than "the tails were never built". **The append state was known to all three screens
and spent on the wrong question**: whether the list is empty, rather than what to draw at its end. A
screen that reads `append` to decide emptiness has already met the value it needed for the footer and
walked past it.

**Read that as evidence about the phrase, not about the screens.** «Пагинация уже есть» meant "a
`Pager` is wired and pages arrive" — and it was true, three times. It never meant "the three drawn
tail states are rendered", and nobody wrote it down because nobody noticed the two had come apart.
Three independent screens do not arrive at the identical omission by coincidence; they arrive at it
because "this screen pages" was understood the same way each time.

**For the five screens still to come:** approach each expecting `already paged ≠ tails owed`, and
check the two separately. The check is one grep — does anything read `loadState.append` outside an
emptiness predicate? — and it costs nothing next to discovering it per screen, three times, which is
what happened here. §26 "Paging tails" states the treatment; nothing until now stated that having a
`Pager` does not discharge it.

---

## 4. Gates

**BUILT.** `feature/archive` now carries the Paparazzi plugin, the shared `golden-gate` apply and
**14 recorded goldens** — the row in both payloads, the two-line clamp, both paging tails, and the
whole screen in each segment, each in both themes. The harness is **not** copied: it comes from
`core:ui:kit`'s `testFixtures`, so device config, tolerance and canvas width cannot drift from the
two siblings this screen must stay in step with.

`assertGoldenLiveness` reports `14 executed for 14 images`, so the green is a measured one and not a
silent skip. **Proven to discriminate rather than assumed**: mutating the name clamp from 2 lines to
1 reddened `rowClamped` in both themes (`VERIFY=1`, 4 failures), and the mutation was reverted.

Note the ordering this did *not* follow: the siblings recorded a baseline of the pre-rebuild surface
first, so a reviewer could read one image diff. Archive's rebuild and its first recording land
together, so **these images are the baseline, not a diff** — there is no before-picture for this
screen and there will not be one. That is a cost of building the delta and the gate in one pass; it
is recorded rather than hidden, and §10.2 already says a golden's guarantee is differential, so a
first recording guarantees nothing about correctness until the element-by-element pass reads it.

Already gated as of this session: `ArchiveListSurfaceTest` (four verdicts, all four mutations red).

What a golden will not reach on this screen, and therefore needs a named value asserted directly:

- the `archiveListSurface` verdicts — **done**;
- the meta line's composition — **done** (`ArchiveMetaLineTest`): a picture photographs whatever
  string it is handed and is right about the pixels either way, so kind-first, tags-last and the
  date's formatter are asserted directly;
- the append-tail decision — **done** (`PagingTailKindTest`), including the absence, which is the
  outcome a dropped branch produces by accident;
- the segment labels' composition — **done** (`ArchiveSegmentLabelTest` is owed; the labels are
  named on `State` and formatted in one place, which is the half that makes it assertable);
- the segment switch itself, which is a state change the screen's own `when` makes;
- `DropdownMenu`'s contents (its own window, §10.4);
- whatever replaces the trailing affordances, once 2.1 is ruled.

`FadeToTransparentRule` is live repo-wide and archive has no `Color.Transparent` animation target,
so it is clean by construction rather than by exemption.

---

## 5. Open, and owed before code

1. **2.1 — the trailing slot. The only one left, and it is the owner's.** Both verbs confirmed
   reachable, so it does not collapse to one. Everything around it is built; the affordances are
   untouched and the 14 goldens photograph them as they are.
2. ~~The paging tails~~ — **built**. A screen change, not a drawing correction (3.1).
3. ~~Segment label formatting~~ — **built**, into `ArchivePagingHandler` beside the counts.

**Owed, small, and not blocking:** a test for the segment labels' composition (the labels are named
and formatted in one place, which is the half that makes it possible); and the selection-mode absence
is a behavioural MATCH marked UNVERIFIED in §3 rather than claimed.

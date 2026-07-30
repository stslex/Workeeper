# Archive — delta mapping (v3 stage 5, group 3)

**Status: in progress. Facts below are verified against the tree; nothing here is built yet.**

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
| Meta | `archivedAtLabel` in `bodySmall`, on its own line | one `mono.meta` line: `kind · archived-since · tags` | **no** — the kind token is this screen's own region (1) |
| Trailing | **two affordances**: a `Restore` text button *and* a `MoreVert` → `DropdownMenu` | see 3 | **no — the open question** |
| Selection | none | none | n/a — no selection mode here |

### 2.1 The trailing slot is the screen's real question

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

**`DropdownMenu` is out of Paparazzi's model** (its own window, §10.4), so whatever survives here is
partly ungated by construction — which is an argument against reading 2 that is worth weighing
alongside the drawing.

---

## 3. Chrome

| Region | Today | Referent | Verdict |
|---|---|---|---|
| Segmented control | `AppSegmentedControl`, already `liftedSurface`-based (track + lifted thumb) | `.mseg` in `#s-set` | **likely MATCH — appearance, to be confirmed by measurement against `.mseg`'s declared properties** |
| Segment labels | `stringResource(…, count)` formatted **in the UI**, with a `TODO(tech-debt-localization)` in place | — | **DELTA** — the repo's own rule puts display strings in the UI mapper; the TODO says so |
| Tag filter band | none | none — archive is not drawn with one | **MATCH** (absence, drawn) |
| Selection mode | none | `#s-list`'s selection frame is titled "режим выбора — **тренировки и упражнения**" | **MATCH** (absence, and the drawing names which screens have it) |
| FAB | none | `#s-list`'s clearance navnote: "Запас нужен только тем экранам, где кнопка есть" | **MATCH** — and therefore **no 88dp clearance is owed** |
| List padding | `screenEdge` horizontal + `Space.sm` vertical + `spacedBy(Space.sm)` | full-bleed rows own their gutter | **DELTA** |
| Empty region | `archiveListSurface` — four verdicts | §26 "List states reached by an action" | **DONE this session** (B22) |
| Paging tails | **none** — `loadState.append` is read nowhere | `#s-list`'s pagination navnote | **UNBUILT, and the drawing is not at fault — see 3.1** |

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

`feature/archive` has **no Paparazzi, no golden suite, no `golden-gate` apply**. Same starting point
as both siblings, so the same order: plugin + apply + recorded PNGs in one commit,
`recordPaparazziDebug` first, baseline against the screen as it is now.

Already gated as of this session: `ArchiveListSurfaceTest` (four verdicts, all four mutations red).

What a golden will not reach on this screen, and therefore needs a named value asserted directly:

- the `archiveListSurface` verdicts — **done**;
- the segment switch, which is a state change the screen's own `when` makes;
- `DropdownMenu`'s contents (its own window);
- whatever replaces the trailing affordances, once 2.1 is ruled.

`FadeToTransparentRule` is live repo-wide and archive has no `Color.Transparent` animation target,
so it is clean by construction rather than by exemption.

---

## 5. Open, and owed before code

1. **2.1 — the trailing slot.** The one region the contract does not answer for this screen. Both
   verbs confirmed reachable, so it does not collapse.
2. **The paging tails** — a screen change, not a drawing correction (3.1).
3. Whether the segment labels' count formatting moves to the mapper with the rest.

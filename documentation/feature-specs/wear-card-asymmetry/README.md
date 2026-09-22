# Wear controller — value-card asymmetry, as rendered

Captured for the PR #284 review so the card ratio can be judged by looking at it. The
approved mockup had the two value cards equal; they landed unequal because the widest
weight (`999.99`) needs 65dp of content at the largest font scale while the widest reps
(`999`) needs 36dp, and equal halves gave both 60dp on a 192dp screen.

**These are historical PR #284 captures.** PR #285 subsequently moved the disabled
word into the button and changed Russian copy to “Отключено”; the disabled image below
still shows the earlier overlay. Current acceptance and outstanding visibility work are
tracked in [the bottom-band spec](../wear-bottom-band-rebudget.md#6-remaining-defects-and-ownership)
and [Wear UI completion](../wear-ui-completion.md).

Whether 1.53:1 reads as intentional or as lopsided is a design call, and no gate can
express it.

## How these were captured

A round Wear OS emulator (`Wear_OS_Large_Round`, density 320 → 2px per dp), driven to each
profile with `wm size`, the locale set per-app with `cmd locale set-app-locales`, and the
`active_boundary` fixture launched through `MainActivity`'s debug extra — the same fixture
the gates use, whose weight is the protocol maximum, so these are the widest values the
surface can ever show.

## Widths, measured from these images

Card fill (`#1E242A`) extents, read off the pixels rather than quoted from the layout:

| Image | Screen | Locale | Weight card | Reps card | Ratio |
| --- | --- | --- | --- | --- | --- |
| `small-round-192dp-en.png` | 192dp | en | **92dp** | **60dp** | 1.53:1 |
| `small-round-192dp-ru.png` | 192dp | ru | **92dp** | **60dp** | 1.53:1 |
| `xl-round-240dp-en.png` | 240dp | en | **121dp** | **79dp** | 1.53:1 |
| `xl-round-240dp-ru.png` | 240dp | ru | **121dp** | **79dp** | 1.53:1 |

Content width inside each card is 16dp less: 76dp / 44dp on small, 105dp / 63dp on XL.

## What is visible at rest on the small screen — after the header cut

The header was cut in three steps to bring the value cards above the fold. Stack height at
192dp, and how much of the 49dp / 53dp card block is visible at rest, at scales 1.0 / 1.24:

| Step | Stack height | Card visible |
| --- | --- | --- |
| baseline | 138dp / 159dp | 3dp / 0dp |
| 1 — «Подход N из M» moves to the pill row's description | 120dp / 137dp | 21dp / 8dp |
| 2 — the status word leaves the drawing in ACTIVE only | 112dp / 124dp | 29dp / 21dp |
| 3 — the exercise name drops to one line | **94dp / 102dp** | **47dp / 43dp** |

**The criterion is not met.** In these **ACTIVE-only captures**, both cards are
**2dp short at scale 1.0 and 10dp short at 1.24** — card bottom at 118dp and 126dp against
a viewport ending at 116dp. The pictured values remain readable while the card fill clips.
This observation does not describe worded read-only states, field errors, or their
accessibility trees, and is not an accepted blanket residual for the controller.
Nothing was shrunk, tightened, reduced or raised to close this historical gap.

Visible at rest on the captured ACTIVE surface, in order: the **connection dot** (filled, with no
word beside it — the word is spoken, not drawn, in this state only), the **exercise name** on
one line, the **set-scale pills** (whose «Подход 4 из 4» is spoken), and **both value cards**
with their icons and values, above the **check-glyph action**.

`small-round-192dp-ru-disabled.png` shows the pre-PR-A read-only variant, where the status
word is drawn and «Недоступно» sits between the pills and the outlined action. It does
not depict the merged PR-A button label.

## The mid-word break, fixed

An earlier revision rendered «Завершить» as «Завершит» / «ь» — the primary action split
inside a word. The overflow gate was green and right to be: the label allowed two lines and
neither overflowed, so a break *inside* the allowance was invisible to it. Gate G10 now
states the missing invariant, and measurement chose the label: «Завершить» split at 192dp on
both font scales and at 240dp on the default scale, and «Complete» split at 192dp, so the
action carries a check glyph with the wording in its content description.

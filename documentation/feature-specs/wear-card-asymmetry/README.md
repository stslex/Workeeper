# Wear controller — value-card asymmetry, as rendered

Captured for the PR #284 review so the card ratio can be judged by looking at it. The
approved mockup had the two value cards equal; they landed unequal because the widest
weight (`999.99`) needs 65dp of content at the largest font scale while the widest reps
(`999`) needs 36dp, and equal halves gave both 60dp on a 192dp screen.

**This directory records what shipped. It argues nothing.** Whether 1.53:1 reads as
intentional or as lopsided is a design call, and no gate can express it.

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
| `small-round-192dp-en-scrolled.png` | 192dp | en | **92dp** | **60dp** | 1.53:1 |
| `small-round-192dp-ru-scrolled.png` | 192dp | ru | **92dp** | **60dp** | 1.53:1 |
| `xl-round-240dp-en.png` | 240dp | en | **121dp** | **79dp** | 1.53:1 |
| `xl-round-240dp-ru.png` | 240dp | ru | **121dp** | **79dp** | 1.53:1 |

Content width inside each card is 16dp less: 76dp / 44dp on small, 105dp / 63dp on XL.

## What is visible at rest on the small screen — reported, not acted on

`small-round-192dp-en.png` and `small-round-192dp-ru.png` are the 192dp surface as it first
appears, before any scrolling. Visible at rest, in order: the **connection dot and status
word**, the **exercise name** (two lines, ellipsized), the **set scale pills**, the **set
progress line** («Подход 4 из 4»), and the **anchored primary action**. The **two value
cards are below the fold** — only their top edges clear the button — so neither the weight
nor the reps can be read without scrolling. `small-round-192dp-ru-disabled.png` shows the
read-only variant, where the unavailability word «Недоступно» is anchored above the arc and
the set-progress line has scrolled out.

The `-scrolled` pair is the same surface after one swipe, which is what the width table
above is measured from. This is consistent with the touch gate, which already scrolls before
tapping a card on this profile. Whether the fold is acceptable is a design question and is
recorded here untouched.

## Measured heights on the 192dp profile — numbers only

Requested for the open layout question. Measured from the running composition at the small
round profile; the harness was checked against a known-negative first (a control string that
cannot fit one line measured 160 × 55dp at scale 1.0 and 160 × 67dp at 1.24, so it reports a
different answer when the answer differs).

| Measure | scale 1.0 | scale 1.24 |
| --- | --- | --- |
| At-rest content stack, top inset (y = 24dp) to the bottom of the value cards | **138dp** | **159dp** |
| Height available above the anchored action (screen top → button top, y = 119dp) | **119dp** | **119dp** |
| — of which usable content area (116dp scroll viewport, less the 24dp top inset) | **92dp** | **92dp** |
| Stack minus usable area | **46dp** | **67dp** |
| Height of the value-card block (what a single row would replace) | **49dp** | **53dp** |
| Single-line row «80 кг · 8», card type size, given the full 160dp content width | **64 × 19dp** | **78 × 23dp** |

Arithmetic on those figures: substituting a 19dp / 23dp row for the 49dp / 53dp card block
would put the stack at 108dp (scale 1.0) and 129dp (scale 1.24).

No conclusion is drawn here and nothing was built.

## The mid-word break, fixed

An earlier revision rendered «Завершить» as «Завершит» / «ь» — the primary action split
inside a word. The overflow gate was green and right to be: the label allowed two lines and
neither overflowed, so a break *inside* the allowance was invisible to it. Gate G10 now
states the missing invariant, and measurement chose the label: «Завершить» split at 192dp on
both font scales and at 240dp on the default scale, and «Complete» split at 192dp, so the
action carries a check glyph with the wording in its content description.

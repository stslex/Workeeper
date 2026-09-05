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

## Two things the images show that no gate measures

1. **On a 192dp screen the cards sit below the fold at rest.** `small-round-192dp-en.png`
   and `small-round-192dp-ru.png` are the surface as it first appears: only the top edges
   of the cards clear the anchored button, and the values need a scroll to read. The
   `-scrolled` pair is the same surface after one swipe. This is consistent with the touch
   gate, which scrolls before tapping a card on the small profile, but it is worth seeing.
2. **«Завершить» wraps mid-word on the small screen** — `small-round-192dp-ru.png` renders
   it as «Завершит» / «ь». The overflow gate is green here and correct to be: the label
   allows two lines and neither line overflows, so a break *inside* the allowance is
   invisible to it. A gate that expressed "does not split a word" would be a different
   instrument.

Both are reported on PR #284 rather than acted on.

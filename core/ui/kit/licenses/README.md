# Bundled fonts — provenance

All three families are SIL Open Font License 1.1. `OFL-Archivo.txt` and `OFL-IBMPlex.txt`
are the upstream license texts, shipped alongside the binaries as OFL 1.1 requires.
One `OFL-IBMPlex.txt` covers both Plex families — IBM ships a byte-identical `LICENSE.txt`
in the Sans and Mono release archives (sha256 `7e6b2818…07da` for both, as distributed with
CRLF endings). The two `.txt` files here are the same text with LF endings: this repo runs
`core.autocrlf=input`, so git normalised them on staging. The hashes in the table below are
the `.ttf` binaries, which git correctly treats as binary and leaves untouched — each one is
verified byte-identical to upstream after staging.

The six IBM Plex `.ttf`s under `../src/main/res/font/` are **unmodified upstream artifacts**,
renamed only to satisfy the Android resource-name grammar (`[a-z0-9_]`). Renaming does not
alter bytes, so each of their hashes is reproducible by downloading the source URL and hashing
it. The Archivo file is **derived**, and its own section below says exactly how — its hash is
reproducible from a published input plus a recorded command rather than from a URL.

| resource | upstream file | family version | bytes | sha256 |
| --- | --- | --- | --- | --- |
| `archivo_bold_wdth116.ttf` | derived from `Archivo/fonts/variable/Archivo[wdth,wght].ttf` — see below | 2.001 | 121 532 | `53a800bc19a2bb6525131d70b826866829905937d0cb48ab44ded4f13652241d` |
| `ibm_plex_sans_regular.ttf` | `ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-Regular.ttf` | 3.005 | 200 500 | `975dcda37d80f038dcd143c22e33ca2d97a0cc5a929aace1c749153b0fe1afa5` |
| `ibm_plex_sans_medium.ttf` | `ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-Medium.ttf` | 3.005 | 202 460 | `331c8639d7598b2cde62a911a71db195e30cb655cd6bdf2e324a7e984955f907` |
| `ibm_plex_sans_semibold.ttf` | `ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-SemiBold.ttf` | 3.005 | 202 632 | `a20caf8286023a6a7a85e40b1d2a4ae9fc3e3b1f9eda8f4c542dd4986af67bb1` |
| `ibm_plex_mono_regular.ttf` | `ibm-plex-mono/fonts/complete/ttf/IBMPlexMono-Regular.ttf` | 2.005 | 173 052 | `7c6fbddca4b700be918f5f6183d9bd4464fa427fe435f0b480d77fe2bb8c5a43` |
| `ibm_plex_mono_medium.ttf` | `ibm-plex-mono/fonts/complete/ttf/IBMPlexMono-Medium.ttf` | 2.005 | 174 008 | `98fbd727aae340b236955879dabed4d991aac9e8e90b3b2a67ce4a59221cc97c` |
| `ibm_plex_mono_semibold.ttf` | `ibm-plex-mono/fonts/complete/ttf/IBMPlexMono-SemiBold.ttf` | 2.005 | 174 608 | `f04d7c488ddf7d1fa99f2574efc3406ea4cbe17bb1af3a1ab960f84d0c96a172` |

Total 1 248 792 bytes on disk.

### The 600 cuts come from the same release, proven by hash

The 600s were taken from the same two tags and the same `fonts/complete/ttf/` directory as the
400/500 already bundled — and that is not a claim about where they were downloaded from, it is
verifiable: re-downloading the four already-bundled files from those tags reproduces their
committed hashes **byte for byte** (`975dcda3…`, `331c8639…`, `7c6fbddc…`, `98fbd727…`). Same
directory listing, same release, same build. Mixed releases would mean mixed vertical metrics,
which is exactly the mistake the Mono section below documents avoiding.

Measured across all six Plex files: `unitsPerEm 1000`, typo `780 / −220 / 300`, hhea
`1025 / −275 / 0`, win `1025 / 275`, `capHeight 698` — identical on every one. `xHeight` is the
single per-weight value (Sans 516 / 520 / **522** at 400 / 500 / 600; Mono 516 at all three),
which is a property of the weight's drawing, not a metric that moves a baseline.

## Sources

- **Archivo** — <https://github.com/Omnibus-Type/Archivo> (`master`),
  `fonts/variable/Archivo[wdth,wght].ttf`, sha256
  `664bbeb10522dac35c174a3860aaecad7b1ad3a0fc8b0d26888e26c824ec556d`, 658 596 bytes.
  Google Fonts publishes this family variable-only (`ofl/archivo/Archivo[wdth,wght].ttf`);
  there is no `ofl/archivoexpanded` directory, so the static width instances only exist
  upstream. The upstream `OFL.txt` is byte-identical to the Google Fonts copy
  (sha256 `108b4e57…716b`).
- **IBM Plex Sans** — <https://github.com/IBM/plex> release `@ibm/plex-sans@1.1.0`,
  asset `ibm-plex-sans.zip`, path `fonts/complete/ttf/`.
- **IBM Plex Mono** — <https://github.com/IBM/plex> release `@ibm/plex-mono@2.5.0`,
  asset `ibm-plex-mono.zip`, path `fonts/complete/ttf/`.

## Why static instances, not variable fonts

Both Archivo and IBM Plex Sans are published as variable fonts with `wdth` + `wght` axes.
`minSdk` is 28, so `FontVariation.Settings` would be supported on every device we ship to —
the decision is purely bytes and render fidelity, and statics win on both counts.

**Archivo `wdth 116 / wght 700` — a derived static, and why that is now the better trade.**

The mockups do not use a published width. Both files set numerals through
`font-variation-settings` at three coordinates — `.data-l` `"wdth" 115`, `.data-s` `"wdth" 116`,
`.data-hero` `"wdth" 122` — and the app is cut at **116**, the width the session timer and the
record value are drawn at. 116 is not reachable as a published artifact: all nine `fvar` named
instances sit at `wdth 100`, and there is no `STAT` axis value for 116 either, which
`fonttools` says out loud (`ValueError: Cannot find Axis Values {'wdth': 116}` when asked to
rename the instance). Archivo does publish `SemiExpanded` (`usWidthClass 6`, ~112.5) and
`Expanded` (`usWidthClass 7`, 125) statics, and both are the wrong width and *larger*
(191 580 / 188 036 bytes).

So the earlier trade — "take the upstream static, pay 66 336 bytes for a hash that matches a
published URL" — no longer has a side to take: there is no published URL for this cut. The
provenance argument changes shape from *identity* to *reproducibility*, and it is discharged
by pinning all three inputs:

```
input   Omnibus-Type/Archivo  fonts/variable/Archivo[wdth,wght].ttf
        sha256 664bbeb10522dac35c174a3860aaecad7b1ad3a0fc8b0d26888e26c824ec556d
tool    fonttools 4.63.0
output  archivo_bold_wdth116.ttf  121 532 bytes
        sha256 53a800bc19a2bb6525131d70b826866829905937d0cb48ab44ded4f13652241d
```

```python
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

vf = TTFont("Archivo[wdth,wght].ttf", recalcTimestamp=False)
inst = instantiateVariableFont(vf, {"wdth": 116, "wght": 700},
                               inplace=False, updateFontNames=False)
inst.recalcTimestamp = False                  # <- load-bearing, see below
inst["head"].modified = vf["head"].modified   # inherit the input's stamp

# Name it. updateFontNames=True cannot: 116 has no STAT axis value, so fonttools raises
# ValueError: Cannot find Axis Values {'wdth': 116}. Left alone, the instance keeps the
# variable font's DEFAULT-instance names -- "Archivo SemiBold", subfamily "Regular" --
# which describe wght 600 at wdth 100 and not this cut at all.
name = inst["name"]
for nid, value in ((1, "Archivo wdth116"), (2, "Bold"), (4, "Archivo wdth116 Bold"),
                   (6, "Archivo-wdth116Bold"), (16, "Archivo wdth116"), (17, "Bold")):
    for rec in list(name.names):
        if rec.nameID == nid:
            name.setName(value, nid, rec.platformID, rec.platEncID, rec.langID)

os2 = inst["OS/2"]                             # fsSelection: BOLD on, REGULAR off
os2.fsSelection = (os2.fsSelection & ~(1 << 6)) | (1 << 5)
inst["head"].macStyle |= 1 << 0

inst.save("archivo_bold_wdth116.ttf")
```

**A font that says SemiBold while being 700 is the same lie as a file called `expanded` that
is not the Expanded cut**, so the names are set rather than left. `usWeightClass` is already
700 and `usWidthClass` 6 straight out of the instancer; the name table, `fsSelection` and
`macStyle` are what needed correcting. None of it changes rendering — Compose resolves this
family through the `FontWeight` declared in `AppTypography`, not through the file's own
metadata — and the goldens prove it: setting all of it moved zero pixels.

**`recalcTimestamp = False` is the line the whole hash rests on.** `head.modified` is a
wall-clock stamp, and `TTFont.save()` overwrites it at compile time — `table__h_e_a_d.compile`
does `if ttFont.recalcTimestamp: self.modified = timestampNow()`, and `recalcTimestamp`
defaults to `True`. So assigning `inst["head"].modified` without also clearing the flag is a
**no-op**, and two runs a second apart produce two different files.

That is not hypothetical: it is what this file said to do at first, and the artifact shipped
with a build-time stamp instead of the input's. It survived a two-run check only because both
runs happened to land inside the same wall-clock second — a green result from a detector that
had never been shown to fire. Caught in review, re-cut, and re-checked properly: **five runs
spread across ~15 seconds now give one sha256**, and the difference between the old artifact
and this one is exactly **12 bytes** — `head.modified` at offsets 144–147 and the two
checksum fields it feeds at 276–279 and 300–303. Every glyph, metric and table body is
identical, which is why re-cutting it moved no pixel in any golden.

With the flag set, `head.created` and `head.modified` are both inherited from the published
input (`3690798257` / `3695063549`), so the derivation is a pure function of a file whose hash
is above — which is what makes writing the output hash down worth anything.

**The derivation is faithful, proven by a controlled pair.** Instancing the same variable font
at `wdth 125 / wght 700` — the coordinates of the *published* `ArchivoExpanded-Bold.ttf` — and
comparing against that published static: **0 of 23** tested advance widths differ (every digit,
`: . , - + / %`, and Latin letters), and the vertical metrics agree exactly (typo `878/−210/0`,
hhea `878/−210/0`, win `1100/410`; `sxHeight`/`sCapHeight` land one unit off at 526/686 vs
527/687, a rounding artefact of instancing and not a metric that positions a line box). So the
instancer reproduces a published artifact when one exists, which is the only evidence available
that it reproduces the right thing when one does not.

**Bytes.** 121 532, against 188 036 for the `wdth 125` static it replaces (**−66 504**) and
658 596 for the variable font (**−537 064**). The derived file is smaller than the published
static because instancing drops the ttfautohint instructions — the one thing genuinely lost
here, and it costs nothing on Android, which ignores TrueType hinting and renders with its own
FreeType/Skia stack.

**The variable font was the other option, and it was evaluated, not assumed.** `minSdk` is 28
and Compose applies `FontVariation.Settings` to a bundled `ResourceFont` above API 26 —
`ResourceFont` carries `variationSettings`, `AndroidFontLoader` passes them on, and the applier
is gated `Build.VERSION.SDK_INT >= 26` (read from `ui-text-android` sources, not assumed). So
option (b) works on every device we ship to, and it is the *only* way to reproduce all three
drawn widths (115 / 116 / 122) rather than one. It loses on two counts. It costs 537 064 bytes,
more than this repo's entire font budget delta. And the applier's mechanism is a runtime
`Paint.fontVariationSettings` round-trip, which under Paparazzi is layoutlib's `Paint` — whether
layoutlib honours it is unverified, and the goldens are the gate. That is the reinstatement
path if the three-width treatment is ever wanted: bundle the VF, set the axis per slot, and
prove layoutlib first.

**IBM Plex Sans 400/500.** Here statics win outright, with no tradeoff to weigh: two official
statics total `402 960` bytes against `537 244` for the single variable font, and instancing
the variable font is *larger* than the official statics (`218 076` vs `200 500` at wght=400).

## Why IBM's own release for Mono, not Google Fonts

Google Fonts ships IBM Plex Mono as per-weight statics that are ~37 KB smaller each
(`135 580` / `136 704` vs `173 052` / `174 008`, a 74 776-byte saving overall) and they cover
every character the app uses. We still take IBM's release, because **vertical metrics must
match the Sans**: Mono is intended for units and meta text set inline beside Plex Sans.

| | typoAsc / typoDesc | hhea asc / desc | xHeight | capHeight |
| --- | --- | --- | --- | --- |
| Plex Sans 3.005 (IBM) | 780 / −220 | 1025 / −275 | 516 | 698 |
| Plex Mono 2.005 (IBM) | **780 / −220** | **1025 / −275** | 516 | 698 |
| Plex Mono 2.3 (Google Fonts) | 1025 / −275 | 1025 / −275 | 516 | 698 |

IBM's Sans and Mono agree on every vertical metric. The Google Fonts Mono is an older
generation whose typo ascender/descender disagree with the Sans, which would shift baselines
and default line heights between the two families. 74 776 bytes is the price of that
alignment.

## Character coverage

Verified against the actual union of every `values/strings.xml` and `values-ru/strings.xml`
in the repo (19 + 18 files) — 55 distinct Cyrillic characters plus `«` `»` `·` `×` `—` `…`
`→` `‘` `’` `“` `”`:

- **IBM Plex Sans** and **IBM Plex Mono** (all three weights) cover the set completely — zero
  missing glyphs for Cyrillic or for any other character the app renders. Re-verified when the
  600 cuts were added: 129 distinct characters across 19 `values` + 18 `values-ru` files, of
  which 55 are Cyrillic; Sans `cmap` 895 entries, Mono 1049, at every weight.
- **Archivo has no Cyrillic at all** — all 55 letters are absent from its `cmap` (0 of the 96
  codepoints in U+0400–U+045F), in the variable font and in every instance of it. Its Google
  Fonts `subsets` are `latin`, `latin-ext`, `menu`, `vietnamese`.
  **Correction:** earlier copies of this file, and the KDoc that quoted it, said the punctuation
  `« » · × — … →` was missing too. It is not — measured from the bundled `cmap`, all of those
  are present, as is `•`. The gap is Cyrillic **letters**, and only those. The distinction is
  load-bearing in one place: a bullet-prefixed elapsed label (`"•12:04"`) can be set in this
  family without splitting the string.

Archivo's coverage gap is acceptable *only* because its scope is numerals and the timer:
digits `0`–`9` and the separators `: . , - + / %` are all present. This is a hard constraint,
not a preference — **never route localized text through `numericFontFamily`**, or Russian
users get glyphs resolved from the system fallback chain instead of Archivo, in a typeface
that does not match.

## Note for the redesign

Archivo's digits are **proportional** by default at every width — in the bundled `wdth 116`
cut `0` is 706 units and `1` is 652 (at `wdth 125` they were 769 and 683) — which makes a
running timer jitter horizontally as digits change. The family ships `tnum` and `zero`, and
`tnum` makes 20 substitutions — the ten lining digits to their `.tf` forms and the ten oldstyle
digits to `.tosf` — after which every digit advances **700** units at `wdth 116` (758 at 125). Every numeric slot sets `fontFeatureSettings = "tnum"`; `TnumCanaryGoldenTest` is the
detector and `AppTypographyContractTest` states it as a value.

IBM Plex Sans and Mono are already tabular by default (every digit 600 units) and, unlike
Archivo, ship **no `tnum` feature at all** — so `fontFeatureSettings = "tnum"` on a Plex style
is a no-op. Harmless, but it is not doing what it looks like it is doing, and it means a timer
slot minted by copying that idiom onto a `text.*` style would have no tabular guarantee.

# Bundled fonts — provenance

All three families are SIL Open Font License 1.1. `OFL-Archivo.txt` and `OFL-IBMPlex.txt`
are the upstream license texts, shipped alongside the binaries as OFL 1.1 requires.
One `OFL-IBMPlex.txt` covers both Plex families — IBM ships a byte-identical `LICENSE.txt`
in the Sans and Mono release archives (sha256 `7e6b2818…07da` for both, as distributed with
CRLF endings). The two `.txt` files here are the same text with LF endings: this repo runs
`core.autocrlf=input`, so git normalised them on staging. The hashes in the table below are
the `.ttf` binaries, which git correctly treats as binary and leaves untouched — each one is
verified byte-identical to upstream after staging.

Every `.ttf` under `../src/main/res/font/` is an **unmodified upstream artifact**, renamed
only to satisfy the Android resource-name grammar (`[a-z0-9_]`). Renaming does not alter
bytes, so each hash below is reproducible by downloading the source URL and hashing it.

| resource | upstream file | family version | bytes | sha256 |
| --- | --- | --- | --- | --- |
| `archivo_expanded_bold.ttf` | `Archivo/fonts/ttf/ArchivoExpanded-Bold.ttf` | 2.001 (ttfautohint 1.8.3) | 188 036 | `1bcc4fd980b708f08b92e8f39c9d3934e1c03eba84a95b1d4a7a99534c526902` |
| `ibm_plex_sans_regular.ttf` | `ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-Regular.ttf` | 3.005 | 200 500 | `975dcda37d80f038dcd143c22e33ca2d97a0cc5a929aace1c749153b0fe1afa5` |
| `ibm_plex_sans_medium.ttf` | `ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-Medium.ttf` | 3.005 | 202 460 | `331c8639d7598b2cde62a911a71db195e30cb655cd6bdf2e324a7e984955f907` |
| `ibm_plex_sans_semibold.ttf` | `ibm-plex-sans/fonts/complete/ttf/IBMPlexSans-SemiBold.ttf` | 3.005 | 202 632 | `a20caf8286023a6a7a85e40b1d2a4ae9fc3e3b1f9eda8f4c542dd4986af67bb1` |
| `ibm_plex_mono_regular.ttf` | `ibm-plex-mono/fonts/complete/ttf/IBMPlexMono-Regular.ttf` | 2.005 | 173 052 | `7c6fbddca4b700be918f5f6183d9bd4464fa427fe435f0b480d77fe2bb8c5a43` |
| `ibm_plex_mono_medium.ttf` | `ibm-plex-mono/fonts/complete/ttf/IBMPlexMono-Medium.ttf` | 2.005 | 174 008 | `98fbd727aae340b236955879dabed4d991aac9e8e90b3b2a67ce4a59221cc97c` |
| `ibm_plex_mono_semibold.ttf` | `ibm-plex-mono/fonts/complete/ttf/IBMPlexMono-SemiBold.ttf` | 2.005 | 174 608 | `f04d7c488ddf7d1fa99f2574efc3406ea4cbe17bb1af3a1ab960f84d0c96a172` |

Total 1 315 296 bytes on disk.

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

- **Archivo** — <https://github.com/Omnibus-Type/Archivo> (`master`), `fonts/ttf/`.
  Google Fonts publishes this family variable-only (`ofl/archivo/Archivo[wdth,wght].ttf`);
  there is no `ofl/archivoexpanded` directory, so the static Expanded instances only exist
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

**Archivo Expanded 700.** The variable font is `658 596` bytes. "Expanded" is the `wdth=125`
edge of the axis; it is *not* a named instance (all nine `fvar` named instances sit at
`wdth=100`), so it can only be reached by explicit axis coordinates. Instancing the variable
font at `wdth=125, wght=700` with `fonttools varLib.instancer` and comparing against the
upstream static:

- advance widths are **identical** for every digit, separator and Latin letter tested
  (`0`→769, `1`→683, `:`→325, …), so line breaking and layout are unaffected;
- outline coordinates differ by at most **0.91 units per 1000 em** where point counts allow a
  direct comparison — sub-pixel at any realistic size;
- `usWidthClass=7`, `usWeightClass=700` and vertical metrics match, confirming the upstream
  static really is the `wdth=125 / wght=700` instance.

So the static renders equivalently at **188 036** bytes vs **658 596** — a 470 560-byte saving
(3.5×). The self-instanced file is smaller still (121 700 bytes, because it drops the
ttfautohint instructions), but it is a locally derived artifact whose hash nobody can verify
against a published upstream URL. We take the upstream static and trade 66 336 bytes for
verifiable provenance and shipped hinting.

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
- **Archivo has no Cyrillic at all** — all 55 characters are absent from its `cmap`, in both
  the variable font and the static. Its Google Fonts `subsets` are `latin`, `latin-ext`,
  `menu`, `vietnamese`.

Archivo's coverage gap is acceptable *only* because its scope is numerals and the timer:
digits `0`–`9` and the separators `: . , - + / %` are all present. This is a hard constraint,
not a preference — **never route localized text through `numericFontFamily`**, or Russian
users get glyphs resolved from the system fallback chain instead of Archivo, in a typeface
that does not match.

## Note for the redesign

Archivo's digits are **proportional** by default (`0` is 769 units, `1` is 683), which makes a
running timer jitter horizontally as digits change. The family does ship the `tnum` and `zero`
features, so a timer slot should set `fontFeatureSettings = "tnum"` on its `TextStyle`.
IBM Plex Sans and Mono are already tabular by default (every digit 600 units).

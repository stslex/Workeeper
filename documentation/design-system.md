# Design System

This document specifies the v1 design system for Workeeper: tokens
(color, typography, spacing, shape, motion, elevation), component
inventory (21 shared components), and the implementation plan for
`core/ui/kit`.

It is the input to the Claude Code prompt at the bottom of this
document. The prompt depends on the database redesign being merged
to dev, but does not depend on feature specs.

For the visual rationale behind these decisions, see the chat
mockups in the Stage 4.5 design system session.

## Foundations

### Theming approach

Material 3 + custom values + custom semantic layer.

- `MaterialTheme` stays in the composition tree. M3 components
  (`Scaffold`, `TopAppBar`, `Snackbar`, `TextField`, `Button`, etc.)
  remain available without rewriting them.
- `ColorScheme` / `Typography` / `Shapes` are filled with custom
  values, not `dynamicColorScheme()`. No Pixel-look.
- A semantic layer of additional tokens lives in
  `LocalAppColors`, `LocalAppTypography`, etc. (CompositionLocal),
  carrying fitness-specific roles (set type colors, PR highlights,
  etc.) that M3 does not cover.

### Theme switch reactivity contract

When the user changes the theme preference (System / Light / Dark in
Settings), three things must update **simultaneously and in a single
recomposition**:

1. M3 `MaterialTheme` color scheme — automatic via `AppTheme`
   recomposition.
2. `LocalAppColors` and other AppUi locals — automatic via
   `AppTheme` recomposition.
3. **Activity window chrome** — status bar tint, navigation bar
   tint, and surface insets controller. This is **not** automatic;
   it requires an explicit side effect inside `AppTheme`.

The window chrome side effect must use `SideEffect` (not
`LaunchedEffect`):

```kotlin
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // ... rest of AppTheme: build ColorScheme, provide locals, MaterialTheme { content }
}
```

`SideEffect` runs on every successful recomposition. `LaunchedEffect`
does not — it relaunches only when its keys change, which can miss
the recomposition triggered by a `darkTheme` boolean flip.

This contract is the source of the "TopAppBar recolors out of phase
with screen content" bug. Any future contributor wiring window
chrome behavior must use `SideEffect`.

### Color palette

Two color modes — `dark` and `light` — with `system` as the default
preference. The user toggles in Settings.

#### Base palette (independent of mode)

```
Accent (brand teal):
  Default:    #4A9B8E
  On accent:  #FFFFFF (light mode) / #0E0F0E (dark mode)
  Tinted bg:  #DCEEE9 (light) / #1F3835 (dark)
  Tinted fg:  #2A6B61 (light) / #6EB7AB (dark)
```

The accent is the only color carrying brand meaning. All other
surfaces and text are warm-tinted neutrals.

#### Dark mode (default)

```
Surface tier 0 (background):   #0E0F0E    (near-black, page)
Surface tier 1 (cards):        #16171A    (rows, cards)
Surface tier 2 (elevated):     #1A1B1A    (input mocks)
Surface tier 3 (active):       #161E1C    (active session, completed set)
Surface tier 4 (input fill):   #1F2122    (alt cards, badges)

Text primary:                  #E8E8E5
Text secondary:                #B5B6B0
Text tertiary:                 #6E6F6A    (placeholders, disabled)
Text disabled:                 #4F5052

Border subtle:                 #1F2122
Border default:                #2A2C2D
Border strong:                 #3F4143

Inverse surface (snackbar):    #1B1C1A    (same as light primary text)
Inverse on-surface:            #E8E8E5
```

#### Light mode

```
Surface tier 0 (background):   #FAFAF8    (warm off-white, page)
Surface tier 1 (cards):        #F2F1ED    (rows, cards)
Surface tier 2 (elevated):     #FFFFFF    (input fill)
Surface tier 3 (active):       #EFF7F4    (active session, completed set)
Surface tier 4 (input alt):    #F8F7F3

Text primary:                  #1B1C1A
Text secondary:                #5A5B58
Text tertiary:                 #7B7C77
Text disabled:                 #B5B6B0

Border subtle:                 #E8E7E2
Border default:                #E0E0DC
Border strong:                 #C7C8C2

Inverse surface (snackbar):    #1B1C1A    (always dark, regardless of mode)
Inverse on-surface:            #E8E8E5
```

#### Semantic colors (set types and states)

```
Set type — Warmup    (W): amber  light=#F8E8C4/#7A5418  dark=#2A2316/#DEAA62
Set type — Work      (·): teal   light=#DCEEE9/#2A6B61  dark=#161E1C/#6EB7AB
Set type — Failure   (F): red    light=#F7DCDC/#7A2828  dark=#2A1818/#D58A8A
Set type — Drop      (D): purple light=#E8DEF8/#5A2B7A  dark=#241A2A/#B89BD8
```

Pair format: `light bg / light fg | dark bg / dark fg`. Each set type
chip applies the bg as background, the fg as text/icon color.

```
Status — Success:  uses accent teal (intentional — success on a
                   fitness tracker = a PR or finished session, both
                   accent-themed).
Status — Warning:  amber (same as warmup chip).
Status — Error:    red.
Status — Info:     uses text secondary (low key).
```

#### Slot roles and dead slots

- `SlotRole` is derived from a slot's **call sites**, never from its name — the recon pass
  found three whose names lie: `record.border` fills a pill, `accentTintedForeground` fills
  a circle, `borderSubtle` paints a Box that is functionally a rule.
- `PaletteInventory.slots(colors)` walks `AppColors` by reflection — nested groups
  discovered rather than listed, at unbounded depth, restricted to the
  `io.github.stslex.workeeper.core.ui.kit.theme` package — and `ContrastGateTest` insists
  every combination it returns is accounted for, so a new slot arrives as a test failure
  rather than as an unmeasured pair.
- `SlotRole.DECORATIVE` takes no threshold: dark `hair-s` on `slab` measures 1.22:1, and a
  thing meant to be barely there fails 3:1 by construction.
- `status.info` has **zero readers** — no production site, no test, no `@Preview`, no
  mention in `documentation/`; the only occurrences of the identifier are its declaration
  and its two assignments in `AppColors.kt`. It is kept because removing it is a public-API
  change to the design system, and pinned as `"status.info" to DEAD` in
  `ContrastContract.ROLES` so it cannot quietly acquire a reader.

#### Material 3 `ColorScheme` mapping

`ColorScheme` carries **48** colour roles, counted by reflection rather than taken from the
spec. Any role left unset falls back to the Material baseline, which in practice renders as
baseline purple — that was the entire `*Fixed` family until it was mapped. `scrim` is the
trap: its Material baseline value **is** `Color.Black`, so assigning `Color.Black` is
indistinguishable from leaving it unmapped and only *looks* like a mapping. Nothing in the
project reads `MaterialTheme.colorScheme.primaryFixed` and no current stock component does;
the roles are mapped anyway, because proving a role unreachable means proving a negative
across every M3 component and every future version of the library, while mapping costs
nothing.

The `*Fixed` roles are contracted by Material to hold the **same tone in light and dark**,
so `FixedTones` assigns them from literal constants — `RAISE = 0xFF242B32`,
`SLAB = 0xFF1E242A`, `MAX = 0xFFF1F5F9`, `BODY = 0xFFB7C0CA`, all from the dark end of the
v3 ramp — and never from `AppColors`. Sourcing them from theme-dependent slots would satisfy
"not purple" while breaking the contract itself, and any component that later started
reading them would flip on a theme switch. Measured: `ON_CONTAINER` on `CONTAINER` 13.07:1,
`ON_CONTAINER_VARIANT` on `CONTAINER` 7.78:1, `ON_CONTAINER` on `CONTAINER_DIM` 14.29:1.

#### Contrast gate

`ContrastGateTest` reads `ContrastContract` and the palette, and scores every declared pair
with `WcagContrast` (all three in `core/ui/kit` unit tests).

- Translucent foregrounds are rejected outright — a ratio for a see-through colour is
  meaningless until flattened onto a **known** backdrop, and flattening onto the wrong one
  yields a plausible, wrong number. Exactly two palette slots are genuinely translucent: the
  molten wash (9% / 11%) and the destructive wash (12%). Both are painted directly on a card,
  so `surfaceTier1` is the backdrop the composite uses. No declared pair currently has a
  translucent foreground on a translucent background; if one appears, `flatten` resolves the
  background first and the foreground against the result.
- `SRGB_LINEAR_THRESHOLD = 0.03928` is WCAG 2.x's **written** value, not sRGB's exact
  inflection point `0.04045`: the goal is to reproduce what a WCAG conformance checker
  reports, not to re-derive sRGB. For 8-bit input the choice is provably moot —
  `0.03928 × 255 = 10.016` and `0.04045 × 255 = 10.315`, so no byte value falls between the
  two thresholds and every printed ratio is bit-identical either way.
- `relativeLuminance` takes channels from the packed 8-bit sRGB bytes rather than from
  `Color.red` / `.green` / `.blue`. Those accessors return `Float`, and `128 / 255f` widened
  to `Double` is `0.50196081399…` against an exact `0.50196078431…` — an error near 1e-8 that
  could never move a two-decimal report, but would make `WcagContrastTest`'s reference
  anchors unverifiable at a tolerance tight enough to catch a genuinely wrong implementation.
  Related: `format(ratio)` truncates via `floor(ratio * 100) / 100` rather than rounding, so
  4.4996 is never printed as "4.50" beside a band column that says it failed.
- **Known hole.** The gate does not read production call sites. Adding a slot, or painting
  one declared slot on another the contract has not accounted for, fails; adding a screen
  that paints an already-declared pair stays green correctly; but adding a screen that paints
  a pair currently covered by an **exclusion** stays green **wrongly**, because an
  exclusion's premise is a claim about layout (e.g. "molten never appears on `field`") that
  the test cannot re-verify. `no_pair_is_both_declared_and_excluded` only catches a contract
  that has already contradicted one of its own exclusions. Closing the remaining gap needs
  call-site analysis — a detekt rule resolving `AppUi.colors.<slot>` against the enclosing
  surface. Until then an exclusion is an assertion about the UI, and it ages.

### Typography

**Stale as written; superseded by the v3 type system.** This section described a
single Inter family fetched through `GoogleFont.Provider` at two weights, and a
fifteen-slot Material scale with its own sizes. None of that is what ships. It is
replaced rather than patched because the shape changed, not only the numbers.

What ships now, and where to read it:

- **`core/ui/kit/.../theme/AppTypography.kt`** is the source of truth — three
  bundled families, six sizes, and the fifteen Material names as *aliases* onto
  those six. Nothing is fetched at runtime.
- **`core/ui/kit/licenses/README.md`** carries the font provenance *and* the measured
  `cmap` coverage: which cut of which family, its hash, why that cut, and which characters
  each family actually has. Archivo covers **zero** of the 55 Cyrillic characters the
  shipped `values-ru` corpus uses; `« » · × — … → •` **are** present, so the gap is
  Cyrillic letters and nothing else.
- **`documentation/feature-specs/v3-redesign-spec.md` §4** is the contract for the
  scale itself; the mockups are the contract for appearance.

| family | slots | weights |
| --- | --- | --- |
| IBM Plex Sans | everything that is words | 400 / 500 / 600 |
| Archivo (`wdth 116 / wght 700`) | numerals and the timer, nothing else | 700 |
| IBM Plex Mono | units and metadata | 400 / 500 / 600 |

Scale: **34 / 26 / 19 / 15 / 12.5 / 11**. The three heading rungs (34 / 26 / 19)
are set in 600; the rest in 400. `text.title` carries `-0.39sp` of tracking and no
other `(family, rung)` pair carries any, beyond the 0.5sp on every caption rung.

Two constraints that are enforced rather than documented:

- **Archivo takes digits and `: . , - + / %` only, never a `stringResource`** — it
  has no Cyrillic. Guarded by `NumericFontFamilyOnLocalizedTextRule` and
  `CyrillicTextGoldenTest`.
- **Every numeric slot sets `fontFeatureSettings = "tnum"`** — Archivo's digits are
  proportional. Guarded by `TnumCanaryGoldenTest` and `AppTypographyContractTest`.
  Note that the same setting on an IBM Plex style is a **no-op**: neither Plex
  family ships a `tnum` feature, being tabular by default already.

Two corollaries of those rules:

- A number formatted into a string is still a string: a rendered `"20 "` plus the localized reps suffix violates the
  digits-and-`: . , - + / %` rule even though it starts with digits. A bullet-prefixed
  timer needs no split — `•` is one of the marks Archivo does cover.
- `NumericFontFamilyOnLocalizedTextRule` recognises the numeric family only through
  `NUMERIC_MARKERS`: `numericFontFamily`, `typography.numeric`, `typography.timer`,
  `typography.dataValue`. Any new alias onto the family must be added there the day it is
  added to the typography, or `Text` / `BasicText` calls reached through it are silently
  unchecked.

Measured digit advances, in font units:

| cut | `0` | `1` | under `tnum` |
| --- | --- | --- | --- |
| Archivo `wdth 116` (bundled) | 706 | 652 | 700, uniform |
| Archivo `wdth 125` (the cut this scale used to ship) | 769 | 683 | 758, uniform |

`tnum` makes 20 substitutions — the ten lining digits to their `.tf` forms and the ten
oldstyle digits to `.tosf`. A dropped `tnum` compiles and looks fine on any string whose
digits do not change, so `TnumCanaryGoldenTest` renders `00:00` above `11:11` through
`AppUi.typography.timer` (the alias the session screen calls, not `numeric.display`): with
tabular figures the colons align vertically, without them the second line contracts and
they visibly separate.

Three more facts about the families:

- `plexSansFontFamily` bundles 400 / 500 / 600 (`ibm_plex_sans_regular` / `_medium` /
  `_semibold`). `FontWeight.Bold` still resolves by synthesis, but bundling 600 changed what
  it synthesises *from*: the matcher now falls back to the 600 file rather than the 500.
  Nothing asks for Bold today — bundle a real 700 file before relying on it.
- IBM Plex Mono shares its vertical metrics **exactly** with IBM Plex Sans, so mono units and
  metadata stay on the same baseline when set inline beside body text. It is tabular by
  default and covers Cyrillic in full, so unlike Archivo it is safe for localized text.
- **Fifteen of Material's thirty type slots are unmapped.** Material 3 `1.5.0-alpha24` grew
  `Typography` to thirty slots — the classic fifteen plus an `*Emphasized` twin for each —
  and `toM3Typography()` maps only the fifteen; the other fifteen keep Material's own
  baseline family and metrics. Nothing renders wrong today: no component token file in that
  library reads an `*Emphasized` key except `ScrollField`, which this app does not use. Same
  shape as the unmapped `ColorScheme` roles above — a live trap, not a settled one.

Thousands grouping in numerals is a pinned NBSP (U+00A0), not a locale API —
`ChartReadoutMapper.formatGrouped`'s `GROUP_SEPARATOR` — so the output cannot drift to NNBSP
(U+202F) on an ICU update: the bundled Archivo cut has real glyphs for SPACE and NBSP but
none for NNBSP, and only the missing one would seam. `formatGrouped` feeds the chart readout
and all three footer statrows.

### Spacing (`AppDimension`)

Existing `core/ui/kit/theme/AppDimension.kt` is extended (not
replaced) with explicit sub-categories. Keep current values where
they apply.

```
Spacing scale (raw values):
  none   = 0.dp
  xxs    = 2.dp     — micro gaps inside chips
  xs     = 4.dp     — gap between elements in a row
  sm     = 8.dp     — gap between rows, internal card padding
  md     = 12.dp    — section internal padding
  lg     = 16.dp    — card padding, screen edge padding default
  xl     = 24.dp    — section separator
  xxl    = 32.dp    — top-of-screen breathing room
  xxxl   = 48.dp    — empty state vertical padding
```

Semantic aliases:

```
screenEdge          = lg (16.dp)        — left/right of every screen
sectionSpacing      = xl (24.dp)        — between major content blocks
listItemPadding     = sm (8.dp)         — vertical inside a list row
cardPadding         = md (12.dp)        — inside cards
componentPadding    = sm (8.dp)         — internal component padding
```

Icon sizes (used wherever an icon is rendered — inside chips, app
bars, list trailing, empty states):

```
iconXs   = 14.dp     — inside chips, badges, dense pills
iconSm   = 18.dp     — TopAppBar actions, list trailing, inline
iconMd   = 24.dp     — default Material icon size
iconLg   = 32.dp     — empty state, hero areas
iconXl   = 48.dp     — empty state alternative, large heroes
```

Component heights (unified — every component pulls from this set
instead of declaring its own height inline):

```
heightXs = 32.dp     — small button, dense list rows, segment button
heightSm = 40.dp     — medium button, default list item, chip row, segmented track
heightMd = 48.dp     — large button, number input, primary CTA
heightLg = 56.dp     — text field, TopAppBar (M3 standard)
heightXl = 64.dp     — BottomBar, modal headers (M3 standard)
```

Component-specific notes:

- AppButton — large=heightMd, medium=heightSm, small=heightXs.
- AppListItem default — heightSm with cardPadding inside.
- AppNumberInput — heightMd (44–48dp range, picks heightMd=48 for
  Live workout where target size matters).
- AppTextField — heightLg (M3 default).
- AppTopAppBar — heightLg.
- AppNavBar — 56dp of content (heightMd pill + 2 x Space.xs padding,
  the drawn 60px derived the same way `.topbar`'s was) plus the
  navigation-bar inset. Was `AppBottomBar` at the M3 NavigationBar
  default; both that component and its 80dp are gone.
- AppSegmentedControl — heightSm track (40dp) = heightXs segment (32dp) + 2 x Space.xs padding.

### Shape (corner radius)

Three sizes only. M3 `Shapes` slots map to these.

```
small   = 6.dp     — chips, pills, set type badges, small inputs
medium  = 10.dp    — cards, list rows, dialogs, buttons
large   = 14.dp    — bottom sheets, modal sheets

extraLarge          — not used in v1
extraSmall          — not used in v1

phoneFrame          = 24.dp (only for mockups; not in production)
```

### Elevation

Workeeper uses **color-based elevation**, not shadow-based, in line
with calm dense aesthetic. Surface tiers (defined above) signal
elevation visually without shadows.

```
M3 elevation slots:
  level0  = surface tier 0
  level1  = surface tier 1
  level2  = surface tier 2
  level3  = surface tier 3 (active states)
  level4  = surface tier 4 (alternative)
  level5  = surface tier 4 (no need for higher)

shadowElevation     = 0.dp everywhere (no drop shadows)
borderElevation     = 0.5.dp (hairline borders for tier separation)
```

The only exception: focus rings on text fields use `0.5.dp` outline
in `accent` color when focused.

#### `liftedSurface`

`Modifier.liftedSurface` is the shared lift treatment (segmented thumb, nav pill, selected
list row) — a cast shadow plus a top-edge highlight.

- It paints its shadow through `graphicsLayer { shadowElevation = shadow.toPx() }`, never
  `Modifier.shadow`, because `Modifier.shadow` compiles to nothing at `0.dp` — so the dark
  theme, and every unlifted state, would add and remove a modifier node on each lift flip
  instead of keeping one node whose elevation happens to be zero. Inside that `graphicsLayer`
  `clip = false` is required: the shadow is drawn **outside** the surface's bounds, so
  clipping to `shape` there removes exactly the part that is the effect.
- `restingFill(restingColor, lifted)` repairs a **fully transparent** `restingColor` to
  `lifted.fadedOut()` — the *lifted* colour faded out, not the surface behind, because a
  component cannot know what it is sitting on. Any alpha but zero passes through untouched;
  without the repair the tween would travel toward transparent *black*. It is pure and
  `internal` specifically so it can be asserted directly, since no golden can see a
  mid-transition frame.
- Call it **unconditionally** and let the `lifted` flag drive it. Branching at the call site —
  applying the modifier only when selected — rebuilds the modifier graph on the flip and kills
  the tween. `ExerciseRow` passes `restingColor = Color.Transparent` because the drawn resting
  row has no fill of its own; it sits on `--base`.

### Motion (`AppMotion`)

Animation durations and easings, exposed as tokens so they remain
consistent.

**The names and numbers below are the v1 set and are stale.** What ships is
`AppMotion.fast` = 140 ms, `base` = 260 ms, `slow` = 520 ms, with easings `out` / `spring` /
`travel` / `linear` (`core/ui/kit/.../theme/AppMotion.kt`).

```
Durations:
  instant     = 100.ms     — color toggles, micro-feedback
  fast        = 200.ms     — chip selection, FAB press
  normal      = 300.ms     — card transitions, dialog appearance
  slow        = 400.ms     — bottom sheet slide, screen transitions
  deliberate  = 600.ms     — initial loading reveal, splash → home

Easings:
  standard    = Material3 standard easing (FastOutSlowInEasing)
  emphasized  = Material3 emphasized easing (custom cubic)
  decelerate  = LinearOutSlowInEasing  — appearing elements
  accelerate  = FastOutLinearInEasing  — dismissing elements
```

The current hard-coded `defaultAnimationDuration = 600` in
`core/ui/kit` is replaced by `AppMotion.deliberate`. All other
animations use `normal` (300ms) or `fast` (200ms) as the default.

#### Loading deferral (`rememberDeferredSurface`)

Appear delay = `AppMotion.fast` = 140 ms; minimum hold = `AppMotion.base` = 260 ms.

- The numbers were set against measured loads. Device-instrumented worst case for a real load
  in this app is **61 ms** on a `debug` build (cold `all-trainings` entry, `refresh = Loading`
  → `NotLoading`); Home's warm path is **23 ms** on the same build. 140 ms therefore clears
  the measured debug distribution by 2.3x, and since release loads are not slower than debug
  ones, a release re-measure moves the margin up, never the threshold down.
- Bounded cost: the maximum content delay this can add is 260 ms (a T = 141 ms load resolves
  visually at 400 ms), and only loads in the open interval (140, 400) ms are delayed at all.
- During the deferral window the function returns `lastSettled` — the last non-loading verdict
  — and `null` only before one has been drawn. `null` means "delete what is drawn", not "leave
  it alone": call sites render a verdict by removing the block it names, and Compose keeps no
  frame behind a composable that has left composition. Returning `null` for the whole window
  blanked the region on every transition **from** an already-settled empty-region verdict —
  tapping retry on a cold-open error moves REFRESH_ERROR → LOADING while the appear delay is
  still running, so the error was removed at once and the region sat blank for up to 140 ms.
  The retained verdict is always an empty-region one: a selector returns its content verdict
  only at `itemCount > 0` and LOADING only at `itemCount == 0`, so rows can never be what
  persists.
- Call-site contract: branch on the return value and on `listBody`, and **never** re-derive
  `surface == loadingSurface` beside them — during the minimum hold the selector has already
  moved to CONTENT (or FIRST_RUN, or an error), so a screen that re-derives draws nothing for
  the rest of the hold and flashes the spinner for the millisecond the two numbers exist to
  prevent. Call `rememberDeferredSurface` where it **outlives** the loading state: sited
  inside `if (surface == LOADING) { ... }` it leaves composition at the instant the hold
  should start, so the minimum silently does nothing however the result is read. Rows and the
  loading treatment are alternatives, not layers — `listBody`'s equality test is total rather
  than heuristic because every surface selector on this arc returns its content verdict first.

#### Set-closure automaton (`SetClosureVisuals`)

- `tickProgress` needs no false→true gate: `animateFloatAsState` seeds its `Animatable` at the
  target on first composition, so a row that arrives already done renders at exactly 1 and
  never animates. `LaunchedEffect` does **not** have that property — it also runs on first
  composition — so the flash needs the explicit `wasDone` / `closedJustNow` gate, without
  which every already-completed set flashes whenever an active session loads or a completed
  card is collapsed and reopened.
- The flash cannot be rewritten as `animateFloatAsState`: it is transient (peaks at closure,
  decays, has no resting value to interpolate to), and a state-driven tween would be a
  permanently-zero constant that animates nothing.
- There is deliberately **no `markScale`**. One used to live here, resting at 0.92 and
  animating to 1.0; the growth it stood in for is real geometry — `AppCheckmarkButton` lerps
  38dp → 42dp off `closedFraction` — so keeping both would scale the growth on top of itself.
  The mockup's only `transform: scale()` is `.mark:active{transform:scale(.9)}`, a press state
  belonging to the interaction, not to the closure automaton.

#### Indicator gel (`IndicatorGel`)

`k = |Δ| / trackWidth`, clamped to 1; the peak is `1 + 0.30 × k`. `trackWidth` must be the
**OUTER** track width — it is the denominator `k` is taken against, and passing the padded
inner width instead inflates every peak.

| | track | item | neighbour peak | two-step peak |
|---|---|---|---|---|
| `AppNavBar` pill | 411.4dp | 129.1dp | +9.71% (12.5dp) | +19.41% (25.1dp) |
| `MetricTabs` thumb | 379.3dp | 121.1dp | +9.89% (12.0dp) | +19.79% (24.0dp) |

Both are three equal-width stops, so `pitch / track` is ~1/3 and ~2/3 on each. Transferable is
not identical: the tabs' item is 8dp narrower, so the same percentage is ~1dp less drawn
width. A track with unequal or differently-many stops would not land here, and the numbers
would have to be recomputed rather than assumed.

### Haptic feedback

Haptics are part of the design system contract, not optional. Every
user-initiated action that produces an MVI side effect emits a
`Haptic` event from the store. The application's Haptic event
handler (existing in `core/ui/kit/utils/`) maps the event to the
device's haptic feedback API.

Workeeper recognizes two haptic intensities:

- **light** — default for any normal click (button tap, row tap,
  toggle, segment switch, FAB press, navigation). 90% of haptic
  emissions.
- **medium** — destructive intents and important confirmations
  (delete confirm, archive permanent delete, finish session).

Stores expose this through the `Event` sealed hierarchy. The
existing pattern (used by all v1 features):

```kotlin
sealed interface Event : Store.Event {
    data object HapticLight : Event
    data object HapticMedium : Event
    // ... feature-specific events
}
```

#### When to emit haptics

Every Click action that produces a state change OR triggers
navigation OR opens a dialog must emit a haptic. Counter-examples
(do NOT emit haptic):

- Cancel/dismiss in dialogs — the haptic was already produced when
  the dialog opened; cancelling is a "negation", not a positive
  action.
- Repeated rapid actions (e.g. holding a button for continuous
  scroll) — haptics fire once on press, not on every frame.
- Pure observational actions (text input typing, scroll, swipe in
  progress before threshold).

#### Convention

Each feature spec under `documentation/feature-specs/` lists
explicit haptic mappings per Click action. The base rule of thumb:

| Action category | Intensity |
|---|---|
| Tap a row, button, FAB, toggle, chip, tab, segment | light |
| Open a screen / dialog / bottom sheet | light (on the trigger) |
| Confirm a destructive action | medium |
| Confirm an important non-destructive action (finish session) | medium |
| Cancel / dismiss any dialog | none |
| Undo from snackbar | light |

Haptic emission is the trigger's responsibility, not the receiver's.
Tapping a row that opens a dialog: the haptic emits at row tap. The
dialog opens silently (no separate haptic).

### Accessibility conventions

- **Alias a `contentDescription` before a `semantics` block.** Inside
  `Modifier.semantics { }` a bare `contentDescription` resolves to the **receiver's** own
  property, so `this.contentDescription = contentDescription` is a silent self-assign that
  compiles and drops the caller's string. Alias the parameter to a local outside the block
  first (`val label = contentDescription`). Sites: `AppExerciseThumb`, `AppNumberInput`
  (`val fieldLabel = accessibilityLabel`).
- **A `BasicTextField` owes two properties M3's `OutlinedTextField` supplies internally.**
  (1) Error semantics — M3 sets `error(...)` from `isError` inside `TextFieldImpl.kt`, so
  without an explicit `semantics { if (isError) error(errorMessage) }` an invalid box
  announces as an ordinary one. (2) The label association — `OutlinedTextField(label = ...)`
  makes it itself, but the drawn `.flabel` is a **sibling** node (`AppFieldLabel`), so it
  names the field on screen and not to a screen reader; with a value present the placeholder
  is gone too and the field would announce its text and role without ever saying which field
  it is. Every caller that draws an `AppFieldLabel` passes the same string as
  `accessibilityLabel`. `AppNumberInput` is the sharper case: its unit lives in
  `SetColumnHeader`, so the label is the only thing telling TalkBack which field it is.
- **Register a `CustomAccessibilityAction` only where it can act.** `ReorderableColumn`
  registers `moveUp` / `moveDown` conditionally (`index > 0`, `index < lastIndex`) because
  `state.moveUp` no-ops at 0 and `state.moveDown` no-ops at `lastIndex` while the action
  lambda still returns `true`: registering both unconditionally advertises an impossible
  action to a screen reader **and** reports success having done nothing. Resolve the announced
  labels outside the `semantics` block, which is not a composable scope.

## Component inventory

21 shared components in `core/ui/kit`. Each has a fixed package and a
clear purpose. No two screens should reimplement these — if a screen
needs a variation, it goes back into the kit.

### 1. AppButton

Primary, secondary, tertiary, destructive variants.

```
package: io.github.stslex.workeeper.core.ui.kit.components.button

variants:
  AppButton.Primary       — accent fill, on-accent text, the main CTA
  AppButton.Secondary     — surface tier 1 fill, primary text, alternative actions
  AppButton.Tertiary      — transparent, accent text, minor links
  AppButton.Destructive   — error tint background, error text, delete confirmations

sizes:
  large  = AppDimension.heightMd  (48.dp), 16dp horizontal padding, labelLarge text
  medium = AppDimension.heightSm  (40.dp), 14dp horizontal padding, labelMedium text
  small  = AppDimension.heightXs  (32.dp), 12dp horizontal padding, labelMedium text

API: composable functions taking text, onClick, modifier, enabled,
     leadingIcon (optional), trailingIcon (optional).

States: enabled, disabled, pressed (handled by Compose Material3 default).
```

### 2. AppCard

Surface container. Single source of card styling.

```
package: io.github.stslex.workeeper.core.ui.kit.components.card

API: AppCard(modifier, onClick = null) { content }
     onClick = null → static card
     onClick != null → ripple, surface tier 1 background, hover state

shape: AppShapes.medium
padding (default): AppDimension.cardPadding
background: surface tier 1
```

### 3. AppTextField

Wrapper around `OutlinedTextField` with standardized styling.

```
package: io.github.stslex.workeeper.core.ui.kit.components.input

API: AppTextField(value, onValueChange, label = null, placeholder = null,
                  leadingIcon = null, trailingIcon = null, modifier,
                  enabled = true, singleLine = false, keyboardOptions = default)

Visual: AppShapes.medium, border-only (no fill), surface tier 0 fill
        when focused with accent border, surface tier 1 fill when filled
```

### 4. AppNumberInput

The mockup's `.field` — a value and its unit on a recessed panel
(extraction §1.6). Specialized for weight / reps in Live workout and
the past-session read-back. Large tap target, large numbers, optimized
keyboard.

```
package: io.github.stslex.workeeper.core.ui.kit.components.input

API: AppNumberInput(value, onValueChange, modifier, decimals = 0,
                    suffix = null, enabled = true, isError = false,
                    isRecord = false, isDone = false, isLogged = false)

Visual:
  height: AppDimension.heightMd (48.dp)
  text: AppTypography.dataValue (26sp Archivo wdth 116 / wght 700);
        values longer than 3 glyphs step down to numeric.section
        (19sp bold) instead of clipping
  value color: textTertiary resting → textPrimary when isDone or
        isLogged → record.textPrimary when isRecord (record wins)
  background: surface tier 3 (field); the isDone `donefill` and
        isRecord `record.background` washes REPLACE the tier
        (isLogged keeps the plain tier — colour without the wash)
  shape: RoundedCornerShape(AppDimension.Radius.small) (8.dp)
  border: none by default (recessed by tier alone); hairline
        status.error outline when isError
  suffix display ("kg", "reps") — mono.caption trailing inside the
        field; textDim resting, promotes to textSecondary over the
        isDone / isRecord washes

KeyboardOptions:
  decimals = 0 → KeyboardType.Number
  decimals > 0 → KeyboardType.Decimal
```

### 5. AppDatePickerDialog

Single source for date pickers. Currently duplicated across two
features (single-training, exercise) — consolidated here.

```
package: io.github.stslex.workeeper.core.ui.kit.components.dialog

API: AppDatePickerDialog(initialDateMillis, onDateSelected, onDismiss,
                          modifier, dateRangeStart = null, dateRangeEnd = null)

Wraps M3 DatePickerDialog with theme-consistent styling.
```

### 6. AppEmptyState

Centered illustration + headline + supporting text + optional CTA.

```
package: io.github.stslex.workeeper.core.ui.kit.components.empty

API: AppEmptyState(headline, supportingText = null, icon = null,
                    actionLabel = null, onAction = null, modifier)

Layout: vertical column, centered, padding xxxl (48.dp) top and bottom.
        icon is AppDimension.iconLg (32.dp), monochrome, text tertiary
        tint by default. AppDimension.iconXl (48.dp) used when the
        screen is mostly empty (true blank state).
```

### 7. AppListItem

Standard row layout for lists. One source of styling for all
list-based screens.

```
package: io.github.stslex.workeeper.core.ui.kit.components.list

API: AppListItem(headline, supportingText = null, leadingContent = null,
                  trailingContent = null, onClick = null, modifier)

Visual:
  background: surface tier 1
  shape: AppShapes.medium
  padding: vertical 10dp, horizontal 12dp
  headline: bodyMedium, text primary
  supportingText: bodySmall, text tertiary
```

### 8. AppTagChip

Single tag visualization, pickable or removable.

```
package: io.github.stslex.workeeper.core.ui.kit.components.tag

variants:
  AppTagChip.Static       — read-only display
  AppTagChip.Selectable   — toggleable (filter context)
  AppTagChip.Removable    — with × close button (edit context)

Visual:
  shape: AppShapes.small
  padding: 2dp vertical, 7dp horizontal
  text: labelSmall, text secondary
  background: surface tier 4 (default), accent tinted bg (selected)
```

`CHIP_CORNER = 6.dp` is a second copy of `AppUi.shapes.small`'s 6dp (`AppShapes.kt:16`),
needed because `dashedBorder` takes a `Dp` corner radius rather than a `Shape`. It is
deliberately **not** `AppDimension.Radius.small`, which is 8dp (`AppDimension.kt:17`). Change
`provideAppShapes` and this literal must follow, or the dashed add-tag chip's outline stops
matching its own clip.

### 9. AppTagPicker

Compound component for tag selection with inline creation.

```
package: io.github.stslex.workeeper.core.ui.kit.components.tag

API: AppTagPicker(selectedTags, availableTags, onTagsChange,
                   onTagCreate, modifier)

Behavior:
  - Shows existing tags as chips, selected ones highlighted.
  - Search field at top filters availableTags by prefix.
  - When the search has no exact match, shows "+ Create '<input>'"
    affordance — calls onTagCreate.
```

### 10. AppTopAppBar

Wrapper around M3 `TopAppBar` with theme-consistent styling and
explicit slots.

```
package: io.github.stslex.workeeper.core.ui.kit.components.topbar

API: AppTopAppBar(title, navigationIcon = null, actions = {}, modifier)

Visual:
  height: AppDimension.heightLg (56.dp, M3 small variant)
  background: surface tier 0
  title: titleMedium, text primary
  no border below by default; subtle border (border subtle) when content
    scrolls under (via M3 scrollBehavior)
```

### 11. AppNavBar

3-destination bottom navigation, **icon-only**. The v3 rebuild
(`pass2d.html` `#s-nav`, the `.nb.track.slide` variant) — see
v3-redesign-spec §26 "Bottom navigation" and "Nav pill motion".

Replaces `AppBottomBar` **and** app/app's `WorkeeperBottomAppBar`;
both are deleted. The destination model deliberately did **not** move
into the kit with the treatment: the kit cannot reach
`core:ui:navigation` or app/app's string resources, which is why the
old kit-resident `AppBottomBarDestination` shipped hardcoded English
labels in a Russian app. `BottomBarItem` stays in the app tier (`app:common`) and passes
resolved icons and strings down.

```
package: io.github.stslex.workeeper.core.ui.kit.components.navbar

API: AppNavBar(items, selectedIndex, onSelect, modifier)
     AppNavBarItem(icon, contentDescription, testTag)

Visual:
  height: 56dp (heightMd 48 pill + 2 x Space.xs) + navigation-bar inset
          — the drawn 60px derived, exactly as .topbar's identical 60px was
  background: surface tier 1 (--sec), hairline (borderSubtle) on top edge
  active: icon textPrimary on a lifted surfaceTier2 pill (liftedSurface)
  inactive: icon textTertiary, no container
  glyphs: iconMd, AppIcons.Home / .Trainings / .Exercises
          (the latter two are the empty-state marks, one vector each)

Motion:
  pill offset 340ms out (transit); scaleX gel peak 1 + 0.30 x k at 42%,
  transform-origin on the leading edge (character, §26 ledger)

Haptics: none. The caller fires SegmentTick, matching every other
         haptic in the app.
```

Two things the layout and the motion depend on:

- The drawn `border-top:1px solid var(--hair-s)` ships as a `HorizontalDivider` **overlaid**
  on the bar's top edge (`Alignment.TopCenter`), not as a third box in a column, so the bar's
  total height stays exactly `AppDimension.BottomNavBar.heightWithInsets`. CSS `border-top` on
  a `content-box` element adds its 1px *outside* the declared 60px; reproducing that as a
  layout row would put the bar 0.5dp off the number `AppNavigationHost` pads every bottom-bar
  destination's content by (the bare `BottomNavBar.height`).
- `stretchOrigin` is written once inside `LaunchedEffect(selectedIndex)` and never derived
  during composition: the jump's delta is gone from state as soon as `previousIndex` catches
  up, so a derived origin would flip back to its default part-way through the 340 ms travel
  and stretch the pill from the wrong edge for the rest of it. `previousIndex` is seeded with
  the initial `selectedIndex` for the same class of reason — unseeded, the first composition
  reads as a 0 → n jump and fires a stretch on a settled bar.

### 12. AppBottomSheet

Wrapper around M3 `ModalBottomSheet`.

```
package: io.github.stslex.workeeper.core.ui.kit.components.sheet

API: AppBottomSheet(onDismiss, modifier) { content }

Visual:
  shape: AppShapes.large for top corners
  background: surface tier 1
  drag handle: visible, default M3 styling
```

- **The window owns the scrim and the grab handle; `AppSheetLayout` owns the title and the
  content.** Both window layers are affordances for gestures the content cannot service.
  `AppBottomSheet` passes `dragHandle = { SheetGrabHandle() }` to `ModalBottomSheet`, so a
  grab handle drawn inside sheet content would be the **second** handle in every sheet.
- **IME-padded, never IME-resized.** `ModalBottomSheet`'s own `contentWindowInsets` defaults
  to `safeDrawing.only(Bottom + Top)` and `safeDrawing` includes the IME, so sheet content
  already gets bottom padding equal to the keyboard — verified on device (API 35, portrait, an
  820px IME: the sheet reflowed above it unaided). What that padding cannot do is make
  oversized content fit: on API 30+ Material sets this window to `SOFT_INPUT_ADJUST_NOTHING`
  (`ModalBottomSheet.android.kt`), so the window is never resized and anything taller than the
  space left above the keyboard is simply covered — measured in landscape, where the exercise
  picker's search field vanished entirely. Content that can outgrow that space must bound
  itself: `AppTagPickerSheetContent` uses `CHIP_AREA_MAX_HEIGHT = 360.dp` + `verticalScroll`,
  the same bound `ExercisePickerSheet` uses for the same seat.

### 13. AppDialog

Standard text dialog with title, body, and 1-2 actions.

```
package: io.github.stslex.workeeper.core.ui.kit.components.dialog

API: AppDialog(title, body, confirmLabel, onConfirm, dismissLabel = null,
                onDismiss = null, destructive = false, modifier)

Visual:
  shape: AppShapes.medium
  background: surface tier 2 (light) / surface tier 1 (dark)
  title: titleLarge
  body: bodyMedium, text secondary
  confirm button: AppButton.Primary (or Destructive if destructive=true)
  dismiss button: AppButton.Tertiary
```

### 14. AppConfirmDialog

Specialized variant for destructive confirmations with explicit
two-tap protection. Used by Archive permanent delete.

```
package: io.github.stslex.workeeper.core.ui.kit.components.dialog

API: AppConfirmDialog(title, body, impactSummary, confirmLabel,
                       onConfirm, onDismiss, modifier)

Visual: same shape as AppDialog, but:
  - Confirm button is NOT the default focus
  - "Cancel" gets default focus
  - confirm button uses AppButton.Destructive
  - impactSummary shown in error tint as a banner above the body text
    (e.g. "47 sessions of history will be deleted")
```

### 15. AppFAB

Floating action button — a single primary action on a list screen.

```
package: io.github.stslex.workeeper.core.ui.kit.components.fab

API: AppFAB(icon, contentDescription, onClick, modifier)

Visual:
  size: 56.dp x 56.dp (M3 default; this is a unique component,
        not pulled from heightLg because FAB is square not bar-shaped)
  background: accent
  icon tint: on-accent, AppDimension.iconMd (24.dp)
  shape: AppShapes.medium
  no shadow (color-based elevation only)
```

Two guards on the glyph crossfade:

- `contentDescription` lives on the `FloatingActionButton`'s semantics, not on the `Icon`s:
  for the 260 ms `AnimatedContent` crossfade two `Icon`s are composed at once, and a
  description on each would merge into a single node announcing both.
- `using null` in
  `transitionSpec = { fadeIn(glyphSpec) togetherWith fadeOut(glyphSpec) using null }`
  suppresses `AnimatedContent`'s default `SizeTransform` — both glyphs are
  `AppDimension.iconMd`, and an animated container would introduce the reflow the fixed size
  exists to prevent.

### 16. AppLoadingIndicator

Generic loading spinner.

```
package: io.github.stslex.workeeper.core.ui.kit.components.loading

API: AppLoadingIndicator(modifier, size = AppDimension.iconMd, color = accent)

Visual: M3 CircularProgressIndicator wrapped with theme defaults.
```

### 17. AppSetTypeChip

Compact badge for set type indicators (W / · / F / D).

```
package: io.github.stslex.workeeper.core.ui.kit.components.setchip

API: AppSetTypeChip(type: SetType, modifier)

SetType is the domain enum (warmup / work / fail / drop).

Visual:
  size: 18.dp height, auto width based on label
  shape: AppShapes.small
  label: 1 char (W/·/F/D), labelSmall, text uppercase, letter-spacing 0.4sp
  bg/fg: per the semantic color table above (warmup/work/fail/drop)
```

### 18. AppSegmentedControl

Tab-like segment selector, used in Archive screen (trainings /
exercises).

```
package: io.github.stslex.workeeper.core.ui.kit.components.segmented

API: AppSegmentedControl(items, selected, onSelectedChange, modifier)
where items is a List of text labels.

Visual:
  track height: AppDimension.heightSm (40.dp) = heightXs + 2 x Space.xs
  segment height: AppDimension.heightXs (32.dp)
  shape: AppShapes.small
  background: surface tier 1
  selected segment: liftedSurface (surfaceTier2 + slabtop), accent tinted foreground
  unselected: text tertiary
  gap between segments: Space.xs (4.dp), no rule
```

Two pieces of `.mseg` geometry are load-bearing for the lifted thumb, not decorative.
`TRACK_PADDING` insets the thumb from the track's clipped edge — without it the light theme's
cast shadow has nowhere to fall and the lift is invisible in exactly one theme, which is the
failure mode `liftedSurface` exists to fix. And the segments are separated by a 3px gap and
**no rule**: a hairline divider is a sibling of the thumb, so a lifted thumb would have a seam
running down its edge. Do not reintroduce the dividers this component used to draw.

### 19. AppSnackbar

Wrapper around M3 `Snackbar` with inverted styling.

```
package: io.github.stslex.workeeper.core.ui.kit.components.snackbar

API: AppSnackbar(snackbarData, modifier)

Used inside an `AppSnackbarHost` which is placed in the Scaffold of
each screen that needs feedback.

Visual:
  background: surface inverse (#1B1C1A) — same in both modes
  text: surface inverse on (#E8E8E5)
  action label: accent (#6EB7AB) — visible against dark bg
  shape: AppShapes.medium
  margin from screen edges: 16.dp
  duration: short = 4s (default), long = 8s (with Undo action)
```

### 20. AppSwipeAction (extra — added during scope review)

Container for swipe-to-archive on list rows.

```
package: io.github.stslex.workeeper.core.ui.kit.components.swipe

API: AppSwipeAction(actionIcon, actionLabel, actionTint, onAction,
                     modifier) { content }

Behavior: wraps `content` in a swipeable row. Reveal action panel on
swipe-from-end. Action panel uses actionTint as background (typically
error or warning).
```

### 21. AppSettingsRow

A full-width row designed for Settings-style preference pickers and
list-style menu entries. The default layout for everything that
appears under Settings, Archive headers, future Manage tags, etc.

```
package: io.github.stslex.workeeper.core.ui.kit.components.settings

variants:
  AppSettingsRow.Navigation — title + optional subtitle + trailing chevron;
                              tap navigates somewhere
  AppSettingsRow.Choice     — title + optional subtitle + leading RadioButton;
                              tap selects (use inside selectableGroup())
  AppSettingsRow.Toggle     — title + optional subtitle + trailing Switch
  AppSettingsRow.Action     — title + optional subtitle + trailing icon;
                              tap fires an action (e.g. external link)

API: composables taking title, subtitle (optional), enabled (default true),
     onClick (or onCheckedChange for Toggle, selected for Choice).

Visual:
  width:            full screen width (modifier.fillMaxWidth())
  height:           AppDimension.heightSm minimum, taller if subtitle wraps
  horizontal padding: AppDimension.screenEdge (16.dp)
  vertical padding:   12.dp
  background:       transparent (the row sits on parent surface)
  ripple:           full row width on tap
  title:            AppUi.typography.bodyMedium, AppUi.colors.textPrimary
  subtitle:         AppUi.typography.bodySmall, AppUi.colors.textTertiary
  spacing between title and subtitle: 2.dp
  trailing chevron / icon: AppDimension.iconSm (18.dp)

Modifier behavior:
  - Use Modifier.clickable for Navigation / Action variants.
  - Use Modifier.selectable(role = Role.RadioButton) for Choice variant.
  - Wrap a group of Choice rows in a Column with Modifier.selectableGroup().
  - Choice variant: tapping anywhere on the row selects the option, ripple
    covers the full row width. The RadioButton is purely visual indicator;
    do not handle its onClick separately.
```

#### Why a dedicated component

Settings UIs frequently fall into the trap of using bare
`RadioButton + Text` or `Text + Switch` without a unifying row
container. The result is small floating hit targets and ripples that
do not match the visual extent of the option. AppSettingsRow
enforces full-width tap targets and consistent typography across all
settings surfaces.

(Counts as 19 + 1 added during scope review per the chat session
+ 1 added in Stage 5.1 (AppSettingsRow); final count = 21 components.)

## Kit components added after v1

Shared components in `core/ui/kit` outside the numbered v1 inventory above, recorded with the
constraints that are not visible from a call site.

### AppListRow

Its two modifier seams are **not** interchangeable. `modifier` reaches the outer `Column` —
the row **plus** its divider — and is what a caller animates (`Modifier.animateItem`).
`rowModifier` reaches the inner `Row` — the ruled area **without** the rule — and is what a
caller lifts, clicks and tags. Swapping them breaks things silently: a `liftedSurface` on the
outer box paints behind the divider, and a `combinedClickable` there makes the hairline part
of the touch target. `rowModifier` is applied **before** `heightIn(min = rowHeight)` and the
`screenEdge` gutter, which is where every caller's chain sat before the extraction, so the
painted region and the touch region are unchanged.

### AppCheckmarkButton

`MARK_SIZE = 46.dp` is the mockup's `.mark` size **and** the room the spring overshoot needs.
In `SetDoneMark` the geometry — the side lerping `SHAPE_REST` 38dp → `SHAPE_DONE` 42dp and
the radius `REST_RADIUS` 19dp → `DONE_RADIUS` 13dp — rides `closedFraction`, which is `spring`
and legitimately overshoots past 1.0, so the drawn side goes beyond 42dp; a canvas sized to
the done state would clip it. Every colour and the tick's own progress ride `out`; the press
scale rides `spring` because it is geometry.

### AppMiniIconButton

`MINI_SIZE = 34.dp` deliberately undershoots the 48dp minimum touch-target guidance. It is the
mockup's drawn `.mini` size (34×34, 17dp glyph), three in a row in a card header where 48dp
targets would not fit — the same trade the mockup makes, recorded rather than accidental.

### DashedBorder

`Modifier.border` cannot draw a `1px dashed` CSS border, which the mockups use as the
excluded-from-the-record signature (skipped rail group, one-off ordinal chip and badge,
`.addex`). CSS leaves dash geometry to the user agent, so the rhythm was measured off Chrome's
rendering of the mockup — a 1px dashed border is ~3px on / ~3px off — and kept as fixed
`DASH_ON = 3.dp` / `DASH_OFF = 3.dp` rather than exposed as a per-call-site parameter. The
stroke is centred inside the bounds (`inset = stroke / 2`) the way a CSS border is.

### ReorderableColumn / ReorderableColumnState

Live-commit: as the dragged item's centre crosses a neighbour's centre the state fires
`onMoveResolved` immediately and re-anchors the offset so the finger stays on the same visual
point across the swap. On release the state simply resets — there is no terminal
`onMoveResolved` call.

- Consumers **must** apply `onMoveResolved` synchronously: update the list state before
  returning from the lambda. Async or throttled updates desync the crossover logic and are not
  supported.
- Live-commit has no preview-displacement layer, so non-dragged rows snap to their new layout
  positions. For smooth slide-in the consumer `Column` must be wrapped in a `LookaheadScope`
  with `Modifier.animateBounds(scope)` applied to each row; without it the siblings jump.
- `onItemPlaced` returns early when `key == draggedKey`, because during a drag
  `boundsInWindow()` for the dragged item **includes** the `graphicsLayer.translationY` the
  drag applies, and feeding those transformed bounds back into the crossover math corrupts it.
- `onItemPlaced` is the only writer of `itemTops` / `itemBottoms` / `itemIndices` /
  `keysByIndex`, and a removed row simply stops calling it — so a row that leaves composition
  without `onItemDisposed` keeps its bounds, its index and its slot, and stays a drop target
  that is not on screen. Two concrete failures: with three 100px rows at y = 0 / 100 / 200 and
  "c" disposed, dragging "b" by 101px puts the finger at 251, past the departed row's old
  centre at 250, and commits a phantom move against a dead key; and `moveDown`'s boundary
  guard, which reads `keysByIndex.keys.maxOrNull()`, still believes index 1 can move down when
  "b" is now the last row. `onItemDisposed` clears the `keysByIndex` slot **only** when it
  still points at that key, because a reorder may already have handed that index to another
  row. It is called from `reorderableColumnItem`'s `DisposableEffect`, so both consumers get
  it without recreating the state when membership changes.
- `lastIndex` is **required** and must not gain a default — a default is a value every
  existing call site would silently keep, which is the bug.
- `reorderableColumnItem` deliberately installs **no** long-press detector on the whole row;
  gesture detection lives only in `reorderableColumnDragHandle`, so child widgets that consume
  long-press (text fields, tooltip wrappers) cannot block the reorder affordance.

### AppIcons

- Contexts whose drawn stroke weights differ by **0.1 viewBox units** are deliberately
  collapsed onto one `ImageVector`, on the ruling that 0.1 is invisible below 24dp:
  `MoreVertical` serves the 1.7 top-bar context and the 1.8 card context (`.mini.menu`) from
  one vector, and `NAV_GLYPH_STROKE` resolves to `EMPTY_GLYPH_STROKE` = 1.6f even though
  `.nb button svg` declares 1.7 — so `Trainings` and `Exercises` are **one vector each**,
  shared by the nav bar and the empty state, and cannot drift apart.
- Constants that are numerically equal are still named separately when the drawing declares
  them separately and either could move alone: `THUMB_STROKE = TOPBAR_STROKE` (1.7f — `.thumb
  svg` sits in the top bar but is not an `.icon-btn`) and
  `NAV_GLYPH_STROKE = EMPTY_GLYPH_STROKE` (1.6f).
- The manifest sets `android:supportsRtl="true"`, so a fixed path in a directional mark points
  the wrong way under an RTL layout direction. `strokeIcon(autoMirror = true)` is therefore set
  on any glyph whose meaning is "back", "forward" or "onward" (`ChevronLeft`, `ChevronRight` —
  the latter also because the Material equivalent it sits beside in `TrainingExerciseRow`
  auto-mirrors) and left off for marks carrying no direction. Media-transport glyphs such as
  `Skip` stay off by convention: a timeline reads left-to-right in every locale.

### AppSectionHeader / AppLabel

`AppSectionHeader`'s `Row` applies `padding(horizontal = AppDimension.screenEdge)` of its own,
so a screen whose column already carries the gutter must use the bare `AppLabel` rung instead
or the gutter doubles — `TrainingEditScreen`'s `TagsSection` does exactly that. The opposite
arrangement is equally valid and also shipped: `TrainingDetailScreen` gives each block the
gutter individually (its `InGutter` helper) so full-bleed sections can opt out and
`AppSectionHeader` is used directly, and `PastSessionScreen`'s `LoadedContent` pads per item
rather than through the `LazyColumn`'s `contentPadding` for the same reason.

## Module structure

```
core/ui/kit/
  src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/
    theme/
      AppTheme.kt           — composable AppTheme { content }
      AppColors.kt          — data class + provideAppColors()
      AppTypography.kt      — three bundled families, six sizes, 15 M3 aliases
      AppDimension.kt       — extended with semantic aliases
      AppShapes.kt          — small/medium/large
      AppMotion.kt          — durations + easings
      AppElevation.kt       — surface tier mapping
      LocalAppColors.kt     — CompositionLocal
      LocalAppTypography.kt — CompositionLocal
      LocalAppMotion.kt     — CompositionLocal
    components/
      button/AppButton.kt
      card/AppCard.kt
      input/AppTextField.kt
      input/AppNumberInput.kt
      dialog/AppDatePickerDialog.kt
      dialog/AppDialog.kt
      dialog/AppConfirmDialog.kt
      empty/AppEmptyState.kt
      list/AppListItem.kt
      tag/AppTagChip.kt
      tag/AppTagPicker.kt
      topbar/AppTopAppBar.kt
      navbar/AppNavBar.kt
      sheet/AppBottomSheet.kt
      fab/AppFAB.kt
      loading/AppLoadingIndicator.kt
      setchip/AppSetTypeChip.kt
      segmented/AppSegmentedControl.kt
      snackbar/AppSnackbar.kt
      swipe/AppSwipeAction.kt
```

## How AppTheme is consumed

```kotlin
// in app entry point:
AppTheme(darkTheme = isSystemInDarkTheme()) {
    // root composable
}

// in any composable:
val colors = LocalAppColors.current        // semantic colors
val typography = LocalAppTypography.current
val motion = LocalAppMotion.current

// Spacing/radius are NOT a CompositionLocal: `AppDimension` is a plain
// object, read directly (e.g. `AppDimension.Radius.largest`). There is no
// `LocalAppDimension`.
val radius = AppDimension.Radius.largest

Text(
    text = "Sample",
    style = typography.titleMedium,
    color = colors.textPrimary,
)

// M3 still works:
MaterialTheme.colorScheme.primary  // = colors.accent
```

`AppTheme` configures both M3 `MaterialTheme` (mapping the custom
ColorScheme) AND provides `LocalAppColors` etc. Any screen can choose
to consume the M3 path or the App path.

## Migration notes

The current `core/ui/kit/theme/AppColors.kt` has 5 colors only. It is
**replaced** with the new structure. The current
`core/ui/kit/theme/AppDimension.kt` is **extended** (existing tokens
stay, new aliases added).

Existing usages of `AppColors.dark`, `AppColors.confirm`, etc. across
the codebase need updating to the new color tokens. This is part of
the implementation task.

## Open questions deferred to feature specs

- Empty state copy and illustration concept per screen.
- Specific tap targets for set entry (whole row tappable vs separate
  weight/reps fields).
- Drag-to-reorder behavior in Edit training (uses AppListItem +
  custom drag handle composable — feature spec decision).
- Dynamic font scaling — whether to clamp user font size, ignore, or
  rescale layout for accessibility settings.
- Reduce-motion preference — global toggle vs per-component default.

---

## Claude Code prompt

Run after this design system spec is approved.

```
Implement Workeeper design system v1 per documentation/design-system.md.

GOAL
Replace and extend `core/ui/kit/theme/` with the full token system from the spec, and create 20 shared components under `core/ui/kit/components/`. Wire AppTheme to expose both Material 3 (via MaterialTheme) and custom App tokens (via CompositionLocal).

**Historical.** What follows is the prompt that built the v1 kit, kept as a record of how it
was specified. It names Inter, `GoogleFont.Provider` and a 13-slot scale, none of which has
shipped since #177 — read it as an account of the past, not as instructions.

PROCESS — TWO PASSES

PASS 1 — TOKENS AND THEME
Goal: get the full token system in place and AppTheme composable working. No components yet.

1. Read documentation/design-system.md cover to cover.
2. Replace core/ui/kit/theme/AppColors.kt with the new structure: data class AppColors with all dark/light values, factory functions provideDarkAppColors() / provideLightAppColors().
3. Create core/ui/kit/theme/AppTypography.kt with Inter font family loaded via Google Fonts (use androidx.compose.ui.text.googlefonts.GoogleFont). Define AppTypography data class with the 13-slot type scale.
4. Extend core/ui/kit/theme/AppDimension.kt with the spacing scale and semantic aliases (do not break existing tokens).
5. Create core/ui/kit/theme/AppShapes.kt with small/medium/large shape definitions.
6. Create core/ui/kit/theme/AppMotion.kt with duration and easing tokens.
7. Create core/ui/kit/theme/AppElevation.kt mapping M3 elevation slots to surface tiers.
8. Create CompositionLocals: LocalAppColors, LocalAppTypography, LocalAppMotion in their respective files.
9. Rewrite core/ui/kit/theme/AppTheme.kt as `@Composable fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` that:
   - Picks dark/light AppColors set
   - Builds M3 ColorScheme from AppColors mapping
   - Provides AppColors / AppTypography / AppMotion via CompositionLocalProvider
   - Wraps content in MaterialTheme with the constructed ColorScheme + Typography + Shapes
10. Update existing usages in the codebase that reference AppColors.dark / AppColors.confirm / AppColors.cancel / AppColors.error — replace with the new tokens (textPrimary, accent, semanticSuccess, etc.).
11. Verify `./gradlew :core:ui:kit:assembleDebug detekt` passes.
12. STOP and report. Do not proceed to PASS 2 without explicit approval.

PASS 2 — COMPONENTS
Goal: implement the 20 shared components.

1. Create one file per component under core/ui/kit/src/main/kotlin/.../components/<package>/<Name>.kt.
2. Each component must:
   - Use only tokens from AppColors / AppTypography / AppDimension / AppShapes / AppMotion. No hardcoded colors, sizes, or font sizes.
   - Have a Compose Preview function below the implementation showing both light and dark mode.
   - Have `internal` visibility on private composables; `public` on the entry-point composable.
   - Follow existing project naming and detekt rules.
3. AppDatePickerDialog — copy logic from the two current implementations (feature/single-training, feature/exercise), unify to one file, delete the originals from feature modules and update their imports.
4. ~~AppBottomBar — encode the 3-destination structure (Home / Trainings / Exercises). Destinations and icons are hard-coded in the kit (not configurable). When a feature module needs the bottom bar, it just calls `AppBottomBar()`.~~ **Superseded, and this instruction is the origin of a shipped defect — kept struck through rather than deleted so the reason survives.** Hard-coding the destinations in the kit is exactly what forced English literal labels: the kit reaches neither `core:ui:navigation` (for `Screen.BottomBar`) nor the app tier's `R.string.bottom_bar_label_*`. The rebuilt `AppNavBar` (§11) takes destinations as a parameter and `BottomBarItem` stays in the app tier (`app:common`). No feature module calls it — the host does.
5. Verify all components compile and previews render: `./gradlew :core:ui:kit:assembleDebug`.
6. Run `./gradlew detekt lintDebug` — pass.
7. STOP and report.

CONSTRAINTS
- All component code in English.
- All naming exact match to the spec (AppButton.Primary, not AppPrimaryButton; AppListItem, not AppListRow; etc.).
- No M3 component wrapping that adds nothing — if a component is just an alias to M3 with no extra behavior, prefer using M3 directly. The components in the spec all add either styling (AppCard's surface tier 1 default) or domain semantics (AppSetTypeChip's enum mapping).
- Do not start UI feature rewrites in this PR. Scope is design system only. The 5 v1 features (Home, Trainings, Exercises, Live workout, Settings) will be rewritten in subsequent stages, using these tokens and components.
- Inter font: use Google Fonts API via androidx.compose.ui.text.googlefonts. Add the dependency to core/ui/kit/build.gradle.kts if not already present. Configure the GoogleFont provider.

VERIFICATION CHECKLIST
- [ ] AppTheme composable works in both light and dark mode (verified via Preview).
- [ ] All 20 components have Compose Previews showing both modes.
- [ ] No hardcoded color hex values outside core/ui/kit/theme/AppColors.kt.
- [ ] No hardcoded font sizes outside core/ui/kit/theme/AppTypography.kt.
- [ ] No hardcoded dp/sp outside core/ui/kit/theme/AppDimension.kt and AppShapes.kt.
- [ ] `./gradlew :core:ui:kit:assembleDebug` passes.
- [ ] `./gradlew detekt lintDebug` passes.
- [ ] Existing usages of AppColors.dark/confirm/cancel/error in feature modules and other core modules are updated and compiling.
- [ ] No reference to the old 3 DatePickerDialog implementations remains in feature modules.

PR
Open one PR titled `feat(ui): implement design system v1`. Body lists tokens added, components created, files migrated. Mark as draft until both PASS 1 and PASS 2 are complete.
```

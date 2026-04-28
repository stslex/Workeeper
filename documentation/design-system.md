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

### Typography

Single font family token. Inter loaded via Google Fonts with system
fallback chain.

```
fontFamily token: AppTypography.fontFamily
  default = FontFamily(GoogleFont("Inter")) with fallback FontFamily.SansSerif

Two weights only:
  regular = FontWeight.Normal (400)
  medium  = FontWeight.Medium (500)

No bold (700) anywhere in the app. Weight contrast comes from 400/500.
```

#### Type scale

Maps directly to Material 3 typography slots (so M3 components pick
them up automatically), but with values calibrated for calm dense
density — slightly smaller than M3 defaults, slightly tighter
line-height.

```
displayLarge        — unused (kept at M3 default for completeness)
displayMedium       — unused
displaySmall        — unused
headlineLarge       — 28sp / 36sp / 500   — only used in onboarding (not v1)
headlineMedium      — 22sp / 28sp / 500   — full-screen titles (Stats — v2)
headlineSmall       — 20sp / 26sp / 500   — Exercise detail name, screen heroes
titleLarge          — 18sp / 24sp / 500   — section headers, dialog titles
titleMedium         — 16sp / 22sp / 500   — TopAppBar title, list emphasis
titleSmall          — 14sp / 20sp / 500   — secondary titles
bodyLarge           — 16sp / 22sp / 400   — main body text (rare)
bodyMedium          — 14sp / 20sp / 400   — primary body in lists, descriptions
bodySmall           — 13sp / 18sp / 400   — meta info, captions
labelLarge          — 14sp / 20sp / 500   — primary buttons
labelMedium         — 12sp / 16sp / 500   — chips, secondary buttons
labelSmall          — 11sp / 14sp / 500   — pills, badges, section eyebrows
```

Letter spacing follows M3 defaults except section eyebrows
(`labelSmall` used as `text-transform: uppercase` headers) which use
`letterSpacing = 0.6sp` and `letterSpacing = 0.5sp` for the smallest
labels — these are the only places where letter spacing is touched.

#### Tabular numbers

Set entry rows, weight values, rep counts use `fontFeatureSettings =
"tnum"`. Numbers stay column-aligned when values change. Critical for
Live workout. Implementation: extension `Modifier.tabularNumbers()`
or wrap in a `TextStyle.copy(fontFeatureSettings = "tnum")`.

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
heightXs = 32.dp     — small button, dense list rows, segmented
heightSm = 40.dp     — medium button, default list item, chip row
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
- AppBottomBar — uses M3 NavigationBar default (80dp in M3 v1.2+),
  no override.
- AppSegmentedControl — heightXs (slightly compact).

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

### Motion (`AppMotion`)

Animation durations and easings, exposed as tokens so they remain
consistent.

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

Specialized for weight / reps in Live workout. Large tap target,
large numbers, optimized keyboard.

```
package: io.github.stslex.workeeper.core.ui.kit.components.input

API: AppNumberInput(value, onValueChange, decimals = 0, suffix = null,
                    modifier, enabled = true)

Visual:
  height: AppDimension.heightMd (48.dp)
  text: titleLarge, tabularNumbers
  background: surface tier 2
  shape: AppShapes.small
  suffix display ("kg", "reps") — tertiary text trailing inside the field

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

### 11. AppBottomBar

3-tab bottom navigation. Hardcoded structure (Home / Trainings /
Exercises) — not a generic component, but lives in the kit because
it's used app-wide.

```
package: io.github.stslex.workeeper.core.ui.kit.components.bottombar

API: AppBottomBar(currentDestination, onDestinationChange, modifier)

Visual:
  height: M3 NavigationBar default (80.dp in M3 v1.2+)
  background: surface tier 0 with border subtle on top
  active tab: accent tint, label visible
  inactive tab: text tertiary, label visible (always show labels)
```

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
  height: AppDimension.heightXs (32.dp)
  shape: AppShapes.small
  background: surface tier 1
  selected segment: accent tinted bg, accent text
  unselected: text tertiary
  divider between segments: border subtle
```

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

## Module structure

```
core/ui/kit/
  src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/
    theme/
      AppTheme.kt           — composable AppTheme { content }
      AppColors.kt          — data class + provideAppColors()
      AppTypography.kt      — Inter font family + 13-slot type scale
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
      bottombar/AppBottomBar.kt
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
val dimension = LocalAppDimension.current  // already exists

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
4. AppBottomBar — encode the 3-destination structure (Home / Trainings / Exercises). Destinations and icons are hard-coded in the kit (not configurable). When a feature module needs the bottom bar, it just calls `AppBottomBar()`.
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

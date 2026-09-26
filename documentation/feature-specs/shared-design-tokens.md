# Shared design tokens

Phone and Wear consume the same palette constants and bundled font assets. Platform adapters
assign those resources to their own component roles and geometry. Extracting the constants does
not change the established palette values or their contrast obligations.

## Contrast roles

The existing measurements and decisions live in
[the v3 blocker registry, B6 and B19](v3-redesign-spec.md#25-blocker-registry--append-only).
These are preserved derivations, not new measurements of Wear UI:

- Dark destructive text uses `#DF714B`. The former specification value `#C4574A` did not meet
  4.5:1 on any dark surface; B6 records 4.46 on base through 3.28 on raise. The selected value
  reaches 4.500986 on raise, so it has no spare contrast margin.
- Light molten text uses `#BE3E0C`. `#C2410C` measured 4.325:1 against the PersonalRecordCard
  backdrop (molten wash over page), below its 4.5:1 obligation. The chosen foreground clears
  the real backdrops at 4.52–5.41. Its border remains `#C2410C` with alpha; the fill is `#F97316`.
- Light tertiary text uses `#596169`; B19 records why the older mockup's `#69727C` was replaced.
- Enabled-control outlines use `#627587` in dark and `#748396` in light to meet 3:1. Decorative
  hairlines keep their separate role and do not substitute for these outlines.

## Alpha roles

The 8-bit alpha constants preserve the mockup's separate semantic roles. Dark hair is 5%
(`0x0D`), grid is 7% (`0x12`), and done-fill is 5% (`0x0D`) over white. Light hair is 7%
(`0x12`), grid is 9% (`0x17`), and done-fill is 6% (`0x0F`) over `#0D1114`. Dark hair and
done-fill therefore coincide, while their light counterparts differ. Keep the tokens distinct.

## Review ledger

2026-09-26, PR #292: the copied contrast derivation and rejected-value history in
`AppColorTokens.kt` were a confirmed comment-policy violation. They are preserved here and in
B6/B19; the code retains role guards and stable documentation anchors.

2026-09-26, PR #292: the Wear runtime UI failure wrapper used unconditional Android logcat,
bypassing the phone's Dev/Debug logging policy and Crashlytics logger. The wrapper must route
recoverable exceptions through the shared logger: Crashlytics receives the exception in every
distribution, while logcat obeys `Log.isLogging`. Fatal errors still propagate and callback
failures do not trigger an automatic retry.

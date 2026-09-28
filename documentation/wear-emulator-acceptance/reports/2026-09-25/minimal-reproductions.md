# Minimal reproductions on the frozen debug APK

Prepared procedures only; this document executes nothing. Use an isolated round 192 dp emulator with the exact DevDebug/test APK hashes in the report. Verify actual API, locale, font scale and APK hash first. Reset inherited idle with the explicit SLEEP → WAKEUP sequence; WAKEUP alone while already Awake does not reset the inherited idle boundary. After launch, inspect `dumpsys power` for Awake and `dumpsys activity activities` for the actual resumed Workeeper MainActivity before capturing. If these preconditions are missing, stop and preserve the setup attempt. Source is 85ae; release ignores synthetic fixture extras. Do not run these interactions during a measured passive interval.

## Completion / information geometry

On API 36, configure the known 192 dp emulator for RU/font 1.24 using the frozen setup tool. Output names must be new:

```sh
ADB=/absolute/path/to/adb
SERIAL=emulator-5580
CHECKOUT=/absolute/path/to/checkout-at-85ae
python3 "$CHECKOUT/documentation/wear-emulator-acceptance/configure_cell.py" \
  --adb "$ADB" --serial "$SERIAL" --locale ru --scale 1.24 \
  --package io.github.stslex.workeeper.dev --screen-timeout-ms 120000 \
  --disable-tilt-to-wake --output /absolute/new-output/configuration.json
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_SLEEP
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP
"$ADB" -s "$SERIAL" shell am start -W --user 0 \
  -n io.github.stslex.workeeper.dev/io.github.stslex.workeeper.wear.MainActivity \
  --es wear_surface_fixture complete
"$ADB" -s "$SERIAL" shell dumpsys power
"$ADB" -s "$SERIAL" shell dumpsys activity activities
# Continue only after observing Awake and actual resumed Workeeper MainActivity.
"$ADB" -s "$SERIAL" exec-out screencap -p > /absolute/new-output/complete-initial.png
```

Observe the finish instruction, then scroll the actual content to its maximum and capture again. Recorded API 36/RU 192/font 1.24 has maximum 38 px and still clips letters. The same fixture was independently observed on API 30; do not infer one API from the other. For EN/font 1.24 use `--locale en` and repeat `complete`, `anonymous_complete`, and `retryable` fixture routes. Preserve the raw rectangle assertion separately from actual glyph-loss evidence. Use the reviewed durable API 30 locale helper/follow-up configure tool for API 30; the frozen property-only setup alone is insufficient to establish RU locale.

Source anchors: `WearControllerScreen.kt` → `InstructionScaffold`, `WorkoutCompleteContent`, `RetryScaffold`, `StatusRow`. The report does not prescribe weakening the round-edge assertion or reducing font scale.

## Actual system Tile freshness / visible status

1. In the actual system carousel/picker, add **Active workout**. Do not substitute an in-app preview. Record system binding to `WorkoutTileService` and the native screen.
2. Seed an active synthetic controller while Awake:

```sh
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_SLEEP
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP
"$ADB" -s "$SERIAL" shell am start -W --user 0 \
  -n io.github.stslex.workeeper.dev/io.github.stslex.workeeper.wear.MainActivity \
  --es wear_surface_fixture active_boundary
```

After the seed command, inspect actual Awake/resumed MainActivity before leaving it.

3. Use HOME and the observed native carousel to return to the actual Tile; do not use guessed tap coordinates. While it is visible, send the protected debug refresh:

```sh
"$ADB" -s "$SERIAL" shell am broadcast --user 0 \
  -n io.github.stslex.workeeper.dev/io.github.stslex.workeeper.wear.runtime.AcceptanceScenarioReceiver \
  -a io.github.stslex.workeeper.wear.ACCEPTANCE_SCENARIO --es scenario refresh
```

Require `result=-1, data="accepted:refresh"`. Capture native Tile pixels/accessibility, elapsed time and cache header. Tap the observed Tile card; verify actual MainActivity/task and active 999/999.99. Return through the native carousel while cache age is still below the recorded effective window and capture again. The observed API 30 repeat stayed stale at 52.477–53.297 s within 119.996 s, and drew only the first word of its full status. This is an observed instance, not a new refresh SLA.

Source anchors: `WorkoutTileService.onTileRequest`, `WorkoutTileRenderer.render`, `WorkoutTileLayout.build`; follow-up design must connect accepted snapshot publication to actual Tile refresh and protect the round-screen text. No callback trace/root-cause fix is claimed here.

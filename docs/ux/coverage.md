# Coverage — what has actually been run

Anything not listed was **not run**. Never mark UNTESTED items as verified.

## Device matrix (2026-11-01)

| Scenario | Status |
|---|---|
| wear6 emulator 454x454, anonymous, debug build | RUN: install, cold launch, permission dialog, onboarding, Done dead-tap test, screenshots+uiautomator |
| Physical SM_R955U 432x432, signed-in, release build | RUN (read-only): cold start x1 (2716 ms), home scroll map, search-entry, swipe-back, Library, Settings, Track Context; screens+XML in evidence/ |
| Smaller display AVD | UNTESTED — no AVD created |
| Larger display (454 vs watch 432 both exist) | partial |
| Large system font | UNTESTED |
| Rotary input | NOT exercised (code review only) |
| Ambient mode | NOT exercised this session |
| Offline/disconnected states | NOT exercised |
| Interrupted/resumed (process kill, backgrounding) | NOT exercised |
| PlayerActivity + sheets on any device | NOT exercised 2026-11-01 |
| Playback audio (any device) | NOT run 2026-11-01 (read-only watch policy; emulator unsigned-in) |
| Downloads/offline on device | NOT exercised 2026-11-01 |
| Macrobenchmark / Perfetto traces | NOT run (no benchmark module yet) |
| Journeys via Android CLI | NOT run (auth blocks post-onboarding journeys) |
| Unit tests | repo has 35 JVM tests; `testDebugUnitTest` full run NOT executed this session (gradle build succeeded; test task pending) |
| lintDebug | NOT run |
| CI | none exists in repo |

## Emulator state (wear6)

Anonymous (device-code flow pending, code expired). App data persists across
scripted boots (`-no-snapshot-load`, no wipe). Watch app data untouched;
watch `screen_off_timeout` was already 30000 (not modified; backup file unused).

## Evidence inventory (docs/ux/evidence/, 2026-11-01)

Emulator: fresh-launch (permission), onboarding, onboard before/after Done tap.
Watch: home, search entry, swipe-dismiss, home scroll map x5, Library,
Settings + scrolled, Track Context + scrolled (all .png + .xml where noted).

## Permanent gaps to schedule

1. Signed-in emulator (UX-009) → unlocks WF-004..010 journeys, player, offline,
   macrobenchmark on representative data (27 downloads on watch as data-shape
   reference).
2. Benchmark module (Macrobenchmark) — `testing-setup` skill installed for this.
3. androidTest/journey harness — repo has zero instrumented tests.
4. Rotary + ambient + font-scale matrix on wear6.

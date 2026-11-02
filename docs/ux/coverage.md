# Coverage — what has actually been run

Anything not listed was **not run**. Never mark UNTESTED items as verified.

## Device matrix (2026-11-01)

| Scenario | Status |
|---|---|
| wear6 emulator 454x454, anonymous, debug build | RUN: install, cold launch, permission dialog, onboarding, Done dead-tap test, screenshots+uiautomator |
| Physical SM_R955U 432x432, signed-in, release build | RUN (read-only): cold start x1 (2716 ms), home scroll map, search-entry, swipe-back, Library, Settings, Track Context; screens+XML in evidence/. LATE SESSION: search→remote-input-picker→Back→Back chain (UX-013 withdrawn) |
| Physical SM_R955U, beta.7 (versionCode 12) install | RUN: uninstall of differently-signed beta.6 (data wipe user-approved), clean install of 0.6.0-beta.7, launch, notification prompt, EXP-001 waiting state live on watch with fresh device code. POST-PAIR SMOKE: signed-in home; Library liked-tracks row shows full v2 count 264 (EXP-006 on real hardware); track context from liked list — Like chip unlike/relike round-trip with `favorite track write ok favorite=false/true` server writes (EXP-005 on real hardware); track row tap autoplays (media started, paused after). Evidence: 2026-11-01-watch-beta7-{paired-home,library,track-context}.png |
| Physical SM_R955U, beta.8 release build (versionCode 13) | RUN: install -r over debug build (same key, data kept incl. offload suspension); streamed HE-AAC playback 60 s with ZERO offload engagement, ZERO stall events, user heard music; flinger 0 OFFLOAD threads (software decode as designed post-suspension). Output-switcher panel launch + Buds4 Pro connect + A2DP route + speaker fallback all verified pre-release (EXP-010); offload wedge -> single sentinel rebuild at T0+48 s -> "Smooth" (EXP-012) |
| wear6 emulator 454x454, SIGNED-IN, debug builds | RUN (late 2026-11-01): streaming playback (playlist play, explicit track play), playlist download batch (20 tracks over LTE toggle), offline shelf play, recent tracking, Track Context Like/Unlike round-trip vs server (curl truth), Library liked-tracks 47→264 fix (EXP-006), Settings LTE toggle, ActionsSheet pull-up |
| Smaller display AVD | UNTESTED — no AVD created |
| Larger display (454 vs watch 432 both exist) | partial |
| Large system font | UNTESTED |
| Rotary input | NOT exercised (code review only) |
| Ambient mode | NOT exercised this session |
| Offline/disconnected states | NOT exercised |
| Interrupted/resumed (process kill, backgrounding) | NOT exercised |
| PlayerActivity + sheets on any device | RUN (emulator): transport buttons present, guarded pull-up opens ActionsSheet (Download/Queue/quality) |
| Playback audio (any device) | RUN (emulator streaming + offline playback); watch read-only policy respected |
| Downloads/offline on device | RUN (emulator): single-track via ActionsSheet, batch playlist via Download chip, metered-constraint stall UX-016 |
| Macrobenchmark / Perfetto traces | NOT run (no benchmark module yet) |
| Journeys via Android CLI | NOT run (auth blocks post-onboarding journeys) |
| Unit tests | `:core:tidal-api:testDebugUnitTest` green (incl. new pagination case); `:app` targeted tests green earlier (SignOutWording); full-suite run still pending |
| lintDebug | NOT run |
| CI | none exists in repo |

## Emulator state (wear6)

SIGNED IN (device code entered by user, session persisted; 20 downloaded
tracks + recent history + likes intact — favorite test residue verified
cleaned server-side). Auth survives boots (`-no-snapshot-load`, no wipe);
`pm clear` would destroy it — do not clear. Watch app data untouched;
watch `screen_off_timeout` was already 30000 (not modified; backup file unused).
Toolchain note: SDK now at `~/Work/toolchains/android-sdk`, JDK via mise;
`scripts/env-android.sh` probes both.

## Evidence inventory (docs/ux/evidence/, 2026-11-01)

Emulator: fresh-launch (permission), onboarding, onboard before/after Done
tap; late session: signed-in home, settings sign-out wording, player,
context before/after (Like chip), liked count before/after, liked list.
Watch: home, search entry, swipe-dismiss, home scroll map x5, Library,
Settings + scrolled, Track Context + scrolled, ux013 picker state
(all .png + .xml where noted).

## Permanent gaps to schedule

1. ~~Signed-in emulator~~ DONE 2026-11-01 (keep it: do not `pm clear`).
   Next: macrobenchmark on this signed-in emulator (20 downloads + 264 likes
   as representative data shape).
2. Benchmark module (Macrobenchmark) — `testing-setup` skill installed for this.
3. androidTest/journey harness — repo has zero instrumented tests.
4. Rotary + ambient + font-scale matrix on wear6.

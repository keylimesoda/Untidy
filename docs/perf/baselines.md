# Baselines (measured distributions, never single best runs)

## From the UX program (provisional — n too small, carried over as brackets)

| Metric | Device | n | Values | Conditions | Date |
|---|---|---|---|---|---|
| Cold start `TotalTime` | SM_R955U signed-in | 2 | 2716 ms; (warm resume 0 — NOT a relaunch) | force-stop then start, screen awake, beta.7 debug | 2026-11-01 |
| Cold start `TotalTime` | wear6 emu anonymous | 1 | 688 ms | first install, debug | 2026-11-01 |
| loadQueue→prepared | watch, streamed HE-AAC | 1 | 15 ms (prepared), play start ~2 s tap→audio | beta.8 debug, LTE | 2026-11-01 |
| Stall→rebuild dropout | watch | 1 clean + storm series | ~1.5-2 s audible dropout, single event | offload wedge, sentinel rebuild | 2026-11-01 |

## First-run TODOs (next perf session, in priority order)

1. Battery scenario cards on SM_R955U beta.8 RELEASE (idle floor 30-60 min →
   6 h overnight with charging test; playback 10 min ×{offload-forced,
   software} ×{speaker, A2DP}, audibility cross-checked; download burst).
2. Cold start ×5 watch beta.8 release (am force-stop + start -W + Displayed).
3. gfxinfo framestats standard scroll (home, library-liked-264) — first-fling
   vs steady histogram.
4. Wake audit: batterystats per-uid after overnight idle (alarms/jobs/
   wakelocks/radio) — idle floor P0 check.
5. simpleperf during browse session — top main-thread slices.

## Conditions log format

Every baseline row: build variant, versionCode, battery level at start,
temperature, network path, route, screen state sequence. Missing condition =
row is provisional.

## 2026-11-02 Library fallback (watch, release)

Tap to `library fallback ok` log marker, n=3 each, charging with playback and AirPods Max: before 8.6 / 7.3 / 6.1 s (median 7.3, worst 8.6); bounded-parallel candidate 6.1 / 5.0 / 5.0 s (median 5.0, worst 6.1). The app was reinstalled and the same track restarted between sets; battery/temperature reported 96%/42.3 C and 92%/41.5 C after respective sets. This marker is not UI-frame timing; see EXP-018 for limits.

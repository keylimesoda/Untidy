# Scorecard — baselines and metrics

All numbers below are **as measured, as few-shot unless stated**. Wear cold
starts vary with thermal/cache state; treat single samples as baselines to be
bracketed, not budgets. No performance budget is a "requirement" until
established from ≥3-run distributions (rubric: `skill://wear-ux-optimizer/references/evaluation-rubric.md`).

## Latency baselines

| Metric | Device | Runs | Values | Notes |
|---|---|---|---|---|
| Cold start `am start -W TotalTime` | watch SM_R955U (432x432, signed-in) | 2 | 2716 ms, (2nd warm: 0 resume) | force-stop then start, screen awake; 2026-11-01 |
| Cold start `TotalTime` | wear6 emu (454x454, anonymous) | 1 | 688 ms | first install, permission dialog then onboarding |
| Warm resume `TotalTime` | watch | 1 | 0 ms (resume) | not a relaunch measurement |

TODO next session: 3× cold (watch, emulator), tap→screen-appeared latency via
`uiautomator events` or macrobenchmark, scroll-to-jank via FrameTimingMetric.

## Interaction-count baselines (from workflows.md)

| Goal | Taps | Notes |
|---|---|---|
| Sign in first time | 0 required (off-watch auth) | +1 misleading dead tap possible (UX-001) |
| Search to playback | 1 + input + result tap | system input not counted |
| Recent track replay | 2 | context-before-play is accepted design |
| Library browse to playback | 3–4 | +scroll to reach Library |
| Settings change | 2 | +scroll |
| Favorite from context | currently N/A — requires starting playback | UX-004 |
| Add to playlist (player) | pull-up + 2 taps | |
| Remove download | 2 + confirm | |

## UI geometry baselines (uiautomator, px; density 2.0 on 454x454, watch 432x432)

| Element | Bounds | Verdict |
|---|---|---|
| Home Search row | 390x119 | OK |
| Home shelf/Explore rows | 390x122 | OK |
| Onboarding Done clickable surface | 358x72 (=36dp tall) | below 48dp min (UX-001) |
| Settings account chip | 398x111 clickable, inert | UX-002 |
| Erase-account chip | 370x103 | OK size; wording UX-003 |
| Track Context action chips | ~380x102 | OK |
| Library Artists row at rest | clipped to 18px | UX-011 |

## Perf baselines

None yet. UXP-001/002 call paths identified; Macrobenchmark/Perfetto
baselines are the first calibration task for that lens (UXP-004 trace markers
would help). Prior power data: `docs/reviews/hardware-power-2026-09-07.md`
(FLAC vs HE-AAC offload; explicitly not battery-life claims).

## Accessibility

No TalkBack walkthrough yet. Icon-only control semantics audit pending
(UXW-004 text sizes; UXC-010 notification-denial path).

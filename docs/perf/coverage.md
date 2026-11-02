# Perf coverage matrix (device x build x scenario actually measured)

| Device | Build | Scenarios RUN | Scenarios UNTESTED |
|---|---|---|---|
| SM_R955U (physical) | beta.7 debug | cold start ×2 (UX scorecard), read-only perf probes (flinger, media_session) | all battery cards, framestats, doze/overnight |
| SM_R955U (physical) | beta.8 release | playback 60 s streamed (zero watchdog events, 0 OFFLOAD threads); batterystats/gfxinfo/deviceidle/perfetto/simpleperf interface probes only | every quantified card (idle floor, playback drain, downloads, jank, cold n≥3) |
| wear6 emulator | debug | cold start ×1 (anonymous) | nothing perf-conclusive (screening only; no battery/radio/DSP) |
| smaller display AVD | — | UNTESTED, none created | — |

Device-side tool presence (verified 2026-11-01, SM_R955U Android 16):
`/system/bin/perfetto` ✓, `/system/bin/simpleperf` ✓, `dumpsys batterystats`
✓ (reset+per-uid), `cmd deviceidle` ✓, `dumpsys gfxinfo` ✓ (release app).
Host: no NDK → simpleperf stays on-device; trace_processor = fetcher stub,
run android-profiler `env_setup.md` before SQL analysis.

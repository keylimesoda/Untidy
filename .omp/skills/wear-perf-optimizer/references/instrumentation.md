# Instrumentation inventory (validated on SM_R955U / this host, 2026-11-01)

## Host

```bash
source ~/Work/Untidy/scripts/env-android.sh     # JAVA_HOME (temurin-21), ANDROID_HOME
adb devices -l | grep -i R955                    # watch serial = mDNS addr, CHANGES per network
adb connect <IP:PORT>                            # re-establish (pairing persists across reconnects)
```

- SDK: `~/Work/toolchains/android-sdk` — **no NDK installed** (simpleperf host
  tools absent; device binary exists, see below).
- Android CLI `~/.local/bin/android` (`--sdk=...` in `~/.androidrc`):
  `android profiler systemtrace|heapdump|session` — verified subcommands.
  `heapdump` requires API 29+ (watch = 36 ✓).
- Profiler skill: `.agents/skills/android-profiler/` — route all trace
  RECORDING through `recording/recording_orchestrator.md` and ANALYSIS
  through `analysis/analysis_orchestrator.md` (triage + per-candidate hints
  incl. `hints_power.md`, `hints_graphics.md`). Perfetto configs live in
  `recording/workflows/perfetto-trace-recording/example-configs/`
  (`app_jank.pftxt`, `sched_cpu.pftxt`, `long_background.pftxt`,
  `memory_counters.pftxt`, `native_heap.pftxt`).
- `references/perfetto/bin/trace_processor` is a 11 KB FETCHER stub, not a
  binary — run `references/env_setup.md` before any SQL analysis.

## On-device (verified present on SM_R955U, Android 16)

| Interface | Verified | Notes |
|---|---|---|
| `/system/bin/perfetto` | ✓ | system traces; prefer driving via `android profiler systemtrace` or the skill's config push flow |
| `/system/bin/simpleperf` | ✓ | sampling CPU profiler ON-device: `simpleperf record -p <pid> --duration 10 -o /data/local/tmp/perf.data` then `simpleperf report -i ...` in-shell; no NDK needed for attribution, though host symbolization needs matching unstripped symbols |
| `dumpsys batterystats --reset` / `--charged` / per-uid | ✓ | real stats while on battery; `--reset` is authorized |
| `cmd deviceidle` | ✓ | force doze for idle-floor tests |
| `dumpsys gfxinfo com.tidal.wear` / `framestats` | ✓ | works on the non-debuggable release build |
| `dumpsys media_session`, `media.audio_flinger`, `audio` | ✓ | audio ground truth (see UX skill automation-notes) |
| `am start -W` | ✓ | `TotalTime` = cold start when preceded by force-stop |
| `logcat -T` ActivityTaskManager `Displayed` | ✓ | TTID alternative to TotalTime |

## Scenario harness patterns (from the UX program, reuse them)

- Batch a whole scenario into ONE adb script; the watch sleeps fast — wake
  (`input keyevent 224`) at script start, parse output AFTER the run.
- Locate targets by fresh `uiautomator dump` (bounds; only `clickable=true`
  nodes are targets), never stale coordinates.
- Media state truth: `dumpsys media_session` PlaybackState position vs wall
  clock; audibility truth: flinger per-thread `Total writes` / `Last write
  occurred` growth while playing.
- Home scroll position persists; re-dump before each tap.

## Known device facts (SM_R955U)

- 432x432 (report), Wear OS 6 / Android 16, `wise6us`, OneUI.
- HE-AAC compressed offload WEDGES ~10-14 s in (EXP-012); offload codecs
  advertised: MP3 / AAC-LC / HE-AAC. FLAC offload absent.
- Speaker + A2DP routes both exist; route switch via
  `com.samsung.android.wearable.media.sessions/.OutputSwitcherJetpackInvocation`.
- Sessions expire ~10 min on emulator; watch stays signed-in — treat as the
  measurement instrument, READ-ONLY except authorized perf scenarios.

## Emulator

`UNTIDY_AVD_NAME=wear6 bash scripts/ensure-wear-emulator.sh` — boots headless,
keeps auth across boots (`-no-snapshot-load`); install debug builds freely.
x86_64 + KVM: fine for gfxinfo-relative A/B and trace shape; USELESS for
absolute latency/battery (different SoC, no radio/DSP/battery).

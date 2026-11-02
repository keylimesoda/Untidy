# Responsiveness playbook (Wear OS, Untidy)

## The four measured quantities

1. **Cold start**: `am force-stop com.tidal.wear` → `am start -W -n
   com.tidal.wear/.MainActivity` → `TotalTime`; cross-check
   `logcat -d -s ActivityTaskManager | grep Displayed`. Seed: watch 2716 ms
   (n=2, brackets only), emulator 688 ms (n=1, screening only).
   Cold≠warm: force-stop first, screen awake, same start point every trial.
   Watch for `smart idle`/cache effects: trial 2 may be warm (TotalTime≈0 is
   a RESUME, not a relaunch — restart properly).
2. **Navigation latency**: tap → destination `uiautomator`-visible (poll dump
   every ~100 ms in ONE batched script, first-seen timestamp), or Perfetto
   frame timeline. Cover: home→library, home→search results, row→context,
   context→play (playback start = first advancing media_session position,
   tap→that).
3. **Input-to-feedback**: tap → first frame change of the touched control.
   `screenrecord --time-limit 5` at native fps during the tap, then frame
   diff; or `dumpsys input` dispatch→handle times. The watch forgives slow
   screens, not dead taps — an acknowledgment <100 ms covers server latency
   (UX pattern: optimistic Like chip).
4. **Frame pacing**: `dumpsys gfxinfo com.tidal.wear framestats` before/after
   a standard scroll (fixed px drag, 800 ms, from automation-notes); report
   Janky% AND the histogram tail (>32 ms, >100 ms counts). Perfetto
   `app_jank.pftxt` via android-profiler skill when attribution needed
   (which composable/section janks).

## Attribution tools, in order of weight

- gfxinfo framestats (cheap, release-build OK) → is there jank, how much.
- Perfetto system trace (android-profiler skill: recording_orchestrator,
  then analysis triage + `hints_graphics.md`/`hints_cpu.md`) → who owns it:
  main-thread slice, RenderThread, GC, binder, IO.
- On-device `simpleperf record -p <pid> --duration <s>` → native/ART CPU
  flame (verified /system/bin/simpleperf on SM_R955U; `simpleperf report`
  in-shell; host symbolization needs unstripped symbols from the matching
  build — for release, use debug-symbol variant or interpret at module level).
- `android profiler heapdump` → allocation pressure/GC storm confirmation
  (API 29+ ✓).
- Macrobenchmark (FrameTimingMetric/StartupTimingMetric): only scaffold a
  `macrobenchmark` module when a claim needs CI-repeatable numbers beyond
  one-off gfxinfo; test-only, never ship-facing.

## Known Untidy critical-path facts (2026-11-01)

- Stall-rebuild + SharedPreferences `apply` + ExoPlayer release/prepare ran on
  the playback main looper → ~1.5-2 s dropout (EXP-012 storm). Main-thread
  discipline during PLAYBACK is a responsiveness budget, not just battery.
- Compose Wear: lazy lists with artwork decode on bind = first-fling jank;
  check coil/image memory cache config during library scroll (framestats
  first-burst vs steady-state differ).
- Search/type latency crosses a system IME boundary (keyboard cold start is
  system cost; measure app-side response separately from IME availability —
  UX-013 withdrawal lesson: phantom system-window behavior on emulator).
- Watch sleep mid-scenario fakes "slowness": wake first, batch everything.

## Optimization order (same philosophy as UX track)

remove work → prefetch/cache → move off critical path → animate/loading
affordance LAST (never mask latency with spinner when removal is possible;
record which strategy a fix used — removal vs masking).

## Budget format

Per interaction: `interaction | build | date | median | p95 | budget |
cross-check`. p95/worst is the budget, never the median — tails are what users
feel. First three sessions = establishing distributions (n≥3 per interaction),
budgets freeze after the distribution is stable.

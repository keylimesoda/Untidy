# Battery playbook (Wear OS, Untidy)

## Drain model — measure all four terms, cut the biggest LEGITIMATE one

```
total = idle_floor (app resident, doze)      ← should approach 0
      + ambient/AOD cost (screen-off ambient draws, if any)
      + active_session (per scenario, screen-on)   ← music playback lives here
      + background_wakes (jobs/alarms/sync/wakelocks/radio/sensors)
```

A music app's dominant controllable terms are `active_session` (audio path,
network fetch, screen-on time) and `background_wakes`. The `idle_floor` must be
verified ≈0 — an app that never lets the device doze is a P0 battery defect even
if every active scenario is optimal.

## Scenario cards (define once, reuse)

1. **Idle floor**: `batterystats --reset`, app installed+signed-in, screen off,
   NO interaction, device goes to doze (`cmd deviceidle step idle|idle_maintenance`
   or wait), measure 30-60 min, `batterystats --charged`; report per-uid drain
   (mAh + %) and wake counts (wakelocks/alarms/jobs/syncs/wifi scans/radio
   wakeups). Cross-check doze actually entered: `dumpsys deviceidle` state.
2. **Playback active**: fixed track (same bitrate/codec), fixed volume, screen
   off after start (watch playback is pocket-scenario), route fixed (speaker vs
   A2DP separately — they are DIFFERENT scenarios), duration ≥10 min, measure
   batterystats delta AND SoC% delta. **Cross-check audibility** (flinger
   writes growing) before crediting ANY drain number — EXP-012: a wedged
   offload pipeline measures super-efficient because it stopped working.
3. **Download burst**: N-track download over LTE/WiFi, screen off; attribute
   radio on-time + CPU; check job yielding/retry behavior (retry storm =
   UX-020-shaped correctness bug AND battery bug).
4. **Browse session**: home→library→search→context x3 (the standard UX
   journey), screen-on; drain + CPU via simpleperf during it.

## Attribution recipes (all verified present on SM_R955U)

- Whole-device + per-uid: `dumpsys batterystats --reset` → scenario →
  `dumpsys batterystats --charged`; per-uid section: `dumpsys batterystats
  com.tidal.wear` — wakelock names+times, alarms, jobs, syncs, GPS, wifi
  scans, "Mobile radio active" time, CPU time in state buckets.
- Kernel wakelock suspects: `dumpsys wakeupreason`, `getprop` +
  `dumpsys power` (why the device refuses to suspend).
- Doze behavior: `cmd deviceidle force-idle` to test doze-restricted paths;
  undo with `unforce-idle`; `reset` afterwards.
- Battery override for repeatable start level: `dumpsys battery unplug`
  (+ `set level`), ALWAYS `dumpsys battery reset` when done — an override left
  set is a device defect, not a test artifact.
- Wake audit without full drain: `simpleperf` for CPU hotspots, plus perfetto
  sched + power hints via the android-profiler skill `hints_power.md`.
- Long background capture: android-profiler `long_background.pftxt` config.

## Untidy-specific battery knowledge (2026-11-01)

- **Audio offload** = real DSP saving, BUT device-buggy for HE-AAC on this
  watch: default ENABLED + stall-sentinel auto-suspension per device
  (EXP-012). Battery credit for offload claims MUST verify audio is audible.
  Open: per-stream-class probing (UX-024) — AAC-LC offload untested.
- **Polling intervals** (recent-history sync, auth refresh) live in
  background_wakes; a dead-session retry loop is a documented battery pattern
  (UX-020 class) — verify backoff exists for every retry path.
- **Watchdog/sentinel timers** cost battery: keep them dormant while idle
  (stall sentinel only ticks during isPlaying) and audit that every
  Handler/JobScheduler/AlarmManager registered by playback is released with
  the player — a leaked timer is both a storm risk (EXP-012 tick-leak: 2 s
  rebuild storm) and a drain.
- **Downloads** run under job constraints; emulator shows CELLULAR metered —
  keep job retry/backoff symmetric on real device paths too.
- Media session, notification listener callbacks: verify no periodic work
  fires when no media session is active.

## Budget format

One row per scenario card: `scenario | build | date | median | worst |
budget | note`. Budget starts at baseline-worst (no self-serving slack) and
ratchets down as improvements land; loosening needs a decisions.md entry.

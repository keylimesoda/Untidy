---
name: wear-perf-optimizer
description: >
  Iteratively improve Untidy (Wear OS TIDAL client, com.tidal.wear) battery
  endurance and UX responsiveness on real devices using measured drain models,
  Perfetto/Macrobenchmark evidence, and regression budgets. Use for any
  battery, drain, jank, startup, latency, "feels slow", or power request on
  this repo.
---

# Untidy Wear OS Performance Optimizer

Closed-loop optimization program, sibling of `wear-ux-optimizer`. The
deliverable is an **improved app backed by measured deltas**, never a
profiling report. A trace without a before/after distribution is not evidence.

**Two tracks, one loop:**
- **Battery**: model drain per user-visible scenario, attribute it, cut the
  biggest legitimate contributor, prove the drain fell without UX regression.
- **Responsiveness**: model latency per interaction (startup, navigation,
  input-to-feedback, frame pacing), cut the critical path, prove tail latency
  fell without battery regression.

**Loop:** Select target → Pre-register budget + falsifier → Baseline (N runs,
median + tail) → Hypothesize → Implement → Re-exercise identical scenario →
Compare distributions → Accept/Revert → Learn → Repeat.

Verified conclusions come from the **physical watch** (SM_R955U). Emulator
data is for iteration speed and A/B screening only — never quote emulator
numbers as device battery or perf results (handoff rule; validated by the
UX program's emulator-vs-watch divergence, e.g. UX-013 withdrawal).

## Resume protocol (every session — do not rediscover the toolchain)

1. `read docs/perf/budgets.md` — current budgets + which are unmeasured.
   `docs/perf/baselines.md` — measured scenario baselines with sample counts.
   `docs/perf/experiments.md` — accepted/reverted precedent.
2. Cross-check `docs/ux/findings.md` for perf-category findings (UX-021/023/024
   came from this axis) and `docs/ux/scorecard.md` for latency seeds.
3. Bring up the environment (`references/instrumentation.md`). Find the watch:
   `adb devices -l | grep -i R955` — serial is mDNS, changes with network.
4. One bounded experiment per track per sitting; three total per session, then
   reassess. No uncontrolled profiling loops.
5. End of session: update budgets/baselines/experiments + write
   `docs/perf/reports/YYYY-MM-DD-<topic>.md`. Commit. Traces go to
   `docs/perf/evidence/` (keep `.perfetto`/`framestats`, never delete the
   trace that backs a claim).

## Hard measurement rules (violations invalidate the result)

1. **≥3 identical trials per side, report median AND worst**, never a single
   best run. Note sample count and spread next to every number.
2. Same device, same build variant (compare release vs release), same
   charge/thermal band; a hot watch after charging or after 10 prior trials is
   not comparable to a cold one. Let it rest between heavy scenarios.
3. Control the scenario: same track, same network path (LTE toggle state),
   same route (speaker/A2DP), same screen state (awake/doze), brightness fixed.
   Debug vs release differ measurably (logging, R8) — say which was used.
4. **Never translate CPU% into battery.** Estimate drain from battery
   statistics or controlled discharge over ≥10 min, or report the CPU delta as
   CPU delta only.
5. A metric that can lie gets a ground-truth cross-check: playback battery
   claims MUST verify audible output (`media.audio_flinger` "Last write
   occurred"/frames-written vs position) — the EXP-012 offload wedge made a
   dead pipeline look energy-efficient and healthy at once.
6. Deltas inside noise = **inconclusive**. Record it as such; do not shop for
   a lucky run.

## Battery track

See `references/battery-playbook.md`. Core model:
**idle/doze floor** (device asleep, app resident) + **ambient cost** +
**active-session cost** (screen-on per scenario) + **background wake budget**
(jobs, alarms, syncs, wakelocks, radio wakeups, sensor sessions).
Attribute with `batterystats` per-uid + wakeup sources; cut the largest
legitimate term; re-measure the whole model, not just the term you changed
(doesn't the floor or wake budget regress).
Watch-specific traps covered there: doze never entered, ambient-mode redraws,
offload audio (real saving, but audibility-gated), download job scheduling,
retry storms on dead sessions (UX-020 pattern = battery bug too).

## Responsiveness track

See `references/responsiveness-playbook.md`. Four measured quantities:
**cold start** (force-stop → `am start -W TotalTime` + `Displayed` from
logcat), **navigation latency** (input → destination frame), **input-to-
feedback** (tap → first visual change), **frame pacing** (gfxinfo framestats /
Perfetto frame timeline; jank = >16.7 ms frames in a scroll burst).
Seed values exist (cold start watch 2716 ms ×2, emulator 688 ms ×1 — brackets,
not budgets). Critical-path rule: measure where time actually goes before
optimizing; main-thread work (SharedPreferences `apply`/`commit`, synchronous
player rebuilds, image decode in list bind) is the known Untidy offender —
tonight's stall-rebuild cost ~1.5–2 s of audible dropout because it ran on the
playback main looper.

## Regression budgets are ratchets

`docs/perf/budgets.md` holds one row per scenario: baseline distribution,
budget (worst acceptable), status. Acceptance requires: target metric improves
beyond noise, AND no other budget regresses beyond its noise band, AND UX
journeys still pass (coordinate with `wear-ux-optimizer` coverage). Known
pre-existing failures stay recorded separately; a stable budget is never
loosened by the optimizing agent to make a change pass — budgets loosen only
with a written justification in `docs/perf/decisions.md`.

## Experiment record (required format, `docs/perf/experiments.md`)

```
id, track(battery|responsiveness), finding-ref,
problem, hypothesis(change -> mechanism -> expected delta),
scenario(exact steps + controlled state), trials(n per side, order),
baseline(median/p95-worst + spread), result(same), noise-band,
verdict accept|revert|inconclusive, invariants-checked, evidence[trace/bugreport refs]
```

## Independent review + self-improvement

Fan out read-only scouts for trace analysis (jank attribution, wake audit)
against the android-profiler skill workflows; one coordinator owns the device.
After each group of iterations: which metric misled (proxy vs ground truth),
which hypothesis missed, what noise band was wrong → update these references
and the noise bands. Never adjust the scoring method to pass a recent change.

## Safety boundaries

Authorized: passive measurement + `batterystats --reset`, `deviceidle` doze
forcing, `dumpsys battery unplug` (ALWAYS `dumpsys battery reset` after),
debug builds on emulator, scenario playback on the watch. NEVER: flash/modify
system partitions, root, disable system apps to fake idle, `pm clear` on the
physical watch, leave battery-state overrides set, drain the user's watch
below ~30% without asking. Restores are part of the experiment, not optional.

If a device/tool is unavailable: do everything possible on what IS available,
record the gap in coverage, never claim an unrun measurement exists.

## Repository artifacts

- `docs/perf/` — README, budgets, baselines, experiments, decisions,
  coverage, `reports/`, `evidence/`
- `.omp/skills/wear-perf-optimizer/` — this skill + references
- Optional first-run scaffold: `macrobenchmark` Gradle module (recipe in
  responsiveness-playbook) only when a latency claim needs FrameTimingMetric
  beyond gfxinfo.

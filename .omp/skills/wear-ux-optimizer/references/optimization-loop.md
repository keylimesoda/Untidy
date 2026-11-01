# Optimization loop — detailed protocol

## Step 1 — Select a candidate
From `docs/ux/findings.md`, pick one high-value open finding.
Heuristic ranking: `priority ≈ impact × frequency × confidence / (effort × risk)`.
Planning heuristic only; unknown frequency/confidence stay explicit.
Prefer obvious friction reduction or correctness before cosmetics.
Each experiment must be independently revertable (own commit).

## Step 2 — Define the hypothesis (before editing)
Record in `docs/ux/experiments.md`:
- exact user problem + currently observed behavior (with evidence refs)
- proposed change + why it should help
- metrics expected to improve (interaction counts, latency, etc.)
- behaviors that must NOT change (invariants)
- what would falsify the hypothesis

Template:

```
## EXP-NNN — <title>
finding: UX-NNN | branch/commit:
problem:
hypothesis:
expected improvement:
regression risk:
invariants:
falsifier:
```

## Step 3 — Implement
Smallest coherent change. Prefer: removing navigation layers, reusing existing
state/nav infrastructure, standard Wear Compose M3 components (see
`.agents/skills/wear-compose-m3`), reducing recomposition/work, clearer
immediate feedback. Avoid: big rewrites, dependency upgrades, new design
systems, excessive animation, speculative abstractions, mixing workflows.

## Step 4 — Build & exercise (device is mandatory)
1. `bash ./gradlew :app:assembleDebug`
2. `adb -s <emulator> install -r app/build/outputs/apk/debug/app-debug.apk`
3. Launch: `adb -s <serial> shell am start -W -n com.tidal.wear/.MainActivity`
   (note the `TotalTime` from `-W` for startup deltas)
4. Replay the affected journey via `android input` / `adb shell input` /
   uiautomator; count interactions deterministically from `uiautomator dump`.
5. Before/after: `android screen capture -d <serial> -o docs/ux/evidence/...`
   + `uiautomator dump` XML for bounds/target-size/semantics comparison.
6. Verify task completion, cancel/back paths, recovery (rotation? app
   background? swipe-dismiss?).
7. Run related unit tests; at minimum `./gradlew test` for touched modules.

## Step 5 — Measure
Baseline vs candidate: interaction counts (taps/swipes/backtracks/screens),
completion success, TotalTime startup, latency deltas, jank (profiler traces),
semantic/visual deltas, accessibility effects. Never fabricate values when the
environment can't supply them — mark N/A with the reason.

Performance protocol (from android-profiler skill / `android profiler`):
same device, same build variant, controlled app state, repeated trials
(≥3), report median + variability + sample count, keep traces under
docs/ux/evidence/. Emulator numbers are not physical-watch conclusions.
Never report CPU as battery.

## Step 6 — Decide
- **accept**: correctness preserved + intended benefit verified + no significant regression + evidence sufficient
- **accept-provisionally**: objective checks pass; benefit needs human/physical-watch validation (record uncertainty)
- **revise**: promising, fixable issue remains
- **revert**: regression, worse workflow, required behavior lost, perf cost outweighs benefit
- **inconclusive**: noisy/insufficient evidence or invalid setup

Record all outcomes — reverted experiments are the most valuable anti-
recurrence data. Update finding status, `docs/ux/decisions.md`, work-items.

## Step 7 — Record & continue
Append EXP entry: measurements table, evidence paths, verdict, remaining
questions, lesson. Re-prioritize backlog. Next cycle.

## Journey definition (reproducible)
Journeys live in `docs/ux/workflows.md` + runnable form in
`app/src/androidTest` (uiautomator) and/or `android journeys run` YAML.
Each: entry point, precondition/fixture, ordered steps with expected UI
assertion, success condition, interaction counts captured.

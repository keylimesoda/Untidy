# Experiment cycle (performance)

## 0. Select
Highest expected user-visible drain/latency with an existing baseline (or the
baseline itself is the experiment). Prefer scenarios already covered by UX
journeys — the same scenario card serves both tracks.

## 1. Pre-register (before ANY edit)
```
target metric + scenario card (verbatim steps + controlled state)
hypothesis: change -> mechanism -> expected delta (magnitude vs noise)
falsifier: what result would DISPROVE it
invariants: UX journeys that must still pass; other budgets watched
trials planned (N per side), rest periods, order
```

## 2. Baseline
Run the card N≥3 times on the CURRENT build. Record median/worst/spread,
battery level + temperature at boundaries, retained artifacts (trace ids,
framestats dumps, batterystats sections) in `docs/perf/evidence/`.

## 3. Implement
Smallest coherent change. No dependency upgrades, rewrites, or dual-metric
experiments in one change. Add measurement hooks only behind debug flags and
remove them before acceptance (a measurement-only change still needs a verdict:
inconclusive is fine — but never ship instrumentation overhead).

## 4. Re-exercise
Identical card, identical conditions, N≥3 on the NEW build. Same time of day
band where thermal matters. Cross-check ground truth (audio audible, doze
entered, frames rendered).

## 5. Compare + verdict
- Δ > spread in target direction AND no watched budget exceeds its noise band
  → **accept** (new number becomes candidate baseline; budget ratchets).
- Δ inside noise → **inconclusive** (record; optionally one cheap follow-up
  with more trials, never open-ended).
- Δ wrong-direction or invariant broken → **revert** (git revert, record what
  the model got wrong — this is where measurement heuristics improve).
- Mixed (target improves, side budget regresses) → tradeoff entry in
  `docs/perf/decisions.md`, decide explicitly, never implicitly.

## 6. Learn
Update: noise bands (per scenario, measured), attribution heuristics,
scenario cards that were flaky, instrumentation gaps. The evaluation system
improves; the success criteria do NOT move to accommodate results.

## Session cadence
≤3 experiments/session; commit after each (accept or revert both commit).
End-of-session report: `docs/perf/reports/YYYY-MM-DD-<topic>.md` with the
table of deltas, verdicts, and next candidates. Update coverage.md with what
scenarios/builds were actually measured (never leave a budget's provenance
implicit).

# docs/perf — performance program artifacts

Operated by `.omp/skills/wear-perf-optimizer`. Same discipline as `docs/ux/`:
every accepted claim names its scenario card, trial count, distribution, and
retained evidence.

- `budgets.md` — one ratcheting row per scenario/interaction. Never loosened
  to make a change pass; loosening requires a decisions.md entry.
- `baselines.md` — measured distributions with build/date/conditions.
- `experiments.md` — the experiment ledger (pre-registrations + verdicts).
- `decisions.md` — tradeoffs and budget changes with rationale.
- `coverage.md` — which scenario cards run on which device+build.
- `reports/` — end-of-session summaries.
- `evidence/` — traces, bugreports, batterystats extracts, framestats dumps;
  filename prefix = experiment id + date. Large `.perfetto` files: keep local
  or Git-LFS per repo policy; the extracted tables/summaries in the experiment
  record are what must survive in git.

Seeded from the UX program: EXP-011/012 (audio offload vs audibility vs
battery) already established cross-check rules used here; UX scorecard latency
rows (watch cold start 2716 ms) are provisional brackets until n≥3.

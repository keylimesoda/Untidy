# Perf decisions log

| Date | Decision | Rationale |
|---|---|---|
| 2026-11-01 | Offload default ON + per-device sentinel suspension (imported from UX EXP-012) | Battery-optimal path by default; device bugs detected on-device via audibility ground truth, not assumed globally |
| 2026-11-01 | Battery claims about audio paths require audibility cross-check | EXP-012: wedged offload measured efficient AND healthy on proxies while producing silence |
| 2026-11-01 | Budgets gate on worst/p95, not median; no budget until n≥3 | Tails are what users feel; single samples were unreliable (cold start variance) |

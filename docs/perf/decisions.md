# Perf decisions log

| Date | Decision | Rationale |
|---|---|---|
| 2026-11-01 | Offload default ON + per-device sentinel suspension (imported from UX EXP-012) | Battery-optimal path by default; device bugs detected on-device via audibility ground truth, not assumed globally |
| 2026-11-01 | Battery claims about audio paths require audibility cross-check | EXP-012: wedged offload measured efficient AND healthy on proxies while producing silence |
| 2026-11-01 | Budgets gate on worst/p95, not median; no budget until n≥3 | Tails are what users feel; single samples were unreliable (cold start variance) |
| 2026-11-01 | Idle-floor measurement windows MUST run adb-disconnected; battery counters persist on-device across reconnect | PERF-001 Card 1: adbd WiFi keepalive attributed 31.0 of 27.6 computed mAh — the harness dominated the signal; app drain was under cutoff and undetectable under harness load |
| 2026-11-11 | Battery sweep fixes H1-H4+L3 applied statically (no before/after battery trial yet); favorites cache TTL 30s→5min with write-through invalidation | Sweep report `reports/untidy-battery-sweep.md` @29127d4; H3's 30 s TTL re-ran full relationship pagination every track change — invalidation-on-write covers in-process likes, cross-device likes lag ≤5 min (acceptable); compile+unit tests green, drain delta deferred to next adb-disconnected idle window |
| 2026-11-11 | Sweep M1-M5+L1/L2/L4/L5 applied via delegated worktrees (opencode mid-free slot), verified by parent compile+unit-test before cherry-pick | Routed through coding-router (route.py plan -> mid-free); 6 workers, one fix-set per branch, report attached as context, workers forbidden from building/pushing; parent compiled :app + :core:playback and ran :core:tidal-api tests on main post-cherry-pick — green; worker self-reported "build unverified" so parent verification was mandatory, not optional |

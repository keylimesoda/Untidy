# Performance budgets (ratcheting; worst/p95 is the gate)

| Track | Scenario card | Build | Date | Median | Worst | Budget | Status |
|---|---|---|---|---|---|---|---|
| resp | cold start watch (am start -W) | beta.7 debug | 2026-11-01 | 2716 ms (n=2) | 2716 | TBD — need n≥3 on beta.8 release | provisional bracket |
| resp | cold start emulator | beta.7 debug | 2026-11-01 | 688 ms (n=1) | — | screening only, no budget | screening |
| resp | tap→playback start (watch, streamed) | beta.8 debug | 2026-11-01 | ~2 s observed once (loadQueue prepared 15 ms + prepare/play) | — | TBD n≥3 | unmeasured |
| resp | playback dropout budget (stall rebuild) | beta.8 debug | 2026-11-01 | 1.5-2 s single rebuild | 1 event only | ≤1 dropout per playback session; zero while suspended | set (EXP-012) |
| battery | idle floor (doze, signed-in) | — | — | — | — | TBD first run | unmeasured |
| battery | playback 10 min speaker (offload) | beta.8 | — | — | — | TBD first run | unmeasured |
| battery | playback 10 min speaker (software) | beta.8 watch | — | — | — | TBD; offload-vs-software delta = headline number | unmeasured |
| battery | download burst | — | — | — | — | TBD | unmeasured |
| battery | background wakes per hour (idle) | — | — | — | — | 0 wakelocks held >1 min; 0 alarms/h when no session | policy set, unmeasured |

Rules: budget = baseline worst at first freeze (no slack); ratchet down on
accepted wins; loosening requires decisions.md. Any battery claim about audio
paths requires the audibility cross-check (EXP-012 rule).

# Marching orders — post-battery-sweep program (2026-11-11)

Standing plan after the completed battery sweep (all 14 items actioned,
H1–H4/M1–M5/L1–L5 on `main`). Ordered by what unblocks shipping vs. what
proves the perf work. Every coding item is delegated per the coding-router
policy (route.py plan → tier); parent verifies compile + tests before merge.

## 1. Prove the fixes (quantitative)
- **Idle-floor drain trial on physical GW6** — adb-disconnected windows,
  battery counters persist across reconnect. Before/after vs pre-sweep
  baseline commit. *Parent task (needs the watch physically); not delegable.*
- **UNTIDY-044 Macrobenchmark scaffold** (todo, P1) — cold-start x3
  distributions, tap→screen latency, FrameTimingMetric scroll jank. Emulator
  smoke in CI; watch runs recorded in `docs/ux/scorecard.md` (fills its TODO).

## 2. Gates we are missing (static analysis)
- **UNTIDY-043 detekt + spotless** (todo, P1) — coroutine rulesets would have
  caught the sweep findings mechanically. Ratchet-only baseline so CI fails
  on *new* violations.
- **UNTIDY-045 Roborazzi screenshot tests** (todo, P1) — round + square
  safe-area goldens for Home/Player/Settings/Album. Safe-area regressions are
  invisible to Lint; this is the Wear-specific gap.
- Android Lint already runs clean (0 errors; 18 warnings, 15 of them
  GradleDependency nags). Keep `:app:lintDebug` in the verification set.

## 3. UX edges still open (from wear-ux-optimizer findings)
- **UXC-007** — swipe-dismiss vs hardware-Back divergence on the
  player→detail handoff: still untested. Verify on the watch during the same
  session as the idle-floor trial (one watch-session, two proofs).
- **UX-024** — HE-AAC vs AAC-LC per-stream-class offload probing: needs Ric
  to switch TIDAL quality tier; then it is a scriptable probe.
- Library browse 3–4 taps + scroll: accepted design, revisit only if the
  ship checklist questions it.

## 4. Ship path (existing board items, not new work)
- **UNTIDY-032** real-watch validation matrix — P0, in-progress; fold UXC-007
  + idle-floor results into its evidence.
- **UNTIDY-014** release readiness — blocked only on #33.
- **UNTIDY-042** commercial/legal readiness — decision item, Ric.
- **UNTIDY-041** full-fidelity streaming quality — follow-up unless promoted
  to release-blocker.

## Sequencing
1. Delegated batch (MID tier): 043, 044, 045 — independent, one worktree each.
2. Watch session (Ric + parent): idle-floor trial + UXC-007 + UNTIDY-032
   matrix entries in one sitting.
3. UX-024 probe once tier is switched.
4. Close 014 when 032 evidence is in.

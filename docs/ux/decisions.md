# Decisions

## Program (2026-11-01)

1. **Physical watch is read-only for the program.** Release build, signed-in,
   real user data (46 playlists / 50 albums / 27 downloads). Allowed: launch,
   navigate, screenshot, uiautomator, startup timing, scroll. Forbidden:
   installs, Play/Favorite/Download/erase taps (they mutate real history/libs),
   data clears, permission changes without restore. Rationale: program must not
   damage the user's device; signed-in state is the only real-data baseline.
2. **Emulator `wear6` is the automation target.** Headless, script-booted,
   data persists between script boots (no -wipe-data). First-run state is
   reproducible via `pm clear`.
3. **No production auth shortcuts for automation.** Any auth fixture is
   debug-source-set-only or test-DI-only; never commit tokens (UX-009 open;
   awaiting user choice on one-time manual sign-in).
4. **Evidence discipline over speed**: corrected measurements stay corrected
   (e.g. Done-node 64x30 text-node error → true clickable 358x72px/36dp).
   Findings mislabeled "measured" get downgraded in place.
5. **Don't re-report resolved prior-audit items** (search prompt, safe area,
   Retry chips, player recovery) — verified against current code by lenses.
6. **Watch screen timeout left at 30s** (already user setting); journeys batch
   device actions in single scripts to survive it.
7. **Journey methodology**: on system input surfaces, edge swipes get captured
   by swipe-typing — use KEYCODE_BACK; home scroll position persists, so
   journeys must re-dump layout before tapping (learned 2026-11-01).
8. **Commits per experiment on `main`**, push only with user approval
   (public repo); revert = `git revert` + note in experiments.md.
9. **Repo toolchain paths**: `~/Work/toolchains/{android-sdk,jdk21}`; env
   script probes locations rather than hardcoding (this host; ThinkPad
   fallbacks preserved).

## Product (pre-approved directions, still per-experiment)

- Prefer removing interactions over restyling them (program spec).
- Keep the explicit "context before play" Recent behavior (prior decision,
  docs/reviews/recent-shelf-review) — not friction to fix.
- Preserve `tidal_settings` store name/keys (correctness lens: shared by UI
  and service).
- Wording must match actual effects ("Erase account" → sign-out semantics).

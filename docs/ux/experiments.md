# Experiments

Calibration principle (from program spec): the first three experiments test
whether this system can distinguish improvements from regressions, not how
much the app can change in one day. One coherent change per experiment, own
commit, device-exercised or explicitly downgraded to provisional.

## EXP-001 — remove the onboarding dead tap (UX-001)

finding: UX-001 | commit: (below)
problem: While device-code auth is pending, the only visible control,
  "Done", is a no-op (measured 2026-11-01: tap → byte-identical screenshots,
  no logcat reaction, polling continues in background). It is redundant when
  auth succeeds (auto-navigation) and sits at 36dp height (below Wear 48dp).
hypothesis: Removing the pending-state button and stating the automatic
  waiting semantics in copy removes a misleading interaction with no loss of
  actionability; error-state Retry remains the sole actionable button, resized
  to 48dp.
expected improvement: 0 no-op affordances on pending screen (before: 1);
  all buttons >= 48dp; user understands auto-advance without tapping.
invariants: device-code flow still completes and auto-navigates; Retry works
  on error; no new screens.
falsifier: pending state becomes confusing (user can't tell it's waiting) or
  error/retry regresses on device.
plan: OnboardingScreen: pending branch = no button, copy "Enter the code at
  the link shown — this screen updates automatically." + existing pulsing
  indicator; error branch Retry height 48dp.
before (measured): tap produces no feedback (evidence
  2026-11-01-emul-onboard-{before,after}-done.png; logcat
  "auth endpoint token/device-flow 400 authorization_pending, poll result=
  AuthorizationPending, elapsed=209s"); Done surface 358x72px=36dp tall.
after: TBD on wear6 this session.
verdict: PENDING

## EXP-002 — progressive slow_down backoff (UX-007)

finding: UX-007 | commit: (below)
problem: TidalAuthRepository maps every slow_down to base interval + 5s, so
  repeated throttling never backs off further (code-observed).
hypothesis: accumulating +5s per slow_down honors OAuth progressive backoff.
expected: polling interval strictly increases per consecutive slow_down.
invariants: normal interval unchanged; success/expiry paths untouched.
falsifier: unit tests show non-monotonic or over-bounded growth.
plan: interval = previous interval + 5 on slow_down (bounded, e.g. <= 60s);
  unit-test sequences of 1, 2, 5 consecutive slow_down responses.
before: unit-test-visible (existing tests cover single slow_down only).
after: TBD.
verdict: PENDING

## EXP-003 — settings account chip affordance (UX-002)

finding: UX-002 | commit: (below)
problem: "TIDAL account / User #…" and "No TIDAL account linked" chips are
  clickable but call an empty handler (code-observed; clickable=true measured
  on physical watch 2026-11-01 evidence watch-10-settings.xml).
hypothesis: rendering account status as non-clickable text removes a dead tap
  without removing information or any needed action (sign-out already has its
  own chip).
expected: Settings dump shows 0 clickable inert rows (before: >=1).
invariants: account identity stays visible; Erase/sign-out flow unchanged.
falsifier: any needed account action becomes unreachable.
verification: needs signed-in emulator for device re-check; code+unit check
  now, device re-check when UX-009 fixture exists -> accept-provisionally.
before/after: TBD
verdict: PENDING

## Reverted / inconclusive

(none yet — reverted experiments are the highest-value anti-recurrence data;
record them here, not in findings prose)

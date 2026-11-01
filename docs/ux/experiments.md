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
after (measured, wear6 2026-11-01): pending screen shows
  "Waiting — updates once you enter the code" + pulsing indicator and
  **0 clickable nodes** anywhere on screen (uiautomator; before: 1 inert
  clickable). Tap at former Done position → no-op possible, no affordance
  (evidence 2026-11-01-emul-after-onboard-fix.png). Error branch
  (bogus http_proxy): "Connection issue - try again" + Retry clickable at
  358x96px = **48dp** (evidence 2026-11-01-emul-onboard-error-retry.png);
  Retry re-run after network restore produced fresh code + live polling
  (2026-11-01-emul-onboard-retry-recovered.png, logcat elapsed=4s/7s).
  Auth-success auto-navigation code untouched (no device re-verify: requires
  completed sign-in — see UX-009).
verdict: **ACCEPT** (emulator; correctness preserved, dead affordance removed)

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
after: 4 new JVM tests in `core/auth/src/test/.../DeviceAuthPollingTest.kt`
  — sequence 5→10→15→20→25 on repeated slow_down (was stuck at 10),
  never-shortens, 60s cap; all green via `:core:auth:testDebugUnitTest`.
  Helper isolated in DeviceAuthPolling.kt (pure; auth file has
  static-init Android deps unusable in plain JVM tests — discovered when the
  first test version threw ExceptionInInitializerError).
verdict: **ACCEPT** (unit-verified; slow_down sequences need live server
  throttling to observe on-device and were NOT exercised)

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
before: measured clickable=true inert chip on signed-in watch
  (watch-10-settings.xml, "TIDAL account / User #38971465" 398x111px).
after: code renders non-clickable status Row (Surface bg, dot, same labels);
  compiles; device re-check of signed-in Settings blocked by UX-009.
UPDATE 2026-11-01 late: auth fixture completed (user entered device code;
  emulator poll logged Success). Signed-in wear6 Settings now shows
  'TIDAL account / User #38971465' as clickable=false row
  (evidence/2026-11-01-emul-settings-after-exp003.png); Erase chip still
  clickable as intended.
verdict: **ACCEPT** (device-verified signed-in, emulator 454x454)

## EXP-005 — Like action in Track Context (UX-004)

finding: UX-004 | commit: 63be2f2
problem: favoriting a recent track requires starting playback (Like exists
  only on Now Playing); Track Context shows Play/View album/View artist/
  Add to playlist/Download but no Like. Measured on signed-in watch
  (watch-13-track-context-scrolled.xml).
hypothesis: a Like toggle chip in Track Context (mirroring the player's
  lookup/toggle/eligibility/haptic pattern) lets users favorite without
  starting audio.
expected: favorite+unfavorite possible from context with no playback;
  state persists across re-entry (server-backed); Library "Liked tracks"
  count +1/−1.
invariants: existing context actions unchanged; eligibility rules identical
  to player (fixture/placeholder ids excluded); no double-toggle races.
falsifier: like state diverges from player/Library, or failure paths leave
  stale "Liked" label.
plan: reuse `isFavoriteLookupEligible` (made internal); lookup
  isFavoriteTrack on track.id; setFavoriteTrack toggle with haptic confirm,
  toast fallback, "Like unavailable" secondary on lookup failure.
verification (device, signed-in emulator): downloaded "Love Is All Around"
  via playlist (needed Download-over-LTE toggle on metered emu net; see
  UX-016) → shelf tap → track recent recorded (prefs JSON verified) →
  Recent screen → context: Play/View album/View artist/Add to playlist/
  Like all present (evidence/2026-11-01-emul-context-before/after.png).
  Like tap → chip "Liked · In your collection", `favorite track write ok
  favorite=true`, server v2 relationships GET shows addedAt matching the
  tap. Exit + re-enter context → still Liked (server read, not just cache).
  Unlike → chip back to "Like", write ok favorite=false, v2 GET shows item
  absent and no entries dated today (user state fully restored).
before/after: context without/with Like chip (screenshots, 454x454)
verdict: **ACCEPT** (device-verified round-trip; falsifier's Library-count
  divergence also fired but is pre-existing and applies to the player's
  like equally → filed separately as UX-017)

## EXP-006 — Library liked-tracks from the v2 store (UX-017)

finding: UX-017 | commit: EXP-006 (same commit as the code below in git log)
problem: "Liked tracks · 47" + truncated list for an account with 281
  favorites; source was the legacy v1 fallback page (limit=50, unordered).
hypothesis: full v2 relationship pagination + batched track metadata gives
  the real count and a browsable complete list.
expected: count reflects true favorites (~all resolvable), list scrolls
  past the old 47 cap, no liked-state regressions.
falsifier: count unchanged/wrong, list still capped, or chip liked-state
  regresses.
plan: favoriteTracksV2(): relationship ids (cursor walk) ->
  GET /v2/tracks?filter[id]=csv&include=artists,albums; filter caps at 20
  values and SILENTLY drops extras (measured) so chunk=20; legacy path
  remains as degraded fallback.
verification (device): library row "Liked tracks · 264" (before 47);
  logcat "library fallback ok tracks=264"; list renders titles+artists and
  scrolls deep (evidence/2026-11-01-emul-liked-count-after.png,
  ...liked-list-after.png).
before/after: 47 -> 264 (count row, same account same second)
verdict: **ACCEPT**
methodology note (IMPORTANT): the original UX-017 "two stores" diagnosis
  was my curl probes under-paginating BOTH stores (v2 next-link encodes
  cursor as page%5Bcursor%5D= — naive `cursor=` regex breaks; v1 offset
  paging is unordered). Lesson for the skill: never conclude dataset size
  from single-page API probes; paginators must be cross-checked against a
  second encoding of the cursor and against a server-reported total.

## Reverted / inconclusive

### EXP-007 — UX-013 search-strand fix (never implemented: finding withdrawn)
Emulator-only repro failed to reproduce on the physical watch (correct
Back chain through the system remote-input picker). No code change made;
UX-013 withdrawn in findings.md. Lesson stored in skill references.

(none yet — reverted experiments are the highest-value anti-recurrence data;
record them here, not in findings prose)

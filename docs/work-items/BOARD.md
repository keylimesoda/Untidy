# Untidy Work Board

_Generated from `work-items.json`._

## in-progress

### UNTIDY-011 — Downloads/offline playback capability spike

- Priority: P2
- Type: spike
- Area: offline
- Owner: Tommy
- Labels: downloads, offline, drm, tidal
- Spec: `docs/spec-downloads-offline-playback.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/11
- Next: Inspect the sanctioned DOWNLOAD manifest shape and SDK cache-read expectations to determine whether it can drive a debug-only cache-fill experiment without STREAM/PLAYBACK manifests; if not, continue searching for the offline license/download-link source before attempting playback.
- Acceptance:
  - Determine whether TIDAL API/SDK permits offline storage for this app
  - If viable, define single-track MVP implementation plan
  - If not viable, remove/neutralize fake download affordances

### UNTIDY-022 — Validate Now Playing actions discoverability and rotary behavior

- Priority: P2
- Type: test
- Area: ui
- Owner: Tommy
- Labels: now-playing, rotary, wear-os, ux
- Spec: `docs/test-plan-now-playing-actions-rotary.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/23
- Next: Run the Now Playing actions/rotary validation matrix on the Wear emulator, then repeat rotary/bezel behavior on a real Wear device if available before review.
- Acceptance:
  - Users can discover player actions without prior knowledge.
  - Action sheet scroll/rotary behavior is validated on emulator and real watch if possible.
  - Any added affordance stays watch-glanceable and does not clutter Now Playing.
  - Relevant compile/lint gate passes.

## review

### UNTIDY-014 — Release readiness / ship checklist

- Priority: P1
- Type: task
- Area: qa
- Owner: Tommy
- Labels: release, ship-readiness, qa, docs
- Spec: `docs/release-readiness-checklist.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/15
- Next: Review release-readiness checklist and use it as the final ship gate after #11 and release-polish dispositions.
- Acceptance:
  - A release-readiness checklist exists and is checked off or explicitly deferred item-by-item.
  - Final build/test/lint evidence is posted.
  - Any remaining release blockers have separate GitHub issues.
  - Debug-only code cannot leak into production release surfaces.
  - Known limitations are documented in README/specs.

### UNTIDY-018 — Senior Wear OS UX path walkthrough

- Priority: P1
- Type: test
- Area: ui
- Owner: Tommy
- Labels: ux, wear-os, review, qa
- Spec: `docs/ux/wear-os-ux-walkthrough-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/19
- Next: Review senior Wear OS UX walkthrough report; top findings converted to follow-up issues #20-#24.
- Acceptance:
  - A UX walkthrough report exists under docs/ux/ or reports/ux/.
  - Report includes path-by-path observations, screenshots/artifact references where available, severity, and recommendations.
  - Top P0/P1 UX issues are converted into GitHub issues or explicitly accepted/deferred.
  - No generic, non-Wear-specific advice without source/evidence.

### UNTIDY-019 — Fix Wear search entry blank-screen UX

- Priority: P1
- Type: bug
- Area: ui
- Owner: Tommy
- Labels: search, wear-os, ux, ime
- Spec: `docs/ux/wear-os-ux-walkthrough-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/20
- Next: Review visible Wear search prompt implementation and emulator artifacts; code landed in commit 81610ff alongside #21 safe-area padding.
- Acceptance:
  - Search entry never appears as blank black screen.
  - User has visible prompt/affordance before and during IME handoff.
  - Existing search results flow still works.
  - Runtime evidence captured.
  - Compile/lint or relevant narrow gate passes.

### UNTIDY-020 — Tune round-screen list safe-area padding

- Priority: P1
- Type: cleanup
- Area: ui
- Owner: Tommy
- Labels: round-screen, padding, wear-os, ux
- Spec: `docs/ux/wear-os-ux-walkthrough-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/21
- Next: Review emulator evidence for shared round-screen list padding and merge if acceptable.
- Acceptance:
  - First/last actionable rows settle inside round safe area on emulator screenshots.
  - Search, Library, and Home screenshots show fully readable primary rows.
  - No major regression to scroll ergonomics.
  - Compile/lint or relevant narrow gate passes.

### UNTIDY-021 — Gate or confirm New test playlist creation

- Priority: P1
- Type: cleanup
- Area: ui
- Owner: Tommy
- Labels: playlist, write, ux, safety
- Spec: `docs/ux/wear-os-ux-walkthrough-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/22
- Next: Review debug-only New test playlist gate; create row is hidden in release builds while preserving dev/test flow.
- Acceptance:
  - Users cannot accidentally create a test playlist from first row with one tap in production UX.
  - If debug-only, row is impossible in release builds.
  - If confirmation-based, confirmation/cancel text is clear on a watch.
  - No delete path is introduced.
  - Relevant compile/test gate passes.

### UNTIDY-015 — Resolve WearRecents/task-affinity lint warnings

- Priority: P2
- Type: cleanup
- Area: ui
- Owner: Tommy
- Labels: wear-os, recents, lint, navigation
- Spec: `docs/refactor-backlog.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/16
- Next: Review Wear-safe route handoff change; PlayerActivity now uses REORDER_TO_FRONT|SINGLE_TOP instead of CLEAR_TOP while preserving authenticated route delivery through MainActivity.onNewIntent.
- Acceptance:
  - WearRecents warnings are fixed or intentionally suppressed with a comment/rationale.
  - Back from View Album/View Artist remains fixed.
  - Navigation from Now Playing remains stable.
  - Narrow build/lint verification passes.

### UNTIDY-017 — Normalize offline/download docs to corrected #11 framing

- Priority: P2
- Type: cleanup
- Area: offline
- Owner: Tommy
- Labels: docs, offline, downloads, tidal
- Spec: `docs/spec-downloads-offline-playback.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/18
- Next: Review normalized offline/download docs; stale permission-blocked language replaced with sanctioned SDK/API provisioning proof framing while retaining no-STREAM-manifest guardrail.
- Acceptance:
  - Docs no longer imply TIDAL offline/download is categorically permission-blocked for this class of client.
  - Docs consistently say #11 is an implementation-proof/orchestration task using sanctioned SDK/API surfaces.
  - Guardrail against raw STREAM/PLAYBACK manifest caching remains prominent.
  - git diff --check passes.

### UNTIDY-023 — Add watch-friendly retry/error recovery

- Priority: P2
- Type: cleanup
- Area: ui
- Owner: Tommy
- Labels: errors, retry, wear-os, ux
- Spec: `docs/ux/wear-os-ux-walkthrough-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/24
- Next: Review retry/error recovery implementation: detail screens now use tap-to-retry chips and action sheet shows inline metadata errors instead of toast-only feedback.
- Acceptance:
  - Detail screen network/API errors have one-tap retry.
  - Missing metadata feedback is visible in the action context, not toast-only.
  - Empty states remain clear and non-alarming.
  - Relevant compile/lint gate passes.

## todo

### UNTIDY-016 — Dispose ExportedService lint warning with documented security posture

- Priority: P2
- Type: cleanup
- Area: platform
- Owner: Tommy
- Labels: exported-service, security, lint, media-controls
- Spec: `docs/flow-and-cleanup-audit.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/17
- Next: Reconfirm exported=true requirement for Media3/Wear controls; fix or suppress with rationale.
- Acceptance:
  - ExportedService lint warning is fixed or suppressed with explicit rationale.
  - Controller access policy tests still pass.
  - Media controls/playback service still function in emulator and preferably real Wear OS validation.
  - Security posture is documented in code or docs.

## blocked

_None._

## done

### UNTIDY-001 — Stabilize Wear emulator workflow under Fedora SELinux

- Priority: P0
- Type: task
- Area: tooling
- Owner: Tommy
- Labels: emulator, selinux, kvm, qa
- Spec: `docs/emulator-selinux-notes.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/1
- Next: Ready to close after PR #14 lands or maintainer review; safe wrapper/docs exist and final cleanup evidence shows selinuxuser_execheap off with no emulator/ADB devices remaining.
- Acceptance:
  - Document safe wrapper/toggle workflow
  - Keep selinuxuser_execheap off by default when emulator is not running
  - Record repeatable emulator boot command

### UNTIDY-002 — Run authenticated Wear emulator smoke test suite

- Priority: P0
- Type: test
- Area: qa
- Owner: Tommy
- Labels: emulator, auth, playback
- Spec: `docs/test-plan-thinkpad-emulator.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/2
- Next: All imported acceptance criteria now have emulator evidence; close after PR lands or maintainer review.
- Acceptance:
  - Authenticated app reaches Home
  - Search returns results
  - Single-track playback starts
  - Album Play all queues and plays multiple tracks
  - No AndroidRuntime crash in filtered logcat

### UNTIDY-003 — Validate album and playlist play-all behavior

- Priority: P0
- Type: test
- Area: playback
- Owner: Tommy
- Labels: album, playlist, queue, playback
- Spec: `docs/test-plan-thinkpad-emulator.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/3
- Next: Closed after emulator runtime validation on emulator-5554: multi-page album fallback loaded 65 tracks, album/playlist Play all queued correctly, requested playlist row index preserved, and queue transitions/prefetch were observed.
- Acceptance:
  - Multi-page album queues more than first page
  - Playlist Play all starts at requested index
  - 100-track watch cap behavior is acceptable/no silent app crash
  - Track transitions continue beyond first 1-2 songs

### UNTIDY-004 — Validate playback transition smoothness and prefetch

- Priority: P0
- Type: test
- Area: playback
- Owner: Tommy
- Labels: prefetch, seamless, media3
- Spec: `docs/test-plan-thinkpad-emulator.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/4
- Next: Closed after emulator runtime validation on emulator-5554: single-track and queue playback started without unexpected stalls, Next transitions updated metadata promptly, prefetch logged, and forced network failure surfaced recoverable Now Playing error UI.
- Acceptance:
  - Starting a track does not pause/stall unexpectedly
  - Natural transition to next track is smooth enough for watch use
  - Playback error UI appears for manifest/network failures

### UNTIDY-005 — Fix/validate shared settings DataStore crash

- Priority: P0
- Type: bug
- Area: stability
- Owner: Tommy
- Labels: datastore, crash, service, settings
- Spec: `reports/emulator-smoke/no-login-smoke-report-2026-06-16.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/5
- Next: Ready to close after PR #14 lands; runtime service/UI coexistence, no DataStore AndroidRuntime crash, full Gradle gate, and diff check all passed.
- Acceptance:
  - UI and playback service can coexist without duplicate DataStore crash
  - No AndroidRuntime DataStore exception in logcat during launch/service probes
  - Full Gradle gate passes

### UNTIDY-013 — Back from View Album route drops authenticated app to onboarding

- Priority: P0
- Type: bug
- Area: stability
- Owner: Tommy
- Labels: auth, navigation, emulator, runtime
- Spec: `reports/emulator-smoke/unexpected-onboarding-after-back-logcat.txt`
- GitHub: https://github.com/keylimesoda/Untidy/issues/13
- Next: Ready to close after PR #14 lands; parent emulator regression verified Back from album route returns to authenticated Home, not onboarding.
- Acceptance:
  - Back from View Album/View Artist does not start onboarding
  - Authenticated state persists
  - Emulator regression passes

### UNTIDY-006 — Decide exported TidalMediaService security posture

- Priority: P1
- Type: decision
- Area: platform
- Owner: Tommy
- Labels: exported-service, security, media-controls
- Spec: `docs/flow-and-cleanup-audit.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/6
- Next: Review-ready after app-private command-token hardening for exported custom service actions; full Gradle gate passed. Remaining residual is real Wear/OEM media-controller validation before final done/merge.
- Acceptance:
  - Test notification/media controls with exported=true
  - Test whether exported=false breaks Wear/system media controls
  - If exported remains true, add controller/package validation or document accepted risk

### UNTIDY-007 — Validate View Album / View Artist from Now Playing

- Priority: P1
- Type: test
- Area: ui
- Owner: Tommy
- Labels: now-playing, album, artist
- Spec: `docs/spec-view-album-artist-from-now-playing.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/7
- Next: Ready to close after PR #14 lands if static fallback-toast verification is accepted; View Album and View Artist passed live runtime validation.
- Acceptance:
  - Action sheet buttons appear during real playback
  - View Album opens correct album screen
  - View Artist opens correct artist screen
  - Missing IDs show fallback toast, not crash

### UNTIDY-008 — Live-validate favorite/like toggle

- Priority: P1
- Type: test
- Area: api
- Owner: Tommy
- Labels: favorites, tidal-api, write
- Spec: `docs/flow-and-cleanup-audit.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/8
- Next: Ready to close after PR #14 lands; live favorite and unfavorite writes passed and were restored, with full Gradle gate passed.
- Acceptance:
  - Heart loads persisted favorite state
  - Favorite write persists to TIDAL
  - Unfavorite write persists to TIDAL
  - Failure rolls back optimistic UI with toast/error

### UNTIDY-009 — Implement Current Queue UI

- Priority: P1
- Type: feature
- Area: ui
- Owner: Tommy
- Labels: queue, feature, player-action-sheet
- Spec: `docs/spec-current-queue-ui.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/9
- Next: Ready to close after PR #14 lands if static empty/single-track queue-state verification is accepted; Queue action/sheet/jump-to-index passed runtime validation and Gradle gate passed.
- Acceptance:
  - Player pull-under/action sheet has Current Queue button
  - Queue screen shows current track and upcoming tracks
  - Empty queue state is safe
  - Compile/lint passes

### UNTIDY-010 — Implement Add to Playlist workflow

- Priority: P2
- Type: feature
- Area: api
- Owner: Tommy
- Labels: playlist, feature, tidal-api, write
- Spec: `docs/spec-add-to-playlist.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/10
- Next: Closed after live TIDAL API validation and implementation: faux playlist creation/add worked within Ric's no-delete boundary, duplicate writes are prechecked, UI exposes New test playlist, and full Gradle gate passed.
- Acceptance:
  - Add current track to existing playlist
  - Create new playlist and add track
  - Success/error/loading states on watch UI
  - Live API write validation passes

### UNTIDY-012 — Migrate remaining Wear Compose lazy/icon deprecations

- Priority: P3
- Type: cleanup
- Area: cleanup
- Owner: Tommy
- Labels: wear-compose, warnings, cleanup
- Spec: `docs/refactor-backlog.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/12
- Next: Do after P0/P1 runtime validation.
- Acceptance:
  - No ScalingLazyColumn/rememberScalingLazyListState deprecation warnings in compile log
  - AutoMirrored icon warnings resolved where straightforward
  - Build/lint/tests pass

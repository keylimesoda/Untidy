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
- Next: Probe endpoint-shape alternates from bytecode results: retry UserOfflineMixes relationships with omitted cursor/non-user IDs or discovered mix IDs, inspect models for download/offline relationship ids, and if still empty, build a compile-only OfflinePlayProvider/cache harness to prove local SDK playback wiring while server task/id orchestration remains unresolved.
- Acceptance:
  - Determine whether TIDAL API/SDK permits offline storage for this app
  - If viable, define single-track MVP implementation plan
  - If not viable, remove/neutralize fake download affordances

## review

_None._

## todo

_None._

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

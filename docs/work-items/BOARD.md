# Untidy Work Board

_Generated from `work-items.json`._

## in-progress

### UNTIDY-032 — Real Wear OS release validation

- Priority: P0
- Type: test
- Area: qa
- Owner: Tommy
- Labels: real-watch, release, qa, wear-os
- Spec: `docs/release-readiness-checklist.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/33
- Next: Physical watch validation started; new findings filed as #35/#36/#37. Resolve P0 playback persistence before release.
- Acceptance:
  - Real watch model and Wear OS version are recorded.
  - APK/build variant and commit under test are recorded.
  - Core navigation and playback results are documented with artifact notes.
  - Rotary/input/media-session/Bluetooth behavior is explicitly pass/fail/deferred.
  - Offline/download real-watch result is either passed or explicitly deferred for non-public beta.
  - Findings are linked back to #15 release decision record.

### UNTIDY-036 — P0 playback must continue after app background/screen-off

- Priority: P0
- Type: bug
- Area: playback
- Owner: Tommy
- Labels: playback, foreground-service, media3, release-blocker
- Spec: `docs/release-readiness-checklist.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/37
- Next: Swarm immediately: reproduce or collect logs, identify service/queue/background failure, fix playback continuation across app background/screen-off/multiple songs.

## review

### UNTIDY-014 — Release readiness / ship checklist

- Priority: P1
- Type: task
- Area: qa
- Owner: Tommy
- Labels: release, ship-readiness, qa, docs
- Spec: `docs/release-readiness-checklist.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/15
- Next: Physical watch validation found new blockers #35/#37; release gate remains open until they are resolved and #33 is updated.
- Acceptance:
  - A release-readiness checklist exists and is checked off or explicitly deferred item-by-item.
  - Final build/test/lint evidence is posted.
  - Any remaining release blockers have separate GitHub issues.
  - Debug-only code cannot leak into production release surfaces.
  - Known limitations are documented in README/specs.

## todo

### UNTIDY-034 — Now Playing needs a clear route back to app Home

- Priority: P1
- Type: bug
- Area: ui
- Owner: Tommy
- Labels: now-playing, navigation, wear-os, ux
- Spec: `docs/release-readiness-checklist.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/35
- Next: Reason through and implement a Wear-appropriate route from Now Playing back to Untidy Home/Search/Library without making Back obnoxious.

### UNTIDY-035 — Review and tune TIDAL-appropriate typography

- Priority: P2
- Type: cleanup
- Area: ui
- Owner: Tommy
- Labels: typography, brand, wear-os, polish
- Spec: `docs/release-readiness-checklist.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/36
- Next: Audit current typography and recommend/implement legally safe, Wear-readable, TIDAL-appropriate styling.

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

### UNTIDY-025 — Wire offline download/replay into end-user UX

- Priority: P0
- Type: feature
- Area: offline
- Owner: Tommy
- Labels: offline, downloads, ux, single-track-mvp
- Spec: `docs/spec-downloads-offline-playback.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/26
- Next: Done. Single-track offline MVP route is validated through app-authored playback; release checklist retains real-watch/manual tap-through validation.
- Acceptance:
  - A user-accessible or explicitly debug-gated UI path can download one track.
  - Downloaded state persists across app/service restart.
  - Network-disabled playback of that downloaded track succeeds from app UI.
  - Runtime artifact proves local replay with no PLAYBACK/STREAM manifest use.
  - Narrow Gradle gate passes.

### UNTIDY-026 — Design full offline/download lifecycle UX

- Priority: P0
- Type: task
- Area: offline
- Owner: Tommy
- Labels: offline, downloads, ux, lifecycle, wear-os
- Spec: `docs/ux/offline-download-lifecycle-ux-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/27
- Next: Done: lifecycle UX report accepted as implementation guide; follow-up implementation issues #28-#32 were created and are now the active/review lanes.
- Acceptance:
  - Report exists and is reviewed by Ric.
  - Design is Wear OS-specific and grounded in current Untidy source/flows.
  - Report explicitly recommends removal/management units.
  - Follow-up implementation issues are created only after review.

### UNTIDY-027 — Implement Home → Downloads shelf

- Priority: P0
- Type: feature
- Area: offline
- Owner: Tommy
- Labels: downloads, offline, ux, shelf
- Spec: `docs/ux/offline-download-lifecycle-ux-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/28
- Next: Closed after review: Home Downloads chip routes to Downloads shelf; downloaded track list/playback and local-only remove controls verified by emulator artifacts reports/downloads-shelf-2026-06-17-1533/.
- Acceptance:
  - Home Downloads row opens Downloads screen.
  - Downloads screen shows empty state or downloaded tracks.
  - A downloaded track row can start playback.
  - UI uses Wear list/safe-area patterns.
  - Compile and emulator smoke pass.

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
- Next: Done; issue #20 can stay closed unless new search-entry runtime evidence regresses.
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
- Next: Done: round-screen safe-area padding accepted with emulator screenshot/XML evidence and narrow build/diff verification.
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
- Next: Done. Debug/test playlist create+add row is impossible in release builds, remains available in debug builds, and no delete path was introduced.
- Acceptance:
  - Users cannot accidentally create a test playlist from first row with one tap in production UX.
  - If debug-only, row is impossible in release builds.
  - If confirmation-based, confirmation/cancel text is clear on a watch.
  - No delete path is introduced.
  - Relevant compile/test gate passes.

### UNTIDY-024 — Align offline cache keys for network-disabled replay

- Priority: P1
- Type: feature
- Area: offline
- Owner: Tommy
- Labels: offline, downloads, cache, media3, tidal
- Spec: `docs/offline-sdk-proof-plan-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/25
- Next: Closed: canonical cache-key replay proof is accepted; product offline UX/playback work continues through already-created follow-up issues and release-readiness review.
- Acceptance:
  - Debug proof artifact shows network-disabled replay reaches READY/playing from app-private cache without chunk network upstream.
  - Artifact shows no STREAM/PLAYBACK manifest use and no URL/token/license leakage.
  - Single-track MVP implementation plan is updated with exact storage/cache/provider contract.
  - If production code is touched, narrow Gradle gate and runtime emulator proof pass.

### UNTIDY-028 — Implement local remove-download UX

- Priority: P1
- Type: feature
- Area: offline
- Owner: Tommy
- Labels: downloads, remove, storage, ux
- Spec: `docs/ux/offline-download-lifecycle-ux-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/29
- Next: Done: local remove-download UX accepted with emulator tap-through proof and local-only cache/metadata cleanup evidence.

### UNTIDY-029 — Implement offline fallback behavior

- Priority: P1
- Type: feature
- Area: offline
- Owner: Tommy
- Labels: offline, fallback, network, ux
- Spec: `docs/ux/offline-download-lifecycle-ux-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/30
- Next: Closed after review: offline fallback runtime evidence and compile gate are sufficient for the P1 fallback MVP; real-watch/manual release validation remains covered by #15/#23.
- Acceptance:
  - Offline mode does not strand the user on live-only screens.
  - Downloaded tracks remain discoverable/playable through Home -> Downloads.
  - Non-downloaded tracks fail with clear user-facing copy.

### UNTIDY-033 — Resolve release legal/attribution/docs placeholders

- Priority: P1
- Type: task
- Area: qa
- Owner: Tommy
- Labels: release, docs, legal, attribution
- Spec: `docs/release-readiness-checklist.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/34
- Next: Closed: Settings legal/attribution placeholders replaced and README current limitations/offline scope refreshed.
- Acceptance:
  - Settings legal/attribution placeholders are removed, replaced, or hidden with rationale.
  - README or docs contain current known limitations and offline/download release scope.
  - Static grep evidence is posted showing no release-facing placeholder/proof copy in production source.
  - Relevant Gradle compile gate and git diff --check pass.

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

### UNTIDY-011 — Downloads/offline playback capability spike

- Priority: P2
- Type: spike
- Area: offline
- Owner: Tommy
- Labels: downloads, offline, drm, tidal
- Spec: `docs/spec-downloads-offline-playback.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/11
- Next: Spike complete. Follow-up implementation moved to UNTIDY-024 / GitHub #25: align offline cache keys/storage for network-disabled replay and single-track MVP.
- Acceptance:
  - Prove the exact TIDAL offline provisioning path for this app
  - If viable, define single-track MVP implementation plan
  - If not viable, remove/neutralize fake download affordances

### UNTIDY-015 — Resolve WearRecents/task-affinity lint warnings

- Priority: P2
- Type: cleanup
- Area: ui
- Owner: Tommy
- Labels: wear-os, recents, lint, navigation
- Spec: `docs/refactor-backlog.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/16
- Next: Done. WearRecents/task-affinity disposition verified: PlayerActivity uses REORDER_TO_FRONT|SINGLE_TOP with rationale, no CLEAR_TOP remains, and lint/compile gate passes.
- Acceptance:
  - WearRecents warnings are fixed or intentionally suppressed with a comment/rationale.
  - Back from View Album/View Artist remains fixed.
  - Navigation from Now Playing remains stable.
  - Narrow build/lint verification passes.

### UNTIDY-016 — Dispose ExportedService lint warning with documented security posture

- Priority: P2
- Type: cleanup
- Area: platform
- Owner: Tommy
- Labels: exported-service, security, lint, media-controls
- Spec: `docs/flow-and-cleanup-audit.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/17
- Next: Done. ExportedService disposition verified: TidalMediaService remains exported with manifest rationale, controller filtering, app-command-token safeguards, and lint/playback unit gates pass.
- Acceptance:
  - ExportedService lint warning is fixed or suppressed with explicit rationale.
  - Controller access policy tests still pass.
  - Media controls/playback service still function in emulator and preferably real Wear OS validation.
  - Security posture is documented in code or docs.

### UNTIDY-017 — Normalize offline/download docs to corrected #11 framing

- Priority: P2
- Type: cleanup
- Area: offline
- Owner: Tommy
- Labels: docs, offline, downloads, tidal
- Spec: `docs/spec-downloads-offline-playback.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/18
- Next: Done: offline/download docs now use corrected sanctioned-download framing, preserve the no-PLAYBACK/STREAM guardrail, and no longer describe #11 as active/open.
- Acceptance:
  - Docs no longer imply TIDAL offline/download is categorically permission-blocked for this class of client.
  - Docs consistently say #11 is closed as the capability spike and follow-up proof/MVP work lives in sanctioned SDK/API surfaces tracked by #25/#26/#27-#32.
  - Guardrail against raw STREAM/PLAYBACK manifest caching remains prominent.
  - git diff --check passes.

### UNTIDY-022 — Validate Now Playing actions discoverability and rotary behavior

- Priority: P2
- Type: test
- Area: ui
- Owner: Tommy
- Labels: now-playing, rotary, wear-os, ux
- Spec: `docs/test-plan-now-playing-actions-rotary.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/23
- Next: Done: Now Playing exposes a visible Actions affordance; action sheet uses explicit rotary focus/scroll wiring; emulator artifacts and compile gate are sufficient for this issue. Real-watch crown/bezel validation remains in release readiness #15.
- Acceptance:
  - Users can discover player actions without prior knowledge.
  - Action sheet scroll/rotary behavior is validated on emulator and real watch if possible.
  - Any added affordance stays watch-glanceable and does not clutter Now Playing.
  - Relevant compile/lint gate passes.

### UNTIDY-023 — Add watch-friendly retry/error recovery

- Priority: P2
- Type: cleanup
- Area: ui
- Owner: Tommy
- Labels: errors, retry, wear-os, ux
- Spec: `docs/ux/wear-os-ux-walkthrough-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/24
- Next: Closed after re-verification: retry/error recovery acceptance is satisfied by RetryStatusChip detail errors, inline Now Playing action feedback, :app:compileDebugKotlin, and git diff --check.
- Acceptance:
  - Detail screen network/API errors have one-tap retry.
  - Missing metadata feedback is visible in the action context, not toast-only.
  - Empty states remain clear and non-alarming.
  - Relevant compile/lint gate passes.

### UNTIDY-030 — Enable minimal Settings Downloads controls

- Priority: P2
- Type: cleanup
- Area: offline
- Owner: Tommy
- Labels: settings, downloads, storage, ux
- Spec: `docs/ux/offline-download-lifecycle-ux-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/31
- Next: Closed after review: Settings Downloads controls are coherent with the single-track MVP, can open Manage downloads, show track/storage summary, keep remove-all local-only with TIDAL-library-safe copy, and distinguish sign-out from downloads.
- Acceptance:
  - Settings Downloads section is coherent with the productized single-track MVP.
  - Settings can open Manage downloads and shows count/storage summary when available.
  - Remove all downloads is local-only and confirms that TIDAL library is kept.
  - Sign-out copy explicitly distinguishes account sign-out from local downloads.
  - No Settings release copy says proof/proof-in-progress.

### UNTIDY-031 — Add album/playlist collection download UX

- Priority: P2
- Type: feature
- Area: offline
- Owner: Tommy
- Labels: album, playlist, downloads, collections
- Spec: `docs/ux/offline-download-lifecycle-ux-2026-06-17.md`
- GitHub: https://github.com/keylimesoda/Untidy/issues/32
- Next: Closed after collection download chips, failure-count state modeling, unit coverage, and runtime evidence.

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

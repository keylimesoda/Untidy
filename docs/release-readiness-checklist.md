# Untidy Release Readiness Checklist

_Last updated: 2026-06-17_

## Purpose

This is the ship gate for Untidy after the current feature/test wave. It is not a generic TODO list; it is the checklist that decides whether the app is ready for a real release candidate.

Release readiness means:

- the app builds cleanly enough to trust;
- known debug/proof-only code cannot leak into release behavior;
- the core Wear OS user paths have runtime evidence;
- UX findings have either shipped fixes or explicit deferrals;
- docs and known limitations match the actual product state;
- remaining warnings are either fixed or intentionally accepted with rationale.

## Current release posture

**Status:** Not release-ready yet.

**Offline/download release decision:** #11 / UNTIDY-011 is closed as a completed capability spike, and the sanctioned `usage=DOWNLOAD` proof line has advanced through the single-track offline MVP. Offline/download is no longer a categorical permission blocker. The remaining release decision is narrower: whether the current single-track Downloads shelf/remove/fallback/settings implementation is accepted for beta, or whether album/playlist collection downloads and real-watch offline validation are deferred.

**P1/P2 UX and documentation items already disposed:**

- #18 / UNTIDY-017 — Offline/download docs normalization uses the corrected sanctioned-download framing and preserves the no-`PLAYBACK`/`STREAM` guardrail.
- #20 / UNTIDY-019 — Search entry no longer presents a blank black screen; visible Wear prompt landed.
- #21 / UNTIDY-020 — Round-screen safe-area padding landed.
- #22 / UNTIDY-021 — New test playlist creation is gated to debug builds and closed done.

**Open release-polish items still needing disposition:**

- None in GitHub/local board. The remaining release gate is #15 itself: final build/test/lint sweep, emulator release-readiness pass, and real-watch/deferral decision.

**Recently disposed release-polish items:**

- #16 / UNTIDY-015 — WearRecents/task-affinity lint warning disposition is closed done after source verification (`REORDER_TO_FRONT | SINGLE_TOP`, no `CLEAR_TOP`) and `:app:lintDebug :app:compileDebugKotlin :core:playback:testDebugUnitTest` / `git diff --check`.
- #17 / UNTIDY-016 — ExportedService lint/security posture is closed done after manifest rationale, controller filtering, app-command-token safeguards, playback policy tests, and `:app:lintDebug :app:compileDebugKotlin :core:playback:testDebugUnitTest` / `git diff --check`.
- #23 / UNTIDY-022 — Now Playing actions discoverability/rotary validation is closed done after runtime validation.
- #24 / UNTIDY-023 — Watch-friendly retry/error recovery is closed done after source re-verification and `:app:compileDebugKotlin` / `git diff --check`.

## Release gate checklist

### 1. Source control and work item state

- [ ] `main` is clean: `git status --short --branch` shows no local changes.
- [ ] No open PRs remain unmerged or unreviewed.
- [ ] GitHub Issues and `docs/work-items/BOARD.md` agree.
- [ ] Every open issue is one of:
  - [ ] explicit release blocker,
  - [ ] accepted release deferral,
  - [ ] post-release backlog.
- [x] #11 has a release decision: capability spike closed; current release choice is single-track offline MVP acceptance vs collection-download/real-watch deferral.
- [ ] #15 is updated with final evidence/comment before closing.

### 2. Build, lint, and tests

Run from repo root:

```bash
source scripts/env-android.sh
bash ./gradlew lintDebug assembleDebug testDebugUnitTest --no-daemon
git diff --check
```

Required disposition:

- [ ] `assembleDebug` passes.
- [ ] `testDebugUnitTest` passes.
- [ ] `git diff --check` passes.
- [ ] `lintDebug` either passes or every remaining warning/error is linked to an issue/explicit rationale below.

Current known lint disposition backlog:

- [x] `UnsafeOptInUsageError` in debug-only `OfflineProofService.kt` resolved with debug-source file-level Media3 `UnstableApi` opt-in; `:app:lintDebug` passes.
- [x] `ExportedService` warning resolved/suppressed/documented via #17.
- [x] `WearRecents` / task-affinity warnings resolved/suppressed/documented via #16.
- [ ] `GradleDependency` warnings either updated or intentionally deferred with dependency-bump issue/rationale.

### 3. Debug/proof-only code audit

- [ ] Search for debug-only/proof code:

```bash
rg -n "OfflineProof|debug-only|BuildConfig.DEBUG|Untidy Test|faux|proof|TODO|FIXME|HACK" app core docs -S
```

- [ ] `OfflineProofService` is only available in debug source sets or otherwise impossible to trigger in release.
- [ ] No debug proof endpoint/activity/service is exported in release.
- [ ] `New test playlist` create+add row is impossible in release builds (#22).
- [ ] No test/faux playlist creation can be triggered by normal release users.
- [ ] No logs print tokens, licenses, download URLs, manifest URLs, encryption keys, or private user data.
- [ ] Any retained debug artifacts are documented as non-release tooling.

### 4. Runtime validation — emulator

Use the self-healing emulator path when needed:

```bash
source scripts/env-android.sh
UNTIDY_EMULATOR_INSTALL_LAUNCH=1 scripts/ensure-wear-emulator.sh
```

Capture final artifacts under `reports/release-readiness-YYYY-MM-DD/`.

Required emulator smoke paths:

- [ ] Clean install and launch.
- [ ] Signed-out/onboarding path, if auth state is cleared.
- [ ] Authenticated home loads without crash.
- [ ] Search entry shows visible Wear prompt, not blank black screen (#20).
- [ ] Search results render and primary result opens.
- [ ] Library category list and selected category render inside round safe area (#21).
- [ ] Album screen play-all/start-track path works.
- [ ] Playlist screen play-all/start-track path works.
- [ ] Now Playing opens and basic controls are visible.
- [ ] Queue screen opens and does not trap the user.
- [ ] View Album and View Artist routes from Now Playing remain authenticated and do not fall into onboarding.
- [ ] Add to Playlist existing-playlist path works against an approved test playlist or is explicitly deferred.
- [ ] Debug-only `New test playlist` row is absent in release-equivalent build and present only in debug if intentionally retained.
- [x] Offline/download shipped/proof state matches the #11 release decision: #11 is closed, #25/#26 carry the accepted `usage=DOWNLOAD` proof/MVP evidence, and remaining UX scope is tracked separately.
- [ ] Settings/account/playback/download sections render and back behavior works.
- [x] Error/empty states do not create dead ends; #24 disposition recorded: detail screens use one-tap retry chips and Now Playing action failures render inline status feedback.

### 5. Runtime validation — real Wear device

Emulator evidence is not enough for final release because Wear OEM keyboards, rotary input, media sessions, Bluetooth output, and power/network behavior vary.

Required real-device checks before a public/beta release:

- [ ] Install current APK on a real Wear OS watch.
- [ ] Launch/authenticated home path.
- [ ] Search entry with actual available input method: voice, keyboard, or OEM input panel.
- [ ] Rotary/bezel/crown behavior in Home, Search results, Library, Settings, Now Playing actions (#23).
- [ ] Media playback starts and continues with screen off / app backgrounded where supported.
- [ ] Bluetooth audio route behaves acceptably.
- [ ] Media controls/service notification behavior is acceptable.
- [ ] Back stack/recents behavior is acceptable (#16).
- [ ] Exported service/media controller behavior is acceptable and documented (#17).
- [ ] If offline/download ships: one-track offline proof on real watch, including network-off playback and license/expiry behavior.

### 6. UX readiness

Use `docs/ux/wear-os-ux-walkthrough-2026-06-17.md` as the baseline.

- [ ] #19 UX walkthrough reviewed/accepted.
- [ ] #20 search blank-screen fix reviewed and accepted.
- [ ] #21 round safe-area padding reviewed and accepted.
- [x] #22 New test playlist gating reviewed and accepted.
- [ ] #23 Now Playing action discoverability/rotary behavior validated or explicitly deferred.
- [x] #24 retry/error recovery implemented and closed done.
- [ ] No remaining user-visible copy says “Coming soon” for a path that is actually proof-in-progress or debug-only.
- [ ] Disabled/proof-in-progress states are honest, concise, and watch-readable.

### 7. Documentation readiness

- [ ] README accurately describes what Untidy currently supports.
- [ ] README or docs identify known limitations.
- [ ] Offline/download docs use the corrected framing: sanctioned SDK/API proof, not permission-blocked in principle (#18).
- [ ] Guardrail remains documented: do not implement offline by caching `PLAYBACK` / `STREAM` manifests.
- [ ] Emulator SELinux wrapper docs are current and do not imply persistent SELinux changes.
- [ ] Test plan links to latest runtime artifacts.
- [ ] Work item board has no stale `Next:` text.

### 8. Security/privacy/account safety

- [ ] No secrets are committed or logged in artifacts.
- [ ] Live TIDAL library mutations are limited to approved test/faux playlist behavior.
- [ ] Created test playlists are preserved; no delete path is introduced without explicit approval.
- [ ] Exported service posture is documented (#17).
- [ ] Debug-only proof services cannot be triggered in release.
- [ ] Auth tokens are stored/used only through existing auth repository patterns.

### 9. Release decision record

Before closing #15, add a GitHub comment with:

- commit SHA under test;
- APK/build variant tested;
- exact Gradle command results;
- emulator device/profile and artifact directory;
- real watch model/OS version and artifact notes;
- #11 offline/download decision and any remaining offline/download deferrals;
- remaining accepted deferrals, each linked to an issue;
- final go/no-go recommendation.

## Known deferral template

Use this format for any issue not fixed before release:

```text
Deferred issue: #NN — title
Severity: P1/P2/P3
Reason release can proceed:
Risk accepted by:
Follow-up trigger/date:
Evidence reviewed:
```

## Current recommended next steps

1. Run the final full Gradle gate and release-readiness emulator sweep against the selected release scope.
2. Decide whether real-watch validation is required before public beta or record it as an explicit non-public-beta deferral in #15.
3. Use the completed #25/#26/#28-#32 offline artifacts to decide whether single-track offline MVP ships now and collection-download UX is accepted for beta scope.
4. Update #15 with the final go/no-go comment and close only after GitHub Issues and the local board agree.

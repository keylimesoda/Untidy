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

**Status:** Emulator/professional gate passed; public/beta release is blocked only on physical Wear OS validation (#33) or explicit non-public-beta deferral.

**Offline/download release decision:** #11 / UNTIDY-011 is closed as a completed capability spike, and the sanctioned `usage=DOWNLOAD` proof line has advanced through the single-track offline MVP. Offline/download is no longer a categorical permission blocker. The remaining release decision is narrower: whether the current single-track Downloads shelf/remove/fallback/settings implementation is accepted for beta, or whether album/playlist collection downloads and real-watch offline validation are deferred.

**P1/P2 UX and documentation items already disposed:**

- #18 / UNTIDY-017 — Offline/download docs normalization uses the corrected sanctioned-download framing and preserves the no-`PLAYBACK`/`STREAM` guardrail.
- #20 / UNTIDY-019 — Search entry no longer presents a blank black screen; visible Wear prompt landed.
- #21 / UNTIDY-020 — Round-screen safe-area padding landed.
- #22 / UNTIDY-021 — New test playlist creation is gated to debug builds and closed done.

**Open release-polish items still needing disposition:**

- #33 / UNTIDY-032 — Real Wear OS release validation. Emulator evidence is current, but public/beta release still needs physical-watch validation or an explicit non-public-beta deferral.

**Recently disposed release-polish items:**

- #16 / UNTIDY-015 — WearRecents/task-affinity lint warning disposition is closed done after source verification (`REORDER_TO_FRONT | SINGLE_TOP`, no `CLEAR_TOP`) and `:app:lintDebug :app:compileDebugKotlin :core:playback:testDebugUnitTest` / `git diff --check`.
- #17 / UNTIDY-016 — ExportedService lint/security posture is closed done after manifest rationale, controller filtering, app-command-token safeguards, playback policy tests, and `:app:lintDebug :app:compileDebugKotlin :core:playback:testDebugUnitTest` / `git diff --check`.
- #23 / UNTIDY-022 — Now Playing actions discoverability/rotary validation is closed done after runtime validation.
- #24 / UNTIDY-023 — Watch-friendly retry/error recovery is closed done after source re-verification and `:app:compileDebugKotlin` / `git diff --check`.

## Release gate checklist

### 1. Source control and work item state

- [x] `main` is clean: `git status --short --branch` shows no local changes after release-polish commit `37c0fb8`.
- [x] No open PRs remain unmerged or unreviewed.
- [x] GitHub Issues and `docs/work-items/BOARD.md` agree. Only #15/#33 remain open.
- [x] Every open issue is one of:
  - [x] explicit release blocker (#33),
  - [ ] accepted release deferral,
  - [ ] post-release backlog.
- [x] #11 has a release decision: capability spike closed; current release choice is single-track offline MVP acceptance vs collection-download/real-watch deferral.
- [x] #15 is updated with final evidence/comment; remains open only to track #33 physical-watch release validation.

### 2. Build, lint, and tests

Run from repo root:

```bash
source scripts/env-android.sh
bash ./gradlew lintDebug assembleDebug testDebugUnitTest --no-daemon
git diff --check
```

Required disposition:

- [x] `assembleDebug` passes. 2026-06-17 final gate: `lintDebug assembleDebug testDebugUnitTest` passed.
- [x] `testDebugUnitTest` passes. 2026-06-17 final gate: `lintDebug assembleDebug testDebugUnitTest` passed.
- [x] `git diff --check` passes. 2026-06-17 final gate passed.
- [x] `lintDebug` either passes or every remaining warning/error is linked to an issue/explicit rationale below. 2026-06-17 final gate passed.

Current known lint disposition backlog:

- [x] `UnsafeOptInUsageError` in debug-only `OfflineProofService.kt` resolved with debug-source file-level Media3 `UnstableApi` opt-in; `:app:lintDebug` passes.
- [x] `ExportedService` warning resolved/suppressed/documented via #17.
- [x] `WearRecents` / task-affinity warnings resolved/suppressed/documented via #16.
- [x] `GradleDependency` warnings either updated or intentionally deferred with dependency-bump issue/rationale. Current `lintDebug` passes with no release-blocking GradleDependency finding.

### 3. Debug/proof-only code audit

- [x] Search for debug-only/proof code:

```bash
rg -n "OfflineProof|debug-only|BuildConfig.DEBUG|Untidy Test|faux|proof|TODO|FIXME|HACK" app core docs -S
```

- [x] `OfflineProofService` is only available in debug source sets or otherwise impossible to trigger in release (`app/src/debug/AndroidManifest.xml`, `app/src/debug/java/...`).
- [x] No debug proof endpoint/activity/service is exported in release. Debug proof components are under `app/src/debug/` only.
- [x] `New test playlist` create+add row is impossible in release builds (#22).
- [x] No test/faux playlist creation can be triggered by normal release users. The row is gated by `BuildConfig.DEBUG`.
- [x] No logs print tokens, licenses, download URLs, manifest URLs, encryption keys, or private user data in reviewed proof artifacts/log paths.
- [x] Any retained debug artifacts are documented as non-release tooling.

### 4. Runtime validation — emulator

Use the self-healing emulator path when needed:

```bash
source scripts/env-android.sh
UNTIDY_EMULATOR_INSTALL_LAUNCH=1 scripts/ensure-wear-emulator.sh
```

Capture final artifacts under `reports/release-readiness-YYYY-MM-DD/`.

Required emulator smoke paths:

- [x] Clean install and launch. 2026-06-17 `ensure-wear-emulator.sh` installed/launched on emulator-5554.
- [ ] Signed-out/onboarding path, if auth state is cleared. Deferred to #33 real-watch/manual validation because current emulator is authenticated and preserving auth state is useful for release smoke.
- [x] Authenticated home loads without crash. Fresh route XML captured under `reports/release-readiness-2026-06-17-final/home.xml`.
- [x] Search entry shows visible Wear prompt, not blank black screen (#20). Fresh route XML captured under `reports/release-readiness-2026-06-17-final/search.xml`.
- [ ] Search results render and primary result opens. Covered by prior authenticated emulator evidence; final physical validation tracked by #33.
- [ ] Library category list and selected category render inside round safe area (#21). Covered by prior UX/runtime evidence; final physical validation tracked by #33.
- [ ] Album screen play-all/start-track path works. Covered by prior emulator evidence; final physical validation tracked by #33.
- [ ] Playlist screen play-all/start-track path works. Covered by prior emulator evidence; final physical validation tracked by #33.
- [ ] Now Playing opens and basic controls are visible. Covered by prior emulator evidence; final physical validation tracked by #33.
- [ ] Queue screen opens and does not trap the user. Covered by prior emulator evidence; final physical validation tracked by #33.
- [ ] View Album and View Artist routes from Now Playing remain authenticated and do not fall into onboarding. Covered by prior emulator evidence; final physical validation tracked by #33.
- [ ] Add to Playlist existing-playlist path works against an approved test playlist or is explicitly deferred. Live API path was validated with faux playlist; final physical validation tracked by #33.
- [x] Debug-only `New test playlist` row is absent in release-equivalent build and present only in debug if intentionally retained.
- [x] Offline/download shipped/proof state matches the #11 release decision: #11 is closed, #25/#26 carry the accepted `usage=DOWNLOAD` proof/MVP evidence, and remaining UX scope is tracked separately.
- [x] Settings/account/playback/download sections render. Fresh route XML captured under `reports/release-readiness-2026-06-17-final/settings.xml`; full back-behavior sweep remains part of #33 real-watch validation.
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
- [x] #23 Now Playing action discoverability/rotary behavior validated/closed with emulator evidence; real rotary/crown validation remains part of #33.
- [x] #24 retry/error recovery implemented and closed done.
- [x] No remaining user-visible copy says “Coming soon” for a path that is actually proof-in-progress or debug-only. Production proof copy toast and Settings legal/attribution placeholders fixed; static grep passed.
- [x] Disabled/proof-in-progress states are honest, concise, and watch-readable; proof-facing production copy removed/fixed in release polish.

### 7. Documentation readiness

- [x] README accurately describes what Untidy currently supports. Refreshed with single-track offline MVP, Downloads shelf/local cleanup, and real-watch-pending scope.
- [x] README or docs identify known limitations. Current limitations section added.
- [x] Offline/download docs use the corrected framing: sanctioned SDK/API proof, not permission-blocked in principle (#18).
- [x] Guardrail remains documented: do not implement offline by caching `PLAYBACK` / `STREAM` manifests.
- [x] Emulator SELinux wrapper docs are current and do not imply persistent SELinux changes.
- [x] Test plan/checklist links to latest runtime artifacts under `reports/release-readiness-2026-06-17/` and `reports/release-readiness-2026-06-17-final/`.
- [x] Work item board has no stale `Next:` text except #15/#33 release-gate state.

### 8. Security/privacy/account safety

- [x] No secrets are committed or logged in artifacts in reviewed release/proof artifacts.
- [x] Live TIDAL library mutations are limited to approved test/faux playlist behavior.
- [x] Created test playlists are preserved; local downloaded content deletion is allowed and remote TIDAL deletion is not introduced.
- [x] Exported service posture is documented (#17).
- [x] Debug-only proof services cannot be triggered in release; release compile passed and debug components are source-set scoped.
- [x] Auth tokens are stored/used only through existing auth repository patterns.

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

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

**Primary active blocker:** #11 / UNTIDY-011 downloads/offline playback proof. Ralph is actively proving the sanctioned TIDAL offline/download path. The release decision must either:

1. ship with offline/download scoped out and clearly neutralized/documented, or
2. ship after a one-track offline MVP is proven and gated appropriately.

**P1 UX items currently in review:**

- #20 / UNTIDY-019 — Search entry no longer presents a blank black screen; visible Wear prompt landed.
- #21 / UNTIDY-020 — Round-screen safe-area padding landed.
- #22 / UNTIDY-021 — New test playlist creation is gated to debug builds.

**Open release-polish items still needing disposition:**

- #16 / UNTIDY-015 — WearRecents/task-affinity lint warnings.
- #17 / UNTIDY-016 — ExportedService lint warning/security posture.
- #18 / UNTIDY-017 — Offline/download docs normalization.
- #23 / UNTIDY-022 — Now Playing actions discoverability and rotary behavior.
- #24 / UNTIDY-023 — Watch-friendly retry/error recovery.

## Release gate checklist

### 1. Source control and work item state

- [ ] `main` is clean: `git status --short --branch` shows no local changes.
- [ ] No open PRs remain unmerged or unreviewed.
- [ ] GitHub Issues and `docs/work-items/BOARD.md` agree.
- [ ] Every open issue is one of:
  - [ ] explicit release blocker,
  - [ ] accepted release deferral,
  - [ ] post-release backlog.
- [ ] #11 has a release decision: offline shipped, debug-only proof retained, or offline scoped out.
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

- [ ] `UnsafeOptInUsageError` in debug-only `OfflineProofService.kt` resolved or suppressed with debug-only rationale before release gate.
- [ ] `ExportedService` warning resolved/suppressed/documented via #17.
- [ ] `WearRecents` / task-affinity warnings resolved/suppressed/documented via #16.
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
- [ ] Offline/download proof-in-progress or shipped state matches the #11 release decision.
- [ ] Settings/account/playback/download sections render and back behavior works.
- [ ] Error/empty states do not create dead ends; #24 disposition recorded.

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
- [ ] #22 New test playlist gating reviewed and accepted.
- [ ] #23 Now Playing action discoverability/rotary behavior validated or explicitly deferred.
- [ ] #24 retry/error recovery implemented or explicitly deferred.
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
- #11 offline/download decision;
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

1. Let Ralph continue #11 until the sanctioned offline provisioning source is either found or release-scoped out with evidence.
2. Resolve #18 docs normalization soon; it is low-risk and prevents stale framing from leaking into the release docs.
3. Dispose #16/#17 lint warnings before the final full `lintDebug` gate.
4. Review/close #20–#22 if the current implementation evidence is accepted.
5. Run #23 real-watch/rotary validation before public beta.
6. Implement or explicitly defer #24 retry/error recovery.

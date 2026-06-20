# Download release UX audit — 2026-06-20

Repo: `/home/riclewis/.openclaw/workspace/projects/Untidy`  
Commit inspected: `3ae8f4c` (`main...origin/main`)  
Scope: public-release polish for current-track download/management and related Downloads/Home/Settings surfaces. No code changes were made.

## Executive summary

The current release UX is honest about broad offline limitations, but it is inconsistent across surfaces and risks feeling broken:

- **Now Playing release builds always show `Offline unavailable`**, even for tracks already marked downloaded, because `initialDownloadState()` returns `Unavailable` before checking local downloaded state (`TidalPlayerScreen.kt:811-815`). This conflicts with the Downloads shelf and Track Context.
- **The action sheet has no release-visible path to download a current track**, but the Downloads empty state says `Download from Now Playing` (`DownloadsScreen.kt:161-167`). That is the most important copy mismatch to fix before public release.
- **Album/playlist detail pages show active-looking `Download album` / `Download playlist` buttons**, but tapping only toasts `... coming after track MVP` (`AlbumScreen.kt:155-163`, `PlaylistScreen.kt:154-162`). This reads like a live affordance rather than a deliberate deferral.
- **Remove flows are mostly good**: Now Playing has a confirm row, Downloads has per-item confirmation, Settings has remove-all confirmation. Copy should be tightened so users understand deletion is local-only and remote TIDAL content is untouched.
- **The validated `v0.5.0-beta.1` pull-over interaction should not be structurally touched.** Fixes should be confined to labels/state passed into `ActionsSheet`, row enabled/disabled behavior, and copy; do not move the grip, change long-press/drag thresholds, or add new gestures.

Recommended public-release posture: ship downloads as **management/playback of already-saved local proof/MVP tracks only**, unless a real release download path is enabled and validated. If current-track downloads are not fully ready in release, Now Playing should say `Download unavailable` / `Already on watch` as state, and Downloads empty state should say `No downloads on this watch` / `Downloads are not available in this release` instead of instructing users to download from Now Playing.

## UX audit findings by surface

### 1. Now Playing pull-over action sheet

Relevant code:

- `ActionsSheet.kt:54-58` defines only `Unavailable`, `NotDownloaded`, `Downloading(progress)`, `Downloaded`.
- `ActionsSheet.kt:163-181` renders the Download row enabled for any state except `Unavailable`; `Downloaded` opens a remove-confirm sub-flow.
- `ActionsSheet.kt:260-264` labels states as `Offline unavailable`, `Download`, `Downloading N%`, `Downloaded`.
- `TidalPlayerScreen.kt:147-166` only starts the debug proof activity; release builds toast `Offline downloads are not available in this build`.
- `TidalPlayerScreen.kt:811-815` makes release builds `DownloadState.Unavailable` before checking `isOfflineTrackDownloaded(track.id)`.

Findings:

- **P0 copy/state mismatch:** release Now Playing cannot show `Downloaded` for an already downloaded track. This blocks the most important management path: removing the current track from the sheet while playing/viewing it.
- **P1 state model is too coarse for release polish:** `Unavailable` conflates at least three user meanings: app-wide release disabled, track not eligible, and not-yet-validated offline system. Those need different user copy even if they share disabled behavior.
- **P1 no detail text for the Download row:** action rows are single-line with optional icon. `Offline unavailable` is honest but not explanatory; the only explanation appears via toast if the user can tap, but the row is disabled in `Unavailable`.
- **Good:** the remove flow is compact and local-only oriented: `Keeps it in TIDAL` (`ActionsSheet.kt:175-178`) then `Remove download` / `Cancel` (`ActionsSheet.kt:184-205`).
- **Good:** `Downloading N%` has smaller font (`ActionsSheet.kt:288-291`), which is watch-appropriate.

Minimum release changes:

1. Compute downloaded state before app-wide unavailable: already-downloaded tracks should show `Downloaded` in release.
2. If release does not support starting downloads, do not show a misleading enabled `Download` action. Show disabled copy such as `Download unavailable` or `Stream only`.
3. Keep the same row position and sheet structure to preserve the validated muscle memory.
4. Consider a one-line action-message when user opens the sheet and state is unavailable only if it does not add motion/layout churn. Otherwise keep it silent and make copy self-explanatory.

### 2. Current-track download start path

Relevant code:

- `TidalPlayerScreen.kt:129-145` simulates/monitors debug proof completion and transitions progress to downloaded.
- `TidalPlayerScreen.kt:147-166` release builds do not start a download; debug builds launch `OfflineProofActivity`.
- `TrackContextScreen.kt:233-245` has the same debug-only download start helper.

Findings:

- **P0 public release should not expose a start-download affordance unless it actually works in release.** Right now the user can see `Download` only when debug/proof-eligible; release Now Playing sees `Offline unavailable`. That is honest, but it conflicts with Downloads copy and album/playlist copy.
- **P1 progress state is proof-only and time-bounded.** It polls for `offline-proof/latest.json` for ~36 seconds and then says `Offline download did not finish`. That is acceptable for debug, but release needs actual queued/paused/failed states if downloads are enabled.
- **P1 no failed state in `DownloadState`.** The collection summary supports failures, but the current-track sheet cannot show `Download failed`, `Retry download`, `Waiting for Wi-Fi`, or `Storage full`.

Minimum release changes:

- If no release download start: current track state should be one of:
  - `Downloaded` → enabled remove path.
  - `Download unavailable` → disabled, no toast needed.
  - `Track unavailable` → disabled for fixtures/invalid IDs.
- If release download start is enabled: introduce a real state model (below) and wire it to persisted/download-manager state, not debug proof polling.

### 3. Downloads shelf/screen

Relevant code:

- `DownloadsScreen.kt:37-49` loads persisted local downloaded-track summaries.
- `DownloadsScreen.kt:65-67` shows an empty state.
- `DownloadsScreen.kt:70-76` plays a downloaded item.
- `DownloadsScreen.kt:78-91` provides per-track `Remove download`.
- `DownloadsScreen.kt:93-123` confirms `Remove local copy?` / `Cancel`.
- `DownloadsScreen.kt:145-157` summary chip shows `Tracks` / `N downloaded`.
- `DownloadsScreen.kt:161-167` empty state says `No downloads yet` / `Download from Now Playing`.

Findings:

- **P0 empty copy is wrong for a release where Now Playing cannot download.** It points users to a path that shows `Offline unavailable`.
- **P1 downloaded rows lack storage/status detail.** For public release this is acceptable for a narrow MVP, but the screen should make clear it is local watch content.
- **P1 removal confirmation is good but could be clearer.** `Remove local copy?` + `Remove download` is okay; better secondary would be `Keeps TIDAL library` for consistency with Now Playing and Settings.
- **P2 summary chip is tappable only to refresh.** That is fine, but not discoverable. It is harmless and should not block release.

Minimum release changes:

- Empty release copy if downloads cannot be created: `No downloads on this watch` / `Downloads are unavailable in this release`.
- Empty release copy if single-track release download is enabled: `No downloads yet` / `Use Now Playing to save a track`.
- Per-row secondary fallback should prefer `On watch` or artist; avoid implying full offline licensing breadth if only proof/MVP.

### 4. Home surface

Relevant code:

- `MainActivity.kt:470-475` renders Home `Downloads` with secondary `Saved on watch`.

Findings:

- **Good:** `Saved on watch` is concise and truthful if the screen is primarily a local shelf.
- **P2 if release has zero creation path:** still acceptable as a management/playback shelf for preexisting local content, but it may feel empty. The Downloads screen empty copy should carry the explanation, not Home.

Recommended copy:

- Keep Home: `Downloads` / `Saved on watch`.
- If no release downloads at all and there will never be preloaded downloads, consider `Downloads` / `Offline status` or keep current for roadmap consistency.

### 5. Settings Downloads section

Relevant code:

- `SettingsScreen.kt:173-185` shows Manage downloads and disabled settings.
- `SettingsScreen.kt:183-185` copy: `Download quality` / `Battery Saver · track MVP`, `Download over LTE` / `Wi-Fi recommended · deferred`, `Storage limit` / `Manual cleanup for MVP`.
- `SettingsScreen.kt:186-217` remove-all downloads with confirmation.
- `SettingsScreen.kt:353-362` summary: `No downloads`, `1 track`, `$count tracks`, with bytes when present.

Findings:

- **P1 disabled setting copy is implementation-speak.** `track MVP`, `deferred`, and `MVP` are internal/product terms. Public release should use user-facing copy.
- **Good:** Manage downloads summary and remove-all confirmation are appropriate.
- **P1 remove-all lacks failure feedback.** If `removeAllOfflineTrackDownloads()` returns false, nothing is shown. This is not a release blocker but should be polished if touched.

Recommended public copy:

- `Download quality` → `Uses watch-friendly quality` or `Fixed for this release`.
- `Download over LTE` → `Wi‑Fi only for now`.
- `Storage limit` → `Manual cleanup for now`.
- `Remove all downloads` secondary → `Local files only` is good.
- Confirm label/secondary → `Remove local copies?` / `Keeps TIDAL library` is good.

### 6. Track Context from Recent

Relevant code:

- `TrackContextScreen.kt:174-183` shows `Downloaded` or `Download`; secondary is `On watch` or `Offline unavailable in release`.
- `TrackContextScreen.kt:233-245` release builds toast `Offline downloads are not available in this build` if tapped.

Findings:

- **P1 mismatch:** The row label says `Download` while the secondary says `Offline unavailable in release`. The row is still clickable and will toast another unavailable message. This is too many contradictory signals.
- **Good:** already-downloaded state shows `Downloaded` / `On watch`.

Minimum release change:

- For non-downloaded release tracks, label should be `Download unavailable` or `Offline unavailable`; row should be disabled or no-op with one clear toast, not `Download`.

### 7. Album and playlist detail download rows

Relevant code:

- `AlbumScreen.kt:155-163`, `PlaylistScreen.kt:154-162` render collection download chips.
- `AlbumScreen.kt:285-307`, `PlaylistScreen.kt:300-323` map collection summary to labels/details/toasts.
- Non-downloaded collection default label is currently `Download album` / `Download playlist`; secondary says `Tracks save one at a time for now`; tap toast says `Album/Playlist download is coming after track MVP`.

Findings:

- **P0/P1 public polish issue:** Active-looking `Download album` / `Download playlist` rows imply functionality. The secondary and toast reveal it is not implemented. This creates disappointment and can be read as broken.
- **Good:** partial/downloaded/failure summaries are useful if local subsets exist.

Minimum release changes:

- For `downloadedCount <= 0`, change collection action label to `Album download unavailable` / `Playlist download unavailable` or `Save tracks one at a time` only if a real one-track save path exists.
- Prefer disabled visual state if collection downloads are deferred. If the custom row cannot be disabled trivially, copy must do the work: `Track downloads only` / `Use Now Playing for single tracks` (if true) or `Not available in this release`.
- Remove internal phrase `track MVP` from user-visible toast.

### 8. Offline/fallback playback messaging

Relevant code:

- `MainActivity.kt:231-234` toast: `Not downloaded · connect to stream this track`.
- `MainActivity.kt:710-711` allows playback only when network is available or track is marked downloaded.
- `DiscoverScreen.kt` and `SearchScreen.kt` show Downloads shortcuts when offline.
- `LibraryScreen.kt` shows `Downloads available` when offline.

Findings:

- **Good:** fallback copy is concise and honest.
- **Good:** offline Search/Discover provide an `Open Downloads` route.
- **P1 consistency:** if a marked downloaded track cannot actually play offline due to license/cache expiry, the UI currently has no visible expired/renew state. If that failure exists in backend, it should surface as `Download expired. Connect to renew.` rather than generic playback failure.

## Recommended copy and state machine for current-track download

### Recommended state machine

Use this as the public-facing item state model, even if implementation initially maps several internal states to the same disabled row.

| State | Entry condition | Primary row label | Secondary/detail copy | Action |
| --- | --- | --- | --- | --- |
| `NotDownloadedReleaseUnavailable` | release cannot start downloads | `Download unavailable` | `Not in this release` or no secondary if row has no room | Disabled/no-op |
| `TrackUnavailable` | no valid TIDAL track id/fixture/current placeholder | `Offline unavailable` | `Track unavailable` | Disabled/no-op |
| `NotDownloaded` | release can start a track download | `Download` | `Save on watch` | Start validation/download |
| `Checking` | entitlement/storage/policy check running | `Checking…` | optional `Preparing download` | Disabled |
| `Queued` | waiting for scheduler | `Queued` | optional `Waiting to start` | Tap opens status/cancel only if implemented |
| `WaitingForWiFi` | policy blocks LTE/metered | `Waiting for Wi‑Fi` | `Downloads use Wi‑Fi` | Open Settings/Downloads if available |
| `Downloading(progress)` | active bytes/proof transfer | `Downloading 42%` | progress indicator if available | Disabled or opens status |
| `Finishing` | validating/license/finalizing | `Finishing…` | optional | Disabled |
| `Downloaded` | valid local asset/marker | `Downloaded` + check | `On watch` if secondary exists | Open remove confirm |
| `Failed(reason)` | recoverable failure | `Download failed` | `Tap to retry` or reason | Retry |
| `StorageFull` | cannot reserve bytes | `Storage full` | `Manage downloads` | Open Downloads/Settings |
| `Expired` | local asset/license expired | `Download expired` | `Connect to renew` | Renew when online |
| `Removing` | delete in progress | `Removing…` | optional | Disabled |

### Minimum public-release mapping if offline playback/download is not fully ready

If the app does **not** support user-created release downloads:

- Existing locally marked track: `Downloaded` → remove confirm.
- Non-downloaded valid track: `Download unavailable` → disabled.
- Invalid/fixture track: `Offline unavailable` → disabled.
- Do not show `Download`, `Queued`, or `Downloading` in release unless the release path actually creates a playable local asset.

### Recommended watch copy

- Current non-downloaded release track: `Download unavailable`.
- Current downloaded track: `Downloaded`; confirm row `Remove download`; helper/message `Keeps it in TIDAL`.
- Remove success: `Download removed`.
- Remove failure: `Couldn’t remove download`.
- Offline playback rejection: current `Not downloaded · connect to stream this track` is good.
- Expired local item, if surfaced: `Download expired · connect to renew`.
- Downloads empty when no creation path: `No downloads on this watch` / `Downloads are not available in this release`.
- Downloads empty when single-track creation ships: `No downloads yet` / `Use Now Playing to save a track`.

## What should be visible in release if offline playback is not fully ready

Recommended visible release scope:

1. **Home:** keep `Downloads` / `Saved on watch`.
2. **Downloads screen:** show local saved-track shelf and local removal controls; empty state must not promise unavailable creation.
3. **Now Playing:** show `Downloaded` and remove-confirm for tracks already on watch; show disabled `Download unavailable` for non-downloaded tracks.
4. **Track Context:** same as Now Playing: `Downloaded` / `On watch`, otherwise `Download unavailable` / `Not in this release`.
5. **Album/playlist:** do not show active `Download album` or `Download playlist` unless collection download works. Show `Track downloads only` only if current-track downloads work; otherwise `Download unavailable` / `Not in this release`.
6. **Settings:** show Manage downloads and local cleanup. Keep policy controls disabled with public copy: fixed quality, Wi‑Fi-only later, manual cleanup.
7. **Offline Search/Discover/Library:** keep shortcuts to Downloads. This is useful and already aligned with local shelf behavior.

Do **not** show in public release unless actually implemented and validated:

- `Download` action that only launches debug proof.
- `Downloading N%` backed by proof polling rather than production transfer state.
- Album/playlist download calls-to-action that only toast `coming after track MVP`.
- Internal terms: `MVP`, `proof`, `deferred`, `debug`, `faux`, `coming after track MVP`.

## Risks to the validated pull-over interaction

The `v0.5.0-beta.1` snapshot specifically validated the Now Playing pull-over menu interaction. Avoid these regressions:

- **Do not move the bottom grip or sheet handle.** `BottomGripPullOverHandle` uses long-press drag (`TidalPlayerScreen.kt:555-579`), and `ActionsSheet` close handle mirrors it (`ActionsSheet.kt:99-123`). These are the validated interaction mechanics.
- **Do not change open/close thresholds** (`TidalPlayerScreen.kt:479-484`, `TidalPlayerScreen.kt:646-649`) as part of download polish.
- **Do not add extra nested gestures inside the sheet** for download status. Use simple rows and existing scroll behavior.
- **Avoid adding tall explanatory copy near the top of the sheet** that changes scroll position or hides known actions. Prefer short labels and existing `actionMessage` if needed.
- **Keep row order stable** unless there is a compelling release reason. Users just validated the menu; the safest change is label/state only.
- **Avoid a modal dialog for single-track remove.** The current inline confirm row is watch-appropriate and low-risk.

## Test checklist for watch/emulator

### Static/source checks

- [ ] Release build grep has no user-visible `MVP`, `proof`, `debug-only`, `Coming soon`, `coming after track MVP`, or fake download copy in main source.
- [ ] `Download` label is not visible in release for a non-downloaded current track unless release download creation is enabled.
- [ ] Already-downloaded current tracks can render `Downloaded` in release state computation.
- [ ] Album/playlist download rows do not look enabled for unsupported collection downloads.
- [ ] `git diff --check` passes after any copy/state edits.

### Emulator smoke

- [ ] Install/launch release or release-equivalent build.
- [ ] Home shows Downloads and opens Downloads screen.
- [ ] Empty Downloads copy matches chosen release posture.
- [ ] With no network, Search/Discover/Library route to Downloads and do not dead-end.
- [ ] Start a known streamable track while online; open Now Playing.
- [ ] Long-press/drag bottom grip opens pull-over sheet exactly as in `v0.5.0-beta.1`.
- [ ] Non-downloaded current track shows disabled/unavailable download copy; no fake progress.
- [ ] View album, view artist, queue, output, and add-to-playlist rows still work/behave as before.
- [ ] Back/drag-down closes the sheet with the validated handle behavior.
- [ ] If a local downloaded marker/cache exists, Now Playing shows `Downloaded`; tapping opens remove confirm; Cancel keeps state.
- [ ] Remove download removes only local state and refreshes Now Playing/Downloads.
- [ ] Settings → Manage downloads opens the shelf; remove-all confirmation works and refreshes summary.

### Physical watch validation

- [ ] Repeat pull-over open/close on the target real watch with finger, not mouse.
- [ ] Crown/rotary still adjusts volume when sheet closed and scrolls/actions behaves naturally when sheet open.
- [ ] Sheet handle remains below the Wear universal settings pulldown zone.
- [ ] Confirm/remove rows are reachable on round display without clipping.
- [ ] Bluetooth/output settings action still opens Wear settings and returns safely.
- [ ] Offline/network-off playback of a marked downloaded track either works or fails with clear copy (`Download expired · connect to renew` / `Not downloaded · connect to stream this track`).
- [ ] Screen-off/background playback remains unaffected after download UI copy/state changes.

## Recommended minimum change set before public release

1. **Fix release state precedence:** already-downloaded current track should show `Downloaded` before app-wide download-start availability is considered.
2. **Align empty Downloads copy** with actual release ability.
3. **Replace internal Settings copy** (`track MVP`, `deferred`, `MVP`) with user-facing terms.
4. **Make Track Context non-downloaded release row say `Download unavailable`** and disable/no-op it rather than `Download` + unavailable secondary.
5. **De-emphasize or disable album/playlist download rows** unless collection downloads are genuinely supported; remove `coming after track MVP` toast.
6. **Do not touch the pull-over gesture code.** Limit Now Playing changes to download state calculation/copy passed into the existing sheet.

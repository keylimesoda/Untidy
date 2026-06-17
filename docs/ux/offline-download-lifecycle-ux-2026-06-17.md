# Offline / Download Lifecycle UX — Wear OS Product Design Report

Date: 2026-06-17  
Scope: Untidy Wear OS TIDAL app offline/download lifecycle UX before full implementation  
Status: Reviewable UX proposal; no production code changes in this pass

## 1. Executive summary / recommended UX model

Untidy should treat offline as a **small, dependable watch shelf**, not a full phone-style download manager. The recommended model is:

- **One primary place to use offline content:** a top-level **Downloads** entry from Home, with the same row/chip grammar as Library.
- **One primary place to manage offline content:** **Downloads** plus a short **Manage storage** path from Settings.
- **Contextual download/remove actions where the user discovers music:** Now Playing for tracks; Album and Playlist detail headers for collections.
- **Offline fallback is automatic, not a separate app mode:** when network is unavailable, Home should bias toward Downloads and Library should show cached/downloaded surfaces first. A Settings toggle can exist later as `Offline only`, but MVP should avoid a mode switch that users must remember.
- **Deletion/removal means Untidy-local content only:** copy must say `Remove download`, never `Delete track`, `Delete album`, or `Delete playlist`. Ric has approved removal of local downloaded content; that approval does **not** apply to TIDAL library or playlist deletion.

The current #26 debug-gated track download path is a good technical proof seam, but not yet a product UX. Before implementation, design should explicitly separate:

1. **Download state** — local asset/progress/license/cache status.
2. **Library state** — whether the user saved the item in TIDAL.
3. **Playback source** — local cache/download vs stream.
4. **Collection completeness** — all, partial, failed, expired, or unavailable children.

Top recommendation: ship the first product version as **single-track download + Downloads shelf + remove download**, then add album/playlist group downloads as the next phase. Album/playlist actions can be designed now, but collection-wide download is the point where partial failures, storage pressure, and renewal become user-visible.

## 2. Core principles for Wear OS offline downloads

1. **Glanceable truth beats feature breadth.** Users should understand in one row whether an item is downloaded, downloading, waiting, failed, expired, or unavailable.
2. **Short-session safe.** Every action should complete as a one-tap intent with background progress, not a long form. The watch is a place to start, resume, remove, or inspect status quickly.
3. **Round-screen compactness.** Prefer chips and short rows. Avoid phone-style multi-select grids, dense tables, checkboxes, long modal forms, or nested storage trees.
4. **Rotary and swipe friendly.** Download and manage lists should be regular scrollable Wear lists with enough circular safe-area padding and rotary focus, consistent with Library/Settings.
5. **Policy defaults protect the watch.** Default to Wi-Fi only, Battery Saver download quality, storage cap, one active download group, and pause on low battery/LTE unless explicitly allowed.
6. **Offline playback is automatic source resolution.** A downloaded track should play from local storage without the user choosing a separate player.
7. **Partial is normal.** Albums/playlists will have unavailable tracks, failures, expired licenses, or storage limits. The UI needs a first-class partial state, not a hidden error.
8. **Removal is local and reversible where feasible.** Single-track removal should be quick with undo/status. Collection removal should confirm.
9. **No raw URL or secret exposure.** UX and debug surfaces should never display TIDAL URLs, tokens, license blobs, or raw manifest details.
10. **No destructive TIDAL mutations.** Untidy may remove local downloaded bytes/metadata/licenses; it must not delete TIDAL playlists, library saves, or remote playlist membership through this offline UX.

## 3. Full lifecycle stories

### 3.1 Track download lifecycle

**Discovery points**

- Now Playing action sheet: primary MVP entry.
- Library > Tracks row overflow/action affordance: later phase if row space allows.
- Search/album/playlist track rows: later phase; avoid overloading tiny rows in MVP.

**Recommended MVP flow from Now Playing**

1. User opens Now Playing.
2. User swipes/taps into Actions.
3. Row shows one of:
   - `Download`
   - `Queued`
   - `Waiting for Wi-Fi`
   - `Downloading 42%`
   - `Finishing…`
   - `Downloaded`
   - `Download failed`
   - `Expired`
   - `Offline unavailable`
4. User taps `Download`.
5. App validates entitlement, network policy, storage, battery, and local duplicate state.
6. Row changes immediately to `Queued` or `Downloading 0%`.
7. If user leaves the player, progress remains visible in Downloads shelf and Settings usage.
8. On success, row becomes `Downloaded` with checkmark.
9. Later, playback chooses local source automatically when the track is valid locally.

**Downloaded track action**

- Tapping `Downloaded` should open a tiny action choice, not re-download silently:
  - `Remove download`
  - `Cancel`
- If there is no room for a second sheet in MVP, make the row label `Remove download` once downloaded, but this is less discoverable than showing the state plus action choice.

**Currently playing removal behavior**

- If the downloaded track is currently playing from local storage, copy should be:
  - `Remove after play?`
  - `Removes local copy when this track stops.`
- MVP may block removal while actively playing with `Stop playback first`; later phase can queue removal.

### 3.2 Album download lifecycle

**Discovery point**

- Album detail screen, directly below or paired with `Play Album`.

**Recommended header layout**

- Keep artwork hero.
- Under hero, use two stacked or horizontally balanced chips depending screen size:
  - `Play Album`
  - `Download album` / state row

For round screens, prefer stacked 48 dp chips over cramming two tiny buttons side by side.

**Flow**

1. User opens album detail.
2. Header shows album-level download state:
   - `Download album`
   - `Queued 0/12`
   - `Downloading 7/12`
   - `Downloaded 12/12`
   - `Partial 9/12`
   - `3 failed`
   - `Offline unavailable`
3. User taps `Download album`.
4. App resolves playable tracks and creates a collection download group.
5. Download progress is by **track count first** (`7/12`), bytes only in detail/settings.
6. Track rows may show small state adornments later, but MVP collection UI can rely on the header summary to avoid clutter.
7. If some tracks are unavailable, album state becomes `Partial`, with a tap detail: `9 downloaded · 2 unavailable · 1 failed`.

**Partial album rules**

- `Downloaded` only if all playable tracks in the resolved album are valid locally.
- `Partial` if at least one track is downloaded but not all playable tracks are valid.
- Unavailable/explicit-filtered tracks should not block `Downloaded` if they are not playable under current catalog preference, but they should be counted in details if known.

### 3.3 Playlist download lifecycle

Playlist download should mirror album download but with stronger staleness semantics because playlists change.

**Discovery point**

- Playlist detail screen below `Play all`.

**Flow**

1. User opens playlist detail.
2. Header shows `Download playlist`.
3. User taps download.
4. App snapshots current playlist membership/order into a download group.
5. Header shows `Downloading 18/47` or `Downloaded 47/47`.
6. If playlist changes online later, local snapshot remains playable and the UI shows `Update available` when online.

**Playlist-specific states**

- `Downloaded` — current known snapshot is fully downloaded.
- `Partial` — some tracks in snapshot are downloaded.
- `Update available` — online playlist membership differs from local snapshot.
- `Updating…` — app is downloading newly added tracks and optionally removing old ones only if user chooses cleanup.

**Important UX choice**

Do **not** silently delete local tracks removed from a TIDAL playlist unless the user confirms. On a watch, silent cleanup can feel like lost music. Later Settings can offer `Clean removed playlist tracks` as a storage tool.

### 3.4 Offline content discovery

**Home**

When signed in, Home should include an enabled Downloads chip once at least one local item or active download exists:

- Label: `Downloads`
- Secondary examples:
  - `3 albums · 42 tracks`
  - `Downloading 7/12`
  - `Storage full`
  - `Nothing downloaded` if enabled as an entry point before first download

If no downloads exist and feature is enabled, Home can show Downloads as enabled with `Save music for offline`, not disabled.

**Downloads screen**

Recommended top-level structure:

1. Title: `Downloads`
2. Summary chip: `642 MB of 1 GB` / `Manage storage`
3. Active downloads section if non-empty:
   - `Downloading 7/12`
   - `Waiting for Wi-Fi`
   - `3 failed · Tap to fix`
4. Content sections:
   - `Albums`
   - `Playlists`
   - `Tracks`

Keep it flat. Do not create a phone-style tab bar. Use category chips like Library already does.

**Library integration**

Add a `Downloaded` category/filter inside Library only after the Downloads screen exists. The first durable IA should be Home > Downloads; Library can later show downloaded badges or a downloaded filter.

### 3.5 Offline fallback when network is unavailable

Offline fallback should happen automatically:

- Home remains usable.
- Downloads is promoted/available.
- Search/Discover should not pretend to fetch live content; show `Connect to search TIDAL` and a Downloads shortcut.
- Library can show cached library metadata if available, but must distinguish cached non-downloaded metadata from playable offline content.
- Non-downloaded playback attempt copy: `Not downloaded` / `Connect to stream this track`.
- Expired local playback attempt copy: `Download expired` / `Connect to renew`.

**Optional later setting**

- `Offline only` toggle in Settings or Downloads screen.
- Copy: `Show downloaded music first` or `Offline only`.
- MVP recommendation: do not add this toggle until automatic fallback is proven; mode switches are easy to forget on a watch.

### 3.6 Removal / cleanup lifecycle

Ric has approved deletion/removal of downloaded tracks/content. UX must keep that scoped to Untidy local downloads.

**Single track removal**

- Entry points:
  - Now Playing Actions when state is `Downloaded`.
  - Downloads > Tracks row.
- Copy:
  - Primary: `Remove download`
  - Secondary/status: `Keeps it in TIDAL`
- Confirmation:
  - MVP: quick confirm row is safer than accidental one-tap deletion on a watch.
  - Recommended compact confirm:
    - `Remove local copy?`
    - `Remove download`
    - `Cancel`
- After success: `Download removed`.

**Album/playlist removal**

- Entry point: collection header in Album/Playlist detail and Downloads collection row.
- Confirm required.
- Copy:
  - Album: `Remove album download?` / `Removes 12 local tracks. Keeps album in TIDAL.`
  - Playlist: `Remove playlist download?` / `Removes local snapshot. Keeps playlist in TIDAL.`
- Do not delete playlist itself.
- Do not remove library favorites.

**Bulk cleanup**

Use Settings > Downloads > Manage storage, not a complex multi-select screen.

Recommended cleanup chips:

- `Remove failed downloads`
- `Remove expired downloads`
- `Remove all track downloads`
- `Remove all downloads`

Bulk destructive actions require confirmation. Copy must include local-only distinction:

- `Remove all downloads?`
- `Deletes local files from this watch. Keeps your TIDAL library.`

**Storage management**

Downloads screen and Settings should show:

- `Downloads: 642 MB of 1 GB`
- `Free on watch: 4.2 GB` if available
- Storage limit choices: `500 MB`, `1 GB`, `2 GB`, `Max allowed` later.

Avoid custom byte-entry forms on watch.

## 4. Information architecture proposal

### Home

Current Home already has Search, Discover, Library, disabled Downloads, Settings. Recommended target:

- `Search` / `Now playing` primary action remains unchanged.
- `Downloads` becomes enabled when feature is productized.
- Offline/network-unavailable Home should promote Downloads with secondary copy like `Available offline`.

Target Home rows when signed in:

1. Search or Now Playing/Resume
2. Discover
3. Library
4. Downloads
5. Settings

### Library

Keep current categories: Playlists, Albums, Tracks, Artists. Add offline awareness later in two lightweight ways:

- Badges/state text on rows when already downloaded.
- Optional first category: `Downloaded`, only if it does not duplicate Home > Downloads too much.

Recommendation: do not make Library the primary offline manager in MVP; it mixes TIDAL library and local lifecycle too easily.

### Downloads

New top-level screen. Proposed states:

- Empty: `No downloads yet` / `Download from Now Playing, albums, or playlists.`
- Active: `Downloading 7/12` as first row.
- Normal categories: `Albums`, `Playlists`, `Tracks`.
- Storage row: `642 MB of 1 GB` / `Manage storage`.

### Album / Playlist details

Add collection-level download row below `Play Album` / `Play all`. The current detail screens have the right place for this because both already show a hero followed by a play chip before tracks.

### Now Playing / Actions

Keep Now Playing as the MVP track-level entry. The action row should evolve from debug `DownloadState` to real repository state. When downloaded, the action should expose remove/manage behavior.

### Settings

Current Settings already has a Downloads section. Target rows:

- `Offline playback` — status: `Ready` / `Needs connection` / `Proof in progress` during dev.
- `Download quality` — selected value.
- `Download over LTE` — toggle.
- `Storage limit` — selected cap.
- `Manage downloads` — opens Downloads/manage storage.

Do not leave multiple disabled rows once productized.

## 5. State model and copy for item / collection states

### Track/item state model

| State | Meaning | Primary copy | Secondary/detail copy |
| --- | --- | --- | --- |
| `NotDownloaded` | No valid local asset | `Download` | `Save to this watch` |
| `Checking` | Validating entitlement/policy/storage | `Checking…` | `One moment` |
| `Queued` | Accepted, waiting for scheduler | `Queued` | `Starts soon` |
| `WaitingForWifi` | Policy blocks current network | `Waiting for Wi-Fi` | `LTE downloads off` |
| `WaitingForBattery` | Battery policy pauses work | `Waiting to charge` | `Battery low` |
| `Downloading(progress)` | Bytes active | `Downloading 42%` | `Keep app/watch online` only if needed |
| `Finishing` | Finalizing metadata/license/cache | `Finishing…` | `Almost done` |
| `Downloaded` | Valid local playable asset | `Downloaded` | `Available offline` |
| `FailedRetryable` | Can retry | `Download failed` | `Tap to retry` |
| `Unavailable` | TIDAL/offline unsupported for this item | `Offline unavailable` | `Streaming only` |
| `Expired` | Local asset/license invalid until renewal | `Expired` | `Connect to renew` |
| `Removing` | Local delete in progress | `Removing…` | — |
| `RemovePending` | Current playback blocks immediate deletion | `Remove after play` | `Queued` |

### Collection state model

| State | Meaning | Primary copy |
| --- | --- | --- |
| `None` | No children downloaded | `Download album` / `Download playlist` |
| `Queued` | Group queued | `Queued 0/12` |
| `Downloading` | Some active/in progress | `Downloading 7/12` |
| `PausedPolicy` | Waiting for Wi-Fi/battery/storage | `Waiting for Wi-Fi` / `Storage full` |
| `Downloaded` | All playable children valid locally | `Downloaded 12/12` |
| `Partial` | Some valid, some not | `Partial 9/12` |
| `HasFailures` | One or more failed | `3 failed` |
| `Expired` | All/some local assets expired | `Renew downloads` |
| `Unavailable` | Collection cannot be downloaded | `Offline unavailable` |
| `Removing` | Collection local removal active | `Removing…` |

### Copy rules

- Use `download` for local save actions.
- Use `remove download` for local deletion.
- Avoid `delete` except in explanatory bulk copy: `Deletes local files from this watch`.
- Never say `Delete playlist` or `Delete album`.
- Prefer count progress for collections: `7/12`, not megabytes.
- Prefer short reason phrases: `Wi-Fi required`, `Storage full`, `Expired`, `Tap to retry`.

## 6. Removal model

### Units

Removal units should map to user mental models:

1. **Track download** — one local track asset/license/cache metadata.
2. **Album download group** — all locally downloaded tracks associated with the album group.
3. **Playlist download group** — the local snapshot of playlist tracks downloaded through that playlist.
4. **Failed/incomplete downloads** — temp files and failed queue rows.
5. **Expired downloads** — invalid local assets/licenses.
6. **All downloads** — all Untidy local offline content.

### One-at-a-time vs bulk

- One-at-a-time: available in Now Playing and Downloads rows.
- Collection-level: available in Album/Playlist header and Downloads collection rows.
- Bulk: only in Manage storage; no multi-select grid.

### Confirmations

- Single track: compact confirm recommended; undo is acceptable later if a Wear-friendly snackbar/status pattern exists.
- Album/playlist: confirm required.
- All downloads: confirm required, with local-only copy.
- Failed/temp cleanup: can be one tap because user intent is maintenance and content is not playable; still show completion status.

### No-delete vs delete distinctions

Use this invariant everywhere:

> Untidy removes downloads from this watch. It does not delete music from TIDAL.

Suggested exact strings:

- Single: `Remove local copy? Keeps it in TIDAL.`
- Album: `Remove album download? Keeps album in TIDAL.`
- Playlist: `Remove playlist download? Keeps playlist in TIDAL.`
- All: `Remove all downloads? Deletes local files from this watch. Keeps your TIDAL library.`

## 7. Edge cases

### Partial albums

- Show `Partial 9/12` in album header and Downloads list.
- Tapping state opens a compact breakdown:
  - `9 downloaded`
  - `2 unavailable`
  - `1 failed · Retry`
- Play offline should skip unavailable tracks with a brief status or create a local queue of only valid tracks.

### Partial playlists / changed playlists

- Treat downloaded playlist as a snapshot.
- When online and server playlist changed: show `Update available`.
- User action choices later:
  - `Update downloads`
  - `Keep snapshot`
  - `Remove old tracks` only in cleanup/manage flow.

### Expired or invalid local content

- State: `Expired`.
- Online action: `Renew download`.
- Offline action: show `Connect to renew`.
- If renewal fails due entitlement loss: `Offline unavailable` or `Removed from TIDAL` depending source data.

### Storage full

- During download start: show `Storage full` and actions:
  - `Manage storage`
  - `Cancel`
- If space can be recovered safely from failed/temp files, do that automatically first.
- Do not silently remove explicitly downloaded albums/playlists to make room for a new download unless user has enabled auto-cleanup.

### Sign-out / erase account

- Settings currently has `Erase account` / `Confirm erase`. Once downloads ship, sign-out confirmation must mention downloads:
  - `Erase account? Removes account and downloads from this watch.`
- On sign-out, remove or invalidate local audio/license artifacts. Recommendation: remove all local downloads on sign-out for privacy/licensing clarity.

### Failed downloads

- Retryable failures remain in Downloads active/failure section.
- Failure copy should be reason-specific but short:
  - `Network failed`
  - `Storage full`
  - `Offline unavailable`
  - `Download expired`
  - `Tap to retry`
- Avoid raw HTTP/API messages in UI.

### Network transitions

- Wi-Fi lost during active download: `Waiting for Wi-Fi` if LTE disabled; otherwise continue if LTE allowed.
- User goes offline after download completed: playback local source automatically.
- Queue with mixed local/online tracks offline: play local tracks; for non-local track show/skip with `Not downloaded`.

### Battery / LTE policy

- Defaults:
  - Download quality: Battery Saver.
  - Download over LTE: off.
  - Low battery: pause below threshold unless already nearly complete; exact implementation can be later.
- When user initiates on LTE with LTE disabled: accept intent and show `Waiting for Wi-Fi`, not a hard error.
- Avoid background download concurrency on watch; one active track or collection group at a time is enough.

### Corrupt cache / missing files

- If metadata says downloaded but files/cache missing: state becomes `Expired` or `Download missing`; offer `Redownload` when online.
- Startup cleanup should remove orphan temp files and mark impossible rows failed/removed.

### Currently playing local content removal

- MVP: block with `Stop playback first`.
- Later: `Remove after play` queue.

## 8. Minimum shippable MVP vs later phases

### Minimum shippable offline UX MVP

1. **Track-level download from Now Playing Actions** with real repository state, not debug polling.
2. **Persisted download state** survives app/service restart and watch reboot.
3. **Downloads top-level Home entry** opens a simple Downloads screen.
4. **Downloads screen supports Tracks category**, empty state, active download status, and storage summary.
5. **Remove download for one track** with compact confirmation and local-only copy.
6. **Offline fallback**: if network unavailable, Home/Downloads still work and non-downloaded playback says `Not downloaded`.
7. **Settings Downloads section enabled minimally**:
   - status row
   - storage usage
   - LTE setting if policy implemented
   - manage downloads link
8. **Sign-out removes local downloads** or explicitly warns that it will.
9. **No user-visible raw URLs/secrets/debug proof wording** in release UX.

### Later phase 1 — collection downloads

- Album `Download album` / `Remove album download`.
- Playlist `Download playlist` / `Remove playlist download`.
- Collection group progress and partial state.
- Retry failed child tracks.

### Later phase 2 — robust management

- Storage cap presets.
- Remove failed/expired/all downloads.
- Playlist update detection.
- Renew expired downloads.
- Better local/offline badges across Library/Search.

### Later phase 3 — power-user polish

- Optional `Offline only` setting.
- Auto-cleanup rules.
- Per-collection keep/pin semantics.
- Real-watch battery/storage profiling tuned defaults.

## 9. Recommended GitHub work items to create/modify after Ric reviews

### Modify existing #26 / UNTIDY-025

Refocus as the product MVP rather than debug proof:

- Title suggestion: `Ship single-track offline download UX MVP`
- Add acceptance:
  - Now Playing action state is repository-backed.
  - Downloaded track appears in Downloads screen.
  - Remove download works for one track with local-only confirmation.
  - Offline network fallback shows downloaded track and blocks non-downloaded playback with clear copy.
  - Sign-out cleanup behavior is implemented/documented.

### Create: Downloads screen IA and empty/active states

- Area: ui/offline
- Acceptance:
  - Home Downloads row is enabled when feature is productized.
  - Downloads screen has empty, active, failed, and downloaded-track states.
  - Round-screen padding/rotary behavior follows Library/Settings patterns.

### Create: Download state model and copy contract

- Area: offline/model/ui
- Acceptance:
  - Track and collection state enums cover queued, waiting policy, downloading, finishing, downloaded, failed, unavailable, expired, removing.
  - UI copy is centralized/testable enough to avoid inconsistent wording.
  - No release copy says `proof` or exposes debug implementation details.

### Create: Local removal / cleanup UX

- Area: offline/storage/ui
- Acceptance:
  - Remove one track.
  - Confirm album/playlist/all local removals once collection downloads exist.
  - Manage storage supports failed/expired cleanup.
  - Copy distinguishes local download removal from TIDAL deletion.

### Create: Album/playlist collection download UX

- Area: album/playlist/offline
- Acceptance:
  - Album detail has collection state row below Play Album.
  - Playlist detail has collection state row below Play all.
  - Partial and failure states show count summary.
  - Offline play uses local-valid subset or explains missing tracks.

### Create: Offline fallback / network unavailable behavior

- Area: navigation/playback/offline
- Acceptance:
  - Downloads reachable offline.
  - Search/Discover show connection-required copy and Downloads shortcut.
  - Non-downloaded playback offline says `Not downloaded`.
  - Expired local playback says `Connect to renew`.

### Create or extend: Settings downloads controls

- Area: settings/offline
- Acceptance:
  - Download quality, LTE, storage cap, and manage downloads are real or intentionally gated.
  - Sign-out warning includes download removal once downloads ship.
  - Debug proof rows remain debug-only.

## 10. Specific source references: current support and conflicts

### Current support

- **Home already has a Downloads placement**: `MainActivity.kt:397-403` renders `Downloads` with secondary `Proof in progress`. This is the right top-level IA location, but it is currently disabled.
- **Home uses round-safe vertical scrolling and rotary focus**: `MainActivity.kt:357-363`. Downloads should reuse this pattern.
- **Navigation has no Downloads route yet**: route list includes Home, Discover, Search, Album, Playlist, Artist, Library, Settings, but no Downloads route (`MainActivity.kt:128-139`).
- **Library category pattern is a good template**: `LibraryScreen.kt:99-148` uses a `ScalingLazyColumn`, rotary focus, safe padding, and category rows for Playlists/Albums/Tracks/Artists. Downloads should copy this structure.
- **Album detail has a natural collection-action slot**: `AlbumScreen.kt:117-153` shows hero, `PlayAlbumChip`, then track list. A `Download album` state chip belongs next to/below `PlayAlbumChip`.
- **Playlist detail has the same natural slot**: `PlaylistScreen.kt:114-153` shows header, `PlayAllChip`, then track list. A playlist download state chip belongs next to/below `PlayAllChip`.
- **Settings already has a Downloads section**: `SettingsScreen.kt:164-168` lists Offline playback, Download quality, Download over LTE, Storage limit. These are currently disabled but correctly located.
- **Settings has account erase confirmation**: `SettingsScreen.kt:73-103`. Once downloads ship, this confirmation needs copy that local downloads will be removed too.
- **Now Playing Actions already includes a download row**: `ActionsSheet.kt:96-109` renders the download action with state label and checkmark support.
- **Current action sheet state labels are compact**: `ActionsSheet.kt:161-166` maps Unavailable/NotDownloaded/Downloading/Downloaded to one-line labels, a useful pattern to extend.
- **#26 debug proof persists a minimal track marker**: `TidalPlayerScreen.kt:606-625` uses `offline-downloads` shared prefs to restore `Downloaded`. Product UX needs a real store, but this proves the UI seam.
- **Playback backend already routes marked downloads toward DOWNLOAD manifest/cache**: `DirectManifestPlaybackBackend.kt:171-204` checks `offline-downloads`, opens an app-private cache, and prefers `usage=DOWNLOAD` before stream fallback.

### Current conflicts / gaps

- **Home Downloads row is disabled**, so `onOffline` cannot actually explain or open anything from the disabled chip (`MainActivity.kt:397-403`). Product UX needs it enabled once Downloads screen exists.
- **Copy is inconsistent across surfaces**: Home says `Proof in progress`, Settings says `Download proof in progress`, Actions says `Offline unavailable` (`MainActivity.kt:397-403`, `SettingsScreen.kt:164-168`, `ActionsSheet.kt:161-166`). Product UX should use consistent state-specific copy and remove `proof` from release strings.
- **No album/playlist download actions exist**: Album and Playlist screens only offer play chips and track rows (`AlbumScreen.kt:131-153`, `PlaylistScreen.kt:131-153`).
- **No Downloads route/screen exists**: Home has a row, but Navigation has no Downloads destination (`MainActivity.kt:128-139`, `MainActivity.kt:235-338`).
- **Download state is debug-gated and track-only**: `TidalPlayerScreen.kt:142-160` launches `OfflineProofActivity`; `initialDownloadState` returns unavailable outside debug (`TidalPlayerScreen.kt:606-610`). This must not be the production UX.
- **No removal action exists**: `DownloadState` has no removing/removal states and `ActionsSheet` has no `Remove download` path (`ActionsSheet.kt:46-50`, `ActionsSheet.kt:96-109`).
- **No collection/partial state model exists**: `DownloadState` only covers unavailable, not downloaded, downloading, downloaded (`ActionsSheet.kt:46-50`). Album/playlist lifecycle needs group states.
- **No user-facing offline fallback exists**: Search/Discover/Library are online-first; Downloads is disabled. Offline network-unavailable flows need explicit surfaces and copy.
- **Sign-out does not mention downloads yet**: erase account copy only says `Remove account from watch` (`SettingsScreen.kt:91-103`). Once downloads ship, this is incomplete.
- **Current backend marker is SharedPreferences proof storage**, not a durable download catalog with bytes, license, expiry, group membership, or removal accounting (`TidalPlayerScreen.kt:613-625`, `DirectManifestPlaybackBackend.kt:171-187`).

## Final recommendation

Use the current app structure, not a phone download-manager pattern:

1. Promote Home > Downloads into the offline shelf.
2. Keep Now Playing as the first track download/remove entry.
3. Add album/playlist header chips only after single-track lifecycle is product-stable.
4. Put bulk cleanup in Settings/Manage storage, not multi-select.
5. Make every removal string explicitly local-only.
6. Treat offline fallback as automatic source/filter behavior, with optional `Offline only` later.

## 11. Post-review implementation status — 2026-06-17

This design report has now served its intended gate: the lifecycle model was accepted as the implementation guide and split into concrete follow-up work instead of remaining an abstract proposal.

Follow-up implementation issues created from this report:

- #28 / UNTIDY-027 — Home → Downloads shelf and playable downloaded-track list.
- #29 / UNTIDY-028 — local remove-download UX with local-only cache/metadata cleanup.
- #30 / UNTIDY-029 — automatic offline fallback behavior for downloaded vs non-downloaded tracks.
- #31 / UNTIDY-030 — minimal Settings Downloads controls and local-only bulk cleanup copy.
- #32 / UNTIDY-031 — album/playlist collection download affordances and partial-state copy.

Important note: the source-reference section above captured the app state at the time of this design review. Several listed gaps have since been intentionally addressed by the follow-up implementation issues. The lasting contract from this report is the product model and safety boundary:

- offline UX is a Wear-sized Downloads shelf plus contextual actions, not a phone-style manager;
- removal means Untidy-local downloaded files, markers, and cache state only;
- Untidy must not delete TIDAL library items, playlists, or remote playlist membership;
- release copy should avoid `proof` language on product surfaces;
- collection downloads may expose partial state, but the one-track lifecycle remains the minimum dependable MVP.

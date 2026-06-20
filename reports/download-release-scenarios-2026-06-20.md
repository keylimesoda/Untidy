# Untidy Wear OS Download / Download-Management Release Scenario Matrix

Date: 2026-06-20  
Role: Product / QA scenario matrix  
Repo: `/home/riclewis/.openclaw/workspace/projects/Untidy`  
Status: Report only; no code changes made.

## Sources reviewed

- `docs/ux/offline-download-lifecycle-ux-2026-06-17.md`
- `docs/spec-downloads-offline-playback.md`
- `docs/offline-sdk-proof-plan-2026-06-17.md`
- `app/src/main/java/com/tidal/wear/ui/player/ActionsSheet.kt`
- `app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/downloads/DownloadsScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/tidal/wear/MainActivity.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/OfflineDownloads.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/DirectManifestPlaybackBackend.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/TidalMediaService.kt`
- `core/playback/src/test/java/com/tidal/wear/core/playback/offline/CollectionDownloadSummaryTest.kt`

## Current implementation snapshot

Important because the UX doc has a historical source-reference section from before follow-up work landed.

- **Now Playing action sheet has a download row** with states: `Unavailable`, `NotDownloaded`, `Downloading(progress)`, `Downloaded`.
- **Release builds still show downloads unavailable** from `TidalPlayerScreen.initialDownloadState(...)`: non-debug builds return `DownloadState.Unavailable`; `startDebugDownload()` also gates the proof launcher behind `BuildConfig.DEBUG`.
- **Debug builds can mark a track downloaded** after proof polling. This persists minimal SharedPreferences metadata via `OfflineDownloads.kt`.
- **Downloads route/screen now exists**. `MainActivity` includes `Routes.Downloads`; Home has an enabled `Downloads` chip with `Saved on watch`; `DownloadsScreen` lists downloaded tracks and per-track remove actions.
- **Settings now has download management affordances**: `Manage downloads`, disabled `Download quality`, disabled `Download over LTE`, disabled `Storage limit`, and `Remove all downloads` when local downloads exist.
- **Playback has an offline fallback seam, but not full release-grade offline playback**:
  - `TidalMediaService` allows playback if network is available or the track is marked downloaded.
  - Offline queues are filtered to marked downloaded tracks.
  - `DirectManifestPlaybackBackend` prefers `usage=DOWNLOAD` and an app-private cache when a track is marked downloaded.
  - The marker/cache mechanism is still proof/MVP-like, not a complete durable catalog with license expiry, byte completeness, renewal, partial-state, policy, or sanctioned offline task lifecycle.
- **Core offline helpers are track-only**: downloaded/failed markers, summaries, per-track removal, remove-all, storage bytes.
- **Collection summary helpers exist** and are unit-tested for distinct playable/downloaded/failed counts, but album/playlist download actions are not productized.

## Release posture recommendation

**Recommended public-release MVP scope:**

Ship downloads publicly only when the single-track lifecycle is real, honest, and device-validated:

1. Single-track download from Now Playing.
2. Repository-backed durable state, not debug proof polling.
3. Downloads shelf lists playable downloaded tracks.
4. Remove one local track copy and remove all local downloads.
5. Offline playback works with radios/network disabled for downloaded tracks.
6. Non-downloaded offline playback fails clearly with `Not downloaded · connect to stream this track` or equivalent user-visible copy.
7. Download quality/LTE/storage settings either work or are intentionally disabled with non-debug, non-`proof` release copy.
8. Sign-out removes local downloads/licenses or clearly confirms that it will.

**Defer from first public download release:**

- Album-level and playlist-level download actions.
- Playlist update detection / sync semantics.
- Automatic eviction beyond simple manual cleanup.
- License renewal UX if the first release can instead fail clearly and require redownload.
- Optional `Offline only` mode.
- Per-collection pin/keep semantics.

## Scenario matrix

Legend:

- **MVP** = required before public release of downloads.
- **Defer** = design now, implement after single-track release is stable.
- **Internal only** = keep debug/proof or diagnostic behavior out of public UX.

| ID | Scenario | User entry point | Current state | Public-release expectation | Acceptance criteria | Scope |
| --- | --- | --- | --- | --- | --- | --- |
| D01 | Single current-track download starts | Now Playing → Actions → `Download` | Debug-gated proof launcher only; release row disabled as `Offline unavailable` | Tap enqueues/starts a sanctioned download for the current track | Button enabled only when signed in, track id valid, track eligible, policy can accept intent; state changes immediately to `Queued`, `Waiting for Wi-Fi`, or `Downloading 0%`; no `OfflineProofActivity`/debug route in release; operation persists if sheet closes | MVP |
| D02 | Download progress | Now Playing action row; Downloads screen active section | Only local polling/progress simulation in debug path | Progress reflects real transfer/finalization state | Progress monotonically reports bounded values; copy fits one line on watch (`Downloading 42%`, `Finishing…`); progress survives process recreation; active download visible in Downloads shelf; no raw URLs, tokens, manifest ids, or debug proof names shown | MVP |
| D03 | Download completes | Same as D01/D02 | Debug proof can mark SharedPreferences downloaded | Completion means bytes/license/metadata are valid for offline playback | State becomes `Downloaded`; checkmark appears; track appears in Downloads screen; storage usage updates; app can kill/restart and still show downloaded; completed state is not written before required local assets/license are durable | MVP |
| D04 | Already downloaded current track | Now Playing → Actions | `Downloaded` if local marker exists in debug/proof store | User sees stable downloaded state and can manage local copy | Row says `Downloaded` with checkmark; tapping does not redownload silently; tapping opens/removes through `Remove download` choice; state matches Downloads screen after navigation/restart | MVP |
| D05 | Remove local copy from Now Playing | Actions → `Downloaded` → `Remove download` | Exists for marked downloads; copy says `Keeps it in TIDAL` | Remove only Untidy-local files/metadata/license, never TIDAL library or playlist data | Confirmation/action copy includes `Remove download` / `Keeps it in TIDAL`; removes local bytes, license artifacts, marker, failed state, title/artist metadata; Downloads screen no longer lists track; playback while offline no longer treats track as downloaded | MVP |
| D06 | Remove local copy from Downloads screen | Home → Downloads → row remove | Exists per track | Same local-only deletion from management shelf | Each downloaded track has a remove affordance; confirmation uses local-only copy; cancel preserves row; success refreshes list and storage; failure keeps row and shows recoverable message | MVP |
| D07 | Remove all local downloads | Settings → Downloads → Remove all | Exists when tracks exist | Bulk cleanup for MVP storage escape hatch | Hidden when no downloads; confirmation says local copies only / keeps TIDAL library; removes all local media/cache/license/metadata; storage becomes 0 or excludes unrelated temp files; failures are reported without pretending all removed | MVP |
| D08 | Download unavailable in release | Actions download row for unsupported track/build | Release currently always unavailable | Use only when the backend/track/account truly cannot support offline | Row disabled with clear non-debug copy (`Offline unavailable`); no `proof` copy; no enabled dead affordance; unavailable reason can be surfaced in detail/toast/log without secrets | MVP until feature enabled; then error-state |
| D09 | Track not eligible | Now Playing on blank/unplayable track id, preview, unsupported region/asset | `isDownloadProofEligible()` only checks id/title/artist for proof | Eligibility must derive from entitlement/catalog/offline capability | Download row disabled or returns `Offline unavailable`; no queue row created; user can still stream if streaming is allowed; logs distinguish blank id vs entitlement vs catalog vs DRM without leaking tokens | MVP |
| D10 | Network absent before starting download | Actions → `Download` while offline | Release unavailable; debug proof likely fails/unavailable | Intent should be accepted as queued only if policy supports future network; otherwise clear wait state | State becomes `Waiting for Wi-Fi` or `Queued`; Downloads active section shows wait reason; no spinner/progress that implies bytes are transferring; no crash if connectivity APIs return null | MVP |
| D11 | Wi-Fi lost during active download | Active download | Not productized | Pause or fail safely based on policy and resumability | Partial/temp files remain atomic; state becomes `Waiting for Wi-Fi` if retryable; resumes when allowed network returns; no item marked downloaded until validation succeeds; no corrupt cache used for playback | MVP |
| D12 | LTE/cellular available but LTE downloads disabled | Start/continue download on LTE watch | Settings row disabled and says deferred | Public MVP may keep LTE disabled; if setting absent/disabled, queue instead of consuming data | User sees `Waiting for Wi-Fi`; no background LTE transfer; if a future LTE toggle is enabled, copy says `Uses watch data plan`; tests verify metered/cellular is blocked by default | MVP policy; toggle can defer |
| D13 | Auth missing/expired before download | Start download signed out or with expired token | App has account state elsewhere; download path debug-only | Downloads require valid auth; auth failure is explicit | If signed out, row disabled or prompts account linking; if token refresh fails, state `Download failed` with `Sign in again`-style detail; no local marker created; no repeated tight retry loop | MVP |
| D14 | Auth/account sign-out with existing downloads | Settings → Erase account | Settings erase copy includes download count via helpers | Sign-out must remove/invalidate local protected content | Confirmation mentions downloads when count > 0; sign-out removes media/cache/license/download metadata or makes them unusable and prompts cleanup; Downloads screen empty after sign-out; offline playback blocked after sign-out | MVP |
| D15 | Storage cap/headroom insufficient before download | Start download near storage cap | Storage usage helper exists; storage limit disabled/manual | Public MVP needs at least preflight + manual cleanup | Preflight detects not enough space/headroom; state becomes `Storage full`/`Download failed`; offers `Manage downloads`; does not exceed configured cap except bounded temp budget; no OS-low-storage instability | MVP |
| D16 | Storage pressure during download | Active download fills temp/cache | Not productized | Fail atomically and recover cleanup | Temp file cleanup runs; state is failed with storage reason; no downloaded marker; storage usage excludes cleaned partials; retry possible after cleanup | MVP |
| D17 | Corrupt/missing local cache despite marker | Downloaded track cache deleted/corrupt | Code checks cache dir; marker alone may allow service gate | UI/backend must repair truth: not downloaded/expired/missing | Startup or playback validation detects missing bytes/license; row becomes `Download missing`, `Expired`, or `Not downloaded`; local marker is cleared or quarantined; offline playback does not attempt network secretly when offline | MVP |
| D18 | Offline playback of downloaded track | Home/Downloads or Now Playing replay with network off | Service allows marked tracks; backend tries download manifest/cache but still not proven release-grade | Track plays with network/radios disabled from local valid source | Airplane-mode playback starts from Downloads; logs/diagnostics show local source; no network requests for audio bytes; wake mode local; player controls/queue/ambient continue to work | MVP release gate |
| D19 | Offline playback of non-downloaded track | Search/Library/Recent/Queue while offline | Service logs `Not downloaded · connect to stream this track` | User-visible failure/fallback, not silent no-op | Attempt does not start foreground service indefinitely; user sees `Not downloaded` / `Connect to stream this track`; Now Playing is not advanced to an unplayable track; no crash | MVP |
| D20 | Offline queue with mixed local/non-local tracks | Play queue offline | Service filters queue to downloaded tracks | Product decision: play local-valid subset with explanation, or stop at missing item | If subset policy: only downloaded tracks are queued; current requested non-local maps to first local or fails clearly; skipped count/reason visible enough for watch UX. If strict policy: queue does not start and explains missing downloads | MVP for queues if queue playback exposed offline |
| D21 | Downloads discovery while offline | Home → Downloads; Search/Discover offline | Downloads route exists; Search/Discover include shortcuts per rg results | Offline content remains reachable; online surfaces do not pretend to load | Home Downloads opens without network; Downloads list renders from local store; Search/Discover show connection-required copy plus Downloads shortcut; no login/network blocking before showing local downloads if account content remains valid | MVP |
| D22 | Downloaded content metadata display offline | Downloads row title/artist/artwork | Downloads uses saved title/artist and fallback art | Rows remain understandable offline | Title and artist persist; artwork fallback works; no online metadata fetch required to render local list; blank artist falls back to `Downloaded`; long labels ellipsize on round screen | MVP |
| D23 | Failure: sanctioned API/offline task returns unsupported | Download attempt | Proof plan says sanctioned path still gated/uncertain | Honest `Offline unavailable` / terminal failure | Failure reason maps to user-safe state; retry hidden if terminal unsupported; no fallback to caching `PLAYBACK`/`STREAM` manifests; logs preserve redacted diagnostic status | MVP |
| D24 | Failure: transient network/server error | Download attempt/active download | Not productized | Retryable failure with clear copy | State becomes `Download failed`; detail says retry/connect; retry action reuses same track id and clears failed marker only on progress/success; no duplicate rows | MVP |
| D25 | Failure: DRM/license provisioning or renewal failure | Download completion or later playback | Offline proof plan identifies license unknowns | Do not mark playable; explain renewal when appropriate | If license cannot be obtained, state failed/unavailable; if expired later, state `Expired`/`Connect to renew`; offline playback does not bypass DRM; online renewal/redownload path exists or is deferred with clear copy | MVP for license failure; renewal can defer if redownload works |
| D26 | Download cancellation | Active download row/action | No public active manager yet | User can stop a mistaken download, at least from Downloads active section | Cancel stops work, deletes temp bytes, state returns `Not downloaded` or `Canceled`; no completed marker; idempotent cancel on process race | MVP if downloads can run long/background |
| D27 | Retry failed download | Downloads failure row or Now Playing action | Failed marker helper exists; no public retry UI seen | Retry recoverable failures without stale failed state | Failed row exposes retry; retry clears failed marker only when accepted/started; previous temp data cleaned; success updates all surfaces; terminal unavailable remains unavailable, not retry loop | MVP |
| D28 | App process death during download | OS kills app/service | Not productized | Download state is durable and restart-safe | On restart, in-progress items are `Queued`, `Waiting`, `Failed`, or resumed based on scheduler truth; temp files reconciled; no track incorrectly marked downloaded | MVP |
| D29 | Watch reboot after successful download | Reboot, open app offline | SharedPreferences summary survives; cache durability not fully proven | Local playback survives reboot | Downloads list shows track; offline playback works; storage usage accurate; no need for network to rediscover local content | MVP release gate |
| D30 | Active local playback then remove current track | Now Playing/Downloads remove currently playing local track | Current removal can delete marker/cache immediately | MVP should avoid breaking current playback | Either block with `Stop playback first` or queue `Remove after play`; no mid-playback crash; copy matches behavior; after stop, local copy removed and next offline play blocked | MVP |
| D31 | Storage cleanup of failed/incomplete temp files | Startup/Settings/Download retry | No explicit temp cleanup surfaced | Prevent space leaks | Startup or manager cleanup removes orphan temp files; `Remove all downloads` removes failed/incomplete local artifacts too or separate `Clean failed downloads`; storage summary matches file-system scan | MVP |
| D32 | Download quality setting | Settings → Downloads | Disabled: `Battery Saver · track MVP` | Keep disabled in MVP unless actual offline quality model exists | If disabled: non-debug copy explains default; download uses conservative quality. If enabled: choice persists and affects manifest/download request; tests cover Battery Saver/Balanced/High mapping | Defer enabling; default required |
| D33 | Storage limit setting | Settings → Downloads | Disabled: `Manual cleanup for MVP` | MVP can ship manual cleanup plus hard-coded conservative cap; public copy must be honest | If disabled, no illusion of configurable cap; app still reserves headroom and prevents runaway storage. Later, cap choices update policy and eviction/preflight | Defer configurable UI; MVP hard cap/preflight |
| D34 | Album download action | Album detail header | Not present | Defer until track lifecycle is stable | Header has `Download album`; progress by count (`7/12`); partial/failure detail; unavailable tracks handled; no release dead affordance before backend supports group downloads | Defer |
| D35 | Album already/partially downloaded | Album detail; Downloads Albums section | Collection summary helpers only | Defer public UI, but model should be ready | `Downloaded 12/12` only when all playable distinct tracks local and no failures; `Partial 9/12`; `3 failed`; duplicate track ids counted once; unavailable/unplayable tracks excluded or disclosed consistently | Defer |
| D36 | Remove album local copies | Album detail/Downloads collection row | Remove all/track only | Defer collection removal | Confirmation required; removes local copies for album group only; does not remove TIDAL album/library save; handles currently playing child according to local playback removal policy | Defer |
| D37 | Playlist download action | Playlist detail header | Not present | Defer; playlist snapshot semantics required | `Download playlist` snapshots current membership/order; progress by count; local snapshot playable offline; no silent deletion if remote playlist changes | Defer |
| D38 | Playlist update/staleness | Playlist detail online after remote change | Not present | Defer | UI can show `Update available`; update downloads new tracks; cleanup of removed tracks is explicit, never silent | Defer |
| D39 | Remove playlist local copies | Playlist detail/Downloads collection row | Not present | Defer | Confirmation says local-only; removes files/metadata associated with playlist group; keeps TIDAL playlist and membership untouched | Defer |
| D40 | Debug/proof diagnostics | Debug builds/settings/logs | Existing proof artifacts/activity | Keep internal only | Release build contains no user-visible `proof`, raw endpoint, token, license, or manifest debug text; debug diagnostics remain gated by `BuildConfig.DEBUG`; release QA scans for `Proof`, `OfflineProof`, raw URLs in UI strings | Internal only / release hygiene |

## Acceptance criteria by release area

### 1. Now Playing / action sheet

- Download row is enabled only for eligible current tracks and supported account/build/backend states.
- Release build never launches `OfflineProofActivity` or any debug-only proof runner.
- State labels cover at minimum: `Download`, `Queued`, `Waiting for Wi-Fi`, `Downloading N%`, `Finishing…`, `Downloaded`, `Download failed`, `Offline unavailable`, `Expired`, `Removing…`.
- `Downloaded` tap must not silently redownload; it must expose a local remove action.
- Single-line labels fit round 454x454 Wear screens; long progress/failure text goes to a secondary detail/status line.
- Haptics/toasts/status lines are short and not required for state persistence.

### 2. Downloads shelf / discovery

- Home `Downloads` is reachable online and offline.
- Empty state: `No downloads yet` / `Download from Now Playing` or equivalent.
- Active state: active or queued download visible with reason/progress.
- Downloaded state: track row is playable and has a local remove affordance.
- Failure state: failed downloads are visible with retry/remove or clear next action.
- Storage summary shown or deliberately omitted; if shown, it must reflect file-system/cache bytes rather than just row count.
- Downloads rendering must not require network metadata fetch.

### 3. Local removal and cleanup

- All removal copy says `download`, `local copy`, or `local files`; never `delete track`, `delete playlist`, or anything implying remote deletion.
- Per-track removal deletes media bytes/cache, offline license if present, metadata row, downloaded marker, failed marker, and group membership if applicable.
- Remove-all deletes all local download artifacts and refreshes Settings/Downloads.
- Removing the currently playing local track is either blocked with `Stop playback first` or deferred with `Remove after play`; behavior is tested.
- Removal is idempotent enough to survive missing files/markers.

### 4. Offline playback / resolver

- A downloaded track plays with network disabled.
- A non-downloaded track does not start offline and produces user-visible `Not downloaded`/`Connect to stream` copy.
- Mixed offline queues have a documented policy and test coverage.
- Playback source selection prefers local valid assets for downloaded tracks and streaming for online non-downloaded tracks.
- Backend must not use `PLAYBACK`/`STREAM` manifest caching as the product offline implementation.
- Any `usage=DOWNLOAD` / license path must be sanctioned and verified on device.

### 5. Policy and settings

- Default download quality is conservative/Battery Saver.
- LTE download is off by default; if the toggle is deferred, attempts on LTE wait for Wi-Fi rather than consuming data.
- Storage has a conservative hard cap/headroom even if configurable UI is deferred.
- Settings rows are either functional or honestly disabled; no `proof in progress` release copy.
- Account erase/sign-out copy mentions local downloads when present and removes/invalidates them.

### 6. Error and edge cases

- Auth failures do not create local rows/markers.
- Storage-full failures leave no playable marker and clean temp data.
- Network failures are retryable where appropriate.
- Terminal unsupported/DRM failures become `Offline unavailable` or non-retryable failure.
- Corrupt/missing local files are detected and reconciled before claiming `Downloaded`.
- Process death/reboot cannot promote an incomplete item to downloaded.

## Required test cases

### JVM/unit tests

1. **Download state reducer / copy mapping**
   - Given internal states, returns exact short Wear copy and enabled/disabled/action semantics.
   - Release copy contains no `proof`, raw endpoint, token, or debug wording.
2. **Eligibility/policy decisions**
   - Signed out, token expired, unsupported track, offline, Wi-Fi, LTE-disabled, storage-full, low-battery if implemented.
3. **Collection summary**
   - Existing tests cover distinct/downloaded/failed count; extend for partial, empty, duplicate, unavailable/unplayable, failed+downloaded conflict.
4. **Storage accounting**
   - File sizes summed correctly; missing dirs count 0; temp/incomplete files excluded or accounted according to policy.
5. **Removal**
   - Per-track removal clears downloaded, failed, title/artist/downloadedAt metadata and deletes cache/license paths.
   - Remove-all invokes per-track cleanup and reports partial failure correctly.
6. **Offline resolver**
   - Network available + non-downloaded → stream.
   - Network unavailable + downloaded valid → local.
   - Network unavailable + non-downloaded → blocked.
   - Marker present + cache/license missing → missing/expired, not local playable.
7. **Queue filtering/policy**
   - Offline mixed queue behavior matches product decision.
8. **Sign-out cleanup**
   - Sign-out removes/invalidates local download catalog and cache/license artifacts.

### Compose/UI tests where feasible

1. `ActionsSheet` displays correct labels/icons/enabled state for each download state.
2. `Downloaded` click reveals `Remove download` and `Cancel`; cancel preserves state.
3. `DownloadsScreen` empty/downloaded/remove-confirm/success/failure states.
4. `SettingsScreen` shows manage downloads, disabled or enabled settings as designed, and remove-all only when downloads exist.
5. Round-screen small text checks: progress/failure labels do not wrap badly or obscure adjacent rows.

### Integration tests / fake backend

1. Fake download manager transitions `Queued → Downloading → Finishing → Downloaded`; UI updates all surfaces.
2. Fake failure transitions to retryable and terminal states.
3. Process recreation reloads state from repository.
4. Downloads list can play a fake locally marked track through service intent path.
5. Offline network fake blocks non-downloaded playback and allows downloaded playback.

## Emulator/device validation checklist

### Build gates

- `git diff --check`
- `source scripts/env-android.sh >/dev/null && bash ./gradlew lintDebug assembleDebug testDebugUnitTest --no-daemon`
- Release compile gate before public claim: `source scripts/env-android.sh >/dev/null && bash ./gradlew :app:compileReleaseKotlin :app:processReleaseManifest --no-daemon`
- Release-string scan: no visible `Proof`, `OfflineProof`, `debug`, raw manifest/license URL, token, or internal proof names in release UX strings.

### Emulator smoke: UI and persistence

1. Install and launch app.
2. Verify Home → Downloads opens.
3. Verify empty Downloads state.
4. With a seeded/fake downloaded track, verify Downloads row displays title/artist and play action.
5. Verify per-track remove confirmation and cancel.
6. Verify remove success refreshes Downloads and Settings counts.
7. Verify Settings → Manage downloads opens Downloads.
8. Rotate/scroll with crown/rotary on Home, Actions, Downloads, Settings.
9. Capture screenshots/XML for Home, Actions row, Downloads empty, Downloads populated, Settings Downloads section.

### Device/real Wear validation: required before public download release

Emulator is not enough for the final claim because offline DRM, storage pressure, and radio behavior are device-sensitive.

1. **Single-track happy path**
   - Online Wi-Fi, signed in.
   - Start download from Now Playing.
   - Leave sheet/app and return; progress remains coherent.
   - Complete download.
   - Reboot watch.
   - Enable airplane mode / disable Wi-Fi/LTE/Bluetooth as applicable.
   - Play from Downloads; confirm audio starts and continues.
2. **No-network non-downloaded path**
   - With radios disabled, attempt a non-downloaded track.
   - Confirm visible `Not downloaded`/connect copy and no stuck foreground service.
3. **Remove path**
   - Remove downloaded track.
   - Confirm local bytes/metadata/license removed.
   - Confirm offline play fails afterward with correct copy.
4. **Process death/reboot**
   - Kill app/service during queued/downloading/finishing states.
   - Reopen; verify state is reconciled and no false downloaded marker exists.
5. **Network transitions**
   - Start download on Wi-Fi, drop Wi-Fi; verify `Waiting for Wi-Fi`/retry.
   - Restore Wi-Fi; verify resume/retry and eventual success.
6. **LTE policy**
   - On LTE-capable watch with LTE disabled in settings/policy, attempt download on LTE; verify no transfer and `Waiting for Wi-Fi`.
7. **Storage pressure**
   - Lower cap or fill test storage; attempt download; verify storage-full state, no marker, temp cleanup, manage-downloads path.
8. **Auth/sign-out**
   - Download a track, sign out/erase account.
   - Confirm sign-out copy mentions downloads.
   - Confirm Downloads empty and offline playback blocked.
9. **Battery/thermal sanity**
   - Download a representative track/short album-size batch later; ensure no excessive battery drain, thermal warning, or UI jank.
10. **Release build parity**
   - Repeat core happy path on a release build or release-like build variant, not only debug.

## Public MVP vs defer summary

### MVP / public release blocker scenarios

- D01-D08: single-track action state, progress, complete, already downloaded, remove, unavailable.
- D10-D18: offline/network/auth/storage/sign-out/local playback fundamentals.
- D19-D22: offline discovery and non-downloaded fallback.
- D23-D31: failure/cancel/retry/process death/corrupt cache/storage cleanup.
- D32-D33 as honest disabled/default policy, not fully configurable UI.
- D40 release hygiene.

### Safe to defer

- D34-D39 album/playlist download and management.
- Configurable download quality/storage cap UI if defaults and hard safety exist.
- LTE enablement UI if default is Wi-Fi-only and attempts on LTE queue/wait.
- Playlist update/staleness and collection cleanup.
- Auto-eviction/pin semantics beyond manual cleanup and hard cap.

## QA risks / watch-outs

1. **Marker vs actual bytes/license**: current helpers can mark a track downloaded independently of full local playback validity. Public MVP must not equate marker with playable offline asset.
2. **Release gating**: current `BuildConfig.DEBUG` gate means release builds intentionally show unavailable. Removing this gate without replacing proof polling with a real repository/manager would create a fake public feature.
3. **User trust copy**: every removal path must say local-only and avoid remote deletion implications.
4. **Offline playback proof**: public release requires real-watch validation with radios disabled, not just compile, emulator, or logs.
5. **Sanctioned path guardrail**: do not regress into caching `usage=PLAYBACK` / `playbackmode=STREAM` as user-visible downloads. The offline proof plan requires sanctioned `DOWNLOAD`/offline SDK surfaces, license handling, and app-private storage.

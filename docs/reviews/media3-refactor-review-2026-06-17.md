# Media3 player refactor review — 2026-06-17

Repo/worktree: `/home/riclewis/.openclaw/workspace/projects/worktrees/untidy-media3-player-refactor`  
Branch: `untidy/media3-player-refactor`  
Reviewed head: `79f4a88` (`Document Media3 Wear playback architecture findings`)  
Mode: review-only helper; no production code changes.

## Review status

**Status: BLOCK for the current branch as a Media3 refactor.**

At the time of review, this branch has no implementation delta from `main`; `git diff --name-status main...HEAD` is empty. The current code is therefore still the build-7 baseline documented by the architecture/research reports, not the real Media3-player refactor. It does **not** eliminate split-brain playback ownership yet.

If an implementation helper commit lands after this review, it should be reviewed against the checklist below before acceptance.

## Sources checked

Local project sources:

- `docs/research/media3-wear-background-playback-2026-06-17.md`
- `docs/reviews/playback-architecture-review-2026-06-17.md`
- `core/playback/src/main/java/com/tidal/wear/core/playback/TidalMediaService.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/DirectManifestPlaybackBackend.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/PlaybackBackend.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/PlaybackActions.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/MediaControllerAccessPolicy.kt`
- `app/src/main/AndroidManifest.xml`

Reference patterns checked:

- AndroidX `DemoPlaybackService.kt` (`androidx/media`, release branch): builds one real `Player`, then `MediaLibrarySession.Builder(this, player, callback)`; releases the session player; uses a `MediaSessionService.Listener` to handle `onForegroundServiceStartNotAllowedException()` with a user-facing notification rather than retrying `startForeground()`.
- Horologist `PlaybackService.kt`: service is thin and returns an injected `MediaLibrarySession`.
- Horologist `LifecycleMediaLibraryService.kt`: delegates start/bind lifecycle to Media3 and does not implement custom playback dispatch in `onStartCommand()`.
- Horologist `PlaybackServiceModule.kt`: constructs one ExoPlayer with audio-only renderers, `setAudioAttributes(..., true)`, `setSuppressPlaybackOnUnsuitableOutput(...)`, `setWakeMode(C.WAKE_MODE_NETWORK)`, offload preferences, then builds `MediaLibrarySession` around that same player.

## Ideal refactor path

### Target architecture

The ideal Untidy shape is one Media3-visible player of record:

1. `TidalMediaService` owns or receives one real `ExoPlayer`/`Player` instance.
2. `MediaLibrarySession` is built directly with that same player:
   - `MediaLibrarySession.Builder(this, player, callback).build()`.
3. Queue playback is represented as a real player playlist/timeline, with stable `MediaItem.mediaId` values and metadata for all queued tracks.
4. Manifest resolution is moved behind a `MediaSource.Factory` / resolver layer used by the ExoPlayer, rather than a separate backend player that calls `setMediaSource()` per track while a shadow session player mirrors state.
5. Media3 owns foreground-service/media-notification lifecycle. Ongoing Activity, if kept, must be layered through Media3 notification customization or notification-only updates that do not re-promote foreground on each transition.
6. Service custom actions become compatibility entrypoints only: validate app command tokens, translate initial app/UI intents into player playlist operations, then let player/session commands handle pause/resume/seek/skip.

### Migration phases

1. **Introduce a real player engine API**
   - Extract manifest resolution from `DirectManifestPlaybackBackend` into a testable resolver/factory.
   - Preserve TIDAL auth, audio preset selection, desktop/openapi fallback, offline `DOWNLOAD` manifest preference, and canonical cache-key behavior.

2. **Make the session player real**
   - Build `MediaLibrarySession` with the actual ExoPlayer, not `TidalSessionPlayer : SimpleBasePlayer`.
   - Remove shadow timeline/position/state estimation from the service.
   - Use ExoPlayer playlist transitions for normal next-track movement; `STATE_ENDED` should mean end of queue, not end of every item.

3. **Remove foreground/notification cruft**
   - Delete custom per-track `startForeground()` calls from `publishOngoingActivity()`.
   - Avoid two competing notifications (`tidal_playback` id 42 and Media3 default media notification).
   - If an OngoingActivity is required for Wear recents, integrate it through one notification path without fighting Media3 foreground accounting.

4. **Rebuild validation around observable invariants**
   - Unit-test queue-to-playlist mapping and offline/live media-source selection.
   - Add tests proving controller filtering and token-gated service actions still work.
   - On watch, capture at least three short-track transitions with no `ForegroundServiceStartNotAllowedException`, no transition-time `Background started FGS: Disallowed`, and a coherent active media session.

## Findings

### Critical — Current branch still has split-brain player ownership

`TidalMediaService` still creates:

- `DirectManifestPlaybackBackend`, which privately owns the real `ExoPlayer` and does actual audio playback.
- `TidalSessionPlayer : SimpleBasePlayer`, which is passed to `MediaLibrarySession.Builder(...)` and manually mirrors backend state.

This is the core failure pattern from the prior architecture review. Media3/Wear observes the `SimpleBasePlayer`, not the private ExoPlayer. The implementation still hides real playback behind a facade, so it has not solved the one-player-of-record problem.

**Required fix before accept:** eliminate `TidalSessionPlayer` as an independent state machine, or replace it with a true forwarding adapter whose state comes from the same ExoPlayer instance used for audio. The preferred fix is direct ExoPlayer session backing.

### Critical — Manual foreground promotion remains on every playback update

`publishOngoingActivity(...)` still calls:

```kotlin
startForeground(MEDIA_NOTIFICATION_ID, builder.build())
```

and it is still invoked from initial play, per-track `playTrack(...)`, pause/resume, errors, and end-of-queue. This is the direct crash mechanism previously observed on Wear OS during background transitions.

It also keeps a custom transport notification/OngoingActivity path alongside Media3's own media notification. That means foreground ownership remains ambiguous.

**Required fix before accept:** remove transition-time `startForeground()` calls. Let Media3 manage the foreground media notification from the session-backed real player. Reintroduce OngoingActivity only through one carefully controlled notification path.

### Critical — Queue transitions are still service-driven, not player-driven

Current queue state lives in `TidalMediaService`:

- `currentQueue`
- `currentQueueIndex`
- `skipToNextInQueue()`
- `handleMediaProductEnded()`

The session player still exposes only a one-item playlist in `getState()`, always with current item index `0`. On backend `MediaEnded`, the service manually loads the next manifest and calls backend `setMediaSource()` again. That makes a natural album transition look like pause/load/play from the session perspective.

**Required fix before accept:** map the queue to a real ExoPlayer playlist/timeline and let player item transitions drive next-track behavior. Use service queue state only as UI/session metadata if needed, not as the authoritative playback engine.

### Important — Offline route preservation is good in concept but must move with the player

Current offline behavior to preserve:

- `canStartLiveOrDownloadedPlayback(trackId)` permits playback when network is unavailable only if `isOfflineTrackDownloaded(trackId)` is true.
- `DirectManifestPlaybackBackend.fetchPlaybackManifest(...)` prefers `usage=DOWNLOAD` manifest when a track is marked downloaded.
- Offline playback uses app-private `SimpleCache`, `CacheDataSource.Factory`, and `canonicalDownloadCacheKeyFactory(trackId)`.
- Offline wake mode switches to `C.WAKE_MODE_LOCAL` when using downloaded cache.

These are worth keeping, but they currently live inside the private backend ExoPlayer layer. A refactor must not drop them while moving playback into the session player.

**Required fix before accept:** create a testable media-source/manifest resolver that preserves online fallback and offline `DOWNLOAD` cache behavior when used by the session-backed ExoPlayer.

### Important — Wake mode exists, but should live on the real session player

`DirectManifestPlaybackBackend` correctly sets wake mode:

- `C.WAKE_MODE_NETWORK` by default.
- `C.WAKE_MODE_LOCAL` when an offline cache is used.
- `C.WAKE_MODE_NONE` on release.

This is good, but today it applies to the private backend player. In the target refactor, the same wake-mode behavior must be applied to the ExoPlayer passed to `MediaLibrarySession`.

**Required fix before accept:** preserve adaptive network/local wake mode on the single player of record.

### Important — Controller security posture is directionally right and should be preserved

The manifest intentionally keeps `TidalMediaService` exported for Media3/Wear discovery. `onConnect()` calls `MediaControllerAccessPolicy.isAllowedController(...)`, allowing:

- own package,
- Media3 trusted controllers,
- the media notification controller.

Custom explicit service actions also require an app-authored command token through `PlaybackCommandTokenProvider`.

This is the right boundary for a Media3 media service and should be retained.

**Required fix before accept:** ensure a refactor does not remove `onConnect()` filtering or token checks for custom start actions. Consider adding `androidx.media3.session.MediaSessionService` and `MediaButtonReceiver` only after confirming they do not bypass the same controller policy.

### Important — Current implementation remains hard to unit-test at the critical seam

The most important behavior — manifest resolution, player state, playlist transition, notification/foreground side effects — is coupled across service, backend, and private ExoPlayer state. Tests can cover helper functions, but not the architectural invariant that Media3 sees the actual player state.

**Required fix before accept:** extract a small resolver/factory and queue-to-media-items mapper with unit tests. Add a fake-player/service-boundary test or instrumentation assertion that item transition does not call app-authored foreground promotion.

### Minor — Manifest service actions are acceptable as app UI entrypoints, not as primary playback architecture

`PlaybackActions` can remain as compatibility entrypoints from existing UI paths, especially to start playback from foreground UI with a token. But pause/resume/skip should increasingly flow through Media3 controller/player commands once the session is backed by the real player.

## Accept/fix/block decision

**Current branch decision: BLOCK.**

Reason: no implementation commit is present beyond documentation, and the current code still has all three release-blocking architecture issues:

1. shadow `SimpleBasePlayer` backed by a private ExoPlayer,
2. per-track `startForeground()` / duplicate notification lifecycle,
3. service-owned manual queue transitions outside the real player playlist.

## Acceptance checklist for the next implementation commit

Do not accept a Media3 refactor unless all of these are true:

- [ ] `MediaLibrarySession.Builder(...)` receives the real audio `Player`/ExoPlayer or a strict forwarding wrapper over the same instance.
- [ ] `TidalSessionPlayer : SimpleBasePlayer` is removed or no longer owns independent timeline/state/position.
- [ ] `DirectManifestPlaybackBackend` no longer owns a separate private ExoPlayer hidden from Media3.
- [ ] Normal queue transitions are ExoPlayer playlist/media-item transitions, not `MediaEnded -> service skip -> setMediaSource()` reloads.
- [ ] No app-authored `startForeground()` runs on track transitions.
- [ ] Only one foreground media notification path exists; OngoingActivity does not compete with Media3 foreground ownership.
- [ ] Controller filtering in `onConnect()` is preserved.
- [ ] App-command-token checks are preserved for explicit exported-service actions.
- [ ] Offline `DOWNLOAD` manifest/cache-key route still works for downloaded tracks.
- [ ] Wake mode remains `NETWORK` for streaming and `LOCAL` for cached/offline playback.
- [ ] Verification includes build/test/lint plus physical watch evidence across at least three short-track transitions.

## Recommended immediate next step

Wait for the implementation helper commit, then review the diff specifically for the checklist above. If the helper only moves code around while retaining a private backend ExoPlayer plus a session facade, reject it as cosmetic; that would hide the split-brain architecture rather than fix it.

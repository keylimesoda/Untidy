# Playback Architecture Review — 2026-06-17

Repo: `keylimesoda/Untidy`  
Reviewed head: `91d50bd` (`Simplify playback service lifecycle`)  
Scope: outside-help review of one-song-and-out / Wear OS playback persistence issue #37.  
Mode: architecture review only; no code changes.

## Executive summary

The most likely root cause is **split-brain playback ownership**: the real audio engine is a private `ExoPlayer` inside `DirectManifestPlaybackBackend`, while the `MediaLibrarySession` is backed by a separate `SimpleBasePlayer` facade (`TidalSessionPlayer`). That facade manually approximates player state, timeline, metadata, position, queue, and transitions. At track end, ExoPlayer briefly reports `NotPlaying`/ended while the facade still exposes a one-item playlist and position from the previous item. Media3/Wear reacts to that transient state as pause/buffering and updates/demotes notifications. The app then calls `startForeground()` manually from the background during the transition, which crashes on Wear OS with `ForegroundServiceStartNotAllowedException`.

Media3 best-practice docs show the simpler pattern: keep the `Player` and `MediaSession` in the `MediaSessionService`/`MediaLibraryService`, and construct the session directly with the player (`MediaSession.Builder(this, player)` / `MediaLibrarySession.Builder(this, player, callback)`). Media3 then syncs notification/session state automatically from the `Player` and `MediaSession`.

Smallest likely durable fix: **make the MediaSession-backed player the actual ExoPlayer**, put the queue into ExoPlayer's playlist, handle manifest resolution in one controlled path before/while adding media items, and remove manual per-track foreground promotion/notification publication from queue transitions. If a direct-Exo refactor is too large in one step, introduce a `ForwardingSimpleBasePlayer`/adapter that delegates state to the same ExoPlayer instance, not a separately maintained shadow player.

## Evidence reviewed

- GitHub issue #37: P0 field report that physical Galaxy Watch playback dies after 1–2 songs / background or screen-off; acceptance criteria require multi-song physical-watch validation.
- `reports/watch-persistence-2026-06-17-2229-build3-fail/summary.txt` and companion dumps:
  - `services.txt` shows `TidalMediaService` with `startForegroundCount=2`, `mAllowStart...=DENIED`, and `infoAllowStartForeground ... code:DENIED`.
  - `media-session.txt` shows a live Media3 session but `PlaybackState {state=NONE(0)}` after failure.
- `reports/watch-persistence-2026-06-17-2312-build5-immediate-stop/summary.txt`:
  - First transition: `MediaEnded` -> `queue next` -> `SessionPlayer loadTrack` -> `BUFFERING` -> `PAUSED` -> `PLAYING`.
  - Manual foreground operations around transition produce `startForegroundService() not allowed` / `ForegroundServiceStartNotAllowedException` in that generation.
  - Later service is stopped due to app idle despite reported playback updates, consistent with lifecycle/FGS state confusion.
- `reports/watch-persistence-2026-06-17-2339-build7-one-song-out/summary.txt`:
  - At first track end: `backend state: NotPlaying`, then MediaSession reports `state=PAUSED(2)` at old position `76535`, then `SessionPlayer loadTrack` and `BUFFERING` still at old position.
  - Wear/ActivityManager then logs `Background started FGS: Disallowed`, `Service.startForeground() not allowed due to mAllowStartForeground false`, and the process crashes from `TidalMediaService.publishOngoingActivity()` / `startForeground()`.
- Source files reviewed:
  - `TidalMediaService.kt`
  - `DirectManifestPlaybackBackend.kt`
  - `PlaybackBackend.kt`
  - `PlaybackActions.kt`
  - `app/src/main/AndroidManifest.xml`
  - `MainActivity.kt` playback start helpers
  - `PlayerActivity.kt`
- Android Media3 docs reviewed:
  - Background playback with `MediaSessionService`: contain `Player` and `MediaSession` in the service; notification updates happen automatically based on `Player`/`MediaSession` state.
  - Player interface docs: player state includes playback state, current timeline/media item, `playWhenReady`, and metadata; custom `SimpleBasePlayer` must accurately populate all current state.

## Severity-ranked findings

### Critical — MediaSession is backed by a shadow `SimpleBasePlayer`, not the real ExoPlayer

**Where**

- `TidalMediaService.onCreate()` builds `MediaLibrarySession` with `sessionPlayer!!`.
- `DirectManifestPlaybackBackend` owns the real `ExoPlayer` privately.
- `TidalSessionPlayer` separately tracks `mediaItem`, `playWhenReady`, `playbackState`, `durationMs`, and position estimates.

**Why this is risky**

Media3's foreground-service and notification heuristics observe the session player, not the private backend ExoPlayer. During transitions, the session player and audio player can disagree about:

- current media item / timeline,
- playback state,
- `playWhenReady`,
- current position,
- queue and current item index,
- ended vs next-item buffering.

The build 7 failure shows exactly this mismatch: immediately after the first track ends, MediaSession sees `PAUSED` at the previous track's terminal position (`76535`) before the second item is fully loaded. That is a valid-looking pause from the platform's perspective, even though the service intends continuous queue playback.

**Answer to question 4:** yes. Keeping actual ExoPlayer separate from the MediaSession player is very likely breaking Media3/Wear foreground heuristics.

**Answer to question 5:** yes. The session should ideally be backed directly by ExoPlayer, or at minimum by a forwarding adapter whose state is derived from the same ExoPlayer instance.

### Critical — Manual `startForeground()` during background queue transitions is crashing the process

**Where**

- `TidalMediaService.publishOngoingActivity()` always calls `startForeground(MEDIA_NOTIFICATION_ID, builder.build())`.
- `playTrack()` calls `publishOngoingActivity(track, isPlaying = true)` for each queue transition.
- `handleMediaProductEnded()` -> `skipToNextInQueue()` -> `playTrack()` runs inside the service while app is backgrounded/screen-off.

**Evidence**

Build 7 summary:

- `queue next index=1 id=20169368`
- `SessionPlayer loadTrack id=20169368`
- notification updates show foreground/media notification state changes
- `Background started FGS: Disallowed ... code:DENIED`
- `Service.startForeground() not allowed due to mAllowStartForeground false`
- fatal `ForegroundServiceStartNotAllowedException`

**Why this is risky**

Once the service is already running in the background, per-track manual `startForeground()` calls are fragile on targetSdk 35/Wear OS. Media3 is designed to manage the media notification/foreground state from the session player state. Manual notification publication is fighting Media3's own media notification (`default_channel_id`, id `1001` in logs) and the custom ongoing notification (`tidal_playback`, id `42`), producing duplicate/competing transport notifications and foreground state churn.

**Answer to question 2:** yes. Manual `publishOngoingActivity/startForeground` is causing lifecycle issues. It is the direct crash mechanism in build 7.

### High — `SimpleBasePlayer` transition state is incorrect/unstable across ended -> next

**Where**

- `DirectManifestPlaybackBackend.onPlaybackStateChanged(STATE_ENDED)` emits only `MediaEnded`; `onIsPlayingChanged(false)` emits `StateChanged(NotPlaying)`.
- `TidalMediaService` applies every backend state directly in build 7 baseline.
- `TidalSessionPlayer.setBackendPlaybackState(NotPlaying)` maps to `Player.STATE_READY` with `playWhenReady=false`.
- `TidalSessionPlayer.loadTrack()` sets new metadata/playlist but does not clear old position until backend position drops to zero; `currentPositionInternal()` prefers `playbackBackend.positionMs` if positive.

**Observed result**

At transition, MediaSession reports:

- `PAUSED(2)` at old terminal position, then
- `BUFFERING(6)` still at old terminal position,
- before the next item reports `PLAYING`.

That is not a clean continuous queue transition. A normal ExoPlayer playlist transition would keep one coherent player timeline and current item index.

**Answer to question 1:** likely yes. The facade is not reliably reporting state/timeline/position during ended/next transitions. It exposes a one-item playlist, always index `0`, and can report previous-track position while loading the next track.

### High — Queue playback is driven outside the Player playlist model

**Where**

- `currentQueue` and `currentQueueIndex` live in `TidalMediaService`.
- `TidalSessionPlayer.getState()` exposes only the current item in `setPlaylist(...)`, never the full queue.
- Next-track advancement is manual: `MediaEnded` event -> service method -> load next manifest -> backend `setMediaSource()`.

**Why this matters**

Media3 already understands playlist/timeline transitions. Untidy currently reimplements this above ExoPlayer and exposes only a single-item shadow timeline to the session. This makes continuous playback look like a stop/pause/load rather than a normal media item transition. It also prevents platform controllers from having a consistent queue model.

### Medium — Service start path is mostly correct for user-initiated start, but transition starts are unnecessary

**Where**

- `MainActivity.startTrackPlayback()` and `startQueuePlayback()` use `ContextCompat.startForegroundService(...)` with a token.
- `TidalMediaService.onStartCommand()` validates tokens and handles queue actions.
- Next-track transitions do not start the service externally; they happen inside the service.

**Answer to question 3**

Initial queue playback is started correctly enough from the foreground UI. The issue is not that the next track uses `startForegroundService()` directly; it is that the service calls `startForeground()` again while backgrounded, and Media3 itself also appears to issue a service/foreground operation after session state churn. Avoiding foreground churn and using a single session-backed player should remove this failure mode.

### Medium — Duplicate notification systems are competing

**Where**

- Custom `publishOngoingActivity()` builds a transport notification and OngoingActivity on channel `tidal_playback`, id `42`.
- Media3 also publishes a media notification on `default_channel_id`, id `1001` in logs.

**Evidence**

Build logs show both local `com.tidal.wear` notifications, one with `isOngoingActivityStyle=true` / not a media notification and another `isMediaNotification=true` with a MediaSession token. Their foreground flags change around the transition.

**Risk**

The platform may demote one notification while Media3 promotes another. On Wear OS this can affect recents/ongoing surfaces and foreground-service accounting.

## Direct answers to requested questions

1. **Is `SimpleBasePlayer` incorrectly reporting state/timeline/commands across ended/next transitions?**  
   Likely yes. It reports a one-item playlist and index `0`, maps backend `NotPlaying` to paused/ready, and can report old backend position during the next item's buffering. The observed `PAUSED` at old terminal position right before next-track load is suspicious and likely contributes to demotion.

2. **Is manual `publishOngoingActivity/startForeground` causing lifecycle issues?**  
   Yes. It is the direct crash site in build 7 (`Service.startForeground() not allowed`). It also competes with Media3's own automatic media notification.

3. **Is the service started incorrectly for queue playback or next-track transitions?**  
   User-initiated `ACTION_PLAY_QUEUE` via `ContextCompat.startForegroundService` is reasonable. Next-track transitions are internal, but they call `publishOngoingActivity()` which re-enters foreground promotion while backgrounded. That is the problematic part.

4. **Are we keeping actual ExoPlayer separate from MediaSession Player in a way that breaks Media3 foreground heuristics?**  
   Yes, probably the core architecture bug. Media3 observes the facade, not the actual audio player.

5. **Should MediaSession be backed directly by ExoPlayer instead of `SimpleBasePlayer` facade?**  
   Yes. That is the cleanest alignment with Media3 best practice. If direct backing is temporarily blocked by manifest-resolution needs, use a forwarding adapter over the real ExoPlayer, not an independent shadow state machine.

6. **What is the smallest refactor likely to fix this without adding cruft?**  
   Replace the shadow-player architecture with one player of record. Prefer direct ExoPlayer session backing, with manifest resolution moved into a small media-source/resolver layer. Remove per-track `startForeground()` and let Media3 manage foreground notification state. Keep OngoingActivity only as a customization layer if it can be attached without foreground re-promotion.

## Recommended implementation plan

### Phase 1 — Stop fighting Media3 foreground management

1. Remove `startForeground()` from per-track `publishOngoingActivity()` calls during continuous playback.
2. Prefer Media3's automatic notification for the foreground media notification.
3. If an OngoingActivity is still needed, publish/update it through `NotificationManager.notify(...)` only after the service is legitimately foreground, and do not call `startForeground()` on every track change.
4. Add logging around `onUpdateNotification`/Media3 notification provider if customization is required, rather than creating a competing notification path.

This may stop the crash, but by itself it does not fix split-brain session state.

### Phase 2 — Make ExoPlayer the MediaSession player of record

1. Move ExoPlayer ownership from `DirectManifestPlaybackBackend` into `TidalMediaService` or expose it from a small `PlaybackEngine` object.
2. Build the session with the actual ExoPlayer:
   - `MediaLibrarySession.Builder(this, exoPlayer, callback).build()`.
3. Represent queue playback as an ExoPlayer playlist/timeline, not a service-only `currentQueue` shadow:
   - For each `TidalTrack`, create a `MediaItem` with stable `mediaId` and metadata.
   - Use a `MediaSource.Factory` / resolver path to turn track ids into DASH/progressive media sources.
   - Pre-resolve the first item before starting playback; prefetch/resolve next items without altering session state.
4. Listen to ExoPlayer's `onMediaItemTransition` and `onPlaybackStateChanged` for diagnostics and UI state only, not to drive a second player state machine.
5. Let ExoPlayer emit `STATE_ENDED` only at true end of queue; normal next-item transitions should be ExoPlayer media item transitions.

### Phase 3 — Preserve existing safety features without reintroducing cruft

Keep:

- `WAKE_LOCK` permission and `exo.setWakeMode(C.WAKE_MODE_NETWORK/LOCAL)`.
- Controller access policy in `onConnect()`.
- App command token checks for explicit service actions.
- Manifest prefetch/cache-key work, but move it behind a small resolver API.
- Diagnostic logs around track id, manifest source, queue index, and player events.

Remove or avoid:

- `serviceOwnedLoadInProgress`-style suppression flags.
- One-time `foregroundStarted` trackers.
- Manual `startForeground()` on each queue transition.
- Shadow position/timeline estimation in `SimpleBasePlayer`.
- Duplicated media notification plus OngoingActivity foreground ownership.

## Validation plan

1. Unit/host tests:
   - Queue of 3 short tracks produces one ExoPlayer playlist with correct media ids/durations.
   - No service-level `startForeground()` is invoked on item transition after initial start.
2. Emulator smoke:
   - Start queue; verify `dumpsys media_session` shows a single active session, coherent current item, and no transient `PAUSED` between items unless user paused.
3. Physical watch validation:
   - Install build.
   - Play the same Julianna Barwick short-track album with screen off/ambient.
   - Capture logcat + `dumpsys media_session` + `dumpsys activity services` through at least 3 transitions.
   - Passing evidence: no `ForegroundServiceStartNotAllowedException`, no `Background started FGS: Disallowed` for Untidy at transitions, session remains active, audio reaches track 3+.

## Bottom line

The current cleaned build removed some speculative patches, but the underlying architecture is still fragile: Media3 sees a custom shadow player while ExoPlayer does the real playback. That makes one-song-and-out failures likely at exactly the ended/next boundary. The smallest durable direction is not more transition guards; it is to collapse to one player of record and let Media3 own session/foreground notification state from that real player.

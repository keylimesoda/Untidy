# Media3/Wear OS background playback research — 2026-06-17

## Scope

Untidy physical-watch validation still stops after one song. This report reviews current official Android/Media3 guidance, AndroidX demo source, Google Horologist Wear media source, and a credible open-source/community pattern to answer what the background playback architecture should be.

Repo context inspected:

- `core/playback/src/main/java/com/tidal/wear/core/playback/TidalMediaService.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/DirectManifestPlaybackBackend.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/tidal/wear/MainActivity.kt`
- `app/src/main/java/com/tidal/wear/playback/NowPlayingStateHolder.kt`

## Executive recommendation

Untidy should move toward a standard **single Media3-owned service/player lifecycle**:

1. Keep `TidalMediaService` as an exported `MediaLibraryService`/`MediaSessionService`-style service for controller discovery, with the existing controller-gating security posture.
2. Let `MediaLibraryService`/`MediaSessionService` own notification and foreground-service state. Do **not** call `startForeground()` manually on each track/transition.
3. Rework playback so the `MediaLibrarySession` is backed by the real `ExoPlayer` (or a very thin `ForwardingPlayer` around it), not a custom `SimpleBasePlayer` that mirrors a second hidden `ExoPlayer`.
4. Put the queue into Media3 `MediaItem`s / ExoPlayer playlist and let ExoPlayer perform transitions. Resolve TIDAL manifests before/inside media-source creation rather than signaling `STATE_ENDED` and manually starting the next item through service actions.
5. Keep Wear-specific audio pieces from Horologist: audio-only renderers, `setAudioAttributes(..., handleAudioFocus = true)`, `setWakeMode(C.WAKE_MODE_NETWORK)`, unsuitable-output suppression/resolver, and audio offload preferences where possible.
6. If Wear Ongoing Activity is required, integrate it through a Media3 notification customization path (`MediaNotification.Provider` or a carefully complete `onUpdateNotification` override), not a separate app-authored notification lifecycle that competes with Media3.

The current strongest suspect is architectural state divergence: Untidy's session exposes a `SimpleBasePlayer`, while actual audio is played by a separate `ExoPlayer` in `DirectManifestPlaybackBackend`. Media3 foreground-service/notification decisions are based on the session player's state, not necessarily on the backend player that is producing audio.

---

## Source findings

### 1. Official Media3 background playback guidance

Source: <https://developer.android.com/media/media3/session/background-playback>

Key guidance/summaries:

- Media3 recommends containing the `Player` and `MediaSession` in a service for background playback: “To enable background playback, you should contain the `Player` and `MediaSession` inside a separate Service.”
- `MediaSessionService` lets external clients such as “Google Assistant, system media controls, media buttons… or companion devices like Wear OS” discover and control playback without the app UI.
- The service lifecycle sample creates `ExoPlayer` and `MediaSession` in `onCreate()`, returns the session from `onGetSession()`, and releases both in `onDestroy()`.
- Notification state is automatic: “All notification updates happen automatically based on the `Player` and `MediaSession` state.”
- If paused/stopped/failed for more than 10 minutes, the service is automatically moved out of foreground so the system can destroy it; playback resumption is the intended restart path.
- `onTaskRemoved()` default behavior keeps the service running when playback is ongoing and stops it otherwise. The docs show `pauseAllPlayersAndStopSelf()` only when the app intentionally wants to stop on task removal.

Relevant source-level Media3 API note from `MediaSessionService.java` (`androidx/media`, release branch):

- `onUpdateNotification(MediaSession, boolean)` is called whenever a notification needs to be shown, updated, or cancelled.
- The default implementation uses a default UI or an app-supplied `MediaNotification.Provider`.
- When playback starts, the service becomes a foreground service; when playback stops, it returns to background.
- If an app overrides `onUpdateNotification`, it must also fully manage foreground start/stop itself.
- `triggerNotificationUpdate()` is only for external events not detectable by the session itself.

**Implication for Untidy:** Media3 should be the notification/foreground owner. Manual `publishOngoingActivity(...); startForeground(...)` before/after loads and at track transitions bypasses the intended state machine and can cause foreground-service crashes or stale/idle decisions.

### 2. Android 17 background-audio hardening guidance

Source: <https://developer.android.com/about/versions/17/changes/bg-audio>

Key guidance/summaries:

- “Use the media3 jetpack library's `MediaSessionService` component to manage background audio playback.”
- Apps using `MediaSessionService` are less likely to be impacted because the library helps manage playback lifecycle.
- Apps not using Media3 must manually start a `mediaPlayback` foreground service, and should do so while the app is foregrounded if background audio may occur.
- At the end of playback or on unrecoverable failure, apps should stop the foreground service and end the media session.

**Implication for Untidy:** The platform direction is away from app-specific FGS juggling and toward Media3’s service lifecycle. Untidy is nominally using `MediaLibraryService`, but because it manually starts foreground and because the real audio player is hidden behind a custom `SimpleBasePlayer`, it is not getting the full benefit of Media3 lifecycle management.

### 3. Media3 playback control guidance

Source: <https://developer.android.com/media/media3/session/control-playback>

Key guidance/summaries:

- The media session is the key routing point for commands from external sources such as headset buttons, Assistant, Android system controls, and Wear OS.
- Media3 automatically updates the media session from player state: “The Media3 library automatically updates the media session using the player's state. As such, you don't need to manually handle the mapping from player to session.”

**Implication for Untidy:** The session should observe the real player state directly. Untidy currently manually maps `PlaybackBackendEvent.StateChanged` into a `SimpleBasePlayer` state. That is a fragile mirror of the actual ExoPlayer state and makes lifecycle bugs more likely at transitions.

### 4. Serving/browsing content with `MediaLibraryService`

Source: <https://developer.android.com/media/media3/session/serve-content>

Key manifest guidance/summaries:

```xml
<service
    android:name=".PlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService"/>
        <action android:name="android.media.browse.MediaBrowserService"/>
    </intent-filter>
</service>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

**Implication for Untidy:** Keeping the service exported is consistent with official media-service discovery, provided controller access is filtered. Untidy’s current exported-service decision plus `onConnect()` controller gating is directionally right.

### 5. Official Wear OS audio guidance

Source: <https://developer.android.com/training/wearables/overlays/audio>

Key guidance/summaries:

- “To provide the best user experience, only play media when Bluetooth headphones or speakers are connected to the watch.”
- After a suitable output is selected, playback on Wear OS is the same as mobile; Google points to ExoPlayer for streaming/downloading and to usual audio-focus best practices.
- Wear OS 5+ provides a system media-output switcher. If no Bluetooth headset is connected, offer to take the user directly there. For devices without it, fall back to `ACTION_BLUETOOTH_SETTINGS`.
- The page explicitly points to Horologist’s `launchOutputSelection()` as an example of output selection.

**Implication for Untidy:** Output handling should be explicit and Wear-native. Use Media3/Horologist output suppression/resolution instead of letting playback silently suppress/fail when the watch speaker or unsuitable output is active.

### 6. Wear Ongoing Activity guidance

Source: <https://developer.android.com/training/wearables/apps/always-on>

Key guidance/summaries:

- On Wear OS 5+, apps can use Ongoing Activity to keep an ongoing task visible and provide a one-tap return path.
- The Ongoing Activity’s touch intent should point to the always-on/activity surface.
- It is built around an ongoing notification builder.

**Implication for Untidy:** Ongoing Activity is appropriate for media UX, but it should be layered onto the Media3 notification path. If Untidy keeps `OngoingActivity`, it should be produced from the same notification lifecycle Media3 uses, not by calling `startForeground()` independently of `MediaSessionService`.

---

## Source findings from AndroidX Media3 demo

### AndroidX `session` demo manifest

Source: <https://github.com/androidx/media/blob/release/demos/session/src/main/AndroidManifest.xml>

Observed pattern:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<service
    android:name=".PlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
  <intent-filter>
    <action android:name="androidx.media3.session.MediaLibraryService"/>
    <action android:name="android.media.browse.MediaBrowserService"/>
    <action android:name="android.media.action.MEDIA_PLAY_FROM_SEARCH"/>
  </intent-filter>
</service>

<receiver android:name="androidx.media3.session.MediaButtonReceiver" android:exported="true">
  <intent-filter>
    <action android:name="android.intent.action.MEDIA_BUTTON" />
  </intent-filter>
</receiver>
```

**Implication for Untidy:** Add/consider `androidx.media3.session.MediaSessionService` and `MediaButtonReceiver` support if playback resumption/media buttons are part of the target. The existing `MediaLibraryService` and legacy browse filters are aligned with the demo.

### AndroidX `DemoPlaybackService`

Source: <https://github.com/androidx/media/blob/release/demos/session_service/src/main/java/androidx/media3/demo/session/DemoPlaybackService.kt>

Observed pattern:

- Service extends `MediaLibraryService`.
- `onCreate()` calls `initializeSessionAndPlayer()` and `setListener(...)`.
- `initializeSessionAndPlayer()` builds one `Player`, builds `MediaLibrarySession.Builder(this, player, callback)`, and sets session activity.
- `buildPlayer()` creates an `ExoPlayer` with `setAudioAttributes(AudioAttributes.DEFAULT, handleAudioFocus = true)`, then wraps it in `CastPlayer` for cast support. There is no separate service-level player mirror.
- `onDestroy()` releases `mediaLibrarySession`, `mediaLibrarySession.player`, clears listener, then calls `super.onDestroy()`.
- The listener handles `onForegroundServiceStartNotAllowedException()` by posting a user notification that takes the user back into the app, not by retrying `startForeground()` in the background.

**Implication for Untidy:** The demo keeps one session-backed player as the source of truth. Untidy should mimic that shape: session owns the real playback player, session activity points to `PlayerActivity`, and foreground lifecycle is handled by Media3.

---

## Source findings from Google Horologist

### Horologist media3 backend docs

Source: <https://github.com/google/horologist/blob/main/docs/media3-backend.md>

Key guidance/summaries:

- The Media3 backend module implements suggested Wear audio approaches from Android’s Wear audio output guidance.
- It builds on Media3/ExoPlayer, described as the standard playback engine for Wear OS media apps.
- Audio offload is emphasized: it allows playback while backgrounded “without waking up,” improving battery life and reducing underruns.

### Horologist sample manifest

Source: <https://github.com/google/horologist/blob/main/media/sample/src/main/AndroidManifest.xml>

Observed pattern:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-feature android:name="android.hardware.type.watch" />
<meta-data android:name="com.google.android.wearable.standalone" android:value="true" />

<service
    android:name=".data.service.playback.PlaybackService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
    </intent-filter>
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>
```

**Implication for Untidy:** Horologist exposes both MediaLibraryService and MediaSessionService actions. Untidy currently exposes `MediaLibraryService` and `MediaBrowserService`; adding `MediaSessionService` is worth considering for compatibility.

### Horologist `PlaybackService`

Source: <https://github.com/google/horologist/blob/main/media/sample/src/main/java/com/google/android/horologist/mediasample/data/service/playback/PlaybackService.kt>

Observed pattern:

```kotlin
class PlaybackService : LifecycleMediaLibraryService() {
    @Inject
    public override lateinit var mediaLibrarySession: MediaLibrarySession

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }
}
```

**Implication for Untidy:** The service itself stays very thin. Player/session construction is injected and lifecycle-managed elsewhere. There is no manual notification code in the service.

### Horologist `LifecycleMediaLibraryService`

Source: <https://github.com/google/horologist/blob/main/media/backend-media3/src/main/java/com/google/android/horologist/media3/service/LifecycleMediaLibraryService.kt>

Observed pattern:

- Extends `MediaLibraryService` and implements `LifecycleOwner`.
- `onGetSession()` returns the `mediaLibrarySession`.
- Calls `super.onStartCommand()` and does not implement custom playback action dispatch in the service.
- Lifecycle dispatcher is used so dependencies can bind to service lifecycle.

**Implication for Untidy:** Horologist’s pattern strongly separates service lifecycle from domain playback commands. Untidy currently uses explicit app-authored service actions (`ACTION_PLAY_QUEUE`, `ACTION_SKIP_NEXT`, etc.) as the playback command path. The more standard route is controllers/session/player commands.

### Horologist playback module / ExoPlayer construction

Source: <https://github.com/google/horologist/blob/main/media/sample/src/main/java/com/google/android/horologist/mediasample/di/PlaybackServiceModule.kt>

Observed pattern:

```kotlin
ExoPlayer.Builder(service, audioOnlyRenderersFactory)
    .setAnalyticsCollector(analyticsCollector)
    .setMediaSourceFactory(mediaSourceFactory)
    .setAudioAttributes(AudioAttributes.DEFAULT, true)
    .setSuppressPlaybackOnUnsuitableOutput(suppressSpeakerPlayback)
    .setWakeMode(C.WAKE_MODE_NETWORK)
    .setLoadControl(loadControl)
    .build()
```

Additional observed details:

- Adds `WearUnsuitableOutputPlaybackSuppressionResolverListener(service)`.
- Enables audio offload preferences with `AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED`.
- Builds `MediaLibrarySession.Builder(service as MediaLibraryService, player, callback).setSessionActivity(...).build()`.
- Releases the session via lifecycle observer on service destroy.
- Uses `WearMedia3Factory.audioOnlyRenderersFactory(...)`, which creates only a `MediaCodecAudioRenderer` for audio-only playback.

**Implication for Untidy:** Untidy already has wake mode, but misses several Wear-specific pieces: audio focus attributes on the real player, unsuitable-output suppression/resolution, audio-only renderer/offload posture, and using the real ExoPlayer as the session player.

### Horologist output switcher

Sources:

- <https://github.com/google/horologist/blob/main/media/media3-outputswitcher/src/main/java/com/google/android/horologist/media3/audio/AudioOutputSelector.kt>
- <https://github.com/google/horologist/blob/main/media/media3-outputswitcher/src/main/java/com/google/android/horologist/media3/audio/BluetoothSettingsOutputSelector.kt>

Observed pattern:

- `AudioOutputSelector` exposes `selectNewOutput(currentAudioOutput)` and `launchSelector()`.
- `BluetoothSettingsOutputSelector` launches output selection and waits up to 15 seconds for a different non-`None` output.

**Implication for Untidy:** If watch playback stops because output becomes unsuitable/suppressed, Horologist’s output resolver path is the closest official/open-source Wear pattern to adopt.

---

## Credible open-source/community signal

### Voice Audiobook Player / AndroidX issue context

Source: <https://github.com/androidx/media/issues/2731>

A current AndroidX issue includes a stack trace from `voice.playback.session.PlaybackService` that uses a `MediaSessionService` and custom `MediaNotificationProvider`. This is not a Wear sample, but it is a credible modern audio-app pattern: a `PlaybackService` hosts a Media3 session service and customizes notification through Media3 notification provider APIs rather than separate app-owned `startForeground()` calls.

### AndroidX issue #3218 community report

Source: <https://github.com/androidx/media/issues/3218>

A 2026 issue describes a production setup “following the session_service demo closely”: `MediaLibraryService`, `ForwardingPlayer` wrapping `ExoPlayer`, `handleAudioFocus = true`, standard `MediaNotification.Provider`, and foreground service. The issue itself is device-specific, but the described architecture matches the official/demo pattern and contrasts with Untidy’s current two-player mirror.

**Implication for Untidy:** If a wrapper is needed, use `ForwardingPlayer` around a real ExoPlayer so Media3 sees the actual player state. Avoid a full `SimpleBasePlayer` façade unless every state, timeline, error, transition, command, and suppression detail is faithfully implemented.

---

## Answers to the requested questions

### 1. Who should own notification/foreground lifecycle?

**Recommendation: Media3 service should own it.**

Use the default `MediaSessionService`/`MediaLibraryService` notification lifecycle or customize through `setMediaNotificationProvider(...)`. Override `onUpdateNotification(session, startInForegroundRequired)` only if Untidy is prepared to fully manage foreground start/stop exactly as Media3 expects.

Untidy should stop using `publishOngoingActivity()` as a routine foreground promotion path during `ACTION_PLAY_TRACK`, `ACTION_PLAY_QUEUE`, pause/resume, and track transitions. That function currently calls `startForeground(MEDIA_NOTIFICATION_ID, builder.build())` directly; this competes with Media3’s own notification manager.

If Ongoing Activity is required, implement it as part of the Media3 notification provider path so the same notification/ID/foreground state is controlled by Media3.

### 2. What should the app do on track transition?

**Recommendation: do not stop/restart foreground or manually publish a new notification at transition.**

Preferred pattern:

1. Represent the queue as a Media3 playlist (`MediaItem`s with stable media IDs and metadata).
2. Resolve each item to a playable `MediaSource` / URI using `MediaSource.Factory`, `ResolvingDataSource`, or callback-driven `onAddMediaItems()` before playback.
3. Let ExoPlayer advance to the next item, emitting `onMediaItemTransition()` and state changes naturally.
4. Use prefetch/preload to hide manifest latency, but keep transition inside the player rather than inside `handleMediaProductEnded() -> skipToNextInQueue() -> playTrack() -> startForeground()`.

For Untidy’s current code, `handleMediaProductEnded()` manually calls `skipToNextInQueue()`, which calls `playTrack()`, which updates the session façade and then loads/plays the backend player. That creates opportunities for the session to look ended/idle/not-playing between tracks, exactly the condition that causes Media3/Wear to drop foreground state.

### 3. How should service be started from Activity vs media controllers?

**Recommendation: Activity should use a `MediaController` for routine playback commands, not custom `startForegroundService()` actions.**

- Activity/UI connects to the service with `SessionToken`/`MediaController` or `MediaBrowser`.
- UI calls controller/player methods: `setMediaItems`, `prepare`, `play`, `pause`, `seekToNextMediaItem`, etc.
- External controllers connect through `onGetSession()` and `onConnect()` gating.
- Media button/resumption support should use `MediaButtonReceiver` / Media3 playback resumption where needed.

Untidy currently starts the service with `ContextCompat.startForegroundService()` for `ACTION_PLAY_TRACK`, `ACTION_PLAY_QUEUE`, pause/resume, and now-playing controls. This is understandable for early bring-up, but it is not the current best-practice route once a Media3 service exists. It also increases exposure to `ForegroundServiceStartNotAllowedException` and token/action drift.

Keep app-authored explicit service actions only for narrow, non-controller bootstrap/probe flows if necessary, and stop them quickly when idle.

### 4. Should the custom `SimpleBasePlayer` wrapper be avoided/reworked?

**Recommendation: yes, rework it.**

`SimpleBasePlayer` is not forbidden, but Untidy’s use is risky because it is a second player façade over a separate real `ExoPlayer`. Media3 foreground decisions, media session state, controllers, notification, and Wear surfaces see the `SimpleBasePlayer`; actual audio, errors, buffering, output suppression, and timeline transitions happen in `DirectManifestPlaybackBackend`’s private `ExoPlayer`.

Better options, in order:

1. **Best:** make the service session use the real `ExoPlayer` directly, with a custom `MediaSource.Factory`/resolver for TIDAL manifests and offline cache routing.
2. **Acceptable:** use `ForwardingPlayer` around the real `ExoPlayer` only to intercept commands or adjust command availability while preserving real player state.
3. **Last resort:** keep `SimpleBasePlayer`, but then fully implement timeline/playlist/current index/media-item transitions/errors/suppression/playback state so it is indistinguishable from the backend. This is more work and higher risk than option 1 or 2.

### 5. How does Horologist handle watch audio/background/outputs?

Horologist’s sample uses:

- A thin `PlaybackService : LifecycleMediaLibraryService` returning an injected `MediaLibrarySession`.
- A real `ExoPlayer` as the session player.
- Audio-only renderers via `WearMedia3Factory`.
- `setAudioAttributes(AudioAttributes.DEFAULT, true)` for audio focus.
- `setWakeMode(C.WAKE_MODE_NETWORK)`.
- `setSuppressPlaybackOnUnsuitableOutput(...)` plus `WearUnsuitableOutputPlaybackSuppressionResolverListener(service)`.
- Audio offload preferences enabled when supported.
- Media3 foreground service types and exported service discovery actions.
- Output selection helpers that launch the system/Bluetooth output selector and wait for a suitable output.

Untidy should borrow this architecture more than its exact UI/data stack.

### 6. Concrete recommendations for Untidy, with code-level pointers

#### A. Stop manual foreground promotion in `TidalMediaService`

File: `core/playback/src/main/java/com/tidal/wear/core/playback/TidalMediaService.kt`

Current pattern:

- `publishOngoingActivity(track, isPlaying)` creates a notification and calls `startForeground(...)`.
- It is called before/after loads and on pause/resume/end.

Recommended direction:

- Remove routine calls to `publishOngoingActivity()` from playback state transitions.
- Let `MediaLibraryService` create/update/cancel the media notification from session player state.
- If Ongoing Activity remains, implement a `MediaNotification.Provider` that builds the notification and applies `OngoingActivity`, then install it with `setMediaNotificationProvider(...)` in `onCreate()`.

#### B. Replace `TidalSessionPlayer : SimpleBasePlayer` with real ExoPlayer session player

Files:

- `TidalMediaService.kt`
- `DirectManifestPlaybackBackend.kt`
- `PlaybackBackend.kt`

Recommended direction:

- Move ExoPlayer construction into the service/session layer, or expose the backend ExoPlayer as the session player.
- Convert TIDAL track IDs into `MediaItem`s with metadata and local configuration/custom cache keys.
- Put queue into `player.setMediaItems(...)` and call `prepare()`/`play()` on the same player the session owns.
- Use `Player.Listener.onMediaItemTransition()` / `onPlaybackStateChanged()` for diagnostics only, not to mirror state into another player.

#### C. Rework manifest resolution around Media3 media-source creation

File: `DirectManifestPlaybackBackend.kt`

Current good pieces to preserve:

- Ordered manifest loading before play.
- Prefetch cache for next track.
- Offline cache key alignment.
- `setWakeMode(C.WAKE_MODE_NETWORK)` / `C.WAKE_MODE_LOCAL`.

Recommended direction:

- Keep `fetchPlaybackManifest(...)`, offline cache lookup, and `canonicalDownloadCacheKeyFactory(...)`.
- Package them behind a `MediaSource.Factory` or resolver used by the single ExoPlayer.
- Ensure next-track manifests are resolved before the transition or preloaded, but do not let session state become `ENDED`/`IDLE` between playable queue items.

#### D. Add Horologist/Wear audio protections

Files likely affected in a future code pass:

- Playback player construction currently in `DirectManifestPlaybackBackend.kt`.
- Gradle dependencies if Horologist is adopted.

Recommended pieces:

```kotlin
ExoPlayer.Builder(service, audioOnlyRenderersFactory)
    .setAudioAttributes(AudioAttributes.DEFAULT, true)
    .setSuppressPlaybackOnUnsuitableOutput(suppressSpeakerPlayback)
    .setWakeMode(C.WAKE_MODE_NETWORK)
    .build()
```

Add `WearUnsuitableOutputPlaybackSuppressionResolverListener(service)` and/or Horologist output selector so the watch prompts for Bluetooth/system media output instead of silently suppressing/stopping playback.

#### E. Move UI controls to MediaController path

Files:

- `app/src/main/java/com/tidal/wear/MainActivity.kt`
- `app/src/main/java/com/tidal/wear/playback/NowPlayingStateHolder.kt`

Current pattern:

- `Context.startTrackPlayback`, `startQueuePlayback`, and `sendServiceAction` use `ContextCompat.startForegroundService()` with custom actions.

Recommended direction:

- UI obtains a `MediaController`/`MediaBrowser` connected to `TidalMediaService`.
- UI calls controller methods for play/pause/resume/skip/seek/queue start.
- Reserve explicit service starts for initial bootstrap only if a controller is not yet connected, and then hand over to controller/session APIs.

#### F. Manifest/service compatibility cleanup

File: `app/src/main/AndroidManifest.xml`

Current:

```xml
<action android:name="androidx.media3.session.MediaLibraryService" />
<action android:name="android.media.browse.MediaBrowserService" />
```

Consider adding:

```xml
<action android:name="androidx.media3.session.MediaSessionService" />
```

Consider adding Media3 media button receiver if playback resumption/media buttons are desired:

```xml
<receiver android:name="androidx.media3.session.MediaButtonReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MEDIA_BUTTON" />
    </intent-filter>
</receiver>
```

Keep exported service plus `onConnect()` gating; this aligns with official/Horologist discovery patterns.

---

## Proposed implementation sequence for the next coding lane

1. Create a small design branch that makes the session player the real ExoPlayer for one simple direct-url/fixture track.
2. Verify Media3 notification/foreground lifecycle with no manual `startForeground()` calls.
3. Add queue as `MediaItem` playlist and verify natural transition on short tracks.
4. Move TIDAL manifest/offline cache resolution into a `MediaSource.Factory` used by that same ExoPlayer.
5. Add Horologist output suppression/resolver and audio focus/offload posture.
6. Only then add Ongoing Activity notification customization through `MediaNotification.Provider`.

## Bottom line

The latest docs and living source examples converge on one pattern: **Media3 service + one real session-backed player + Media3-owned foreground notification + controller-driven commands**. Untidy currently has the service/export pieces, but the two-player façade and manual notification lifecycle are the parts most likely to explain “plays one song then stops” on a physical watch.

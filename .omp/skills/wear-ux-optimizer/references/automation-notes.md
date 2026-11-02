# Device automation notes (learned 2026-11-01; update as evidence accumulates)

## Physical watch (Galaxy Watch SM_R955U, 432x432)

- Auto-sleeps fast; batch an entire journey into ONE adb script (wakeup →
  start → actions → captures), never interleave slow parsing between taps.
  Parse dumps AFTER captures.
- Screen timeout is ~7 s: a successful interactive probe does NOT leave the
  screen up for the NEXT adb call. Wake (keyevent 224) INSIDE the batch
  immediately before each dump/tap sequence, with a wake-retry loop; any
  "probe then batch" split across tool calls races sleep and dies.
- `am start -W` right after `force-stop` gives real cold `TotalTime`; a
  second start resumes (`TotalTime: 0`) — not a relaunch.
- `uiautomator dump` → "null root node" means the screen is asleep: wake
  (`input keyevent 224`) and retry.
- Home scroll position PERSISTS across app exit/re-entry and across screens
  returning to home: always re-dump and locate rows before tapping; never
  reuse stale coordinates.
- Overscroll-capture: swiping up on content already at its BOTTOM can open the
  global Actions sheet (Personalized mixes / On this watch / Settings...)
  instead of scrolling — looks like a scroll in the log, changes the screen.
  Reset to TOP with swipe-down drags first, then scroll up with count limits.
- Scroll deterministically: ~119-122px rows; use slow drags (duration 800ms)
  rather than flings; a mis-mapped gesture nets zero movement.
- Three BACK presses from the keyboard chain keyboard→search→home→APP EXIT.

## System input surfaces (search)

- Tapping Search opens the system keyboard immediately. A mid-height
  left-edge swipe there is captured by swipe-TYPING (it typed "at"), not
  back-navigation. Use KEYCODE_BACK or swipe back only from keyboard-free
  surfaces.

## Emulator (wear6, 454x454, density 320 → 48dp = 96px)

- `pm clear com.tidal.wear` re-triggers the POST_NOTIFICATIONS dialog:
  tap Allow (~227,279) before journeys or the dialog masks state.
- `svc wifi disable` does NOT cut emulator networking. Force network
  failure with `settings put global http_proxy 127.0.0.1:9`; restore with
  `:0`. (Error branch + Retry verified this way.)
- Device-code sessions expire (~10 min): a fresh `pm clear` or Retry yields
  a new code.

## Evidence discipline

- uiautomator dump can fail SILENTLY (null root) while the old XML file stays
  on /sdcard — a subsequent `cat` then parses STALE content and finds phantom
  targets from a previous screen. Always `rm` + re-dump + `[ -s file ]`
  before parsing, per capture.
- One device, one owner: NEVER run two adb jobs against the watch
  concurrently — a force-stop loop cold-starting in parallel silently killed
  a playback battery card mid-window (caught by the NOPLAY guard; wasted 1
  card attempt). Serialize device-touching jobs explicitly.
- uiautomator: only `clickable="true"` node bounds are touch targets; text
  node bounds inside them are NOT the target (nearly mis-reported the
  onboarding Done target as 64x30px from a text node).
- Text matchers: match SUBSTRINGS, never `==` on track/title text — real
  titles carry articles/prefixes ("The Memory of Trees"); an exact-match
  finder silently yields empty targets and aborts healthy scenarios.
- Track Context Play affordance DEPENDS ON SESSION STATE: live media session
  → transport icons with content-desc "Play"/"Previous"/"Next"; dead session
  → TEXT button "Play"+"Start this track" (content-desc empty, Compose nodes
  report clickable=false — tap the text-node center, it's inside the touch
  target). Journey finders must handle BOTH shapes or they die after any
  force-stop.
- Lens/agent contracts: never pass numbers as fact — mark them "reported,
  unverified" or make the lens re-measure from source/screenshots; a
  subagent labeled a coordinator's wrong number "measured".
- Density: 36dp renders as 72px at 320dpi. Convert before judging any
  bounds against the 48dp rule.

## JVM test traps

- `internal` top-level helpers placed in files with Android-dependent static
  init throw `ExceptionInInitializerError` under plain JUnit. Pure logic
  gets its own file (see `core/.../DeviceAuthPolling.kt`).

## Probe reliability (API + window state; learned 2026-11-01 late)

- Never infer dataset size or "store splits" from API probes — validate
  pagination against a server-reported total AND a second cursor encoding.
  TIDAL v2 `links.next` encodes cursors as `page%5Bcursor%5D=`; a naive
  `cursor=` regex silently stops paging. v1 offset paging is unordered
  (duplicates/drops across pages). Both traps produced a false "two
  favorite stores" diagnosis before full paging showed one store (281).
- TIDAL v2 `filter[id]` takes at most 20 values and SILENTLY drops the
  rest (no error). Batch by 20.
- Stranded-state findings on emulators: check
  `dumpsys window | grep mCurrentFocus` first. Phantom system windows
  (RemoteInput picker with no IME installed) fake app bugs. UX-013 was
  withdrawn after the same journey on the physical watch behaved
  correctly (picker → "Back exits search" hint → Home).
- Emulator network presents as metered CELLULAR: download jobs with
  UNMETERED constraints stall forever. Toggle in-app "Download over LTE"
  (re-queues with CONNECTED) rather than fighting job constraints.
- Deep uiautomator XML trees blow Python's recursion limit — iterate with
  `root.iter()`, never recursive walks.
- Shared prefs readable for ground truth: `run-as com.tidal.wear cat
  shared_prefs/untidy_recent_history.xml` (and `untidy_player.xml`,
  `tidal_auth.xml` for tokens when replaying API calls with curl).
- MediaSession binds to a specific Player instance: if the backend RELEASES and
  replaces its ExoPlayer, the session (and every controller on it) silently
  mirrors the dead player. Tell: PlaybackState `updated=` timestamp frozen while
  audio still audible, session NONE/PAUSED while media is heard. Any player
  swap MUST rebind the session (Untidy: PlayerReplaced event -> setPlayer).
- `input keyevent 121/126` (media keys) is dropped by Samsung SM_R955U input
  routing; use `cmd media_session dispatch pause|play` to exercise session
  commands from adb.

## Audio verification rule (learned 2026-11-01, UX-021)
- **Audio features are verified by hearing them.** CPU/utilization metrics can
  profile a pipeline that silently stalls: HE-AAC compressed offload on the
  SM-R955U showed perfect-looking playback (PLAYING, advancing position) while
  `media.audio_flinger` reported the OFFLOAD thread with "0 active" tracks and
  no HAL writes for minutes.
- Ground-truth instruments: `dumpsys media.audio_flinger` (per-thread
  "N Tracks of which M are active", `Total writes`, `Last write occurred`,
  `Frames written`, Signal power history), `dumpsys audio`
  (AudioPlaybackConfiguration state + deviceId route), `media_session`
  PlaybackState. Compare frames-written growth vs position while playing.
- Test BOTH routes (built-in speaker AND A2DP) and the disconnect/reconnect
  transition; Samsung route switching: system panel
  `com.samsung.android.wearable.media.sessions/.OutputSwitcherJetpackInvocation`
  (also tap a connected entry there to disconnect).
- `input keyevent 24/25` adjusts STREAM_MUSIC; media-volume per-device table
  in `dumpsys audio` shows the route-specific levels.

## Session-death testing recipe (debug builds)

- Force an expired+dead refresh token: BACK UP `shared_prefs/tidal_auth.xml`
  first, then replace `refresh_token` with garbage and `expires_at` with 100.
  Inject via `adb push <file> /data/local/tmp/ && run-as com.tidal.wear cp`
  (run-as cannot read /sdcard; stdin pipes to run-as hang — use the tmp dir).
- MUST force-stop and verify with `pidof` empty BEFORE swapping prefs: a
  live process re-persists its in-memory SharedPreferences over your file
  (media service can respawn the process after force-stop — re-check pid).
- Restore afterwards from backup; re-verify with a user-scoped call
  (`library fallback ok tracks=264`).

## Emulator auth persistence

- `scripts/run-wear-emulator.sh` boots `-no-snapshot-load` (no wipe): once
  signed in, the emulator KEEPS auth across boots; `adb install -r` keeps
  data; `pm clear` destroys it (re-auth needed).

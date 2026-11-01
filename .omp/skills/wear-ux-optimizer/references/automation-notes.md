# Device automation notes (learned 2026-11-01; update as evidence accumulates)

## Physical watch (Galaxy Watch SM_R955U, 432x432)

- Auto-sleeps fast; batch an entire journey into ONE adb script (wakeup →
  start → actions → captures), never interleave slow parsing between taps.
  Parse dumps AFTER captures.
- `am start -W` right after `force-stop` gives real cold `TotalTime`; a
  second start resumes (`TotalTime: 0`) — not a relaunch.
- `uiautomator dump` → "null root node" means the screen is asleep: wake
  (`input keyevent 224`) and retry.
- Home scroll position PERSISTS across app exit/re-entry and across screens
  returning to home: always re-dump and locate rows before tapping; never
  reuse stale coordinates.
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

- uiautomator: only `clickable="true"` node bounds are touch targets; text
  node bounds inside them are NOT the target (nearly mis-reported the
  onboarding Done target as 64x30px from a text node).
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

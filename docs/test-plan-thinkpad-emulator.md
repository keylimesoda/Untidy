# ThinkPad Wear OS Emulator Test Plan

> Work-item tracking lives in `docs/work-items/BOARD.md` with source data in `docs/work-items/work-items.json`. Update that tracker as test items move from todo → in-progress → review → done.

Purpose: give Ric a practical, command-ready QA plan for the first Untidy emulator sessions now that KVM works and the remaining host blocker has been narrowed to Fedora SELinux `execheap` policy for Android Emulator/QEMU.

Scope: manual QA on the existing local Wear OS AVD `UntidyWearOS51` for Untidy, a Wear OS TIDAL client. This plan focuses on emulator boot validation, install/launch sanity, TIDAL catalog/auth flows, playback, queue handoff/process-death tolerance, Now Playing actions, favorites, and media-service behavior.

Non-goals for this pass:
- No production code changes.
- No new automated test framework.
- No Gradle work unless the APK is missing or stale.
- No claims about real-watch Bluetooth/audio/Ongoing Activity fidelity until follow-up device testing.

---

## Current status — 2026-06-16

Emulator unblock state:

- KVM/VT-x is now working on the ThinkPad/Fedora host; the remaining blocker is no longer hardware virtualization.
- Android Emulator has been updated to **37.1.4**. The package update alone did not eliminate the Wear AVD crash.
- Crash evidence points to SELinux denying `execheap` for the emulator/QEMU `RenderThread` path, followed by QEMU SIGSEGV.
- A temporary diagnostic `sudo setsebool selinuxuser_execheap on` allowed the Wear AVD `UntidyWearOS51` to boot. This is an unblock for bounded testing, **not** a recommended persistent workstation setting.
- With that temporary toggle active, the debug APK installed and launched successfully once. Untidy reached onboarding/device-code auth polling and logcat showed `authorization_pending`.
- Detailed host policy notes live in [`docs/emulator-selinux-notes.md`](emulator-selinux-notes.md).

Next testing assumption: run a bounded emulator smoke session with `selinuxuser_execheap` enabled only for the session, then turn it back off immediately after collecting the no-login/auth/playback evidence.

---

## 0. Ground rules and artifacts

Run from the repo root:

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh
```

Known package/component names:

```text
Application ID: com.tidal.wear
Launcher activity: com.tidal.wear/.MainActivity
Player activity: com.tidal.wear/.PlayerActivity
Media service component: com.tidal.wear/com.tidal.wear.core.playback.TidalMediaService
Wear AVD: UntidyWearOS51
Debug APK: app/build/outputs/apk/debug/app-debug.apk
```

Useful log tags already present in the codebase:

```text
Untidy/Player
Untidy/DirectPlayback
Untidy/API
```

Recommended terminal layout:

1. Terminal A: emulator process.
2. Terminal B: adb/device commands.
3. Terminal C: filtered logcat.
4. Optional: screenshot/screenrecord capture.

---

## 1. Setup verification after KVM and SELinux emulator unblock

KVM is expected to pass now. If the Wear AVD still segfaults before boot, check `docs/emulator-selinux-notes.md` before changing app code; the known successful diagnostic path was a temporary `selinuxuser_execheap` toggle.

### 1.1 Confirm CPU virtualization is visible to Linux

Expected after BIOS change: `vmx` appears, `/dev/kvm` exists, and `emulator -accel-check` reports usable KVM.

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh

printf 'CPU VMX flag: '
grep -m1 -o '\bvmx\b' /proc/cpuinfo || true

printf '\n/dev/kvm:\n'
ls -l /dev/kvm || true

printf '\nKVM modules:\n'
lsmod | grep -E '^kvm|^kvm_intel' || true

printf '\nlscpu virtualization lines:\n'
lscpu | grep -Ei 'Virtualization|VMX|Model name' || true

printf '\nEmulator acceleration check:\n'
emulator -accel-check
```

Pass criteria:
- `grep` prints `vmx`.
- `/dev/kvm` exists.
- `kvm` and/or `kvm_intel` modules are loaded.
- `emulator -accel-check` reports KVM is installed/usable.

Fail criteria / likely fixes:
- `vmx` missing: BIOS virtualization is still disabled or not exposed.
- `/dev/kvm` missing with `vmx` present: check KVM modules and permissions.
- `/dev/kvm` exists but permission denied: add user to the KVM-capable group, log out/in, or temporarily verify with the correct group membership. Do not permanently loosen `/dev/kvm` permissions as the first fix.

### 1.2 Confirm SELinux boolean state before boot

Use this only to make the host state explicit. Keep the boolean off by default; turn it on only for a bounded diagnostic/smoke session and turn it off again afterward.

```bash
getenforce
sudo getsebool selinuxuser_execheap

# Diagnostic/smoke-session unblock only:
sudo setsebool selinuxuser_execheap on

# After the emulator session:
sudo setsebool selinuxuser_execheap off
sudo getsebool selinuxuser_execheap
```

Pass criteria:
- Default state is documented before the session.
- If enabled for testing, it is disabled again at the end of the session.

Fail criteria:
- Treating `selinuxuser_execheap=on` as a permanent daily-workstation fix without a narrower policy decision.

### 1.3 Confirm Android SDK and AVD are visible

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh

java -version
javac -version
sdkmanager --version
adb version
emulator -version

avdmanager list avd | sed -n '/Name: UntidyWearOS51/,+12p'
sdkmanager --list_installed | grep -E 'platforms;android-35|build-tools;35\.0\.0|system-images;android-35-ext15;android-wear;x86_64|cmdline-tools;latest'
```

Pass criteria:
- Java/Javac are JDK 17 from `scripts/env-android.sh`.
- AVD `UntidyWearOS51` is listed.
- Android 35 / Wear x86_64 system image is installed.

### 1.4 Confirm or rebuild APK only if needed

No Gradle is required for this documentation task, but when the emulator is ready the APK must exist before installation.

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

If the APK is missing or stale:

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh
bash ./gradlew assembleDebug --no-daemon
```

---

## 2. Emulator boot commands and expected checks

### 2.1 Normal interactive boot

Use this first for manual Wear UI testing:

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh
adb kill-server
emulator -avd UntidyWearOS51 -no-snapshot-load -no-boot-anim -gpu swiftshader_indirect
```

Expected:
- Emulator window opens.
- Device appears in `adb devices -l` as `device`, not `offline`.
- Boot completes within a few minutes with KVM enabled.

In another terminal:

```bash
source /home/riclewis/.openclaw/workspace/projects/Untidy/scripts/env-android.sh
adb wait-for-device
adb devices -l
adb shell getprop sys.boot_completed
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abi
adb shell wm size
adb shell dumpsys battery | sed -n '1,80p'
```

Pass criteria:
- `adb devices -l` shows one emulator in `device` state.
- `getprop sys.boot_completed` returns `1`.
- ABI is `x86_64`.
- UI is responsive in the emulator window.

### 2.2 Headless fallback boot

Use only if the UI window is unstable or for repeated install/log checks:

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh
adb kill-server
emulator -avd UntidyWearOS51 -no-window -no-audio -no-snapshot-load -no-boot-anim -gpu swiftshader_indirect
```

Then verify:

```bash
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent KEYCODE_MENU || true
```

### 2.3 Stabilize emulator state before app QA

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell svc wifi enable
adb shell cmd connectivity airplane-mode disable || true
adb shell date
```

Optional screenshots and video capture:

```bash
mkdir -p reports/emulator-smoke
adb exec-out screencap -p > reports/emulator-smoke/boot-screen.png
adb shell screenrecord --time-limit 30 /sdcard/untidy-smoke.mp4
adb pull /sdcard/untidy-smoke.mp4 reports/emulator-smoke/
```

---

## 3. Install/launch baseline

### 3.1 Clean install

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh
adb uninstall com.tidal.wear || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm list packages | grep com.tidal.wear
adb shell dumpsys package com.tidal.wear | grep -E 'versionName|versionCode|userId|pkg=|granted=true' | sed -n '1,120p'
```

Grant notification permission for Android/Wear OS versions that require it:

```bash
adb shell pm grant com.tidal.wear android.permission.POST_NOTIFICATIONS || true
```

### 3.2 Launch app

```bash
adb shell am start -W -n com.tidal.wear/.MainActivity
adb shell pidof com.tidal.wear
adb exec-out screencap -p > reports/emulator-smoke/untidy-launch.png
```

Pass criteria:
- Install succeeds with no `INSTALL_FAILED_*` errors.
- App launches to onboarding if no auth token exists, or Home if previously authenticated.
- No crash dialog.
- `pidof com.tidal.wear` returns a PID.

Fail criteria:
- Crash on first launch.
- Permission prompt blocks core UI and cannot be dismissed/granted.
- App starts in an impossible state, e.g. authenticated-only Home with no token.

---

## 4. Logcat and adb toolbox

### 4.1 Filtered runtime logs

```bash
adb logcat -c
adb logcat -v time \
  'Untidy/Player:D' \
  'Untidy/DirectPlayback:D' \
  'Untidy/API:D' \
  'AndroidRuntime:E' \
  'MediaSessionService:D' \
  '*:S'
```

Broader crash-focused log:

```bash
adb logcat -v time AndroidRuntime:E ActivityTaskManager:W ActivityManager:W '*:S'
```

Persist logs for a full run:

```bash
mkdir -p reports/emulator-smoke
adb logcat -c
adb logcat -v time > reports/emulator-smoke/full-logcat.txt
```

### 4.2 App and service inspection

```bash
adb shell dumpsys activity activities | grep -A40 -E 'com.tidal.wear|Hist #'
adb shell dumpsys activity services com.tidal.wear | sed -n '1,200p'
adb shell dumpsys media_session | grep -A60 -i 'tidal\|untidy\|com.tidal.wear'
adb shell dumpsys notification --noredact | grep -A80 -i 'tidal\|untidy\|com.tidal.wear'
adb shell cmd package resolve-activity --brief com.tidal.wear
```

### 4.3 Manual service action probes

These are useful for no-login and edge-case checks. They should not crash the app/service even when playback cannot start.

```bash
# Malformed/empty track request should fail gracefully, not crash.
adb shell am start-foreground-service \
  -n com.tidal.wear/com.tidal.wear.core.playback.TidalMediaService \
  -a com.tidal.wear.action.PLAY_TRACK

# Explicit fake fixture/probe actions if still wired in debug builds.
adb shell am start-foreground-service \
  -n com.tidal.wear/com.tidal.wear.core.playback.TidalMediaService \
  -a com.tidal.wear.action.PROBE_DEVICE_AUTH

# Transport actions should be safe before playback exists.
adb shell am start-foreground-service -n com.tidal.wear/com.tidal.wear.core.playback.TidalMediaService -a com.tidal.wear.action.PAUSE
adb shell am start-foreground-service -n com.tidal.wear/com.tidal.wear.core.playback.TidalMediaService -a com.tidal.wear.action.RESUME
adb shell am start-foreground-service -n com.tidal.wear/com.tidal.wear.core.playback.TidalMediaService -a com.tidal.wear.action.SKIP_NEXT
adb shell am start-foreground-service -n com.tidal.wear/com.tidal.wear.core.playback.TidalMediaService -a com.tidal.wear.action.SKIP_PREVIOUS
```

Pass criteria:
- No `FATAL EXCEPTION` / `AndroidRuntime` crash.
- Logs explain ignored or unavailable requests.
- Service does not leave a stuck foreground notification for invalid requests.

---

## 5. Prioritized smoke-test matrix

Priority legend:
- P0: first-session blockers; run immediately after emulator boot.
- P1: core product flows; run same day if P0 passes.
- P2: deeper reliability/regression checks; run after basic confidence.

### 5.1 No-login emulator checks

These do not require a TIDAL account. Start from a clean install where possible.

| ID | Priority | Test | Steps | Pass criteria | Fail criteria / notes |
|---|---:|---|---|---|---|
| NL-01 | P0 | Emulator boots with KVM | Run setup commands in sections 1-2. | `sys.boot_completed=1`; adb state is `device`; UI responsive. | Any `offline` hang, KVM error, or boot loop blocks all emulator QA. |
| NL-02 | P0 | Clean install and launch | `adb uninstall`, `adb install`, `am start -W`. | Untidy opens to onboarding/signed-out state with no crash. | Crash, blank screen >30s, or missing launcher activity. |
| NL-03 | P0 | App survives rotation/round display basics | Launch app, interact with onboarding/home/settings if reachable; use touch/rotary/mouse wheel. | UI fits round viewport; primary actions reachable; no clipped required buttons. | Important for Wear; screenshot any clipped CTA. |
| NL-04 | P0 | Notification permission handling | Fresh launch; grant/deny prompt if shown; also run `pm grant`. | App remains usable whether permission is granted or prompt dismissed. | Permission prompt loops or blocks onboarding. |
| NL-05 | P0 | Exported media service defensive behavior | Run malformed service action probes in section 4.3. | No crash; service logs safe ignore/error. | Crash is a release blocker because service is exported. |
| NL-06 | P1 | Signed-out navigation honesty | Attempt Search/Library/Now Playing entry points if visible. | Auth-required flows explain sign-in requirement; no silent empty state that looks broken. | Catalog-only behavior may exist; record actual behavior. |
| NL-07 | P1 | Data extraction / backup-sensitive state sanity | Inspect manifest and app data state after launch. | `allowBackup=false`; no auth created before login. | Emulator cannot prove cloud backup, but can confirm no login token appears pre-auth. |

Useful commands:

```bash
adb shell am start -W -n com.tidal.wear/.MainActivity
adb shell dumpsys package com.tidal.wear | grep -E 'allowBackup|dataExtraction|POST_NOTIFICATIONS|FOREGROUND_SERVICE'
adb logcat -d -v time | grep -E 'AndroidRuntime|Untidy/' | tail -n 120
```

### 5.2 Authenticated TIDAL checks

Prerequisites:
- Valid TIDAL account.
- TIDAL developer credentials configured in the app/build as expected by the repo.
- Network reachable from the emulator.
- Be ready to complete the device-code flow on a phone/desktop browser.

| ID | Priority | Test | Steps | Pass criteria | Fail criteria / notes |
|---|---:|---|---|---|---|
| AUTH-01 | P0 | Device-code auth starts | Fresh app → Sign in. | Watch shows verification URI and user code; logcat has no auth crash. | Missing client credentials or network failure blocks authenticated QA. |
| AUTH-02 | P0 | Device-code auth completes | Enter code on browser; wait for watch polling. | App transitions to Home; token persists across app relaunch. | Timeout must show a clear retry path, not a spinner forever. |
| AUTH-03 | P1 | Auth persistence after process restart | `adb shell am force-stop com.tidal.wear`; relaunch. | Still signed in; Home reachable without re-auth. | Unexpected sign-out means token storage/auth-state issue. |
| AUTH-04 | P1 | Search returns mixed results | Search for a known artist/album/track, e.g. `Pearl Jam`, `Enya`, or a current TIDAL catalog item. | Results include tracks/albums/artists/playlists; rows open the right detail screens. | Empty result with good network needs API/log review. |
| AUTH-05 | P1 | Album pagination | Open an album with >50 tracks/discs if available; scroll to the end. | Tracks beyond first page load; no duplicate/missing obvious page boundary. | Recent code touched album pagination; capture album ID/title if wrong. |
| AUTH-06 | P1 | Library/favorites loads | Open Library/Favorites. | Favorite tracks/albums/artists/playlists render or empty state is explicit. | 401/403 should prompt re-auth, not loop. |
| AUTH-07 | P1 | View Album from Now Playing | Start playback from a searched track; Now Playing → actions sheet → `View album`. | MainActivity opens matching album route and loads tracks. | If metadata missing, app should toast/explain, not crash. |
| AUTH-08 | P1 | View Artist from Now Playing | Same as above, choose `View artist`. | MainActivity opens matching artist route; top tracks/albums load. | Recent feature depends on `artistId` in track metadata. |
| AUTH-09 | P1 | Favorite/like read state | Start a known track; observe heart/like state after load. | State matches TIDAL favorites/library for that track. | If unknown, validate by toggling twice and checking logs/library. |
| AUTH-10 | P1 | Favorite/like write success and revert | Tap like, wait; leave/re-enter Now Playing or Library. Tap again to restore original state. | UI optimistically changes then persists; TIDAL library reflects change; second tap restores. | On API failure, UI must revert and show `Couldn't update like`/clear error. |
| AUTH-11 | P2 | Token expiry / re-auth behavior | If a stale token is available, launch and perform Library/Search/favorite write. | 401/403 surfaces re-auth or clear failure. | Do not brute-force repeated writes. |

Useful commands:

```bash
adb shell am start -W -n com.tidal.wear/.MainActivity
adb logcat -v time 'Untidy/API:D' 'AndroidRuntime:E' '*:S'
adb shell dumpsys connectivity | sed -n '1,120p'
adb shell am force-stop com.tidal.wear && adb shell am start -W -n com.tidal.wear/.MainActivity
```

### 5.3 Playback/audio checks

Emulator audio may not perfectly represent watch hardware, Bluetooth routing, or TIDAL entitlement behavior. Still, it can validate the app/service/session/control state and many playback race conditions.

| ID | Priority | Test | Steps | Pass criteria | Fail criteria / notes |
|---|---:|---|---|---|---|
| PB-01 | P0 | Single track starts playback | Search → tap a playable track. | Player opens; title/artist/artwork show; progress starts; notification/media session appears. | Direct playback load/play failure must show visible error, not silent idle. |
| PB-02 | P0 | Playback error display | Trigger an unavailable track, bad network, or invalid service action. | Now Playing displays a concise error; log has reason; app remains interactive. | Recent code added playback error UI; silent failures are regressions. |
| PB-03 | P1 | Album play-all | Open album → Play all / first track. | Queue starts at expected track; next button moves through album; metadata updates smoothly. | Queue payload fallback and pagination are both relevant here. |
| PB-04 | P1 | Playlist play-all | Open playlist → Play all / first track. | Queue starts; next/previous work; no long stall between tracks. | Capture playlist size; watch cap is expected at 100 known tracks. |
| PB-05 | P1 | Queue transition smoothness | Let one track approach end or repeatedly press Next. | Transition changes title/artwork promptly; no double-play, stuck spinner, or old metadata flash >2s. | Recent work touched load/play race cleanup and prefetch. |
| PB-06 | P1 | Player controls | Test play/pause/resume/next/previous from Now Playing. | Controls update UI, media session, and notification consistently. | Previous at first item should replay/stay sane, not crash. |
| PB-07 | P1 | Background playback | Start playback; press Back/Home to leave app. | Playback continues; media notification/Ongoing Activity remains; relaunch opens/resumes Now Playing state. | Back from Now Playing should exit to watch while playback continues per README. |
| PB-08 | P1 | Notification/media controls | Use notification or `cmd media_session`/keyevents to pause/resume/skip. | Same behavior as in-app controls. | MediaSession command mapping regressions show here. |
| PB-09 | P2 | Network drop while playing/loading | Disable Wi-Fi during load or between tracks, then re-enable. | User sees recoverable error; no crash; retry/play after restore works. | Emulator networking may behave differently than watch LTE/Wi-Fi. |
| PB-10 | P2 | Audio route/settings affordance | From actions sheet, open Bluetooth settings if offered. | Intent opens settings and return path works. | Real Bluetooth validation belongs on watch. |

Useful commands:

```bash
# Media key events often reach the active media session.
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
adb shell input keyevent KEYCODE_MEDIA_NEXT
adb shell input keyevent KEYCODE_MEDIA_PREVIOUS

# Inspect current media session and notification.
adb shell dumpsys media_session | grep -A80 -i 'com.tidal.wear\|tidal\|untidy'
adb shell dumpsys notification --noredact | grep -A100 -i 'com.tidal.wear\|tidal\|untidy'

# Network interruption.
adb shell svc wifi disable
sleep 10
adb shell svc wifi enable
```

### 5.4 Process/lifecycle checks

These are the highest-value reliability tests after basic playback works because recent work included queue payload fallback, direct playback race cleanup, and process-death tolerance.

| ID | Priority | Test | Steps | Pass criteria | Fail criteria / notes |
|---|---:|---|---|---|---|
| LIFE-01 | P0 | App process kill while idle | Launch app, then `am kill` or `am force-stop`; relaunch. | No auth loss; no crash; app returns to correct route/state. | `force-stop` is harsher and clears scheduled/background behavior; use both intentionally. |
| LIFE-02 | P0 | Process-kill queue handoff during album start | Start album Play all, immediately background app and kill app process after service receives intent. | Playback either continues or relaunch reconstructs current track/queue from service/payload; no empty queue crash. | This is the key queue payload fallback check. |
| LIFE-03 | P1 | Process-kill queue handoff during playlist start | Same as LIFE-02 with playlist. | Queue start index and next/previous still sane after relaunch. | Capture logs around `playQueue id=... size=... start=...`. |
| LIFE-04 | P1 | Service survives MainActivity destruction | Start playback; back out of app; verify service/notification; relaunch. | Service continues; Now Playing state reconnects to current media item. | If service dies, confirm whether emulator memory pressure caused it. |
| LIFE-05 | P1 | Direct PlayerActivity route recovery | Start playback; open Now Playing; use View Album/View Artist; back/return. | PlayerActivity and MainActivity route extras do not create duplicate stuck tasks. | Recent View Album/View Artist work uses route extras. |
| LIFE-06 | P1 | Ongoing Activity/media notification lifecycle | Start/pause/resume/stop-ish flows; inspect notification. | Foreground notification appears only when appropriate; controls match state. | Watch-specific Ongoing Activity final pass on real device. |
| LIFE-07 | P1 | Exported service malformed external intents | Send empty/bad `PLAY_TRACK`, `PLAY_QUEUE`, controls before and during playback. | No crash; bad intents ignored with logs; valid playback not corrupted. | Service is exported, so defensive behavior matters. |
| LIFE-08 | P2 | Low-memory pressure | While playback active, background app and use emulator UI or `am send-trim-memory`. | App reconnects cleanly; no stale metadata. | Exact Android shell support varies by API image. |
| LIFE-09 | P2 | Reboot persistence expectation | Start playback/queue; reboot emulator. | App should not pretend old streaming queue is active unless intentionally restored; auth should persist. | Downloads/offline persistence is future work, not required here. |

Commands for lifecycle tests:

```bash
# Prefer graceful background kill first.
adb shell am kill com.tidal.wear

# Harsher app stop; use when explicitly testing cold restart behavior.
adb shell am force-stop com.tidal.wear
adb shell am start -W -n com.tidal.wear/.MainActivity

# Get PIDs. On emulator/userdebug, shell kill may work; if denied, use am kill/force-stop.
adb shell pidof com.tidal.wear
adb shell 'pid=$(pidof com.tidal.wear); [ -n "$pid" ] && kill -9 $pid || true'

# Service state after backgrounding.
adb shell dumpsys activity services com.tidal.wear | sed -n '1,220p'
adb shell dumpsys media_session | grep -A80 -i 'com.tidal.wear\|tidal\|untidy'

# Reboot persistence.
adb reboot
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell am start -W -n com.tidal.wear/.MainActivity
```

Expected log patterns during queue handoff:

```text
Untidy/Player: onStartCommand action=com.tidal.wear.action.PLAY_QUEUE
Untidy/Player: playQueue id=<id> size=<n> start=<index> track=<trackId>
Untidy/Player: backend load start id=<trackId>
Untidy/Player: backend load completed id=<trackId>
Untidy/Player: backend play start
Untidy/Player: backend play completed
Untidy/Player: queue prefetch next index=<n> id=<trackId>
```

Failure signals:

```text
AndroidRuntime: FATAL EXCEPTION
Untidy/Player: playQueue ignored empty queue id=<id> payload=false
Untidy/Player: backend load/play failed
Repeated queue next unavailable when queue should contain tracks
Now Playing shows old track after skip/next completes
```

### 5.5 Follow-up real-watch checks

Emulator pass is necessary but not sufficient for Wear OS release confidence. Run these on a physical watch after emulator P0/P1 flows pass.

| ID | Priority | Test | Why emulator is insufficient | Pass criteria |
|---|---:|---|---|---|
| RW-01 | P0 | Bluetooth audio route with real earbuds | Emulator audio routing is not watch Bluetooth. | TIDAL playback is audible, stable, and controls remain responsive. |
| RW-02 | P0 | Ongoing Activity on actual watch face | Ongoing Activity UX is watch/launcher dependent. | Tile/indicator/notification appears and opens Now Playing correctly. |
| RW-03 | P0 | Hardware crown/rotary scrolling | Mouse wheel is not the same as crown/bezel. | Search, album, playlist, library, artist, and actions lists scroll naturally. |
| RW-04 | P1 | Ambient/AOD Now Playing | Emulator does not prove burn-in/ambient readability. | Ambient mode remains readable and low-noise; no over-bright static art. |
| RW-05 | P1 | Battery/thermal during playback | Emulator cannot model watch battery. | 30+ minutes playback does not overheat or drain abnormally. |
| RW-06 | P1 | Background/service restrictions | OEM Wear builds may be stricter than emulator. | Playback survives normal backgrounding, screen off, and brief network changes. |
| RW-07 | P1 | Real network transitions | Watch Wi-Fi/Bluetooth/LTE handoff differs. | Errors are clear; retry after reconnection works. |
| RW-08 | P2 | Notification permission prompt UX | OEM prompt style may differ. | Permission request does not block first-run auth/playback. |

---

## 6. Detailed first-run script

This is the recommended first successful KVM session from zero to first playback.

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh

# 1. Verify KVM and AVD.
emulator -accel-check
avdmanager list avd | sed -n '/Name: UntidyWearOS51/,+12p'

# 2. Start emulator in Terminal A.
emulator -avd UntidyWearOS51 -no-snapshot-load -no-boot-anim -gpu swiftshader_indirect

# 3. Wait and stabilize in Terminal B.
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# 4. Install and launch.
adb uninstall com.tidal.wear || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.tidal.wear android.permission.POST_NOTIFICATIONS || true
adb shell am start -W -n com.tidal.wear/.MainActivity

# 5. Start focused logs in Terminal C before manual auth/search/playback.
adb logcat -c
adb logcat -v time 'Untidy/Player:D' 'Untidy/DirectPlayback:D' 'Untidy/API:D' 'AndroidRuntime:E' '*:S'
```

Manual sequence after launch:

1. Confirm signed-out/onboarding state.
2. Start TIDAL device auth and complete it in a browser.
3. Search for a known artist/album/track.
4. Start single-track playback.
5. Open album from track and run album Play all.
6. Use Next/Previous and observe queue transition smoothness.
7. Test View Album/View Artist from Now Playing.
8. Toggle like on/off and validate persistence.
9. Background app and verify media notification/Ongoing Activity/session controls.
10. Run process-kill queue handoff checks.

---

## 7. Feature-specific pass/fail criteria

### Install/launch

Pass:
- App installs and opens in under ~10 seconds after boot stabilization.
- No crash logs.
- Round-screen layout exposes the primary CTA.

Fail:
- `INSTALL_FAILED_*`, crash dialog, stuck splash, blank Compose surface, inaccessible CTA.

### Auth

Pass:
- Device code is visible and usable.
- Completion transitions to Home without restart.
- Auth survives relaunch/`am force-stop`.

Fail:
- Infinite polling without timeout, token not persisted, 401/403 loops without re-auth path.

### Search and catalog navigation

Pass:
- Search results load within a reasonable network-dependent time.
- Track/album/artist/playlist rows navigate to the correct detail screens.
- Long album pagination shows later tracks.

Fail:
- Empty states with successful API logs, route mismatch, duplicate/missing pages at 50-track boundaries.

### Album/playlist Play all and queue

Pass:
- Queue starts at intended track.
- Current metadata matches audible/session state.
- Next/previous update within ~2 seconds and never skip unexpectedly.
- Known 100-track cap is acceptable on watch; app must not crash or lie about unavailable tracks.

Fail:
- Queue payload lost between UI and service.
- `playQueue ignored empty queue` for a valid album/playlist.
- Stuck old metadata or duplicate playback after quick controls.

### Player controls

Pass:
- UI, media session, notification, and service logs agree for play/pause/resume/next/previous.
- Background playback continues when leaving Now Playing.

Fail:
- UI says playing while media session is paused, or vice versa.
- Controls disabled/stuck after transition.

### View Album / View Artist

Pass:
- Actions sheet entries route to matching album/artist for the current track.
- Missing metadata shows a toast/explanation and does not crash.

Fail:
- Wrong route, empty ID crash, duplicate task stack that traps Back navigation.

### Favorite/like live validation

Pass:
- Initial heart state matches server/library where known.
- Tap toggles optimistically, then persists.
- On failure, UI reverts and shows a clear error.

Fail:
- UI says liked but server/library did not change.
- Error leaves button permanently loading or wrong state.
- 401/403 loops instead of clear re-auth guidance.

### Playback error display

Pass:
- Invalid/unavailable playback shows clear Now Playing error text.
- Log includes enough reason to debug.
- User can recover by selecting a valid track or retrying after network restore.

Fail:
- Silent no-op, permanent spinner, crash, or error hidden behind actions sheet only.

### Process-kill queue handoff

Pass:
- For album/playlist starts, service can recover queue from direct payload when in-memory `PlaybackQueueStore` is gone.
- After process kill/relaunch, current track and next/previous remain coherent.

Fail:
- Valid queue becomes empty after process boundary.
- Service plays only first track then loses next/previous.
- Relaunch shows no current track while media session is active.

### Ongoing Activity / media controls / exported service behavior

Pass:
- Foreground media notification appears during playback with working previous/play-pause/next.
- Media session responds to hardware/media key events.
- Exported service ignores malformed external intents safely.

Fail:
- Notification missing during active playback.
- Controls crash service or corrupt queue.
- External empty intents start stuck foreground service or crash.

---

## 8. Blockers and unknowns

Known current blocker:
- KVM now works. The current host-side blocker is SELinux `execheap` denial for the Android Emulator/QEMU RenderThread path. A temporary `selinuxuser_execheap` diagnostic toggle allowed the Wear AVD to boot, but broad persistent enablement is not recommended.

Potential blockers after the temporary emulator unblock:
- KVM permissions: if this regresses, `/dev/kvm` may exist but user may not have access until group membership/session refresh.
- Wear system image first boot may be slow; do not judge app behavior until `sys.boot_completed=1` and launcher is responsive.
- TIDAL auth may require valid developer credentials and scopes; old tokens may lack newer playlist/favorite scopes.
- TIDAL playback may fail for account entitlement, regional catalog, DRM/codec, or emulator audio stack reasons. Separate app/service errors from service entitlement errors using `Untidy/DirectPlayback` logs.
- Emulator may not faithfully reproduce watch Bluetooth, battery, thermal, AOD, Ongoing Activity launcher integration, or rotary input.
- Downloads/offline, queue UI, and add-to-playlist are currently specs/follow-up features; this plan includes their adjacent regression surfaces but should not mark unimplemented spec work as emulator failures.

Unknowns to record during the first session:
- Does the Wear OS emulator image expose enough media-session/Ongoing Activity behavior to validate notification controls meaningfully?
- Does direct TIDAL playback work on the emulator audio stack, or do we only get service/session/error validation?
- Which real catalog items are best stable fixtures for long album pagination, playlist queue, and favorite toggling?
- Whether `adb shell kill -9 $(pidof com.tidal.wear)` is permitted on this emulator image; if not, use `am kill` and `force-stop` variants.

---

## 9. Ordered TODO list after emulator unblock

### 9.1 Immediate no-login tests

1. **Host state capture:** record KVM pass, emulator version 37.1.4, SELinux mode, and `selinuxuser_execheap` before toggling.
2. **Bounded Wear AVD boot:** if needed, enable `selinuxuser_execheap` temporarily; boot `UntidyWearOS51`; prove `sys.boot_completed=1` and adb state `device`.
3. **Clean install/launch:** install `app-debug.apk`, launch `com.tidal.wear/.MainActivity`, capture screenshot/logcat, and confirm onboarding/device-code polling appears.
4. **Malformed exported-service probes:** run the invalid service intents from section 4.3 and confirm no crash/stuck foreground service.
5. **Turn SELinux diagnostic off:** after the bounded session, run `sudo setsebool selinuxuser_execheap off` and record the final state.

### 9.2 Authenticated tests

1. **AUTH-01/AUTH-02 — TIDAL device-code auth:** complete sign-in and verify Home state plus token persistence after relaunch.
2. **PB-01/PB-02 — Single-track playback and error surfacing:** search a known playable track, start playback, verify Now Playing metadata/progress/media session, and confirm playback errors render visibly.
3. **PB-03 + LIFE-02 — Album Play all with process-kill queue handoff:** start an album queue, inspect `playQueue` logs, kill/relaunch/background as appropriate, and verify next/previous still work.
4. **PB-04 + LIFE-03 — Playlist Play all with process-kill queue handoff:** repeat with a playlist and record queue size/cap behavior.
5. **AUTH-07/AUTH-08/AUTH-09/AUTH-10 — Now Playing actions:** validate View Album, View Artist, favorite/like read state, favorite/like write success, and rollback on failure.
6. **PB-07/PB-08 — Background media behavior:** verify notification/media controls, Ongoing Activity surface where emulator exposes it, and relaunch-to-current playback state.

### 9.3 Feature implementation order

1. **Current Queue UI** — implement first because playback/queue handoff is now central to emulator validation and user confidence. Spec: `docs/spec-current-queue-ui.md`.
2. **Add to Playlist** — implement after favorite/like auth/write flows are validated, reusing the same authenticated API/error/optimistic-update lessons. Spec: `docs/spec-add-to-playlist.md`.
3. **Downloads/offline playback spike** — implement last because it needs explicit storage, entitlement, background, and real-watch validation beyond emulator smoke. Spec: `docs/spec-downloads-offline-playback.md`.
4. **Keep View Album/View Artist and favorite/like toggles in regression** — these are implemented but still need live authenticated validation on the emulator. Spec: `docs/spec-view-album-artist-from-now-playing.md`.

### 9.4 SELinux/emulator policy follow-up

1. Keep `selinuxuser_execheap` off by default; use on/off only for bounded emulator sessions.
2. Capture exact AVC, journal, and coredump evidence with the boolean off.
3. Research a narrower local SELinux policy/module scoped to Android Emulator/QEMU instead of broad user-domain `execheap`.
4. Prefer an isolated test host/VM if persistent policy relaxation is required.
5. Retest with the boolean off after Android Emulator/Fedora SELinux policy updates.

If any P0 item fails, stop and debug before moving to playlist queue, like toggles, or real-watch testing.

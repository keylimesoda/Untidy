# Emulator SELinux Notes

_Last updated: 2026-06-16_

## Current emulator unblock status

- KVM/VT-x is now visible and usable on the ThinkPad/Fedora host.
- Android Emulator was updated from the earlier 36.x package to **37.1.4**.
- The Wear OS AVD `UntidyWearOS51` still crashed under normal SELinux policy with timestamp-matched AVC denials for `execheap` from the emulator/QEMU `RenderThread` path.
- A temporary diagnostic toggle of `selinuxuser_execheap` allowed the Wear AVD to boot.
- With that temporary toggle active, Untidy installed and launched once on the Wear AVD. The first-run UI reached onboarding/device-auth polling and showed `authorization_pending`, confirming the app/APK path works at least to first launch.

## Important security posture

Do **not** leave `selinuxuser_execheap` broadly enabled on the daily workstation as the standing fix. It is useful as a narrow diagnostic/unblock for a manual emulator session, but it relaxes policy for the user domain more broadly than this app test needs.

Use this as a temporary on/off diagnostic only:

```bash
sudo getsebool selinuxuser_execheap
sudo setsebool selinuxuser_execheap on
# Run the bounded emulator/app smoke session.
sudo setsebool selinuxuser_execheap off
sudo getsebool selinuxuser_execheap
```

Avoid `setsebool -P` unless a deliberate host policy decision is made later.

## Recommended emulator launch for the next bounded session

```bash
cd /home/riclewis/.openclaw/workspace/projects/Untidy
source scripts/env-android.sh

adb kill-server
emulator -avd UntidyWearOS51 \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -gpu swiftshader_indirect \
  -no-snapshot-load
```

Then verify:

```bash
adb wait-for-device
adb devices -l
adb shell getprop sys.boot_completed
adb shell getprop ro.build.version.release
adb shell wm size
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.tidal.wear/.MainActivity
adb shell pidof com.tidal.wear
adb logcat -d -v time | grep -E 'Untidy/|AndroidRuntime|authorization_pending' | tail -n 160
```

## SELinux/emulator policy follow-up

Ordered follow-up options:

1. **Keep using the temporary on/off boolean only for bounded smoke sessions** until a safer policy is proven.
2. **Capture exact AVC evidence** around emulator startup after a clean failure with the boolean off:
   ```bash
   sudo ausearch -m avc -ts recent | grep -Ei 'execheap|qemu|RenderThread' -C 3
   journalctl --since '20 minutes ago' --no-pager | grep -Ei 'avc:.*execheap|ANOM_ABEND.*qemu|qemu-system.*sig=11'
   coredumpctl list --since '20 minutes ago' | grep -Ei 'qemu|emulator'
   ```
3. **Research a narrower local SELinux policy/module** scoped to the Android Emulator/QEMU path instead of enabling the user-domain boolean broadly.
4. **Prefer an isolated test host/VM** for persistent emulator policy relaxations if a narrow policy is not practical.
5. **Watch Android Emulator release notes / Fedora SELinux policy updates** and retest with the boolean off after updates.

## What is now unblocked

The emulator is no longer blocked by KVM or APK install/launch. The next useful work is product QA, in order:

1. Re-run no-login install/launch and capture screenshot/logcat.
2. Complete authenticated TIDAL device-code sign-in.
3. Validate single-track playback and visible playback error handling.
4. Validate album Play all plus process-kill queue handoff.
5. Validate Now Playing View Album/View Artist and favorite/like toggles.

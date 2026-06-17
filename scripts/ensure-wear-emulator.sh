#!/usr/bin/env bash
# Ensure the Untidy Wear OS emulator is booted and usable for runtime validation.
#
# This is intentionally a thin self-healing wrapper around run-wear-emulator.sh:
# - If a booted Wear emulator is already attached, it exits successfully.
# - If no booted Wear emulator is attached, it starts run-wear-emulator.sh in the background.
# - run-wear-emulator.sh owns the temporary SELinux selinuxuser_execheap toggle and restores it on exit.
# - This script verifies boot completion and, optionally, installs/launches the debug APK.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=env-android.sh
source "$REPO_ROOT/scripts/env-android.sh" >/dev/null

AVD_NAME="${UNTIDY_AVD_NAME:-UntidyWearOS51}"
BOOT_TIMEOUT_SECONDS="${UNTIDY_EMULATOR_BOOT_TIMEOUT_SECONDS:-180}"
LOG_DIR="$REPO_ROOT/reports"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/wear-emulator-self-heal.log"

log() {
  printf '[%s] %s\n' "$(date -Is)" "$*" | tee -a "$LOG_FILE" >&2
}

booted_wear_serial() {
  adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}' | while read -r serial; do
    [[ -n "$serial" ]] || continue
    boot="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    wear="$(adb -s "$serial" shell pm has-feature android.hardware.type.watch 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot" == "1" && "$wear" == "true" ]]; then
      echo "$serial"
      return 0
    fi
  done
}

any_emulator_serial() {
  adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1; exit}'
}

serial="$(booted_wear_serial | head -n1 || true)"
if [[ -n "$serial" ]]; then
  log "Wear emulator already booted: $serial"
else
  if pgrep -af "qemu-system|emulator.*-avd $AVD_NAME|Android/Sdk/emulator" >/dev/null 2>&1; then
    log "Emulator process already running; waiting for ADB boot completion."
  else
    log "No booted Wear emulator found; starting $AVD_NAME via scripts/run-wear-emulator.sh"
    nohup "$REPO_ROOT/scripts/run-wear-emulator.sh" >>"$LOG_FILE" 2>&1 &
    log "Started emulator wrapper pid=$!"
  fi

  deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    serial="$(booted_wear_serial | head -n1 || true)"
    [[ -n "$serial" ]] && break
    sleep 3
  done
fi

if [[ -z "${serial:-}" ]]; then
  log "ERROR: Wear emulator did not reach boot-complete within ${BOOT_TIMEOUT_SECONDS}s"
  adb devices -l | tee -a "$LOG_FILE" >&2 || true
  exit 2
fi

log "Wear emulator ready: $serial"
adb -s "$serial" shell getprop ro.build.version.release | sed 's/^/[android] /' | tee -a "$LOG_FILE" >&2
adb -s "$serial" shell wm size | sed 's/^/[wm] /' | tee -a "$LOG_FILE" >&2

if [[ "${UNTIDY_EMULATOR_INSTALL_LAUNCH:-0}" == "1" ]]; then
  apk="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
  if [[ ! -f "$apk" ]]; then
    log "Debug APK missing; building :app:assembleDebug"
    (cd "$REPO_ROOT" && bash ./gradlew :app:assembleDebug --no-daemon)
  fi
  log "Installing debug APK"
  adb -s "$serial" install -r "$apk" | tee -a "$LOG_FILE" >&2
  log "Launching Untidy"
  adb -s "$serial" shell am start -W -n com.tidal.wear/.MainActivity | tee -a "$LOG_FILE" >&2
  sleep 3
  adb -s "$serial" shell pidof com.tidal.wear | sed 's/^/[untidy pid] /' | tee -a "$LOG_FILE" >&2 || true
fi

printf '%s\n' "$serial"

#!/usr/bin/env bash
# Run the Untidy Wear OS emulator with the temporary SELinux execheap workaround.
#
# Why this exists:
#   On this Fedora ThinkPad, Android Emulator/QEMU crashes under SELinux with:
#     AVC denied { execheap } ... comm="RenderThread" ... qemu-system-x86_64-headless
#   Temporarily enabling selinuxuser_execheap lets the emulator boot.
#
# Safety behavior:
#   - Records the current selinuxuser_execheap value.
#   - Enables it only for this emulator run.
#   - Restores the original value when the emulator exits or the script is interrupted.
#   - Does NOT use setsebool -P; nothing is made persistent across reboot.
#
# Usage:
#   cd ~/.openclaw/workspace/projects/Untidy
#   scripts/run-wear-emulator.sh
#
# Optional: pass extra emulator args after --
#   scripts/run-wear-emulator.sh -- -wipe-data

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=env-android.sh
source "$REPO_ROOT/scripts/env-android.sh" >/dev/null

AVD_NAME="${UNTIDY_AVD_NAME:-UntidyWearOS51}"
DEFAULT_ARGS=(
  -avd "$AVD_NAME"
  -no-window
  -no-audio
  -no-boot-anim
  -gpu swiftshader_indirect
  -no-snapshot-load
)

EXTRA_ARGS=()
if [[ "${1:-}" == "--" ]]; then
  shift
  EXTRA_ARGS=("$@")
elif [[ $# -gt 0 ]]; then
  EXTRA_ARGS=("$@")
fi

if ! command -v getsebool >/dev/null 2>&1 || ! command -v setsebool >/dev/null 2>&1; then
  echo "SELinux tools not found; running emulator without SELinux toggle." >&2
  exec emulator "${DEFAULT_ARGS[@]}" "${EXTRA_ARGS[@]}"
fi

original="$(getsebool selinuxuser_execheap 2>/dev/null | awk '{print $3}')"
if [[ "$original" != "on" && "$original" != "off" ]]; then
  echo "Could not read selinuxuser_execheap state; got: ${original:-<empty>}" >&2
  exit 1
fi

restore() {
  local rc=$?
  if [[ "$original" == "off" ]]; then
    echo "Restoring selinuxuser_execheap=off"
    sudo setsebool selinuxuser_execheap off || true
  else
    echo "Leaving selinuxuser_execheap=on because it was already on before this run"
  fi
  exit "$rc"
}
trap restore EXIT INT TERM

if [[ "$original" == "off" ]]; then
  echo "Temporarily enabling selinuxuser_execheap for Android Emulator/QEMU."
  echo "This is intentionally non-persistent and will be restored when the emulator exits."
  sudo setsebool selinuxuser_execheap on
else
  echo "selinuxuser_execheap is already on before emulator launch."
fi

echo "Launching $AVD_NAME..."
emulator "${DEFAULT_ARGS[@]}" "${EXTRA_ARGS[@]}"

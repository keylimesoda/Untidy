#!/usr/bin/env bash
# Source this before building/debugging Untidy from the CLI:
#   source scripts/env-android.sh
#   bash ./gradlew assembleDebug
#
# Host-agnostic: probes known JDK/SDK locations, first match wins.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  for candidate in \
    "$HOME/Work/toolchains/jdk21" \
    "$REPO_ROOT/../toolchains/jdk21" \
    "$REPO_ROOT/../../tools/jdks/jdk-17" \
    "$REPO_ROOT/../../../tools/jdks/jdk-17"; do
    if [[ -x "$candidate/bin/java" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [[ -z "${ANDROID_HOME:-}" || ! -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
  for candidate in \
    "$HOME/Work/toolchains/android-sdk" \
    "$REPO_ROOT/../toolchains/android-sdk" \
    "$HOME/Android/Sdk"; do
    if [[ -x "$candidate/platform-tools/adb" ]]; then
      export ANDROID_HOME="$candidate"
      break
    fi
  done
fi

export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$HOME/.local/bin:$PATH"

if [[ ! -x "${JAVA_HOME:-}/bin/java" ]]; then
  echo "No JDK found (set JAVA_HOME or install ~/Work/toolchains/jdk21)" >&2
  return 1 2>/dev/null || exit 1
fi
if [[ ! -x "${ANDROID_HOME:-}/platform-tools/adb" ]]; then
  echo "No Android SDK found (set ANDROID_HOME or install ~/Work/toolchains/android-sdk)" >&2
  return 1 2>/dev/null || exit 1
fi

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
java -version 2>&1 | head -n 1
adb version | head -n 1

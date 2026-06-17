#!/usr/bin/env bash
# Source this before building/debugging Untidy from the CLI on the ThinkPad:
#   source scripts/env-android.sh
#   bash ./gradlew assembleDebug

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE_ROOT="$(cd "$REPO_ROOT/../.." && pwd)"

export JAVA_HOME="$WORKSPACE_ROOT/tools/jdks/jdk-17"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "Missing local JDK at $JAVA_HOME" >&2
  return 1 2>/dev/null || exit 1
fi
if [[ ! -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  echo "Missing Android SDK/platform-tools at $ANDROID_HOME" >&2
  return 1 2>/dev/null || exit 1
fi

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
java -version 2>&1 | head -n 1
adb version | head -n 1

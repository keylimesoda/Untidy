#!/bin/bash
# UX/perf pause-path reproduction matrix on the physical watch.
# Starts playback cold, then exercises on-screen pause/resume and session
# key events, capturing session state + audio player state at each step.
W=${WATCH_SERIAL:-192.168.183.172:36843}
A() { adb -s $W shell "$@"; }
dump() { A "rm -f /sdcard/qa.xml; uiautomator dump /sdcard/qa.xml >/dev/null 2>&1; [ -s /sdcard/qa.xml ]"; adb -s $W pull /sdcard/qa.xml /tmp/qa.xml >/dev/null 2>&1; }
st() { A dumpsys media_session | grep -m1 -oE 'state=[A-Z]+\([0-9]\), position=[0-9]+'; }
players() { A dumpsys audio | grep -A1 'AudioPlaybackConfiguration' | grep -E 'state:|playerId' | tr '\n' ' '; echo; }

A am force-stop com.tidal.wear; sleep 3
B=""
for i in 1 2 3; do
  A input keyevent 224; sleep 1
  A am start -n com.tidal.wear/.MainActivity >/dev/null 2>&1; sleep 2
  dump && B=$(python3 scripts/perf/match.py /tmp/qa.xml card)
  [ -n "$B" ] && break
  A input keyevent 4; sleep 1
done
echo "CARD=$B"; [ -z "$B" ] && exit 1
A input tap $B; sleep 2
P=""
for j in 1 2 3; do
  dump && P=$(python3 scripts/perf/match.py /tmp/qa.xml play)
  [ -n "$P" ] && break
  A input keyevent 224; sleep 1
done
echo "PLAY=$P"; [ -z "$P" ] && exit 1
A input tap $P; sleep 6
echo "P0_start $(st)"
sleep 4; echo "P1_after4s $(st)"
players

# --- on-screen pause ---
PA=""
for j in 1 2 3; do
  dump && PA=$(python3 scripts/perf/match.py /tmp/qa.xml pause)
  [ -n "$PA" ] && break
  A input keyevent 224; sleep 1
done
echo "PAUSE_BTN=$PA"
if [ -n "$PA" ]; then
  A input tap $PA; sleep 2; echo "S1_onscreen_pause $(st)"; players
  sleep 4; echo "S2_hold4s $(st)"
  dump && P2=$(python3 scripts/perf/match.py /tmp/qa.xml play) || P2=""
  echo "RESUME_BTN=$P2"
  [ -n "$P2" ] && { A input tap $P2; sleep 3; echo "S3_resumed $(st)"; players; }
fi

# --- session key events ---
sleep 3; echo "R0_prekey $(st)"
A input keyevent 121; sleep 2; echo "K1_media_pause $(st)"; players
sleep 3; echo "K2_hold3s $(st)"
A input keyevent 126; sleep 3; echo "K3_media_play $(st)"; players

# --- backgrounded on-screen pause ---
A input keyevent 3; sleep 2
A input keyevent 224; sleep 1
A am start -n com.tidal.wear/.MainActivity >/dev/null 2>&1; sleep 2
echo "BG0_back_foreground $(st)"
dump && PA2=$(python3 scripts/perf/match.py /tmp/qa.xml pause) || PA2=""
echo "BG1_pause_btn=$PA2"
[ -n "$PA2" ] && { A input tap $PA2; sleep 2; echo "BG2_paused $(st)"; players; }
echo REPRO-DONE=$(date +%H:%M:%S)

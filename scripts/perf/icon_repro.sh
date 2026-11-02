#!/bin/bash
# UX-027 repro: start playback from a list, then check whether the player
# screen's transport button reflects isPlaying. PLAYING session + "Play"
# content-desc = bug reproduced.
W=${WATCH_SERIAL:-192.168.183.172:36843}
A() { adb -s $W shell "$@"; }
dump() { A "rm -f /sdcard/qa.xml; uiautomator dump /sdcard/qa.xml >/dev/null 2>&1; [ -s /sdcard/qa.xml ]"; adb -s $W pull /sdcard/qa.xml /tmp/qa.xml >/dev/null 2>&1; }
descs() { python3 -c "
import xml.etree.ElementTree as ET
try: root=ET.parse('/tmp/qa.xml').getroot()
except Exception: print('NO-DUMP'); raise SystemExit
print(','.join(sorted({n.get('content-desc') for n in root.iter() if n.get('content-desc') in ('Play','Pause','Previous','Next','Queue')})))"; }

A am force-stop com.tidal.wear; sleep 3
# navigate: home -> Library -> Liked tracks -> first row
A input keyevent 224; sleep 1
A am start -n com.tidal.wear/.MainActivity >/dev/null 2>&1; sleep 2
dump; L=$(python3 scripts/perf/match.py /tmp/qa.xml lib 2>/dev/null || true)
if [ -z "$L" ]; then
  for i in 1 2 3 4; do A input swipe 227 380 227 150 800 >/dev/null; sleep 1; dump && L=$(adb -s $W exec-out cat /sdcard/qa.xml | python3 -c "
import sys,xml.etree.ElementTree as ET,re
try: root=ET.fromstring(sys.stdin.read())
except Exception: sys.exit(0)
for n in root.iter():
    b=n.get('bounds')
    if b and n.get('clickable')=='true' and any((m.get('text') or '')=='Library' for m in n.iter()):
        m=re.findall(r'\d+',b); print((int(m[0])+int(m[2]))//2,(int(m[1])+int(m[3]))//2); break"); [ -n "$L" ] && break; done
fi
echo LIB=$L; [ -z "$L" ] && exit 1
A input tap $L; sleep 3
A input swipe 227 380 227 150 800 >/dev/null; sleep 1; A input swipe 227 380 227 150 800 >/dev/null; sleep 1
dump; K=$(adb -s $W exec-out cat /sdcard/qa.xml | python3 -c "
import sys,xml.etree.ElementTree as ET,re
try: root=ET.fromstring(sys.stdin.read())
except Exception: sys.exit(0)
for n in root.iter():
    b=n.get('bounds')
    if b and n.get('clickable')=='true' and any('Liked' in (m.get('text') or '') for m in n.iter()):
        m=re.findall(r'\d+',b); print((int(m[0])+int(m[2]))//2,(int(m[1])+int(m[3]))//2); break"); echo LIKED=$K; [ -z "$K" ] && exit 1
A input tap $K; sleep 4
dump; R=$(adb -s $W exec-out cat /sdcard/qa.xml | python3 -c "
import sys,xml.etree.ElementTree as ET,re
try: root=ET.fromstring(sys.stdin.read())
except Exception: sys.exit(0)
for n in root.iter():
    b=n.get('bounds')
    if b and n.get('clickable')=='true':
        c=re.findall(r'\d+',b)
        if int(c[1])>220 and any((m.get('text') or '') for m in n.iter()):
            print((int(c[0])+int(c[2]))//2,(int(c[1])+int(c[3]))//2); break"); echo ROW=$R; [ -z "$R" ] && exit 1
A logcat -c
A input tap $R
# watch the player screen right as playback starts
for i in 1 2 3 4 5; do sleep 2; dump; echo "t$i=$(date +%H:%M:%S) session=$(A dumpsys media_session | grep -m1 -oE 'state=[A-Z]+\([0-9]\), position=[0-9]+') transport=[$(descs)]"; done
echo REPRO-DONE=$(date +%H:%M:%S)

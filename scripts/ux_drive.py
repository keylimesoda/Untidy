#!/usr/bin/env python3
"""Drive the Wear watch UI reliably: wake, dump (retry), tap by text, swipe, screenshot.

Wear traps this solves (see .omp/skills/wear-ux-optimizer/references/automation-notes.md):
- ~7s screen timeout + ambient mode -> empty/failed uiautomator dumps
- uiautomator intermittently returns "null root node" during animations
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SER = "192.168.183.172:36843"


def sh(*args, binary=False):
    r = subprocess.run(["adb", "-s", SER, *args], capture_output=True)
    if r.returncode != 0 and not binary:
        sys.stderr.write(f"adb {' '.join(args)}: {r.stderr.decode(errors='replace')[:120]}\n")
    return r.stdout if binary else r.stdout.decode(errors="replace")


def wake():
    sh("shell", "input", "keyevent", "224")
    time.sleep(0.6)


def dump(root_retries=4):
    for attempt in range(root_retries):
        sh("shell", "uiautomator", "dump", "/sdcard/d.xml")
        raw = sh("shell", "cat", "/sdcard/d.xml")
        try:
            root = ET.fromstring(raw)
        except ET.ParseError:
            wake()
            time.sleep(1.2)
            continue
        nodes = [n for n in root.iter()]
        if any((n.get("text") or n.get("content-desc") or n.get("contentDescription")) for n in nodes):
            return nodes
        wake()
        time.sleep(1.2)
    return []


def bounds_center(node):
    c = re.findall(r"\d+", node.get("bounds") or "")
    if len(c) != 4:
        return None
    return (int(c[0]) + int(c[2])) // 2, (int(c[1]) + int(c[3])) // 2


def find(text, nodes=None):
    nodes = nodes if nodes is not None else dump()
    for n in nodes:
        label = (n.get("text") or "") + " " + (n.get("content-desc") or "")
        if text.lower() in label.lower():
            return n
    return None


def tap_text(text, scrolls=3):
    for attempt in range(3):
        nodes = dump()
        node = find(text, nodes)
        swipe_tries = 0
        while node is None and swipe_tries < scrolls:
            sh("shell", "input", "swipe", "227", "400", "227", "200", "500")
            swipe_tries += 1
            time.sleep(0.7)
            node = find(text)
        if node is not None:
            center = bounds_center(node)
            if center:
                sh("shell", "input", "tap", str(center[0]), str(center[1]))
                print(f"tapped '{text}' at {center}")
                return True
        wake()
        time.sleep(1)
    print(f"MISS '{text}'")
    return False


def shot(path):
    wake()
    time.sleep(0.5)
    with open(path, "wb") as f:
        f.write(sh("exec-out", "screencap", "-p", binary=True))
    print(f"shot {path}")


def swipe_up(times=1):
    for _ in range(times):
        sh("shell", "input", "swipe", "227", "400", "227", "200", "500")
        time.sleep(0.6)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "tap":
        sys.exit(0 if tap_text(sys.argv[2]) else 1)
    elif cmd == "shot":
        shot(sys.argv[2])
    elif cmd == "swipe":
        swipe_up(int(sys.argv[2]) if len(sys.argv) > 2 else 1)
    elif cmd == "texts":
        for n in dump():
            t = n.get("text") or n.get("content-desc")
            if t and t.strip():
                print(t)
    else:
        sys.exit("usage: ux_drive.py tap|shot|swipe|texts ...")

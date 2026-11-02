# Perf experiment ledger

Format per `skill://wear-perf-optimizer` SKILL.md "Experiment record".
Pre-register BEFORE editing; verdicts ∈ accept | revert | inconclusive.

## Accepted

(none yet — EXP-011/EXP-012 from the UX ledger carry battery relevance:
offload policy lives here conceptually; cross-refs kept in docs/ux/experiments.md)

## Reverted

(none)

## Inconclusive

(none)

## Pre-registered, not yet run

- PERF-002: cold-start n=5 distribution + first attribution pass (TODO 2+5).

## PERF-001 — battery scenario cards + wake audit, beta.8 RELEASE on SM_R955U

track: battery | started: 2026-11-01 16:0x local | pre-flight: level 86%,
temp 30.8C, AC false, doze IDLE_PENDING, app pid alive (backgrounded)

Cards (batterystats --reset per card; full-wake-history enabled):
1. IDLE FLOOR / WAKE AUDIT: force-idle, screen untouched OFF, app signed-in
   backgrounded, no input. Window ≥17.5 min (checkpointed), extend if quiet.
   Metrics: per-uid estimated current (mA) + total drain, wakeups:
   wakelocks/alarms/jobs/syncs/radio; did device enter idle (mState history);
   app process still alive at end. Falsifier for "app is clean at idle":
   ANY periodic alarm/job/wakelock attributed to com.tidal.wear.
2. PLAYBACK SOFTWARE 10 min, screen off, speaker, fixed track (streamed
   HE-AAC, suspension active), volume unchanged. Cross-check: media_session
   position advancing at 10 min + flinger writes grew. Metrics: per-uid mA
   (audio/CPU/wifi/radio), SoC delta.
3. offload-vs-software delta: BLOCKED on this device for HE-AAC (offload
   wedges — EXP-012); requires AAC-LC tier → UX-024 quality-tier decision.
   Do NOT substitute a wedged-offload run as an offload number.

Noise note: SoC% delta at 86% over 10 min may quantize to 0-2%; estimated
currents from batterystats are the primary numbers, SoC the sanity check.

### Results (2026-11-01)

**Card 1 — idle floor / wake audit: CLEAN (verdict: baseline set)**
Window 17:59:40→18:17:32 (17.9 min), force-idle, screen untouched off,
beta.8 release, signed-in app backgrounded (pid alive whole window).
- com.tidal.wear attributed: **401 ms CPU total** (153 usr + 248 krn, 0 ms
  fg, all screen-off), **0 wakelocks, 0 alarms, 0 jobs, 0 syncs, 0 radio
  wakeups**. ~0.04% CPU. No P0 background-drainer pattern exists.
- Device context: mState=IDLE reached; uptime fraction 68.9% and
  "screen doze: 0ms" -> **WiFi-adb keepalive prevents deep suspend**;
  whole-device drain 8%/17.9 min ~= 79 mA avg (295 mAh est capacity) is
  mostly WLAN/suspend-blocked system floor, NOT the app.
- LIMITATION + next step: true idle floor needs an overnight run with NO adb
  (bugreport diff), user-scheduled.

**Card 1b — methodological finding (decision recorded)**: the adb-WiFi
keepalive dominates measured idle drain — Estimated power use tree: computed
27.6 mAh/17.9 min, of which wifi 31.0 mAh attributed to UID 2000 (adbd);
com.tidal.wear below report cutoff (<0.018 mAh, <0.07%). Idle windows must be
run DISCONNECTED: reset -> `adb disconnect` -> wait -> reconnect -> dump
(stats persist on-device). Re-run scheduled as PERF-003 overnight card.

**Card 2 — playback software 10 min screen off speaker:** in flight
(first attempt aborted cleanly: scroll-persisted home hid the target card;
NOPLAY_ABORT guard fired before any contaminated number was recorded —
retried scroll-resilient).

## EXP-018 — bounded concurrent liked-track batches (UX-029; in progress)

Baseline: beta.9 local release on SM-R955U, 2026-11-02, three tap-to-`library fallback ok` log samples: 8.6, 7.3, 6.1 s (median 7.3, worst 8.6); 264 tracks and 46 playlists on final sample. Watch charging, 96%, 42.3 C observed after trials, AirPods Max connected, playback active. This is a log milestone proxy, not UI-frame timing. Prior ~24 s single run was under different conditions.
Hypothesis: fetch the existing 20-ID batches in groups of three concurrently while preserving pagination and order; reduce median and worst completion without changing track count or fallback.
Invariants: complete paginated results, stable ordering, cancellation and failure propagation; bounded request concurrency; no changes to favorites writes or audio routing.
Falsifier: incomplete results, request errors, degraded playback, or no repeatable benefit. Same watch, release build, route, charging and playback state for comparison; record thermal differences and do not claim battery improvement.
Candidate: release installed on same watch; AirPods Max, same track restarted after install, playback active and charging. Three matching log-marker trials: 6.1, 5.0, 5.0 s (median 5.0, worst 6.1), each reporting 264 tracks, 50 albums, 17 artists and 46 playlists. Library UI displayed 264 and 46. Watch reported 92% and 41.5 C after candidate trials (baseline 96%, 42.3 C after baseline); those differing conditions and three samples limit confidence. AudioFlinger showed an active Untidy PCM track and a write 34 ms before inspection; this alone does not establish audibility or absence of dropouts.
Verdict: provisional: API marker improved in this small run; no battery claim. Needs a longer real-world observation and audible playback confirmation. User-visible frame timing was not measured.

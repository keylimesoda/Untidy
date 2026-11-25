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

## PERF-004 — defer recent artwork lookup until Recent opens

Hypothesis: home renders icons rather than recent artwork; moving artwork
refresh from app composition into the Recent destination removes startup
lookups while retaining progressive artwork refresh when Recent opens.
Baseline/candidate: same signed-in `wear6` emulator and debug build, three cold
launches each with a temporary lookup counter, fixed recent history. The count
measures lookup attempts, not radio energy or physical-watch performance.
Regression gates: home and Recent navigation work, recent covers still load,
track context can load metadata, cancellation does not leave stale state.
Physical watch release smoke is required; no battery saving is claimed without
controlled on-device energy comparisons.

Emulator screening: baseline 4/4/4 lookup attempts on home versus candidate
0/0/0 over 12 seconds, using the same temporary counter and saved recents.
On opening Recent the candidate attempted the same four lookups; track covers
rendered and a track opened its context. Debug `am start -W` values were
723/665/815 ms versus 665/632/850 ms; these overlapping emulator timings do
not establish a latency improvement. Counter removed before release. Evidence:
`evidence/2026-11-02-perf004-emulator-{baseline,candidate}.txt` and
`../ux/evidence/2026-11-02-perf004-emulator-recent.png`. A lookup attempt is
not proof of a network request; energy impact remains unmeasured.

Physical watch: minified release installed over the existing build; signed-in
home, Recent navigation, two visible track covers and a track-context title,
artist and album exercised. Screenshot:
`../ux/evidence/2026-11-02-perf004-watch-recent.png`. The controlled
cancellation gate and a watch-side before/after power comparison were not run.
Verdict: accept provisionally for eliminating unnecessary lookup attempts;
latency and battery improvements remain unverified. Release build succeeded
with R8 Kotlin-metadata parsing warnings.

## AAC decoder and offload inspection — 2026-11-02

SM-R955U, beta.9 release, charging, Bluetooth A2DP. Existing device-wide
offload suspension was retained. The same streamed track delivered HE-AAC
(`mp4a.40.5`, 96 kbps) at Battery Saver and AAC-LC (`mp4a.40.2`, 320 kbps)
at High; both selected `c2.android.aac.decoder`. A temporary on-device
`MediaCodecList.ALL_CODECS` probe listed only `c2.android.aac.decoder` and
`OMX.google.aac.decoder` for `audio/mp4a-latm`, both reported software-only
and not hardware-accelerated. This does not describe DSP offload capability.

For the AAC-LC sample, AudioFlinger showed PCM output on the A2DP mixer with
a recent write; actual audibility was not independently confirmed. No AAC-LC
offload trial was run: the persisted suspension prevents it, and clearing it
would discard the known HE-AAC safety state. Therefore no format-specific
offload policy or power improvement is justified by this inspection. Battery
Saver was restored, playback paused, and the unchanged release reinstalled.

### Isolated AAC-LC offload screening

Separate debug package on SM-R955U, charging, locally generated 190-second
44.1 kHz stereo AAC-LC tone, Media3 1.5.0.1. This is not a TIDAL track or
the production build; its app data is separate and Untidy's suspension was
not changed. One trial per configuration; no battery comparison.

Bluetooth software control: `c2.android.aac.decoder`, active A2DP PCM mixer
and advancing position through 186 seconds. Offload enabled with gapless
required: software decoder and active PCM mixer at 55 seconds. With gapless
not required: an AAC-LC A2DP OFFLOAD thread opened, but after 32 seconds
without a write it had one total write and no active track, while Media3
reported playing and its position advanced. Stopped as a suspected stall.

Speaker software control showed active PCM. Speaker offload with gapless
required selected the software decoder; without the requirement, an AAC-LC
speaker OFFLOAD thread opened but had no active track and no write for 33
seconds at the checkpoint, while Media3's position advanced. Stopped as a
suspected stall. Actual audibility was not independently confirmed in any
case. The short-lived inactive-track state alone is not proof of silence;
these prolonged inactive/no-write observations warrant keeping the protection.

In the software-only route-switch probe, Bluetooth to speaker was confirmed
by AudioFlinger while playback progressed. Switching back to Bluetooth was
requested, but the output was not captured after that request. No production
policy change or battery claim: offload failed the reliability gate and the
watch was charging. The trial package was removed and the existing release
reinstalled.

### Audible AAC-LC offload follow-up — 2026-11-06

SM-R955U, Android 16 build R955USQS2CZF5, charging, isolated debug
package with Media3 1.5.0.1 and a locally generated 44.1 kHz stereo AAC-LC
128 kbps tone. Offload enabled, gapless support not required. The first
speaker trial used a 440 Hz tone; the listener confirmed continuous sound,
though quiet. At approximately 64 seconds of player position AudioFlinger
reported an active AAC-LC OFFLOAD track routed to speaker, 80 total writes,
and a write 1.5 seconds earlier. A second trial used a louder 1 kHz tone;
the listener confirmed it was clear and continuous. At approximately 41
seconds AudioFlinger again reported an active speaker AAC-LC OFFLOAD track,
53 writes, and a recent write. These observations establish that this local
AAC-LC sample can play audibly using the offload path on this watch; they do
not establish TIDAL stream reliability or a power saving. The earlier isolated
suspected stalls remain unexplained. Bluetooth was temporarily disabled to
select speaker, then re-enabled; the probe package was removed.

Gapless control: on the same watch and local AAC-LC tone, a separate run with
Media3 offload enabled and gapless support required produced an active PCM
track on the normal mixer. This reproduces the earlier fallback and contrasts
with the active AAC-LC offload track when gapless was not required. It does
not establish whether a TIDAL stream will behave the same way.

TIDAL AAC-LC follow-up — 2026-11-06: a separate debug application ID,
using the Untidy playback pipeline with High quality forced and gapless support
not required, streamed a 320 kbps `mp4a.40.2` track at 44.1 kHz stereo to
the SM-R955U speaker. At media position 63.6 seconds, AudioFlinger reported
an active AAC-LC OFFLOAD track, 90 writes, and a write 1.1 seconds earlier;
the listener reported continuous sound. This is one stream and route, not a
battery or transition test. A prior trial in the same test app delivered
96 kbps HE-AAC because the saved setting remained Battery Saver; the listener
reported no sound, and it should not be counted as an AAC-LC result. The
production app's stored offload suspension was not changed.

Bluetooth AAC-LC follow-up — 2026-11-06: the isolated High-quality test
application streamed a 320 kbps AAC-LC TIDAL track to Galaxy Buds over A2DP.
AudioFlinger showed an active AAC-LC OFFLOAD track and continuing writes at
194 seconds. The debugging connection dropped before the transition; the
listener reported uninterrupted playback into the next track. After reconnect,
the second track was also 320 kbps AAC-LC, with an active Bluetooth OFFLOAD
track and recent writes. A pause and resume produced PAUSED then PLAYING and
continued active offload writes for at least 20 seconds; the listener confirmed
that audio resumed normally. This is one album
and one Bluetooth route, with gapless support not required; it does not prove
sample-accurate gapless transitions or quantify power savings.

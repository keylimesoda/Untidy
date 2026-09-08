# Hardware power diagnostic — 2026-09-07

## Scope and limits

Galaxy Watch6 LTE (SM-R955U), Untidy 0.6.0-beta.5 for the FLAC baseline and the beta 6 candidate for AAC. Output: watch speaker. Display state: dozing after a sleep key event. Wireless ADB and diagnostic sampling add overhead. No battery statistics were reset. The AAC test was performed off charger; the screen-timeout setting was temporarily extended during setup and restored to 30 seconds before measurement.

## Completed measurements

| State | Measured interval | App CPU time | Average, one CPU core |
| --- | ---: | ---: | ---: |
| Paused, dozing | 120.10 s | 0.25 s | 0.21% |
| Playing FLAC, dozing, speaker | 120.62 s | 22.09 s | 18.31% |
| Playing FLAC, unplugged, dozing, speaker | 180.74 s | 33.23 s | 18.39% |
| Playing HE-AAC v1 with hardware offload, unplugged, dozing, speaker | 180.29 s | 1.19 s | 0.66% |

The charging playback sample began at position zero and ended with the media session still PLAYING at approximately 121 seconds. The unplugged FLAC sample reported 71% falling to 68%, while charge counter fell from 219000 to 206700. The unplugged AAC sample played “Go” continuously from approximately 18 to 199 seconds; it reported 36% falling to 32%, while charge counter fell from 111900 to 100500. Three minutes and whole-percent battery readings are far too short and noisy to extrapolate endurance. The charge-counter deltas (12.3 mAh FLAC and 11.4 mAh AAC) are also too close under these conditions to claim a battery-life percentage. CPU percentages cover the Untidy process, not total system power, decoder services, radio, speaker amplifier, or thermal effects.

The FLAC track was the downloaded “The Memory of Trees” by Enya. Delivered format: FLAC, 44.1 kHz, two channels, reported bitrate 706944 bit/s; bit depth was not reported. Beta 6 subsequently repaired the legacy download and cold-started it from the persisted descriptor and cache without a manifest request.

The AAC track was the streamed “Go” by Moby. Delivered format: `mp4a.40.5` (HE-AAC v1), 44.1 kHz, two channels, reported bitrate 96018 bit/s. AudioFlinger showed a type `OFFLOAD` thread at both endpoints with `AUDIO_FORMAT_AAC_HE_V1`, `AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD`, and the built-in speaker route. App-process CPU was about 28 times lower than the matched FLAC run. This is strong evidence that offload materially reduces Untidy's own CPU work, but not yet a whole-device endurance result.

## Playback and wake-lock evidence

- Runtime selected `c2.android.flac.decoder`, a software decoder. AudioFlinger showed a normal PCM mixer/deep-buffer speaker output, not compressed offload.
- Untidy held its ExoPlayer partial wake lock during playback. Pausing released that lock; it was absent both in the paused baseline and the final post-test power dump.
- The paused CPU baseline is encouraging, but does not establish idle drain over hours, process-death behavior, or every ambient/UI state.
- The watch's audio policy advertises compressed offload for AAC LC/HE and MP3, including speaker and Bluetooth outputs. No FLAC compressed-offload profile appeared in that policy.
- Beta 6 enables optional Media3 audio offload with normal-playback fallback and required gapless support. [Media3 documents these preferences and their disabled default](https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters.AudioOffloadPreferences.Builder). Actual routing remained stream-dependent: another HE-AAC track (“Porcelain”) used the normal PCM deep-buffer path during setup, while “Go” used verified compressed offload. Capability must therefore be checked at runtime rather than inferred only from codec name.

## Next experiments

Run hour-scale, matched AAC and FLAC discharge tests from similar charge and temperature, ideally with locally cached media to remove radio variance. Verify offload across album transitions, Bluetooth route changes, pause/resume, and a representative set of TIDAL AAC manifests. Hardware policy support alone does not guarantee that a particular stream/route can use offload or quantify the savings.

Bluetooth listening, AAC playback, streaming/LTE, long idle, and sustained discharge remain unmeasured. A proper before/after comparison requires longer matched runs from similar charge and temperature. Do not present this short diagnostic as an endurance result.

## Artifacts and device handoff

Read-only capture helper: `scripts/measure-wear-power.ps1`. Raw diagnostics remain in ignored `reports/hardware-2026-09-07/` and should not be published wholesale because device logs can contain private information.

The beta 6 candidate was installed and physically smoke-tested. The output picker opened as a separate Wear dialog and returned to the preserved pull-up position on Back. Optional Media3 offload never changes the requested format and retains ordinary fallback. FLAC remained software-decoded on this watch, as expected from its advertised profiles. Raw AAC evidence is in `reports/hardware-2026-09-07/beta6-aac-offload-speaker-unplugged-go/`.

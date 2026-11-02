# Measurement discipline (what makes a number believable)

1. **Identical scenario definition.** Write the exact step sequence +
   controlled state ONCE (scenario card), reuse verbatim for baseline and
   candidate. Any drift invalidates comparison.
2. **N ≥ 3 per side**, interleaved or at least same-session where feasible;
   report median + worst + spread. Deltas < observed spread = inconclusive;
   add trials rather than squint.
3. **Thermal/charge band**: record `dumpsys battery` level + temperature at
   scenario boundaries; reject runs after a charge session or rapid consecutive
   heavy scenarios (SoC DVFS state differs). Rest ≥2 min between active
   scenarios on the watch.
4. **Build variant parity**: release-vs-release or debug-vs-debug. Release is
   the product; debug builds add logging overhead and skip R8 — quantify a
   debug-only optimization only as directional.
5. **Proxy vs ground truth.** Every proxy metric has a named ground-truth
   cross-check:
   - CPU% ≠ battery. Cross-check = batterystats drain or controlled discharge.
   - Media3 PLAYING ≠ audible. Cross-check = flinger writes (EXP-012 proved a
     dead offload pipeline scores PERFECT on proxies — assume every proxy guilty
     until cross-checked).
   - `TotalTime` ≈ TTID but includes launch bookkeeping; cross-check
     `Displayed` and, for perceived snappiness, first-frame via screenrecord.
   - gfxinfo jank% ≠ felt smoothness: check the actual long frames
     (`framestats` histogram tail), a 500 ms hitch is felt, 17 ms ones are not.
6. **Statistical honesty**: never delete an inconvenient sample; exclude only
   with a pre-registered reason (e.g. scenario aborted by system dialog),
   logged in the experiment record.
7. **Evidence retention**: keep the specific traces/bugreports/framestats
   dumps backing each accepted claim in `docs/perf/evidence/` with the
   experiment id in the filename; a claim with no retained artifact gets
   downgraded to hypothesis on audit.
8. **Baseline freshness**: budgets reference their baseline date + build.
   A budget from beta.7 does not silently apply to beta.8 code — re-baseline
   after any change that plausibly affects the scenario (major dep bumps,
   playback rewrites).

# Untidy Typography / TIDAL-Appropriate Brand Review — 2026-06-17

Issue: [#36 / UNTIDY-035](https://github.com/keylimesoda/Untidy/issues/36)

## Recommendation

Keep Untidy on the Wear OS platform typeface for the release candidate. Do **not** bundle or imitate a proprietary TIDAL font/wordmark.

For the current app, the most TIDAL-appropriate polish direction is:

1. **Use the system/Wear Compose sans family for all functional UI.** It is legally safe, already tuned for small Android/Wear screens, and avoids adding font weight/size to the APK.
2. **Preserve artist/track metadata casing exactly.** TIDAL's developer guidance explicitly says not to manipulate metadata casing or names.
3. **Use brand-like restraint instead of a custom font:** black background, white text, cyan accent, compact all-caps section labels, strong but not decorative weights, and short one-line labels with ellipsis.
4. **Avoid copying the TIDAL wordmark or any proprietary app font.** Use `TIDAL` only as required/accurate text and attribution; keep `UNTIDY` as the product wordmark.

No code change is recommended for this slice. Current typography is already more release-safe than bundling an unvalidated font at the end of the release gate.

## What Untidy uses now

Source inspection:

- `app/src/main/java/com/tidal/wear/ui/theme/` only defines `TidalColors.kt`; there is no custom `Typography` object or app-level `FontFamily`.
- `app/src/main/res/` has no `font/` resources and no `.ttf`/`.otf` files.
- App entry points wrap screens in `androidx.wear.compose.material.MaterialTheme` without overriding typography (`MainActivity.kt`, `PlayerActivity.kt`).
- Most screens set local `fontSize`, `fontWeight`, `letterSpacing`, and truncation rules directly on `Text`.
- One explicit `FontFamily.SansSerif` appears in onboarding for the device-code display; that still resolves to the platform sans family.

Practical answer: **Untidy is using the Wear OS / Android platform sans-serif stack, effectively Roboto/system sans via Wear Compose defaults, with local weights and sizes.**

## Public TIDAL brand direction found

Publicly safe source: TIDAL Developer Portal design guidelines, `https://developer.tidal.com/documentation/guidelines/guidelines-design-guidelines`.

Relevant guidance observed:

- The logo consists of the **TIDAL Diamond + TIDAL wordmark**.
- The TIDAL Diamond is an abstracted letter T and is meant to evoke premium audio quality.
- Use the full TIDAL logo where possible; the diamond alone is for constrained/small TIDAL environments, with the word `TIDAL` in supporting headline/body copy.
- Logo color guidance is stark: white on black or black on white.
- Metadata should be displayed accurately and legibly; examples explicitly say not to alter title/artist capitalization.

What was **not** found in public official guidance:

- No public, redistributable TIDAL UI typeface package.
- No official statement that a third-party client should use a named TIDAL text face.
- No permission to recreate or modify the TIDAL wordmark.

Conclusion: TIDAL's safe public direction is **minimal, high-contrast, black/white, premium, all-caps brand mark, exact metadata, legible text** — not a specific bundled font for third-party UI.

## Should Untidy bundle a font?

### Recommendation: no, not for release

Bundling a font is not worth it for this app right now:

- **Legal risk:** TIDAL's actual wordmark/app type may be custom or licensed. Copying it, tracing it, or using an unofficial clone would be inappropriate.
- **Product risk:** Wear OS screens are tiny, round, variable-density, and often glanced at during movement. The platform font is the safest baseline for legibility.
- **Engineering risk:** A font change affects every screen. It needs screenshot comparison on at least 454x454 emulator and a physical watch before release.
- **APK/runtime cost:** Font resources add size and can create weight/rendering mismatches unless applied carefully.

### If a future font experiment is desired

Use only open, redistributable fonts and test them as a **display/accent** face first, not globally.

Shortlist:

- **Inter** (SIL Open Font License): excellent digital UI font, broad weights, neutral/premium, strong small-size readability. Best candidate if replacing the whole app font is ever tested.
- **Manrope** (SIL Open Font License): geometric, modern, slightly more distinctive than Inter; good candidate for `UNTIDY` wordmark/headers only.
- **Montserrat** (SIL Open Font License): geometric/all-caps brand feel, but can feel wide and less efficient on watch screens; use only for sparse headers if tested.

I would **not** use a display font for track titles, artist names, playlist rows, queue rows, or body copy.

## Wear OS readability constraints

For Untidy's current screens, the constraints matter more than brand imitation:

- **Functional text should stay 12–18sp.** Below ~11sp becomes fragile on physical hardware; above ~18sp quickly crowds a 454x454 round screen.
- **One-line metadata is correct.** Track/artist/album labels should stay `maxLines = 1` with ellipsis; wrapping creates unstable layouts.
- **Avoid all-caps for metadata.** All-caps is appropriate for section labels (`SEARCH RESULTS`, `TRACKS`) and the `UNTIDY` wordmark, but not for song/artist data.
- **Use weight sparingly.** `Black` works for short titles/section labels; long rows should prefer `SemiBold`/`Bold` for readability and reduced visual noise.
- **Letter spacing should be small and limited.** Current section labels at about `0.8–1.sp` and wordmark at `2.sp` are reasonable. Do not letter-space body text or metadata.
- **Contrast is already good.** Black background, white primary text, muted secondary text, and cyan action/accent color are aligned with TIDAL's public black/white premium direction.
- **Round-screen edge safety matters.** Typography should not be widened or condensed without re-checking safe-area screenshots; text near edges must remain clipped/ellipsized, not wrapped.

## Current app polish assessment

Good current choices:

- `UNTIDY` wordmark: all caps, centered, letter-spaced, short. This gives a premium/music-app feel without copying TIDAL.
- Section headers: all caps + cyan + letter spacing. This is brand-adjacent and scan-friendly.
- Player title/artist: centered, one-line, high contrast, ellipsized. Correct for glanceability.
- Search/results/list rows: one-line label + one-line secondary text. Correct for Wear.
- Attribution/settings copy already says this is an unofficial client and TIDAL marks belong to TIDAL.

Cautions for future cleanup:

- Many screens hard-code local sizes/weights. A future typography refactor should centralize a small `UntidyTypography` token set rather than changing values ad hoc.
- `FontWeight.Black` is used heavily. It works for short labels but should not spread to body text.
- Any future custom font experiment must be tested on physical hardware, not just screenshots.

## Decision

**Do not bundle a font now.** The release-safe brand polish is to stay with Wear/system sans and continue tightening weights, spacing, case, and metadata handling.

If Ric wants a more premium custom look after release, run a visual A/B spike with Inter and Manrope on:

1. Home wordmark + section headers only.
2. Player title/artist.
3. Search/list rows.
4. Settings/downloads rows.

Gate it on physical-watch readability before adopting it globally.

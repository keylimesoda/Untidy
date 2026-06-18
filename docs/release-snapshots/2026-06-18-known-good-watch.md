# Untidy known-good watch snapshot — 2026-06-18

Ric used this app build on a physical Wear OS watch for a full night and reported: "It was great!" This snapshot preserves the build as the current known-good device baseline.

## Snapshot identity

- Snapshot tag: `v0.1.0-known-good-watch-2026-06-18`
- Source commit: `4a874f6ee33de9b74e4ba7a5e198c98ae2b61a77`
- Commit subject: `Mark Recent shelf ready for review`
- Prior validation tag already on commit: `v0.1.0-watch-validation.9`
- APK variant preserved: release APK from `bash ./gradlew assembleRelease --no-daemon`
- Physical-use feedback: whole-night real-watch use was positive; this is the baseline to compare future regressions against.

## APK checksums

```text
e6b9c99ccd68351904b699510bfaefeef4bbb5d3c1ba053074dd7723685d4a3c  app/build/outputs/apk/release/app-release.apk
d960cd691d2c51ff9ca5135f3b6021393b28fd3284b5d79f94c0f532c8683c35  app/build/outputs/apk/debug/app-debug.apk
```

## Local artifacts

- `reports/build-snapshots/2026-06-18-known-good-watch/untidy-0.1.0-known-good-watch-release.apk`
- `reports/build-snapshots/2026-06-18-known-good-watch/release-output-metadata.json`
- `reports/build-snapshots/2026-06-18-known-good-watch/debug-output-metadata.json`

## Verification performed at snapshot time

```bash
source scripts/env-android.sh
bash ./gradlew assembleRelease --no-daemon
sha256sum app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/debug/app-debug.apk
git rev-parse HEAD
git status --short --branch
git show -s --format='commit=%H%nshort=%h%nauthor=%an <%ae>%ndate=%cI%nsubject=%s' HEAD
```

Result: `assembleRelease` passed; repo was clean on `main...origin/main` before snapshot artifact creation; source commit was `4a874f6ee33de9b74e4ba7a5e198c98ae2b61a77`.

# GitHub APK releases

Tag pushes build and publish an installable, signed APK. Each repo has its own
signing key, stored only as GitHub Actions secrets. Never commit a keystore or
send signing credentials in Discord.

## One-time setup (each repository separately)

In `Settings → Secrets and variables → Actions`, add:

- `RELEASE_KEYSTORE_BASE64` — base64 encoding of that app's `.jks` file
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

For Untidy also add the TIDAL application configuration as Actions secrets:

- `TIDAL_CLIENT_ID`
- `TIDAL_CLIENT_SECRET`
- `TIDAL_REDIRECT_URI`

Generate an independent long-lived key for each app on a trusted computer;
keep an encrypted offline backup. Example (choose your own alias and passwords;
never paste them into chat or commit them):

```sh
keytool -genkeypair -v -keystore untidy-release.jks -alias untidy \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 untidy-release.jks
```

Upload the base64 output and matching alias/password values directly into the
repository's GitHub Actions secrets. Repeat with a different keystore/alias for
Thumbscript. Losing a signing key prevents future in-place APK updates.

## Release procedure

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.
2. Merge the release commit to `main`.
3. Create and push an exact matching `v<versionName>` tag, e.g. `v0.6.2`.
4. Actions verifies the tag/version match, builds `:app:assembleRelease`, checks
   the APK signature, generates `SHA256SUMS.txt`, and publishes both assets on
   the GitHub Release. Hyphenated versions (beta/rc) are marked prerelease.

The workflow intentionally fails before building if required secrets are
missing. Untagged main builds continue using the developer's existing local
signing behavior and do not publish.

## Signing migration warning

The existing public APKs (`Untidy v0.6.0-beta.8` and `Thumbscript/Still
Scriptures v0.6.1`) were produced before this workflow and are signed with the
previous debug certificate. The original private debug keystore is not present
on this build host. The new per-app release keys therefore will **not** update
those installations in place. For the first release signed by the new key,
users must uninstall the old build before installing the new APK; this removes
local app data. After that one-time migration, later releases signed with the
same key install as upgrades and preserve data. Do not publish the first new-key
APK until you accept that migration and have backed up any local data you need.

These are sideloadable GitHub releases, not Google Play releases. They are not
Play App Signing keys and are not a substitute for Play Console publishing.

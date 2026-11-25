# APK publication paused

Future Untidy publication is fail-closed. `.github/workflows/release-apk.yml`
contains only a failing notice: no checkout, build, secrets, artifacts or release
API calls, with an empty permission set. Tag pushes, main pushes and manual
runs of this version cannot publish. Existing releases remain untouched.

## Authorization policy (separate from pipeline security)

The current device-code login uses an obfuscated TIDAL first-party credential
not registered to this project. The maintainer has no TIDAL sign-off. The
source comment in `core/auth/.../TidalAuthRepository.kt` describes that origin;
no credential values are reproduced here. Obfuscation is not confidentiality.
This change does not alter authentication or other application behavior.

Resume publication only after documented TIDAL authorization, review of the
permitted client classification and distribution terms, and a separately
reviewed change that replaces this pause with an approved release pipeline.
Passing build/signing checks or manual QA is not authorization from TIDAL.

## Build configuration audit

The old workflow's `TIDAL_CLIENT_ID`, `TIDAL_CLIENT_SECRET` and
`TIDAL_REDIRECT_URI` references were **not unused**: it wrote them to
`.dev-secrets/tidal-app.properties`; `app/build.gradle.kts` and
`core/auth/build.gradle.kts` compile those properties into BuildConfig fields.
`TidalAuthRepository` reads the generated auth fields. A repository-wide Kotlin
call-site scan found the `client_credentials` helper implementation in core
auth but no production caller outside that repository; this does not establish
that the grant is active at runtime. User device login uses the separate
first-party fallback. Supplying developer credentials does not replace that
device-login credential.

All CI TIDAL and signing-secret references were removed because publication
and its build have been retired, not because the TIDAL fields were unused.
Local build/auth configuration remains unchanged. Actions secrets protect
storage and log handling in CI; they cannot keep values confidential once
compiled into a distributed APK. No secret values were inspected or decoded.

## Required repository-owner UI actions

Local changes have no effect on GitHub until reviewed and merged by the owner.
Even after merging, historical tags retain the old workflow contents.

1. In **Actions → Publish signed Wear OS APK** (the existing
   `release-apk.yml` workflow) → **… → Disable workflow**. Confirm it is disabled;
   leave it disabled after this rename. This blocks old workflow versions and
   reruns through this workflow. Alternatively disable all Actions under
   **Settings → Actions → General** if no Actions should run at all.
2. Inspect queued/in-progress historical publishing runs and cancel them in
   Actions if any exist. Do not rerun old versions or create release tags.
3. Review/remove obsolete repository signing and TIDAL Actions secrets through
   **Settings → Secrets and variables → Actions** after checking for other
   consumers. This cleanup does not delete GitHub secrets. Do not copy values
   into tickets, logs or documentation.
4. Review/merge this branch through the normal review process; keep the workflow
   disabled until authorization and a new pipeline have independent approval.

Disabling Actions does not revoke an APK-embedded credential. Credential
ownership/remediation needs the provider's authorized process. Do not modify,
withdraw or delete existing releases as part of this cleanup.

## Observed GitHub failure

Read-only GitHub queries confirm
[run 43301417154](https://github.com/keylimesoda/Untidy/actions/runs/43301417154)
failed on `push` to `main` at `2986773d2991f994c7040e0777d4f7521bf1c577`, with
no jobs or commit check-runs. The Actions summary explicitly reports:
`Invalid workflow file` at line 17, column 30: `Unrecognized named-value:
'runner'` in the expression `runner.temp`. The old workflow used
`${{ runner.temp }}` in job-level `env`; GitHub's [context-availability
table](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability)
does not allow the `runner` context at `jobs.<job_id>.env`. This parser error,
not missing secrets or a build/signing failure, explains the zero-job run. The
replacement pause workflow removes the expression. Branch-protection
inspection on 2026-11-12 returned `404 Branch not protected`; the repository
rulesets list was empty. The current workflow is still active, the repository
has no configured environments, and its repository-level Actions secrets list
is empty. Disable the historical workflow in the Actions UI before merging this
replacement; the owner actions above remain required.

## Local cleanup validation (2026-11-12)

Both worktrees started clean on their requested `security/release-guard-*`
branches. `actionlint` was not installed. Equivalent targeted validation used
duplicate-aware YAML 1.2 loading, Python jsonschema Draft 7 against
SchemaStore's GitHub workflow schema,
`bash -n` for every inline shell step, full-SHA action-reference checks and
`git diff --check`. All passed. The schema was retrieved read-only from
`SchemaStore/schemastore/src/schemas/json/github-workflow.json`; its SHA-256 was
`10f7b2dba224b461c81bbd288b07c9c64712f0d0df58ff0856d09b2431612d8b`.
The original workflows also passed this schema, so this check does not explain
the server-side zero-job rejection.

Targeted local fixtures executed the actual inline gates without credentials:
Untidy's notice exits 1; Thumbscript accepts the exact reviewed version and
rejects a version mismatch, unsafe tag, manual event, branch ref, duplicate
version declaration and off-main commit. The actual certificate comparison
accepts the expected fingerprint and rejects a wrong, multiple or missing
signer. Static assertions confirm secret isolation, environment assignments,
publish dependencies, full action pins and the contents-write boundary.

No full Android build, end-to-end signing or GitHub service execution was
performed. No credentials were printed, decoded or reconstructed. No pushes,
tags, workflow dispatches, artifact uploads or release mutations were performed.
GitHub protections and secret configuration require owner action; no workflow
was dispatched, so local validation cannot certify GitHub Actions runtime
behavior.

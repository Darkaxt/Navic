# Third-Party Attribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close QA finding A21 by preserving and publishing the exact attribution for copied reader components in both the repository and Navic's existing in-app Acknowledgements screen.

**Architecture:** Keep AboutLibraries as the single acknowledgement pipeline. Add pinned custom library/license records for Anx Reader, foliate-js, and PDF.js, feed them into the existing generated Compose resource, and protect the source and packaged APK with deterministic verification.

**Tech Stack:** Kotlin Multiplatform, AboutLibraries 15.0.2, Gradle Kotlin DSL, PowerShell, GitHub Actions, Android APK resources.

---

## File Structure

- `THIRD_PARTY.md`: human-readable project license decision and copied-reader provenance.
- `third_party/licenses/*`: exact upstream license notices required by the copied components.
- `composeApp/aboutlibraries/libraries/*.json`: custom in-app library metadata.
- `composeApp/aboutlibraries/licenses/*.json`: exact custom MIT notices keyed by stable IDs; PDF.js uses AboutLibraries' standard Apache-2.0 record.
- `composeApp/build.gradle.kts`: wires the custom records into the existing AboutLibraries export.
- `scripts/verify-third-party-attributions.ps1`: validates source records and generated/package output.
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ThirdPartyAttributionSourceTest.kt`: source-level regression guard.
- `.github/workflows/build.yml`: runs attribution verification before APK upload.

### Task 1: Define The Failing Governance Contract

**Files:**
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ThirdPartyAttributionSourceTest.kt`
- Create: `docs/superpowers/plans/2026-07-13-third-party-attribution-implementation.md`

- [x] Add a host test that requires `THIRD_PARTY.md`, exact Anx/foliate-js/PDF.js versions and commits, exact license files, AboutLibraries `configPath`, the existing Acknowledgements resource path, and CI verification.
- [x] Run `./gradlew :composeApp:testAndroidHostTest --tests "paige.navic.reader.ThirdPartyAttributionSourceTest"` and confirm it fails because `THIRD_PARTY.md` is absent.

### Task 2: Add Exact Notices And Generated Acknowledgements

**Files:**
- Create: `THIRD_PARTY.md`
- Create: `third_party/licenses/Anx-Reader-MIT.txt`
- Create: `third_party/licenses/foliate-js-MIT.txt`
- Create: `third_party/licenses/PDF.js-Apache-2.0.txt`
- Create: `composeApp/aboutlibraries/libraries/anx-reader.json`
- Create: `composeApp/aboutlibraries/libraries/foliate-js.json`
- Create: `composeApp/aboutlibraries/libraries/pdfjs-dist.json`
- Create: `composeApp/aboutlibraries/licenses/anx-reader-mit.json`
- Create: `composeApp/aboutlibraries/licenses/foliate-js-mit.json`
- Modify: `composeApp/build.gradle.kts`

- [x] State that Navic is GNU GPL version 3 under the top-level `LICENSE`; do not claim AGPL or an unverified `or later` option.
- [x] Record Anx Reader commit `107f4fa74db0e7247c846c49d6211df3edf9887c` as MIT, foliate-js `1.0.1` / `f52d42c6127d0ad981a2c67634113541b17ae01e` as MIT, and PDF.js `3.11.174` / `ce87167432819f85df49b6b16c7a78556e9a4ee0` as Apache-2.0.
- [x] Configure `collect.configPath = file("aboutlibraries")`, export the definitions, and assert the three custom libraries and their exact licenses are present in `acknowledgements.json`.
- [x] Rerun the focused host test and confirm it passes.

### Task 3: Protect Source And APK Output

**Files:**
- Create: `scripts/verify-third-party-attributions.ps1`
- Modify: `.github/workflows/build.yml`

- [x] Parse JSON structurally and reject missing/incorrect library IDs, versions, source URLs, license IDs, or license content.
- [x] Support source verification against the generated Compose resource and packaged verification against the APK entry containing `acknowledgements.json`.
- [x] Run source verification after `:composeApp:exportLibraryDefinitions` and packaged verification after `:androidApp:assembleDebug`.
- [x] Add the packaged verifier to GitHub Actions before artifact upload.

### Task 4: Validate And Publish

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-third-party-attribution-implementation.md`

- [x] Set consolidated release metadata to `versionCode=538` and `versionName=v1.0.11-iota11`.
- [x] Run focused tests, attribution verification, Android debug assembly, packaged verification, `git diff --check`, and the established full-suite baseline comparison.
- [x] Rebase the current public `master`, rerun the final gate, commit, push `master`, and push annotated tag `v1.0.11-iota11`.
- [x] Verify GitHub Actions, public release metadata/hash/signature, and an ADB in-place emulator upgrade with no fatal startup error.
- [x] Record release evidence, push it, remove this worktree, and delete the local feature branch without touching ebook/page-turn worktrees.

## Release Evidence

- Release/tag commit: `4f5dfbe7a46b51de022931098e958afc8bcb2f44`.
- GitHub Actions: build/release run `29235127291` and checks run `29235127286` succeeded; the Android job passed both source and packaged governance checks, while iOS was skipped.
- Public APK: 46,208,784 bytes, SHA-256 `14a8fae5c3321e222f59b4fb1fc1548920601f33dc80ea3d3cfd10cbe88e8daa`.
- Signature/version: APK Signature Scheme v2, certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`, `versionCode=538`, `versionName=v1.0.11-iota11`.
- Governance proof: the public APK passed all 30 reader-vendor hashes and contains exact Anx Reader, foliate-js, and PDF.js acknowledgement records. A deliberate missing-library mutation failed verification.
- Device: signed upgrade on `emulator-5554`; PID `30458` remained the resumed activity with no AndroidRuntime or fatal startup error.
- Suite proof: 2,326 Android host tests ran with the same 35 pre-existing failure names as the pre-attribution baseline; all three new attribution tests passed.

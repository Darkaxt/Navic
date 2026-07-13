# Reader Vendor Provenance Implementation Plan

**Goal:** Close B7 by making every Foliate/PDF.js byte shipped in the Android APK traceable to an immutable upstream source and protected by source-tree and packaged-APK hash verification.

**Scope:** Governance and build verification only. Do not update Foliate or PDF.js, alter reader behavior, or modify any reader/page-turn implementation.

**Release:** `v1.0.11-kappa3`, `versionCode=535`.

## Provenance Baseline

- `foliate-js@1.0.1`
  - npm integrity: `sha512-Cj4h2ub5aVA+yUgbhvVhCyxwi0GPF4pyNBa6Lw9+6WKY1ReBxipItn2kEBO6u7Vu/xYXjK711R74+t+yW/0u5w==`
  - upstream commit: `f52d42c6127d0ad981a2c67634113541b17ae01e`
  - import proof: 26 of 27 files at Navic import commit `c253f223` match the upstream Git tree byte-for-byte; only npm's published `package.json` version differs from the source tree.
- `pdfjs-dist@3.11.174`
  - npm integrity: `sha512-TdTZPf1trZ8/UFu5Cx/GXB7GZM30LT+wWUNfsi6Bq8ePLnb+woNKtDymI2mxZYBpMbonNFqKmiz684DIfnd8dA==`
  - upstream release commit: `ce87167432819f85df49b6b16c7a78556e9a4ee0`
  - npm package git head: `f287f540ed3ed393e137c9ff7a2e98f6e73ea527`
  - embedded build id: `ce8716743`
  - import proof: the two Navic bundles differ from the npm `build/` files only by stripped trailing whitespace.
  - security review: `GHSA-wgrm-67xf-hhpq` / `CVE-2024-4367` affects this version when eval support is enabled. Navic's only `getDocument` call already sets `isEvalSupported: false`, the upstream advisory's documented workaround; the parity test now requires that exact secure call and no longer allows an insecure fallback.
- Navic intentionally modifies Foliate after import. The manifest therefore records upstream provenance while SHA-256 entries describe the exact current Navic bytes.

## Task 1: Define failing governance proof

- [x] Add a PowerShell verifier self-test that requires the manifest and verifier.
- [x] Prove the self-test fails before implementation.
- [x] Require valid-tree success, hash-tamper rejection, and unmanifested-file rejection.

## Task 2: Ship exact provenance and file hashes

- [x] Add `reader/vendor/manifest.json` with immutable component provenance and one SHA-256 entry for every vendored file.
- [x] Add a deterministic manifest hash updater that changes only the file inventory.
- [x] Add a verifier that rejects invalid metadata, unsafe/duplicate paths, missing files, extra files, and hash mismatches.
- [x] Support verification of both source assets and `assets/reader/vendor/**` entries inside a built APK.

## Task 3: Make verification continuous

- [x] Run the verifier self-test and source verification before Android builds in GitHub Actions.
- [x] Verify the produced release/debug APK before artifact upload.
- [x] Document the update, upstream-diff, security-review, local-patch review, validation, and manifest regeneration procedure.
- [x] Add a host source guard for the manifest and workflow contracts.

## Task 4: Validate and publish

- [x] Run focused PowerShell and Android host tests.
- [x] Build the Android debug APK and verify its packaged assets.
- [x] Run the broader owning test group and compare any failures with the existing baseline.
- [x] Set `versionCode=535` and `versionName=v1.0.11-kappa3`; update B7 and the roadmap.
- [x] Commit, push public `master`, create and push annotated tag `v1.0.11-kappa3`.
- [x] Verify GitHub Actions, signature, public APK metadata/hash, and emulator upgrade.
- [x] Commit final release evidence, remove this worktree, and delete the local feature branch.

## Release Evidence

- Release/tag commit: `2b9db5780c33592de05421c660f03d2d07df7924`.
- GitHub Actions: build/release run `29231979895` and checks run `29231979929` succeeded; the Android job passed both source and packaged vendor verification, while iOS was skipped.
- Public APK: 46,208,196 bytes, SHA-256 `1ebd28ae743c3c7153fa44fa7dea11915505ec04491e26784783d5347fefc231`.
- Signature/version: APK Signature Scheme v2, certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`, `versionCode=535`, `versionName=v1.0.11-kappa3`.
- Vendor proof: the downloaded public APK contains the exact shipped manifest and all 30 source-matched vendor hashes.
- Device: signed upgrade from kappa2 to kappa3 on `emulator-5554`; app PID `29006` remained alive with no fatal startup log.
- Suite proof: 2,321 Android host tests ran with the same 35 pre-existing failure names as kappa2; both new B7 tests passed. Focused governance/PDF mitigation tests and Android debug assembly passed again after rebasing the concurrent public reader commit.

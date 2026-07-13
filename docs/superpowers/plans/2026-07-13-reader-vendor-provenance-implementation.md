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
- Navic intentionally modifies Foliate after import. The manifest therefore records upstream provenance while SHA-256 entries describe the exact current Navic bytes.

## Task 1: Define failing governance proof

- [ ] Add a PowerShell verifier self-test that requires the manifest and verifier.
- [ ] Prove the self-test fails before implementation.
- [ ] Require valid-tree success, hash-tamper rejection, and unmanifested-file rejection.

## Task 2: Ship exact provenance and file hashes

- [ ] Add `reader/vendor/manifest.json` with immutable component provenance and one SHA-256 entry for every vendored file.
- [ ] Add a deterministic manifest hash updater that changes only the file inventory.
- [ ] Add a verifier that rejects invalid metadata, unsafe/duplicate paths, missing files, extra files, and hash mismatches.
- [ ] Support verification of both source assets and `assets/reader/vendor/**` entries inside a built APK.

## Task 3: Make verification continuous

- [ ] Run the verifier self-test and source verification before Android builds in GitHub Actions.
- [ ] Verify the produced release/debug APK before artifact upload.
- [ ] Document the update, upstream-diff, security-review, local-patch review, validation, and manifest regeneration procedure.
- [ ] Add a host source guard for the manifest and workflow contracts.

## Task 4: Validate and publish

- [ ] Run focused PowerShell and Android host tests.
- [ ] Build the Android debug APK and verify its packaged assets.
- [ ] Run the broader owning test group and compare any failures with the existing baseline.
- [ ] Set `versionCode=535` and `versionName=v1.0.11-kappa3`; update B7 and the roadmap.
- [ ] Commit, push public `master`, create and push annotated tag `v1.0.11-kappa3`.
- [ ] Verify GitHub Actions, signature, public APK metadata/hash, and emulator upgrade.
- [ ] Commit final release evidence, remove this worktree, and delete the local feature branch.

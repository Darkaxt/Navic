# Reader Engine Capabilities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not use agents for this unit.

**Goal:** Resolve roadmap finding B3 by defining publication-format capabilities and preventing unsupported search and media-overlay work at controller, UI/loading, and adapter boundaries.

**Architecture:** A single capability matrix on `ReaderPublicationFormat` drives controller predicates, Whispersync launch eligibility, Compose visibility, and Foliate command/event filtering. Unsupported controller actions no-op before state mutation; adapters remain the final safety boundary.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Foliate reader bridge, Kotlin Test, Gradle, PowerShell, ADB, GitHub Actions

---

### Task 1: Define failing capability contracts

**Files:**
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderEngineCapabilityTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/FoliateEpubEngineAdapterTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderCoordinatorTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncLaunchPolicyTest.kt`

- [x] Add matrix and command-requirement tests for all six formats.
- [x] Add PDF/CBZ adapter tests proving search and overlay commands preserve view state and command key.
- [x] Add controller/coordinator tests proving unsupported actions do not mutate native state or dispatch bridge commands.
- [x] Add launch-policy tests proving PDF/CBZ cannot resolve Whispersync attachments while supported formats can.
- [x] Run the focused tests and record RED evidence before production edits.
- [x] Commit the failing tests.

RED evidence: `:composeApp:compileAndroidHostTest` failed before production edits because `ReaderEngineCapability`, `readerEngineCapabilities`, `requiredCapability`, and `supportsReaderEngineCapability` were unresolved in `ReaderEngineCapabilityTest`. The focused command included `ReaderEngineCapabilityTest` and `ReaderWhispersyncLaunchPolicyTest`; failure occurred at compile time after 54 seconds, before any test could pass accidentally.

### Task 2: Implement the capability matrix and behavioral gates

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncLaunchPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt`

- [x] Add `ReaderEngineCapability`, the format matrix, and command/event requirement predicates.
- [x] Gate controller actions before state mutation and clear transient capability state when opening a publication.
- [x] Gate Whispersync launch work and reader controls with the shared matrix.
- [x] Gate Foliate commands and host events without command-key changes.
- [x] Run focused GREEN tests and commit the implementation.

GREEN evidence: the focused capability and launch contracts passed, then the existing Foliate, controller, coordinator, step-consumer, Whispersync sync, readaloud sync, launch-policy, and viewer suites passed 155/155 with zero failures, errors, or skips. The updated common-chrome search source contract also passed. A 195-test reader runtime/chrome batch had 21 failures on the branch versus 20 on clean public `master`; the sole branch delta was the obsolete unconditional-search source assertion, which was updated to require the B3 capability guard and then passed. The 20 remaining failures are baseline reference-fixture/source-shape issues, including the two previously documented runtime asset failures.

### Task 3: Run integrated Android validation

**Files:**
- Verify: `composeApp/src/commonMain/kotlin/paige/navic/reader/`
- Verify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/`
- Verify: `scripts/verify-reader-vendor-assets.ps1`
- Verify: `scripts/verify-third-party-attributions.ps1`

- [x] Run full capability-owner, controller, coordinator, Foliate adapter, Whispersync launch, common chrome, and reader runtime host suites.
- [x] Run `git diff --check`, vendor verification, verifier self-test, and attribution verification.
- [x] Assemble Android debug and inspect packaged metadata/assets. Do not run iOS tasks.
- [x] Record exact test counts, APK digest, and any baseline failures separately.

Integrated evidence: the B3 owner batch passed 156/156 with zero failures, errors, or skips. `git diff --check`, source vendor verification (30/30), verifier tamper self-test, and generated attribution passed. Android debug assembly succeeded without invoking an iOS task. The candidate debug APK is 74,988,168 bytes with SHA-256 `ddacb6e569b5a106fa55998362c4e808425a8d65bff795fcbf99bfc90c7c0b72`, package `darkaxt.navic.debug`, and pre-release metadata `544 / v1.0.11-iota17`; all 30 packaged vendor files and packaged attribution passed. The broad 195-test runtime/chrome batch's remaining 20 failures reproduced exactly on clean public `master` and are not B3 regressions; the detached baseline worktree was removed.

### Task 4: Publish `v1.0.11-iota18`

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-engine-capabilities-implementation.md`

- [x] Record candidate evidence and mark B3 candidate-validated.
- [x] Set `versionCode=545` and `versionName="v1.0.11-iota18"` only after code validation.
- [x] Verify the version, naming continuity, and absence of unpadded iota/kappa/lambda refs.
- [x] Rebase on current public master if it advanced and rerun affected validation.
- [x] Push the integrated candidate, create the annotated tag, and publish Android only.
- [x] Verify GitHub Actions succeeded with both iOS jobs skipped.
- [x] Download and independently verify the signed public APK, metadata, digest, certificate, vendor assets, and attribution.
- [x] Upgrade `darkaxt.navic` in place with ADB and verify resumed startup plus targeted error logs.

Release-candidate evidence: `verify-android-release-version.ps1` accepted `v1.0.11-iota18`; source metadata is `545 / v1.0.11-iota18`; `git diff --check` passed; `iota18` was absent from remote tags and releases; and no unpadded iota, kappa, or lambda remote ref existed. The branch was four commits ahead and zero behind public `master` at `4cb276b2`, so no rebase was required. Post-bump owner tests remained 156/156. Android debug assembly succeeded; the 74,988,172-byte APK has SHA-256 `45d46cddbe80072cb52951c4f9bc5f2d97908a5674af8f634417c50278935d3c`, package `darkaxt.navic.debug`, metadata `545 / v1.0.11-iota18`, all 30 packaged vendor files, and complete packaged attribution.

Release evidence: release-code commit `922fbddb` is tagged `v1.0.11-iota18`. Workflow `29261226228` completed successfully with Android build and release creation successful, and both iOS jobs plus IPA attachment skipped. Public `Navic.apk` is 46,225,284 bytes with SHA-256 `62e210ab7536e8448366a141970bcc07ef0a67d3dd09de7706856bbb42ed410c`, matching GitHub's digest; APK Signature Scheme v2 uses certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; metadata is `545 / v1.0.11-iota18`; and packaged vendor 30/30 plus attribution passed. The emulator upgraded public `darkaxt.navic` in place from `iota17`, resumed `MainActivity` as PID `15663`, and had no targeted error-level startup logs.

### Task 5: Record immutable evidence and clean

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-engine-capabilities-implementation.md`

- [x] Record release commit, workflow, public APK, certificate, metadata, iOS skip state, and ADB evidence.
- [x] Push the evidence commit and independently verify public master, tag peel, release, and contiguous naming.
- [x] Remove only this isolated worktree and branch after proving all commits are public.
- [x] Recheck every protected Navic worktree head and dirty state.

Immutable-ref evidence: local HEAD and public `master` resolve to release-evidence commit `d84db407`; annotated tag object `15dc93f9` peels to release-code commit `922fbddb`. The public release remains non-draft and non-prerelease with the same 46,225,284-byte APK and SHA-256 `62e210ab7536e8448366a141970bcc07ef0a67d3dd09de7706856bbb42ed410c`. Public naming is contiguous `iota01` through `iota18`, with no unpadded iota, kappa, or lambda tag/release.

Cleanup boundary: this worktree is clean at `d84db407` and all commits are on public `master`. Protected heads remain primary animation `8340a4b8`, destination-aware `b83092b9`, rev4 `5a1ed120`, page-wave baseline `4bc24e1a`, and playlist/master `4cb276b2`; their existing tracked and untracked states are outside B3 and remain untouched. The disposable public-APK verification directory and detached B3 baseline worktree were removed. Only `navic-qa-tranche-3-engine-capabilities` and local branch `fix/qa-tranche-3-engine-capabilities` are eligible for final removal.

## Self-Review

- B3 only; B15/B24 and later roadmap units remain pending.
- One matrix owns all optional capability decisions.
- Unsupported actions stop before state mutation and again at the adapter boundary.
- No timeout, symlink, backup, iOS task, or ebook-animation edit is introduced.
- Release naming remains `iota##`; the next code release is `v1.0.11-iota18`.

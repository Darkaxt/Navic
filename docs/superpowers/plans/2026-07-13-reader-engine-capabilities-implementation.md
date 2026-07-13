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

- [ ] Add matrix and command-requirement tests for all six formats.
- [ ] Add PDF/CBZ adapter tests proving search and overlay commands preserve view state and command key.
- [ ] Add controller/coordinator tests proving unsupported actions do not mutate native state or dispatch bridge commands.
- [ ] Add launch-policy tests proving PDF/CBZ cannot resolve Whispersync attachments while supported formats can.
- [ ] Run the focused tests and record RED evidence before production edits.
- [ ] Commit the failing tests.

### Task 2: Implement the capability matrix and behavioral gates

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncLaunchPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt`

- [ ] Add `ReaderEngineCapability`, the format matrix, and command/event requirement predicates.
- [ ] Gate controller actions before state mutation and clear transient capability state when opening a publication.
- [ ] Gate Whispersync launch work and reader controls with the shared matrix.
- [ ] Gate Foliate commands and host events without command-key changes.
- [ ] Run focused GREEN tests and commit the implementation.

### Task 3: Run integrated Android validation

**Files:**
- Verify: `composeApp/src/commonMain/kotlin/paige/navic/reader/`
- Verify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/`
- Verify: `scripts/verify-reader-vendor-assets.ps1`
- Verify: `scripts/verify-third-party-attributions.ps1`

- [ ] Run full capability-owner, controller, coordinator, Foliate adapter, Whispersync launch, common chrome, and reader runtime host suites.
- [ ] Run `git diff --check`, vendor verification, verifier self-test, and attribution verification.
- [ ] Assemble Android debug and inspect packaged metadata/assets. Do not run iOS tasks.
- [ ] Record exact test counts, APK digest, and any baseline failures separately.

### Task 4: Publish `v1.0.11-iota18`

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-engine-capabilities-implementation.md`

- [ ] Record candidate evidence and mark B3 candidate-validated.
- [ ] Set `versionCode=545` and `versionName="v1.0.11-iota18"` only after code validation.
- [ ] Verify the version, naming continuity, and absence of unpadded iota/kappa/lambda refs.
- [ ] Rebase on current public master if it advanced and rerun affected validation.
- [ ] Push the integrated candidate, create the annotated tag, and publish Android only.
- [ ] Verify GitHub Actions succeeded with both iOS jobs skipped.
- [ ] Download and independently verify the signed public APK, metadata, digest, certificate, vendor assets, and attribution.
- [ ] Upgrade `darkaxt.navic` in place with ADB and verify resumed startup plus targeted error logs.

### Task 5: Record immutable evidence and clean

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-engine-capabilities-implementation.md`

- [ ] Record release commit, workflow, public APK, certificate, metadata, iOS skip state, and ADB evidence.
- [ ] Push the evidence commit and independently verify public master, tag peel, release, and contiguous naming.
- [ ] Remove only this isolated worktree and branch after proving all commits are public.
- [ ] Recheck every protected Navic worktree head and dirty state.

## Self-Review

- B3 only; B15/B24 and later roadmap units remain pending.
- One matrix owns all optional capability decisions.
- Unsupported actions stop before state mutation and again at the adapter boundary.
- No timeout, symlink, backup, iOS task, or ebook-animation edit is introduced.
- Release naming remains `iota##`; the next code release is `v1.0.11-iota18`.

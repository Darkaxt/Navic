# Reader Managed Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do not use subagents for this goal.

**Goal:** Move Android reader assets out of OS-evictable cache storage while preserving durable fonts and explicitly releasing reconstructable publication/read-aloud sessions.

**Architecture:** A new Android storage-layout component owns one `filesDir/reader` asset root, one-time legacy migration/stale cleanup, session-size/clear operations, and idempotent directory leases. Existing resolvers attach leases to resolved resources; Android runtime hosts release them on disposal without changing the WebView URL contract.

**Tech Stack:** Kotlin Multiplatform, Android `Context`/`File`, Compose lifecycle effects, WebViewAssetLoader, Media3, Kotlin test, Gradle, ADB, GitHub Actions.

---

## File Structure

- Create `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderManagedStorage.android.kt`: managed/legacy roots, initialization, font migration, session accounting/clearing, and leases.
- Create `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderManagedStorageTest.kt`: filesystem behavior and lease tests.
- Modify `ReaderPublicationResource.android.kt`: use the managed root contract and return a publication lease.
- Modify `StorytellerReadaloudAudioCache.android.kt` and `StorytellerReadaloudRuntimeLoader.android.kt`: expose and combine read-aloud session leases.
- Modify `ReaderPublicationRuntimeHost.android.kt` and `ReaderReadaloudRuntimeHost.android.kt`: retain and release leases at host disposal, after media release where applicable.
- Modify `ReaderEngineWebViewHost.android.kt` and `ReaderFontImporter.android.kt`: bind the asset loader and fonts to the managed root.
- Modify `StorageManager.android.kt`: include both managed and legacy session trees in size/clear operations while excluding fonts.
- Modify owning tests and QA documents; prepare Android `v1.0.11-iota15` / `542`.

### Task 1: Define managed storage and migration

- [x] **Step 1: Write failing filesystem tests**

Add tests that construct separate temporary managed and legacy roots and assert that the missing API:

```kotlin
initializeReaderManagedStorage(managedRoot, legacyRoot)
```

moves legacy fonts, preserves an existing valid managed font, deletes stale managed/legacy session directories, and leaves managed fonts untouched.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderManagedStorageTest
```

Expected: compilation fails because `initializeReaderManagedStorage` and the managed storage API do not exist.

- [x] **Step 3: Implement the storage layout**

Create constants for `reader`, `fonts`, `reader-publications`, and `storyteller-readaloud`; implement synchronized one-time context initialization plus an internal two-root initializer that is directly testable. Prefer directory/file rename and use copy fallback only after checking the target. Delete a legacy source only after the target is verified.

- [x] **Step 4: Verify GREEN and commit**

Run the focused test until it passes, then commit the new storage component and test.

### Task 2: Add idempotent session leases

- [x] **Step 1: Write failing lease and accounting tests**

Assert that `ReaderSessionLease.release()` deletes only its leased directories, repeated release is harmless, and `readerSessionStorageSizeBytes` / `clearReaderSessionStorage` cover both session directory names while excluding `fonts`.

- [x] **Step 2: Run RED, implement the minimal API, and run GREEN**

Implement an idempotent lease with private normalized directory ownership and helpers for managed/legacy session size and clearing. Do not add an LRU or arbitrary-path deletion API.

- [x] **Step 3: Commit the storage lifecycle primitive**

Commit the passing lease/accounting behavior separately.

### Task 3: Attach leases to publication and read-aloud resolution

- [x] **Step 1: Extend resolver/Storyteller tests first**

Add assertions that a resolved publication exposes a live lease, Storyteller combines the resolver and extracted-audio directories, and release removes both trees. Run the exact resolver, Storyteller cache, and loader tests and observe the expected missing-property failures.

- [x] **Step 2: Implement resolver lease propagation**

Add the publication-directory lease to `ReaderResolvedPublicationResource`, the extracted directory lease to `MaterializedStorytellerReadaloudAudio`, and the combined lease to `StorytellerReadaloudRuntime`.

- [x] **Step 3: Run owning tests and commit**

Run `BinderyReaderPublicationResolverTest`, `StorytellerReadaloudAudioCacheTest`, and `StorytellerReadaloudRuntimeLoaderTest`; commit only after all pass.

### Task 4: Bind Android hosts and storage UI to managed ownership

- [x] **Step 1: Write failing Android source-contract tests**

Prove the asset loader and font importer use `readerManagedStorageRoot`, both runtime hosts retain/release leases, read-aloud releases the controller before files, and `StorageManager` delegates size/clear to managed plus legacy session helpers.

- [x] **Step 2: Run RED and implement host/storage wiring**

Update Android call sites. Runtime hosts keep leases until disposal so renderer-generation recovery does not lose files. Keep common `ReaderScreen` and all ebook animation sources untouched.

- [x] **Step 3: Run focused and adjacent owner suites**

Run managed-storage contracts, resolver/font/Storyteller suites, WebView lifecycle/ack contracts, storage UI source tests, and reader runtime navigation/chrome tests. Record exact JUnit counts.

- [x] **Step 4: Commit host ownership wiring**

Commit after focused tests pass with zero failures/errors.

Validation evidence: 25/25 exact storage-owned and adjacent Android host tests passed with zero failures, errors, or skips. A broader 104-test source-contract run also produced 85 passes; its 19 failures are confined to unrelated common ebook component expectations owned by the concurrent animation worktrees.

### Task 5: Validate, document, and release Android

- [ ] **Step 1: Run broad reader/governance/build gates**

Run reader controller/coordinator/bridge tests, JavaScript syntax and Chromium harnesses, all 30 source and packaged vendor checks, attribution checks, and `:androidApp:assembleDebug :androidApp:assembleReaderDev`.

- [ ] **Step 2: Validate managed storage with ADB**

Seed legacy font/session fixtures, install the candidate, and verify migration/cleanup under the app sandbox. Open a cached/available publication, kill only the renderer, verify exact-locator recovery and live files, then leave the reader and verify lease cleanup. Record PIDs, paths, and fatal-log checks.

- [ ] **Step 3: Update QA evidence and prepare only `iota15`**

Set `versionCode=542`, `versionName=v1.0.11-iota15`; update B22 and the roadmap while preserving all remaining findings. Verify no `kappa`/`lambda` refs.

- [ ] **Step 4: Integrate public master, tag, publish, and verify**

Fetch/rebase only this isolated branch if required, rerun affected gates, push public `master`, create annotated `v1.0.11-iota15`, and run the Android-only publisher. Download the public APK and independently verify digest, v2 certificate, embedded version, packaged governance, and in-place startup. All iOS jobs must remain skipped.

- [ ] **Step 5: Record immutable evidence and clean**

Push the evidence commit, verify public master/tag/release refs, then remove only `.codex-temp/navic-qa-tranche-3-reader-storage` and local `fix/qa-tranche-3-reader-storage`. Do not modify any ebook worktree.

## Self-Review

- B22 coverage: active publications/audio are non-evictable, fonts are durable, and every reconstructable tree has process-start, host-disposal, and manual-clear cleanup.
- Compatibility: the WebView origin/path and remote-font metadata URLs do not change.
- Scope: no common reader UI, ebook animation, LRU, B3/B8/B15/B23/B24 state, iOS, or timeout work is included.
- Delivery: next release is `iota15`, not a new Greek letter.

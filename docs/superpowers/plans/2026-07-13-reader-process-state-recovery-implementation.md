# Reader Process-State Recovery Implementation Plan

**Goal:** Complete `B15` and the process-state portion of `B24` without retaining the reader runtime in a ViewModel.

**Release:** `v1.0.11-iota19`, `versionCode=546`

## Task 1: Lock the recovery contract with failing tests

- [ ] Add common tests for snapshot encoding, `SavedStateHandle` recreation, publication matching, malformed payloads, semantic selection, note draft text, and search intent.
- [ ] Add controller/coordinator tests proving submitted search is reissued after a fresh open while derived search results, TOC, popups, Whispersync runtime state, and command state are not retained.
- [ ] Add capability tests proving PDF/CBZ drop unsupported search and Whispersync dialog restoration.
- [ ] Run the focused tests and record the expected RED caused by missing recovery symbols.

## Task 2: Implement the retained-state boundary

- [ ] Add an immutable serializable reader process snapshot with a bounded, tolerant codec.
- [ ] Add `ReaderProcessStateViewModel` backed only by `SavedStateHandle` and register it with Koin.
- [ ] Add controller/coordinator restoration operations that validate publication identity and capabilities and emit only reconstructed commands.
- [ ] Keep `ReaderCoordinator`, WebView/runtime hosts, sidecars, playback plans, and coroutine scopes in `ReaderScreen`.

## Task 3: Wire immediate input capture and event-driven reconstruction

- [ ] Key the ViewModel to the reader route and bind it to the opened publication.
- [ ] Capture accepted coordinator state transitions without allowing `open()` to erase the pre-recreation snapshot.
- [ ] Propagate search-input and selection-note-input changes immediately from dialogs.
- [ ] Open from the best durable locator, apply the transient snapshot, then let publication readiness and command acknowledgements reconstruct derived state.
- [ ] Clear the transient snapshot on intentional reader exit.

## Task 4: Verify host and device behavior

- [ ] Run focused common and Android host tests, then the full reader owner batch.
- [ ] Run source-vendor, tamper, attribution, JavaScript, and Android debug/reader-dev assembly gates.
- [ ] Exercise EPUB, PDF, and CBZ on Android across background/foreground and process recreation. Confirm submitted search is reissued for EPUB, unsupported search is not issued for PDF/CBZ, note/dialog state returns, and the latest durable locator reopens.
- [ ] Confirm renderer-kill recovery remains acknowledgement-driven and no AndroidRuntime fatal is emitted.

## Task 5: Publish and independently validate iota19

- [ ] Update the QA analysis and deployment roadmap with implementation evidence and close `B15`/`B24`.
- [ ] Bump only Android metadata to `versionCode=546` and `versionName=v1.0.11-iota19`.
- [ ] Commit, integrate onto current public `master`, create annotated `v1.0.11-iota19`, and publish the Android release with every iOS job skipped.
- [ ] Download the public APK and verify digest, v2 certificate, embedded metadata, packaged governance, and in-place startup.
- [ ] Record immutable release evidence, push the evidence commit, and remove only this completed temporary worktree/branch after confirming protected worktrees are unchanged.

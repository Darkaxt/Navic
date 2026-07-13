# Navic QA Remediation Deployment Roadmap

Date: 2026-07-13
Baseline: `master` @ `2de204a1` (`feat(reader): coordinate visual and settled slide targets`)
Source audit: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
Released foundation: `v1.0.11-theta94`
Released Tranche 1: `v1.0.11-iota1`
Released Tranche 2 storage slice: `v1.0.11-iota2`
Released Tranche 2 download slice: `v1.0.11-iota3`
Released Tranche 5 session-client slice: `v1.0.11-iota7`
Released Tranche 5 network-policy slice: `v1.0.11-iota9`
Released Tranche 5 consolidated delivery: `v1.0.11-iota11`
Released Tranche 3 bridge-diagnostics slice: `v1.0.11-iota12`
Released Tranche 3 command-acknowledgement slice: `v1.0.11-iota13`
Type: **Cross-cutting remediation design and deployment roadmap.** Each tranche requires its own TDD implementation plan before production code changes.

## Objective

Provide a complete disposition and deployment path for every numbered QA-audit entry without mixing unrelated fixes into one release. The original audit has 60 numbered entries:

- 56 actionable findings.
- 2 Android-only scope notes (`A20`, `B21`).
- 1 duplicate cross-reference (`B1` → `A10`).
- 1 verified non-bug (`C12`).

`v1.0.11-theta94` released fixes for `C1`, `C5`, and `C8`. Current-source review supersedes `B14` as written because `ReaderProgressSaveGate` no longer matches relocation-reason strings. This initially left **52 pending implementation findings**; current delivery accounting is maintained below.

## Delivery principles

1. **Android only.** iOS completeness is not a release gate and no tranche adds iOS features.
2. **No cancellation timeouts.** Readiness, acknowledgements, ownership, and retries are event/state driven. Time-based operations are allowed only for sampling, backoff scheduling, or monitoring heartbeats; they never force hard cancellation.
3. **One behavioral boundary per commit.** A tranche may contain several commits, but every commit must have a focused failing test first and remain independently reviewable.
4. **One public prerelease per deployable tranche.** Do not batch two unrelated tranches merely to reduce release count.
5. **Forward fixes for persisted state.** Database and secure-storage changes use tested forward migrations. Rollback means shipping a corrective build, never installing an older schema over user data.
6. **Preserve ebook worktree ownership.** Reader tranches use isolated worktrees from the then-current `master`, rebase before integration, and never stage unrelated reader-animation edits from another task.
7. **Observed behavior is the gate.** Source tests protect contracts, but playback/reader changes also require device evidence on the phone or tablet.

## Target architecture

### Lifecycle ownership

- `AppCoroutineScope` exists for process-lifetime infrastructure.
- `SessionCoroutineScope` is replaceable and is cancelled on logout before credentials are cleared.
- Screen/ViewModel work remains screen-scoped.
- MediaController futures and WebViews have explicit owners with deterministic `close`/release paths that do not depend on already-cancelled scopes.

### Serialized ownership and work queues

- Audio ownership is a serialized `claim/release` state machine. `StateFlow` exposes the result but does not provide mutual exclusion itself.
- Sync actions are ordered durable work with terminal/transient failure classification and poison-item isolation.
- Download intent is durable. Cancellation and retry compare an intent generation so a stale retry cannot resurrect a cancelled item.

### Reader command protocol

- JS→Kotlin decoding returns a typed success/failure result with bounded diagnostics.
- Kotlin→JS commands carry stable IDs and are removed only after explicit acknowledgement.
- A WebView generation owns its bridge and dispatch ledger. Renderer recreation replays publication and locator state from durable/retained state.
- Publication identity and last locator are durable; small dialog/draft state is saved; large derived coordinator state is reconstructed.

### Persistence and networking

- Each entity has one database owner. User intent/work registries are not treated as disposable cache.
- Network clients are isolated per origin/service but created from shared JSON and baseline builder policy.
- Authentication cannot leak across origins. External fetches pass explicit scheme/host allowlists.
- Optional integrations expose typed availability/error state rather than collapsing all failures into empty data.

## Deployment pipeline

Every tranche uses the following pipeline:

1. Create `fix/<tranche-name>` in a dedicated `.codex-temp` worktree from current `master`.
2. Revalidate each assigned finding against current source; mark a finding superseded only with direct source/test evidence.
3. Write the tranche implementation plan and tests. Run tests red before production edits.
4. Implement in dependency order, committing each behavior boundary separately.
5. Rebase onto current `master`; rerun the tranche test matrix and Android APK build.
6. Fast-forward `master` without staging any unrelated worktree changes.
7. Publish the next unused release tag through GitHub Actions. Keep the `{letter}{number}` series monotonic; after `v1.0.11-iota10`, continue with `v1.0.11-iota11` unless the user explicitly advances the letter.
8. Verify workflow, release asset digest, APK signature, embedded version, install, launch, and tranche-specific device behavior.
9. Record release evidence in the audit/plan, push the evidence commit, then remove the worktree, branch, and downloaded verification artifact.

### Universal release gates

- `git diff --check` passes.
- All newly added tests pass.
- Affected-package Android host tests pass.
- `:androidApp:assembleDebug` passes locally.
- The tag workflow's signed Android job and release-creation job pass.
- Downloaded `Navic.apk` digest matches GitHub, `apksigner verify` succeeds, and `aapt dump badging` matches the tag.
- Installation and launch succeed on a connected Android device.
- Known unrelated reader-harness failures are recorded, not silently described as green.

## Tranche 1 — Playback ownership and controller lifecycle

**Findings:** `B9`, `B10`, `C9`, `C10`, `C11`

**Why first:** These findings can stop or overlap playback and directly affect the walking/roaming use case. They sit below UI and should be corrected before decomposing the media ViewModel.

### Change units

1. Add `AudioPlaybackOwnershipCoordinator` with serialized `claim(owner)`, `release(owner)`, and replayable `owner: StateFlow<AudioPlaybackOwner?>`. Migrate music and audiobook players together (`B9`).
2. Decide the service boundary after ownership works: keep two signature-protected services only if external-controller routing tests prove deterministic session selection; otherwise make readaloud in-app only (`B10`).
3. Introduce an Android MediaController connection owner that handles future success/failure, service disconnect, and synchronous release (`C10`, `C11`). Apply the same future-result pattern to `ReadaloudAudioController` while touching the shared boundary.
4. Persist a stable shuffle seed/order or a resolved upcoming-ID sequence alongside queue state (`C9`). Restore must reproduce the same next-five order.

### Required proof

- Simultaneous claim, claim-before-subscribe, handoff, release, and stale-release unit tests.
- Failed `buildAsync()` callback test proves no main-thread `ExecutionException` escapes.
- ViewModel clear source/behavior test proves `MediaController.releaseFuture` is not launched in `viewModelScope`.
- Process-restart test proves current item and next-five IDs are unchanged with shuffle enabled.
- Device matrix: music→readaloud→music handoff, Bluetooth interruption, cached queue, uncached cellular queue, and process recreation.

### Rollout and rollback

- Ship behind no user-facing flag; ownership must be singular and deterministic.
- Collect diagnostics for owner transitions and controller connection states without logging credentials/URLs.
- Roll back by forward release reverting coordinator wiring; retain the persisted shuffle format with backward-compatible defaults.

### Release evidence

- Released `v1.0.11-iota1` from commit `790028f2` on 2026-07-13.
- GitHub Actions run `29213897610` passed release build, APK signature verification, artifact upload, and release creation; checks run `29213897640` passed wrapper validation.
- Public `Navic.apk` SHA-256: `8c15e411766dfeb8a5c5733a0840eb9e302b3975a8b117b3ee36f668133d95e2`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7` (`CN=Darkaxt Navic Release`).
- Downloaded APK embeds `versionCode=523` and `versionName=v1.0.11-iota1`; signed APK installed and launched on `emulator-5554` with no fatal or MediaController connection error during startup smoke testing.
- Focused host tests cover concurrent/replayable/stale ownership claims, failed and disconnected controller futures, synchronous release, readaloud service visibility, and exact persisted shuffle-order reconstruction.

## Tranche 2 — Session, sync, and download integrity

**Findings:** `A13`, `A14`, `A15`, `A18`, `A19`, `C2`, `C3`, `C4`, `C6`, `C7`, `C15`

**Dependency order:** lifetime ownership → database ownership → queue correctness → mapper/storage cleanup.

### Change units

1. Add app and replaceable session scopes; move credential-bound sync/download jobs to the session child (`A13`). Logout first cancels/joins session work, then clears credentials and recreates a fresh child on login.
2. Make `DownloadDatabase` the sole owner of `DownloadEntity`; add a forward migration that copies active rows from `cache.db` before removing the duplicate schema binding (`A19`, `A18`). Keep player-state DataStore separate but document the storage contract.
3. Replace `Channel.UNLIMITED` with DAO-backed pending work or a bounded wakeup channel whose source of truth is Room (`A14`). Name and justify worker/concurrency constants rather than exposing arbitrary preferences (`A15`).
4. Add durable download intent generation/tombstones so cancel wins over stale retry (`C3`).
5. Remove `syncMutex.isLocked` check-then-act. Serialize connectivity/manual/enqueue triggers through one sync actor/mutex entry (`C4`).
6. Add ordered sync actions with attempt count, next-attempt scheduling, terminal/transient classification, and dead-letter visibility. A poison action cannot block later work (`C6`). Backoff schedules future work; it does not cancel in-flight requests.
7. Remove manual PlayerStateRepository DCL and let DI own one Android instance (`C7`).
8. Replace the persisted `"unknown artist"` ID sentinel with nullable/explicit unknown identity through entity, mapper, domain, and UI layers (`C2`).
9. Make logout transactionally clear both sync scheduling state and queued work for the outgoing account, or namespace both by account (`C15`).

### Required proof

- Room migration tests from current production schemas with real fixture rows for downloads and pending sync actions.
- Cancel-vs-retry concurrency tests and process-restart download recovery test.
- Reconnect-during-sync test proves queued actions flush after the current cycle.
- Poison 4xx action does not block a later valid action; transient 5xx/network remains queued.
- Logout/login test proves old credentials cannot be used after logout and new session jobs run normally.
- Large-library queue test demonstrates bounded memory independent of queued song count.

### Rollout and rollback

- Release database ownership migration separately from sync behavior if the migration changes both DB versions.
- Before tagging, install over the previous public APK with populated download/sync fixtures.
- Never downgrade schemas. Any migration defect receives a forward repair release.

### Storage slice release evidence

- Released `v1.0.11-iota2` from commit `2e63e7aa` on 2026-07-13.
- GitHub Actions run `29214968632` passed release build, APK signature verification, artifact upload, and release creation; checks run `29214968629` passed.
- Public `Navic.apk` SHA-256: `874b0ecbff1925b7f428cc6c3167cb9673c5b7ce5986becd35ce580b5926daed`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=524`, `versionName=v1.0.11-iota2`.
- A signed upgrade over a populated `v1.0.11-iota1` install migrated cache schema `20→21`, removed cache `DownloadEntity`, copied a legacy-only row, retained the conflicting current `downloads.db` row, and cold-launched successfully on `emulator-5554`.
- Fixture-backed SQLite tests cover legacy-row reads, destination-wins conflict handling, and migration removal of only the duplicate table. Storage ownership is documented in `docs/architecture/storage-ownership.md`.

### Download slice release evidence

- Released `v1.0.11-iota3` from commit `4ab721b4` on 2026-07-13.
- GitHub Actions run `29216297092` passed release build, APK signature verification, artifact upload, and release creation; checks run `29216297093` passed.
- Public `Navic.apk` SHA-256: `f55fe6141eba92bc1028ccc49ba809b98b6c34df06d7bed85c339396d1f5c78e`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=525`, `versionName=v1.0.11-iota3`.
- A signed upgrade over populated download schema 4 migrated to schema 5, preserved a downloaded row, recovered a queued missing-song row into a generation-1 cancellation tombstone, and cold-launched successfully on `emulator-5554`.
- Tests cover cancel-vs-retry generations, stale completion rejection, 100,000 enqueue wakeups remaining bounded to one in-memory signal, one-row-at-a-time restart recovery, migration fixtures, and named sync concurrency/batch limits.

### Sync slice release evidence

- Released `v1.0.11-iota4` from commit `be60c548` on 2026-07-13.
- GitHub Actions run `29217711517` passed the Android release build, APK signature verification, artifact upload, and release creation; iOS was skipped.
- Public `Navic.apk` SHA-256: `1a6d6886be896e98b5be3088a90c8267bf488e6a5b67e2da915a4384dfaed276`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=526`, `versionName=v1.0.11-iota4`.
- A signed in-place upgrade from `v1.0.11-iota3` launched successfully on `emulator-5554` with a live app process and no fatal or Room migration error. Direct SQLite inspection confirmed cache schema 22, all retry/dead-letter columns, and `index_SyncActionEntity_deadLettered_nextAttemptAtEpochMs_id`.
- Focused tests cover bounded actor ownership, migration defaults/index creation, terminal/transient classification, bounded exponential retry, and poison-action continuation. Android debug assembly passed. The broad host suite still has 32 unrelated source-contract failures in concurrently changing reader/player tests and is not claimed green by this slice.

### Session lifetime slice release evidence

- Released `v1.0.11-iota5` from commit `8481a4cf` on 2026-07-13.
- GitHub Actions run `29219295227` passed the Android release build, APK signature verification, artifact upload, and release creation; iOS was skipped.
- Public `Navic.apk` SHA-256: `20712b85f757ff08ff96d0e1582c1ea8db884c1d5fe9ad579e0aa0e6b82e525b`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=527`, `versionName=v1.0.11-iota5`.
- A signed in-place upgrade from `v1.0.11-iota4` installed and launched on `emulator-5554`; the app process remained alive with no fatal, Room, or Koin startup error.
- Tests prove session cancellation joins old work before replacement, repeating workers restart in the new child, post-login full sync is actor-owned and awaitable, enqueue cannot race after logout, and logout orders joined cancellation before sync queue/timestamp and credential clearing. Android debug assembly passed.

### Nullable artist identity slice release evidence

- Released `v1.0.11-iota6` from commit `c50a35e2` on 2026-07-13.
- GitHub Actions run `29220154972` passed the Android release build, APK signature verification, artifact upload, and release creation; iOS was skipped.
- Public `Navic.apk` SHA-256: `070a674582039c57e897c5650de2104a7f39797c16a115ee1d9190bad8323301`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=528`, `versionName=v1.0.11-iota6`.
- A signed in-place upgrade from `v1.0.11-iota5` installed and launched on `emulator-5554`. Direct SQLite inspection confirmed cache schema 23, nullable `artistId` columns in both `AlbumEntity` and `SongEntity`, and zero remaining legacy `unknown artist` sentinel rows.
- Tests cover nullable identity resolution, real-ID preservation, mixed-case/whitespace sentinel conversion, physical column nullability, and explicit behavior for artist grouping, queue scoring, artist queries, and collection UI fallbacks. Android debug assembly passed.

## Tranche 3 — Reader bridge and process recovery

**Findings:** `B3`, `B4`, `B5`, `B6`, `B8`, `B15`, `B22`, `B23`, `B24`

**Current nuance:** Bridge decode diagnostics shipped in `iota12`. The `iota13` candidate makes commands acknowledgement-driven and preserves publication plus latest locator across renderer generations. Bridge lifetime, cache policy, capability gating, managed session storage, and process-death restoration of small transient drafts remain separate change units.

### Change units

1. Add explicit engine capability sets per publication format and gate search/overlay/UI commands (`B3`).
2. Change bridge decoding to `ReaderBridgeDecodeResult` with bounded raw-message diagnostics; one malformed event is logged, persistent protocol failure becomes UI-visible (`B4`).
3. Add command IDs and `commandAck` events. Keep pending commands until ack; reset/replay from current publication/locator on WebView generation changes (`B5`, `B24`). No acknowledgement timeout is used.
4. Construct and remove `ReaderJavascriptBridge` inside the WebView generation lifecycle (`B6`).
5. Use normal local-asset caching instead of `LOAD_NO_CACHE` plus unconditional cache clearing (`B8`).
6. Classify reader state: durable publication/locator, saved small drafts/dialog selection, reconstructable search/coordinator/runtime state (`B15`, `B24`). Introduce a ViewModel only for the retained state boundary, not as a container for the entire controller.
7. Move active publication/imported-font session files from evictable `cacheDir` to managed files storage with explicit cleanup on publication close/account removal (`B22`).
8. Keep WebView debugging scoped to `readerDev`; reset the global flag when the dev host is disposed and assert release builds cannot enable it (`B23`).

### Required proof

- Decode fuzz/shape tests with bounded logs and no crash.
- Ack state-machine tests: send, duplicate-ready, ack, renderer death, publication change, and replay ordering.
- Instrumented renderer-kill test restores the exact publication and locator.
- Process-death test restores durable/saved state and intentionally reconstructs derived state.
- Format capability tests prevent unsupported commands for CBZ/PDF/etc.
- Device tests for EPUB/PDF/CBZ, background/foreground, renderer kill, and process recreation.

### B4 implementation evidence

- Released in `v1.0.11-iota12`. `ReaderBridgeDecodeResult` classifies malformed JSON, non-object payloads, missing event types, unknown event types, invalid known payloads, and successful events without throwing across the JavaScript interface.
- Rejected messages retain only a control-sanitized diagnostic snapshot capped at 500 characters. The Android bridge no longer logs every raw message; it warning-logs only the first rejection in each consecutive-failure episode.
- `ReaderBridgeMessageProcessor` resets the episode on any decoded event and emits one `ReaderBridgeEvent.Error(code="reader_bridge_protocol")` after three consecutive rejections. The existing adapter/controller/viewer path makes that persistent protocol failure UI-visible; no elapsed-time cancellation or acknowledgement timeout was added.
- Protocol, processor, Android JavaScript bridge, Foliate adapter, and reader controller tests passed 124/124 with zero failures or errors.
- Release commit `a94ad4b2` passed build/release run `29237439257` and checks run `29237439171`; iOS was skipped. The public APK is 46,208,788 bytes with SHA-256 `316ea9d1afa4c8ac5a65c738fe414a3d1dda60befc6a18c4db1e932afcbf5f3d`, established signing certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`, `versionCode=539`, and `versionName=v1.0.11-iota12`.
- The downloaded public APK passed all 30 reader-vendor hashes and packaged attribution verification. A signed in-place upgrade from `iota11` installed on `emulator-5554`; explicit activity start returned `Status: ok`, the app remained alive as PID `31000`, and AndroidRuntime reported no startup error.

### B5/B24 renderer implementation evidence

- `ReaderBridgeDispatchCommand` carries stable opaque IDs; `ReaderBridgeEvent.CommandAcknowledged` rejects blank/missing IDs. `ReaderWebCommandDispatchState` retains a single in-flight queue head until acknowledgement, suppresses duplicate-ready redispatch, replaces state on publication changes, and deterministically rebuilds the open command with the latest observed locator on generation changes.
- `navic-reader.js` deduplicates tracked IDs and posts `commandAck` after the command handler settles. Direct reader-dev harness commands remain untracked and do not alter the production acknowledgement ledger. No acknowledgement timeout or elapsed-time cancellation was introduced.
- Protocol, processor, dispatch, Android host, runtime asset, Storyteller, Foliate adapter, controller, and coordinator tests passed 165/165. The Chromium command-ack runtime, reader smoke/trace smoke, page-turn model, 30/30 source vendor hashes, packaged governance, debug assembly, and reader-dev assembly also passed.
- ADB renderer recovery used a non-zero Alcatraz EPUB locator. Killing only WebView renderer PID `2270` kept `darkaxt.navic.readerdev` PID `2197` alive and created renderer PID `2509`. Generation 1 replayed the same `reader-open-1` and publication key, restored `OEBPS/Text/capitancebolleta01.xhtml` at `epubcfi(/6/16!/4,/2[sigil_toc_id_4],/22/1:265)`, reached `publicationReady`, and acknowledged `reader-open-1`; AndroidRuntime emitted no fatal error.
- This resolves B5 and the renderer-generation portion of B24. B15/B24 process-death restoration for drafts, dialogs, selection, and reconstructed search state remains pending as change unit 6.
- Released as `v1.0.11-iota13` from commit `3703e84a`. Build/release workflow `29247528988` completed successfully; the Android APK and GitHub release jobs passed, while all iOS jobs were skipped.
- Public `Navic.apk` is 46,208,900 bytes with SHA-256 `8800939e69566f8dcf43e7e79cabdad7a3f544e6b9d9c8fbf77387da3ea46725`, matching GitHub's asset digest. APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=540`, `versionName=v1.0.11-iota13`.
- The downloaded public APK passed all 30 reader-vendor hashes and packaged attribution verification. It upgraded `darkaxt.navic` in place from `iota12`/539 on `emulator-5554`; explicit activity start returned `Status: ok`, the app remained alive as PID `2878`, and AndroidRuntime/MediaController startup checks were clean.

### Rollout and rollback

- Ship protocol changes with Kotlin and packaged JS in one commit/release; never deploy one side independently.
- Keep protocol version in diagnostics. A forward rollback release can accept both old/new event shapes during one compatibility window.

## Tranche 4 — Reader media, progress, cache, and localization

**Findings:** `B11`, `B12`, `B13`, `B19`, `B20`

### Change units

1. Stream EPUB ZIP entries once, parsing OPF/SMIL and extracting only referenced audio; remove duplicate whole-archive maps (`B11`).
2. Define one overlay-sync state machine with adapters for Whispersync sidecar and EPUB media-overlay timelines; remove unused duplicate coordinators only after call-site evidence (`B12`).
3. Make start-locator conflict policy timestamp-aware and explicit about rereading. Preserve both candidates for diagnostics when divergence exceeds policy threshold (`B13`).
4. Give Bindery metadata cache mutation-aware invalidation and stale-while-error behavior (`B19`).
5. Move visible reader/Bindery strings into Compose resources; numeric seek labels remain literals (`B20`).

### Required proof

- Large readaloud EPUB test demonstrates peak memory no longer scales as two full archives.
- Overlay contract suite runs the same scenarios against both timeline adapters.
- Cross-device progress tests cover newer-behind, older-ahead, reread, missing timestamp, and equal timestamp.
- Cache invalidation tests cover base URL, API key/account, source mutation, stale fallback, and explicit refresh.
- Resource scan rejects new hard-coded visible strings in scoped packages.

### Rollout and rollback

- Streamed extraction uses a versioned managed cache directory; old cache remains readable for one release and is cleaned after successful regeneration.
- Progress policy changes emit old/new selection diagnostics during canary validation but persist only the chosen locator.

## Tranche 5 — Network, security, and attribution boundaries

**Findings:** `A16`, `A17`, `A21`, `B7`, `B17`, `B18`, `C14`

### Change units

1. Add shared `Json` and `HttpClient` builder policy while creating isolated clients per service/origin (`A16`). Do not share mutable auth plugins across services and do not add cancellation timeouts.
2. Replace public mutable `SessionManager.api` with an internal atomic/session-owned client facade (`A17`).
3. Replace broad `runCatching(...).getOrDefault(empty)` paths with typed optional-integration results and user-visible availability state where absence and failure differ (`C14`).
4. Store Bindery credentials through Android secure storage with a one-time migration from current preferences; scope headers to validated Bindery origins (`B17`).
5. Restrict `fetchExternalText` to HTTPS and an explicit host/purpose allowlist; reject private, loopback, link-local, and redirect escapes (`B18`).
6. Pin foliate-js/pdfjs source versions and hashes and add an update/security-review procedure (`B7`).
7. Add `THIRD_PARTY.md`/notices and expose required attributions in Acknowledgements (`A21`).

### Required proof

- Tests prove auth headers never cross origins or redirects.
- SSRF matrix covers localhost, RFC1918, IPv6 local/link-local, DNS/redirect host changes, unsupported schemes, and approved sources.
- Secure-storage migration preserves the existing key once and clears plaintext storage.
- Optional integration UI distinguishes unavailable, unauthorized, malformed, empty, and stale-fallback states.
- Vendored asset manifest hashes match packaged files.

### Session-client slice release evidence

- Released `v1.0.11-iota7` from commit `7c518ea6` on 2026-07-13.
- GitHub Actions run `29221266585` passed the Android release build, APK signature verification, artifact upload, and release creation; iOS was skipped.
- Public `Navic.apk` SHA-256: `f09d1cf8ad6e745c95b23dd0eb6d09e8dc1e5b13a70e4bb42596b822e0b53d6b`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=529`, `versionName=v1.0.11-iota7`.
- A signed in-place upgrade from `v1.0.11-iota6` installed and launched on `emulator-5554`; the app process remained alive with no fatal startup exception.
- Tests prove each operation observes one atomic client snapshot while replacement can proceed, prevent public mutable API access, and preserve session logout ordering. Android debug assembly passed after rebasing public master.

### Network-policy slice release evidence

- `v1.0.11-iota8` from commit `302eccb3` was published by GitHub Actions run `29222385870`, but independent startup verification found a Koin constructor-injection failure for `NetworkClientFactory`; the immutable release was superseded by the forward repair below and is not the accepted A16 delivery.
- Released `v1.0.11-iota9` from commit `6987e497` on 2026-07-13. GitHub Actions run `29222872454` passed the Android release build, APK signature verification, artifact upload, and release creation; iOS was skipped.
- Public iota9 `Navic.apk` SHA-256: `76bbc8cd9ba66a50b1482b46c80db67d214fd35dd4475b85614c67db7e643cb3`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=531`, `versionName=v1.0.11-iota9`.
- A signed in-place upgrade from the crashing iota8 install launched successfully on `emulator-5554`; the app remained alive as PID `22640` with no fatal or Koin startup error.
- MockEngine tests prove every factory call creates a distinct client, applies the shared User-Agent/JSON policy, and does not leak one client's authorization header into another. A source guard covers all production client construction and the explicit zero-argument Koin registration; affected repository suites and Android debug assembly passed.

### Optional-integration state slice release evidence

- Released `v1.0.11-iota10` from commit `6f888448` on 2026-07-13. GitHub Actions run `29225267656` passed the Android release build, APK signature verification, artifact upload, and release creation; iOS was skipped.
- Public `Navic.apk` SHA-256: `c3bcff82e0758f624e52b051566841ff62cb4966924a873e38c3c5e1b119b958`.
- APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=532`, `versionName=v1.0.11-iota10`.
- A signed in-place upgrade from `v1.0.11-iota9` installed and launched on `emulator-5554`; the app remained alive as PID `25449` with no fatal activity or Koin startup error.
- Repository and display-policy tests distinguish disabled, misconfigured, unauthorized, malformed, unavailable, empty, and stale states. Aurral base discovery paints before parallel supplements, Bindery row loads remain parallel without dropping failures, stale content remains interactive, affected legacy suites passed, and Android debug assembly passed after rebasing public master.

### Consolidated Tranche 5 release evidence

- Released `v1.0.11-iota11` from commit `4f5dfbe7` on 2026-07-13. GitHub Actions build/release run `29235127291` passed the signed Android release build, signature verification, reader-vendor source and packaged verification, attribution verification, artifact upload, and release creation; iOS was skipped. Checks run `29235127286` passed wrapper validation.
- Public `Navic.apk` SHA-256: `14a8fae5c3321e222f59b4fb1fc1548920601f33dc80ea3d3cfd10cbe88e8daa` (46,208,784 bytes). APK Signature Scheme v2 verified with certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`; embedded metadata is `versionCode=538`, `versionName=v1.0.11-iota11`.
- The public APK independently passed all 30 reader-vendor hashes and the packaged acknowledgement check. A signed in-place upgrade installed and launched on `emulator-5554`; the app remained alive as PID `30458` with no AndroidRuntime startup error.
- The mistakenly advanced later-letter releases and tags were withdrawn after `iota11` passed all public gates. The supported public sequence now continues directly from `iota10` to `iota11`.

### Secure-credential slice implementation evidence

- Device migration moved `binderyApiKey=navic-b17-migration-proof` out of `darkaxt.navic_preferences.xml` into a versioned AES-GCM envelope in `navic_secure_credentials.xml`; a recursive plaintext scan of shared preferences returned no match. Plaintext deletion implies successful encrypted commit and exact decrypt-readback under the migration contract.
- The final gate passed 69 focused Bindery/security/DI tests plus Android debug assembly. Tests cover migration success/failure/precedence, Keystore source policy, canonical origin matching, off-origin absolute paths, redirects, per-resource playback/artwork headers, optional integration behavior, and production DI fixtures.

### External-fetch slice implementation evidence

- The final gate reran 19 focused SSRF/DNS/redirect/parser tests with zero failures and passed Android debug assembly. The full branch suite ran 2,319 tests with the exact same 35 unrelated baseline failures as the pre-B18 baseline (2,303 tests), proving the 16 newly added B18 tests introduced no suite regression.
- Tests cover unsupported schemes, credentials, custom ports, fragments, deceptive/off-allowlist hosts, localhost, RFC1918, carrier-grade NAT, IPv4/IPv6 loopback and link-local, IPv6 unique/site-local, multicast, IPv4-mapped private IPv6, empty/mixed DNS answers, redirect host changes, approved sources, malformed HTML, and internal/off-domain cover candidates. Generated acknowledgements include Ksoup `0.2.6` under MIT.

### Reader-vendor provenance slice implementation evidence

- `reader/vendor/manifest.json` records immutable source/package provenance for Foliate `1.0.1` and PDF.js `3.11.174`, including source commits, npm integrity, PDF.js package Git head/build id, licenses, component ownership, and SHA-256 hashes for all 30 shipped vendor files.
- The advisory review identified `GHSA-wgrm-67xf-hhpq` / `CVE-2024-4367` for the pinned PDF.js version. Navic already applies the advisory's `isEvalSupported: false` workaround at its only `getDocument` call; the prior parity test's insecure fallback allowance was removed so this mitigation is now mandatory.
- The deterministic updater is idempotent. The verifier self-test proves the valid tree passes while modified bytes and unmanifested files fail. The same verifier passed against all 30 `assets/reader/vendor/**` files in the assembled Android debug APK.
- GitHub Actions now runs the self-test before every Android build and verifies the final APK before upload. The documented update process requires upstream release/advisory review, immutable artifact validation, upstream diff review, deliberate Navic-patch reapplication, reader tests, and packaged-APK proof.
- The full host suite ran 2,321 tests with the exact same 35 pre-existing failure names as the pre-vendor baseline; both new B7 host tests passed. No vendored JavaScript or reader runtime behavior changed in this slice.

### Attribution-governance slice implementation evidence

- `THIRD_PARTY.md` records Navic's GNU GPL version 3 license and immutable source revisions for Anx Reader, foliate-js `1.0.1`, and PDF.js `3.11.174`. Exact upstream MIT and Apache-2.0 license files are retained under `third_party/licenses`.
- Exact-source verification corrected the audit's Anx copyleft premise: pinned Anx Reader commit `107f4fa74db0e7247c846c49d6211df3edf9887c` is MIT-licensed. Its real copyright and permission notice are preserved instead of claiming nonexistent Anx copyleft obligations.
- AboutLibraries custom records feed the copied components into the existing Acknowledgements screen. The structural verifier checks component identity, version/commit, source URL, copyright, license ID, and MIT text in both the generated Compose resource and the assembled APK.
- The assembled Android debug APK passed all 30 existing reader-vendor hashes and the new packaged-attribution check. A deliberate generated-JSON tamper was rejected.
- The full host suite ran 2,326 tests with the same 35 pre-existing failure names as the pre-attribution baseline; all three new attribution tests and the existing reader-vendor governance tests passed.

### Rollout and rollback

- Credential migration release precedes removal of plaintext-read compatibility by at least one public prerelease.
- Security policy defaults closed. Emergency allowlist changes are configuration/code forward releases, not broad bypasses.

## Tranche 6 — UI and navigation containment

**Findings:** `A4`, `A6`, `A7`, `A8`, `A11`, `B16`

### Change units

1. Start background workers explicitly after login/readiness instead of eager Koin construction (`A4`).
2. Consolidate destination metadata and remove duplicate navigation `when` tables; add exhaustive route tests (`A6`).
3. Replace stringly/unchecked scene metadata where Navigation3 exposes typed keys; otherwise isolate casts behind one tested adapter and pin the dependency (`A7`).
4. Replace or isolate the Material3 invisible-reference sheet animation override behind one compatibility adapter and dependency guard (`A8`).
5. Split ReaderSettingsDialog by stable settings sections without changing preference ownership (`A11`).
6. Document and narrow the Komikku-to-EPUB navigation taxonomy; expose paged/scrolled concepts at the public reader boundary and keep manga-specific modes internal (`B16`).

### Required proof

- Login/startup tests show no library sync before authenticated readiness.
- Every navigation destination has compiler/test-enforced area/root metadata.
- Sheet and custom-scene screenshot/smoke tests across phone/tablet.
- Reader settings source and Compose tests prove section extraction preserves controls and state.

### Rollout and rollback

- Separate startup-worker ownership from navigation/UI refactors if either changes runtime behavior; each can ship as its own prerelease.
- Capture phone/tablet navigation and reader-settings screenshots before and after deployment.
- Roll back by reverting the affected adapter/section commit in a forward release; preference keys and route identifiers remain backward compatible.

## Tranche 7 — Package and state ownership

**Findings:** `A1`, `A2`, `A3`, `A5`, `A12`, `B2`

### Change units

1. Document package rules, then move transport DTO/client/serialization code from `domain` to feature-owned `data/remote` packages incrementally (`A1`).
2. Make DAO/database platform bindings explicit and remove the DatabaseModule workaround after ownership is stable (`A2`).
3. Introduce interactors only for multi-repository transactional workflows exposed by earlier tranches; do not wrap one-line repository calls (`A3`).
4. Replace state-bearing CompositionLocals with explicit owners/parameters; retain only ambient UI concerns (`A5`).
5. Consolidate tiny policy files by feature and responsibility, preserving pure testable functions (`A12`).
6. Either give ReaderCoordinator real cross-component orchestration responsibility or remove mechanical forwarding after reader state boundaries settle (`B2`).

### Required proof

- Dependency-rule/source tests reject transport imports from pure domain packages.
- DI graph starts and affected feature tests pass after every move.
- No large one-shot package migration; each commit compiles and is mechanically reversible.

### Rollout and rollback

- Package moves ship only after rebasing all active feature branches that touch the moved files, avoiding prolonged dual-package compatibility shims.
- State-owner changes deploy one feature at a time with startup/login smoke tests.
- Rollback is commit-local because no persisted format changes occur in this tranche; do not retain duplicate compatibility classes after the transition release.

## Tranche 8 — Large-object decomposition and cache lifecycle

**Findings:** `A9`, `A10`, `C13`

**Why last:** Correctness changes must land before large-file extraction so behavior is tested at stable boundaries and concurrent ebook work no longer churns the same files.

### Change units

1. Extract MediaController connection, queue mutation, playback synchronization, effects, and recovery behind interfaces already proven by Tranches 1–2 (`A9`).
2. Extract reader selection, annotations, progress, overlays, and Whispersync reducers after Tranches 3–4 settle protocol/state contracts (`A10`).
3. Add artwork-color URL/version identity, TTL/invalidation, and logout/maintenance cleanup (`C13`).

### Required proof

- Characterization tests pass before and after every extraction.
- Constructor dependency count and file-size targets are measured, but no extraction is accepted solely for reducing lines.
- Artwork color tests cover changed URL, expired entry, logout, and bounded cleanup.

### Rollout and rollback

- Media and reader decomposition ship in separate prereleases and only after active reader-animation work has merged or stopped touching the same files.
- Extraction commits must be behavior-preserving; any behavior delta is split into its own tested commit and release decision.
- Artwork-color schema changes use a forward migration. Structural extraction rollback is commit-local; database rollback is always a forward repair.

## Superseded finding

### B14 — Progress-save reason allowlist

The audit's stated path is no longer present: `ReaderProgressSaveGate` now gates on publication readiness and placeholder location rather than a freeform reason allowlist. `ReaderController` still uses reason strings for shell-cover dismissal, which is a different UI contract and remains covered by reader tests. Do not implement the original B14 recommendation unless a fresh finding demonstrates progress persistence depends on those strings again.

## Complete finding disposition

| ID | Audit severity | Current disposition | Delivery |
| --- | --- | --- | --- |
| A1 | Medium | Pending | Tranche 7 |
| A2 | Medium | Pending | Tranche 7 |
| A3 | Medium | Pending | Tranche 7 |
| A4 | Low | Pending | Tranche 6 |
| A5 | Medium | Pending | Tranche 7 |
| A6 | Medium | Pending | Tranche 6 |
| A7 | Medium | Pending | Tranche 6 |
| A8 | Medium | Pending | Tranche 6 |
| A9 | High | Pending | Tranche 8 |
| A10 | High | Pending | Tranche 8 |
| A11 | Medium | Pending | Tranche 6 |
| A12 | Low | Pending | Tranche 7 |
| A13 | Medium | Released | `v1.0.11-iota5` |
| A14 | Medium | Released | `v1.0.11-iota3` |
| A15 | Low | Released | `v1.0.11-iota3` |
| A16 | Medium | Released | `v1.0.11-iota9` |
| A17 | Medium | Released | `v1.0.11-iota7` |
| A18 | Medium | Released | `v1.0.11-iota2` |
| A19 | Medium | Released | `v1.0.11-iota2` |
| A20 | Scope note | Excluded | Android-only contract |
| A21 | Medium (governance) | Released | `v1.0.11-iota11` |
| B1 | Cross-reference | Counted as A10 | Tranche 8 |
| B2 | Low | Pending | Tranche 7 |
| B3 | Low | Pending | Tranche 3 |
| B4 | High | Released | `v1.0.11-iota12` |
| B5 | High | Released | `v1.0.11-iota13` |
| B6 | Medium | Pending | Tranche 3 |
| B7 | Medium | Released | `v1.0.11-iota11` |
| B8 | Low | Pending | Tranche 3 |
| B9 | High | Released | `v1.0.11-iota1` |
| B10 | Medium | Released | `v1.0.11-iota1` |
| B11 | Medium | Pending | Tranche 4 |
| B12 | Medium | Pending | Tranche 4 |
| B13 | Medium | Pending | Tranche 4 |
| B14 | Medium | Superseded as written | No deployment |
| B15 | Medium | Pending | Tranche 3 |
| B16 | Low | Pending | Tranche 6 |
| B17 | Medium | Released | `v1.0.11-iota11` |
| B18 | Medium | Released | `v1.0.11-iota11` |
| B19 | Medium | Pending | Tranche 4 |
| B20 | Low | Pending | Tranche 4 |
| B21 | Scope note | Excluded | Android-only contract |
| B22 | Medium | Pending | Tranche 3 |
| B23 | Low | Pending | Tranche 3 |
| B24 | Medium | Renderer slice released; process state pending | `v1.0.11-iota13` + Tranche 3 |
| C1 | Critical | Released | `v1.0.11-theta94` |
| C2 | Medium | Released | `v1.0.11-iota6` |
| C3 | Medium | Released | `v1.0.11-iota3` |
| C4 | Medium | Released | `v1.0.11-iota4` |
| C5 | High | Released | `v1.0.11-theta94` |
| C6 | Medium | Released | `v1.0.11-iota4` |
| C7 | Medium | Released | `v1.0.11-iota2` |
| C8 | High | Released | `v1.0.11-theta94` |
| C9 | Medium | Released | `v1.0.11-iota1` |
| C10 | Medium | Released | `v1.0.11-iota1` |
| C11 | Medium | Released | `v1.0.11-iota1` |
| C12 | Verified | Preserve | Regression tests only |
| C13 | Medium | Pending | Tranche 8 |
| C14 | Low | Released | `v1.0.11-iota10` |
| C15 | Low | Released | `v1.0.11-iota5` |

## Coverage accounting

- Numbered audit entries: 60.
- Original actionable findings: 56.
- Released findings: 26 (`A13`, `A14`, `A15`, `A16`, `A17`, `A18`, `A19`, `A21`, `B7`, `B9`, `B10`, `B17`, `B18`, `C1`, `C2`, `C3`, `C4`, `C5`, `C6`, `C7`, `C8`, `C9`, `C10`, `C11`, `C14`, `C15`).
- Superseded findings: 1 (`B14`).
- Pending implementation findings assigned to tranches: 29.
- Scope notes: 2 (`A20`, `B21`).
- Cross-reference: 1 (`B1`).
- Verified numbered non-bug: 1 (`C12`).

No actionable finding is unassigned. Completion of this roadmap means all eight tranches have shipped or a later evidence-backed revision explicitly changes a finding's disposition.

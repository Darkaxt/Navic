# Navic QA Remediation Deployment Roadmap

Date: 2026-07-13
Baseline: `master` @ `2de204a1` (`feat(reader): coordinate visual and settled slide targets`)
Source audit: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
Released foundation: `v1.0.11-theta94`
Type: **Cross-cutting remediation design and deployment roadmap.** Each tranche requires its own TDD implementation plan before production code changes.

## Objective

Provide a complete disposition and deployment path for every numbered QA-audit entry without mixing unrelated fixes into one release. The original audit has 60 numbered entries:

- 56 actionable findings.
- 2 Android-only scope notes (`A20`, `B21`).
- 1 duplicate cross-reference (`B1` → `A10`).
- 1 verified non-bug (`C12`).

`v1.0.11-theta94` released fixes for `C1`, `C5`, and `C8`. Current-source review supersedes `B14` as written because `ReaderProgressSaveGate` no longer matches relocation-reason strings. This leaves **52 pending implementation findings**, all assigned below.

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
7. Publish the next unused release tag through GitHub Actions. Tranche 1 starts the `iota` series at `v1.0.11-iota1`.
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

## Tranche 3 — Reader bridge and process recovery

**Findings:** `B3`, `B4`, `B5`, `B6`, `B8`, `B15`, `B22`, `B23`, `B24`

**Current nuance:** Renderer-death handling now resets `ReaderWebCommandDispatchState`, so generation replay is partially improved. Commands are still considered consumed before JS acknowledgement, decode failures remain untyped, and the bridge lifetime remains outside the WebView generation.

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
| A13 | Medium | Pending | Tranche 2 |
| A14 | Medium | Pending | Tranche 2 |
| A15 | Low | Pending | Tranche 2 |
| A16 | Medium | Pending | Tranche 5 |
| A17 | Medium | Pending | Tranche 5 |
| A18 | Medium | Pending | Tranche 2 |
| A19 | Medium | Pending | Tranche 2 |
| A20 | Scope note | Excluded | Android-only contract |
| A21 | Medium (governance) | Pending | Tranche 5 |
| B1 | Cross-reference | Counted as A10 | Tranche 8 |
| B2 | Low | Pending | Tranche 7 |
| B3 | Low | Pending | Tranche 3 |
| B4 | High | Pending | Tranche 3 |
| B5 | High | Partially improved; ack pending | Tranche 3 |
| B6 | Medium | Pending | Tranche 3 |
| B7 | Medium | Pending | Tranche 5 |
| B8 | Low | Pending | Tranche 3 |
| B9 | High | Pending | Tranche 1 |
| B10 | Medium | Pending | Tranche 1 |
| B11 | Medium | Pending | Tranche 4 |
| B12 | Medium | Pending | Tranche 4 |
| B13 | Medium | Pending | Tranche 4 |
| B14 | Medium | Superseded as written | No deployment |
| B15 | Medium | Pending | Tranche 3 |
| B16 | Low | Pending | Tranche 6 |
| B17 | Medium | Pending | Tranche 5 |
| B18 | Medium | Pending | Tranche 5 |
| B19 | Medium | Pending | Tranche 4 |
| B20 | Low | Pending | Tranche 4 |
| B21 | Scope note | Excluded | Android-only contract |
| B22 | Medium | Pending | Tranche 3 |
| B23 | Low | Pending | Tranche 3 |
| B24 | Medium | Partially improved; process state pending | Tranche 3 |
| C1 | Critical | Released | `v1.0.11-theta94` |
| C2 | Medium | Pending | Tranche 2 |
| C3 | Medium | Pending | Tranche 2 |
| C4 | Medium | Pending | Tranche 2 |
| C5 | High | Released | `v1.0.11-theta94` |
| C6 | Medium | Pending | Tranche 2 |
| C7 | Medium | Pending | Tranche 2 |
| C8 | High | Released | `v1.0.11-theta94` |
| C9 | Medium | Pending | Tranche 1 |
| C10 | Medium | Pending | Tranche 1 |
| C11 | Medium | Pending | Tranche 1 |
| C12 | Verified | Preserve | Regression tests only |
| C13 | Medium | Pending | Tranche 8 |
| C14 | Low | Pending | Tranche 5 |
| C15 | Low | Pending | Tranche 2 |

## Coverage accounting

- Numbered audit entries: 60.
- Original actionable findings: 56.
- Released findings: 3 (`C1`, `C5`, `C8`).
- Superseded findings: 1 (`B14`).
- Pending implementation findings assigned to tranches: 52.
- Scope notes: 2 (`A20`, `B21`).
- Cross-reference: 1 (`B1`).
- Verified numbered non-bug: 1 (`C12`).

No actionable finding is unassigned. Completion of this roadmap means all eight tranches have shipped or a later evidence-backed revision explicitly changes a finding's disposition.

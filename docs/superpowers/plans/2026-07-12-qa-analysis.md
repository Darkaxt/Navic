# Navic QA Analysis — Architecture, Reader/Bindery, Correctness & Data Safety

Date: 2026-07-12
Base: `master` @ `93290009` ("Prepare v1.0.11-theta92")
Type: **Analysis (audit), not an implementation plan.** See *Remediation candidates for impact prioritization* for dependency grouping; implementation order remains undecided.

> This document complements — and deliberately does not duplicate — the existing
> performance analysis under `music-tab-lag-report.md`, `navic-performance-optimization-plan.md`,
> `navic-cache-refactor-plan.md`, `artwork-consolidation-spec.md`, and
> `docs/superpowers/reports/2026-06-27-performance-opportunity-audit.md`. Performance is
> out of scope here; this pass covers the three areas those docs leave open.

## Scope

Three focus areas, requested explicitly:

1. **Architecture & design** — module/layering, DI, state management, navigation, large-file hotspots, concurrency/networking/persistence design, KMP coherence.
2. **Reader/Bindery subsystem** — the ebook engine, WebView bridge, readaloud/media-overlay, Whispersync, reader UI, and the Bindery (OPDS 2) client. This half of the app is entirely uncovered by prior analysis.
3. **Correctness & data safety** — Room migrations, mapper null-handling, races, process-death recovery, exception handling, cache invalidation, playback correctness.

Navic is evaluated as an **Android-only application**. Kotlin Multiplatform source-set artifacts are relevant only where they affect the Android build or obscure Android ownership; feature completeness on iOS is not a product requirement.

### Methodology

- All code was read at the **`master` branch tip** (`93290009`), via `git show master:<path>`. The working tree is on `feat/page-turn-animation` with uncommitted page-turn WIP and is **excluded** from this audit.
- Every finding cites `file:line` (paths relative to repo root, at master) that was verified against the actual code at that commit.
- Findings marked **Info / Verified** are positive (things done well) or explicit non-bugs, included so the picture is balanced and so future contributors don't "fix" something that's correct.

### Out of scope

- **Performance** (recomposition, subscription hoisting, cache reactivity, tab-switch lag) — thoroughly covered by the four root `navic-*.md` docs and the performance-opportunity audit report. Cross-referenced where a correctness/design issue overlaps.
- **Security & privacy hardening** (cleartext traffic, credential storage, exported components) — noted only where it overlaps a correctness/design finding (e.g. Bindery `X-Api-Key`, `fetchExternalText` SSRF) because the root cause is architectural. A dedicated security pass is recommended separately.
- **iOS product support** — Navic does not support or intend to ship on iOS. iOS stubs and source-set shims are recorded only as non-shipping build context, not as product defects or implementation priorities.

---

## Executive summary

Navic is an ambitious, feature-rich Kotlin Multiplatform / Compose Multiplatform Navidrome fork — music playback, audiobooks (Bindery/OPDS), a foliate-js-backed ebook reader, LidaClips video, and Aurral artist discovery — whose architecture has not kept pace with its scope. The strongest parts are genuinely well-engineered: the reader engine's command/event core is a pure, immutable state machine (every `ReaderController`/coordinator is a `data class` returning step values), the reader↔UI dependency is strictly one-way, Bindery is cleanly walled off from the Subsonic music model, EPUB/SMIL XML parsing is XXE-hardened, Ktor call sites uniformly check status and throw typed errors (no `expectSuccess` foot-guns), and the Aurral/Bindery metadata caches implement a coherent 6-hour freshness policy with graceful stale-on-failure fallback.

The most serious problems are **data-safety, not design**. Both Room databases open with `fallbackToDestructiveMigration(true)` and **zero migrations**, so an upgrade without a registered migration path destroys cached library data, lyrics, playback history, artwork colors, the **pending `SyncActionEntity` queue** (unflushed stars/ratings/scrobbles), and the **`DownloadEntity` registry** (which breaks offline playback even though the audio files themselves survive on disk). This is compounded by `DbRepository.syncLibrarySongs` tolerating malformed album payloads and then deleting "obsolete" albums/songs from the incomplete successful-ID set. Continuous playback-state updates also prevent the debounced persistence collector from becoming quiescent, so durable queue and position state can remain arbitrarily stale while music is playing. Beyond data loss, the dominant themes are **two God objects** (`AndroidMediaPlayerViewModel` at ~1,310 lines / 15 dependencies, `ReaderController` at ~1,632 lines), **fragmented state and concurrency** (several Koin singletons own independent lifetime scopes and state), **a reader JS bridge that silently swallows decode errors and fire-and-forgets commands that can be lost on renderer recreation**, and an **audio ownership publisher that is a replayless `SharedFlow`, not an atomic arbitrator**. The networking layer also duplicates client configuration across six call sites. The audit found no evidence that every architectural concern is a current production failure; the recommended work therefore separates confirmed correctness defects from maintainability improvements.

---

## Severity rollup

56 distinct actionable findings, plus 4 positive "Verified" entries and 2 Android-only scope notes. Cross-references are counted once. Severity reflects demonstrated impact on data integrity, stability, and maintainability, not implementation cost.

| Severity | Architecture | Reader/Bindery | Correctness | Total |
| --- | ---: | ---: | ---: | ---: |
| Critical | 0 | 0 | 1 | **1** |
| High | 2 | 3 | 2 | **7** |
| Medium | 15 | 13 | 9 | **37** |
| Low | 3 | 6 | 2 | **11** |
| Info / Verified (positive) | 1 | 1 | 2 | **4** |

---

## Implementation status

Stage 1 was integrated into `master` and publicly released in `v1.0.11-theta94` on 2026-07-13:

- **C1:** `791ef4ce` removes destructive fallback from both Android Room builders and adds a fail-closed source contract test.
- **C5:** `ed68bd1c` reconciles albums from authoritative summary IDs and suppresses global song deletion whenever any listed album detail was skipped.
- **C8:** `86c0530f` persists structural queue/playback changes immediately and samples progress through the same serialized DataStore writer.

The severity table remains the original audit snapshot. C1, C5, and C8 are closed at the implementation/release level; the remaining findings are not implied to be resolved by this stage.

The complete current disposition, dependency design, and staged deployment gates are maintained in `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`.

---

## Executive risk shortlist

This is a severity-oriented summary of the most consequential confirmed risks, not an implementation sequence. Impact-based prioritization follows as a separate exercise.

1. **🔴 Critical — Destructive Room migrations.** `PlatformModule.android.kt:29,40` — `fallbackToDestructiveMigration(true)` on both DBs destroys persisted database state whenever Room cannot find an upgrade path. Register real migrations and reserve destructive fallback for explicitly accepted downgrade/cache-only cases; never destroy `SyncActionEntity` or the download registry implicitly.
2. **🔴 High — Obsolete-delete after tolerated partial sync.** `DbRepository.kt:195,259-260` — a skipped malformed album is absent from the successful-ID sets and is then deleted as obsolete. Gate deletion on a fully authoritative pass or reconcile against the server summary IDs.
3. **🔴 High — Playback state may remain arbitrarily stale.** `MediaPlayer.kt:154-169` — progress changes every 200ms while persistence waits for one second of quiescence, so `debounce` may emit nothing throughout continuous playback. Separate sampled progress persistence from immediate durable queue/command-state writes.
4. **🔴 High — Reader bridge silently loses decode and dispatch state.** `ReaderBridgeProtocol.kt:550-668`, `ReaderEngineWebViewHost.android.kt:147-176` — log malformed events, acknowledge successful command execution, and replay unacknowledged/open-locator state when the WebView generation changes. Do not use elapsed-time cancellation.
5. **🔴 High — `AudioPlaybackArbitrator` does not arbitrate atomically.** `AudioPlaybackArbitrator.kt` — `replay=0` loses ownership state and reactive self-pause does not serialize claims. Introduce an atomic `claim/release` API and expose its resulting owner as `StateFlow<Owner?>`.
6. **🔴 High — Two God objects.** `ReaderController.kt` (1,632 lines) and `AndroidMediaPlayerViewModel.android.kt` (1,310 lines, 15 deps) — extract per-concern reducers/controllers around confirmed change hotspots.
7. **🟠 Medium — `onCleared` cleanup is launched in an already-cleared scope.** `AndroidMediaPlayerViewModel.android.kt:1264-1271` — AndroidX clears `viewModelScope` before invoking `onCleared`, so the launched cleanup may never run. Release the non-suspending controller future synchronously and give suspending collaborators explicit lifecycle-owned cleanup.
8. **🟠 Medium — Session work lacks an explicit lifetime.** Long-lived managers own independent scopes while logout only clears credentials. Keep an app-lifetime root scope, but put credential-bound sync/download work in a replaceable session child scope cancelled on logout.

---

# Part A — Architecture & design

## A1. `domain/` is a dumping ground for transport, DTOs, and repositories

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/` (41 files), `domain/manager/`, `domain/models/` (159 files)
- **What's wrong:** The "domain" package mixes pure domain models, HTTP transport (`AurralApiClient`, `BinderyApiClient`), wire DTOs (`AurralDtos.kt` ~348 lines, `BinderyModels.kt`), serialization config (`AurralSerialization.kt`, `BinderySerialization.kt`), URL policies (`AurralUrlPolicy.kt`, `BinderyUrlPolicy.kt`), repositories, and a background worker (`AurralConfirmationQueueManager.kt`). `data/` exists but holds only Room entities/DAOs. The Aurral subsystem alone is ~4,400 lines across 16 files in `domain/repositories/`.
- **Impact:** The classic "domain = pure, data = transport" contract is inverted. Anything in `domain/` is implicitly reachable from anything else, so nothing enforces that UI never depends on Ktor/DTOs. A newcomer cannot infer layering from package names.
- **Direction:** Move API clients + DTOs + serialization + URL policies into `data/remote/`; keep only repository interfaces and domain models in `domain/`. Optionally split a `:domain` module that has no Ktor dependency to make the boundary compiler-enforced.

## A2. `DatabaseModule` leaks platform knowledge; the "shitty workaround" TODO is structural

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/di/DatabaseModule.kt:5-7`; DB construction in `androidMain/.../di/PlatformModule.android.kt:17-37` and `iosMain/.../di/PlatformModule.ios.kt:21-40`
- **What's wrong:** `databaseModule` (common) declares all DAO `single{}`s but cannot declare the `CacheDatabase`/`DownloadDatabase` themselves because they need a platform Context/path, so those are shoved into `platformModule` with the comment `// TODO: find a less shitty workaround for that^`. The workaround has lived long enough to ship in the v1.0.11 release prep.
- **Impact:** `DatabaseModule.kt` shows a dangling `get<CacheDatabase>()` with no definition in the same module — confusing. The split also makes it impossible to add DB-level config (e.g. a shared migration callback) in one place.
- **Direction:** Move the entire database graph into the per-platform modules, or expose an `expect fun buildDatabase(path): CacheDatabase` / `actual` and keep the DAOs in common.

## A3. No use-case / interactor layer; ViewModels get 7-15 collaborators directly

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/di/ViewModelModule.kt`; `SongListViewModel` takes 7 deps, `NowPlayingViewModel` 7, `AndroidMediaPlayerViewModel` 15 (see `PlatformModule.android.kt:64-83`)
- **What's wrong:** Koin hands each ViewModel a grab-bag of repositories (sometimes 3) plus 2-4 managers. There are zero use-case classes anywhere. Business rules ("should this download be rejected", "how to assemble the now-playing queue") are inlined into ViewModels or into managers that double as global state.
- **Impact:** Tolerable at small scale, but the 15-dependency `AndroidMediaPlayerViewModel` is the smell this produces. Logic can't be re-used across screens without duplicating it into a manager, which is why so much logic ends up in God-managers (see A9).
- **Direction:** Introduce a thin `usecase/` package for cross-repository flows (queue assembly, download orchestration, sync trigger) so ViewModels depend on 1-2 use-cases rather than 7 raw collaborators.

## A4. `createdAtStart = true` on background workers runs during Koin init

- **Severity:** Low
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/di/ManagerModule.kt:17-19` (`SyncManager`), `:23-25` (`DownloadQueueNotificationCoordinator`), `:32` (`AppLogManager`); combined with `koin.createEagerInstances()` in `KoinInit.kt:17`
- **What's wrong:** Three singletons are eagerly constructed and started at `startKoin` time. `SyncManager.startPeriodicSync()` immediately launches a coroutine that will pull the entire library if `albumDao.getAlbumCount() == 0` (`SyncManager.kt:67-72`). Sync work begins before the first Composable or any login-gate is reached.
- **Impact:** On first launch the app kicks off a full DB sync before the UI exists. The eager start couples init ordering to graph construction — fragile if a future manager needs `SessionManager.isLoggedIn` to be true first.
- **Direction:** Start background workers explicitly from `App.kt` after the login-gate resolves, not via `createdAtStart`.

## A5. Global state is fragmented across 6+ singletons, then re-wired via CompositionLocals

- **Severity:** Medium
- **Location:** `App.kt:148-163` (6 `CompositionLocal`s); owners: `SessionManager.isLoggedIn` (`SessionManager.kt:24`), `PreferenceManager`, `SnackBarManager.events`, `DownloadManager._downloadedSongs` (`DownloadManager.kt:94`), `MediaPlayerViewModel._uiState` (`MediaPlayer.kt:40`), `SyncManager.syncState` (`SyncManager.kt:50`)
- **What's wrong:** There is no single "state owner" story. Each long-lived Koin singleton holds its own `MutableStateFlow` as private mutable state and exposes it publicly; `App.kt` threads these (plus a hand-rolled `BottomBarScrollManager`, plus `LocalArtistPhotoEntries`) through 6 `staticCompositionLocalOf`s. State can mutate from any manager at any time with no central consistency point. `MediaPlayerViewModel` is *also* a Koin singleton, so its `_uiState` is effectively a global mutable singleton — not scoped to any screen.
- **Impact:** Reasoning about "what's the source of truth for X" requires hunting across managers.
- **Direction:** Pick one model — a small number of top-level store/state-holder classes composed in `App.kt`, or move shared state into the ViewModel layer with explicit data dependencies. Drop `CompositionLocal` as a state-propagation mechanism except for genuinely ambient UI concerns (theme, density).

## A6. `Screen` sealed interface is a 50+-destination flat enum coupled to duplicate `when`-tables

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/Screen.kt`; exhaustively matched in `NavBackPolicy.kt:21-104` and `NavBackPolicy.kt:82-137` (`areaRootDestinationFor`) and again in `BottomBarProfilePolicy.kt:78-119`
- **What's wrong:** Adding any destination requires touching `Screen.kt`, two `when` blocks in `NavBackPolicy.kt`, and one-or-two in `BottomBarProfilePolicy.kt`. The two `NavBackPolicy.kt` functions (`visibleRootBackDestinationFor` and `areaRootDestinationFor`) duplicate large overlapping screen lists with subtly different fallback targets — easy to diverge.
- **Impact:** The nav model scales linearly worse with screen count; duplicate when-tables are a latent bug source. No compiler forcing function because they use `else`.
- **Direction:** Attach back-fallback/profile metadata to each `Screen` instance (data on the sealed member) so policy is computed, or generate the when-tables. At minimum merge the two `NavBackPolicy` functions.

## A7. Custom scene strategies `UNCHECKED_CAST` against an unstable navigation3 beta API

- **Severity:** Medium
- **Location:** `ui/navigation/NowPlayingScene.kt:79` (`lastEntry.contentKey as T`), `BottomSheetScene.kt:46` (`entry.contentKey as T`); stringly-typed metadata keys `PROPERTIES_KEY`/`MAX_WIDTH_KEY`/`IS_TRANSPARENT_KEY` at `NowPlayingScene.kt:109-111`
- **What's wrong:** Both custom `SceneStrategy`s reach into `NavEntry.contentKey` and unchecked-cast it to `T`. `NowPlayingSceneStrategy` additionally passes three untyped `Map<String,Any>` string keys through entry metadata — a hand-rolled, stringly-typed contract. (The casts are safe by NavDisplay convention today — see C-Verified.)
- **Impact:** navigation3 is a beta; `NavEntry.contentKey` shape or the metadata API can change between betas and silently break the cast at runtime (no compile error — it's suppressed). The string-keyed metadata has no type safety.
- **Direction:** Define a typed metadata holder keyed via the library's `NavMetadataKey` (as `BottomSheetSceneStrategy.MetadataKey` already does). Pin the beta and add a smoke test that exercises both strategies.

## A8. `ModalBottomSheet` writes to Compose-M3 internals via `INVISIBLE_REFERENCE`

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/ui/components/sheets/ModalBottomSheet.kt:63-67`
- **What's wrong:** After delegating to `androidx.compose.material3.ModalBottomSheet`, the wrapper runs `LaunchedEffect(Unit) { sheetState.showMotionSpec = ...; sheetState.hideMotionSpec = ... }`, assigning to `internal`-visibility `MutableState` fields on `SheetState` that the suppress makes visible. This is the only way to apply a custom sheet animation, but it pins the build to a specific M3 internals layout.
- **Impact:** Any Compose update that renames or removes those fields compiles fine (invisible-reference) and silently no-ops, or breaks at runtime. Fragile coupling to undocumented internals; a silent regression risk on every Compose bump.
- **Direction:** Fork/wrap the M3 sheet properly or upstream a public API; remove the invisible-reference hack.

## A9. `AndroidMediaPlayerViewModel` is a ~1,310-line God object (transport, queue, effects, recovery, UI state)

- **Severity:** High
- **Location:** `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt` (1,310 lines, 15 constructor deps, ~60 functions)
- **What's wrong:** One class owns: MediaController connect/lifecycle (`connectToService`, `setupController` ~291), playback-state→MediaSession sync (`updatePlaybackState` ~519), replay-gain (`applyReplayGain` ~637), queue mutation (8 functions ~748-892), radio assembly (`startSongRadio` ~1017, `playRadio` ~1079), system-equalizer launch (`openSystemEqualizer` ~1289), artwork-cache observation (~229), audio-claim arbitration (~260), downloaded-file recovery (`recoverCurrentMediaItemFromDownloadedFile` ~585), and a progress loop (`startProgressLoop` ~700).
- **Impact:** Severe SRP violation; the single most change-prone file in the app. Behavior changes require coordinating a 1,300-line class with 15 collaborators. Pure policies around it are testable, but the ViewModel itself is expensive to instantiate and isolate. See C8, C10, and C11 for concrete correctness defects in this area.
- **Direction:** Extract a `QueueController`, `PlaybackStateSynchronizer`, `AudioEffectsController`, and `DownloadRecoveryPolicy` — each testable; the ViewModel becomes a thin coordinator.

## A10. `ReaderController` is a ~1,632-line state machine with ~15 co-located state data classes

- **Severity:** High
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt` (1,632 lines); ~15 state data classes in the same file (`ReaderSelection`, `ReaderSelectionActionState`, `ReaderSelectionNoteDraft`, `ReaderChapterProgressState`, `ReaderLoadedDocument`, `ReaderAnnotationPopupState`, `ReaderFootnotePopupState`, …) plus `ReaderControllerState` (~145) aggregating them
- **What's wrong:** One class + a cluster of co-evolving data classes own search, selection, annotations, footnotes, chapter progress, chrome, overlays, Whispersync (~692-754), and link interactions. The state classes are all in one file and the controller mutates `ReaderControllerState` piecemeal across hundreds of functions.
- **Impact:** The most complex subsystem in the app is concentrated in one file with no sub-component seams. Enormous bug surface; onboarding is effectively impossible without the author.
- **Direction:** Split into `ReaderSelectionController`, `ReaderAnnotationStore`, `ReaderProgressTracker`, `ReaderWhispersyncCoordinator` (which already has a file but is also driven from here). Move each state class next to its controller. See B1 for the reader-side detail.

## A11. `ReaderSettingsDialog` is a ~1,252-line monolithic Composable

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt` (1,252 lines; `KomikkuReaderSettingsDialog` alone ~568)
- **What's wrong:** A single Composable holds the entire reader-settings UI (typography, theme, pagination, margins, columns, fonts, direction, paper textures, Whispersync/listening) plus in-dialog session state. Supporting row composables (`SliderItem`, `CheckboxItem`, `SettingsChipRow`) are defined in the same file.
- **Impact:** Every reader-preference change means editing one giant file; preview-tooling and reuse of sections is blocked.
- **Direction:** Decompose into per-section sub-Composables (`TypographyPage`, `LayoutPage`, `ColorPage`, `ListeningPage`) in separate files, sharing only the small primitives.

## A12. `domain/models` contains ~90 `*Policy.kt` files — over-decomposition with no consistent meaning

- **Severity:** Low
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/models/*Policy.kt` (~90 files; 19 are ≤10 lines, e.g. a single 6-line `fun`)
- **What's wrong:** "Policy" is used as a suffix for free functions ranging from 4 to 811 lines, with no `interface` and no polymorphism — plain top-level functions split into 90 files. The label mixes pure UI math, business rules, and filtering logic.
- **Impact:** Extreme file fragmentation makes `domain/models` (159 files) impossible to navigate by intent; the "Policy" suffix carries no consistent meaning.
- **Direction:** Group related functions into cohesive files by feature; reserve "Policy" for genuine strategy abstractions, or drop the suffix.

## A13. Credential-bound background work has no explicit session lifetime

- **Severity:** Medium
- **Location:** `DownloadManager.kt:64` (`CoroutineScope(Dispatchers.IO + SupervisorJob())`), `SyncManager.kt:42` (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`), `AndroidAudiobookPlaybackManager.kt:14` (`MainScope()`), `MediaPlayer.android.kt:140` (`MainScope()`); plus `ConnectivityManager.android.kt:46`, `LogManager.android.kt:17`, `CoilBitmapLoader.kt:29`
- **What's wrong:** Every long-lived Koin singleton creates its own scope. `DownloadManager` has no `shutdown`/`cancel`/`close` (only job-level cancellation), while `SyncManager` only exposes `stopPeriodicSync()`. On logout, `SessionManager.logout` clears credentials and login state, but independently running sync/download work has no shared credential-session boundary.
- **Impact:** Credential-bound work can outlive the session that authorized it, and tests cannot substitute or deterministically close those lifetimes. Independent app-lifetime scopes are not themselves a leak; the defect is the missing distinction between application lifetime and authenticated-session lifetime.
- **Direction:** Provide an app-lifetime root scope in DI and a replaceable child `SessionCoroutineScope` for credential-bound work. Cancel and recreate only the session child on logout/login; do not cancel the application root scope on logout.

## A14. `Channel<DomainSong>(Channel.UNLIMITED)` for the download queue is unbounded memory risk

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/DownloadManager.kt:81`
- **What's wrong:** The song download queue is an unlimited buffered channel. "Download all" enqueues every `DomainSong` into memory immediately (workers drain only `downloadSchedulerWorkerCount()` at a time).
- **Impact:** `DomainSong` is a rich model; queueing tens of thousands on a large library is real memory pressure with no natural back-pressure.
- **Direction:** Bound the channel (with `BufferOverflow`) or drive the queue off the persisted `DownloadDao` rows rather than an in-memory channel.

## A15. `Semaphore(20)` and `dbChunkSize = 500 // should be enough` are unexplained magic numbers

- **Severity:** Low
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/DbRepository.kt:77-79`
- **What's wrong:** `concurrentRequestLimit = Semaphore(20)` gates concurrent DB/network fan-out and `dbChunkSize = 500 // should be enough` chunks inserts. Both are unexplained; 20 concurrent ops on a single SQLite connection can serialize/contend depending on the driver.
- **Impact:** Hard to tune; the comment signals the author wasn't sure.
- **Direction:** Make them `PreferenceManager`-backed or at least named constants with a rationale; verify 20 doesn't oversubscribe the DB connection pool.

## A16. Six independent `HttpClient{}` instances duplicate baseline network policy

- **Severity:** Medium
- **Location:** `AurralApiClient.kt:215`, `BinderyApiClient.kt:118`, `LastFmRepository.kt:128`, `MusicBrainzArtworkRepository.kt:69`, `LyricsRepository.kt:32`, `DownloadManager.kt:76` (bare `HttpClient()`, no plugins); plus `SessionManager` wrapping `SubsonicClient` separately (`SessionManager.kt:30`)
- **What's wrong:** Each remote data source constructs its own `HttpClient` and repeats parts of JSON, status, logging, and user-agent policy. Configuration differs by service, and `DownloadManager` uses a bare `HttpClient()`. Some differences are intentional because credentials and protocols differ; the defect is duplicated policy with no documented common baseline.
- **Impact:** Cross-cutting network behavior is difficult to audit and change consistently. A single shared client would be unsafe because service-specific authentication or plugins could leak across origins, so this is a maintainability concern rather than evidence of a current outage.
- **Direction:** Introduce shared `Json` and client-builder configuration plus a factory that creates isolated per-service clients. Keep authentication and origin-specific plugins local. Add explicit connection-state or heartbeat diagnostics where indefinite operations require observability; do not add cancellation timeouts or unconditional global retries.

## A17. `SessionManager.api` is a public-mutable `var` holding the Subsonic client, built outside any networking module

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/SessionManager.kt:30-57` (password in `Settings` at `:24,28,71`; `api` is a public mutable `var` at `:29`)
- **What's wrong:** The Subsonic `api` client is constructed inside a manager (not a network module), stored as a public `var api` (reassigned on login/refresh), and its custom headers are injected from `preferenceManager.serverRequestHeadersMap()` — the same customization the other 5 clients don't get.
- **Impact:** The session/auth story is the one place headers are customizable but is disconnected from the other 5 clients; `var api` being public-mutable is a data-race smell (read from `getCoverArtUrl` on any thread, reassigned on login).
- **Direction:** Move client construction into the networking module; expose auth via an interceptor applied to all clients. Make `api` an internal delegate with a thread-safe swap.

## A18. Three uncoordinated storage mechanisms (Settings, DataStore, Room) with overlapping responsibilities

- **Severity:** Medium
- **Location:** `multiplatform-settings` via `AppModule.kt:5` consumed by `PreferenceManager`, `SessionManager`, `LyricsRepository`, `AccountSheet.kt`, 2 VMs; **DataStore** via `PlayerStateRepository.kt:13` (only player UI state); **Room** `CacheDatabase` (v20, 15 entities) and `DownloadDatabase` (v4)
- **What's wrong:** Preferences are split across two key-value stores with no clear rule — `PreferenceManager` uses `multiplatform-settings`, but `PlayerStateRepository` (also preferences-shaped: one JSON blob) uses DataStore. No documented rationale for why player state deserves DataStore but every other pref uses Settings. The choice is invisible to the caller.
- **Impact:** Two preference stacks → two migration stories, two threading models (DataStore async/transactional vs Settings/SharedPreferences synchronous-ish), no guidance on which to pick.
- **Direction:** Standardize on one preferences mechanism (DataStore Preferences is the safer async choice) or document a hard rule for when each is used.

## A19. `DownloadEntity` is registered in BOTH databases — ambiguous ownership

- **Severity:** Medium
- **Location:** `CacheDatabase.kt:25,49` (includes `DownloadEntity` + exposes `downloadDao()`) and `DownloadDatabase.kt:9,15` (also `DownloadEntity` + `downloadDao()`); `DatabaseModule.kt:21` binds `get<CacheDatabase>().downloadDao()` while `:23` binds `get<DownloadDatabase>().lidaClipDownloadDao()`
- **What's wrong:** The same Room entity and DAO interface are members of two separate `@Database` declarations. `DownloadDatabase` *also* declares `downloadDao()`, but DI only binds the one from `CacheDatabase`. It's ambiguous which database actually owns downloads; two `@Database` builders listing the same entity risk two tables drifting.
- **Impact:** A reader can't tell which DB the download table lives in. If both builders create the table, there are two `downloads` SQLite files. Latent correctness hazard on top of design confusion.
- **Direction:** Pick one database for downloads (the aptly-named `DownloadDatabase`) and remove `DownloadEntity`/`downloadDao` from `CacheDatabase`.

## A20. Android-only scope note: iOS source sets are non-shipping

- **Classification:** Out of scope / non-finding
- **Location:** `LogManager.ios.kt:7` (`get() = TODO()`); `build.gradle.kts:154-185` (KT-84055 `TextFieldDecorator` shim that writes a Kotlin file to the build dir at compile time and deletes it after); `PlatformModule.ios.kt:69` (`NoOpAudiobookPlaybackManager()`); no `iosApp` Gradle module (only 34 iosMain actuals vs 179 androidMain actuals)
- **Observation:** There is no `iosApp` shell; audiobook playback is a no-op; `LogManager.ios.kt` contains `TODO()`; and the build carries a source-generation shim for KT-84055. These do not constitute a Navic product defect because iOS is intentionally unsupported.
- **Direction:** Preserve the Android-only product contract. Remove or simplify unused iOS targets only when they impose measurable Android build or maintenance cost; do not schedule iOS feature implementation.

## A21. Attribution / licensing governance is incomplete for an OSS app

- **Severity:** Medium (governance)
- **Location:** app `LICENSE` is **GPL v3** (`LICENSE:1-3`); vendored `androidMain/assets/reader/vendor/foliate-js/LICENSE` is **MIT** (John Factotum, 2022); pdfjs at `…/vendor/foliate-js/vendor/pdfjs/{pdf.js,pdf.worker.js}` (Apache-2.0); adaptation comments in `reader/FoliateEpubEngineAdapter.kt:3-5` reference `tmp/references/anx-reader/…` (a path that doesn't exist in the repo)
- **What's wrong:** The reader is adapted from "Anx Reader" and foliate-js; the only source-level attribution is two comments pointing at `tmp/references/…`. There's no top-level `NOTICES`/`THIRD_PARTY.md` aggregating foliate-js (MIT), pdfjs (Apache-2.0), and Anx Reader attributions — discoverable only by digging into `assets/reader/vendor/`. GPL-3.0 + MIT + Apache is compatible, but the bundling notice is incomplete (MIT requires the notice accompany "all copies").
- **Impact:** For an OSS app this is a governance/reputational risk, not a code bug. A downstream packager (F-Droid, etc.) needs accurate, aggregated notices; the current scattered story invites an attribution complaint.
- **License verification:** The exact referenced Anx Reader revision, `107f4fa74db0e7247c846c49d6211df3edf9887c`, is MIT-licensed (`Copyright (c) 2025 Anxcye`), not copyleft. Navic's top-level license is GNU GPL version 3, not AGPL. The earlier suggestion that Anx-specific copyleft obligations needed to be stated was incorrect; its MIT copyright and permission notice must instead be preserved.
- **Direction:** Add a top-level `THIRD_PARTY.md` listing foliate-js, PDF.js, and the pinned Anx Reader revision with exact licenses; surface them through the existing generated in-app `AcknowledgementsScreen` (`ui/screens/settings/AcknowledgementsScreen.kt`), and verify the generated resource inside the packaged APK.
- **Resolution:** Released in `v1.0.11-iota11`. `THIRD_PARTY.md` and exact upstream license files are present; AboutLibraries custom records add all three components to the existing Acknowledgements screen; a structural verifier checks both generated and packaged JSON; CI verifies the packaged notices before upload. The public APK independently passed the same check.

## A-Verified. Reader↔UI dependency is strictly one-way (positive)

- **Severity:** Info (verified correct)
- **Location:** `reader/` vs `ui/screens/reader/`
- **What's wrong:** Nothing. `git grep "import paige.navic.ui" -- composeApp/src/commonMain/kotlin/paige/navic/reader/` returns zero hits — `reader/` never reaches into `ui/`. `ui/screens/reader/ReaderScreen.kt` imports from `reader/` 32 times; `ReaderController.kt` imports only `domain.repositories.BinderyReadingProgress` and `util.core.Logger` — no Koin, no ViewModel. The "reader↔ui circularity" hypothesis is false at master.

---

# Part B — Reader/Bindery subsystem

## B1. Cross-reference: `ReaderController` God object is counted in A10

- **Classification:** Cross-reference / not counted separately
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt:234-1369` (1,632 lines incl. state models)
- **What's wrong:** See A10. From the reader perspective: the Whispersync methods alone (`onReadaloudPlaybackState` ~113 lines, `loadWhispersyncSidecar`, `repairWhispersyncMismatch`, `onVisibleTextRange`, `onTextPoint`) embed detailed logging and status derivation that belongs in a dedicated reducer; `ReaderControllerState` (~145) carries ~30 fields mixing chrome, search, selection, annotations, bookmarks, Whispersync, media overlay, and error state.
- **Direction:** Split into per-concern reducers (SearchReducer, SelectionReducer, AnnotationReducer, WhispersyncReducer) returning partial state patches composed by a thin orchestrator. The Whispersync block (~659-1016) is the clearest extraction candidate.

## B2. Coordinator↔Controller split is a mechanical forwarding wrapper, not a real boundary

- **Severity:** Low
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt:26-199`
- **What's wrong:** `ReaderCoordinator` is almost entirely 1-line forwarding methods (`fun search(query) = applyControllerStep(controller.search(query))`) plus `applyEngineCommand`. The only real logic is mapping `activeEngine`→adapter and folding engine commands into state. The name implies orchestration of multiple components, but it orchestrates exactly one controller + one adapter map.
- **Impact:** Doubles the API surface (every action exists on both classes), makes call sites noisier (`ReaderScreen.kt` calls `coordinator.*` everywhere), and obscures where state actually lives.
- **Direction:** Either fold adapter-dispatch into the controller (drop the wrapper) or give the coordinator genuine cross-component responsibility (progress persistence, playback dispatch) so the layer earns its name.

## B3. Engine swap (EPUB/PDF/CBZ…) is dispatch-only — no per-format capability negotiation

- **Severity:** Low
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt:52-117` and `ReaderCoordinator.kt:14-22`
- **What's wrong:** All six formats (`Epub`, `Pdf`, `Azw3`, `Mobi`, `Cbz`, `Fb2`) share one `FoliateWebViewEngineAdapter` that blindly translates every `ReaderEngineCommand` into the same JS bridge command. No capability check, so `Search` or `ApplyMediaOverlay` is dispatched to a PDF/CBZ engine even though foliate-js doesn't support search/overlay for those.
- **Impact:** Silent no-ops or JS errors when the UI offers overlay/search actions on unsupported formats; failures only surface via the generic `ReaderBridgeEvent.Error`.
- **Direction:** Add `capabilities: Set<ReaderEngineCapability>` per adapter and gate actions (e.g. hide Whispersync controls for CBZ) rather than relying on JS to ignore them.

## B4. JS→Kotlin bridge errors are silently swallowed (`runCatching{}.getOrNull()`)

- **Severity:** High
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt:550-668` (`decodeReaderBridgeEvent`)
- **What's wrong:** The entire event decode is `runCatching { ... }.getOrNull()`. If JS posts malformed JSON, a numeric field where a string is expected, or a structural change after a foliate-js update, the message is dropped with **no log and no error event**. The `error` case (line 660) only fires when foliate explicitly sends `{type:"error"}`, not when decoding itself fails.
- **Impact:** Reader appears to "freeze" (no relocation, no overlay updates) with zero diagnostics; impossible to debug field-shape regressions without a custom build. The single biggest diagnosability gap in the reader.
- **Direction:** On decode failure, log a safely truncated raw message and return a typed diagnostic result. Surface a user-facing error only when failures are persistent or prevent reader operation; a single malformed event should not create UI noise.
- **Resolution (2026-07-13):** Released in `v1.0.11-iota12`. `decodeReaderBridgeMessage` now distinguishes malformed JSON, non-object payloads, missing/unknown types, and invalid known payloads. Rejections carry a control-sanitized raw snapshot capped at 500 characters. A per-bridge processor warning-logs only the first rejection in a consecutive-failure episode, resets on any decoded event, and sends one existing `ReaderBridgeEvent.Error` with code `reader_bridge_protocol` after three consecutive rejections so the current reader error UI becomes visible without a timeout. Protocol, processor, Android bridge, adapter, and controller tests passed 124/124; the signed public APK passed workflow, hash, signature, packaged-governance, embedded-version, and ADB upgrade/startup validation.

## B5. Kotlin→JS commands are consumed before execution is acknowledged

- **Severity:** High
- **Location:** `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt:147-176` (`dispatchReadyReaderCommands`), `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWebCommandDispatch.kt:22-40`
- **What's wrong:** Commands are fire-and-forget `evaluateJavascript(..., null)`. The dispatch state machine records `publicationKey`/`lastCommandKey` before JavaScript confirms execution. That dispatch state survives `webViewGeneration` changes, so a recreated WebView can suppress both the publication-open command and the last locator command as already consumed. There is no acknowledgement channel from JavaScript confirming execution.
- **Impact:** After a render-process crash, the new WebView can remain blank or reopen without the saved locator because required commands are absent from its generation-specific execution history.
- **Direction:** Add per-command JS acknowledgement events (`{type:"commandAck", key}`), retain unacknowledged commands, and reset/replay publication plus last-known locator state whenever `webViewGeneration` changes. Drive retries from explicit readiness, acknowledgement, and generation transitions; do not use elapsed-time cancellation.
- **Resolution (2026-07-13):** Released in `v1.0.11-iota13`. Kotlin now assigns stable opaque command IDs and retains an acknowledgement-driven, one-command-in-flight ledger. JavaScript acknowledges a tracked command only after its handler settles and deduplicates a replayed ID. Duplicate ready events do not redispatch within a generation; renderer generation changes replay the same publication ID with the latest observed locator, and an acknowledgement unlocks the next queued command. No elapsed-time retry or cancellation was added. The focused Kotlin/Android owner suites passed 165/165, the Chromium acknowledgement runtime passed, and an ADB renderer kill retained the app process while reopening the same publication at the exact pre-kill href and range CFI. GitHub release workflow `29247528988` succeeded with iOS skipped; the public APK passed digest, signature, embedded-version, packaged-governance, and in-place upgrade/startup verification.

## B6. WebView bridge object held across destroy; potential leak window

- **Severity:** Medium
- **Location:** `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt:120-130` and `ReaderWebRuntime.kt:48-62`
- **What's wrong:** `ReaderJavascriptBridge` is created once via `remember { }` and captures `webView` indirectly (`onEvent` calls `webView?.post { ... }`). The `DisposableEffect` (~131) destroys the WebView on dispose, but the bridge outlives any given WebView instance (survives `webViewGeneration` bumps at ~251). After render-process death a new WebView reuses the same bridge whose lambda still references the nullable `webView` var — correct only because of the `?.post` null-check, but fragile.
- **Impact:** A stale WebView dispatching during teardown routes through a lambda that may post to a destroyed view; subtle use-after-destroy risk. The retained bridge also prevents GC of the host composable's captured references.
- **Direction:** Tie the bridge lifetime to a specific WebView generation (recreate bridge inside the `key(webViewGeneration)` block); `removeJavascriptInterface` in `DisposableEffect` before destroy.
- **Resolution (2026-07-13):** Released in `v1.0.11-iota14`. Each keyed WebView generation now owns one `ReaderJavascriptBridge`, an atomic reference to its exact view, and an idempotent disposed gate. Retired bridges are deactivated, `NavicAndroidBridge` is removed, references are cleared, and only then is that generation's view destroyed. Already-posted callbacks execute only when their generation and view still match the live host; the previous no-view fallback is gone. Both Compose disposal and renderer loss use the same teardown path. The focused owner suites passed 168/168, all JavaScript/governance/assembly gates passed, and an ADB renderer kill retained the app while restoring the exact pre-kill publication locator with no AndroidRuntime fatal. GitHub release workflow `29249483437` succeeded with all iOS jobs skipped; the public APK matched its published digest, passed v2 signature, embedded `541/iota14`, vendor/attribution, and in-place `iota13` upgrade/startup verification.

## B7. Vendored foliate-js + pdfjs have no version/commit pin or update strategy

- **Severity:** Medium (security + maintainability)
- **Location:** `composeApp/src/androidMain/assets/reader/vendor/foliate-js/package.json` (`"version": "1.0.1"`, upstream git URL, no commit hash), `…/vendor/pdfjs/pdf.js`/`pdf.worker.js` (no version file), `runtime.json`
- **What's wrong:** Vendored foliate-js records only a version string, no commit hash; pdfjs has no version file at all. No tooling/CI checks for upstream CVEs. This JS runs with full DOM access inside the reader WebView and parses untrusted book content.
- **Impact:** Impossible to audit which upstream commit is shipped; security reviews can't trace pdfjs (historically CVE-rich) to a known version; updates are manual and unverifiable.
- **Direction:** Record `foliate-js@<commit-sha>` and `pdfjs@<version>` in a `vendor/VERSIONS.txt` or `runtime.json`; add a CI note flagging pinned versions; periodically diff against upstream.
- **Resolution (2026-07-13):** Released in `v1.0.11-iota11`. The packaged `reader/vendor/manifest.json` pins Foliate `1.0.1` to source commit `f52d42c6127d0ad981a2c67634113541b17ae01e` and PDF.js `3.11.174` to release commit `ce87167432819f85df49b6b16c7a78556e9a4ee0`, npm package Git head `f287f540ed3ed393e137c9ff7a2e98f6e73ea527`, and both npm SHA-512 integrity values. It assigns all 30 files to components and records exact SHA-256 hashes. The review also records `GHSA-wgrm-67xf-hhpq` / `CVE-2024-4367`; Navic's only PDF.js load explicitly disables eval support as prescribed by the advisory, and a host guard now requires that mitigation. Deterministic PowerShell tooling rejects invalid metadata, path escapes, wrong ownership, missing/extra files, and modified bytes in both the source tree and final APK. GitHub Actions runs the verifier self-test before Android builds and verifies packaged assets before upload; `docs/architecture/reader-vendor-assets.md` defines the upstream diff, security review, local patch, test, and release process.

## B8. `LOAD_NO_CACHE` + `clearCache(true)` on every `configure()` is wasteful

- **Severity:** Low
- **Location:** `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt:30-46`
- **What's wrong:** Hardening is mostly correct (`allowFileAccess=false`, `allowContentAccess=false`, JS enabled only for the asset-loader origin, debugging gated behind `BuildConfig`). But `cacheMode = LOAD_NO_CACHE` plus `clearCache(true)` on every `configure()` forces foliate-js (~hundreds of KB) and pdfjs to be re-parsed on every WebView recreation (including every render-process death recovery). Assets are served via `WebViewAssetLoader` (local), so there's no cache-coherence risk to justify disabling cache.
- **Impact:** Slower cold-start and slower crash-recovery; wasted work.
- **Direction:** Use `LOAD_DEFAULT` (assets are immutable local files); drop `clearCache(true)` from `configure`.

## B9. `AudioPlaybackArbitrator` is a fire-and-forget `SharedFlow` with no arbitration logic and a race window

- **Severity:** High
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/AudioPlaybackArbitrator.kt:1-15`; policy at `domain/models/AudioPlaybackOwnershipPolicy.kt:1-11`; consumers `shared/AndroidAudiobookPlaybackManager.kt:33-49` and `shared/AndroidMediaPlayerViewModel.android.kt`
- **What's wrong:** The "arbitrator" is literally `MutableSharedFlow(extraBufferCapacity=8)` + `tryEmit`. No replay (`replay=0`), no current-owner state, no central decision. Each side independently `collectLatest { shouldPauseForAudioPlaybackClaim(...) }`.
  1. **Lost-claim race:** because `replay=0`, any `claim()` emitted before a collector is subscribed (e.g. audiobook manager init ordering) is silently dropped.
  2. **No mutual exclusion:** the policy only says "pause if a *different* owner claimed and I'm playing." Both services can be `playWhenReady=true` simultaneously if their claims interleave; ExoPlayer audio-focus is the actual backstop, not the arbitrator.
- **Impact:** Audiobook and music can briefly play simultaneously, or one side never learns it should pause. The "arbitrator" provides a false sense of centralized control.
- **Direction:** Introduce a serialized `claim/release` API that changes ownership atomically, then expose the resulting owner as `StateFlow<AudioPlaybackOwner?>` for replayable observation. A `StateFlow` alone is not mutual exclusion. Add tests for initialization order, simultaneous claims, release, and handoff.

## B10. Two `MediaSessionService`s both `exported="true"` — external controllers can drive either

- **Severity:** Medium
- **Location:** `androidApp/src/main/AndroidManifest.xml:54-74` (`PlaybackService` and `ReadaloudPlaybackService`); `ReadaloudPlaybackService.android.kt:1-131`
- **What's wrong:** Both services are `android:exported="true"` with `android:permission="${applicationId}.permission.PLAYBACK_SERVICE"` (signature-level — protects against random apps). But Android Auto / Wear OS / Assistant can connect to the *readaloud* session and trigger playback independent of the reader UI; since the two services don't share a player, an Assistant-initiated readaloud won't be coordinated with the reader's Whispersync overlay state. Each service builds its own `ExoPlayer`, so two independent media notifications can appear.
- **Impact:** Confusing UX (two media notifications, Assistant may start the wrong one); no single source of truth for "what's playing."
- **Direction:** Unify on one `MediaSessionService` with a virtual player that switches music↔readaloud, or make the readaloud session unexported and drive it only in-app.

## B11. SMIL/media-overlay pipeline fully materializes the entire EPUB into memory (twice)

- **Severity:** Medium (stability)
- **Location:** `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerMediaOverlayParser.android.kt:80-96` (`epubEntries`) and `StorytellerReadaloudAudioCache.android.kt:16-32` (second `epubEntries` copy); `StorytellerReadaloudRuntimeLoader.android.kt:20-35`
- **What's wrong:** `epubEntries()` reads every zip entry into a `Map<String, ByteArray>` via `ByteArrayOutputStream`. For a readaloud EPUB this includes all audio (often 100s of MB). The runtime loader `readBytes()` the whole EPUB, parses once, then the audio cache reads it **again** into a second full in-memory map and writes the EPUB + every audio file back to disk. Peak memory ≈ 2× EPUB size.
- **Impact:** OOM risk on large audiobook EPUBs (200-500MB); double disk write; slow first-open.
- **Direction:** Stream the zip and extract only OPF + SMIL + referenced audio lazily; share one parsed-entries pass between parser and audio cache; write audio files individually during the single zip walk.

## B12. Two parallel, partially-duplicated overlay sync state machines (Whispersync vs Readaloud/MediaOverlay)

- **Severity:** Medium
- **Location:** `ReaderWhispersyncSyncCoordinator.kt:1-455` (Whispersync path) vs `ReaderReadaloudSyncCoordinator.kt` + `ReaderMediaOverlaySync.kt:1-111` (Readaloud path)
- **What's wrong:** Two independent overlay-sync implementations — Whispersync (driven by `WhispersyncTimeline`/`WhispersyncSegment`, when a sidecar alignment artifact exists) and Readaloud/MediaOverlay (driven by `MediaOverlayTimeline`/`MediaOverlayClip`, from in-EPUB SMIL). Near-identical shape (`activeSegmentKey`/`activeClipKey`, `withEngineCommand`, `clearOverlayIfNeeded`, sync toggle) but separate types, separate active-key computations, separate status enums. Only the Whispersync path is wired through `ReaderController`; the readaloud coordinators appear largely unused from the controller.
- **Impact:** Duplicated logic drifts; two notions of "which overlay is active" that could disagree; bugs from editing one path and not the other.
- **Direction:** Unify behind one `OverlaySync` abstraction parameterized by segment/clip source, or explicitly delete the readaloud-sync path if Whispersync superseded it (the controller never calls `ReaderReadaloudSyncState`).

## B13. Progress conflict resolution is "most advanced wins" with no timestamp guard

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt:91-108` (`bestReaderStartLocator`)
- **What's wrong:** Remote-vs-local start-locator resolution compares only `progressFraction` deltas (`localProgress > remoteProgress + ReaderProgressAdvanceThreshold`). No `updatedAt` comparison. If device A reads ahead to 80% and device B is at 20% *with a newer timestamp* (B just opened the book), opening on B still picks A's 80% locator because it's "more advanced." `BinderyReadingProgress.updatedAt` exists but is only used for the copy, not for arbitration.
- **Impact:** Progress can jump backwards/forwards unexpectedly across devices; "furthest read" is privileged over "most recent," wrong for re-reading.
- **Direction:** Make the policy explicit and timestamp-aware (last-writer-wins, or a user-visible "sync conflict" prompt when local and remote diverge beyond a threshold in opposite directions).

## B14. Progress-save gate relies on a brittle freeform string `reason` allowlist

- **Severity:** Medium
- **Location:** `ReaderProgressSync.kt:131-178` (`ReaderProgressSaveGate.onReaderEvent`); `ReaderController.kt:1459-1473` (`readerExplicitReadableRelocationDismissesNativeShellCover`)
- **What's wrong:** The gate that prevents overwriting saved progress with a startup-placeholder relocation matches on freeform `locator.reason` strings: `"relocate-committed"`, `"initial-resume"`, `"pagination-profile-"` prefixes, `"media-overlay-follow"`. These originate in vendored JS (`ReaderBridgeProtocol.kt:578` just reads `reason` from JSON) with no shared constant or contract test.
- **Impact:** If foliate-js renames or adds a reason, the gate silently misbehaves — either saving a placeholder as real progress, or skipping a legitimate relocation. Subtle progress-regression bugs tied to JS string literals the Kotlin side doesn't own.
- **Direction:** Define the reason vocabulary as a Kotlin enum and have the JS bridge emit only those values (validated on decode); drop unknown reasons defensively.

## B15. Reader transient state is not retained across process recreation

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt:64-130`
- **What's wrong:** `ReaderScreen` holds `ReaderCoordinator`, search/selection state, `whispersyncPlaybackPlan`, listening settings, and commands in `remember` state. Composition recreation is handled, but process recreation rebuilds this transient state. `rememberCoroutineScope()` is correctly composition-scoped; work launched there is cancelled when the screen leaves composition.
- **Impact:** Search results, draft annotations, dialog state, and an in-memory Whispersync plan can be lost after process death. Publication identity and reading progress are persisted through other paths, so this is primarily recovery UX rather than silent loss of the book or durable progress.
- **Direction:** Retain only state whose recovery has user value: publication identity and last durable locator belong in persistent storage; small transient UI state can use `SavedStateHandle`; large derived coordinator state should be reconstructed. A `ReaderViewModel` is one possible owner, not a requirement by itself.

## B16. Komikku (manga viewer) port forms the entire reader input/nav layer — implicit seam

- **Severity:** Low
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/KomikkuViewerNavigation.kt:1-7` (provenance header), `ReaderViewer.kt:1-8`, `androidMain/.../KomikkuReaderNativeFrameHost.android.kt`
- **What's wrong:** Tap-zone/region-mapping/viewer-mode logic (`KomikkuNavigationRegion`, `readerViewerActionFor`, `WebtoonPublicationReaderViewer`) is ported from Komikku/Tachiyomi (manga) and adapted to drive a WebView-based EPUB/PDF reader. "Webtoon/scrolled" and "paged vertical" modes and inversion modes (`KomikkuTappingInvertMode`) are manga concepts stretched onto EPUB. The seam between "Komikku native frame" (Android `View` owning gestures + page-turn bitmap) and "foliate WebView content" is implicit and lives entirely in platform-specific files.
- **Impact:** Behavior surprises (manga-style tap regions on reflowable EPUB); hard to reason about without knowing both codebases; `WebtoonPublicationReaderViewer.viewerActionFor` (`ReaderViewer.kt:159-166`) is the only place scroll-mode diverges — a subtle special case.
- **Direction:** Document the mapping explicitly; consider whether the manga viewer-mode taxonomy is meaningful for reflowable EPUB, or whether it should collapse to paged/scrolled.

## B17. Bindery API key stored in plaintext prefs and sent as `X-Api-Key` on every request

- **Severity:** Medium (security, architectural root cause)
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyUrlPolicy.kt:5-9` (`binderyApiKeyHeaders` → `X-Api-Key`), consumed in `BinderyApiClient.kt` (every method), `ReadaloudPlaybackService.android.kt:54-55`, `ReaderPublicationRuntimeHost.android.kt`
- **What's wrong:** The key is read from `preferenceManager.binderyApiKey` (plain prefs) and attached as `X-Api-Key` on all OPDS/API/resource/audio requests, including baked into the readaloud ExoPlayer's `DefaultHttpDataSource.Factory` default request properties (every audio segment fetch). No `Authorization` scheme, no rotation, no `EncryptedSharedPreferences` on Android.
- **Impact:** Key recoverable from a backup or rooted device; broad exposure surface (every byte request carries it).
- **Direction:** Store via `EncryptedSharedPreferences` on Android; prefer a standard auth scheme; scope the header only to the Bindery origin (note `binderyRequestHeadersForUrl` at `BinderyUrlPolicy.kt:11-21` already origin-scopes *image* requests — extend that discipline to audio/data).
- **Resolution (2026-07-13):** Released in `v1.0.11-iota11`. Android now encrypts the key with AES-GCM backed by a non-exportable Android Keystore key; the first release retains a read-only legacy migration path and clears plaintext only after encrypted commit plus decrypt-readback succeed. Ktor API/resource requests filter `X-Api-Key` against the canonical configured origin and do not auto-follow authenticated redirects. Readaloud audio and notification artwork resolve headers per final request URI instead of installing a service-global key. `EncryptedSharedPreferences` was not added because Android deprecated it in favor of direct Keystore/JCA use.

## B18. `fetchExternalText` is an SSRF / arbitrary-HTML-scraping surface with no allowlist

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyApiClient.kt:227-238` (`fetchExternalText`); `BinderyUrlPolicy.kt:53-167` (AudioBookBay cover regex/scraping)
- **What's wrong:** `fetchExternalText(url)` fetches any absolute `http(s)` URL with a hard-coded `User-Agent: Navic/1.0 provider-cover-resolver` and returns raw HTML, which is then regex-mined for cover images (`AudioBookBayMetaImageRegexes`, `AudioBookBayImageSrcRegex`). No host allowlist — any `sourceUrl` from Bindery provider data is fetched. A malicious/compromised Bindery server can point the client at arbitrary internal URLs (metadata SSRF).
- **Impact:** Internal-network fetches driven by server-controlled data; regex-HTML parsing is fragile and a classic injection vector.
- **Direction:** Allowlist provider hosts; reject private/loopback IP ranges; parse HTML with a real parser, not regex.
- **Resolution (2026-07-13):** Released in `v1.0.11-iota11`. `fetchExternalText` now requires an explicit `AudioBookBayProviderCover` purpose and accepts only exact `https://audiobookbay.lu:443` URLs without credentials or fragments. Android performs the provider request through an isolated Ktor/OkHttp client whose DNS adapter rejects empty, mixed, private, loopback, link-local, carrier-grade NAT, multicast, unique-local, site-local, and IPv4-mapped private answers while returning the same validated address objects OkHttp connects to. Ktor and both OkHttp redirect modes are disabled. Ksoup replaces regex extraction, and cover candidates are limited to credential-free HTTPS URLs on `image.bayimg.com` or `audiobookbay.lu`.

## B19. Bindery metadata cache is stale-after-6h with no invalidation on mutation except `clearBaseUrl`

- **Severity:** Medium
- **Location:** `BinderyMetadataCache.kt:14` (`BINDERY_METADATA_CACHE_FRESH_MILLIS = 6h`), `BinderyRepository.kt:466-545` (`withConfiguredCachedPayload`), `performAction` at `BinderyRepository.kt:455-465`
- **What's wrong:** Cache returns any payload <6h old as fresh without revalidation. Only `performAction` calls `metadataCache.clearBaseUrl(baseUrl)` — and it clears **all** payload types for that base URL. No per-path/per-payload-type invalidation; mutations elsewhere (e.g. `putReadingProgress`) don't invalidate `BookSync`/catalog caches. The cache key includes path but not API key, so on key change (different permissions) stale higher-privilege payloads remain visible.
- **Impact:** Stale catalog/manifest after server-side changes that don't go through a local `performAction`; potential permission boundary leak across key changes; "ghost" findings/collections.
- **Direction:** Include an API-key fingerprint in the cache key; add targeted invalidation (progress PUT invalidates the book's sync cache); expose pull-to-refresh forcing `fullRefresh=true` (the VM already supports it).

## B20. Hard-coded English UI strings in Bindery screens and reader Whispersync labels (i18n)

- **Severity:** Low
- **Location:** `BinderyHubScreen.kt:623` (`Text("Ebook only")`), `:668` (`Text("Open")`), `:677` (`Text("Cancel")`); also `BinderyBookScreen.kt` (4 sites), `BinderyAudiobookDetailScreen.kt` (2). In commonMain reader: `ReaderController.kt:498` (`"Syncing audiobook"`), `:528` / `ReaderWhispersyncSyncCoordinator.kt:243,276,310` (`"Whispersync paused"`, `"No synced text here"`, `"End of visible page"`), `ReaderWhispersyncPlaybackPolicy.kt:21,33,36`
- **What's wrong:** The app has a string-resource system (other bindery screens use `stringResource`), but user-visible labels are hard-coded here — especially the Whispersync status labels, the most user-facing readaloud UI.
- **Impact:** No translation possible for these strings; inconsistent with the rest of the app (which has 18 locale variants).
- **Direction:** Extract all listed literals to `Res.string.*` resources.

## B21. Android-only scope note: the iOS reader stub is non-shipping

- **Classification:** Out of scope / non-finding
- **Location:** `composeApp/src/iosMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.ios.kt:1-40`
- **Observation:** The reader engine exposes common contracts while the iOS host is a no-op. This does not represent a supported-platform regression because Navic is Android-only.
- **Direction:** Keep common pure reader logic where it improves testability and Android organization. Move contracts to `androidMain` only if maintaining unused actuals creates measurable cost; do not implement an iOS reader.

## B22. Reader publication/font cache lives under `cacheDir` — OS eviction mid-session

- **Severity:** Medium
- **Location:** `ReaderPublicationResource.android.kt:21-24` (`readerPublicationCacheRoot = File(context.cacheDir, "reader")`), `ReaderImportedFontCache.android.kt`, `StorytellerReadaloudAudioCache.android.kt`
- **What's wrong:** All resolved EPUBs, extracted audio, imported fonts, and remote-font packages are written under `context.cacheDir/reader/...`. Android may evict cache files under storage pressure at any time. Code keys off `publicationFile.isFile && length() > 0` to reuse cached publications but doesn't re-fetch if the file vanishes between open and a resource request; the WebView's `InternalStoragePathHandler` then 404s the asset. Imported fonts can similarly disappear, and `ReaderImportedFontCache.listRemoteFonts()` reads metadata referencing `.ttf` siblings that may have been evicted.
- **Impact:** Rare-but-real "reader fails to render pages" or "imported font reverted" after low-storage pressure.
- **Direction:** For user-imported fonts (explicitly added by the user) use `filesDir`, not `cacheDir`; for publications, detect missing cache files and re-resolve; consider a self-managed size cap with LRU eviction instead of relying on OS behavior.
- **Resolution (2026-07-13 candidate):** Android reader assets now use `filesDir/reader`. Imported fonts are durable under `fonts/`; publications and Storyteller read-aloud assets use explicit session directories whose idempotent leases are retained across WebView renderer generations and released when the reader host is disposed. A synchronized process initializer migrates legacy fonts and removes stale legacy/managed session trees. Storage size/clear operations cover reconstructable sessions but intentionally exclude user-imported fonts. The `/reader-cache/` WebView URL contract is unchanged; no iOS reader, timeout, symlink, or LRU policy was added.
- **Validation:** The post-rebase JVM owner suite passed 184/184, and 10/10 exact storage UI/source assertions passed. Debug and reader-dev APKs assembled and passed all 30 packaged vendor checks plus attribution. On `emulator-5554`, a legacy font migrated with content intact, seeded stale sessions were removed, a live EPUB resolved under managed files storage, and killing only renderer PID `5452` retained app PID `5403`, created renderer PID `5628`, restored `OEBPS/Text/sinopsis.xhtml` at `epubcfi(/6/4!/4/2,,/4/1:586)`, and acknowledged `reader-open-1`. Leaving the reader removed the publication session while preserving the font; AndroidRuntime reported no fatal error.

## B23. `readerDev` build type forces WebView debugging for all WebViews for the app's lifetime

- **Severity:** Low
- **Location:** `androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt:34,75-122`
- **What's wrong:** `applyReaderDevIntentSeed` is correctly gated by `BuildConfig.NAVIC_READER_DEV` (line 76) — not a release risk. But when active, `readerWebContentsDebuggingEnabled` is force-set `true` unless the intent overrides (`:105-108`), and `ReaderWebRuntime.setForceWebContentsDebuggingEnabled(BuildConfig.NAVIC_READER_DEV)` (`:36`) forces the global WebView debugging flag for **all** WebViews in the process for the app's lifetime. A readerDev build left installed effectively exposes `chrome://inspect` bridge access to the reader JS.
- **Impact:** Acceptable for dev builds; any leak of a readerDev build to end-users exposes the bridge.
- **Direction:** Keep for dev, but ensure release packaging strictly excludes readerDev builds; document that readerDev implies insecure WebView.

## B24. Process-death recovery reopens the publication but loses transient reader state and in-flight commands

- **Severity:** Medium
- **Location:** `ReaderScreen.kt:64-130` (state in `remember`), `ReaderPublicationRuntimeHost.android.kt:38-118` (re-resolve on reopen), `ReaderEngineWebViewHost.android.kt:235-253` (renderer-death recovery)
- **What's wrong:** Combining B15 and B5: after process death the host re-resolves the publication and `bestReaderStartLocator` recovers a *start* position, but the active search session, selection, draft note, current dialog, Whispersync sidecar reload, and any commands the coordinator had queued but the WebView hadn't acked are all gone. The renderer-death path resets `commandDispatchState` and `readerRuntimeReady` but doesn't re-issue the open-command's start locator if the page already finished loading once.
- **Impact:** User returns to reader after being killed and finds search gone, half-written note gone, possibly wrong page if a navigation was in flight.
- **Direction:** Tie recovery to a ViewModel + `SavedStateHandle` (B15); persist selection-note drafts and last dialog to the state handle; re-issue start locator on renderer recovery.
- **Partial resolution (2026-07-13):** The renderer-generation portion shipped in `v1.0.11-iota13`: the dispatch ledger survives WebView renderer loss, rebuilds the publication command with the latest observed locator, reuses the stable command ID, and replays only unacknowledged state in deterministic order. On `emulator-5554`, killing renderer PID `2270` left app PID `2197` alive, created renderer PID `2509`, replayed `reader-open-1` at generation 1, restored `OEBPS/Text/capitancebolleta01.xhtml` with `epubcfi(/6/16!/4,/2[sigil_toc_id_4],/22/1:265)`, and received `commandAck(reader-open-1)` without an AndroidRuntime fatal. Process-death restoration for drafts, dialogs, selection, and reconstructed search state remains pending under B15/B24.

## B-Verified. Reader engine core is pure/immutable; XML parsing hardened; Bindery isolated; OPDS pagination correct (positive)

- **Severity:** Info (verified correct)
- **Location:** `reader/ReaderEngine.kt`, `ReaderController.kt`, `ReaderCoordinator.kt` (all `data class`es returning step values); `ReaderPublicationResource.android.kt:380-397` and `StorytellerMediaOverlayParser.android.kt` `parseXml` (XXE/secure-resolver hardening); `BinderyRepository.kt`/`BinderyApiClient.kt` (no `Subsonic` references); `BinderyUrlPolicy.kt` OPDS pagination
- **What's wrong:** Nothing. The command/event core is genuinely unidirectional and unit-testable; the EPUB/SMIL XML parsing disables external entity resolution and uses secure resolvers; the Bindery data model is walled off from the Subsonic music model (the only shared seam is `AudioPlaybackArbitrator` and UI types); OPDS `next`-link pagination is honored rather than page-incremented.
- **Direction:** None — preserve these properties during refactoring.

---

# Part C — Correctness & data safety

## C1. Missing Room upgrade paths trigger destructive database recreation

- **Severity:** Critical
- **Location:** `composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt:29` and `:40`
- **What's wrong:** Both database builders pass `fallbackToDestructiveMigration(true)`, and no `Migration` objects are registered. Room therefore drops and recreates a database whenever it cannot find a migration path. With the current zero-migration configuration, the next version bump has no supported upgrade path. Confirmed against `CacheDatabase.kt` (version 20, 15 entities) and `DownloadDatabase.kt` (version 4, 2 entities).
- **Impact:** On an upgrade without a registered migration path the user loses, irreversibly and silently:
  - From `CacheDatabase`: the entire music library cache (Album/Song/Artist/Genre/Radio), all playlists + `PlaylistSongCrossRef` ordering, all saved lyrics (`LyricEntity`), the offline sync-action queue (`SyncActionEntity` — pending stars/ratings/scrobbles/deletes not yet flushed), `PlaybackOriginEntity` listening history/"most played" stats, `ArtistPhotoCacheEntity`, `AurralMetadataCacheEntity`, `BinderyMetadataCacheEntity`, `ArtworkColorEntity` (forces a full recolor pass).
  - The active `DownloadEntity` registry is bound from `CacheDatabase`; `DownloadDatabase` redundantly declares the same entity and also stores `LidaClipDownloadEntity` (see A19). Destructive recreation can therefore invalidate both music and clip download registries.
  - The on-disk audio files themselves survive (`StorageManager` writes outside Room), but without `DownloadEntity` rows the app can no longer map a `songId` to its local file path, so `getDownloadedFilePath()` returns null and offline playback breaks until the user re-downloads.
  - The pending `SyncActionEntity` queue loss is the worst part: starred/rated/scrobbled items queued while offline are silently discarded, so the server never learns about them.
- **Direction:** Register and test explicit upgrade migrations before changing either schema version. If destructive downgrade behavior is intentionally accepted, configure it separately and document the data consequence; a downgrade is not inherently safe for pending sync or download state. At minimum, keep `SyncActionEntity` and the active `DownloadEntity` registry outside any cache-only destructive policy.

## C2. Null `artistId` silently collapsed to `"unknown artist"` sentinel string

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/data/database/mappers/AlbumMappers.kt:16` and `SongMappers.kt:20` (both carry `// TODO: figure out why this can be null and how to handle it`)
- **What's wrong:** `ApiAlbum.toEntity` and `ApiSong.toEntity` do `this.artistId ?: "unknown artist"`. This sentinel is persisted as `artistId` in `AlbumEntity`/`SongEntity`. The TODO is still open. The fallback fires whenever the Subsonic source omits `artistId` (compilations, album-artist-only entries, podcast-style entries, certain Navidrome responses).
- **Impact:** Two correctness consequences: (1) all such items collapse onto one fake artist `"unknown artist"`, so artist-grouped views, "more by this artist", and sync-by-artist dedupe treat unrelated tracks as the same artist; (2) `albumSyncSongArtistOverrides` (`DbRepository.kt:34-46`) spreads that sentinel back into song rows during sync, so the bad value propagates and survives across syncs. Hit on every album/song sync batch — high frequency whenever affected items exist.
- **Direction:** Distinguish "absent" from "unknown" — make `artistId` nullable end-to-end (entity column + domain model + UI fallback), or use a real unknown marker (`null`) that the UI explicitly localizes, instead of a sentinel that collides with artist-name matching.

## C3. `DownloadManager.retryFailedDownload(s)` can race a fresh `cancelDownload`

- **Severity:** Medium
- **Location:** `DownloadManager.kt:265` (`retryFailedDownload`), `:235` (`retryFailedDownloads`), vs `:207` (`cancelDownload`)
- **What's wrong:** `retryFailedDownload(songId)` re-queues via `queueSongDownloads(listOf(song))` purely on the basis of a stale DB read (`getDownloadById` then `songDao.getSongsByIds`). It re-inserts a `QUEUED` row and calls `sendSongsToQueue` unconditionally. If the user calls `cancelDownload(songId)` between the retry's read and its `queueSongDownloads`, the retry resurrects a download the user just cancelled. `cancelDownload` removes `queuedSongIds` and cancels the `activeDownloads` job, but the retry path inserts a brand-new `QUEUED` entity after the cancel's DB delete and enqueues on the `Channel.UNLIMITED` queue. No re-check of intent.
- **Impact:** A download the user explicitly cancelled re-starts. Not data loss, but a correctness/UX violation and wasted bandwidth. Applies to `retryFailedDownloads` (bulk) too.
- **Direction:** Have `queueSongDownloads` re-validate cancellation intent (e.g. check a per-song "cancelled epoch" under `queuedSongIdsMutex`), or have `cancelDownload` set a durable tombstone the queue worker respects.

## C4. `SyncManager` connectivity collector TOCTOU on `syncMutex.isLocked`

- **Severity:** Medium
- **Location:** `SyncManager.kt:60-66`; same pattern at `enqueueAction` (`:106`)
- **What's wrong:**
  ```
  connectivityManager.isOnline.collect { isOnline ->
      if (!syncMutex.isLocked && isOnline) {
          syncMutex.withLock { processQueue() }
      }
  }
  ```
  Check-then-lock. If a `runSyncCycle` is mid-flight (holds the lock), the connectivity-online transition is silently skipped — queued pending actions won't flush until the next periodic cycle (up to 15 min). Two near-simultaneous online events can both observe `isLocked == false` and one blocks — not unsafe, just pointless.
- **Impact:** Offline→online star/queue actions may not flush for up to 15 minutes. No data loss (queue persists in `SyncActionEntity`), but the "sync on reconnect" contract is unreliable.
- **Direction:** Just `syncMutex.withLock { if (isOnline) processQueue() }` unconditionally — the mutex serializes correctly and you avoid the lost-wakeup.

## C5. `DbRepository.syncLibrarySongs` deletes "obsolete" rows after tolerated deserialization skips

- **Severity:** High
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/DbRepository.kt:194-200` (`deleteObsoleteAlbums`/`deleteObsoleteSongs`), `syncEverything` structure
- **What's wrong:** `syncLibrarySongs` accumulates `allValidAlbumIds`/`allValidSongIds` only from albums successfully fetched in this pass, then unconditionally calls `albumDao.deleteObsoleteAlbums(allValidAlbumIds)` and `songDao.deleteObsoleteSongs(allValidSongIds)`. Ordinary network exceptions are rethrown and abort before deletion, but `SerializationException` is deliberately logged and skipped. A malformed album is therefore omitted from the valid-ID sets and deleted as obsolete with all of its songs. Process death can still leave partial chunk inserts, but it does not reach the obsolete-delete step.
- **Impact:** A server payload that one client version cannot deserialize can silently evict otherwise cached/offline-addressable album metadata. The risk is recoverable after a compatible clean sync, but it is a real offline data-safety issue.
- **Direction:** Do not derive global obsolescence from successfully deserialized detail payloads. Prefer authoritative summary IDs for album existence and delete songs only for albums whose detail was successfully reconciled; alternatively skip all obsolete deletion whenever any detail payload was skipped.

## C6. `SyncActionEntity` processing has no retry/backoff and breaks the whole queue on one failure

- **Severity:** Medium
- **Location:** `SyncManager.kt:131-160` (`processQueue`)
- **What's wrong:** `for (action in actions) { try { … } catch (e) { break } }`. Any single failed action (e.g. a 404 on one stale star, or a transient 500) breaks the loop, leaving all subsequent actions unprocessed until the next sync cycle. No per-action retry, no skip-on-terminal-error, no ordering guarantee (`getPendingActions()` order is unspecified).
- **Impact:** A single bad item (e.g. star on a deleted song) blocks every queued action behind it indefinitely, since the same failing action is retried first every cycle. Silent failure of user intent (stars/scrobbles never reach server).
- **Direction:** Per-action try/catch with skip-on-terminal-status (4xx) and continue, retry-on-transient (5xx/network), and an attempt-count / dead-letter column on `SyncActionEntity` so poison messages don't block the queue forever.

## C7. `PlayerStateRepository` uses non-volatile double-checked locking outside DI ownership

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/PlayerStateRepository.kt:35-42` (DCL); Android DI binding in `PlatformModule.android.kt`
- **What's wrong:** `getInstance` uses double-checked locking with a plain, non-volatile `instance`. On Android/JVM the synchronized slow path is serialized, but the unsynchronized fast-path read lacks the publication guarantee required by classic DCL. Android DI already creates this repository as a Koin singleton, so the manual singleton mechanism is redundant and appears reachable only through direct calls outside the normal graph.
- **Impact:** The Android race is theoretical and low-reachability in the current graph, but retaining two ownership mechanisms invites future direct use and makes initialization harder to reason about.
- **Direction:** Make Koin the sole Android owner and delete the manual DCL API if no callers require it. If it must remain, use an Android-safe publication primitive. No iOS remediation is required for the Android-only product.

## C8. Continuous progress updates can suppress playback-state persistence indefinitely

- **Severity:** High
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/shared/MediaPlayer.kt:122-152` (`observeAndSaveState`)
- **What's wrong:** `_uiState.debounce(1.seconds).collect { … stateRepository.saveState(json) }` emits only after one second without another state change. `startProgressLoop` updates progress every 200ms while playback continues, so the flow may never become quiescent and no state is persisted for the entire continuous session. Queue mutations occurring during that period are folded into the same perpetually deferred snapshot.
- **Impact:** Process death during playback can restore an arbitrarily old position and queue, not merely a position one second behind. This can also contribute to changed Up Next ordering after recovery.
- **Direction:** Split durable structural state from high-frequency progress. Persist queue/current-item/shuffle mutations immediately and serialize them through one writer; sample progress periodically and flush the latest snapshot on explicit pause/stop and lifecycle stop. Reducing the debounce alone is not a reliable fix.

## C9. `currentCollection` and shuffle ordering not reliably reconstructable on restore

- **Severity:** Medium
- **Location:** `MediaPlayer.kt:104-117` (`restoreState` → `restoredPlayerStateForPreferences`), `AndroidMediaPlayerViewModel.android.kt:660-698` (`syncPlayerWithState`)
- **What's wrong:** `syncPlayerWithState` rebuilds the ExoPlayer queue from `state.queue` and re-applies `state.isShuffleEnabled`, but the actual shuffle order ExoPlayer had is not part of `PlayerUiState`. After restore, ExoPlayer recomputes a shuffle sequence that will differ from the pre-crash order. `currentCollection` is re-derived lazily via `refreshCurrentCollection` in `updatePlaybackState` (`AndroidMediaPlayerViewModel.android.kt:504`), not restored from persisted state, so the "now playing from album X" grouping can be lost.
- **Impact:** After crash/restart the user's up-next order changes (different shuffle) and the collection chrome may briefly mismatch. This is exactly what README line 221 admits ("does not yet preserve the original per-collection grouping after the app process is killed").
- **Direction:** Persist the resolved shuffle seed/order alongside the queue, or accept the limitation and surface it consistently.

## C10. `AndroidMediaPlayerViewModel.onCleared` launches cleanup in an already-cleared `viewModelScope`

- **Severity:** Medium
- **Location:** `AndroidMediaPlayerViewModel.android.kt:1264-1271`
  ```
  override fun onCleared() {
      viewModelScope.launch {
          playbackVolumeFader.cancel(controller)
          queueAutoFiller.cancel()
          super.onCleared()
          controllerFuture?.let { MediaController.releaseFuture(it) }
      }
  }
  ```
- **What's wrong:** AndroidX clears the ViewModel's closeable resources, including `viewModelScope`, before invoking the `onCleared()` callback. Launching cleanup into `viewModelScope` from that callback therefore schedules work in an already-cancelled scope; it may never start. `super.onCleared()` is not the cancellation trigger, so merely moving that call does not fix the defect.
- **Impact:** `playbackVolumeFader.cancel`, `queueAutoFiller.cancel`, and `MediaController.releaseFuture` may all be skipped, leaving cleanup to service teardown or garbage collection.
- **Direction:** Release `controllerFuture` synchronously in `onCleared`. Give collaborators non-suspending close operations where possible; otherwise register independently owned closeables/lifecycle resources whose cleanup does not depend on `viewModelScope` after clear.

## C11. `MediaController.Builder` future has no failure listener; bind failure crashes or leaves `controller == null`

- **Severity:** Medium
- **Location:** `AndroidMediaPlayerViewModel.android.kt:264-272`
  ```
  controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
  controllerFuture?.addListener({
      controller = controllerFuture?.get()
      setupController()
  }, MoreExecutors.directExecutor())
  ```
- **What's wrong:** Only a success-listener. If the `PlaybackService` bind fails (service crashed, permission denied, ANR), `buildAsync()`'s future completes exceptionally and `controllerFuture?.get()` throws `ExecutionException` on the main executor (crashes main thread) or the future never completes and `controller` stays null forever. No retry, no `ConnectionStateListener`, no reconnection if the service later unbinds.
- **Impact:** Either a crash on bind failure, or the player silently never connects (non-responsive UI, no errors). Stale controller after service teardown.
- **Direction:** Add a failure branch (`Futures.addCallback` with `onFailure`), register `MediaController` lifecycle callbacks to reconnect on disconnect, surface the failure via `SnackBarManager`.

## C12. Verified: `moveQueueItem` preserves the current item across forward and backward moves

- **Severity:** Info (verified correct)
- **Location:** `AndroidMediaPlayerViewModel.android.kt:869-890`
- **What's wrong:** Nothing in the index formula. For a forward move, an original current item in `(fromIndex, toIndex]` shifts left by one; for a backward move, an item in `[toIndex, fromIndex)` shifts right by one; and the moved current item follows `fromIndex -> toIndex`.
- **Direction:** Preserve the formula. Add exhaustive tuple tests over valid `(fromIndex, toIndex, currentIndex)` combinations so future queue refactors do not reintroduce a desynchronization.

## C13. `ArtworkColorDao` is write-only-forever — no TTL, no invalidation, no eviction

- **Severity:** Medium
- **Location:** `composeApp/src/commonMain/kotlin/paige/navic/data/database/dao/ArtworkColorDao.kt` (no delete/clear query); `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/ArtworkColorManager.kt`
- **What's wrong:** `ArtworkColorDao` exposes only `getColor` and `upsertColor`. No `clear`, no `delete`, no `WHERE updatedAt < …`. `ArtworkColorManager.putColor` upserts on every computation; `getColor` returns any prior row. If the underlying artwork changes server-side (new cover for the same `artworkKey`), the stale color is returned forever (until wiped by C1).
- **Impact:** Stale dominant-color theming after an artwork change. Cosmetic but persistent and user-visible (wrong tinted UI). Also unbounded growth over a DB version's lifetime.
- **Direction:** Add a TTL check (stored timestamp) or invalidate on artwork-url change; expose a `clear` for the logout flow.

## C14. Many `runCatching{}.getOrNull()/getOrDefault(...)` in repositories silently swallow errors

- **Severity:** Low
- **Location:** `AurralRepository.kt:96,125,144,163,199,227,253,278,…` (~25 sites); `BinderyRepository.kt:74,126,…`
- **What's wrong:** The Aurral/Bindery repositories wrap nearly every network call in `runCatching { … }.getOrNull() ?: return null` or `getOrDefault(emptyList())`. Intentional for optional integrations that may be down, but the error surface to the user is inconsistent: an integration being misconfigured (wrong base URL, expired token) presents as "empty list" rather than an error, with only `Logger.w` traces.
- **Impact:** Silent failure for optional integrations; user can't distinguish "no data" from "broken connection." No data loss.
- **Direction:** For optional integrations the trade-off is acceptable, but expose a connectivity/status signal (partially present via `markIntegrationServiceDown`, `AurralServiceStatus`) and surface it in the relevant screen's empty-state.

## C15. `lastFullSyncTime` (Settings) vs `SyncActionEntity` (Room) — two sources of sync state

- **Severity:** Low
- **Location:** `PreferenceManager` (`lastFullSyncTime`, via `BasePreferenceManager` `Settings`-backed `by preference`); `SyncActionEntity` in Room (`SyncActionDao`)
- **What's wrong:** Two sources of truth for sync state. `lastFullSyncTime` (Settings) gates whether `runSyncCycle` does a full pull (`SyncManager.kt:111`). If Settings is wiped (logout) but the Room sync queue survives, the next sync does a full pull (good) but processes stale queued actions (bad if the queue referenced server-state that changed). Conversely if Room is wiped (C1) but Settings survives, queued actions are lost and the user isn't told. Not a hard conflict, but the two stores can drift.
- **Direction:** Document the contract: Settings holds sync *scheduling* state, Room holds sync *work* state. On logout, clear both atomically.

## C-Verified. Coherent cache freshness; correct Ktor status pattern; benign Room-KMP suppress; clean playback-state split (positive)

- **Severity:** Info (verified correct)
- **Location:** `AurralRepository.kt:1131-1168` (`withMetadataCache`), `BinderyRepository.kt:403,503,566` (`isFresh`) — 6h freshness with stale-on-failure fallback + `Logger.w`; `AurralApiClient.kt`, `DownloadManager.kt:429-435` — `response.status.isSuccess()` / `200..299` checks + typed throws (no `expectSuccess`); `CacheDatabase.kt:77`, `DownloadDatabase.kt:22` — `@Suppress("KotlinNoActualForExpect")` is the standard Room3-KMP `@ConstructedBy` codegen contract (KSP synthesizes the actual), not a real iOS gap; `PlayerStateRepository` (DataStore) vs `PlaybackOriginEntity` (Room) — different purposes, no overlapping fact.
- **What's wrong:** Nothing. These are correct. Flagging so the picture is balanced and so future contributors don't "fix" them.
- **Direction:** None — preserve.

---

## Remediation candidates for impact prioritization

This audit does **not** prescribe implementation order. The next pass should rank confirmed findings using user-data impact, occurrence likelihood, affected surface, recovery cost, implementation risk, and the strength of available regression tests. The groups below prevent dependent fixes from being planned independently; their order is intentionally neutral.

### Persistence and data integrity

- **C1/A19:** Define database ownership, add explicit migrations, and protect pending sync and download registries from cache-only destructive policies.
- **C5:** Reconcile obsolescence from an authoritative complete set; never globally delete from a partial detail-deserialization result.
- **C6/C15:** Define durable sync-queue ordering, terminal failure handling, and logout consistency.
- **C8/C9:** Separate durable queue structure from sampled playback progress and persist enough shuffle state to reconstruct Up Next.

### Playback lifecycle and ownership

- **B9:** Add an atomic audio `claim/release` protocol, with `StateFlow` used only to observe the resulting owner.
- **C10/C11:** Make MediaController connection and cleanup explicit on both success and failure paths without relying on a cleared scope.
- **A13:** Introduce separate application and authenticated-session lifetimes; cancel only credential-bound children on logout.

### Reader reliability

- **B4/B5:** Add bounded diagnostic logging, explicit JavaScript acknowledgements, and WebView-generation replay. Retries are event-driven by readiness/ack/generation state, not timeouts.
- **B11:** Stream EPUB parsing and extraction instead of materializing the archive twice.
- **B13/B14:** Make progress conflict policy timestamp-aware and validate a shared relocation-reason vocabulary.
- **B15/B24:** Decide which reader state is durable, saved transient state, or safely reconstructed before selecting a ViewModel boundary.

### Structural maintainability

- **A9/A10:** Decompose the large playback and reader coordinators around behavior that is already independently testable.
- **A1/A5:** Clarify package and state ownership only where doing so removes an active dependency or testing obstacle.
- **A16/A17:** Share JSON and client-construction policy while retaining isolated per-origin clients and authentication.

### Explicitly excluded from prioritization

- **A20/B21:** iOS feature completeness. Navic is Android-only.
- **C12:** Queue-move index arithmetic. The current formula is verified correct.

---

## Appendix — Related prior work (complementary, not duplicated)

- `music-tab-lag-report.md` — root-cause forensic for the tab-switch lag regression (commit `4e3c069c`, three compounding effects). Music-module perf.
- `navic-performance-optimization-plan.md` — 11-task, 3-tier execution plan for recomposition/subscription hoisting (music module). Music-module perf.
- `navic-cache-refactor-plan.md` — 5-stage reactive-cache refactor (cold `flow{}` → `StateFlow` via `stateIn`). Music-module perf.
- `artwork-consolidation-spec.md` — approved (unimplemented) consolidation of the duplicated artwork source-selection layers. Music-module design.
- `docs/superpowers/reports/2026-06-27-performance-opportunity-audit.md` — Coil/Compose/DB efficiency audit (mostly fixed).
- `docs/superpowers/specs/2026-06-11-reader-stabilization-design.md` — reader WebView stabilization (pagination/texture/cover) design; overlaps B5/B6/B8 but focuses on renderer CSS, not bridge robustness.
- `docs/superpowers/specs/2026-06-18-whispersync-design.md` and `2026-06-22-whispersync-implementation-review.md` — Whispersync design + review; overlaps B12/B13/B14.
- `docs/superpowers/specs/2026-06-19-large-file-refactor-goal.md` — the large-file decomposition goal this report's A9/A10/A11 operationalize.
- `docs/superpowers/plans/2026-06-11-reader-webview-stabilization.md` — reader WebView harness/stabilization implementation plan (Phase 1).

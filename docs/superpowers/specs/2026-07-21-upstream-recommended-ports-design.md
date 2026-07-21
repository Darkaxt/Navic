# Recommended Upstream Ports Design

**Date:** 2026-07-21
**Source:** `ssalggnikool/Navic:master` audit at upstream commit `218c93b3`
**Target:** Navic Android `master`, after `v1.0.11-iota25`

## Purpose

Port the useful behavior from five upstream changes without merging the 34-commit upstream batch or replacing Navic's newer playback, genre, deletion-safety, and reader work.

The release is split into independently testable stages. Each stage must leave the application buildable and can be reverted without reverting later unrelated stages.

## Scope

1. Correct LRCLIB duration units from Kotlin `Duration` text to whole seconds.
2. Use LRCLIB search for tolerant lookup, migrate the known legacy `/api/get` endpoint, rank candidates deterministically, and retain exact lookup as a fallback.
3. Preserve playlist-only songs and playlist cross-references during metadata refresh.
4. Replace playlist membership atomically so observers never see a transient empty playlist.
5. On Android 17+, wait for the local-network permission result before login starts and expose a settings path after denial.
6. Scope Compose Navigation3 ViewModels to their back-stack entries so popped screens release memory.

## Non-Goals

- Do not merge upstream queue, genre, year-sort, dependency, or broad UI changes.
- Do not change Navic's playback recovery pipeline.
- Do not weaken `librarySyncDeletionPlan` when album retrieval is incomplete.
- Do not retain all root-tab ViewModels in a process-wide store. Root-tab reloads are preferable to retaining every heavy library model indefinitely.
- Do not add iOS product support or publish an iOS artifact. A compile-only no-op actual may remain where Kotlin Multiplatform requires one.
- Do not use functional timeouts for permission, sync, or cancellation behavior.

## Stage 1: LRCLIB Correctness and Search

### Request model

The persisted `LyricsConfig.lrcLibBaseUrl` remains source-compatible, but its default becomes `https://lrclib.net/api/search`. A known URL ending in `/api/get` is normalized to `/api/search` for the search request; custom hosts and already-search URLs are preserved.

Search uses the title with trailing parenthetical qualifiers removed when that leaves a non-empty title, plus the artist. It does not require album or duration to match at request time.

### Candidate policy

Deserialize the search array into a small LRCLIB candidate model. Candidate ranking is deterministic:

1. normalized title match;
2. normalized artist match;
3. normalized album match;
4. smallest duration delta;
5. synchronized lyrics before plain lyrics when metadata confidence is otherwise equal.

Normalization is case-insensitive and ignores punctuation and repeated whitespace. A candidate without either synchronized or plain lyrics is ineligible. The selected object is serialized back to the existing lyrics parser contract.

If search fails, yields malformed data, or produces no credible title-and-artist match, perform an exact `/api/get` request using original metadata and `song.duration.inWholeSeconds`. This preserves custom deployments and exact-match behavior while removing album/duration strictness from the first attempt.

### Invariants

- Never send Kotlin duration text such as `3m 42s` to LRCLIB.
- Never select the first search result solely because it appears first.
- Preserve provider priority and cached-lyrics behavior.

## Stage 2: Playlist and Song Integrity

### Upsert behavior

Use Room `@Upsert` for song refreshes. `REPLACE` deletes and reinserts a row in SQLite; because `PlaylistSongCrossRef.songId` uses `ON DELETE CASCADE`, that can erase playlist membership while updating song metadata.

### Atomic membership replacement

Build all song entities and ordered cross-references first. Upsert songs, then call the existing transactional `PlaylistDao.replacePlaylistSongs`. Empty remote playlists must still execute replacement so stale local membership is removed atomically.

### Deletion safety

Before authoritative library song deletion, read all current playlist song IDs and union them with the library keep-set. Continue to suppress song deletion completely when `librarySyncDeletionPlan` reports an incomplete album fetch.

This avoids sharing a mutable song-ID set between concurrent playlist jobs and protects playlist-only songs during standalone library refreshes as well as full refreshes.

### Invariants

- A metadata update must not remove playlist membership.
- Playlist observers see either the old membership or the complete new membership, never an intermediate empty/partial state.
- An empty remote playlist clears local membership.
- Partial library retrieval cannot trigger song deletion.

## Stage 3: Android Local-Network Permission

Move permission ownership out of `PlatformContext` into a platform permission manager registered by `MainActivity` before Compose content starts.

On Android below API 37 or when permission is already granted, the request returns immediately. Otherwise, the login action suspends until the Activity Result callback returns. Login runs only after a granted result. Denial shows an explanation and an action that opens Navic's application settings.

Concurrent requests are serialized and cancellation removes only the matching pending request. A missing Activity launcher fails closed instead of crashing through `!!`.

### Invariants

- No server request starts while the required permission decision is pending.
- Denial never calls login.
- Permission cancellation does not resume a stale login.
- No functional timeout is introduced.

## Stage 4: Navigation3 ViewModel Lifecycle

Add the saveable-state and ViewModel-store Navigation3 entry decorators to `NavDisplay`. Switch the lifecycle dependency from `lifecycle-viewmodel-compose` to `lifecycle-viewmodel-navigation3`, which supplies the decorator while retaining Compose ViewModel integration.

Do not import upstream's `PersistentViewModelStoreOwner`. Detail and root screens are scoped to their actual back-stack entries. Popping or replacing an entry clears its ViewModel store, releasing genre, artist, search, reader, and other screen state. Long-lived playback and application managers remain Koin singletons and are unaffected.

### Invariants

- Each back-stack entry owns its screen ViewModels.
- Removing an entry clears its ViewModel store.
- Application singletons and media playback survive navigation.
- Navigation3 itself is not upgraded in this tranche unless compilation proves the current pinned version cannot consume the lifecycle integration artifact; such an upgrade requires a separate compatibility decision.

## Validation

Each stage starts with a failing focused test and ends with that test passing. Final validation requires:

- common tests for endpoint migration, title normalization, duration conversion, candidate ranking, and deletion keep-set policy;
- Android host source-contract tests for transactional playlist replacement, Room upserts, permission ordering, launcher registration, and Nav3 decorators;
- the existing common and Android host regression suites;
- Android debug and release assembly;
- APK manifest/version/signature inspection;
- installation and launch smoke test on an available ADB Android target;
- CI Android release completion with the iOS job skipped.

## Deployment and Rollback

1. Commit this specification and the implementation plan independently.
2. Land LRCLIB, playlist integrity, permission, and lifecycle stages as separate commits.
3. Bump Android to `v1.0.11-iota26`, version code `553`.
4. Rebase or fast-forward from the latest fork `master`, rerun release gates, push, tag, and publish the Android release.
5. Record release evidence, then remove only this task's worktree and branch.

Rollback is stage-specific. The permission stage can be reverted without changing database behavior; the lifecycle stage can be reverted without data migration; LRCLIB retains exact-request fallback; and the playlist changes do not alter the Room schema.

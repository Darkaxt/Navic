# Music Layout Data-Loading QA Specification

Date: 2026-07-19
Baseline: `master` at `5ae6ef7e`
Runtime evidence: Samsung SM-F966B, Navic `v1.0.11-iota24` (`versionCode=551`)
Scope: Android music surfaces only. Reader and iOS behavior are excluded.

## Objective

Make music screens publish the first useful local result as soon as it is available, keep enrichment and explicit refresh work asynchronous, and make memory cost proportional to the visible or requested collection rather than the entire library.

The governing rule is **first resolved, first served**:

1. Local identity and cached state are published first.
2. Independent local sections are loaded concurrently when they do not contend on the same large relation.
3. Remote enrichment and explicit full refreshes update already-visible state as each durable Room write becomes available.
4. A screen requesting one artist, genre, shortcut, or row must not materialize unrelated songs or album-song relations.
5. Concurrency is bounded at work creation. A semaphore inside one coroutine per library item is not considered bounded work creation.

## Runtime Incident

At 20:48:42 on 2026-07-19, opening **Classical Crossover** from the Library **Most Played** row caused a foreground hard crash:

- Process PSS rose from approximately 576 MB to 4.607 GB.
- `ApplicationExitInfo` recorded `LOW_MEMORY` with approximately 3.7 GB RSS.
- Native thread creation then failed with `OutOfMemoryError: pthread_create (4112KB stack)`.
- The failing `qf4 Dispatcher` class maps to OkHttp. It was the first subsystem unable to replace workers after process-wide exhaustion, not evidence of an HTTP protocol failure.
- The package had launched 30 seconds earlier. A startup full sync may have overlapped, but retained logs do not prove that condition.
- Samsung process history contains a comparable approximately 4.6 GB Navic event from `theta84`, so the defect predates the iota24 reader changes.

The preserved device evidence is outside the repository at:
`C:/Users/darka/Documents/Projects/Android/_analysis/navic-oom-20260719-204846/`.

## Findings

### M1 - Critical: Genre detail loads every album and every song before selecting one genre

`GenreRepository.getGenreByName()` calls `getLocalData()`, which executes `AlbumDao.getAllAlbumsList()`, maps every `AlbumWithSongs`, builds every genre group, and only then scans for the requested name. `AlbumDao.getAlbumsByGenre()` already exists but is unused.

**Impact:** Opening one genre duplicates a complete album/song object graph while the Library genre state can still retain another complete graph. This is the strongest source-level match for the observed crash trigger.

**Required correction:** Observe only candidate albums for the requested genre, apply exact normalized genre matching after the SQL prefilter, and derive the detail state in one pass.

### M2 - Critical: Genre summaries retain complete album/song graphs

`GenreListViewModel` stores `List<DomainGenre>`, and every `DomainGenre` stores complete `DomainAlbum` values with their complete `DomainSong` lists. The Library creates this ViewModel unconditionally to render cards needing only name, counts, and two cover IDs.

**Impact:** A summary row retains nearly the same graph as a full library query. The list and detail routes can overlap during navigation.

**Required correction:** Introduce a summary model containing only name, album count, song count, and up to two cover IDs. Build summaries from album metadata projections that do not join songs.

### M3 - High: Song views load all album-song relations for two album timestamps

`SongRepository.songsFlow()` combines the full song table with `AlbumDao.getAllAlbums()`, maps every album and every nested song, then uses albums only for `createdAt` ordering in Quick Picks and Newest.

**Impact:** Library Quick Picks creates a complete song list and a second complete song graph nested under albums during startup. Other song sort modes pay the same album relation cost even though they do not use album metadata.

**Required correction:** Replace the album relation with an `albumId`/`createdAt` projection. Keep the existing Quick Picks ordering policy and visible behavior.

### M4 - High: Artist Detail scans every song for credits

`ArtistDetailViewModel.loadArtistData()` calls `SongRepository.getAllSongs()` and filters the complete library for direct and contributor credits.

**Impact:** Opening a single artist allocates and maps all songs, including contributor lists and metadata unrelated to that artist.

**Required correction:** Query direct artist IDs/names and encoded contributor candidates in SQL, then retain the existing exact Kotlin match as the correctness filter.

### M5 - High: Most Played observes artwork for every song and album

`MostPlayedShortcutsViewModel` combines a maximum of 20 shortcuts with `observeAlbumArtistArtwork()` and `observeArtistSongArtwork()` over the complete album and song tables. Album/song fallback artwork is only used for shortcuts of type Artist.

**Impact:** A small Library row retains and remaps library-wide artwork projections after any relevant Room invalidation.

**Required correction:** Derive artist shortcut IDs/names first, then `flatMapLatest` into DAO flows restricted to those identities. Non-artist shortcuts must not activate artist fallback queries.

### M6 - High: Genre refresh is all-or-nothing

Both genre list and genre detail use cold one-shot flows. Explicit refresh waits for `syncLibrarySongs()` and `syncGenres()` to finish before publishing refreshed state.

**Impact:** Existing local state remains wrapped in one loading operation, and intermediate durable database progress is not reflected by the screen.

**Required correction:** Genre list/detail must observe Room continuously. Explicit refresh goes through the existing serialized `SyncManager` actor; visible local data remains available while Room emissions incrementally replace it.

### M7 - High: Library sync creates one coroutine per album

`DbRepository.syncLibrarySongs()` creates one coroutine for every album and places the concurrency semaphore inside each coroutine.

**Impact:** Network concurrency is eight, but coroutine/job allocation scales with total album count and overlaps with UI graph construction during startup sync.

**Required correction:** Feed summaries through a bounded channel to exactly `LIBRARY_SYNC_NETWORK_CONCURRENCY` workers. The number of live fetch workers must not scale with library size.

### M8 - Medium: Library eagerly owns every row pipeline

`LibraryScreen` constructs Quick Picks, three album ViewModels, playlists, artists, genres, Most Played, login, and Aurral pipelines before checking row visibility. Three album ViewModels map separate album-song query results.

**Impact:** Hidden rows still load, and visible horizontal rows retain complete result sets even though the lazy row composes only a window.

**Disposition:** Partially mitigated by M2, M3, and M5 in this release. Conditional row ownership and bounded Library-only result queries require a dedicated Library state owner and remain follow-up work.

### M9 - Medium: Aurral artist local matching loads every artist before first local publication

`AurralArtistScreen` maps every local artist and the complete photo-cache index before publishing the local artist and local albums. The full list is later used to match similar artists.

**Impact:** A secondary section delays primary local identity.

**Disposition:** Existing network enrichment is already core-first and parallel. Split primary local identity from the later similar-artist index in a follow-up change after the crash-critical graph fixes are deployed.

### M10 - Medium: Artist enrichment still uses unbounded per-release async fan-out

Artist ownership/missing-album helpers use `map { async { ... } }.awaitAll()` over release collections.

**Impact:** Work creation scales with release count even when transport concurrency is bounded elsewhere.

**Disposition:** Not implicated in the genre crash and runs after local artist publication. Convert to a bounded worker policy in a separate Aurral ownership tranche.

### M11 - Low: Radio and collection repositories retain cold refresh flows

Radio and collection detail still use cold `flow { local; optional remote; final }` pipelines. Their result sets are bounded and do not load unrelated library relations.

**Disposition:** Keep under observation. Convert when those screens need incremental section state; they are not release blockers for this incident.

## Target Model

```text
Route identity
    -> targeted Room Flow
        -> first local state published
        -> lightweight derived sections published

Explicit refresh
    -> serialized SyncManager request
        -> bounded network workers
        -> batched Room writes
            -> targeted Room Flow emits incrementally
                -> existing UI state is replaced without blanking
```

Summary routes and detail routes have separate data contracts. A summary never owns complete songs. Detail queries are keyed by the route identity. Full refresh is a background producer, not a prerequisite for rendering local data.

## Release Stages

### Stage 1 - Crash containment

- Replace genre list graphs with lightweight summaries.
- Replace all-library genre detail lookup with a targeted reactive query.
- Derive genre detail lists once per Room emission.
- Keep local genre data visible during serialized full refresh.

### Stage 2 - Remove unrelated full-library joins

- Replace SongRepository's album-song relation with album timestamp metadata.
- Restrict Artist Detail credit candidates to the requested artist.
- Restrict Most Played fallback artwork to visible artist shortcuts.

### Stage 3 - Bound producer concurrency

- Replace per-album sync coroutine fan-out with a fixed worker set.
- Preserve network concurrency, database batching, progress reporting, skip semantics, and authoritative deletion rules.

### Stage 4 - Validation and deployment

- Run policy, DAO source-contract, repository source-contract, and affected ViewModel tests.
- Run the broad Android host suite and distinguish baseline reader failures from branch-owned failures.
- Assemble Android debug and release APKs.
- Install over the current public package and reproduce the Classical Crossover route while sampling PSS.
- Publish the next `v1.0.11-iota##` Android-only release, verify asset digest/signature/version/install/launch, and remove only this worktree and branch.

## Acceptance Criteria

1. Genre summary production code has no `getAllAlbumsList()` call and no summary model containing `DomainAlbum` or `DomainSong`.
2. Genre detail production code observes an album query keyed by genre name and does not call `genreGroupsFromAlbums()` for a one-genre request.
3. `SongRepository.songsFlow()` does not call `AlbumDao.getAllAlbums()` or map `AlbumWithSongs`.
4. Artist Detail does not call `SongRepository.getAllSongs()`.
5. Most Played does not observe unfiltered album/song artwork flows.
6. Library sync creates a fixed number of album fetch workers independent of album count.
7. Pull-to-refresh keeps prior genre data visible and uses the serialized sync actor.
8. Focused tests, affected host tests, Android build, APK validation, and device route validation pass with fresh evidence.
9. No iOS task is run or published.


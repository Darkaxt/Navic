# Randomized Collection Start and Aurral Maintenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Randomize the generated first song for shuffled bulk playback while preserving index-zero startup, and reduce the known Android host-test baseline from 68 failures to the 64 reader-only failures.

**Architecture:** A pure common policy generates either canonical or shuffled session queues. Android replaces the Media3 queue and `PlayerUiState` atomically through one `playAll` operation, while direct song selection remains unchanged. Aurral mutation commands move into a focused collaborator, and three stale source-contract tests are updated to assert current behavior rather than historical filenames or schema versions.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines, AndroidX Media3, Room, Koin, kotlin.test, Gradle Android host tests.

---

### Task 1: Deterministic Collection Queue Generation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackQueuePolicy.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackQueuePolicyTest.kt`

- [ ] **Step 1: Write the failing policy tests**

Add tests proving canonical order is unchanged, shuffled order uses supplied deterministic randomness, every item occurs exactly once, and empty/single-item collections are safe:

```kotlin
@Test
fun shuffledCollectionGenerationChangesTheFirstSongWithoutLosingEntries() {
    val songs = listOf("first", "second", "third")
    val generated = collectionPlaybackOrder(songs, shuffleEnabled = true, Random(7))

    assertNotEquals("first", generated.first())
    assertEquals(songs.toSet(), generated.toSet())
    assertEquals(songs.size, generated.size)
}
```

- [ ] **Step 2: Run the policy test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.PlaybackQueuePolicyTest"
```

Expected: compilation fails because `collectionPlaybackOrder` does not exist, or the old canonical shuffle-plan assertion fails.

- [ ] **Step 3: Implement the pure queue generator**

Add this common policy and update the explicit shuffle plan to generate a shuffled queue:

```kotlin
fun <T> collectionPlaybackOrder(
    items: List<T>,
    shuffleEnabled: Boolean,
    random: Random = Random.Default
): List<T> = if (shuffleEnabled && items.size > 1) items.shuffled(random) else items
```

`collectionShufflePlaybackPlan().queueOrder` must become `CollectionShuffleQueueOrder.Shuffled`; start index remains a player concern and remains zero.

- [ ] **Step 4: Run the policy test and verify GREEN**

Run the command from Step 2. Expected: all `PlaybackQueuePolicyTest` tests pass.

- [ ] **Step 5: Commit the policy**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackQueuePolicy.kt composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackQueuePolicyTest.kt
git commit -m "fix(playback): randomize generated shuffle queues"
```

### Task 2: Atomic Bulk Playback and Surface Migration

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/shared/MediaPlayer.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AndroidMediaPlayerViewModelSourceTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/viewmodels/GenreDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/components/HeadingRowButtons.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubViewModel.kt`

- [ ] **Step 1: Write failing Android source-contract tests**

Require a single bulk operation and prohibit the old clear/add/play sequencing in the affected surfaces:

```kotlin
assertContains(commonPlayerText, "open fun playAll(")
assertContains(androidPlayerText, "override fun playAll(")
assertContains(androidPlayerText, "collectionPlaybackOrder(")
assertContains(androidPlayerText, "player.setMediaItems(mediaItems, 0, 0L)")
assertContains(genreText, "player.playAll(state.collection.songs)")
assertContains(artistText, "player.playAll(songs)")
```

Also assert that direct `playCollection(collection, startSong)` still resolves the selected song index.

- [ ] **Step 2: Run the focused source test and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.shared.AndroidMediaPlayerViewModelSourceTest"
```

Expected: failure because `playAll` and the migrated call sites do not exist.

- [ ] **Step 3: Add the common player contract**

Add an overridable `playAll(songs, forceShuffle)` operation. Its common fallback must no-op for an empty list, generate the order from active/forced shuffle, replace the queue, and play index zero. Android overrides it atomically.

```kotlin
open fun playAll(songs: List<DomainSong>, forceShuffle: Boolean = false) {
    if (songs.isEmpty()) return
    val shuffleEnabled = forceShuffle || uiState.value.isShuffleEnabled
    val playbackOrder = collectionPlaybackOrder(songs, shuffleEnabled)
    clearQueue()
    addToQueue(playbackOrder, notify = false)
    if (forceShuffle && !uiState.value.isShuffleEnabled) toggleShuffle()
    playAt(0)
}
```

- [ ] **Step 4: Implement Android atomic replacement**

The override must generate media items off the main dispatcher, retain repeat mode, clear recovery and stale selection state, replace queue/index/current song together, and use `playbackStateSynchronizer.sync(nextState)` when the controller is unavailable.

```kotlin
override fun playAll(songs: List<DomainSong>, forceShuffle: Boolean) {
    if (songs.isEmpty()) return
    viewModelScope.launch {
        val shuffleEnabled = forceShuffle || controller?.shuffleModeEnabled || _uiState.value.isShuffleEnabled
        val playbackOrder = withContext(Dispatchers.Default) {
            collectionPlaybackOrder(songs, shuffleEnabled)
        }
        val mediaItems = withContext(Dispatchers.Default) { playbackOrder.map { it.toMediaItem() } }
        val nextState = _uiState.value.copy(
            queue = playbackOrder,
            currentIndex = 0,
            currentSong = playbackOrder.first(),
            isPaused = false,
            isShuffleEnabled = shuffleEnabled,
            progress = 0f
        )
        _uiState.value = nextState
        controller?.let { player ->
            player.shuffleModeEnabled = shuffleEnabled
            player.setMediaItems(mediaItems, 0, 0L)
            player.prepare()
            claimMusicPlayback()
            player.play()
        } ?: playbackStateSynchronizer.sync(nextState)
    }
}
```

`shufflePlay(collection)` delegates to `playAll(collection.songs, forceShuffle = true)`.

- [ ] **Step 5: Migrate every multi-song index-zero caller**

Genre, artist, collection heading, and Aurral station/flow paths must call `playAll`. Keep single-song quick picks, song-row Play, queue selection, and selected album-song playback unchanged.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.PlaybackQueuePolicyTest" --tests "paige.navic.shared.AndroidMediaPlayerViewModelSourceTest"
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit atomic playback**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/shared/MediaPlayer.kt composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/shared/AndroidMediaPlayerViewModelSourceTest.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens
git commit -m "fix(playback): randomize bulk playback first song"
```

### Task 3: Repair Three Stale Source Contracts

**Files:**
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/di/AndroidDatabaseMigrationPolicySourceTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/library/LibraryStartupAsyncSourceTest.kt`

- [ ] **Step 1: Reproduce the three stale failures**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.di.AndroidDatabaseMigrationPolicySourceTest" --tests "paige.navic.ui.screens.library.LibraryStartupAsyncSourceTest"
```

Expected failures:

- database builders are no longer in `PlatformModule.android.kt`;
- artwork cache still asserts historical schema version 20 instead of current version 24;
- Aurral discovery still asserts obsolete non-typed repository method names.

- [ ] **Step 2: Point the migration guard at its current owner**

Read `DatabaseModule.android.kt`, assert exactly two Room builders, assert both download/cache migration registrations, and continue prohibiting destructive migration.

- [ ] **Step 3: Make the artwork schema guard current and structural**

Assert `version = 24`, `ArtworkColorEntity::class`, `artworkColorDao()`, and `CacheDatabaseMigration23To24` registration. The test message must describe the current schema contract rather than the historical 19-to-20 change.

- [ ] **Step 4: Update the incremental discovery contract**

Assert `getDiscoveryOptional(hydrateMissingImages)` is followed by publication of `UiState.Success(result.data)`, then `loadDiscoverySupplement`. Assert supplements call `getDiscoveryRecentlyAddedOptional()` and `getDiscoveryRecentReleasesOptional()`.

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run the command from Step 1. Expected: both test classes pass.

- [ ] **Step 6: Commit test maintenance**

```powershell
git add composeApp/src/androidHostTest/kotlin/paige/navic/di/AndroidDatabaseMigrationPolicySourceTest.kt composeApp/src/commonTest/kotlin/paige/navic/ui/screens/library/LibraryStartupAsyncSourceTest.kt
git commit -m "test: align shared source contracts with current owners"
```

### Task 4: Extract Aurral Mutation Ownership

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralMutationRepositoryActions.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/domain/repositories/AurralRepositorySourceTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt`

- [ ] **Step 1: Strengthen the failing architecture contract**

Read the new mutation collaborator in `AurralRepositorySourceTest` and require repository delegation:

```kotlin
assertContains(repositoryText, "private val mutationActions = AurralMutationRepositoryActions(")
assertContains(mutationText, "internal class AurralMutationRepositoryActions")
assertContains(mutationText, "suspend fun cancelAcquisitionRequest(")
assertContains(mutationText, "suspend fun setArtistMonitoring(")
assertFalse("apiClient.cancelAcquisitionRequest(" in repositoryText)
assertFalse("apiClient.monitorArtist(" in repositoryText)
```

Keep the `< 1_400` repository budget unchanged.

- [ ] **Step 2: Run the architecture test and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.AurralRepositorySourceTest"
```

Expected: failure because the mutation collaborator does not exist and the repository exceeds the budget.

- [ ] **Step 3: Extract mutation behavior without changing its public API**

Move cancel/retry acquisition, both album request forms, and artist monitoring into `AurralMutationRepositoryActions`. Inject `PreferenceManager`, `AurralApiClient`, `AurralRepositoryAuth`, `AurralRepositoryLocalState`, `AurralConfirmationQueueManager`, `nowMillis`, `confirmationWorkerEnabled`, and metadata-cache invalidation. Keep logging, availability marking, optimistic state, confirmation queue, and validation messages identical.

The repository retains one-line public delegates so all callers remain source compatible:

```kotlin
suspend fun cancelAcquisitionRequest(item: AurralAcquisitionQueueItem): Result<Unit> =
    mutationActions.cancelAcquisitionRequest(item)

suspend fun setArtistMonitoring(artist: DomainArtist, monitored: Boolean): Result<Unit> =
    mutationActions.setArtistMonitoring(artist, monitored)
```

- [ ] **Step 4: Run mutation behavior and architecture tests**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.AurralRepositoryTest" --tests "paige.navic.domain.repositories.AurralRepositoryArtistEnrichmentTest" --tests "paige.navic.domain.repositories.AurralRepositorySourceTest"
```

Expected: all selected tests pass and `AurralRepository.kt` is below 1,400 lines.

- [ ] **Step 5: Commit the extraction**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralMutationRepositoryActions.kt composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt composeApp/src/androidHostTest/kotlin/paige/navic/domain/repositories/AurralRepositorySourceTest.kt
git commit -m "refactor(aurral): extract mutation actions"
```

### Task 5: Full Verification and Release

**Files:**
- Modify: release version files selected by the repository release task

- [ ] **Step 1: Run focused music and Aurral tests**

Run all test classes touched in Tasks 1-4. Expected: zero focused failures.

- [ ] **Step 2: Run the broad Android host suite**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest
```

Expected: 3,414 or more tests with exactly 64 failures, all reader-related. No music, Aurral, database, or library-startup test may fail.

- [ ] **Step 3: Build the Android release artifact**

Run the repository's release verification/version guard and signed APK assembly using the next `iota##` version. Confirm Gradle exits successfully, inspect APK package/version/signature, and retain the artifact hash.

- [ ] **Step 4: Device smoke test when Android hardware is attached**

Install the signed APK, launch Navic, and inspect logs for startup/player crashes. Exercise Classical Crossover and Lindsey Stirling bulk playback repeatedly when their server data is available; verify shuffled launches are not structurally pinned to the same first song and repeat-all remains enabled.

- [ ] **Step 5: Commit release metadata, push, and publish**

Push `fix/randomized-collection-start`, create the next `v1.0.11-iota##` release, verify the public APK asset and hash, then remove the isolated worktree only after the branch and release are recoverable from Git.

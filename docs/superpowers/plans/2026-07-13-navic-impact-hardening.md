# Navic Impact Hardening Stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent silent Android database recreation, protect cached library rows after partial album deserialization, and keep the persisted playback queue current during continuous playback.

**Architecture:** This stage changes three independent failure boundaries without broad refactoring. Android Room builders become fail-closed when a migration path is absent; library deletion is driven by a pure reconciliation plan derived from authoritative summaries and successfully decoded details; playback persistence uses one serialized merged flow, saving structural changes immediately while sampling progress-only changes.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines/Flow, AndroidX Room 3, DataStore Preferences, kotlin.test, Gradle.

**Status:** Completed and released as `v1.0.11-theta94` on 2026-07-13. Affected Android test packages and the complete debug APK build passed. The unfiltered Android host suite retained 29 unrelated reader-harness/bitmap failures from the concurrent ebook work; the unsupported iOS aggregate target also remained non-compiling.

---

### Task 1: Fail-closed Android Room migration policy

**Files:**
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/di/AndroidDatabaseMigrationPolicySourceTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt:22-41`

- [ ] **Step 1: Write the failing source-contract test**

Create a test that reads `PlatformModule.android.kt`, asserts both `databaseBuilder` calls remain present, and rejects `fallbackToDestructiveMigration` anywhere in the Android module:

```kotlin
package paige.navic.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidDatabaseMigrationPolicySourceTest {
	@Test
	fun androidDatabasesFailClosedWhenAnUpgradeMigrationIsMissing() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt"
		).readText()

		assertEquals(2, "databaseBuilder<".toRegex().findAll(source).count())
		assertFalse("fallbackToDestructiveMigration" in source)
	}

	private fun sourceFile(path: String): File = listOf(File(path), File("../$path"))
		.firstOrNull { it.isFile }
		?: error("Unable to locate $path")
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:androidHostTest --tests paige.navic.di.AndroidDatabaseMigrationPolicySourceTest
```

Expected: FAIL because `PlatformModule.android.kt` contains two destructive fallback calls.

- [ ] **Step 3: Remove destructive fallback from Android builders**

Delete only these two calls:

```kotlin
.fallbackToDestructiveMigration(true)
```

Do not change database versions and do not add a no-op migration. Existing version-20/version-4 installations continue opening normally; a future unregistered schema upgrade now fails visibly instead of deleting state.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit the migration-policy change**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/di/AndroidDatabaseMigrationPolicySourceTest.kt
git commit -m "fix(data): fail closed on missing Android migrations"
```

### Task 2: Authoritative library obsolete-row reconciliation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/LibrarySyncDeletionPolicy.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/LibrarySyncDeletionPolicyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/DbRepository.kt:150-261`

- [ ] **Step 1: Write failing pure-policy tests**

Cover a complete pass and a pass where one authoritative album detail was skipped:

```kotlin
package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibrarySyncDeletionPolicyTest {
	@Test
	fun completeDetailPassAllowsAlbumAndSongReconciliation() {
		val plan = librarySyncDeletionPlan(
			authoritativeAlbumIds = setOf("album-a", "album-b"),
			fetchedAlbumIds = setOf("album-a", "album-b"),
			fetchedSongIds = setOf("song-a", "song-b")
		)

		assertEquals(setOf("album-a", "album-b"), plan.albumIdsToKeep)
		assertEquals(setOf("song-a", "song-b"), plan.songIdsToKeep)
	}

	@Test
	fun skippedDetailPreservesSongsUntilACompletePass() {
		val plan = librarySyncDeletionPlan(
			authoritativeAlbumIds = setOf("album-a", "album-b"),
			fetchedAlbumIds = setOf("album-a"),
			fetchedSongIds = setOf("song-a")
		)

		assertEquals(setOf("album-a", "album-b"), plan.albumIdsToKeep)
		assertNull(plan.songIdsToKeep)
	}
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.models.LibrarySyncDeletionPolicyTest
```

Expected: compilation failure because `librarySyncDeletionPlan` does not exist.

- [ ] **Step 3: Implement the pure deletion plan**

```kotlin
package paige.navic.domain.models

data class LibrarySyncDeletionPlan(
	val albumIdsToKeep: Set<String>,
	val songIdsToKeep: Set<String>?
)

fun librarySyncDeletionPlan(
	authoritativeAlbumIds: Set<String>,
	fetchedAlbumIds: Set<String>,
	fetchedSongIds: Set<String>
): LibrarySyncDeletionPlan = LibrarySyncDeletionPlan(
	albumIdsToKeep = authoritativeAlbumIds,
	songIdsToKeep = fetchedSongIds.takeIf { fetchedAlbumIds.containsAll(authoritativeAlbumIds) }
)
```

- [ ] **Step 4: Verify the policy test is GREEN**

Run the command from Step 2. Expected: both tests PASS.

- [ ] **Step 5: Apply the plan in `syncLibrarySongs`**

After detail collection completes, derive and apply one plan:

```kotlin
val deletionPlan = librarySyncDeletionPlan(
	authoritativeAlbumIds = allAlbumSummaries.mapTo(mutableSetOf()) { it.id },
	fetchedAlbumIds = allValidAlbumIds,
	fetchedSongIds = allValidSongIds
)
albumDao.deleteObsoleteAlbums(deletionPlan.albumIdsToKeep)
deletionPlan.songIdsToKeep?.let { songDao.deleteObsoleteSongs(it) }
```

This keeps deleted albums removable from the authoritative summary set while preserving every song when any listed album detail was skipped.

- [ ] **Step 6: Run policy tests and common tests**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.models.LibrarySyncDeletionPolicyTest
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.*" --tests "paige.navic.ui.core.*" --tests "paige.navic.shared.*" --tests "paige.navic.di.*"
```

Expected: PASS.

- [ ] **Step 7: Commit the reconciliation change**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/domain/models/LibrarySyncDeletionPolicy.kt composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/DbRepository.kt composeApp/src/commonTest/kotlin/paige/navic/domain/models/LibrarySyncDeletionPolicyTest.kt
git commit -m "fix(sync): preserve rows after partial album decoding"
```

### Task 3: Durable playback-state persistence

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/core/PlayerPersistencePolicy.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/ui/core/PlayerPersistencePolicyTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/PlayerPersistenceSourceTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/shared/MediaPlayer.kt:1-170`

- [ ] **Step 1: Write failing durable-key tests**

```kotlin
package paige.navic.ui.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlayerPersistencePolicyTest {
	@Test
	fun progressAndTransientPlaybackMetadataDoNotChangeTheDurableKey() {
		val initial = PlayerUiState(progress = .1f)
		val updated = initial.copy(
			progress = .9f,
			isLoading = true,
			playbackDownloadProgress = .5f,
			playbackBitrate = 320,
			playbackSampleRate = 48_000,
			playbackMimeType = "audio/flac"
		)

		assertEquals(initial.durablePlayerStateKey(), updated.durablePlayerStateKey())
	}

	@Test
	fun queueAndPlaybackControlChangesChangeTheDurableKey() {
		val initial = PlayerUiState(currentIndex = 0, isPaused = false)

		assertNotEquals(initial.durablePlayerStateKey(), initial.copy(currentIndex = 1).durablePlayerStateKey())
		assertNotEquals(initial.durablePlayerStateKey(), initial.copy(isPaused = true).durablePlayerStateKey())
		assertNotEquals(initial.durablePlayerStateKey(), initial.copy(isShuffleEnabled = true).durablePlayerStateKey())
	}
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.ui.core.PlayerPersistencePolicyTest
```

Expected: compilation failure because `durablePlayerStateKey` does not exist.

- [ ] **Step 3: Implement the durable key**

Create an internal data class containing every persisted structural/control field and excluding only `progress`, `isLoading`, download progress, and decoded media-format metadata. `durablePlayerStateKey()` must include `queue`, `currentSong`, `currentCollection`, `currentIndex`, `upcomingIndexes`, pause/shuffle/repeat, speed, and pitch.

- [ ] **Step 4: Verify the policy tests are GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Replace debounce with one serialized merged persistence flow**

In `observeAndSaveState`, merge:

```kotlin
val structuralSnapshots = _uiState.distinctUntilChangedBy { it.durablePlayerStateKey() }
val progressSnapshots = _uiState.sample(5.seconds)

merge(structuralSnapshots, progressSnapshots).collect { state ->
	persistState(state)
}
```

Move existing DataStore clear/save/error handling into one suspend `persistState` function called only by that collector. This guarantees immediate structural saves, periodic progress snapshots during uninterrupted playback, and serialized DataStore writes. Sampling is persistence cadence, not cancellation or an operation timeout.

- [ ] **Step 6: Add a source guard against regression to debounce**

Extend `PlayerPersistencePolicyTest` or add an Android host source test that asserts `MediaPlayer.kt` contains `distinctUntilChangedBy`, `sample`, and `merge`, and does not contain `.debounce(`.

- [ ] **Step 7: Run focused and broad tests**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.ui.core.PlayerPersistencePolicyTest --tests paige.navic.shared.PlayerPersistenceSourceTest
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.*" --tests "paige.navic.ui.core.*" --tests "paige.navic.shared.*" --tests "paige.navic.di.*"
.\gradlew.bat :androidApp:assembleDebug
```

Expected: PASS.

- [ ] **Step 8: Commit playback persistence**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/shared/MediaPlayer.kt composeApp/src/commonMain/kotlin/paige/navic/ui/core/PlayerPersistencePolicy.kt composeApp/src/commonTest/kotlin/paige/navic/ui/core/PlayerPersistencePolicyTest.kt
git commit -m "fix(playback): persist queue changes during playback"
```

### Task 4: Audit status, release validation, and cleanup

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `androidApp/build.gradle.kts`
- Modify: `README.md` only if release documentation requires the new tag/version.

- [ ] **Step 1: Mark C1, C5, and C8 implemented with commit references**

Add a short implementation-status block without changing the remaining findings or their impact ranking.

- [ ] **Step 2: Run final validation**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.*" --tests "paige.navic.ui.core.*" --tests "paige.navic.shared.*" --tests "paige.navic.di.*"
.\gradlew.bat :androidApp:assembleDebug
git diff --check
git status --short
```

Expected: all commands PASS; only intended release/version documentation remains uncommitted before the release commit.

- [ ] **Step 3: Bump the Android prerelease version and commit**

Use the next unused theta version and increment `versionCode` by one, then commit the audit, implementation plan, and release metadata.

- [ ] **Step 4: Integrate to `master`, push, tag, and verify the public release**

Fast-forward or merge from the clean master worktree only after confirming the ebook animation branches have not been included. Push `master`, create the matching tag, verify the GitHub workflow, release asset, embedded APK version, signature, and install/launch on the connected Android device.

- [ ] **Step 5: Clean the temporary worktree**

After the branch is merged and reachable from `master` and the public release tag:

```powershell
git worktree remove C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-qa-impact-hardening
git worktree prune
git branch -d fix/qa-impact-hardening
```

Confirm the path is absent and `git worktree list --porcelain` no longer lists it. If Git unregisters the worktree but leaves a Windows filesystem residue, verify the resolved path is exactly the intended `.codex-temp` child before removing only that residue.

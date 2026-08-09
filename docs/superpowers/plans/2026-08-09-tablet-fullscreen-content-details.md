# Tablet Fullscreen Content Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make content destinations replace Library at full tablet width and hide playlists that have no declared or locally loaded songs.

**Architecture:** Keep Navigation3 adaptive list/detail support for the Settings group only. Root content destinations use Navigation3's default single-scene presentation by omitting `detailPane("root")` metadata; the back stack still owns restoration of Library. A domain playlist display predicate feeds every existing playlist grouping function while leaving persistence and add-to-playlist selection untouched.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, AndroidX Navigation3, Material3 Adaptive, Kotlin host tests, Gradle, ADB.

---

### Task 1: Lock the navigation contract

**Files:**
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/navigation/RootContentDetailSceneSourceTest.kt`

- [ ] **Step 1: Write the failing source-contract test**

Create a test that reads `composeApp/src/commonMain/kotlin/paige/navic/App.kt`, rejects every occurrence of `detailPane("root")`, asserts all eight content entries have no metadata argument, and confirms Settings still uses its `settings` list/detail group.

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.navigation.RootContentDetailSceneSourceTest"
```

Expected: FAIL because `App.kt` still contains `detailPane("root")`.

- [ ] **Step 3: Commit the test**

```powershell
git add composeApp/src/androidHostTest/kotlin/paige/navic/ui/navigation/RootContentDetailSceneSourceTest.kt
git commit -m "test(navigation): require fullscreen root details"
```

### Task 2: Present root content details as one scene

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/App.kt`

- [ ] **Step 1: Remove root detail metadata**

Change each of the eight root content entries from this form:

```kotlin
entry<Screen.CollectionDetail>(metadata = detailPane("root")) { key ->
```

to this form:

```kotlin
entry<Screen.CollectionDetail> { key ->
```

Apply the same metadata removal to all Aurral root content entries and `SongDetail`. Do not alter any `settings` metadata.

- [ ] **Step 2: Run the focused contract and verify GREEN**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.navigation.RootContentDetailSceneSourceTest"
```

Expected: PASS.

- [ ] **Step 3: Run adjacent navigation tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.navigation.*"
```

Expected: PASS.

- [ ] **Step 4: Commit the implementation**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/App.kt
git commit -m "fix(navigation): fill tablets with content details"
```

### Task 3: Hide empty playlists on display surfaces

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaylistStationPolicy.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaylistStationPolicyTest.kt`

- [ ] **Step 1: Write failing visibility tests**

Add tests proving that all grouping functions exclude a playlist with `songCount == 0` and no local songs, retain a playlist with a positive declared count, and retain a playlist with locally loaded songs even when declared count is stale at zero.

- [ ] **Step 2: Run the policy test and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.PlaylistStationPolicyTest"
```

Expected: FAIL because empty playlists currently remain in category results.

- [ ] **Step 3: Add the shared display predicate**

Add:

```kotlin
fun DomainPlaylist.hasVisibleEntries(): Boolean =
	songCount > 0 || songs.isNotEmpty()
```

Filter each playlist category through this predicate before classifying its station/mix/user type.

- [ ] **Step 4: Run the policy test and verify GREEN**

Run the focused test from Step 2. Expected: PASS.

- [ ] **Step 5: Commit the playlist policy**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaylistStationPolicy.kt composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaylistStationPolicyTest.kt
git commit -m "fix(playlists): hide empty display entries"
```

### Task 4: Build and validate on the physical tablet

**Files:**
- Modify: `androidApp/build.gradle.kts` only for the next release version after source validation

- [ ] **Step 1: Run Android build and packaging gates**

Run:

```powershell
.\gradlew.bat :androidApp:assembleDebug
.\scripts\verify-reader-vendor-assets.ps1 -ApkPath androidApp\build\outputs\apk\debug\Navic.apk
.\scripts\verify-third-party-attributions.ps1 -ApkPath androidApp\build\outputs\apk\debug\Navic.apk
```

Expected: all commands exit successfully.

- [ ] **Step 2: Prepare and publish the next Android release**

Increment version code once and version name from `v1.0.11-iota51` to `v1.0.11-iota52`, commit, tag, and run the repository release publisher with Android public-release approval. The prerelease tag must continue to skip iOS.

- [ ] **Step 3: Verify and install the public APK**

Verify release SHA-256, package version, signing certificate, vendor files, and attributions. Install with:

```powershell
adb -s R52W60CFTRL install -r <downloaded-Navic.apk>
```

Expected: installation succeeds and package reports version code `579` and name `v1.0.11-iota52`.

- [ ] **Step 4: Reproduce the tablet flow**

Open Library, select a collection, and capture a landscape screenshot plus UI hierarchy. Confirm the detail occupies the full Navic content width and only one mini-player and bottom navigation are rendered.

- [ ] **Step 5: Validate Back**

Send Android Back once and confirm Library is restored full-width without restarting the process.

- [ ] **Step 6: Clean temporary artifacts**

After public `master` and the release tag contain the work, remove screenshots, downloaded APKs, the isolated worktree, and its local feature branch. Do not modify the active ebook worktree.

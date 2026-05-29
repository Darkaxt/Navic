# MusicBrainz Cover Recovery And Trivia Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover blank Navidrome covers with MusicBrainz/Cover Art Archive, move MusicBrainz controls into Integrations, and add a lyrics-style MusicBrainz info screen with optional cached LidaClips video backgrounds.

**Architecture:** Keep Navidrome/Subsonic canonical and treat MusicBrainz/CAA as enrichment. Add small pure policy functions for cover-failure fallback and LidaClips extra-screen background eligibility, then wire Compose screens/settings around those policies. Reuse existing MusicBrainz cache, LidaClips cache, Lyrics layout, and Now Playing action patterns.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, Coil 3, Ktor, Media3-backed Android LidaClips player, Kotlin common tests, Gradle.

---

## File Structure

- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/MusicBrainzArtworkRepository.kt` for cover failure reporting, diagnostics, and display row helpers.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/MusicBrainzArtworkRepositoryTest.kt` for fallback policy and diagnostics tests.
- Create `composeApp/src/commonMain/kotlin/paige/navic/domain/models/LidaClipsExtraScreenBackgroundPolicy.kt` for Lyrics/Trivia background gating.
- Create `composeApp/src/commonTest/kotlin/paige/navic/domain/models/LidaClipsExtraScreenBackgroundPolicyTest.kt`.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt` for `lidaClipsLyricsVideoBackground` and `lidaClipsMusicBrainzInfoVideoBackground`.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt` to report server cover failures.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/IntegrationsScreen.kt`, `LidaClipsScreen.kt`, `DataStorageScreen.kt`, and `SettingsSearchResults.kt` for settings placement/search.
- Create `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/musicBrainz/MusicBrainzInfoScreen.kt`.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/Screen.kt`, `App.kt`, and `NowPlayingScreen.kt` to add the screen and action.
- Modify `composeApp/src/commonMain/composeResources/values/strings.xml` for user-facing copy.
- Update `README.md` after implementation.

---

### Task 1: MusicBrainz cover failure policy and diagnostics

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/MusicBrainzArtworkRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/MusicBrainzArtworkRepositoryTest.kt`

- [ ] **Step 1: Write failing tests**

Add tests proving:

```kotlin
@Test
fun artworkLookupAllowsFallbackWhenServerCoverFailed() {
    assertTrue(
        shouldResolveMusicBrainzArtworkOnPlayback(
            enabled = true,
            isOnline = true,
            isRadio = false,
            songCoverArtId = "song-cover",
            albumCoverArtId = "album-cover",
            serverCoverLoadFailed = true,
            songMusicBrainzId = RecordingMbid,
            albumMusicBrainzId = null
        )
    )
}

@Test
fun artworkLookupStillBlocksHealthyServerCover() {
    assertFalse(
        shouldResolveMusicBrainzArtworkOnPlayback(
            enabled = true,
            isOnline = true,
            isRadio = false,
            songCoverArtId = "song-cover",
            albumCoverArtId = "album-cover",
            serverCoverLoadFailed = false,
            songMusicBrainzId = RecordingMbid,
            albumMusicBrainzId = null
        )
    )
}
```

Run: `.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.repositories.MusicBrainzArtworkRepositoryTest`

Expected: FAIL because `serverCoverLoadFailed` is not a parameter yet.

- [ ] **Step 2: Implement minimal policy**

Add `serverCoverLoadFailed: Boolean = false` to `shouldResolveMusicBrainzArtworkOnPlayback`, and change the cover gate to allow fallback when both server cover IDs are absent or when `serverCoverLoadFailed` is true.

- [ ] **Step 3: Add repository state**

Add a small in-memory failed-cover set keyed by song ID, plus:

```kotlin
fun reportServerCoverLoadFailed(songId: String)
```

`prefetchArtworkForPlayingSong` should pass `serverCoverLoadFailed = failedServerCoverSongIds.contains(song.id)` and should keep using the normal fingerprint/cache logic.

- [ ] **Step 4: Verify**

Run the same targeted test. Expected: PASS.

---

### Task 2: Report server cover load failures from Coil

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt`
- Modify: cover-art call sites that know the song ID, starting with Now Playing, MiniPlayer, Lyrics, and now-playing background surfaces.

- [ ] **Step 1: Add a failing compile target**

Add an optional `onServerCoverLoadFailed: (() -> Unit)? = null` parameter to intended call sites in tests or production call sites first so compilation fails until `CoverArt` supports it.

Run: `.\gradlew.bat :composeApp:testAndroidHostTest`

Expected: FAIL with unknown parameter.

- [ ] **Step 2: Implement callback**

In `CoverArt`, call `onServerCoverLoadFailed` only when:

- the request used server cover art, not an external `imageUrl`;
- Coil enters the `error` state.

- [ ] **Step 3: Wire current-song surfaces**

In the Now Playing artwork and Lyrics artwork paths, call:

```kotlin
musicBrainzArtworkRepository.reportServerCoverLoadFailed(song.id)
```

when the server cover fails.

- [ ] **Step 4: Verify**

Run: `.\gradlew.bat :composeApp:testAndroidHostTest`

Expected: PASS.

---

### Task 3: Move MusicBrainz setting into Integrations

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/IntegrationsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/DataStorageScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchResults.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Write failing settings-search test**

Extend existing settings search result tests to include a MusicBrainz integration row with id `integrations.musicbrainz`. If the current test harness cannot instantiate Compose string resources, add a pure helper in `SettingsSearchResults.kt` for the row id/path metadata and test that helper directly.

- [ ] **Step 2: Add Integrations row**

Add a `SettingSwitchRow` to `SettingsIntegrationsScreen`:

- title: `MusicBrainz and Cover Art Archive`
- subtitle: `Fetch public MusicBrainz metadata during playback and recover missing artwork from Cover Art Archive`
- value: `preferenceManager.musicBrainzArtworkFallbackEnabled`
- on change: update preference and call `musicBrainzArtworkRepository.refreshCacheVisibility()`

- [ ] **Step 3: Keep storage management in Data & Storage**

Remove or de-emphasize the enable toggle in `DataStorageScreen` if duplicated, but keep the cache summary and `Clear MusicBrainz cache`.

- [ ] **Step 4: Verify**

Run: `.\gradlew.bat :composeApp:testAndroidHostTest`

Expected: PASS.

---

### Task 4: LidaClips extra-screen video background toggles

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/LidaClipsExtraScreenBackgroundPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/LidaClipsExtraScreenBackgroundPolicyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/LidaClipsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchResults.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Write failing tests**

Test:

```kotlin
@Test
fun extraScreenVideoBackgroundRequiresSettingClipAndPlayback() {
    assertFalse(shouldShowLidaClipExtraScreenBackground(false, true, true, true))
    assertFalse(shouldShowLidaClipExtraScreenBackground(true, false, true, true))
    assertFalse(shouldShowLidaClipExtraScreenBackground(true, true, false, true))
    assertFalse(shouldShowLidaClipExtraScreenBackground(true, true, true, false))
    assertTrue(shouldShowLidaClipExtraScreenBackground(true, true, true, true))
}
```

Run: `.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.models.LidaClipsExtraScreenBackgroundPolicyTest`

Expected: FAIL because the policy does not exist.

- [ ] **Step 2: Implement policy**

Create:

```kotlin
fun shouldShowLidaClipExtraScreenBackground(
    settingEnabled: Boolean,
    lidaClipsEnabled: Boolean,
    hasClip: Boolean,
    musicIsPlaying: Boolean
): Boolean = settingEnabled && lidaClipsEnabled && hasClip && musicIsPlaying
```

- [ ] **Step 3: Add preferences and settings rows**

Add:

```kotlin
var lidaClipsLyricsVideoBackground by preference(false)
var lidaClipsMusicBrainzInfoVideoBackground by preference(false)
```

Add two switch rows in `SettingsLidaClipsScreen`, visible on Android when LidaClips is enabled.

- [ ] **Step 4: Verify**

Run: `.\gradlew.bat :composeApp:testAndroidHostTest`

Expected: PASS.

---

### Task 5: Lyrics-style MusicBrainz info screen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/musicBrainz/MusicBrainzInfoScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/Screen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add route and compile-failing entry**

Add `data object MusicBrainzInfo : Screen`, add a Now Playing action button using an Info-style icon, and add an `App.kt` entry that references `MusicBrainzInfoScreen(song)`.

Run: `.\gradlew.bat :composeApp:testAndroidHostTest`

Expected: FAIL until the screen exists.

- [ ] **Step 2: Implement the screen**

Base it on `LyricsScreen` structure:

- transparent bottom sheet metadata;
- `SheetScaffold` with top-left dismiss;
- top-right link action;
- `BlendBackground` or equivalent dynamic artwork background;
- optional cached LidaClips blurred background using the policy from Task 4;
- cover art near the top;
- `LazyColumn` with crisp metadata rows.

- [ ] **Step 3: Use cached metadata**

Read `musicBrainzArtworkRepository.metadataBySongId` and render existing `musicBrainzMetadataDisplayFields(metadata)`. Add a first row explaining source priority.

- [ ] **Step 4: Verify**

Run: `.\gradlew.bat :composeApp:testAndroidHostTest`

Expected: PASS.

---

### Task 6: Apply LidaClips video background to Lyrics and Info

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/lyrics/LyricsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/musicBrainz/MusicBrainzInfoScreen.kt`
- Reuse: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/LidaClipVideo.kt`

- [ ] **Step 1: Resolve a current-song cached clip**

Resolve the current song through `LidaClipsRepository.findClipForSong(song)` and `LidaClipCacheManager.getOrCacheClip(...)` inside the Lyrics/MusicBrainz screen state. Do not depend on `NowPlayingViewModel` internals, and do not show a video background unless the returned `DomainLidaClip.streamUrl` is a local cached file URI.

- [ ] **Step 2: Render optional background**

On both screens, when the corresponding setting is enabled and a cached clip exists, render a muted blurred crop video behind the crisp text. Keep artwork background visible until first frame is rendered.

- [ ] **Step 3: Verify**

Run: `.\gradlew.bat :composeApp:testAndroidHostTest`

Expected: PASS.

---

### Task 7: Documentation and release verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update README**

Document:

- MusicBrainz under Integrations.
- Cover Art Archive recovery for failed server covers.
- MusicBrainz info screen from Now Playing.
- LidaClips toggles for Lyrics and MusicBrainz info backgrounds.

- [ ] **Step 2: Full verification**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug --stacktrace
git diff --check
```

Expected: all exit 0.

- [ ] **Step 3: GitHub/release**

Commit implementation, push with `git push fork master`, create a GitHub release with `gh`, upload the release APK, and install only the release package `darkaxt.navic` if device installation is explicitly part of the current release step.

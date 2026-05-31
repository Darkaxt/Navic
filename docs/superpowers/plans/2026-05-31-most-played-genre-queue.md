# Most Played, Genre Pages, And Uncapped Lazy Queue

## Objective

Implement three connected playback-navigation features:

1. **Lazy Queue & No Cap**: keep full playback queues available, remove hidden queue caps as a performance workaround, and make the queue screen handle large queues through lazy rendering and stable item identity.
2. **Genre Page**: replace the current genre-to-album-filter shortcut with a native genre detail page containing metrics, playback/queue controls, artists, and albums.
3. **Most Played Shortcuts**: add a Library row of frequently played entities, ranked by effective playback duration for final playback launches only. It tracks Artist, Genre, Album, Playlist, and Station (`[A]` playlist) entries. It does not use click count, rating stars, cache/download stars, or browsing events.

Song-level Recent/Frequently played and Navidrome scrobbling remain unchanged.

## Current State

- `GenreListScreenCard` currently opens `Screen.AlbumList(... DomainAlbumListType.ByGenre(...))`, so genres are only album-list filters.
- `QueueScreen` already uses `LazyColumn`, but it still passes the full `playerState.queue` list and uses index keys for queue items.
- `MediaPlayer.shufflePlay()` calls `limitQueueShuffle(... preferenceManager.queueShuffleLimit)`.
- `queueShuffleLimit` defaults to `0` (unlimited), but the runtime still supports capped shuffles through playback settings.
- `PlayerUiState` has no playback-source/origin field.
- `ScrobbleManager` is intentionally song-only and should not be reused for entity shortcut ranking.
- Library content has rows for quick picks, recent albums, stations, playlists, artists, Aurral Discover, and genres. There is no entity-level Most played row.

## Design Decisions

- Ranking uses `totalPlayedMillis` only. Short accidental tests contribute little time rather than creating a persistent click shortcut.
- Credits are assigned to the entity that launched the final playback queue:
  - Artist play/shuffle credits that Artist.
  - Genre play/shuffle credits that Genre.
  - Album play/shuffle credits that Album.
  - Playlist play/shuffle credits that Playlist or Station when the playlist is an Aurral `[A]` station.
- Browsing, opening details, adding to queue, and play-next actions do not immediately add ranking credit.
- The tracker records elapsed playback time while the launched queue is actually playing. Paused time does not count.
- Genre pages derive their Artists and Albums from the local genre-album relation that already backs `DomainGenre`.
- Queue rendering remains lazy in Compose. This plan does not introduce a virtual Media3 queue, because the existing playback engine expects a complete queue list. It removes queue caps and makes the queue surface stable for large lists.

## Implementation Plan

### 1. Add Playback Origin Domain Models

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackOrigin.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackOriginTest.kt`

Add:

- `enum class PlaybackOriginType { Artist, Genre, Album, Playlist, Station }`
- `data class PlaybackOrigin(type, id, title, subtitle, coverArtId)`
- `val key: String = "${type.name}:$id"`
- Station detection helper for playlist names/metadata using the existing `[A]` station convention.

Tests:

- Stable keys are unique across entity types with the same id.
- `[A]` playlists map to `Station`; normal playlists map to `Playlist`.
- Blank or invalid ids are rejected before persistence.

### 2. Persist Entity Playback Duration

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/data/database/entities/PlaybackOriginEntity.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/data/database/dao/PlaybackOriginDao.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/data/database/mappers/PlaybackOriginMappers.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/DomainMostPlayedShortcut.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/PlaybackOriginRepository.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/di/DatabaseModule.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/di/RepositoryModule.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/data/database/CacheDatabase.kt`

Database:

- Add `PlaybackOriginEntity` to `CacheDatabase.entities`.
- Bump the Room database version.
- Register `PlaybackOriginDao`.
- Keep destructive fallback behavior consistent with the current project setup.

Entity fields:

- `originKey`
- `type`
- `itemId`
- `title`
- `subtitle`
- `coverArtId`
- `totalPlayedMillis`
- `lastPlayedAt`

DAO behavior:

- `observeMostPlayed(limit: Int): Flow<List<PlaybackOriginEntity>>`
- Upsert origin metadata.
- Increment `totalPlayedMillis` only when duration is positive.
- Update `lastPlayedAt` when duration is credited.

Tests:

- Repository ignores zero/negative duration.
- Repository accumulates duration for the same origin.
- `observeMostPlayed` sorts by descending duration, then recent activity.

### 3. Track Effective Playback Time For Origins

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PlaybackOriginTracker.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/PlaybackOriginTrackerTest.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/shared/MediaPlayer.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt`
- iOS/non-Android media player implementations if they implement `MediaPlayer`

Add tracker API:

- `setOrigin(origin: PlaybackOrigin?)`
- `onPlaybackState(isPlaying: Boolean, nowMillis: Long)`
- `flush(nowMillis: Long)`

Rules:

- Time accrues only while `isPlaying == true`.
- Changing origin flushes the previous origin first.
- Clearing the queue flushes and clears origin.
- Manual queue playback without an origin does not create shortcut credit.

Wire the Android player:

- Inject `PlaybackOriginRepository` or a tracker that owns it.
- Add `MediaPlayer.setPlaybackOrigin(origin: PlaybackOrigin?)`.
- Call tracker state changes from existing playback state/progress update paths.
- Flush on media player teardown and queue clear.

Tests:

- Paused time does not accrue.
- Origin switch credits old origin and starts new origin.
- Clearing origin stops credit.

### 4. Remove Queue Cap Runtime Behavior

Files:

- `composeApp/src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/QueueShufflePolicy.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/models/QueueShufflePolicyTest.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/PlaybackScreen.kt`
- `README.md`

Changes:

- Stop applying `limitQueueShuffle()` in `shufflePlay()`.
- Remove the Playback setting that presents queue shuffle caps as a runtime behavior.
- Keep the stored preference key only if needed for backward-compatible settings reads; it must no longer affect playback.
- Delete `QueueShufflePolicy` and its tests if there are no remaining callers.
- Update README to state queues are uncapped and the queue UI is lazy-rendered.

Verification:

- Search for `queueShuffleLimit` and `limitQueueShuffle` after edits. No playback path should use them.

### 5. Make Queue Rendering Stable For Large Queues

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/queue/QueueScreen.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/queue/QueueItemKeyTest.kt` or a small domain policy test if UI tests are not practical.

Changes:

- Replace index-only lazy keys with a stable queue item key that includes song identity and occurrence index for duplicates.
- Keep drag/drop behavior based on visible indexes.
- Avoid expensive recomputation in composition by using memoized queue metadata helpers.
- Preserve `currentIndex`, upcoming indexes, and remove/move actions.

Tests:

- Duplicate songs in a queue produce distinct stable keys.
- Reordering keeps keys tied to item identity plus occurrence, not only list position.

### 6. Add Genre Detail Data And Actions

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/viewmodels/GenreDetailViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/GenreDetailScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/components/GenreDetailHeader.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/components/GenreMetrics.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/components/GenreQueueActions.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/components/GenreArtistsSection.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/components/GenreAlbumsSection.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/GenreRepository.kt`

View model state:

- Genre name.
- Album count.
- Song count.
- Artist count.
- Total duration where available from album/song metadata.
- Albums.
- Artists derived from albums.
- Full playable song collection for genre playback actions.

Actions:

- `Play`: clears queue, sets `PlaybackOrigin(Genre, genreName, ...)`, enqueues all genre songs, starts playback.
- `Shuffle`: same origin, full genre song list, shuffled with no cap.
- `Play next`: queues genre songs after current playback without immediate Most played credit.
- `Add to queue`: appends genre songs without immediate Most played credit.

Tests:

- Genre artists are deduplicated by artist id/name.
- Genre collection contains all playable songs from genre albums.
- Play/shuffle actions use Genre origin.

### 7. Route Genre Cards To The Native Genre Page

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/Screen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/App.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/components/Card.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/components/Content.kt`

Changes:

- Add `Screen.GenreDetail(genreName: String)`.
- Register the destination in app navigation.
- Update genre cards in the genre list and Library genre row to open `GenreDetail`.
- Keep the old album-list-by-genre type only for places that still explicitly need album filtering.

Verification:

- Genre list card opens a genre detail screen.
- Library genre row card opens the same detail screen.

### 8. Add Library Most Played Shortcuts Row

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/components/Content.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/viewmodels/MostPlayedShortcutsViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/components/MostPlayedShortcutCard.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/Screen.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

Row behavior:

- Title: `Most played`.
- Visible only when the repository returns at least one shortcut.
- Sort order is repository order: highest `totalPlayedMillis`, then most recent.
- Card tap routes to the native entity destination:
  - Artist -> `Screen.ArtistDetail`
  - Genre -> `Screen.GenreDetail`
  - Album -> `Screen.CollectionDetail`
  - Playlist -> `Screen.CollectionDetail`
  - Station -> existing station/playlist destination used for Aurral `[A]` playback objects

Card content:

- Cover image when available.
- Title.
- Entity type label.
- Human-readable played duration.

Tests:

- Mapper converts persisted origin entities to shortcut route models.
- Station shortcuts route as stations, not normal albums.

### 9. Attribute Existing Playback Entry Points

Files to inspect and update:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/CollectionDetailViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/playlist/PlaylistListViewModel.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/stations/*`
- Any detail screen that clears queue and starts an album/playlist/station/artist.

Changes:

- Before direct Play or Shuffle actions, set the corresponding playback origin.
- Do not set origin for details opening, browsing, add-to-queue, or play-next.
- Clear origin for ad hoc radio/song-radio playback unless it has a specific tracked Station origin.

Verification:

- Artist direct play credits Artist.
- Album direct play credits Album.
- Playlist direct play credits Playlist.
- Aurral `[A]` station direct play credits Station.
- Manual queue operations do not immediately create Most played shortcuts.

### 10. README And Release Notes

Files:

- `README.md`

Document:

- Native Genre pages with metrics, artists, albums, and playback controls.
- Most played entity shortcuts based on effective playback duration.
- Uncapped queue behavior and lazy queue rendering.
- Song scrobbling remains Navidrome/Subsonic song-level behavior.

### 11. Verification Commands

Run after implementation:

```powershell
./gradlew :composeApp:testAndroidHostTest
./gradlew :androidApp:assembleDebug
git diff --check
rg "queueShuffleLimit|limitQueueShuffle" composeApp README.md
```

Expected `rg` result after removing the cap:

- No playback usage.
- Either no matches, or only a backward-compatible stored preference field with no UI or runtime effect.

## Completion Criteria

- Genre cards open a native Genre page.
- Genre page exposes metrics, artists, albums, and full-genre playback controls.
- Genre Play/Shuffle use all playable genre songs with no cap.
- Queue screen remains responsive for large queues through lazy rendering and stable keys.
- Most played shortcuts appear on Library after effective playback time is accrued.
- Most played shortcuts route to native Artist, Genre, Album, Playlist, and Station destinations.
- No Most played credit is created by browsing, opening details, cache/download stars, ratings, add-to-queue, or quick clicks without playback.
- README reflects the new behavior.
- Focused tests and Android debug build pass.

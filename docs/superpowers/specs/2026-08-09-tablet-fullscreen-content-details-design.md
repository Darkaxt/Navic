# Tablet Fullscreen Content Details Design

## Problem

On a wide Android tablet, Navic currently combines the active Library destination with collection, song, and Aurral destinations through Navigation3's `ListDetailSceneStrategy`. Both destinations are complete application screens: each owns top-level content, a mini-player, and bottom navigation. The resulting scene shows two app shells at once, constrains Library to a narrow clipped pane, and duplicates the player and navigation chrome.

ADB evidence from the Samsung SM-X910 confirms that Android is running one fullscreen Navic activity. The split is produced inside Navic and is not Samsung multi-window behavior.

## Required Behavior

1. Opening a collection, song, or Aurral content destination replaces the previous root screen and uses the full available tablet content dimensions.
2. The previous Library destination remains in the navigation back stack but is not composed beside the active detail destination.
3. Exactly one mini-player and one bottom navigation bar are visible.
4. Back navigation restores the previous Library state.
5. Phone behavior remains a single-screen navigation flow.
6. Settings retains its existing `listPane("settings")` / `detailPane("settings")` adaptive layout because its list and detail screens are designed as one settings workflow.
7. Playlist display surfaces hide stale playlists when both their declared `songCount` and locally loaded song list are empty.

## Design

Remove `detailPane("root")` metadata from these root content destinations in `App.kt`:

- `AurralHub`
- `AurralDiscoverList`
- `AurralDiscoverCollection`
- `AurralDiscoverTag`
- `AurralArtist`
- `AurralMissingAlbum`
- `CollectionDetail`
- `SongDetail`

Without root detail metadata, Navigation3 uses its normal single-scene presentation for the latest destination. Root tab destinations may retain their existing metadata and transitions; they have no matching root detail pane after this change. Settings metadata remains unchanged.

## Empty Playlist Presentation

Define one domain display-policy predicate that considers a playlist visible when `songCount > 0` or `songs.isNotEmpty()`. Apply that predicate inside the existing station, mood mix, genre mix, and user-playlist grouping functions. This keeps Library rows and Playlist screens consistent without deleting data or filtering the playlist picker used to add songs.

## Non-Goals

- Do not redesign pane widths or breakpoints.
- Do not centralize all screen scaffolds in this patch.
- Do not change collection loading, missing-song behavior, Aurral requests, playback, or queue behavior.
- Do not delete empty playlists or hide them from add-to-playlist dialogs.
- Do not change iOS-specific code.

## Validation

- A host source-contract test must fail while any `detailPane("root")` metadata remains and must enumerate every content destination as a metadata-free entry.
- Focused navigation host tests must pass.
- Playlist policy tests must cover empty metadata, declared entries, and locally loaded songs with stale zero metadata.
- The Android APK must compile and pass vendor/attribution checks.
- On the SM-X910 in landscape, opening a Library collection must produce one full-width detail screen, one mini-player, and one bottom bar.
- Pressing Back must restore the Library screen.

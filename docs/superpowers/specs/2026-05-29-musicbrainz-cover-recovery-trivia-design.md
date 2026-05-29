# MusicBrainz Cover Recovery And Trivia Design

## Goal

Improve the existing MusicBrainz / Cover Art Archive integration so it is useful in daily playback: recover blank covers, move the feature into Integrations, and add a lyrics-style Now Playing info screen for MusicBrainz metadata and future trivia.

## Scope

This phase includes:

- MusicBrainz / Cover Art Archive cover recovery for songs whose Navidrome/Subsonic cover is missing or fails to load.
- MusicBrainz settings placement under Settings -> Integrations.
- Diagnostics for why external cover recovery did or did not run.
- A new lyrics-style MusicBrainz info screen opened from Now Playing by a dedicated action beside Lyrics.
- LidaClips settings that can enable cached blurred video backgrounds independently for Lyrics and for the MusicBrainz info screen.

This phase does not include:

- Scraping Songfacts text into the app.
- Discogs API integration.
- Shipping MusicBrainz OAuth credentials.
- Whole-library MusicBrainz crawling.
- Replacing Navidrome/Subsonic metadata as the canonical source.

## Source Priority

Navidrome/Subsonic remains the first source for track metadata and artwork. MusicBrainz and Cover Art Archive are enrichment and recovery sources only.

Artwork priority:

1. Song cover from Navidrome/Subsonic, if present and loadable.
2. Album cover from Navidrome/Subsonic, if present and loadable.
3. Cover Art Archive fallback, only when enabled and MusicBrainz can identify the track or album with acceptable confidence.

Metadata priority:

1. Navidrome/Subsonic track fields.
2. MusicBrainz recording/release/release-group fields and safe external links.
3. Future trusted trivia sources, if added later.

## Cover Recovery

The current implementation only tries Cover Art Archive when the song and synced album appear to have no cover IDs. That misses the practical blank-cover case where Navidrome has a cover ID but Coil or the server returns an error, empty image, or otherwise fails to render.

The new behavior should let UI artwork loaders report a server-cover load failure for the current song. Once reported, MusicBrainz cover recovery may run for that song even if the original song cover ID was nonblank. The app must not eagerly crawl the whole library or repeatedly hammer external services.

Recovery constraints:

- Only resolve on playback or when viewing the current song's Now Playing surfaces.
- Respect the existing MusicBrainz enabled setting.
- Respect offline/network availability.
- Keep the existing MusicBrainz request throttle.
- Cache successful and missing results.
- Preserve existing custom server headers and normal server artwork loading.
- Skip synthetic `[Unknown Artist]` title/artist fallback searches unless a valid MBID is available.

Diagnostics should explain the current reason when fallback does not run, such as setting disabled, offline, server cover already loaded, cached miss, unknown artist fallback skipped, no reliable identifier, or external request failed.

## Settings

MusicBrainz should appear under Settings -> Integrations as "MusicBrainz and Cover Art Archive". The setting remains off by default unless the existing app state already has it enabled.

The Integrations screen should provide:

- Toggle: MusicBrainz and Cover Art Archive.
- Short explanation: fetch public MusicBrainz metadata during playback and recover missing artwork from Cover Art Archive.
- Cache summary or a row linking to cache management.
- Optional diagnostics/readout for last cover recovery reason if that is simple to expose.

Data & Storage can keep the cache readout and clear-cache action, because those are storage-management operations. Search results should find both the integration setting and the cache management rows.

## MusicBrainz Info Screen

Add a dedicated Now Playing action beside Lyrics for MusicBrainz / Info. This action opens a new full-screen surface modeled on the existing Lyrics screen:

- Top-left dismiss button.
- Top-right action for source links or sharing/opening the canonical MusicBrainz page.
- Dynamic blurred artwork background like Lyrics.
- Compact cover image near the top.
- Large, crisp, vertically scrollable metadata rows where lyric lines would normally appear.
- No lyric-style text blur on metadata rows.

Initial rows should use already available cached MusicBrainz data:

- Source priority, explicitly showing that Navidrome/Subsonic remains first.
- Recording title / artist credit when available.
- Release and release-group information.
- First release date or selected release date.
- Genres/tags/ISRCs when available.
- Cover fallback status and match confidence when available.
- Safe external links such as MusicBrainz, Discogs, Songfacts, Wikipedia, and Wikidata when MusicBrainz exposes them.

If metadata has not been resolved yet, the screen should show a clear loading or unavailable state and may trigger a current-song-only lookup if that does not violate the playback-only constraint. It should not start a whole-library scan.

## LidaClips Background Reuse

Add LidaClips settings to control whether cached blurred video backgrounds may appear on secondary full-screen playback surfaces:

- Use video background in Lyrics.
- Use video background in MusicBrainz info.

Both should be independent toggles. Video background should only be used when:

- LidaClips is enabled and configured.
- A matching clip exists for the current song.
- The clip is cached or otherwise ready according to the same no-black-screen rule used in Now Playing.
- The first frame is ready before replacing the artwork-derived background.

If any condition fails, the screen silently uses the normal artwork-derived background. Text must remain readable, with a stronger dark scrim for the MusicBrainz info screen than for Now Playing if needed.

## UX Decisions

The artwork flip interaction is deferred. It is visually interesting, but it overloads the existing artwork tap setting and competes with Lyrics, Track Info, swipe-to-skip, and LidaClips foreground video state.

The first implementation should use the dedicated Now Playing action and lyrics-style full-screen info screen. The artwork flip can be reconsidered later after the info screen proves useful.

## Testing

Unit tests should cover:

- Cover recovery policy when server covers are missing, present, or reported failed.
- MusicBrainz fallback gating for settings, offline state, radio tracks, synthetic unknown artist, valid MBIDs, and cache status.
- LidaClips background eligibility for Lyrics and MusicBrainz info surfaces.
- Settings search entries for MusicBrainz and LidaClips background toggles.

Build verification should include:

- `.\gradlew.bat :composeApp:testAndroidHostTest`
- `.\gradlew.bat :androidApp:assembleDebug --stacktrace`
- `git diff --check`

Manual smoke tests should include:

- A song with normal Navidrome artwork.
- A song with a blank or failing Navidrome cover.
- A song with no cover IDs but identifiable MusicBrainz metadata.
- Lyrics screen with video background toggle on/off.
- MusicBrainz info screen with video background toggle on/off.

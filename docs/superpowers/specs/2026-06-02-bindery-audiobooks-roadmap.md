# Bindery Audiobooks Integration Roadmap

Date: 2026-06-02

Status: brainstorming roadmap, not an implementation plan.

## Current Bindery Contract

Bindery exposes the fork-only OPDS 2 API at `/opds`.

Confirmed capabilities:

- API-key authentication for every OPDS request via `X-Api-Key`, `Authorization: Bearer`, or `?apikey=`.
- Catalog routes for root, books, search, recent, wanted, authors, series, languages, and formats.
- Audiobook format route: `/opds/formats/audiobook`.
- Paginated catalog responses using `limit`, `offset`, and `next` links.
- Publication detail and Readium audiobook manifest routes.
- Addressable audio resources in `readingOrder`.
- Direct `GET|HEAD /opds/books/{id}/resources/{resourceKey}` resource serving with Range-capable HTTP behavior.
- Resource metadata: `resourceKey`, `relativePath`, track/disc numbers, size, duration when known, origin, and delivery policy.
- Stable resource keys across `book_files` row rebuilds.
- Server-side progress stored per `(book, alias)`.
- Progress alias can be supplied by `X-Bindery-Progress-Alias`, `?alias=`, or JSON body `alias`.
- OPDS prewarm support exists in Bindery so first client access does not need to synchronously walk every audiobook folder.

Navic should use a stable alias derived from the Navidrome user identity, such as:

```text
X-Bindery-Progress-Alias: navidrome:<userId>
```

## Product Direction

Bindery audiobooks are a separate media domain inside Navic.

Confirmed decisions:

- Audiobooks must not be mixed into the existing music Albums, Artists, or Playlists surfaces.
- Library should include a custom Audiobooks row backed by Bindery OPDS.
- The bottom toolbar should have an Audiobooks category.
- Audiobook cards may use album-like visual treatment internally, but they are book entities, not music albums.

## Navigation Model

Use contextual bottom-toolbar profiles.

Compact/root profile:

```text
Library / Audiobooks / Activity
```

Music profile:

```text
Library / Albums / Playlists / Artists / Audiobooks / Activity
```

Audiobook profile:

```text
Library / Audiobooks / Books / Collections / Authors / Activity
```

Confirmed behavior:

- Tapping `Library` switches to the music/library-oriented profile.
- Tapping `Audiobooks` switches to the audiobook-oriented profile.
- Navic should remember the last active mode/profile after restart.
- The remembered mode should align with the app's existing last-played behavior.
- With no prior mode, Navic can fall back to the compact/root profile.

## Open Design Questions

The next major decision is playback UX:

- Option A: Reuse the current Now Playing screen and add audiobook-specific controls.
- Option B: Create a dedicated long-form audiobook player screen.

Current recommendation:

- Prefer a dedicated long-form player. Audiobook resume, chapter navigation, progress conflict handling, bookmarks, sleep timer, and long-duration ergonomics will diverge from music quickly.
- Build it from the existing Now Playing architecture rather than from scratch, so artwork, media-session plumbing, theming, and playback state handling stay robust.

This still needs explicit approval before implementation planning.

## Audiobook Player Direction

Confirmed player design decisions:

- Use a Now Playing-derived audiobook mode, not a blank new player implementation.
- Keep the visual language close to the current Now Playing screen, but make it long-form-first.
- Make the cover artwork smaller than the music player artwork to leave room for chapter and resume context.
- Remove music-only actions from the audiobook player:
  - Shuffle.
  - Repeat/loop.
  - Lyrics.
  - LidaClips/music-video actions.
- Replace `Up next` with a chapter explorer backed by the OPDS `readingOrder`.
- Use audiobook-oriented transport controls:
  - Previous chapter.
  - Play/pause.
  - Next chapter.
  - Configurable skip-back/skip-forward controls.
- Seek controls should be configurable in Settings. The user can choose which skip ranges are shown at the same time, and Navic renders those ranges as ordered visible actions in the audiobook UI.
- The first-pass Info action should render Bindery/OpenLibrary metadata first. Other sources such as Google Books, Audible public data, Goodreads-like data, Hardcover, or `pennydreadful/bookshelf` are Navic-side enrichment candidates and must not be required for playback.
- The ebook-related action should be a bridge to ebook candidates or another client for the first pass, not a full ebook reader integration.

Artwork treatment direction:

- Replace the spinning vinyl metaphor with an audiobook/book treatment.
- Do not rely on runtime SVG image loading for this player artwork, because Android SVG image-loader behavior has already caused crashes.
- Prefer a Compose/vector frame that renders the real cover art into an audiobook/book shape.
- Candidate visual metaphors include an open audiobook, audio-book icon, or monocolor audiobook glyph treatment.

## Integration Phases

### Phase 1: Capability And Settings

- Update Bindery service status to detect and show:
  - OPDS reachable.
  - Audiobooks available.
  - Search available.
  - Authors available.
  - Series available.
  - Pagination supported.
  - Alias-based progress sync supported.
  - Prewarm status available if using authenticated `/api/v1/opds/prewarm/status`.
- Keep integration health in Settings, not Activity.
- Keep disabling Bindery separate from cache deletion.

### Phase 2: OPDS Client Model

- Extend `BinderyRepository` beyond root/catalog/manifest:
  - Fetch paginated catalogs and follow `next` links lazily.
  - Fetch book detail.
  - Fetch manifest.
  - Fetch resources if needed separately.
  - Fetch and update progress using the Navidrome-derived alias.
  - Build absolute resource, cover, and progress endpoints from relative OPDS links.
- Preserve local progress/cache for offline use and conflict resolution.

### Phase 3: Audiobook Surfaces

- Add Library Audiobooks row from `/opds/formats/audiobook`.
- Add Audiobooks hub screen.
- Add Books screen.
- Add Collections screen using OPDS series first.
- Add Authors screen using OPDS authors.
- Keep labels generic and first-party: avoid unnecessary "Bindery" branding in normal content rows.

Implementation checkpoint, 2026-06-02:

- First-pass surfaces are implemented for the Library Audiobooks row, Audiobooks hub, Books, Collections, Authors, and generic drilled-down OPDS catalogs.
- Contextual bottom-bar profiles are implemented and persisted:
  - Compact: `Library / Audiobooks / Activity`.
  - Music: `Library / Albums / Playlists / Artists / Audiobooks / Activity`.
  - Audiobooks: `Library / Audiobooks / Books / Collections / Authors / Activity`.
- The row and screens use OPDS publications for books/audiobooks, OPDS navigation links for authors/series collections, and authenticated cover requests via the configured Bindery API key.
- Book detail, playback, resume/progress conflict handling, and audiobook-specific Now Playing remain Phase 4 work.

### Phase 4: Playback And Resume

- Treat a Bindery book as an album-like playback collection.
- Treat `readingOrder` items as chapters/tracks.
- On play:
  - Fetch manifest.
  - Fetch alias-scoped server progress.
  - Compare with local progress.
  - Resume from the best/conflict-resolved source.
- During playback:
  - Persist local progress frequently.
  - Push server progress periodically and on pause/stop/track change.
  - Include `resourceKey`, `positionMs`, `durationMs`, and alias.
- On completion:
  - Mark progress completed when the final resource is effectively complete.

### Phase 5: Queue And Activity

- Audiobook queue should be separate from music queue semantics.
- Activity should show audiobook playback/download/cache activity only if there is an actual queue or useful user action.
- Bindery service health belongs in Settings, not Activity.
- If audiobook offline caching is added, it should use clear row controls for retry/cancel/discard, not service-health summaries.

### Phase 6: Testing

Focused tests should cover:

- URL normalization and auth headers.
- Progress alias precedence and request headers.
- Catalog pagination and `next` link parsing.
- Manifest parsing with `resourceKey`, duration, size, and chapter order.
- Resume-source conflict policy.
- Toolbar profile persistence and switching.
- Library row visibility when Bindery is configured/unconfigured/down.
- No leakage of audiobook entities into music Albums/Artists/Playlists.

## Non-Goals For First Pass

- Do not integrate ebooks.
- Do not mix books into music album search/results.
- Do not build a complex recommendation system for audiobooks.
- Do not depend on OPDS 1.2.
- Do not require Google Drive-specific client behavior; Bindery URLs remain canonical.

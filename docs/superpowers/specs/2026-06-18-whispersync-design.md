# Whispersync Reader Integration Design

## Reference Authority

This spec extends `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`.

- Komikku remains authoritative for reader shell, overlays, tap ownership, progress rail behavior, and controller-first UI.
- Anx Reader/Foliate remains authoritative for EPUB/PDF rendering, visible text range, locators, annotations, highlights, and bridge event semantics.
- Bindery remains authoritative for Whispersync artifact discovery, sidecar URLs, audiobook identities, coverage, score, and ASR alignment payloads.

Whispersync must not replace the Komikku shell or bypass the Anx/Foliate behavior boundary. It consumes Bindery sidecars and feeds tested timeline state into the existing reader/audio controller path.

## Goal

Open an ebook with a selected compatible audiobook, fetch or receive the Bindery Whispersync sidecar, parse it into a deterministic audio/text timeline, and expose enough pure-domain behavior to later synchronize playback, visible text, highlights, and progress without destabilizing the reader shell.

## Current State

- Bindery rows already expose ready Whispersync matches and can route an ebook reader session with:
  - `whispersyncSidecarUrl`
  - `whispersyncArtifactId`
  - `whispersyncAudiobookId`
  - `whispersyncAudiobookBookFileId`
  - `whispersyncAudiobookTitle`
- Reader progress can already create companion progress for selected audiobook metadata.
- Storyteller/readaloud media-overlay models already provide reusable audio/text timeline ideas, but Bindery ASR sidecars are a separate artifact class and must keep their own model.

## Implementation Status As Of 2026-06-20

Implemented and covered by source/tests:

- Bindery sidecar parsing and tolerant timeline models.
- Audio position to text overlay query model.
- Visible text range to audio seek target query model.
- Bindery repository sidecar fetch through the metadata cache.
- Reader route attachment extraction from `Screen.Reader`.
- Reader startup sidecar fetch and `ReaderCoordinator.loadWhispersyncSidecar(...)`.
- Foliate visible text range bridge event from rendered documents.
- Controller/coordinator propagation of `WhispersyncAudioSeekTarget`.
- Reader-side audiobook manifest loading for paired Whispersync routes.
- Visible text range seek targets consumed by `ReaderScreen` through `AudiobookPlaybackManager`.
- Seek target to audiobook track matching, including relative sidecar resources against absolute Bindery playback URLs.

Important correction to older audits:

- `BinderyWhispersyncMatchesSheet.onOpenSidecar` is still intentionally inert, but it is not the active launch path for paired reading. `BinderyWhispersyncLaunchSheet` builds a `Screen.Reader` with sidecar and audiobook route metadata, and `ReaderScreen` consumes that route to load the sidecar.

Still missing:

- Playback position from the audiobook player is not fed back into `ReaderWhispersyncSyncCoordinator.onAudiobookPlaybackPosition(...)` for live text highlighting.
- No controller-owned sync status or mismatch repair UI exists yet.
- No release APK claim should be made for end-to-end Whispersync playback until the reader-to-audio seek path and audio-to-reader highlight path are device-validated together.

## Non-Negotiable Guardrails

1. Do not implement Whispersync by adding more UI-only badges or type-only bridge events.
2. Do not wire live synchronization until the timeline parser/model is covered by tests.
3. Do not make Whispersync depend on page numbers alone. It must prefer text ranges, CFI, href, audio resource, and millisecond positions.
4. Do not let normal short taps reach ebook interactive content just to support sync. Native tap ownership remains the Komikku controller contract; long press is the content interaction path.
5. Do not call the feature complete until a release APK validates the reader shell gates that sync depends on: resume persistence, chapter rail endpoints, cover chrome layering, and visible-range reporting.

## Sidecar Model

The parser must be tolerant of Bindery payload variants because the service side is still evolving. It should accept the following semantic fields when present:

- artifact identity: `artifactId`
- ebook identity: `ebookBookFileId`, `ebook.bookFileId`, `ebook.id`
- audiobook identity: `audiobookBookFileId`, `audiobook.bookFileId`, `audiobook.id`
- audio resources: `audioResource`, `audioHref`, `audio.href`, `audiobook.resources[].href`
- text resources: `textHref`, `textResource`, `href`, `ebook.href`
- text range: `textStart`, `textEnd`, `documentTextLength`
- locators: `cfi`, `rangeCfi`, `fragmentId`
- audio range: `startMs`/`endMs`, `audioStartMs`/`audioEndMs`, or seconds variants
- labels: `label`, `chapterLabel`, `sectionLabel`

Missing `documentTextLength` must not make the sidecar unusable. The timeline should fall back to segment-local text ranges and later visible-range bridge data.

## Foundation Implementation Order

1. Restore this spec and reference it from future Whispersync work.
2. Add tests for sidecar parsing and timeline lookup before production code.
3. Implement pure commonMain models:
   - `WhispersyncSidecar`
   - `WhispersyncSegment`
   - `WhispersyncTimeline`
   - parser entry point for JSON strings.
4. Add timeline queries:
   - active segment for audiobook resource plus playback position.
   - seek target for ebook href plus visible character range.
   - overlay fragment projection for existing reader highlight commands.
5. Only after the model is stable, add repository fetch and reader/audio coordinator wiring.

## Deferred Until Reader Shell Gates Pass

- Live playback-to-text highlight loop.
- Release-device validation of visible range reporting and automatic audio seek when turning pages.
- UI for sync status or mismatch repair.
- Release claim that Whispersync is usable end to end.

## Acceptance Criteria For This Slice

- A common test proves a Bindery-style JSON sidecar parses into stable segments.
- A common test proves active audio position resolves the expected segment.
- A common test proves visible ebook character range resolves the best audio seek target.
- A common test proves parser accepts `documentTextLength` absence without discarding segments.
- The implementation uses no Android APIs and does not touch reader shell UI.

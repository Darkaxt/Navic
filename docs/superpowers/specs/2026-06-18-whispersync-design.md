# Whispersync Reader Integration Design

## Reference Authority

This spec extends `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`.
Active staged execution is tracked in `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`.

- Komikku remains authoritative for reader shell, overlays, tap ownership, progress rail behavior, and controller-first UI.
- Anx Reader/Foliate remains authoritative for EPUB/PDF rendering, visible text range, locators, annotations, highlights, and bridge event semantics.
- Bindery remains authoritative for Whispersync artifact discovery, sidecar URLs, audiobook identities, coverage, score, ASR alignment payloads, and optional generated fullscreen cover assets exposed through OPDS/API metadata.
- Bindery-generated fullscreen cover URLs may require the same API-key headers as resource, sidecar, progress, and image proxy calls. Navic must therefore fetch those generated cover assets through the authenticated Bindery resource path and cache them into the reader-local asset-loader surface before passing them to the native Komikku cover renderer.
- Generated reader-shell covers are a Bindery server concern, not a Navic runtime generation concern. Navic expects explicit generated cover assets or variants with stable source-cover hash / generator-version identity, chooses the closest aspect for the reader surface, and falls back to the EPUB/native cover when no generated asset is present.

Whispersync must not replace the Komikku shell or bypass the Anx/Foliate behavior boundary. It consumes Bindery sidecars and feeds tested timeline state into the existing reader/audio controller path.

Diagnostic-only ASR matching references are available if a production sidecar is ambiguous or incomplete:

- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\hobbit-sync-poc`
- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\whispersync-coverage-check`
- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\hobbit-sync-poc\sync_poc.py`
- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\hobbit-sync-poc\test_sync_poc.py`
- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\whispersync-coverage-check\anchor_gap_align.py`
- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\whispersync-coverage-check\merge_corrupt_windows_from_report.py`
- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\hobbit-sync-poc\work\hobbit-ch1-full-tiny-side-by-side-report.html`
- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\whispersync-coverage-check\alcatraz-ebook571-full-merged-window-report.html`
- `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\.codex-temp\whispersync-coverage-check\alcatraz-ebook571-full-anchor-gap-report.html`

These POCs are not a runtime dependency and must not override a valid Bindery JSON sidecar. They are only evidence for debugging matching semantics when the sidecar contract itself is suspect.

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

## Implementation Status As Of 2026-06-21

Implemented and covered by source/tests:

- Bindery sidecar parsing and tolerant timeline models.
- Audio position to text overlay query model.
- Visible text range to audio seek target query model.
- Bindery repository sidecar fetch through the metadata cache.
- Reader route attachment extraction from `Screen.Reader`.
- Row-aware Bindery Whispersync match launch for both ebook rows and audiobook rows, with no raw/inert sidecar action in the visible match sheet.
- Reader startup sidecar fetch and `ReaderCoordinator.loadWhispersyncSidecar(...)`.
- Foliate visible text range bridge event from rendered documents.
- Controller/coordinator propagation of `WhispersyncAudioSeekTarget`.
- Reader-side audiobook manifest loading for paired Whispersync routes.
- Visible text range seek targets consumed by `ReaderScreen` through `AudiobookPlaybackManager`.
- Seek target to audiobook track matching, including relative sidecar resources against absolute Bindery playback URLs. Explicit sidecar `audioTrackIndex` is authoritative over stale resource-name matches when it is present and valid.
- Audiobook playback state fed back into `ReaderController.onReadaloudPlaybackState(...)` for Whispersync overlay/highlight commands.
- Playback-position to text overlay matching through `ReaderWhispersyncSyncCoordinator.onAudiobookPlaybackPosition(...)`. When both playback state and sidecar segment provide track identity, track-index disagreement blocks a stale resource match from activating the wrong text segment.
- Character-offset ASR cues now survive `WhispersyncSegment -> ReaderOverlayFragment -> ReaderBridgeCommand.ApplyOverlayFragment -> overlayFragmentActive` and the Foliate runtime can mark a raw text-node range when a sidecar segment has no EPUB fragment id.
- Controller-owned Whispersync status state for ready, page-to-audio seek, audiobook playback, paused sync, and mismatch states.
- Native Komikku overlay route for Whispersync mismatch status so the sync path is not silently failing.
- One-tap mismatch repair routed through `ReaderCoordinator.repairWhispersyncMismatch()`, reusing the current visible text range to reapply the correct overlay and dispatch the existing audiobook seek target path.
- Reader progress companion state now persists exact Whispersync audio resource, sidecar track index, and millisecond position when the controller has a sidecar-derived seek target, so audiobook resume can prefer the precise segment target over a total-duration fraction estimate.
- Audiobook resume now compares direct audiobook progress against Whispersync companion progress by `updatedAtMs`; a newer ebook-derived sidecar target can resume the audiobook instead of being hidden behind stale direct audiobook progress.
- Paired reader sessions now use the same newest direct-or-companion resume policy when preparing their Whispersync audiobook playback plan, so opening the ebook side cannot silently fall back to stale direct audiobook progress.
- Whispersync sidecar and paired-audiobook manifest failures now surface through controller-owned native status instead of only logs; load failures are attention states but not repairable mismatch states.
- Whispersync sidecar attachment now replays any visible text range already emitted by Foliate before the sidecar fetch completed, so page-to-audio seek does not depend on sidecar load winning a startup race.
- Clean readerdev emulator validation for production book `3809` now proves a cue-covered page persists exact companion progress with sidecar track identity: `OEBPS/xhtml/Authorforeword.xhtml` visible range `3-4923` resolved to `263360ms`, dispatched `applyOverlayFragment`, received `overlayFragmentActive`, and stored `audioTrackIndex: 0` in `binderyWhispersyncCompanionProgressJson`.
- A no-build/no-install paired readerdev route reopen with preserved data used that companion progress to load the audiobook plan at `startTrack=0 startPositionMs=263360`, then re-applied the same visible-range overlay for `OEBPS/xhtml/Authorforeword.xhtml`.
- Controller-owned top-left Whispersync playback control policy now derives hidden/loading/play/pause/crossed states from the reader Whispersync status and paired audiobook playback state, and routes taps through the audiobook playback manager rather than the WebView.
- The top-left Whispersync playback control is page-scoped: a sidecar-only `Ready` state is hidden, cue-covered pages use `SeekingAudio` as the loading state while audio is unavailable, and moving to an unsupported visible range demotes the session back to `Ready` and clears the media overlay.
- The page-scoped Whispersync control is intentionally rendered as a dim `22.dp` headset glyph at `0.42f` opacity on the paper layer with a transparent `48.dp` tap target, not as a circular Material badge, pill, or progress-ring chrome element.
- Readerdev emulator validation for production book `3809` proves the top-left control starts the paired readaloud session at the sidecar target `263360ms` and pauses it on the next tap; the control also follows the Komikku chrome touch-shield pattern so visible disabled/loading states do not leak taps into page navigation.
- Audio-position-driven reader navigation now marks WebView relocations as `media-overlay-follow`; visible text range bridge events preserve that source, and the controller stores the resulting visible range without dispatching a fresh audiobook seek. This prevents audio-follow page movement from immediately bouncing the audiobook back to a different visible-range cue while keeping normal user page-turn seeking intact.
- Readerdev emulator validation for production book `3809` proves playback-driven overlay commands now enter WebView with `controlled-relocate:begin media-overlay-follow`.
- The dedicated `whispersync-audio-follow` DevTools smoke probe now forces a non-duplicate audio-follow relocation and captures `visibleTextRange(... source=media-overlay-follow)` in Android logcat without a follow-on reader-to-audio seek, closing the previous host-only validation gap for feedback-loop suppression.
- The dedicated `whispersync-page-scoped-control` DevTools smoke probe now repeats the production book `3809` cue/unsupported-page sequence: `Authorforeword.xhtml` emits `visibleTextRange(... source=page-scoped-control-cue-covered)`, resolves to `positionMs=263360`, activates the overlay, then `mini_toc.xhtml` emits `visibleTextRange(... source=page-scoped-control-unsupported)` and dispatches `clearOverlay`.
- The dedicated `whispersync-char-offset-overlay` DevTools smoke probe now proves the ASR character-offset highlight fallback in a live Android WebView: readerdev wrapped `OEBPS/Text/Chapter-37.xhtml` characters `32-80` with `navic-active-overlay-fragment navic-media-overlay-range`, posted `overlayFragmentActive`, and cleared the marker through `clearOverlay`.
- Stage 5C.3 now provides a repeatable `scripts/adb-whispersync-enjoyment.ps1` gate for the production paired route. The 2026-06-29 readerdev run `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260629-194317` passed page-scoped control, audio-follow suppression, character-offset overlay, and exact companion-progress probes in one command.

## Bindery API Compatibility As Of 2026-06-29

Authority: `C:\Users\darka\Documents\Projects\Stremio Add-on Tester\github-export\bindery\docs\navic-opds-api-schema.md`.
Last updated: 2026-06-29.

- A concrete Whispersync action still requires an exact `syncPairs[]` entry whose `whispersync.status == "ready"` and whose `whispersync.artifactHref` is non-empty. Book-level `whispersyncStatus` is only a summary badge.
- Book/audiobook OPDS publication properties may embed `whispersyncStatus`, `syncPairCounts`, and `syncPairs`; Navic now decodes those into `BinderyPublication.sync` / `BinderyManifest.sync` instead of leaving them as generic property-bag data.
- The `/opds/books/{bookId}/sync` endpoint remains the preferred pair authority when it succeeds. If that request fails, `BinderyBookViewModel` falls back to the manifest's embedded sync pairs rather than discarding actionable pair data.
- Direct JSON audiobook detail responses now decode `whispersyncAvailable`, `whispersyncReadyCount`, `whispersyncStatus`, and `whispersync[]` for future audiobook-side UI decisions.
- Direct JSON audiobook detail responses also decode `studio` plus rich provider provenance (`providerKind`, `providerTitle`, `mappingStatus`, `metadataProvider`, `metadataConfidence`, `metadataConfidenceScore`, and `metadataConfidenceReason`). The audiobook detail UI surfaces `studio` alongside publisher metadata; the provenance fields are retained for diagnostics and future source-aware matching.
- Ready exact pairs remain launchable even when the book/version screen has not preloaded a `/opds/audiobooks/{audiobookVersionId}` row. Navic now keeps the pair alive from `ebookBookFileId + audiobookBookFileId + artifactHref`, derives a route artifact id from `artifactHref` when `artifactId` is omitted, parses sidecar `resources.audiobookManifestHref`, and loads the paired audiobook manifest from that OPDS path before falling back to the older routed audiobook version id.
- Book catalog/detail/search/hub cover cards now expose a subtle top-left headset badge only when the embedded publication has at least one exact ready pair with `artifactHref`; summary-only `whispersyncStatus` does not show the badge.
- The current Bindery schema is now represented directly in parser state: sidecars retain top-level `score`, `coverage`, `audioCoverage`, and `ebookCoverage`, and sync pair diagnostics retain `lastJob.state`, fractional `lastJob.progressPercent`, and `lastJob.updatedAt`.
- Generated reader-shell cover parsing now accepts the explicit Bindery asset shape `type="readerShellCover"` in addition to fullscreen/extended/expanded/shell-cover rels, manifest properties, and variant arrays, while ordinary `rel="cover"` thumbnails remain excluded from native fullscreen cover routing.
- The current Bindery resource schema is now represented in audio/readaloud metadata: Navic decodes `audio.bitrate`, `audio.bitrateKbps`, `audio.sampleRate`, `audio.sampleRateHz`, `audio.sampleRateKHz`, `audio.channels`, `audio.qualityLabel`, `audio.qualityScore`, plus `sourceRelease.title`, `sourceRelease.bitrate`, and `sourceRelease.editionType` so paired audiobook labels and diagnostics do not depend on older OPDS field names.
- Selectable audiobook versions follow the current Bindery quality order when the book screen has multiple candidates: `qualityScore`, then bitrate, then sample rate, then duration. Legacy codec/size heuristics are only a later fallback and must not outrank schema-provided quality fields.
- The current Bindery progress schema is now represented in resume/save paths: Navic decodes numeric `bookId`, `alias`, `resourceKey`, `href`, seconds and millisecond positions, `completed`, and `updatedAt`; resume identity prefers legacy `resourceHref`, then current `href`, then `/opds/books/{bookId}/resources/{resourceKey}`; saved reader progress now writes current `resourceKey` and `href` while preserving legacy `resourceHref`.
- Regression coverage: `BinderyRepositoryCatalogJsonTest.catalogJsonDecodesEmbeddedWhispersyncPairsFromPublicationProperties`, `BinderyBookVersionPolicyTest.bookVersionRowsUseManifestEmbeddedWhispersyncPairsWhenSyncEndpointIsUnavailable`, `BinderyBookVersionPolicyTest.audiobookVersionsUseCurrentBinderyQualitySort`, `BinderyBookVersionPolicyTest.embeddedReadyPairCanLaunchWithoutPreloadedAudiobookVersionRow`, `ReaderWhispersyncLaunchPolicyTest.readerWhispersyncLaunchAttachmentRequiresSelectedAudiobookContract`, `WhispersyncTimelineParserTest.productionBinderySidecarCuesParseIntoTimelineSegments`, `BinderyBookSyncJsonTest.decodesAudiobookDetailWhispersyncSummaryFields`, `BinderyBookSyncJsonTest.decodesAudiobookDetailProviderProvenanceFieldsFromNavicApiSchema`, `BinderyBookSyncJsonTest.readyWhispersyncPairOnlyRequiresReadyStatusAndArtifactHref`, `BinderyBookSyncJsonTest.decodesWhispersyncLastJobStateAndFractionalProgressFromNavicApiSchema`, `BinderyRepositoryProgressCacheTest.progressJsonDecodesCurrentBinderyProgressSchema`, `ReaderProgressSyncTest.binderyProgressMatchesCurrentBinderyHrefWhenLegacyResourceHrefIsAbsent`, `ReaderProgressSyncTest.binderyProgressMatchesCurrentBinderyResourceKeyWhenHrefIsAbsent`, `ReaderProgressSyncTest.readerLocatorSavesEbookProgressAsCfiTextHrefAndFragment`, `ReaderProgressSyncTest.readerLocatorSavesReadaloudProgressAsReadaloudKindAndClampsFraction`, `BinderyContinueShelfPolicyTest.readerProgressCreatesWhispersyncCompanionProgressFromCurrentBinderyHref`, and `BinderyContinueShelfPolicyTest.continueReadingItemsUseCurrentBinderyProgressHref`.

Important correction to older audits:

- Raw sidecar buttons are not an acceptable user-facing path. Both the ebook "open with audiobook" sheet and the generic Whispersync matches sheet must build a `Screen.Reader` with sidecar and audiobook route metadata, and `ReaderScreen` consumes that route to load the sidecar. A visible Whispersync match row must either launch a paired reader session or disable the launch button; it must not expose an inert sidecar action.
- GLM's older current-progress summary that `onOpenSidecar` is still a no-op is stale on this branch; that route is now guarded by `BinderyBookVersionPolicySourceTest` and must remain a real `Screen.Reader` launch path.

Still missing:

- No signed public-release claim should be made for end-to-end Whispersync playback until the reader-to-audio seek path and audio-to-reader highlight path are device-validated together on a real paired Bindery sidecar/audiobook session.
- Lack of logged-in public-release state is not an implementation blocker. Development validation must use the debuggable APK path with ignored credentials such as `bindery-debug.env`, direct reader launch metadata, and ADB/DevTools probes.
- Navidrome/global app login is outside the ebook/audiobook acceptance path. If fresh signed-package validation lands on the app login screen, that is an app-shell packaging-state limitation only; Bindery-scoped readerdev validation must continue for sidecar fetch, audiobook manifest/resource resolution, page-scoped playback, audio-follow overlays, and companion progress.
- Exact companion progress persistence and paired readerdev route reopen are emulator-proven for production book `3809`, sidecar `/opds/books/3809/sync/8`, audiobook `34`, and audiobook book file `633`; the reopen gate loaded `startPositionMs=263360` and preserved the same saved companion audio position after `media-overlay-follow`. The Stage 5C.3 orchestrator now repeats the full readerdev enjoyment matrix in one command.
- Current-source debug validation was rerun after the validation-boundary correction: `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260629-235005` passed page-scoped control, audio-follow suppression, character-offset overlay, and exact companion-progress probes through `darkaxt.navic.readerdev` using `bindery-debug.env`.
- Current-source debug validation was rerun again after the theta25 release/covers/artwork branch state: `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260630-120106` passed page-scoped control, audio-follow suppression, character-offset overlay, and exact companion-progress probes through `darkaxt.navic.readerdev` using `bindery-debug.env`.
- Current-source debug validation was rerun again after removing the false Navidrome/release-login blocker from the active plan: `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260630-140918` passed page-scoped control, audio-follow suppression, character-offset overlay, and exact companion-progress probes through `darkaxt.navic.readerdev` using `bindery-debug.env`.
- Current-source debug validation was rerun again after the paper texture and page-native headset visual slices: readerdev was rebuilt/installed, `captures\reader-dev\reader-dev-20260630-144505.png` reached `publicationReady` for production book `3809`, and `captures\reader-whispersync-enjoyment\stage5c3-whispersync-enjoyment-20260630-144538` passed page-scoped control, audio-follow suppression, character-offset overlay, and exact companion-progress probes through `darkaxt.navic.readerdev` using `bindery-debug.env`.
- Source-aware audio-follow visible range suppression is host-tested and readerdev-emulator-proven with a non-duplicate `visibleTextRange(source=media-overlay-follow)` bridge event.
- Character-offset ASR overlay highlighting is readerdev-emulator-proven with a direct WebView probe.
- Signed-release validation still needs to prove the same behavior on the installed public APK with real login/data; that is a packaging/state proof, not a reason to delay debug implementation validation.

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
- manifest resources: `resources.ebookManifestHref`, `resources.audiobookManifestHref`
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

- Release-device validation of visible range reporting, automatic audio seek when turning pages, and audiobook-position-driven text overlays.
- Release claim that Whispersync is usable end to end.

## Acceptance Criteria For This Slice

- A common test proves a Bindery-style JSON sidecar parses into stable segments.
- A common test proves active audio position resolves the expected segment.
- A common test proves visible ebook character range resolves the best audio seek target.
- A common test proves parser accepts `documentTextLength` absence without discarding segments.
- The implementation uses no Android APIs and does not touch reader shell UI.

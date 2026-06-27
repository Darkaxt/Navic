# Navic Performance Opportunity Audit

Date: 2026-06-27
Branch: master

## Scope

This audit focused on the three requested areas:

- Coil image pipeline configuration and image-request behavior
- Compose recomposition efficiency in Library, Aurral, and artist surfaces
- Network and database efficiency around list reloads and repeated collectors

The goal was to prioritize source-confirmed issues that can reduce Library/artist-page jank without adding timeouts or changing user-visible behavior unnecessarily.

## Findings

| Priority | Area | Finding | Status |
| --- | --- | --- | --- |
| P1 | Compose / DB | `ArtistDetailScreen` created a `getCollectionDownloadStatus()` Flow collector for every rendered owned Aurral album row. This is unnecessary because the ViewModel already exposes `allDownloads`. | Fixed. Album sheet status now derives from the existing `allDownloads` snapshot with `collectionDownloadStatus(...)`. |
| P1 | Coil / image memory | `DownloadManager.cacheCoverArt()` forced `Size.ORIGINAL` while warming offline cover art. This can decode large images during download/cache work even though the Subsonic URL already carries the configured cover-art size. | Fixed. The explicit original-size decode was removed; disk-cache priming is preserved. |
| P1 | Compose / CPU | `AurralHubScreen` projected the full artist-photo cache directly during composition. This repeats CPU work on the UI path as the cache changes. | Fixed. Projection now runs in `produceState` with `withContext(Dispatchers.Default)`. |
| Verified | Coil / logging | `CoverArt` failure logging is already gated by `imageDiagnosticLabel`, so normal scrolling no longer emits full throwable logs per failed cover. | No change needed. |
| Verified | Coil / cache | The singleton Coil loaders share an explicit memory cache capped at 8% and a disk cache. | No change in this pass. |
| Verified | Compose | `LibraryScreen` already projects Aurral library rows off the UI dispatcher and remembers cached rows. | No change needed. |
| Verified | Compose | Artist list alphabetical grouping is already wrapped in `remember(data)`. | No change needed. |
| Follow-up | Coil / rendering | `CoverArt` requests still depend on the configured Subsonic cover-art size. At `High`, URLs request 4096px covers. If jank persists on large grids, add a separate grid/thumbnail request size instead of changing full-screen artwork quality globally. | Not changed in this pass because it affects visual quality policy. |
| Follow-up | Aurral queue | Acquisition queue updates are still poll/snapshot based. A reactive server signal would reduce stale state and refresh churn. | Server/API dependent; not changed here. |

## Regression Guards Added

Added source-level guards in `LibraryStartupAsyncSourceTest` for:

- Aurral Hub artist-photo projection must run off the UI dispatcher.
- Offline cover cache warming must not request `Size.ORIGINAL`.
- Artist detail Aurral-owned album rows must not create per-row download-status Flow collectors.

These guards are intended to catch the specific regressions that caused repeated work while keeping validation focused.

## Recommended Next Pass

If device testing still shows Library or artist-page jank after this patch:

1. Split cover-art quality into display contexts: grid thumbnail, row thumbnail, full artwork.
2. Add request-size assertions for grid `CoverArt` call sites.
3. Capture an Android Studio or Perfetto trace while scrolling the Library tab and John Powell missing albums.
4. Convert Aurral acquisition queue state to a reactive stream when the backend supports it.

# Bindery OPDS 2 Client Contract

Date: 2026-06-01

Navic has a native Bindery settings integration that validates an API-key authenticated OPDS 2 root catalog and can parse OPDS catalogs, audiobook manifests, links, images, and reading-order resources. This document captures the remaining Bindery API contract needed before Navic can build an Audible-like audiobook surface with native browsing, playback, and resume.

## Current Client Baseline

Implemented in Navic:

* Settings -> Integrations -> Bindery.
* OPDS 2 URL validation for `http://` and `https://` URLs without credentials, query strings, or fragments.
* `X-Api-Key` authentication for OPDS catalogs and manifests.
* Root catalog validation and capability status rows.
* OPDS catalog parsing for `metadata`, `links`, `navigation`, and `publications`.
* Audiobook manifest parsing for metadata, images, subjects, and `readingOrder`.
* Reading-order resource parsing for `href`, `title`, media type, duration, and size.

## Live Endpoint Evidence

Using the local `bindery-debug.env` configuration on 2026-06-01:

| Endpoint | Result | Client Impact |
| --- | --- | --- |
| `GET /opds/` | `200`, about 350 ms, 7 navigation links | Good root validation target. |
| `GET /opds/authors` | Previously returned quickly with author navigation | Useful for future author shelves. |
| `GET /opds/series` | Previously returned quickly | Useful for future series shelves. |
| `GET /opds/recent?limit=5` | `200`, about 58 seconds, returned 50 publications | `limit` is ignored and the feed is too slow for app startup or library rows. |
| `GET /opds/books?limit=5` | Timed out at 60 seconds | Cannot be used for native browsing. |
| `GET /opds/formats/audiobook?limit=5` | Timed out at 60 seconds | Cannot be used for the primary audiobook library. |
| `GET /opds/search?q=the&limit=5` | Timed out at 60 seconds | Cannot be used for app search. |

These timings are too slow for an Android client. Navic should not place long blocking calls behind Library, Search, or Settings status refreshes.

## Required Server Capabilities

Bindery needs these OPDS 2 routes and behaviors before Navic can move beyond setup/status into full audiobook playback:

* Fast paginated OPDS 2 catalogs:
  * `GET /opds/books?limit=<n>&offset=<n>` or cursor pagination.
  * `GET /opds/recent?limit=<n>&offset=<n>` or cursor pagination.
  * `GET /opds/formats/audiobook?limit=<n>&offset=<n>` or cursor pagination.
  * `GET /opds/search?q=<query>&limit=<n>&offset=<n>` or cursor pagination.
* Pagination metadata:
  * `links` with `rel=["next"]` when another page exists.
  * Stable ordering between pages.
  * Honored `limit` values; Navic should be able to request 20-50 items.
* Audiobook manifests:
  * `GET /opds/books/{id}/manifest`.
  * `metadata.identifier`, `metadata.title`, `metadata.author`, optional description, subjects, duration where available.
  * `images` for cover art.
  * `readingOrder` entries for every playable file/chapter.
* Direct audio resources:
  * `GET /opds/books/{id}/resources/{resourceKey}`.
  * `HEAD /opds/books/{id}/resources/{resourceKey}`.
  * `Accept-Ranges: bytes`.
  * Stable `Content-Length`, `Content-Type`, and `ETag` when available.
  * API-key authentication through the same public Bindery URL; Google Drive must remain an origin/backend detail, not the public client contract.
* Progress/resume:
  * `GET /opds/books/{id}/progress`.
  * `PUT /opds/books/{id}/progress`.
  * Progress payload should include the active resource/chapter key, position in milliseconds, optional completed flag, and updated timestamp.
  * Server should tolerate idempotent updates and return the saved progress.

## Recommended Progress Payload

```json
{
  "bookId": "3693",
  "resourceKey": "bf-572-audio-001",
  "positionMs": 1234567,
  "durationMs": 7200000,
  "completed": false,
  "updatedAt": "2026-06-01T06:00:00Z"
}
```

## Navic Implementation Plan After Server Support

Once the server supports the required routes:

1. Add a native Audiobooks library row backed by a paginated `/opds/formats/audiobook` feed.
2. Add audiobook detail pages from OPDS manifests, using album-like artwork/title/author metadata and chapter rows.
3. Add playback queue construction from `readingOrder` resources.
4. Stream Range-capable resources through Media3 with the Bindery API key attached only to the configured Bindery origin.
5. Persist local progress during playback and sync it with Bindery through progress `GET`/`PUT`.
6. Add Resume, Continue Listening, Authors, Series, Recent, and Search surfaces once pagination is reliable.

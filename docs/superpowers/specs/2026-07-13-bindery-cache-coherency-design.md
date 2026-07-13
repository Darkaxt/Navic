# Bindery Cache Coherency Design

## Scope

Resolve B19 while retaining the existing six-hour fresh-cache window and stale-on-error fallback. No database schema change is required.

## Cache Identity

Every Bindery metadata key includes a truncated SHA-256 fingerprint of the trimmed API key. The raw credential is never persisted or logged. Changing account or permissions therefore creates a distinct cache namespace; clearing a base URL still removes every namespace for that server.

## Refresh And Fallback

Repository metadata reads gain an explicit `forceRefresh` input. A forced read skips only the fresh-cache early return: it still keeps the cached payload available as stale fallback if the live request fails. View-model `fullRefresh` paths pass this flag, while normal startup reads retain cache-first behavior.

## Mutation Invalidation

`putReadingProgress` invalidates the matching book-sync entry after the server accepts the mutation. Generic actions invalidate catalog and findings payloads plus exact book-owned manifest/resource/sync entries and audiobook-version prefixes when a book ID can be derived. Unknown actions fall back to clearing the configured base URL because their affected ownership cannot be proven.

## Verification

Focused tests cover key separation without plaintext leakage, base-URL separation, progress PUT invalidation, known-action scoped invalidation, unknown-action fallback, forced live reads, and stale fallback after forced-read failure. Broad database and UI validation is deferred until the implementation backlog is complete.

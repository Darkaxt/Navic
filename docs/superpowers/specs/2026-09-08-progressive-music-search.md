# Progressive Music Search And Monitoring

Status: client implementation verified; live origin diagnosis awaits SSH approval.
Authorization: already_authorized by the
reported global-search hang. Baseline: `fb106c6f7` on `fix/music-maintenance-qa`.
The ebook checkout and deployment remain out of scope.

## Evidence

SearchViewModel starts library, Aurral artist, and Aurral album work concurrently
but awaits all three before publishing. SearchRepository also waits for online
search before considering cached library results. A slow optional request thus
keeps completed results behind the full-screen loading state. The user clarified
that `Koji Kondo` stays resolving, not that search completes with no matches.
This ordering is source-confirmed. Read-only logs on the connected AYN Thor
contained no matching search trace. The endpoint probes below establish a live
response delay but do not identify its origin stage. No device interaction or
installation is required for this client fix.

A read-only unauthenticated GET to the configured public Aurral artist-search
endpoint for Koji Kondo did not complete within a 45-second diagnostic window.
That is evidence of an unresolved live request, not proof of which server stage
was slow. The diagnostic did not submit monitoring or change service state.
An additional GET of a known artist's library status completed TLS in 0.39 seconds
but received no HTTP bytes in its 20-second diagnostic window. This locates the
wait after connection establishment, without establishing a specific origin cause.

The monitoring action discards its HTTP response, then always starts a separate
confirmation lookup. The worker can repeatedly resubmit mutations after timed
polling cycles. ArtistDetailViewModel also treats request acceptance as confirmed
monitoring and clears its busy flag without a finally block. The local read-only
Aurral snapshot returns an authoritative artist from PUT but queues POST with 202;
its later GET can use an older in-process artist cache. This snapshot is not
verified as the deployed version.

## Requirements

- Q1: Publish each completed search source independently. Cached library search,
  Navidrome, Aurral artists, and Aurral albums must run independently. A slow or
  failed optional provider must not hide available results. Merge local/remote
  library entries without duplicate identities or replacing newer remote data
  with late cache data.
- Q2: Loading, successful absence, and failure are distinct per source. Keep
  partial results usable, show continuing work without a full-screen placeholder,
  and show an error/retry affordance for failed sources. An empty category whose
  relevant providers are still pending is not a terminal no-results state.
- Q3: A query edit or clear invalidates old work immediately, including during
  the existing 300 ms input debounce. Late/cancellation-swallowing providers must
  not publish old results, errors, or availability failures. Retrying the same
  query is supported. Offline/disabled integrations must not issue requests.
- Q4: Preserve existing category filtering, history, result actions, settings,
  and library entity caching. Do not add polling, arbitrary request deadlines,
  automatic retries, new dependencies, or reader behavior.
- Q5: Aurral fresh search cache must be usable without first waiting for login.
  Cancellation propagates through search/auth/cache boundaries and does not
  falsely mark the service down or turn into stale success. Preserve the existing
  cache identity and stale-on-real-error policy; no auth single-flight redesign.
- Q6: Global-search artist cards must not report an ambiguous/default nonpositive
  album count as verified absence. Repair an existing zero/negative cached count
  when search supplies a positive count, without replacing unrelated metadata or
  concurrent local edits. Suppress nonpositive count subtitles on search cards
  only; keep the artist result and existing non-search presentation. Do not infer
  discography totals from returned albums or fabricate contributor counts.
- Q7: Distinguish an accepted monitoring request from explicit confirmation.
  An update response with matching artist identity and explicit requested state
  confirms immediately, without another GET or waiting for enrichment. Queued,
  empty, unknown, or mismatched responses must not report success. Keep queued
  work observable through the existing confirmation queue and use read-only
  observation, never periodic mutation replay. Preserve the existing observation
  cadence without a completion deadline. Cancellation, replacement, and disabled
  configuration cannot publish stale success/failure or orphan a replacement job.
  Once a server request is accepted, page disposal or cancellation of subsequent
  cache maintenance must not discard the repository-owned confirmation observer.
- Q8: The regular artist page consumes confirmation directly and never infers
  monitoring success from Result<Unit>. Keep duplicate requests gated while busy
  or awaiting confirmation, and clear submission busy state in finally. Existing
  confirmation queue feedback remains the source of asynchronous outcomes.
  Retain confirmed state across subsequent pending/failed actions; terminal
  confirmation ends the visible submission spinner before cache maintenance.

## Acceptance

Deterministically hold one provider pending while completing the others in both
orders; verify results are visible before the held provider completes. Cover
source failure with partial results, all-empty completion, category-specific
pending state, cache/remote identity collisions, offline gating, same-query retry,
immediate supersession during debounce, clear, cancelled auth/cache/provider work,
and credentialed fresh cache with no login. Run the shared music host gate with
search regressions included and compare every requirement before committing.
Also verify zero-cache/positive-response repair, ambiguous incoming zero, local
metadata preservation, and count-subtitle policy for search versus other lists.
Host proof is not a claim of live-server or device validation. No release or
installation is authorized by this task.

Monitoring tests must cover confirmed PUT, queued POST, incomplete/mismatched
responses, the existing-artist POST race, cancellation/replacement, read-only
observation, and direct UI confirmation while enrichment is pending. The current
server contract supplies no queued operation ID or terminal background failure;
Navic cannot honestly infer failure from an artist remaining absent. Server-side
operation tracking and shortening the PUT backend work are outside this client
fix, and this limitation must be reported rather than hidden with a deadline.

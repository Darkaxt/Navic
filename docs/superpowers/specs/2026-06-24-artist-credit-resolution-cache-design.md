# Artist Credit Resolution Cache Design

## Problem

Navidrome can expose multi-artist credits as a single artist entity, for example
`Anyma & LISA` or `Afrojack, Sia & David Guetta`. Other endpoints already expose
better per-artist data in some contexts, such as album `artists[]`, but Navic
currently mixes raw display strings, structured credits, and Aurral enrichment in
different surfaces. The result is duplicated artist labels, composite artist rows,
and inconsistent Aurral artwork resolution.

The fix should not block playback, tab loading, or album rendering. Resolution can
be slow, so the UI must render from local state first and let the resolver update a
persistent translation cache afterward.

## Goals

- Preserve server-provided structured artist arrays when they genuinely decompose
  a credit.
- Split raw artist-credit strings only when every candidate is validated.
- Use Aurral artist search as the primary external validation source.
- Use album context as a fallback when a raw credit has multiple plausible splits.
- Persist a hash-keyed translation cache so future renders do not repeat network
  work.
- Keep unresolved or unsafe credits unchanged.
- Run resolution asynchronously and never as a synchronous UI prerequisite.

## Non-Goals

- Do not rewrite Navidrome metadata.
- Do not invent permanent artist identities in Navic without validation.
- Do not hide unresolved artists.
- Do not make Aurral a hard dependency for local music playback.

## POC Result

The POC lives in `tools/artist-credit-poc/`.

Deterministic tests cover:

- Dirty album display credit plus structured `artists[]`.
- Known groups that contain delimiters, such as `Earth, Wind & Fire`.
- Validated delimiter splits, such as `Anyma & LISA`.
- Unsafe splits that must remain raw, such as an unvalidated `Chase & Status`.
- Immediate render from cache before the resolver pass.

The live Navidrome run against the local Firefox session produced seven contexts.
Six resolved and one intentionally remained raw:

- `Anyma & LISA` -> `Anyma`, `LISA`
- `Afrojack, Sia & David Guetta` -> `Afrojack`, `Sia`, `David Guetta`
- `Eric Buchholz & Braxton Burks, Eric Buchholz • Eric Buchholz & Braxton Burks`
  -> `Eric Buchholz`, `Braxton Burks`
- `Earth, Wind & Fire` remained one artist because the full credit validated.
- `Chase & Status` remained raw in the sample because neither split candidate was
  validated in that index.

One important finding: Navidrome may return `artists[]` with a single entry equal
to the same dirty composite credit. Navic must not treat that as a decomposed
structured result.

## Resolution Pipeline

The production resolver should use this order:

1. Build an `ArtistCreditContext` from the current surface:
   - raw display credit
   - track id when available
   - album id/title when available
   - server structured artists when available
   - source surface for diagnostics
2. Compute a stable hash from normalized raw credit plus album/track context.
3. Render immediately:
   - cache hit: render cached artist names.
   - cache miss: render the raw credit and enqueue resolution.
4. Resolve in the background:
   - if structured artists contain more than one unique name, accept them.
   - if structured artists contain a single name equal to the raw credit, ignore
     them and continue.
   - if the full raw credit validates as an Aurral artist, keep it as one artist.
   - split using conservative delimiters.
   - validate every candidate through Aurral.
   - when direct validation is ambiguous, use album context to confirm the artist
     set.
5. Persist only positive, validated resolutions.
6. Notify interested UI state holders so visible rows update without a full tab
   reload.

## Cache Model

Store entries in Navic's existing `AurralMetadataCache` using payload type
`artist-credit-resolution`:

```text
baseUrl: navic:artist-credit
payloadType: artist-credit-resolution
path: stable hash of normalized credit + optional album/track context
payloadJson:
  - displayNames: List<String>
  - reason: String
  - confidence: Double
updatedAtMillis: Long
```

The cache is a translation table, not canonical metadata. It can be invalidated by
the normal metadata/artwork cache invalidation path.

Using the existing metadata cache avoids adding a Room entity while the cache
database currently uses destructive fallback migrations on Android/iOS builders.

Negative results should not be persisted at first. Aurral and Navidrome metadata
can improve over time, and storing misses would make fixed metadata look broken
until manual invalidation.

## Android Components

Add these production units:

- `ArtistCreditContext`: immutable input for any surface that renders artists.
- `ArtistCreditResolution`: resolved names plus reason/confidence.
- `resolveArtistCredit`: pure policy for structured artists, full-credit checks,
  split candidates, and album-context fallback.
- `ArtistCreditResolutionRepository`: database cache plus background resolution
  orchestration.
- `ArtistCreditLookup`: interface backed by Aurral artist search and local album
  context.

The resolver must be testable without Compose, network, or database.

## UI Behavior

Rows and detail pages should render immediately with the raw artist credit if the
translation is not cached. When a resolution arrives, the visible artist chips/text
can update in place.

Initial production wiring applies this to:

- Artist tab rows that currently show composite names.

Follow-up surfaces:

- Recently added songs.
- Album detail headers.
- Song rows.

Aurral rows should continue to show their title immediately and use loading state
only for enrichment/artwork that has not resolved yet.

## Validation

Required tests:

- structured multiple artists wins
- structured single dirty artist is ignored
- full validated group is not split
- all split candidates validated -> resolved
- partial split candidate missing -> unresolved
- album context confirms candidate set
- cache hit renders before resolver work
- cache miss renders raw and enqueues exactly once

Manual validation should include:

- `Anyma & LISA`
- `Afrojack, Sia & David Guetta`
- `Pokemon Reorchestrated: Double Team!`
- a known group containing delimiters
- an unresolved credit that must stay raw

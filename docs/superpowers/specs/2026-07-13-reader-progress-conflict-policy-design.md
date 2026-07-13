# Reader Progress Conflict Policy Design

## Scope

Resolve QA finding B13 without changing explicit route navigation or the existing progress persistence format. The change applies only when Navic chooses between Bindery progress and device-local progress while opening a reader publication.

## Policy

Each candidate contains its locator, source, and optional `updatedAt` value. Numeric epoch-millisecond values and ISO-8601 values are comparable.

1. An explicit route locator remains authoritative and bypasses conflict resolution.
2. If only one fallback candidate exists, select it.
3. If both timestamps parse, select the newer candidate even when it is behind. This makes intentional rereading last-writer-wins.
4. Equal timestamps select the remote Bindery candidate deterministically.
5. If either timestamp is missing or malformed, retain the existing placeholder/progress policy for backward compatibility.
6. When progress differs by more than one percentage point, preserve both candidates, the selected source, and the policy reason in a conflict diagnostic.

## Ownership

`ReaderProgressSync.kt` owns candidate construction, timestamp parsing, selection, and conflict classification. `ReaderOpenRequest.kt` attaches an optional conflict diagnostic to `ReaderEngineOpenRequest`. `ReaderScreen.kt` emits that diagnostic before opening the publication. No persistence migration or network change is required.

## Verification

Focused tests cover newer-behind, older-ahead, explicit reread, missing timestamps, malformed timestamps, equal timestamps, placeholder fallback, and explicit-route precedence. Broader reader and device validation remains deferred until the implementation backlog is complete.

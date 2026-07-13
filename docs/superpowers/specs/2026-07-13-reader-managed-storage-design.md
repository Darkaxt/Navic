# Reader Managed Storage Design

Date: 2026-07-13
Finding: `B22`
Release target: `v1.0.11-iota15` (`versionCode=542`)

## Problem

Navic currently serves publications, extracted read-aloud audio, imported fonts, and remote fonts from `context.cacheDir/reader`. Android may evict that tree while a WebView or Media3 session is still consuming it. The result can be a mid-session 404, missing audio file, or silently reverted font.

Moving the existing tree wholesale to `filesDir` would prevent OS eviction but would turn reconstructable publications and audio into unbounded permanent storage. The storage model must distinguish durable user assets from session-owned files and must define their cleanup owners.

## Considered Approaches

1. **Move the entire reader tree to `filesDir` without lifecycle changes.** This prevents OS eviction but leaks every opened publication and extracted audio package until the user manually clears storage.
2. **Move only fonts to `filesDir`.** This preserves user-imported assets but leaves active publications and read-aloud audio exposed to eviction, so it does not resolve B22.
3. **Use one managed root with durable and leased subtrees.** This is the selected approach. It preserves the existing WebView virtual URLs, migrates user assets, and gives reconstructable files explicit startup, screen-disposal, and manual-clear cleanup paths.

## Storage Layout

The WebView asset-loader origin and `/reader-cache/` path remain unchanged. Only the physical root changes.

```text
filesDir/reader/
  fonts/                         durable user-imported and downloaded fonts
  reader-publications/<key>/     leased publication, cover, and tint files
  storyteller-readaloud/<key>/   leased EPUB and extracted audio files
```

The former `cacheDir/reader` tree is treated as legacy input. Font files are migrated into the managed root without overwriting an already-valid managed copy. Legacy publication and read-aloud directories are reconstructable and are deleted rather than migrated.

## Initialization

`readerManagedStorageRoot(context)` performs one synchronized initialization per process and physical root:

- create `filesDir/reader`;
- migrate `cacheDir/reader/fonts` by directory rename when possible, with verified per-file move/copy fallback;
- remove stale managed and legacy `reader-publications` and `storyteller-readaloud` directories left by process death;
- never remove managed fonts during session initialization.

Initialization is idempotent. No symlink, elapsed-time cancellation, or background race is introduced.

## Session Ownership

Publication resolution returns a `ReaderSessionLease` for the exact publication directory. Storyteller loading combines that lease with the exact extracted-audio directory lease.

Android publication and read-aloud runtime hosts retain every lease they expose to the reader for the lifetime of that host. On host disposal they release the leases, recursively deleting only those session directories. Read-aloud disposal releases Media3 first, then deletes its files. A renderer-generation restart does not dispose the runtime host, so B5/B6 recovery continues to use the same live files.

If Android kills the process before disposal, the next process initialization clears stale session directories before resolving the publication again. Leaving the reader during logout/account removal disposes the hosts and therefore releases active sessions.

## Manual Storage Actions

`StorageManager.readerPublicationCacheSizeBytes()` reports managed and legacy reconstructable reader-session bytes, excluding fonts because fonts already have their own storage UI.

`StorageManager.clearReaderPublicationCache()` removes both session directory types from both managed and legacy roots. It does not remove imported or remote fonts. Existing Bindery metadata cleanup remains unchanged.

## Failure Handling

- A failed font migration keeps the source file unless a target file with the same non-zero length is verified.
- Failed publication resolution does not publish a lease or URL.
- Lease release is idempotent and constrained to directories created by reader resolvers; callers cannot lease an arbitrary parent root.
- Missing session files after process death cause normal re-resolution rather than reuse of a stale URL.

## Verification

- JVM host tests prove legacy-font migration, stale-session cleanup, durable-font preservation, lease idempotence, and managed/legacy size and clear behavior.
- Existing resolver, imported-font, Storyteller cache/loader, WebView host, storage UI, and reader runtime tests remain green.
- Android debug and reader-dev assemblies pass.
- ADB validation seeds a legacy font and stale publication, starts the candidate, verifies the font moved under `filesDir`, verifies stale session removal, opens a publication, and proves its managed session survives renderer recovery but is removed after leaving the reader.
- The Android-only public APK is independently checked for GitHub digest, v2 signature, `542/iota15`, packaged reader assets/notices, and in-place startup. iOS remains skipped.

## Out Of Scope

This unit does not add an LRU, change the `/reader-cache/` URL contract, alter reader UI/state restoration, modify WebView cache policy, add iOS behavior, or use timeouts.

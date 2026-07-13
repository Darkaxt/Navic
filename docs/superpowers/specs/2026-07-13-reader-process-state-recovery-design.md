# Reader Process-State Recovery Design

**Date:** 2026-07-13

**Scope:** Android reader process recreation (`B15` and the remaining process-state portion of `B24`)

**Release target:** `v1.0.11-iota19`, `versionCode=546`

## Problem

`ReaderScreen` owns `ReaderCoordinator` and its transient controller state in Compose `remember` values. Android process recreation therefore rebuilds the publication from its saved navigation route and durable locator but loses the current dialog, semantic text selection, half-written selection note, and active search intent. Search results, the renderer, the command acknowledgement ledger, the table of contents, and the Whispersync playback plan also disappear, but those values are derived from the publication and must be reconstructed rather than serialized.

The recovery boundary must not retain the WebView, coordinator, command queue, repositories, coroutine scope, sidecar, playback plan, or other runtime owners in a ViewModel.

## State Classification

### Durable

- `Screen.Reader` remains the serialized publication identity and launch attachment.
- The latest accepted reader locator remains in the existing local and Bindery progress stores.
- Annotations, bookmarks, reader settings, listening settings, and Whispersync companion progress remain in their existing persistent stores.

### Saved transient state

A route-matched `ReaderProcessStateViewModel` backed by `SavedStateHandle` retains only:

- the last open reader dialog;
- the submitted or typed search query and whether it had been submitted;
- semantic selection fields needed for copy/highlight/note recovery: text, CFI, and href;
- the selection-note draft anchor, selected text, section title, and current note text.

Geometry, popup coordinates, search result lists, and other renderer-derived fields are excluded.

### Reconstructed state

After the publication resolves, `ReaderScreen` creates a fresh coordinator and issues the normal open command using the best durable locator. It then applies the saved transient state:

- a submitted search query emits a fresh search command, while results remain empty until the engine replies;
- an unsubmitted query repopulates only the search input;
- a saved dialog reopens only when supported by the current publication capabilities;
- semantic selection or a note draft is restored only when it matches the opened publication;
- the Whispersync sidecar and playback plan reload through the existing asynchronous publication-ready path.

The normal acknowledgement-driven command dispatcher orders the reconstructed open and search commands. No timeout, delayed retry, or serialized in-flight command ledger is introduced.

## Ownership And Lifecycle

`ReaderProcessStateViewModel` owns only an encoded immutable snapshot in `SavedStateHandle`. `ReaderScreen` continues to own the coordinator, renderer host events, playback plan, and composition-scoped coroutines.

The ViewModel is keyed to the reader route identity. It rejects snapshots for a different publication. The screen clears the snapshot before an intentional navigation out of the reader. Activity recreation and process death do not execute that exit path, so Android can restore the handle.

Every accepted coordinator transition refreshes the saved snapshot, except publication opening itself: opening clears transient controller fields, so the screen first opens the publication and then applies the previously saved snapshot. Search-input and note-input changes are propagated immediately so unsaved text is not stranded in a dialog-local `remember` value.

## Validation Contract

Tests must prove:

1. The snapshot round-trips through `SavedStateHandle` and rejects malformed or publication-mismatched data.
2. Opening a fresh coordinator and applying the snapshot restores dialog, semantic selection, and note text.
3. Submitted search intent emits a new search command and does not restore old results.
4. Unsubmitted search text restores without executing a search.
5. PDF and CBZ recovery drops unsupported search and Whispersync dialog state.
6. TOC, loaded-document, renderer, command-dispatch, popup, and Whispersync runtime state are reconstructed rather than retained.
7. EPUB, PDF, and CBZ still open after background/foreground and process recreation; the public Android APK upgrades and starts cleanly.

## Rollback

Rollback is a forward release that removes the ViewModel wiring and restoration step. The `SavedStateHandle` payload is app-process state only, so no database or preference migration is required and old payloads may be ignored safely.

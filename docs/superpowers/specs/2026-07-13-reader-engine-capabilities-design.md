# Reader Engine Capabilities Design

**Date:** 2026-07-13

**Finding:** B3

**Target release:** `v1.0.11-iota18` (`versionCode=545`, Android only)

## Problem

Navic routes EPUB, PDF, AZW3, MOBI, CBZ, and FB2 through Foliate-backed adapters, but every adapter currently accepts every reader command. Search and media-overlay commands are therefore dispatched to PDF and CBZ even though those publication engines do not support those operations. The controller also enters search or Whispersync state before dispatch, and the UI offers controls that can never complete successfully.

The result is not only an unnecessary JavaScript call. Native state can claim that an unsupported search or overlay is active, and a CBZ with Whispersync route metadata can start sidecar and audiobook loading despite having no usable overlay engine.

## Goals

- Define one explicit capability set for each publication format.
- Keep search and media overlays enabled for EPUB, AZW3, MOBI, and FB2.
- Disable search and media overlays for PDF and CBZ.
- Stop unsupported actions before controller state changes.
- Omit unsupported search and Whispersync controls and avoid unsupported sidecar loading.
- Reject unsupported commands at the adapter boundary even if a future caller bypasses the controller or UI.
- Preserve every navigation, page-turn, settings, annotation, and selection path.
- Make no iOS, ebook-animation, persistence, or process-restoration change.

## Capability Matrix

| Format | Search | Media overlay / Whispersync |
| --- | --- | --- |
| EPUB | Yes | Yes |
| AZW3 | Yes | Yes |
| MOBI | Yes | Yes |
| FB2 | Yes | Yes |
| PDF | No | No |
| CBZ | No | No |

Only optional capabilities are modeled. Commands common to every adapter remain unconditional and do not acquire synthetic capability flags.

## Design

### Single format-owned capability source

`ReaderEngineCapability` will initially contain `Search` and `MediaOverlay`. `ReaderPublicationFormat.readerEngineCapabilities` will own the matrix, and a shared predicate will answer whether a format, controller state, engine, or command supports/requires a capability.

Search and clear-search commands require `Search`. Apply, update, and clear media-overlay commands require `MediaOverlay`. All other commands have no optional capability requirement.

### Controller gate

Public controller entry points for search, search dialog, media-overlay mutation, Whispersync sidecar/session actions, and the Whispersync player will no-op when the active format lacks the required capability. Opening a publication will also clear transient search, overlay, and Whispersync state so state from a previously opened capable publication cannot leak into PDF or CBZ.

This is the primary behavioral gate: unsupported actions cannot create misleading native state, audio seeks, playback commands, or engine commands.

### UI and loading gate

The reader bottom bar will omit Search when the active controller format lacks `Search`. Whispersync attachment resolution will return no launch attachment when the publication format lacks `MediaOverlay`, preventing sidecar and audiobook work from starting. Reader-root status, playback, settings, and player surfaces will use the same capability predicate so stale state cannot reveal an unsupported control.

### Adapter safety boundary

Every Foliate adapter will expose its format-derived capability set. Before dispatching a command, the shared adapter will check the command requirement. An unsupported command returns the current adapter and view state without incrementing the command key or replacing the last bridge command.

Search-result and media-overlay host events will be ignored by adapters that lack the corresponding capability. This prevents unsolicited or stale bridge events from re-entering unsupported controller state.

## Alternatives Considered

### Format checks at each call site

Rejected. Repeating `format != Pdf && format != Cbz` across controller, Compose, and bridge code invites drift and gives no auditable engine contract.

### Adapter-only filtering

Rejected. It prevents the JavaScript call but still lets native search and Whispersync state change, still displays unusable controls, and still permits unnecessary sidecar/audio loading.

### Treat all Foliate-backed formats as equivalent

Rejected. Sharing an implementation adapter does not imply equal publication-engine features. Capability ownership belongs to the selected format contract.

## Validation

- Test first: matrix, controller, adapter, coordinator, and Whispersync launch tests must fail before production changes.
- Capability tests cover all six formats and all gated commands/events.
- PDF and CBZ tests prove no native state mutation, command-key increment, bridge dispatch, dialog, or Whispersync attachment.
- EPUB, AZW3, MOBI, and FB2 tests prove existing supported behavior remains available.
- Existing controller, coordinator, Foliate adapter, reader launch, common chrome, and runtime host suites remain green.
- Android debug assembly, vendor/attribution gates, release metadata, signed public APK, and in-place ADB upgrade are verified.
- The public release remains `iota##`; `iota18` is used only for this code change. All iOS jobs remain skipped.

## Rollback

The change is stateless. A forward rollback may remove the capability gates and matrix without data migration. Existing persisted reader settings and progress are unaffected.

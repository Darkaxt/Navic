# Reader Web Debugging Scope Design

**Date:** 2026-07-13

**Finding:** B23

**Target release:** `v1.0.11-iota17` (`versionCode=544`, Android only)

## Problem

Android's `WebView.setWebContentsDebuggingEnabled` switch is process-global. Navic currently enables a permanent force flag when `MainActivity` starts in the `readerDev` build type. The flag is never released, so every WebView created later in that process remains inspectable even after the reader-dev activity that requested the diagnostic capability is disposed.

The public release build does not currently set the force flag, but this boundary is represented only by a default `BuildConfig` value and a source convention. The release contract should explicitly prove that only `readerDev` can acquire forced debugging.

## Goals

- Forced WebView debugging exists only while at least one live `readerDev` activity owns it.
- Activity recreation cannot let an older activity disable debugging underneath a newer activity.
- Disposing the final owner restores the process-global WebView debugging state to the normal default.
- Public release packaging has an explicit false `NAVIC_READER_DEV` build constant and cannot enter the forced path through `MainActivity`.
- The existing user-controlled developer setting continues to control debugging for an active reader WebView independently of the forced reader-dev lease.
- No timeout, process restart, iOS change, or reader animation change is introduced.

## Non-Goals

- Making Android WebView debugging per-WebView; the platform API is process-global.
- Removing the developer-options toggle from normal Navic builds.
- Changing reader bridge permissions, appassets origin hardening, reader-dev intent seeding, or publication behavior.
- Treating `readerDev` as a distributable production artifact.

## Design

### Lease-counted force state

`ReaderWebRuntime` will replace the mutable `setForceWebContentsDebuggingEnabled(Boolean)` API with an acquisition API that returns an idempotent `AutoCloseable` lease.

- Acquiring with `enabled=false` returns a no-op lease and changes no global state.
- The first enabled lease applies forced debugging.
- Additional enabled leases increment ownership without repeating the global transition.
- Closing one of several leases leaves forced debugging enabled.
- Closing the final lease removes the force and reapplies the normal default state.
- Closing the same lease more than once is harmless.

The ownership counter and release gate are synchronized. This handles configuration changes where a replacement activity may acquire before the retired activity finishes `onDestroy`.

`setWebContentsDebuggingEnabled(enableDebugging)` remains the single place that calls Android's global API. Its effective value is `forcedOwnerCount > 0 || enableDebugging`, preserving the existing user setting for live reader hosts.

### Activity ownership

`MainActivity.onCreate` acquires one lease using `BuildConfig.NAVIC_READER_DEV` before Compose mounts the reader surface. `MainActivity.onDestroy` closes and clears that exact lease before delegating to the superclass.

A release or ordinary debug build receives a no-op lease because its build constant is false. A `readerDev` activity owns forced debugging only for its lifecycle.

### Build boundary

`androidApp/build.gradle.kts` will retain the default false build constant, set it explicitly to false for `release`, and set it to true only for `readerDev`. Contract tests will assert all three facts and assert that `MainActivity` passes only `BuildConfig.NAVIC_READER_DEV` to the acquisition API.

## Alternatives Considered

### Raw enable in `onCreate`, disable in `onDestroy`

Rejected. During activity recreation, the old activity can run `onDestroy` after the new activity has enabled debugging, incorrectly disabling the surviving host.

### Keep the process-lifetime force and document it

Rejected. Documentation does not satisfy the roadmap requirement to reset global state when the dev host is disposed, and it leaves unrelated later WebViews inspectable.

### Toggle around each reader WebView

Rejected. The Android API is process-global, and independently toggling individual hosts has the same overlapping-owner race. Ownership must be coordinated centrally.

## Validation

- Test first: an Android host test must fail against the current raw setter because no lease lifecycle exists.
- Lease-state tests cover disabled acquisition, first/second owner, out-of-order release, final release, and duplicate close.
- Source/build contract tests prove `MainActivity` acquires and releases the lease, release/default constants are false, only `readerDev` is true, and the obsolete process-lifetime setter is absent.
- Existing reader runtime, bridge lifecycle, renderer recovery, and reader-dev environment tests remain green.
- Debug and reader-dev APKs assemble, retain their separate package IDs, and pass packaged vendor/attribution checks.
- The public Android APK embeds `544 / v1.0.11-iota17`, uses the established signing certificate, passes packaged governance, and starts without targeted AndroidRuntime/MediaController/Koin/Room errors. All iOS jobs remain skipped.

## Rollback

A forward rollback may restore the previous runtime implementation while retaining the explicit release build constant. No persisted data or protocol migration is involved.

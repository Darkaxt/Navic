# Reader WebView Cache Policy Design

**Status:** Approved by the QA remediation roadmap, Tranche 3 change unit B8

**Target release:** `v1.0.11-iota16` (Android only)

## Problem

`ReaderWebRuntime.configure()` currently combines `WebSettings.LOAD_NO_CACHE` with `webView.clearCache(true)` every time a reader WebView is configured. Renderer recovery creates and configures a replacement WebView, so the policy discards reusable WebView cache and compiled runtime work exactly when recovery should be fast. The reader shell, Foliate modules, PDF.js modules, styles, and images are APK assets served by `WebViewAssetLoader`, not mutable network resources.

Android documents `LOAD_DEFAULT` as the normal mode that uses valid cached resources and revalidates as needed. Android also describes `WebViewAssetLoader` as the performant HTTPS-origin mechanism for static in-app assets. The asset loader resolves every intercepted response from the currently installed application package or managed internal reader path; it does not delegate those URLs to an external server.

## Goals

- Use normal WebView caching for APK-backed reader runtime assets.
- Stop globally clearing WebView cache during every reader configuration.
- Preserve the current secure appassets origin, bridge setup, storage URLs, and renderer-generation recovery behavior.
- Prove the policy with a positive `LOAD_DEFAULT` assertion and negative regression assertions for `LOAD_NO_CACHE` and unconditional `clearCache(true)`.
- Verify that renderer death still restores the exact publication locator and acknowledges the replayed open command.

## Non-Goals

- No user-facing cache control, cache size cap, LRU, or storage migration.
- No change to publication/read-aloud session storage or explicit session leases.
- No change to JavaScript bridge ownership, command acknowledgement, debugging policy, publication capabilities, or saved reader state.
- No iOS implementation or iOS build.
- No timeout or elapsed-time cancellation behavior.

## Considered Approaches

### 1. Normal cache policy with no configure-time clear (selected)

Set `webView.settings.cacheMode = WebSettings.LOAD_DEFAULT` and remove `webView.clearCache(true)`. This is the smallest policy that matches Android's default behavior and the roadmap. It preserves all existing origin and security boundaries while allowing WebView to retain normal HTTP/code-cache state between generations.

### 2. Clear only when the APK version changes

Persist an application version marker and call `clearCache(true)` once after an upgrade. This introduces lifecycle state, performs a process-global destructive operation, and assumes cache invalidation is required for intercepted APK responses without evidence of stale delivery. It is rejected unless device evidence later demonstrates a real upgrade-coherence defect.

### 3. Add versioned reader asset URLs

Include a build/version token in the reader entrypoint and every module URL. This couples the shared runtime URL contract to Android build metadata and requires complete subresource propagation to be effective. It is disproportionate to B8 and is rejected.

## Runtime Contract

`ReaderWebRuntime.configure()` will continue to:

1. Apply the requested debugging state.
2. Enable JavaScript and DOM storage for the trusted appassets reader runtime.
3. Use `LOAD_DEFAULT`.
4. Preserve viewport, zoom, file-access, and content-access hardening.
5. Install the generation-owned JavaScript bridge.
6. Load the existing appassets entrypoint.

It will not clear WebView cache. Manual storage controls remain responsible only for their explicitly owned application data; B8 does not add a replacement clear trigger.

## Failure and Recovery Behavior

Changing cache mode must not alter renderer-loss handling. When Android replaces the WebView renderer, the host still creates a new generation, configures the replacement WebView, reloads the same appassets entrypoint, and replays the unacknowledged publication command from the latest locator. A cache miss simply falls back to the asset loader; it is not an error path and requires no retry timer.

## Verification

- A focused Android host source contract must fail against the current implementation, then pass only when `LOAD_DEFAULT` is present and both old anti-patterns are absent.
- Existing bridge lifecycle, command acknowledgement, runtime asset, controller, coordinator, Storyteller, and managed-storage tests must remain green.
- JavaScript syntax, Chromium command acknowledgement, page-turn model, reader smoke/trace smoke, vendor manifest, tamper self-test, and attribution checks must pass.
- Debug and reader-dev APKs must embed `versionCode=543` and `versionName=v1.0.11-iota16` and pass packaged governance.
- On `emulator-5554`, reader-dev must open an available EPUB, survive renderer-only process death, restore the exact href/CFI, acknowledge `reader-open-1`, and emit no AndroidRuntime fatal error.
- The public Android APK must be independently verified for digest, signing certificate, embedded version, packaged governance, and signed in-place startup. Every iOS job must be skipped.

## References

- [Android WebSettings cache modes](https://developer.android.com/reference/android/webkit/WebSettings.html#setCacheMode(int))
- [Android WebViewAssetLoader](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader.html)
- [Android guidance for loading in-app WebView content](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)


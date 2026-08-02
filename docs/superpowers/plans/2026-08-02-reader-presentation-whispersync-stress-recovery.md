# Reader Presentation, Whispersync, and Stress-Recovery Plan

**Goal:** Repair the production-only presentation and synchronization failures observed during rapid real-device reading, while adding explicit feedback for page gestures rejected because the renderer is busy.

**Architecture:** Foliate remains the sole location and pagination authority. PlayLikeCurl remains the curl renderer and rendered pixels remain the visual authority. Native blocking presentation stays fail-closed, but transient preparation must never reuse publication cover artwork. Whispersync may seek or play only after Foliate positively confirms that the matching overlay was painted in the current viewport.

**Validation:** Use focused common and Android host tests plus a ReaderDev assembly. Do not schedule emulator acceptance; the user will perform real-device stress validation. Raw visual evidence remains local under `.codex-validation`.

---

## Confirmed production defects

1. **Cover dismissal bypasses the resumed Foliate presentation.** A Next action hides the native cover without emitting a Foliate command. This can expose stale ordinal-0 WebView pixels while PlayLikeCurl remains centered on the resumed ordinal, so the next curl appears to jump many pages.
2. **Whispersync seeks before overlay activation.** A visible range under the cover can optimistically select a cue and seek audio. If Foliate rejects the overlay, the stale cue survives and later playback emits progress updates without a confirmed highlight.
3. **Preparation reuses publication cover artwork.** `shell=false, preparation=true` displays the same external shell-cover view. Rapid `Invalidated`/`ContentRejected` recovery pulses therefore flash the cover and its margins.
4. **Busy rejection has no user feedback.** Settling, preparation, and renderer-unavailable terminal outcomes consume the gesture silently.

The deferred synthetic closed-book endpoint remains out of scope.

## Task 1: Make cover dismissal acknowledgement-driven

**Primary files:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressReducer.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt`

- [ ] Change Next/downward-scroll on the real shell cover to retain the cover and emit `NavigateTo(currentLocator)` when the locator has a usable Foliate identity.
- [ ] Keep the cover in place if no valid resume locator exists; never reveal an unacknowledged WebView.
- [ ] Let only the existing explicit readable Foliate relocation acknowledgement dismiss the cover.
- [ ] After acknowledgement, request a fresh visible-text-range observation rather than reusing one captured while the cover owned presentation.
- [ ] Prove that initial-resume, pagination-profile, relocation-committed, invalid, and boundary locators cannot dismiss the cover.
- [ ] Prove that the next action after acknowledgement performs an ordinary page turn.

## Task 2: Require positive Whispersync overlay activation

**Primary files:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderOverlaySync.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncReducer.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderOverlayReducer.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader.js`

- [ ] While the native cover is visible, retain visible-range observations only; emit no overlay, seek, or play action.
- [ ] Represent overlay activation as pending versus Foliate-confirmed, with an opaque request identity carried through bridge apply/active/inactive messages.
- [ ] A fresh visible range may issue `ApplyMediaOverlay`, but it must not seek audio or mark the overlay active.
- [ ] Promote only a matching, successfully painted `overlayFragmentActive` response; then emit exactly one audio seek and enable Play.
- [ ] A matching inactive response clears pending/confirmed cue state, cached seek state, metadata, and synchronized playback.
- [ ] Reject stale or mismatched active/inactive responses.
- [ ] Prevent `UpdateMediaOverlayProgress` until the cue is confirmed and still visible.
- [ ] Make JavaScript post active only after painting succeeds; failed visibility or painting posts inactive and cannot reach fallback range painting.
- [ ] Add a bridge command that requests a fresh visible range without relocating Foliate.
- [ ] Defensively reject direct Play dispatch while the cover is visible or activation remains pending.

## Task 3: Separate shell artwork from preparation shielding

**Primary files:**
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPagePreparationOverlay.kt`

- [ ] Preserve the existing blocking and fail-closed preparation policy.
- [ ] Render publication cover artwork and its backdrop only when `shellCoverVisible` is true.
- [ ] When only preparation owns visibility, render an opaque neutral reader-background shield without publication artwork or cover margins.
- [ ] Keep preparation-only presentation noninteractive as a shell cover and prevent WebView exposure.
- [ ] Verify actual shell-cover appearance and navigation remain unchanged.

## Task 4: Add coalesced renderer-busy gesture feedback

**Primary files:**
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.ios.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] Trigger feedback only from the one-terminal-per-gesture publisher for `RejectedPreparing`, `RejectedSettling`, and `RejectedRendererUnavailable`.
- [ ] Exclude boundary rejection, cancellation, accepted gestures, and background work without a rejected gesture.
- [ ] Keep transient state local to the reader UI rather than persisted controller/process state.
- [ ] Draw one noninteractive crossed red circle at bottom center, above system bars, reader controls, and preparation cards.
- [ ] Provide a polite live-region description without moving accessibility focus.
- [ ] Keep it visible for at least 500 ms, expire at about two seconds, clear early after readiness when the minimum duration has passed, and restart rather than stack on repeated rejection.
- [ ] Ensure blank-area touches during blocking preparation still reach the terminal router while the retry control remains usable.

## Task 5: Verification and delivery

- [ ] Add focused common tests for cover acknowledgement, overlay pending/confirmed transitions, stale response rejection, playback gating, and busy-indicator timing.
- [ ] Add focused Android host/source tests for JavaScript paint acknowledgement, preparation shielding, callback plumbing, and pointer-terminal convergence.
- [ ] Run the focused tests while iterating.
- [ ] Run `:composeApp:testAndroidHostTest`.
- [ ] Assemble `:androidApp:assembleReaderDev` without launching an emulator or changing a physical device.
- [ ] Inspect the final diff and working tree.
- [ ] Commit with the required co-author footer and push `hotfix/reader-presentation-gating` to `fork`.
- [ ] Do not publish or replace a production release asset without separate explicit authorization.

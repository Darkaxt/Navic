# Reader Bridge Decode Diagnostics Implementation Plan

> **Execution:** Implement inline in this task. The user explicitly requested no agents. Track every step with the checkboxes below.

**Goal:** Close QA finding B4 by preserving typed JS-to-Kotlin bridge decode failures, logging bounded diagnostics without UI noise for isolated failures, and surfacing persistent protocol failure through the existing reader error UI.

**Architecture:** `decodeReaderBridgeMessage` returns `ReaderBridgeDecodeResult.Decoded` or a typed `Rejected` result while the existing nullable helper remains as a compatibility adapter. A common `ReaderBridgeMessageProcessor` owns consecutive-failure episode state: it logs the first rejection, resets on a valid event, and emits one `ReaderBridgeEvent.Error` after three consecutive rejections. Android's `ReaderJavascriptBridge` delegates to this processor; no elapsed-time cancellation or timeout is introduced.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization JSON, Android WebView JavaScript bridge, kotlin.test, Gradle Android host tests.

---

## File Map

- Modify `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt`: typed decode result, rejection classification, bounded diagnostic snapshot, nullable compatibility adapter.
- Create `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeMessageProcessor.kt`: consecutive-failure episode policy and persistent error emission.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt`: delegate bridge messages to the processor and log typed rejection details.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt`: remove unconditional raw bridge logging.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderBridgeProtocolTest.kt`: typed malformed/shape/unknown/invalid-payload decode tests and bounded snapshot proof.
- Create `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderBridgeMessageProcessorTest.kt`: isolated, persistent, reset, and one-report-per-episode tests.
- Modify the QA analysis and remediation roadmap: record B4 implementation and release evidence.

### Task 1: Return Typed Decode Results

- [x] Add failing tests requiring malformed JSON, non-object JSON, missing type, unknown type, and invalid required fields to return distinct `ReaderBridgeDecodeFailure` values.
- [x] Add a failing test requiring diagnostic raw text to be at most 500 characters, replace control characters with spaces, and end a truncated value with `...`.
- [x] Run `./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderBridgeProtocolTest"` and confirm the typed API is absent.
- [x] Add `ReaderBridgeDecodeResult`, `ReaderBridgeDecodeFailure`, and `decodeReaderBridgeMessage`; keep `decodeReaderBridgeEvent` as a nullable adapter.
- [x] Rerun `ReaderBridgeProtocolTest` and confirm green.
- [x] Commit as `fix(reader): return typed bridge decode failures`.

### Task 2: Log Failure Episodes And Surface Persistent Failure

- [ ] Add failing processor tests proving one rejection logs once without an error event, a valid event resets the episode, three consecutive rejections emit exactly one error with code `reader_bridge_protocol`, and a later valid event permits a new episode.
- [ ] Run `./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderBridgeMessageProcessorTest"` and confirm the processor is absent.
- [ ] Implement `ReaderBridgeMessageProcessor` with a three-consecutive-failure threshold and no timeout.
- [ ] Make `ReaderJavascriptBridge` log the first typed rejection at warning level and forward processor events; remove unconditional raw-message logging from the WebView host.
- [ ] Rerun both focused test classes and the existing reader controller/adapter tests.
- [ ] Commit as `fix(android): surface persistent reader bridge failures`.

### Task 3: Validate And Publish Iota12

- [ ] Update B4 and the roadmap with implementation evidence; set `versionCode=539` and `versionName=v1.0.11-iota12`.
- [ ] Run focused bridge tests, the owning reader test group, Android debug assembly, release-version verification, and `git diff --check`.
- [ ] Rebase onto current `fork/master`, rerun the final gate, commit, push public `master`, and push annotated tag `v1.0.11-iota12`.
- [ ] Verify GitHub Actions, public APK hash/signature/embedded identity, and an ADB in-place emulator upgrade with no AndroidRuntime startup error.
- [ ] Record public release evidence, push it, remove this worktree and local branch, and verify unrelated ebook/page-turn worktrees are unchanged.

## Self-Review

- Spec coverage: typed failure reasons, safely bounded diagnostics, isolated-failure logging, persistent UI error, no crash, reset behavior, no timeout, Android-only release, and cleanup each have an explicit task.
- Placeholder scan: no deferred implementation placeholders remain.
- Type consistency: protocol decoding returns `ReaderBridgeDecodeResult`; the compatibility helper returns `ReaderBridgeEvent?`; the processor consumes the typed result and emits existing `ReaderBridgeEvent` values.

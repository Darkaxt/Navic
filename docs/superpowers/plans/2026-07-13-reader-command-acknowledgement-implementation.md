# Reader Command Acknowledgement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. The user explicitly requested inline execution without subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android reader commands acknowledgement-driven and replay the exact retained publication plus latest locator/state command after a WebView renderer generation changes.

**Architecture:** Kotlin assigns opaque, stable command IDs and keeps commands in a generation-aware pending ledger until JavaScript posts `commandAck`. Exactly one tracked command is in flight: an acknowledgement removes the queue head and unlocks the next entry, so `openPublication` resolves before any locator command starts. The host retains the latest observed locator; a new WebView generation reuses stable IDs, reopens directly at that locator, and replays the latest command only when it was still unacknowledged. No elapsed-time retry or cancellation is introduced.

**Tech Stack:** Kotlin Multiplatform, Compose Android `WebView`, kotlinx.serialization JSON, JavaScript ES modules, kotlin.test, Android host tests, Gradle.

---

## File Map

- Modify `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt`: add command envelopes and typed `commandAck` decoding.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWebCommandDispatch.kt`: own stable IDs, pending entries, acknowledgement removal, generation replay, and ordering.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt`: serialize pending command envelopes.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt`: connect ready/ack/generation events to the ledger without resetting it on renderer loss.
- Modify `composeApp/src/androidMain/assets/reader/navic-reader.js`: deduplicate command IDs and post acknowledgements after successful completion.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderBridgeProtocolTest.kt`: verify the wire contract and malformed acknowledgement rejection.
- Rewrite `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWebCommandDispatchTest.kt`: verify send, duplicate-ready, ack, generation death/replay, publication replacement, and ordering.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`: verify packaged JS acknowledgement/deduplication semantics.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudRuntimeLoaderTest.kt`: adapt the existing dispatch assertion to envelopes.
- Modify `docs/superpowers/plans/2026-07-12-qa-analysis.md` and `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`: record B5/B24 implementation and release evidence after validation.
- Modify version metadata files selected by `scripts/prepare-release-version.sh`: prepare `v1.0.11-iota13`, Android `versionCode=540`.

### Task 1: Define the acknowledged wire protocol

- [ ] **Step 1: Write failing protocol tests**

Add tests proving that `ReaderBridgeDispatchCommand(id = "reader-open-1", command = open).toJavaScript()` emits both `commandId` and the existing command shape, that `{ "type": "commandAck", "commandId": "reader-open-1" }` decodes to `ReaderBridgeEvent.CommandAcknowledged`, and that a blank/missing ID is rejected as `InvalidPayload`.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderBridgeProtocolTest
```

Expected: compilation fails because the envelope and acknowledgement event do not exist.

- [ ] **Step 3: Implement the protocol types**

Add this production surface:

```kotlin
data class ReaderBridgeDispatchCommand(
    val id: String,
    val command: ReaderBridgeCommand
)

data class CommandAcknowledged(val commandId: String) : ReaderBridgeEvent

fun ReaderBridgeDispatchCommand.toJavaScript(): String
```

Serialize `commandId` into the existing top-level command JSON object. Add `commandAck` to the known event types and decode only a non-blank `commandId`.

- [ ] **Step 4: Re-run the focused protocol tests and confirm GREEN**

- [ ] **Step 5: Commit the wire contract**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderBridgeProtocolTest.kt
git commit -m "feat(reader): add acknowledged command protocol"
```

### Task 2: Replace consumed keys with a pending command ledger

- [ ] **Step 1: Write the failing state-machine tests**

Cover these exact transitions:

1. Initial ready in generation 0 emits `openPublication`, queues the current command behind it with a stable distinct ID, and retains both pending.
2. A duplicate ready/update in generation 0 emits nothing while the dispatched queue head remains unacknowledged.
3. Acknowledging the open ID removes only that pending entry and unlocks the current command; duplicate and unknown acknowledgements are no-ops.
4. A newer `commandKey` appends and emits only the newer command.
5. Generation 1 reconstructs `openPublication` with the latest observed locator, preserving its stable ID. It replays the current command behind open only when that command was unacknowledged in generation 0.
6. A publication change discards the old publication ledger and emits the new open command before its current command, even when its numeric `commandKey` matches the old publication.

- [ ] **Step 2: Run `ReaderWebCommandDispatchTest` and confirm RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderWebCommandDispatchTest
```

- [ ] **Step 3: Implement the immutable ledger**

Use these responsibilities:

```kotlin
data class ReaderWebPendingCommand(
    val dispatch: ReaderBridgeDispatchCommand,
    val lastDispatchedGeneration: Int? = null
)

data class ReaderWebCommandDispatchState(
    val publicationKey: String? = null,
    val publicationSequence: Long = 0,
    val runtimeGeneration: Int? = null,
    val lastCommandKey: Long? = null,
    val pendingCommands: List<ReaderWebPendingCommand> = emptyList()
) {
    fun acknowledge(commandId: String): ReaderWebCommandDispatchState
}
```

`commandsForReadyReaderRuntime` accepts `runtimeGeneration`. It creates IDs from the host-owned publication sequence and command key, marks only the queue head dispatched for the current generation, and returns at most one `ReaderBridgeDispatchCommand`. `observeLocator` retains `LocationChanged` state. On a generation transition the ledger rebuilds open with that locator, then only the latest still-unacknowledged command; it does not replay superseded or already-acknowledged one-shot commands.

- [ ] **Step 4: Run the state-machine tests and confirm GREEN**

- [ ] **Step 5: Adapt the Storyteller host test and commit**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWebCommandDispatch.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWebCommandDispatchTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudRuntimeLoaderTest.kt
git commit -m "fix(reader): retain commands until acknowledgement"
```

### Task 3: Acknowledge successful JavaScript execution

- [ ] **Step 1: Write a failing packaged-runtime contract test**

Assert that the shipped runtime has an acknowledged-ID set, returns an acknowledgement again for a duplicate ID without executing it twice, wraps dispatch results with `Promise.resolve`, posts `commandAck` only in the success path, and leaves failures unacknowledged.

- [ ] **Step 2: Run the focused host test and confirm RED**

```powershell
.\gradlew.bat :composeApp:androidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest
```

- [ ] **Step 3: Implement the JavaScript dispatcher**

Keep `NavicReaderRuntime.dispatch` unchanged. Kotlin envelopes always provide a non-empty `commandId`; direct ADB/browser harness calls without an ID remain untracked and receive no acknowledgement. At `window.NavicReaderBridge.dispatch`, return an acknowledgement for duplicate tracked IDs without re-executing them, execute new commands once, and use:

```javascript
return Promise.resolve(result).then(value => {
  acknowledgedCommandIds.add(commandId)
  post({ type: 'commandAck', commandId })
  return value
})
```

Do not acknowledge the rejection path. Preserve native page-turn settlement cleanup with the existing `finally` behavior.

- [ ] **Step 4: Run the host test and confirm GREEN**

- [ ] **Step 5: Commit Kotlin and JavaScript together**

```powershell
git add composeApp/src/androidMain/assets/reader/navic-reader.js composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt
git commit -m "feat(reader): acknowledge successful web commands"
```

### Task 4: Connect acknowledgements and renderer replay in the Android host

- [ ] **Step 1: Add a failing Android-host source contract**

Verify that the host passes `webViewGeneration` into the ledger, consumes `CommandAcknowledged` into `acknowledge`, serializes `ReaderBridgeDispatchCommand`, triggers dispatch from `Ready`, and does not replace the ledger with an empty state in `onRenderProcessGone`.

- [ ] **Step 2: Run the focused host test and confirm RED**

- [ ] **Step 3: Implement host integration**

On `Ready`, set readiness and dispatch against the current WebView. On `CommandAcknowledged`, remove the matching pending entry, dispatch the newly exposed queue head, and keep the protocol event internal to the host. On `LocationChanged`, retain the locator before forwarding it. On renderer loss, set readiness false, destroy the dead view, increment generation, and retain the dispatch state. Log opaque command IDs and generation without publication URLs.

- [ ] **Step 4: Run protocol, ledger, asset, and host tests and confirm GREEN**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderBridgeProtocolTest --tests paige.navic.reader.ReaderWebCommandDispatchTest
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest --tests paige.navic.reader.ReaderCommandAcknowledgementHostContractTest
```

- [ ] **Step 5: Commit host integration**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderCommandAcknowledgementHostContractTest.kt
git commit -m "fix(android): replay reader state after renderer loss"
```

### Task 5: Validate, document, release, and clean

- [ ] **Step 1: Run the complete owning suites and Android assembly**

Run common reader tests, Android host reader tests, vendor/source governance checks, packaged attribution checks, and `:composeApp:assembleDebug`. Any failure blocks release.

- [ ] **Step 2: Rebase onto the current public `master`**

Fetch `fork`, verify concurrent ebook worktree changes, and rebase this isolated branch only. Resolve no unrelated page-turn changes by replacement; preserve both owners' edits and rerun the complete owning suites.

- [ ] **Step 3: Validate renderer recovery on Android**

Install the signed candidate on the available emulator/tablet, open a publication at a non-zero locator, kill/recreate the renderer through the supported debug/ADB path, and capture logs proving the same stable open/current-command IDs are replayed in order and acknowledged. Confirm the reader returns to the same publication and locator with no `AndroidRuntime` fatal error.

- [ ] **Step 4: Record B5/B24 evidence and prepare only `iota13`**

Update both QA documents with test/device evidence. Run the repository release preparation path for `v1.0.11-iota13` and Android `versionCode=540`; inspect the diff to ensure no `kappa` or `lambda` version is introduced.

- [ ] **Step 5: Commit, push, tag, and create the public Android release**

Push the reviewed commits to public `master`, tag the release commit `v1.0.11-iota13`, run the existing GitHub release workflow with iOS skipped, and verify checks, signature, embedded version, public APK SHA-256, and an in-place ADB upgrade/start.

- [ ] **Step 6: Record release evidence and remove this worktree**

Commit/push release evidence, verify public `master` and release state, then remove `C:/Users/darka/Documents/Projects/Android/.codex-temp/navic-qa-tranche-3-command-ack` and delete `fix/qa-tranche-3-command-ack`. Do not touch the active ebook worktrees.

## Self-Review

- B5 coverage: stable IDs, pending-until-ack, successful JS ack, duplicate-ready behavior, and no timeout are explicit in Tasks 1-4.
- B24 slice coverage: renderer-generation replay restores retained publication and latest locator/state command in deterministic order. ViewModel/process-death draft state remains in the separate B15/B24 change unit from the roadmap.
- Deployment coverage: Android-only tests, device renderer recovery, `iota13`, public verification, and isolated-worktree cleanup are explicit in Task 5.
- No placeholders or iOS implementation steps are present.

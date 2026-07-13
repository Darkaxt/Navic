# Reader WebView Cache Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve roadmap finding B8 by using Android's normal WebView cache policy for local reader assets without clearing process-global WebView cache during configuration.

**Architecture:** Keep `ReaderWebRuntime` as the single Android WebView policy owner. Change only its cache mode and configure-time clear behavior, protect the contract with an Android host source test, and reuse the existing renderer-generation recovery path for device proof.

**Tech Stack:** Kotlin Multiplatform, Android WebView/WebSettings, AndroidX WebKit `WebViewAssetLoader`, Kotlin Test, Gradle, Node.js/Playwright reader harness, PowerShell, ADB, GitHub Actions

---

### Task 1: Replace the forced no-cache runtime policy

**Files:**
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt`

- [x] **Step 1: Write the failing cache-policy contract**

Replace `androidReaderWebViewRuntimeBypassesCachedBundledAssets` with:

```kotlin
@Test
fun androidReaderWebViewRuntimeUsesNormalCacheForBundledAssets() {
	val runtimeText = readerWebRuntimeFile().readText()

	assertContains(
		runtimeText,
		"cacheMode = WebSettings.LOAD_DEFAULT",
		message = "APK-backed appassets should use normal WebView caching across renderer generations."
	)
	assertFalse(
		runtimeText.contains("WebSettings.LOAD_NO_CACHE"),
		"Reader configuration must not force every local asset request to bypass WebView cache."
	)
	assertFalse(
		runtimeText.contains("webView.clearCache(true)"),
		"Reader configuration must not clear process-global WebView cache."
	)
}
```

- [x] **Step 2: Run RED and confirm the expected failure**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderWebViewRuntimeUsesNormalCacheForBundledAssets --no-daemon
```

Expected: FAIL because `ReaderWebRuntime.kt` still contains `LOAD_NO_CACHE` and does not contain `LOAD_DEFAULT`.

- [x] **Step 3: Implement the minimal runtime change**

In `ReaderWebRuntime.configure()`, replace the cache assignment and remove the clear call:

```kotlin
webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
```

Do not change any other WebView setting, bridge call, or load URL.

- [x] **Step 4: Run GREEN and adjacent runtime contracts**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest `
  --tests paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderWebViewRuntimeUsesNormalCacheForBundledAssets `
  --tests paige.navic.reader.ReaderRuntimeAssetsTest.androidRuntimeConstantsPointAtPackagedReaderEntrypoint `
  --tests paige.navic.reader.ReaderRuntimeAssetsTest.androidWebViewRuntimeHonorsReaderViewportMeta `
  --tests paige.navic.reader.ReaderCommandAcknowledgementHostContractTest `
  --tests paige.navic.reader.ReaderBridgeGenerationLifecycleHostContractTest `
  --no-daemon
```

Expected: all selected tests pass with zero failures or errors.

Validation evidence: RED failed at `ReaderRuntimeAssetsTest.kt:235` because unchanged production code did not contain `LOAD_DEFAULT`. After the minimal runtime edit, all 5 selected cache/entrypoint/viewport/acknowledgement/generation tests passed with zero failures or errors.

- [x] **Step 5: Commit the cache policy**

```powershell
git add composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt
git commit -m "perf(android): retain reader WebView cache"
```

### Task 2: Run integrated reader and governance gates

**Files:**
- Verify: `composeApp/src/androidMain/assets/reader/`
- Verify: `tools/reader-harness/`
- Verify: `scripts/verify-reader-vendor-assets.ps1`
- Verify: `scripts/verify-third-party-attributions.ps1`

- [ ] **Step 1: Run the focused Kotlin owner suite**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest `
  --tests paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderWebViewRuntimeUsesNormalCacheForBundledAssets `
  --tests paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderRuntimeAcknowledgesSuccessfulTrackedCommandsAndDeduplicatesTheirIds `
  --tests paige.navic.reader.ReaderManagedStorageTest `
  --tests paige.navic.reader.ReaderManagedStorageHostContractTest `
  --tests paige.navic.reader.BinderyReaderPublicationResolverTest.resolvedPublicationOwnsItsSessionDirectory `
  --tests paige.navic.reader.ReaderImportedFontCacheTest `
  --tests paige.navic.reader.StorytellerReadaloudAudioCacheTest `
  --tests paige.navic.reader.StorytellerReadaloudRuntimeLoaderTest `
  --tests paige.navic.reader.StorytellerMediaOverlayParserTest `
  --tests paige.navic.reader.ReaderBridgeProtocolTest `
  --tests paige.navic.reader.ReaderBridgeMessageProcessorTest `
  --tests paige.navic.reader.ReaderWebCommandDispatchTest `
  --tests paige.navic.reader.ReaderJavascriptBridgeTest `
  --tests paige.navic.reader.ReaderBridgeGenerationLifecycleHostContractTest `
  --tests paige.navic.reader.ReaderCommandAcknowledgementHostContractTest `
  --tests paige.navic.reader.FoliateEpubEngineAdapterTest `
  --tests paige.navic.reader.ReaderControllerTest `
  --tests paige.navic.reader.ReaderCoordinatorTest `
  --tests paige.navic.reader.ReaderCoordinatorStepConsumerTest `
  --no-daemon
```

Record exact test, failure, error, and skip counts from `composeApp/build/test-results/testAndroidHostTest/*.xml`.

- [ ] **Step 2: Run JavaScript and source-governance gates**

Run:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
Push-Location tools\reader-harness
npm ci
npm run test:command-ack-runtime
npm run test:page-turn-model
npm run smoke
node src\run-reader-harness.mjs --mode trace-smoke
Pop-Location
pwsh -NoProfile -File scripts\verify-reader-vendor-assets.ps1
pwsh -NoProfile -File scripts\test-reader-vendor-assets-verifier.ps1
pwsh -NoProfile -File scripts\verify-third-party-attributions.ps1
```

Expected: command acknowledgement 1/1, page-turn model 15/15, smoke and trace-smoke pass, source vendor verification 30/30, tamper self-test passes, and source attribution passes.

- [ ] **Step 3: Assemble Android candidates**

Run:

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:assembleReaderDev --no-daemon
```

Expected: BUILD SUCCESSFUL. Do not invoke an iOS task.

- [ ] **Step 4: Verify both APKs**

For `androidApp/build/outputs/apk/debug/Navic.apk` and `androidApp/build/outputs/apk/readerDev/Navic.apk`, record size and SHA-256, run packaged vendor and attribution verification, and inspect metadata with the latest installed SDK `aapt.exe`. Before the version bump, both APKs must still report the current public metadata; after Task 4 they must report `543 / v1.0.11-iota16`.

### Task 3: Prove renderer recovery on Android

**Files:**
- Generated only: `captures/reader-cache-device/publication.epub`
- Use: `D:/Downloads/Trash/01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub`
- Use: `scripts/install-reader-dev.ps1`

- [ ] **Step 1: Prepare a local reader fixture and server**

Copy the known EPUB to `captures/reader-cache-device/publication.epub`. Start a hidden Python HTTP server on port `8876` rooted at that capture directory and verify the owning process command line contains `http.server 8876`. Do not add the fixture or server logs to Git.

- [ ] **Step 2: Install and open reader-dev**

Run `scripts/install-reader-dev.ps1` with the existing primary checkout's `bindery-debug.env`, `-NoBuild`, `-NoDiscoverPublication`, `-RequireReaderLaunch`, and these explicit values:

```text
ReaderPublicationUrl=http://10.0.2.2:8876/publication.epub
ReaderResourceHref=b8-fixture.epub
ReaderBookId=b8-fixture
ReaderTitle=B8 WebView Cache Policy
ReaderKind=ebook
ReaderFormat=epub
```

Wait for the existing script's foreground and `publicationReady` checks. Confirm the app reaches `commandAck(reader-open-1)` and record a non-empty href and CFI from the latest `locationChanged` event.

- [ ] **Step 3: Kill only the WebView renderer**

Resolve the reader-dev app PID with `adb shell pidof darkaxt.navic.readerdev`. Resolve the current renderer PID from `adb shell ps -A -o PID,PPID,NAME` by selecting `webview:sandboxed_process`. Clear logcat, kill only that renderer PID, and wait for the host's existing `render process gone` recovery sequence to complete.

- [ ] **Step 4: Verify exact replay and process continuity**

Confirm:

- The app PID is unchanged.
- A different `webview:sandboxed_process` PID exists.
- Logs show generation 1, `publicationReady`, and `commandAck(reader-open-1)`.
- The post-recovery href and CFI exactly equal the pre-kill values.
- AndroidRuntime contains no fatal error.

Stop only the verified `http.server 8876` process after the checks complete.

### Task 4: Document and publish `v1.0.11-iota16`

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-webview-cache-policy-implementation.md`

- [ ] **Step 1: Record candidate evidence and update B8**

Mark B8 as candidate-validated, add the exact Kotlin/JavaScript/build/ADB evidence, preserve all other finding dispositions, and update the Tranche 3 nuance so B3, B15/B24, and B23 remain pending.

- [ ] **Step 2: Bump only the next iota release**

Set:

```kotlin
versionCode = 543
versionName = "v1.0.11-iota16"
```

Run the Android version verifier, `git diff --check`, and searches proving there are no unpadded iota tags and no `kappa`/`lambda` tags or release references.

- [ ] **Step 3: Integrate current public master**

Fetch `fork/master`. If it advanced, rebase this isolated branch, inspect every incoming path for overlap, and rerun the focused owner, governance, build, packaged APK, and version gates on the integrated tree.

- [ ] **Step 4: Commit and publish Android only**

Push the integrated candidate to public `master`, create annotated tag `v1.0.11-iota16`, and invoke `scripts/publish-github-release.ps1` with `-AllowPublicRelease`, a B8 readiness note, and `-SkipPush`. The hyphenated prerelease tag must leave all iOS jobs skipped.

- [ ] **Step 5: Independently verify the public APK**

Download `Navic.apk` from the public release. Verify GitHub asset digest equals local SHA-256, APK Signature Scheme v2 uses certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`, metadata is `543 / v1.0.11-iota16`, packaged vendor checks are 30/30, and attribution passes. Upgrade `darkaxt.navic` in place from iota15, launch `MainActivity`, confirm a live app PID, and scan AndroidRuntime/MediaController/Koin/Room startup logs.

### Task 5: Record immutable evidence and clean

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-webview-cache-policy-implementation.md`

- [ ] **Step 1: Record release evidence**

Add release commit, workflow ID, public APK size/digest, signing certificate, metadata, iOS skip state, and in-place startup evidence. Mark B8 Released while leaving B3, B15/B24, and B23 pending.

- [ ] **Step 2: Push and verify immutable refs**

Push the evidence commit and verify public `master`, the peeled `v1.0.11-iota16` tag commit, release record, and asset digest independently.

- [ ] **Step 3: Remove only this worktree and branch**

After proving the branch is on public `master`, remove `C:/Users/darka/Documents/Projects/Android/.codex-temp/navic-qa-tranche-3-reader-cache-policy` and local branch `fix/qa-tranche-3-reader-cache-policy`. Verify the primary animation checkout, `navic-playlist-pattern-fix`, `navic-page-turn-animation-rev4`, and `navic-destination-aware-page-turns` remain registered and unchanged.

## Self-Review

- Scope covers B8 only; B3, B15/B24, B23, reader animation, managed storage, and iOS are untouched.
- The production change is test-first and limited to normal cache mode plus removal of unconditional cache clearing.
- Device proof exercises renderer generation recovery, not merely cold startup.
- Release naming continues `iota##` as `v1.0.11-iota16`.
- No timeout, symlink, global backup, or purposeless retained fixture is introduced.

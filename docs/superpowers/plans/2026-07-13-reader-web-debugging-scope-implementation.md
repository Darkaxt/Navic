# Reader Web Debugging Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve roadmap finding B23 by limiting forced Android WebView debugging to live `readerDev` activity owners and proving public release builds cannot acquire that force path.

**Architecture:** Add a small synchronized, lease-counted force registry beside `ReaderWebRuntime`. `MainActivity` acquires with the compile-time `NAVIC_READER_DEV` flag and closes the lease in `onDestroy`; Android build contracts make the release flag explicitly false and the reader-dev flag exclusively true.

**Tech Stack:** Kotlin Multiplatform, Android WebView, Android activity lifecycle, Kotlin Test, Gradle, PowerShell, ADB, GitHub Actions

---

### Task 1: Define the failing ownership and build contracts

**Files:**
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderWebDebuggingForceRegistryTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt`

- [ ] **Step 1: Write the lease-state tests**

Create tests that record Boolean transitions from a `ReaderWebDebuggingForceRegistry` callback:

```kotlin
@Test
fun disabledLeaseNeverForcesWebContentsDebugging() {
	val transitions = mutableListOf<Boolean>()
	val registry = ReaderWebDebuggingForceRegistry(transitions::add)

	registry.acquire(enabled = false).close()

	assertFalse(registry.isForced())
	assertEquals(emptyList(), transitions)
}

@Test
fun forcedDebuggingRemainsUntilLastEnabledLeaseCloses() {
	val transitions = mutableListOf<Boolean>()
	val registry = ReaderWebDebuggingForceRegistry(transitions::add)
	val first = registry.acquire(enabled = true)
	val second = registry.acquire(enabled = true)

	first.close()
	assertTrue(registry.isForced())
	second.close()
	second.close()

	assertFalse(registry.isForced())
	assertEquals(listOf(true, false), transitions)
}
```

- [ ] **Step 2: Replace the process-lifetime source assertion**

Update `androidReaderDevBuildTypeIsLocalDebuggableAndSeparateFromPublicRelease` to assert:

```kotlin
val releaseBlock = androidBuild
	.substringAfter("getByName(\"release\")")
	.substringBefore("getByName(\"debug\")")
val readerDevBlock = androidBuild.substringAfter("create(\"readerDev\")")

assertTrue(releaseBlock.contains("buildConfigField(\"boolean\", \"NAVIC_READER_DEV\", \"false\")"))
assertTrue(readerDevBlock.contains("buildConfigField(\"boolean\", \"NAVIC_READER_DEV\", \"true\")"))
assertTrue(mainActivity.contains("acquireForcedWebContentsDebugging(BuildConfig.NAVIC_READER_DEV)"))
assertTrue(mainActivity.contains("readerDevWebDebuggingLease?.close()"))
assertTrue(mainActivity.contains("override fun onDestroy()"))
assertFalse(mainActivity.contains("setForceWebContentsDebuggingEnabled"))
```

Retain all unrelated reader-dev assertions.

- [ ] **Step 3: Run RED**

Run:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.reader.ReaderWebDebuggingForceRegistryTest" `
  --tests "paige.navic.reader.ReaderDevEnvironmentContractTest.androidReaderDevBuildTypeIsLocalDebuggableAndSeparateFromPublicRelease"
```

Expected: compilation fails because `ReaderWebDebuggingForceRegistry` does not exist, and the source contract would reject the current process-lifetime setter.

- [ ] **Step 4: Commit the failing tests**

```powershell
git add composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderWebDebuggingForceRegistryTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt
git commit -m "test(android): require scoped reader WebView debugging"
```

### Task 2: Implement lease-counted forced debugging

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebDebuggingForceRegistry.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt`
- Modify: `androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt`
- Modify: `androidApp/build.gradle.kts`

- [ ] **Step 1: Add the synchronized registry**

Implement:

```kotlin
internal class ReaderWebDebuggingForceRegistry(
	private val onForcedStateChanged: (Boolean) -> Unit
) {
	private val lock = Any()
	private var ownerCount = 0

	fun acquire(enabled: Boolean): AutoCloseable {
		if (!enabled) return AutoCloseable {}
		synchronized(lock) {
			ownerCount += 1
			if (ownerCount == 1) onForcedStateChanged(true)
		}
		val released = java.util.concurrent.atomic.AtomicBoolean(false)
		return AutoCloseable {
			if (!released.compareAndSet(false, true)) return@AutoCloseable
			synchronized(lock) {
				check(ownerCount > 0)
				ownerCount -= 1
				if (ownerCount == 0) onForcedStateChanged(false)
			}
		}
	}

	fun isForced(): Boolean = synchronized(lock) { ownerCount > 0 }
}
```

- [ ] **Step 2: Route `ReaderWebRuntime` through the registry**

Replace the Boolean force field and setter with:

```kotlin
private val webContentsDebuggingForceRegistry = ReaderWebDebuggingForceRegistry {
	currentWebContentsDebuggingEnabled = null
	setWebContentsDebuggingEnabled(WebContentsDebuggingDefaultEnabled)
}

fun acquireForcedWebContentsDebugging(enabled: Boolean): AutoCloseable =
	webContentsDebuggingForceRegistry.acquire(enabled)
```

Compute the effective value with `webContentsDebuggingForceRegistry.isForced() || enableDebugging`. Remove `setForceWebContentsDebuggingEnabled`.

- [ ] **Step 3: Bind the lease to `MainActivity`**

Add:

```kotlin
private var readerDevWebDebuggingLease: AutoCloseable? = null
```

Acquire before mounting Compose:

```kotlin
readerDevWebDebuggingLease =
	ReaderWebRuntime.acquireForcedWebContentsDebugging(BuildConfig.NAVIC_READER_DEV)
```

Release on disposal:

```kotlin
override fun onDestroy() {
	readerDevWebDebuggingLease?.close()
	readerDevWebDebuggingLease = null
	super.onDestroy()
}
```

- [ ] **Step 4: Make the release boundary explicit**

Inside `getByName("release")`, add:

```kotlin
buildConfigField("boolean", "NAVIC_READER_DEV", "false")
```

Keep the default false field and the reader-dev true override.

- [ ] **Step 5: Run GREEN and commit**

Run the two Task 1 tests and then:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.reader.ReaderWebDebuggingForceRegistryTest" `
  --tests "paige.navic.reader.ReaderDevEnvironmentContractTest" `
  --tests "paige.navic.reader.ReaderRuntimeAssetsTest.androidReaderWebViewDebuggingIsControlledByDeveloperSetting" `
  --tests "paige.navic.reader.ReaderBridgeGenerationLifecycleHostContractTest"
```

Expected: all selected tests pass. Commit only the B23 implementation and tests:

```powershell
git add androidApp/build.gradle.kts androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebDebuggingForceRegistry.kt composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderWebDebuggingForceRegistryTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt
git commit -m "fix(android): scope reader WebView debugging"
```

### Task 3: Run integrated Android validation

**Files:**
- Verify: `composeApp/src/androidMain/kotlin/paige/navic/reader/`
- Verify: `androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt`
- Verify: `scripts/verify-reader-vendor-assets.ps1`
- Verify: `scripts/verify-third-party-attributions.ps1`

- [ ] **Step 1: Run the focused owner suite**

Run the registry, full reader-dev environment, runtime asset debugging/cache/entrypoint, bridge generation, command acknowledgement, Android host, controller, and coordinator test classes. Record exact JUnit test/failure/error/skip counts from `composeApp/build/test-results/testAndroidHostTest`.

- [ ] **Step 2: Run governance and Android builds**

Run:

```powershell
pwsh -NoProfile -File scripts\verify-reader-vendor-assets.ps1
pwsh -NoProfile -File scripts\test-reader-vendor-assets-verifier.ps1
pwsh -NoProfile -File scripts\verify-third-party-attributions.ps1
.\gradlew.bat --no-daemon :androidApp:assembleDebug :androidApp:assembleReaderDev
```

Do not invoke an iOS task. Verify debug and reader-dev package IDs, metadata, SHA-256, all 30 packaged vendor files, and packaged attribution.

- [ ] **Step 3: Exercise reader-dev ownership on the emulator**

Install the reader-dev APK and launch a known local EPUB through the existing reader-dev intent contract. Confirm a live `webview_devtools_remote` socket while the reader-dev activity owns the lease. Remove that task with Android activity-manager tooling and capture `ReaderWebRuntime` logging that the final owner restored `WebView debugging enabled=false`; if Android kills the cached process before the transition can be observed, rely on the deterministic lease test and record the device limitation rather than weakening the contract.

- [ ] **Step 4: Verify no regression in public startup**

Keep the currently installed public package intact until release verification. Scan reader-dev logs for AndroidRuntime fatal errors and targeted MediaController/Koin/Room startup errors. Stop and remove only temporary local fixture/server artifacts created by this unit.

### Task 4: Prepare and publish `v1.0.11-iota17`

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-web-debugging-scope-implementation.md`

- [ ] **Step 1: Record candidate evidence and B23 disposition**

Document the RED/GREEN result, integrated test counts, build/governance evidence, and emulator evidence. Mark B23 candidate-validated while leaving B3 and B15/B24 pending.

- [ ] **Step 2: Bump only the next iota release**

Set:

```kotlin
versionCode = 544
versionName = "v1.0.11-iota17"
```

Run the Android version verifier, `git diff --check`, and remote tag/release searches proving `iota17` is unused and no unpadded iota, kappa, or lambda ref exists.

- [ ] **Step 3: Integrate current public master**

Fetch `fork/master`. If it advanced, rebase this isolated branch, inspect incoming paths for overlap, and rerun every affected owner, governance, assembly, metadata, and package check.

- [ ] **Step 4: Publish Android only**

Push the integrated candidate to public `master`, create annotated tag `v1.0.11-iota17`, and invoke `scripts/publish-github-release.ps1` with `-AllowPublicRelease`, `-SkipPush`, and a B23 readiness note. Every iOS job must remain skipped.

- [ ] **Step 5: Independently verify the public APK**

Download public `Navic.apk`. Verify GitHub digest equals local SHA-256, APK Signature Scheme v2 uses certificate SHA-256 `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`, metadata is `544 / v1.0.11-iota17`, and packaged vendor/attribution checks pass. Upgrade `darkaxt.navic` in place from iota16, launch `MainActivity`, confirm a live PID/resumed activity, and scan error-level AndroidRuntime/MediaController/Koin/Room logs.

### Task 5: Record immutable evidence and clean

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-web-debugging-scope-implementation.md`

- [ ] **Step 1: Record release evidence**

Add release commit, workflow ID, public APK size/digest, certificate, metadata, iOS skip state, and in-place startup evidence. Mark B23 Released while leaving B3 and B15/B24 pending.

- [ ] **Step 2: Push and verify immutable refs**

Push the evidence commit and verify public `master`, the peeled `v1.0.11-iota17` tag commit, release record, asset digest, and contiguous `iota01` through `iota17` naming independently.

- [ ] **Step 3: Remove only this worktree and branch**

After proving the branch is on public `master`, remove `C:/Users/darka/Documents/Projects/Android/.codex-temp/navic-qa-tranche-3-reader-web-debug-scope` and local branch `fix/qa-tranche-3-reader-web-debug-scope`. Verify the primary animation checkout, `navic-playlist-pattern-fix`, `navic-page-turn-animation-rev4`, and `navic-destination-aware-page-turns` retain their captured heads and dirty states.

## Self-Review

- Scope covers B23 only; B3, B15/B24, reader animation, reader storage, and iOS are untouched.
- The registry handles overlapping owners and idempotent release without elapsed-time cancellation.
- Release builds retain the normal developer setting but cannot acquire the reader-dev force path through `MainActivity`.
- Release naming remains `iota##`; the next target is `v1.0.11-iota17`.
- No timeout, symlink, backup, or retained fixture is introduced.

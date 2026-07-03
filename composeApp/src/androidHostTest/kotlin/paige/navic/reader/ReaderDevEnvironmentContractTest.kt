package paige.navic.reader

import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderDevEnvironmentContractTest {
	private val root = sequence {
		var candidate = kotlin.io.path.Path("").toAbsolutePath()
		while (true) {
			yield(candidate)
			candidate = candidate.parent ?: break
		}
	}.first { candidate ->
		candidate.resolve("androidApp/build.gradle.kts").exists()
	}

	@Test
	fun androidReaderDevBuildTypeIsLocalDebuggableAndSeparateFromPublicRelease() {
		val androidBuild = root.resolve("androidApp/build.gradle.kts").readText()
		val mainActivity = root.resolve("androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt").readText()
		val app = root.resolve("composeApp/src/commonMain/kotlin/paige/navic/App.kt").readText()
		val preferenceManager = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt"
		).readText()
		val sideloadingDialog = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/components/dialogs/SideloadingDialog.kt"
		)
		val readerRuntime = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt"
		).readText()

		assertTrue(
			androidBuild.contains("create(\"readerDev\")"),
			"androidApp must expose a dedicated readerDev build type for emulator-only dirty reader APKs."
		)
		assertTrue(
			androidBuild.contains("applicationIdSuffix = \".readerdev\""),
			"readerDev must install beside public Navic so emulator data can be isolated from release data."
		)
		assertTrue(
			androidBuild.contains("isDebuggable = true") &&
				androidBuild.contains("isMinifyEnabled = false") &&
				androidBuild.contains("isShrinkResources = false"),
			"readerDev must be inspectable and avoid release shrinking while preserving release-like runtime code."
		)
		assertTrue(
			androidBuild.contains("buildConfigField(\"boolean\", \"NAVIC_READER_DEV\", \"true\")"),
			"readerDev must expose a BuildConfig flag so the app can force reader diagnostics."
		)
		assertTrue(
			mainActivity.contains("ReaderWebRuntime.setForceWebContentsDebuggingEnabled(BuildConfig.NAVIC_READER_DEV)"),
			"MainActivity must force WebView debugging for readerDev before the reader surface is mounted."
		)
		assertTrue(
			mainActivity.contains("readerDevInitialScreen") &&
				mainActivity.contains("Screen.Reader(") &&
				mainActivity.contains("NAVIC_READER_DEV_PUBLICATION_URL"),
			"readerDev must support direct reader launch from env-driven intent extras."
		)
		assertTrue(
			mainActivity.contains("navic.dev.reader.start_progress") &&
				mainActivity.contains("NAVIC_READER_DEV_START_PROGRESS") &&
				mainActivity.contains("startProgress ="),
			"readerDev must support direct progress-fraction launch so resume/persistence validation can bypass manual navigation."
		)
		assertTrue(
			mainActivity.contains("navic.dev.reader.whispersync_sidecar_url") &&
				mainActivity.contains("NAVIC_READER_DEV_WHISPERSYNC_SIDECAR_URL") &&
				mainActivity.contains("whispersyncSidecarUrl =") &&
				mainActivity.contains("whispersyncAudiobookId =") &&
				mainActivity.contains("whispersyncAudiobookBookFileId ="),
			"readerDev must support direct paired Whispersync reader launch so emulator validation can bypass manual Bindery sheet navigation."
		)
		assertTrue(
			mainActivity.contains("Screen.BinderyBooks") &&
				!mainActivity.contains("showedSideloadingWarning") &&
				!app.contains("SideloadingDialog") &&
				!preferenceManager.contains("showedSideloadingWarning") &&
				!sideloadingDialog.exists(),
			"readerDev must fall back to Bindery Books when only Bindery credentials are seeded; the sideloading modal is globally removed and cannot block validation."
		)
		assertTrue(
			app.contains("fun App(initialScreenOverride: Screen? = null)") &&
				app.contains("initialScreenOverride ?: if (isLoggedIn)"),
			"App must accept a dev initial screen so the emulator can bypass library interaction and open an ebook."
		)
		assertTrue(
			readerRuntime.contains("setForceWebContentsDebuggingEnabled") &&
				readerRuntime.contains("forceWebContentsDebuggingEnabled || enableDebugging"),
			"ReaderWebRuntime must honor the forced dev diagnostic flag even if user settings are false."
		)
	}

	@Test
	fun readerDevScriptsAndSecretFilesAreDocumentedAndIgnored() {
		val gitignore = root.resolve(".gitignore").readText()
		val setupScript = root.resolve("scripts/setup-android-reader-dev.ps1")
		val installScript = root.resolve("scripts/install-reader-dev.ps1")
		val viewportScript = root.resolve("scripts/set-reader-dev-viewport.ps1")
		val envExample = root.resolve("navic-reader-dev.env.example")
		val spec = root.resolve("docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md").readText()
		val mainActivity = root.resolve("androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt").readText()
		val installScriptText = installScript.readText()

		assertTrue(setupScript.exists(), "The local Android reader lab must have an SDK/AVD setup script.")
		assertTrue(installScript.exists(), "The local Android reader lab must have a dirty build/install script.")
		assertTrue(viewportScript.exists(), "The local Android reader lab must have a viewport simulation script.")
		assertTrue(envExample.exists(), "The local Android reader lab must document the ignored credential env file.")
		assertTrue(
			gitignore.contains("navic-reader-dev.env"),
			"Local reader dev credentials must be ignored."
		)
		assertTrue(
			spec.contains("Required Emulator Gate") &&
				spec.contains("install-reader-dev"),
			"The active Komikku reader plan must record the emulator/dev-build loop."
		)
		assertTrue(
			installScriptText.contains("Resolve-ReaderDevPublicationFromBindery") &&
				installScriptText.contains("BINDERY_API_KEY_HEADER"),
			"The install script must be able to derive a reader launch target from Bindery OPDS credentials without printing secrets."
		)
		assertTrue(
			installScriptText.contains("Get-ReaderDevPublicationEbookLink") &&
				installScriptText.contains("http://opds-spec.org/acquisition") &&
				installScriptText.contains("Use catalog acquisition links before probing /resources"),
			"The install script must prefer EPUB/PDF acquisition links already present in /opds/books before slow per-book resource catalog probing."
		)
		assertTrue(
			installScriptText.contains("ConvertTo-AdbShellQuotedValue") &&
				installScriptText.contains("Add-ShellStringExtra") &&
				!installScriptText.contains("Add-StringExtra -Arguments \$launchArgs"),
			"The install script must quote adb shell string extras so discovered titles with spaces do not become stray am-start package arguments."
		)
		assertTrue(
			setupScript.readText().contains("immersive_mode_confirmations") &&
				installScriptText.contains("immersive_mode_confirmations") &&
				installScriptText.contains("confirmed"),
			"The readerDev loop must suppress Android's fullscreen education overlay so screenshots and gestures hit the reader surface."
		)
		assertTrue(
			installScriptText.contains("POST_NOTIFICATIONS") &&
				installScriptText.contains("grant") &&
				installScriptText.contains("notification permission"),
			"The readerDev install script must grant Android notification permission after install so the permission controller cannot block foreground reader validation."
		)
		assertTrue(
			installScriptText.contains("\$previousErrorActionPreference = \$ErrorActionPreference") &&
				installScriptText.contains("\$ErrorActionPreference = \"Continue\"") &&
				installScriptText.contains("\$output = & adb @adbArgs 2>&1"),
			"The install script must capture adb stderr/progress output and throw only on non-zero exit; adb pull progress is not a failed setup."
		)
		assertTrue(
			installScriptText.contains("function Invoke-GradleWrapper") &&
				installScriptText.contains("gradle-wrapper.jar") &&
				installScriptText.contains("function ConvertTo-ProcessArgument") &&
				installScriptText.contains("\$startInfo.Arguments =") &&
				!installScriptText.contains("\$startInfo.ArgumentList.Add") &&
				!installScriptText.contains("& .\\gradlew.bat") &&
				!installScriptText.contains("& ./gradlew.bat") &&
				!installScriptText.contains("& \$gradle") &&
				!installScriptText.contains("cmd.exe /c"),
			"The install script must invoke the Gradle wrapper jar directly through ProcessStartInfo; calling gradlew.bat creates cmd/conhost windows during reader validation."
		)
		assertTrue(
			installScriptText.contains("function Wait-ReaderDevForeground") &&
				installScriptText.contains("\"dumpsys\", \"activity\", \"activities\"") &&
				installScriptText.contains("mCurrentFocus") &&
				installScriptText.contains("Wait-ReaderDevForeground -Package \$Package"),
			"The install script must wait until the launched reader package is foreground before capture; otherwise screencap can record the Android launcher during the app transition."
		)
		assertTrue(
			installScriptText.contains("function Wait-ReaderDevPublicationReady") &&
				installScriptText.contains("\"logcat\", \"-d\"") &&
				installScriptText.contains("\"-t\", \"1000\"") &&
				installScriptText.contains("publicationReady") &&
				installScriptText.contains("Wait-ReaderDevPublicationReady -Package \$Package"),
			"The install script must wait for the reader bridge publicationReady event after launch using a bounded recent logcat window; foreground alone can still capture the splash screen, while full logcat dumps can fail on noisy emulators."
		)
		assertTrue(
			installScriptText.contains("[switch] \$RequireReaderLaunch") &&
				installScriptText.contains("Reader launch target required") &&
				installScriptText.contains("NAVIC_READER_DEV_PUBLICATION_URL") &&
				installScriptText.contains("NAVIC_READER_DEV_RESOURCE_HREF") &&
				installScriptText.contains("BINDERY_TEST_RESOURCE_ID"),
			"The install script must have a required-reader mode for validation; if no EPUB/PDF target is resolved it must fail instead of silently launching the catalog and producing false reader screenshots."
		)
		assertTrue(
			installScriptText.contains("NAVIC_READER_DEV_START_PROGRESS") &&
				installScriptText.contains("navic.dev.reader.start_progress"),
			"The install script must pass explicit progress-fraction reader starts for resume/persistence validation."
		)
		assertTrue(
			installScriptText.contains("[switch] \$SkipNativeShellCover") &&
				installScriptText.contains("navic.dev.reader.skip_native_shell_cover") &&
				mainActivity.contains("ReaderDevExtraSkipNativeShellCover") &&
				mainActivity.contains("skipNativeShellCover ="),
			"The readerdev launcher must be able to open directly on readable content without the native cover hiding text-page validation targets."
		)
		assertTrue(
			installScriptText.contains("[Alias(\"PublicationUrl\")]") &&
				installScriptText.contains("[Alias(\"ResourceHref\")]") &&
				installScriptText.contains("[Alias(\"BookId\")]") &&
				installScriptText.contains("\$ReaderPublicationUrl") &&
				installScriptText.contains("\$ReaderResourceHref") &&
				installScriptText.contains("\$ReaderBookId"),
			"The install script must accept explicit reader target CLI overrides so validation never needs manual am-start commands or env-file edits."
		)
		assertTrue(
			installScriptText.contains("function Resolve-ReaderDevExplicitBinderyResource") &&
				installScriptText.contains("Get-ReaderDevBookFileIdFromUrl") &&
				installScriptText.contains("properties.bookFileId") &&
				installScriptText.contains("Resolved explicit reader target to Bindery OPDS resource"),
			"The install script must canonicalize explicit direct /api/v1/book/{id}/file?bookFileId=... launches to the matching OPDS resource href so progress saves do not use resourceKey=file."
		)
		assertTrue(
			installScriptText.contains("NAVIC_READER_DEV_WHISPERSYNC_SIDECAR_URL") &&
				installScriptText.contains("navic.dev.reader.whispersync_sidecar_url") &&
				installScriptText.contains("NAVIC_READER_DEV_WHISPERSYNC_AUDIOBOOK_ID") &&
				installScriptText.contains("NAVIC_READER_DEV_WHISPERSYNC_AUDIOBOOK_BOOK_FILE_ID"),
			"The install script must pass paired Whispersync route metadata so emulator validation can open a real sidecar/audiobook reader session directly."
		)
		val smokeScriptText = root.resolve("scripts/adb-reader-smoke.ps1").readText()
		assertTrue(
			smokeScriptText.contains("[switch] \$RequireNeutralReaderVisualState") &&
				smokeScriptText.contains("function Assert-NeutralReaderVisualState") &&
				smokeScriptText.contains("History back") &&
				smokeScriptText.contains("Close history controls") &&
				smokeScriptText.contains("activeOverlayMarkerCount") &&
				smokeScriptText.contains("activeMediaOverlayMarkerCount"),
			"Visual acceptance captures must be able to fail on transient native/WebView overlays instead of recording polluted Komikku parity screenshots."
		)
		val viewportScriptText = viewportScript.readText()
		assertTrue(
			viewportScriptText.contains("zfold7-inner") &&
				viewportScriptText.contains("zfold7-cover") &&
				viewportScriptText.contains("tab-s9-ultra-landscape"),
			"The viewport script must simulate the user's Fold7 and Tab S9 Ultra reader dimensions."
		)
		assertTrue(
			viewportScriptText.contains("LockRotation = \$false") &&
				!Regex("""Rotation\s*=\s*"1"""").containsMatchIn(viewportScriptText),
			"Viewport profiles must simulate tablet/foldable dimensions through wm size/density, not by forcing Android rotation over an already-landscape override."
		)
	}

	@Test
	fun releaseWatcherUsesConditionPollingWithoutTimeoutCancellation() {
		val releaseScript = root.resolve("scripts/publish-github-release.ps1").readText()

		assertTrue(
			releaseScript.contains("[switch] \$AllowPublicRelease") &&
				releaseScript.contains("[string] \$ReleaseReadinessNote") &&
				releaseScript.contains("Use debug builds/readerdev installs for emulator iteration") &&
				releaseScript.contains("fully implemented, deployed in debug/readerdev, validated through its plan gates, committed") &&
				releaseScript.contains("ready for physical-device acceptance"),
			"The public release script must fail closed unless a coherent candidate is explicitly marked release-worthy."
		)
		assertTrue(
			releaseScript.contains("[int] \$PollSeconds") &&
				releaseScript.contains("Start-Sleep -Seconds \$PollSeconds"),
			"The release watcher may keep a polling heartbeat while waiting for GitHub Actions and release assets."
		)
		assertTrue(
			!releaseScript.contains("TimeoutMinutes") &&
				!releaseScript.contains("AddMinutes(") &&
				!releaseScript.contains("Timed out waiting") &&
				!releaseScript.contains("was not visible after"),
			"The release watcher must not use timeout/deadline cancellation as the release control path."
		)
		assertTrue(
			releaseScript.contains("while (\$RunId -le 0)") &&
				releaseScript.contains("while (\$true)"),
			"The release watcher must continue until GitHub reports a workflow run, workflow completion, or release publication."
		)
	}

	@Test
	fun releasePackageReaderValidationHasCredentialSafeLoginAutomation() {
		val gitignore = root.resolve(".gitignore").readText()
		val releaseLoginScript = root.resolve("scripts/adb-release-login.ps1")
		val envExample = root.resolve("navic-release-login.env.example")
		val plan = root
			.resolve("docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md")
			.readText()

		assertTrue(
			releaseLoginScript.exists(),
			"The public release validation loop must have an adb release-login script so reader/Whispersync checks do not stay readerdev-only."
		)
		assertTrue(
			envExample.exists(),
			"Release login credentials must be documented in an example env file, never committed as real secrets."
		)
		assertTrue(
			gitignore.contains("navic-release-login.env"),
			"Local release login credentials must be ignored."
		)
		assertTrue(
			plan.contains("Stage 8C: Release Login And Reader Route Automation") &&
				plan.contains("darkaxt.navic") &&
				plan.contains("readerdev remains the seeded implementation lab"),
			"The gap plan must keep release-package login validation separate from readerdev implementation evidence."
		)

		val scriptText = releaseLoginScript.readText()
		assertTrue(
			scriptText.contains("[string] \$Package = \"darkaxt.navic\"") &&
				scriptText.contains("[string] \$DeviceSerial") &&
				scriptText.contains("[switch] \$DetectOnly") &&
				scriptText.contains("function Assert-SingleAdbDeviceOrSelectedSerial") &&
				scriptText.contains("Pass -DeviceSerial") &&
				scriptText.contains("function Invoke-Adb") &&
				scriptText.contains("\$env:ANDROID_SERIAL = \$DeviceSerial"),
			"The release-login script must target the public package by default and route every adb command to the selected serial."
		)
		assertTrue(
			scriptText.contains("NAVIC_INSTANCE_URL") &&
				scriptText.contains("NAVIDROME_BASE_URL") &&
				scriptText.contains("NAVIC_USERNAME") &&
				scriptText.contains("NAVIDROME_USERNAME") &&
				scriptText.contains("NAVIC_PASSWORD") &&
				scriptText.contains("NAVIDROME_PASSWORD"),
			"The release-login script must accept the documented Navic/Navidrome credential aliases."
		)
		assertTrue(
			scriptText.contains("Missing release login env keys") &&
				scriptText.contains("Write-RedactedCredentialSummary") &&
				scriptText.contains("value: <redacted>") &&
				!scriptText.contains("Write-Host \$instanceUrl") &&
				!scriptText.contains("Write-Host \$username") &&
				!scriptText.contains("Write-Host \$password"),
			"The release-login script must list missing key names but never print credential values."
		)
		assertTrue(
			scriptText.contains("uiautomator") &&
				scriptText.contains("Log in") &&
				scriptText.contains("Instance URL") &&
				scriptText.contains("Username") &&
				scriptText.contains("Password") &&
				scriptText.contains("Invoke-ReleaseLoginDetection") &&
				scriptText.contains("detectOnly=true"),
			"The release-login script must detect the actual Navic login screen without requiring credential submission."
		)
		assertTrue(
			scriptText.contains("navic-release-login-window.xml") &&
				scriptText.contains("release-login-summary.txt"),
			"The release-login script must write artifacts for later validation-log evidence."
		)
	}

	@Test
	fun whispersyncEnjoymentGateRunsTheWholePairedReaderdevMatrix() {
		val gateScript = root.resolve("scripts/adb-whispersync-enjoyment.ps1")
		val plan = root
			.resolve("docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md")
			.readText()
		val validationLog = root
			.resolve("docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md")
			.readText()

		assertTrue(
			gateScript.exists(),
			"Stage 5C must have one executable Whispersync enjoyment gate instead of relying on scattered manual probe commands."
		)
		assertTrue(
			plan.contains("Stage 5C.3: Whispersync Enjoyment Gate Orchestrator") &&
				plan.contains("adb-whispersync-enjoyment.ps1"),
			"The gap plan must record the orchestrated Whispersync gate as the active Stage 5C closure path."
		)

		val scriptText = gateScript.readText()
		assertTrue(
			scriptText.contains("install-reader-dev.ps1") &&
				scriptText.contains("adb-reader-smoke.ps1") &&
				scriptText.contains("Invoke-WhispersyncProbe"),
			"The enjoyment gate must launch the paired readerdev route and then run focused smoke probes by name."
		)
		assertTrue(
			scriptText.contains("https://bindery.remaxku.eu/book/3809") &&
				scriptText.contains("https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426") &&
				scriptText.contains("/opds/books/3809/sync/8") &&
				scriptText.contains("[string] \$ReaderWhispersyncAudiobookId = \"34\"") &&
				scriptText.contains("[string] \$ReaderWhispersyncAudiobookBookFileId = \"633\""),
			"The enjoyment gate must default to the real production paired book 3809 route used by the Whispersync acceptance evidence."
		)
		assertTrue(
			scriptText.contains("whispersync-page-scoped-control") &&
				scriptText.contains("whispersync-audio-follow") &&
				scriptText.contains("whispersync-char-offset-overlay") &&
				scriptText.contains("whispersync-companion-progress"),
			"The enjoyment gate must cover page-to-audio, audio-follow, char-offset highlight, and exact companion progress in one run."
		)
		assertTrue(
			scriptText.contains("stage5c3-whispersync-enjoyment-summary.txt") &&
				scriptText.contains("probe-results.jsonl") &&
				scriptText.contains("Result=PASS"),
			"The enjoyment gate must write a compact machine-readable probe summary and a human-readable stage summary."
		)
		assertTrue(
			scriptText.contains("Secrets are passed through the existing readerdev launcher and are not printed here") &&
				!scriptText.contains("Write-Host \$apiKey") &&
				!scriptText.contains("Write-Host \$envValues") &&
				!scriptText.contains("Get-Content \$EnvFile"),
			"The enjoyment gate must not print Bindery credentials or raw env-file contents."
		)
		assertTrue(
			plan.contains("Stage 5C.5: Credential-Bootstrapped Whispersync Enjoyment Validation") &&
				plan.contains("Stage 5C.6: Signed Release Whispersync Packaging Validation") &&
				!Regex("""Stage 5C\.5\s+release-package""").containsMatchIn(validationLog) &&
				!validationLog.contains("Stage 5C.5 remains the release-package proof gate"),
			"Stage 5C.5 is the credential-bootstrapped readerdev proof path; signed public APK proof belongs to Stage 5C.6."
		)
	}

	@Test
	fun whispersyncEnjoymentGateDefaultExpectedVersionTracksAndroidReleaseIdentity() {
		val androidBuild = root.resolve("androidApp/build.gradle.kts").readText()
		val gateScript = root.resolve("scripts/adb-whispersync-enjoyment.ps1").readText()
		val androidVersion = Regex("""versionName\s*=\s*"([^"]+)"""")
			.find(androidBuild)
			?.groupValues
			?.get(1)
			.orEmpty()

		assertTrue(
			androidVersion.isNotBlank(),
			"The Android app must declare a release versionName."
		)
		assertTrue(
			gateScript.contains("""[string] ${'$'}ExpectedVersionName = """"") &&
				gateScript.contains("function Get-AndroidReleaseVersionName") &&
				gateScript.contains("versionName\\s*=\\s*\"([^\"]+)\"") &&
				gateScript.contains("if ([string]::IsNullOrWhiteSpace(${ '$' }ExpectedVersionName))") &&
				gateScript.contains("${ '$' }ExpectedVersionName = Get-AndroidReleaseVersionName"),
			"The Whispersync enjoyment gate must derive its default ExpectedVersionName from androidApp/build.gradle.kts instead of hardcoding a release tag like $androidVersion."
		)
	}

	@Test
	fun whispersyncEnjoymentGateRequiresRealNativePlaybackFeedback() {
		val gateScript = root.resolve("scripts/adb-whispersync-enjoyment.ps1").readText()
		val closeHistoryIndex = gateScript.indexOf("tapDescIfPresent:Close history controls")
		val backIndex = gateScript.indexOf("keyevent:4,1000")

		assertTrue(
			gateScript.contains("-PostProbeAction") &&
				gateScript.contains("tapDescWhenPresent:Play Whispersync audiobook") &&
				gateScript.contains("keyevent:4,1000"),
			"The Whispersync enjoyment gate must press the native headset playback control and leave the reader; DevTools-only overlay probes cannot prove audio playback feedback or lifecycle pause."
		)
		assertFalse(
			gateScript.contains("keyevent:KEYCODE_BACK"),
			"The lifecycle gate must use numeric Android BACK (4): emulator evidence showed the KEYCODE_BACK string path can fail to drive the reader back handler."
		)
		assertTrue(
			closeHistoryIndex >= 0 && closeHistoryIndex < backIndex,
			"The lifecycle gate must close transient history controls before pressing Back; emulator evidence showed the first Back can dismiss history chrome without leaving the reader or pausing Whispersync."
		)
		assertTrue(
			gateScript.contains("-RequireReaderLog") &&
				gateScript.contains("Whispersync activeSegment") &&
				gateScript.contains("ApplyMediaOverlay") &&
				gateScript.contains("overlayFragmentActive") &&
				gateScript.contains("Pausing Whispersync audiobook on reader exit"),
			"The Whispersync enjoyment gate must fail unless real playback produces activeSegment, ApplyMediaOverlay, overlayFragmentActive, and reader-exit pause evidence in the same paired session."
		)
	}

	@Test
	fun komikkuMatrixCanPrepareNativeCoverStartStateBeforeCoverChecks() {
		val matrixScript = root.resolve("scripts/adb-reader-komikku-matrix.ps1").readText()

		assertTrue(
			matrixScript.contains("[switch] \$PrepareReaderLaunch") &&
				matrixScript.contains("[string] \$PrepareStartProgress = \"0\""),
			"The Komikku matrix must expose a controlled launch mode so cover checks do not depend on stale emulator state."
		)
		assertTrue(
			matrixScript.contains("[string] \$PreparePublicationUrl") &&
				matrixScript.contains("[string] \$PrepareBookId") &&
				matrixScript.contains("[string] \$PrepareWhispersyncSidecarUrl"),
			"The Komikku matrix must let prepared launches target a concrete production EPUB/Whispersync route."
		)
		assertTrue(
			matrixScript.contains("\$installReaderDevScript = Join-Path \$scriptRoot \"install-reader-dev.ps1\"") &&
				matrixScript.contains("Invoke-ReaderMatrixPrepareLaunch") &&
				matrixScript.contains("NoBuild = \$true") &&
				matrixScript.contains("NoInstall = \$true") &&
				matrixScript.contains("RequireReaderLaunch = \$true"),
			"The Komikku matrix must reuse the no-console readerdev launcher instead of duplicating manual adb am-start logic."
		)
		assertTrue(
			matrixScript.contains("StartProgress = \$PrepareStartProgress") &&
				matrixScript.contains("PublicationUrl = \$PreparePublicationUrl") &&
				matrixScript.contains("WhispersyncSidecarUrl = \$PrepareWhispersyncSidecarUrl") &&
				matrixScript.contains("if (\$PrepareReaderLaunch)") &&
				matrixScript.contains("-NoLaunch"),
			"Cover validation must launch the reader at the native shell-cover boundary, then run smoke checks without relaunching over that state."
		)
		assertTrue(
			matrixScript.contains("if ([string]::IsNullOrWhiteSpace(\$PrepareStartHref) -and [string]::IsNullOrWhiteSpace(\$PrepareStartCfi))") &&
				matrixScript.contains("StartProgress = \$PrepareStartProgress"),
			"Prepared launches with an explicit href/CFI must not also send the default zero progress extra, or the href target is bypassed."
		)
	}

	@Test
	fun komikkuReaderPlanRequiresReferenceParityBeforeReaderWork() {
		val spec = root.resolve("docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md").readText()

		assertTrue(
			spec.contains("## Reference Authority"),
			"The active reader plan must keep reference parity as a top-level procedure, not only as transient chat context."
		)
		assertTrue(
			spec.contains("Komikku is authoritative for the reader UI layer") &&
				spec.contains("Anx Reader/Foliate is authoritative for the reader behavior layer"),
			"Reader work must distinguish Komikku UI/shell authority from Anx/Foliate engine authority before implementation."
		)
		assertTrue(
			spec.contains("If a Navic feature works but is not faithful to the reference, treat it as unfinished") &&
				spec.contains("Do not polish or build dependent behavior on a non-faithful workaround"),
			"Working but non-faithful reader behavior must be treated as incomplete and redesigned."
		)
		assertTrue(
			spec.contains("Every Anx bridge callback/event exposed by the reference EPUB/Foliate layer must have a Navic bridge/engine counterpart") &&
				spec.contains("Missing Anx events are failing behavior parity, not optional future polish"),
			"Working but non-faithful behavior must block dependent work and release candidates until redesigned."
		)
		assertTrue(
			spec.contains("## Non-Negotiable Guardrails") &&
				spec.contains("Do not invent Navic-specific reader behavior where Anx already defines a bridge callback"),
			"The per-slice acceptance map must force concrete reference matching for both new features and fixes."
		)
	}
}

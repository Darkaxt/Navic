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
		val releaseBlock = androidBuild
			.substringAfter("getByName(\"release\")")
			.substringBefore("getByName(\"debug\")")
		val readerDevBlock = androidBuild.substringAfter("create(\"readerDev\")")

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
			releaseBlock.contains("buildConfigField(\"boolean\", \"NAVIC_READER_DEV\", \"false\")"),
			"release must explicitly compile out the reader-dev force path."
		)
		assertTrue(
			readerDevBlock.contains("buildConfigField(\"boolean\", \"NAVIC_READER_DEV\", \"true\")"),
			"readerDev must expose a BuildConfig flag so the app can force reader diagnostics."
		)
		assertTrue(
			readerDevBlock.contains("matchingFallbacks += listOf(\"debug\")"),
			"readerDev must consume debug variants from reusable Android libraries without requiring Navic-specific build types."
		)
		assertTrue(
			mainActivity.contains("ReaderWebRuntime.acquireForcedWebContentsDebugging(BuildConfig.NAVIC_READER_DEV)"),
			"MainActivity must acquire scoped WebView debugging for readerDev before the reader surface is mounted."
		)
		assertTrue(
			mainActivity.contains("override fun onDestroy()") &&
				mainActivity.contains("readerDevWebDebuggingLease?.close()"),
			"MainActivity must release its reader-dev WebView debugging lease when disposed."
		)
		assertFalse(
			mainActivity.contains("setForceWebContentsDebuggingEnabled"),
			"Reader-dev debugging must not use an unscoped process-lifetime setter."
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
			readerRuntime.contains("acquireForcedWebContentsDebugging") &&
				readerRuntime.contains("webContentsDebuggingForceRegistry.isForced() || enableDebugging"),
			"ReaderWebRuntime must honor live forced dev leases even if user settings are false."
		)
	}

	@Test
	fun readerQaFaultReceiverExistsOnlyInReaderDevMergedManifest() {
		val receiver =
			"paige.navic.androidApp.ReaderPageQaFaultReceiver"
		val action =
			"darkaxt.navic.readerdev.READER_QA_FAULT"
		val senderPermission = "android.permission.DUMP"
		val readerDev = mergedManifest("readerDev")
		val debug = mergedManifest("debug")
		val release = mergedManifest("release")
		val androidBuild =
			root.resolve("androidApp/build.gradle.kts").readText()
		val receiverSource = root.resolve(
			"androidApp/src/readerDev/kotlin/paige/navic/androidApp/" +
				"ReaderPageQaFaultReceiver.kt"
		).readText()
		val actionGate = receiverSource.indexOf(
			"ReaderPageQaFaultCommandDecoder.acceptsAction(intent.action)"
		)
		val firstExtraRead = receiverSource.indexOf("intent.getStringExtra")

		assertTrue(androidBuild.contains("releaseBuildRequested"))
		assertTrue(androidBuild.contains("task.contains(\"Release\")"))
		assertTrue(androidBuild.contains("(isRelease || releaseBuildRequested) && !hasReleaseSigning"))
		assertTrue(readerDev.contains("PlayLikeCurlReferenceActivity"))
		assertTrue(readerDev.contains(receiver))
		assertTrue(readerDev.contains(action))
		assertTrue(readerDev.contains(senderPermission))
		assertFalse(debug.contains(receiver))
		assertFalse(debug.contains(action))
		assertFalse(release.contains(receiver))
		assertFalse(release.contains(action))
		assertTrue(actionGate >= 0 && firstExtraRead > actionGate)
		assertTrue(receiverSource.contains("ReaderPageQaFaultCommandDecoder.decode"))
		listOf(
			"requireNotNull",
			"ReaderPageQaFault.valueOf",
			"error("
		).forEach { forbidden -> assertFalse(receiverSource.contains(forbidden)) }
		assertFalse(
			Regex("Log\\.[a-zA-Z]+\\([^)]*intent\\.getStringExtra")
				.containsMatchIn(receiverSource)
		)
	}

	@Test
	fun readerQaRunnerBindsTheInstalledPackageAndCompleteCandidateTree() {
		val runner = root.resolve(
			"scripts/adb-reader-playlikecurl-qa.ps1"
		).readText()

		assertTrue(
			runner.contains("git diff HEAD --name-status --no-renames"),
			"Precommit evidence must include staged-only tracked changes."
		)
		assertTrue(
			runner.contains(
				"'(?m)^\\s*versionName=(?<Value>[^\\r\\n]*)\\r?$'"
			),
			"Installed package parsing must accept adb's CRLF dumpsys output."
		)
		assertTrue(
			runner.contains("Get-FileHash -Algorithm SHA256") &&
				runner.contains("installed package identity mismatch"),
			"The runner must verify the installed APK against the sealed build."
		)
		assertTrue(
			runner.contains("\$readerLaunchTimeoutSeconds = 120") &&
				runner.split("-WaitTimeoutSeconds \$readerLaunchTimeoutSeconds")
					.size == 3,
			"The runner must allow both cold and warm publication launches enough time to finish on the bounded ReaderDev emulator."
		)
		assertTrue(
			runner.contains("function Wait-ReaderWarmupOwnership") &&
				runner.contains("\$_.Phase -eq 'peak-preparation' -or") &&
				runner.contains("\$_.Phase -eq 'steady-state'") &&
				runner.contains("\$warmupSnapshot = Wait-ReaderWarmupOwnership") &&
				runner.contains("\$reopened = Wait-ReaderWarmupOwnership") &&
				!runner.contains("Wait-ReaderSteadyState"),
			"Warmup and reopen must admit a prepared-deck ownership snapshot; steady-state snapshots are turn-completion evidence."
		)
		assertTrue(
			runner.contains("function Wait-ReaderPid") &&
				runner.split("\$script:ReaderPid = Wait-ReaderPid").size == 3,
			"Initial and reopened ReaderDev launches must wait for one bounded process identity before reading PID-scoped diagnostics."
		)
		assertTrue(
			runner.contains(
				"\$initialPreparationSnapshot = Wait-ReaderWarmupOwnership 'ReaderDev initial preparation'"
			) &&
				runner.contains(
					"Assert-OwnershipWithinBounds @(\$initialPreparationSnapshot) 'ReaderDev initial preparation'"
				) &&
				runner.contains("\$script:ReaderPid = \$null"),
			"The first launch must finish and persist a prepared deck before force-stop so the warm reopen can prove persistent hydration."
		)
		val stressRetryOutcomes = runner
			.substringAfter("\$transientRetryOutcomes = @(")
			.substringBefore(")")
		assertTrue(
			stressRetryOutcomes.contains("'CancelledByUser',") &&
				stressRetryOutcomes.contains("'RejectedPreparing',") &&
				stressRetryOutcomes.contains("'RejectedSettling',") &&
				stressRetryOutcomes.contains("'RejectedRendererUnavailable'") &&
				runner.contains("if (\$newTerminal.Outcome -in \$transientRetryOutcomes)"),
			"The stress sweep must retry bounded snap-backs and transient readiness rejections instead of requiring turn directions from them."
		)
		assertTrue(
			runner.split("-AllowPendingRecovery").size == 3 &&
				runner.contains("-Context 'ReaderDev completed stress interval'"),
			"Stress polling must admit a pending recoverable ownership callback, then require recovery before accepting the completed interval."
		)
		assertTrue(
			runner.contains("\$maximumConsecutiveNoTerminalAttempts = 3") &&
				runner.contains("\$consecutiveNoTerminalAttempts += 1") &&
				runner.contains("\$consecutiveNoTerminalAttempts = 0") &&
				runner.contains("ReaderDev stress emitted no gesture terminal for") &&
				runner.contains("Start-Sleep -Milliseconds 250") &&
				runner.contains("continue"),
			"A swipe that lands during a bounded refill must be retried, while repeated missing terminals still fail the gate."
		)
		assertTrue(
			runner.contains("function Invoke-ReaderQaFaultMatrix") &&
				listOf(
					"FailNextPersistence",
					"PauseNextPublication",
					"MissNextRasterLoad",
					"ForceRepairWithoutPreparedDeck",
					"DelayNextVisualStateCallback",
					"DelayNextRelocationAcknowledgement"
				).all(runner::contains) &&
				runner.contains("Assert-ReaderQaFaultCorrelation") &&
				runner.contains("logcat-fault-injection.txt"),
			"The emulator runner must execute and persist operation-correlated persistence, repair, and delayed-callback fault evidence."
		)
		val hierarchyDump = runner
			.substringAfter("function Invoke-ReaderQaUiHierarchy")
			.substringBefore("function Invoke-ReaderQaPreparationRetry")
		val retryControl = runner
			.substringAfter("function Invoke-ReaderQaPreparationRetry")
			.substringBefore("function Invoke-ReaderPersistenceFault")
		assertTrue(
			runner.contains("function Wait-ReaderPreparedDeckOwnership") &&
				runner.contains("function Invoke-ReaderQaPreparationRetry") &&
				hierarchyDump.split(
					"if ([DateTime]::UtcNow -ge \$DeadlineUtc) { return '' }"
				).size == 3 &&
				hierarchyDump.contains("(\$DeadlineUtc - [DateTime]::UtcNow)") &&
				hierarchyDump.contains("WaitForExit(\$remainingMilliseconds)") &&
				hierarchyDump.contains("Kill(\$true)") &&
				hierarchyDump.contains("'uiautomator', 'dump', '/dev/tty'") &&
				retryControl.contains("Invoke-ReaderQaUiHierarchy `") &&
				retryControl.contains("-DeadlineUtc \$deadline") &&
				retryControl.contains("\$remainingMilliseconds") &&
				retryControl.contains("\$deadline = [DateTime]::UtcNow.AddSeconds(15)") &&
				retryControl.contains("[Math]::Min(250, \$remainingMilliseconds)") &&
				retryControl.contains("Start-Sleep -Milliseconds \$sleepMilliseconds") &&
				retryControl.contains("while ([DateTime]::UtcNow -lt \$deadline)") &&
				runner.contains("ReaderDev durable persistence retry") &&
				runner.contains("\$_.QaFaultRelation -eq 'Retry'") &&
				runner.contains("\$_.Result -eq 'Durable'"),
			"The persistence fault interval must recover durably before the process is restarted for warm-reopen validation."
		)
		assertTrue(
			runner.contains("\$maximumRepairFaultAttempts = 5") &&
				runner.contains("\$missIds = [Collections.Generic.List[string]]::new()") &&
				runner.contains("Kind = 'RepairTerminated'") &&
				runner.contains("ReaderDev forced repair exhausted its bounded attempts"),
			"A full preparation may validly supersede a synthetic miss repair, so the fault matrix must retry with fresh miss identities until the queued forced-role seam is applied."
		)
		assertTrue(
			runner.contains("function Wait-ReaderQaRelocationTerminal") &&
				runner.contains("function Wait-ReaderQaWorkingSetReady") &&
				runner.contains("-AfterIndex \$visualTurn.Index") &&
				runner.contains("\$turnRightToLeft = \$repairFaultAttempt % 2 -eq 0") &&
				runner.contains("-States @('Completed', 'Rejected')") &&
				runner.contains("ReaderDev repaired active deck proof turn"),
			"Forced active repair must isolate the prior refill, turn away from the imminent publication boundary, accept either valid relocation terminal, and prove that the prepared recovery deck accepts the next turn."
		)
		assertTrue(
			runner.contains("\$script:ReaderAccumulatedLogLines") &&
				runner.contains("function Reset-ReaderLogAccumulator") &&
				runner.contains("ReaderAccumulatedLogLineSet.Add"),
			"The runner must accumulate PID snapshots so the circular logcat buffer cannot evict early stress evidence."
		)
		assertTrue(
			runner.contains("\$script:ReaderLogcatCursor") &&
				runner.contains("\$script:ReaderAccumulatedDiagnosticLogLines") &&
				runner.contains("\$arguments.Add('-T')") &&
				runner.contains("Assert-ReaderRuntimeLogSafe -Log \$newRawLog") &&
				runner.contains("\$ReaderDiagnosticIntroducerPattern.IsMatch(\$newRawLog)") &&
				runner.contains("[switch] \$Full") &&
				runner.contains("\$recentDiagnosticWindow = 1024"),
			"Polling must ingest logcat incrementally, validate every new raw line, and parse a bounded diagnostic window while retaining the complete interval for final evidence."
		)
		assertTrue(
			runner.contains("ReaderDev stress emitted an unexpected terminal") &&
				runner.contains("run-failed.json") &&
				runner.contains("failure-diagnostics.txt") &&
				runner.contains("Assert-ReaderDiagnosticRecordSet") &&
				runner.contains("--es command clear") &&
				runner.contains("} finally {"),
			"Unexpected outcomes must preserve only closed-schema diagnostics before every failed run releases QA-owned callbacks."
		)
	}

	@Test
	fun readerQaRunnerInjectsPersistenceFaultBeforeBackgroundPrefetch() {
		val runner = root.resolve(
			"scripts/adb-reader-playlikecurl-qa.ps1"
		).readText()
		val initialLaunch = runner
			.substringAfter("\$runSucceeded = \$false")
			.substringBefore("\$faultMatrixLog = Invoke-ReaderQaFaultMatrix")
		val clearIndex = initialLaunch.indexOf(
			"Invoke-Adb @('shell', 'pm', 'clear', 'darkaxt.navic.readerdev')"
		)
		val launchIndex = initialLaunch.indexOf(
			".\\scripts\\install-reader-dev.ps1"
		)
		val prearmIndex = initialLaunch.indexOf(
			"-ReaderQaFaultRequestId \$persistenceRequestId"
		)
		val pidIndex = initialLaunch.indexOf(
			"\$script:ReaderPid = Wait-ReaderPid 'ReaderDev initial launch'"
		)
		val faultIndex = initialLaunch.indexOf(
			"\$persistenceFault = Invoke-ReaderPersistenceFault `\n    -RequestId \$persistenceRequestId"
		)
		val warmupIndex = initialLaunch.indexOf(
			"\$initialPreparationSnapshot = Wait-ReaderWarmupOwnership"
		)

		assertTrue(clearIndex >= 0, "The run must start from empty ReaderDev app data.")
		assertTrue(
			launchIndex > clearIndex && prearmIndex > launchIndex &&
				pidIndex > prearmIndex &&
				initialLaunch.substring(launchIndex, pidIndex)
					.contains("-ReaderQaFault 'FailNextPersistence'") &&
				initialLaunch.substring(launchIndex, pidIndex)
					.contains("-EnableCanvasPageTurn"),
			"The deterministic persistence fault must travel on the canvas-enabled Activity launch that begins foreground preparation."
		)
		assertTrue(
			faultIndex > pidIndex && warmupIndex > faultIndex,
			"The runner must validate persistence recovery before prepared-deck completion can start background prefetch."
		)

		val persistenceFault = runner
			.substringAfter("function Invoke-ReaderPersistenceFault")
			.substringBefore("function Invoke-ReaderQaFaultMatrix")
		assertFalse(
			persistenceFault.contains("Invoke-ReaderQaCommittedTurn"),
			"The persistence fault must not race an already-running adjacent prefetch behind a committed turn."
		)
		assertTrue(
			persistenceFault.contains("Wait-ReaderQaFaultState `") &&
				persistenceFault.contains("\$RequestId 'Enqueued'") &&
				persistenceFault.contains("ReaderSession = \$readerSession"),
			"The launched reader must acknowledge the prearmed fault before the runner accepts its recovery evidence."
		)
		val failedPublicationWait = persistenceFault
			.substringAfter("\$failedPublication = Wait-ReaderQaCondition `")
			.substringBefore("\$preparationFailure = Wait-ReaderQaCondition `")
		val preparationFailureWait = persistenceFault
			.substringAfter("\$preparationFailure = Wait-ReaderQaCondition `")
			.substringBefore("Invoke-ReaderQaPreparationRetry")
		val preparationRetryWait = persistenceFault
			.substringAfter("-Context 'ReaderDev persistence preparation retry' `")
			.substringBefore("\$evidenceLog = Read-ReaderPidLog")
		assertTrue(
			failedPublicationWait.contains("-Full `") &&
				preparationFailureWait.contains("-Full `") &&
				preparationRetryWait.contains("-Full `"),
			"Same-poll and split-poll publication/preparation evidence must compare indexes from the shared full diagnostic stream."
		)
		assertTrue(
			persistenceFault.contains(
				"\$failedPublication = Wait-ReaderQaCondition `"
			) && persistenceFault.contains(
				"\$_.Index -gt \$failedPublication.Match.Index"
			),
			"A startup failure that predates the injected publication failure must not satisfy persistence-fault recovery."
		)

		val installer = root.resolve("scripts/install-reader-dev.ps1").readText()
		val installBlockIndex = installer.indexOf("if (!\$NoInstall) {")
		val permissionGrantIndex = installer.indexOf(
			"if (!\$NoInstall -or !\$NoLaunch) {\n" +
				"    Grant-ReaderDevNotificationPermission -Package \$Package\n" +
				"}"
		)
		val activityLaunchIndex = installer.indexOf("if (!\$NoLaunch) {")
		assertTrue(
			installBlockIndex >= 0 && permissionGrantIndex > installBlockIndex &&
				activityLaunchIndex > permissionGrantIndex,
			"Every ReaderDev launch, including -NoInstall after pm clear, must restore notification permission before Activity startup."
		)
		val mainActivity = root.resolve(
			"androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt"
		).readText()
		assertTrue(
			installer.contains("navic.dev.reader.qa_fault_request_id") &&
				installer.contains("navic.dev.reader.qa_fault") &&
				installer.contains("navic.dev.reader.canvas_page_turn"),
			"The launcher must place the bounded QA command and explicit canvas mode on the same Activity intent as the publication seed."
		)
		assertTrue(
			mainActivity.contains("applyReaderDevQaFaultSeed(intent)") &&
				mainActivity.contains("ReaderPageQaFaultCommandDecoder.decode(") &&
				mainActivity.contains("ReaderPageQaFaultControl.enqueue(") &&
				mainActivity.contains(
					"preferenceManager.readerDragAnimationMode = ReaderDragAnimationCanvas"
				),
			"ReaderDev must synchronously enable the canvas and prearm the validated fault before Compose attaches the reader registry."
		)
	}

	private fun mergedManifest(variant: String): String {
		val intermediates =
			root.resolve("androidApp/build/intermediates").toFile()
		val marker = "/${variant.lowercase()}/"
		val manifests = intermediates.walkTopDown()
			.filter { file ->
				val normalized =
					file.invariantSeparatorsPath.lowercase()
				file.isFile &&
					file.name == "AndroidManifest.xml" &&
					normalized.contains("/merged_manifest/") &&
					normalized.contains(marker)
			}
			.toList()
		assertTrue(
			manifests.isNotEmpty(),
			"Run readerQaProcessVariantManifests before this test; missing variant=$variant"
		)
		return manifests.joinToString("\n") { it.readText() }
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
		assertTrue(installScriptText.contains("Discovered Bindery reader target (format="))
		assertTrue(installScriptText.contains("Resolved explicit reader target to Bindery OPDS resource (format="))
		assertFalse(installScriptText.contains("Discovered Bindery reader target: {0}"))
		assertFalse(installScriptText.contains("Bindery OPDS resource: {0}"))
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
			installScriptText.contains("[switch] \$RedactFailure") &&
				installScriptText.contains("if (\$RedactFailure) {") &&
				installScriptText.contains("ReaderDev launch failed with exit code") &&
				installScriptText.contains(
					"Invoke-Adb -Arguments \$launchArgs.ToArray() -RedactFailure"
				),
			"Secret-bearing ReaderDev launch failures must not print adb extras or adb output containing credentials, URLs, hrefs, book identifiers, or reader-session metadata."
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
				installScriptText.contains("mFocusedApp") &&
				installScriptText.contains("topResumedActivity") &&
				installScriptText.contains("ResumedActivity") &&
				installScriptText.contains("Wait-ReaderDevForeground -Package \$Package"),
			"The install script must recognize current and modern resumed-activity fields before capture; otherwise it can time out after a successful reader launch."
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
			installScriptText.contains("[switch] \$NoForceStopLaunch") &&
				installScriptText.contains("[switch] \$PreserveLogcat") &&
				installScriptText.contains("[int] \$WaitTimeoutSeconds = 60") &&
				installScriptText.contains("[ValidateRange(1, 600)]") &&
				installScriptText.contains("[DateTime]::UtcNow.AddSeconds(\$WaitTimeoutSeconds)") &&
				installScriptText.contains("if (-not \$NoForceStopLaunch) { \$launchArgs.Add(\"-S\") }") &&
				installScriptText.contains("if (\$readerLaunchHasPublication -and -not \$PreserveLogcat)"),
			"ReaderDev relaunches must have a bounded timeout and let the QA runner preserve the process and measured logcat interval."
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
				installScriptText.contains("Resolved explicit reader target to Bindery OPDS resource (format="),
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
		val qaRunnerScriptText = root.resolve("scripts/adb-reader-playlikecurl-qa.ps1").readText()
		val matrixScriptText = root.resolve("scripts/adb-reader-komikku-matrix.ps1").readText()
		val postStressQaRunnerText = qaRunnerScriptText.substringAfter("logcat-stress.txt")
		val privacySafeMatrixSetup = matrixScriptText
			.substringAfter("\$smokeArgs = @{")
			.substringBefore("if (\$InstallApk")
		assertTrue(
			smokeScriptText.contains("[switch] \$PreserveLogcat") &&
				smokeScriptText.contains("if (-not \$PreserveLogcat) {") &&
				postStressQaRunnerText.contains("-RequireNoReaderConsoleErrors") &&
				postStressQaRunnerText.contains("-RequireNeutralReaderVisualState") &&
				postStressQaRunnerText.contains("-PreserveLogcat") &&
				privacySafeMatrixSetup.contains(
					"\$smokeArgs.RequireNeutralReaderVisualState = \$true"
				),
			"Privacy-safe smoke orchestration must preserve the stress log and require a proven neutral visual state before sealing evidence."
		)
		assertTrue(
			smokeScriptText.contains("\$script:NeutralVisualState = \$false") &&
				smokeScriptText.contains("\$script:NeutralVisualState = \$true") &&
				smokeScriptText.indexOf("\$script:NeutralVisualState = \$true") >
					smokeScriptText.indexOf("function Assert-NeutralReaderVisualState"),
			"Privacy-safe smoke summaries must report a neutral visual state only after the neutral-state assertion runs successfully."
		)
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

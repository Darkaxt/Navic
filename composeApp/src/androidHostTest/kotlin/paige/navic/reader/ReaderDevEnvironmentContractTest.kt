package paige.navic.reader

import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
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
			mainActivity.contains("Screen.BinderyBooks") &&
				mainActivity.contains("preferenceManager.showedSideloadingWarning = true"),
			"readerDev must fall back to Bindery Books when only Bindery credentials are seeded and must not block validation with the sideloading modal."
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
			installScriptText.contains("\$previousErrorActionPreference = \$ErrorActionPreference") &&
				installScriptText.contains("\$ErrorActionPreference = \"Continue\"") &&
				installScriptText.contains("\$output = & adb @adbArgs 2>&1"),
			"The install script must capture adb stderr/progress output and throw only on non-zero exit; adb pull progress is not a failed setup."
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

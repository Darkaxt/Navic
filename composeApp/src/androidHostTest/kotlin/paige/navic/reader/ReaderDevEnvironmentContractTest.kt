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

		assertTrue(setupScript.exists(), "The local Android reader lab must have an SDK/AVD setup script.")
		assertTrue(installScript.exists(), "The local Android reader lab must have a dirty build/install script.")
		assertTrue(viewportScript.exists(), "The local Android reader lab must have a viewport simulation script.")
		assertTrue(envExample.exists(), "The local Android reader lab must document the ignored credential env file.")
		assertTrue(
			gitignore.contains("navic-reader-dev.env"),
			"Local reader dev credentials must be ignored."
		)
		assertTrue(
			spec.contains("Local Android Reader Lab"),
			"The active Komikku reader plan must record the emulator/dev-build loop."
		)
		assertTrue(
			installScript.readText().contains("Resolve-ReaderDevPublicationFromBindery") &&
				installScript.readText().contains("BINDERY_API_KEY_HEADER"),
			"The install script must be able to derive a reader launch target from Bindery OPDS credentials without printing secrets."
		)
		assertTrue(
			installScript.readText().contains("Get-ReaderDevPublicationEbookLink") &&
				installScript.readText().contains("http://opds-spec.org/acquisition") &&
				installScript.readText().contains("Use catalog acquisition links before probing /resources"),
			"The install script must prefer EPUB/PDF acquisition links already present in /opds/books before slow per-book resource catalog probing."
		)
		assertTrue(
			installScript.readText().contains("ConvertTo-AdbShellQuotedValue") &&
				installScript.readText().contains("Add-ShellStringExtra") &&
				!installScript.readText().contains("Add-StringExtra -Arguments \$launchArgs"),
			"The install script must quote adb shell string extras so discovered titles with spaces do not become stray am-start package arguments."
		)
		assertTrue(
			setupScript.readText().contains("immersive_mode_confirmations") &&
				installScript.readText().contains("immersive_mode_confirmations") &&
				installScript.readText().contains("confirmed"),
			"The readerDev loop must suppress Android's fullscreen education overlay so screenshots and gestures hit the reader surface."
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
			spec.contains("## Hard Procedure: Reference Parity Gate"),
			"The active reader plan must keep reference parity as a top-level procedure, not only as transient chat context."
		)
		assertTrue(
			spec.contains("Every reader feature and every reader bugfix must start from the reference product") &&
				spec.contains("Komikku is the reference authority for reader layout") &&
				spec.contains("Anx Reader/Foliate is the reference authority for EPUB/PDF engine capabilities"),
			"Reader work must distinguish Komikku UI/shell authority from Anx/Foliate engine authority before implementation."
		)
		assertTrue(
			spec.contains("A feature is not accepted just because it appears to work") &&
				spec.contains("If it diverges from the reference product's ownership model") &&
				spec.contains("A working but non-faithful implementation is still a failing implementation") &&
				spec.contains("must be redesigned"),
			"Working but non-faithful reader behavior must be treated as incomplete and redesigned."
		)
		assertTrue(
			spec.contains("Each new feature or bugfix must name the reference source file/function it is matching") &&
				spec.contains("The guard must protect behavior and ownership from the reference product") &&
				spec.contains("If a Navic implementation works but remains less faithful than the reference model, it is not done"),
			"The per-slice acceptance map must force concrete reference matching for both new features and fixes."
		)
	}
}

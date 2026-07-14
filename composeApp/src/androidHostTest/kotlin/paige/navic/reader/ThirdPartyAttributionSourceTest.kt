package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ThirdPartyAttributionSourceTest {
	@Test
	fun copiedReaderComponentsHavePinnedRepositoryNotices() {
		val notices = repoFile("THIRD_PARTY.md").readText()

		assertContains(notices, "GNU General Public License version 3")
		assertContains(notices, "Anx Reader")
		assertContains(notices, "107f4fa74db0e7247c846c49d6211df3edf9887c")
		assertContains(notices, "foliate-js 1.0.1")
		assertContains(notices, "f52d42c6127d0ad981a2c67634113541b17ae01e")
		assertContains(notices, "PDF.js 3.11.174")
		assertContains(notices, "ce87167432819f85df49b6b16c7a78556e9a4ee0")

		val expectedLicenseHeaders = mapOf(
			"third_party/licenses/Anx-Reader-MIT.txt" to "Copyright (c) 2025 Anxcye",
			"third_party/licenses/foliate-js-MIT.txt" to "Copyright (c) 2022 John Factotum",
			"third_party/licenses/PDF.js-Apache-2.0.txt" to "Apache License"
		)
		expectedLicenseHeaders.forEach { (path, requiredText) ->
			assertContains(repoFile(path).readText(), requiredText)
		}
	}

	@Test
	fun playLikeCurlGeometryPortHasSourceAndPackagedAttribution() {
		val notices = repoFile("THIRD_PARTY.md").readText()
		val generated = repoFile(
			"composeApp/src/commonMain/composeResources/files/acknowledgements.json"
		).readText()
		val libraryRecord = repoFile(
			"composeApp/aboutlibraries/libraries/playlikecurl.json"
		).readText()
		val licenseRecord = repoFile(
			"composeApp/aboutlibraries/licenses/playlikecurl-mit.json"
		).readText()
		val license = repoFile("third_party/licenses/playlikecurl.txt").readText()
		val verifier = repoFile("scripts/verify-third-party-attributions.ps1").readText()

		assertContains(notices, "PlayLikeCurl")
		assertContains(notices, "https://github.com/karankalsi/PlayLikeCurl")
		assertContains(notices, "915a5a33773b1b2534134a56cdab00303b29a442")
		assertContains(notices, "third_party/licenses/playlikecurl.txt")
		assertContains(libraryRecord, "https://github.com/karankalsi/PlayLikeCurl")
		assertContains(libraryRecord, "playlikecurl-mit")
		assertContains(licenseRecord, "MIT License - PlayLikeCurl")
		assertContains(license, "MIT License")
		assertContains(license, "Copyright (c) [year] [fullname]")
		assertContains(verifier, "id = \"playlikecurl\"")
		assertContains(verifier, "third_party/licenses/playlikecurl.txt")
		assertEquals(
			1,
			Regex("\\\"uniqueId\\\":\\s*\\\"playlikecurl\\\"").findAll(generated).count(),
			"Generated acknowledgements must contain exactly one PlayLikeCurl record."
		)
	}

	@Test
	fun existingAcknowledgementsPipelineIncludesCopiedReaderComponents() {
		val buildScript = repoFile("composeApp/build.gradle.kts").readText()
		val screen = repoFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/AcknowledgementsScreen.kt"
		).readText()
		val generated = repoFile(
			"composeApp/src/commonMain/composeResources/files/acknowledgements.json"
		).readText()

		assertContains(buildScript, "configPath = file(\"aboutlibraries\")")
		assertContains(screen, "Res.readBytes(\"files/acknowledgements.json\")")
		listOf("anx-reader", "foliate-js", "pdfjs-dist", "playlikecurl").forEach { id ->
			assertEquals(
				1,
				Regex("\\\"uniqueId\\\":\\s*\\\"$id\\\"").findAll(generated).count(),
				"Generated acknowledgements must contain exactly one $id record."
			)
		}
	}

	@Test
	fun ciVerifiesGeneratedAndPackagedAttributions() {
		val verifier = repoFile("scripts/verify-third-party-attributions.ps1").readText()
		val workflow = repoFile(".github/workflows/build.yml").readText()

		assertContains(verifier, "acknowledgements.json")
		assertContains(verifier, "System.IO.Compression.ZipFile")
		assertContains(workflow, "scripts/verify-third-party-attributions.ps1")
		assertContains(workflow, "-ApkPath")
	}

	private fun repoFile(path: String): File = listOf(File(path), File("../$path"))
		.firstOrNull { it.isFile }
		?: error("Could not locate repository file: $path")
}

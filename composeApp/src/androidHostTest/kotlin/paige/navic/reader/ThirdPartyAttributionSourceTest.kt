package paige.navic.reader

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
		val license = repoFile("third_party/playlikecurl/LICENSE.txt").readText()
			val verifier = repoFile("scripts/verify-third-party-attributions.ps1").readText()
			val provenance = repoFile("third_party/playlikecurl/provenance.json").readText()
			val releaseIdentity = "production API 2; source commit a16ea9aa46484f3068242577e1189af66fb1fa9d; release AAR SHA-256 4c356f44443b5a1abcd70851f062d38f136dcdcc67d72eb3a699a12126584bcd"
			val generatedLibraries = Json.parseToJsonElement(generated)
				.jsonObject["libraries"]!!.jsonArray
			val generatedPlayLikeCurl = generatedLibraries.single { library ->
				library.jsonObject["uniqueId"]!!.jsonPrimitive.content == "playlikecurl"
			}.jsonObject
			val generatedDescription = generatedPlayLikeCurl["description"]!!
				.jsonPrimitive.content
			val playLikeCurlNotice = Regex(
				"(?ms)^## PlayLikeCurl\\r?$.*?(?=^## |\\z)"
			).find(notices)?.value ?: error("Missing PlayLikeCurl notice section" )

			assertContains(notices, "PlayLikeCurl")
		assertContains(notices, "https://github.com/karankalsi/PlayLikeCurl")
		assertContains(notices, "a16ea9aa46484f3068242577e1189af66fb1fa9d")
		assertContains(notices, "third_party/playlikecurl/LICENSE.txt")
		assertContains(libraryRecord, "https://github.com/Darkaxt/PlayLikeCurl/releases/tag/1.2.1")
		assertContains(libraryRecord, "https://github.com/karankalsi")
		assertContains(libraryRecord, "playlikecurl-mit")
		assertContains(licenseRecord, "MIT License - PlayLikeCurl")
		assertContains(license, "MIT License")
		assertContains(license, "Copyright (c) [year] [fullname]")
		assertContains(verifier, "id = \"playlikecurl\"")
			assertContains(verifier, "third_party/playlikecurl/LICENSE.txt")
			listOf(playLikeCurlNotice, generatedDescription, libraryRecord, verifier).forEach { representation ->
				assertEquals(
					1,
					Regex(Regex.escape(releaseIdentity)).findAll(representation).count(),
					"Each PlayLikeCurl representation must contain one exact API/commit/AAR identity."
				)
			}
			assertContains(provenance, "\"tag\": \"1.2.1\"")
			assertContains(provenance, "\"apiVersion\": 2")
			assertContains(provenance, "\"commit\": \"a16ea9aa46484f3068242577e1189af66fb1fa9d\"")
			assertContains(provenance, "\"releaseArtifactDigest\": \"sha256:4c356f44443b5a1abcd70851f062d38f136dcdcc67d72eb3a699a12126584bcd\"")
			assertEquals(0, Regex("production API 1").findAll(libraryRecord).count())
			assertEquals(0, Regex("production API 1").findAll(generated).count())
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

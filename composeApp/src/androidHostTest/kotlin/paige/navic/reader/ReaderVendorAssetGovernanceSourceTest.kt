package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ReaderVendorAssetGovernanceSourceTest {
	@Test
	fun manifestPinsImmutableFoliateAndPdfjsSourcesAndHashesEveryVendorFile() {
		val manifestFile = repoFile("composeApp/src/androidMain/assets/reader/vendor/manifest.json")
		val manifest = manifestFile.readText()
		val vendorRoot = manifestFile.parentFile ?: error("Reader vendor manifest has no parent directory.")
		val vendorFileCount = vendorRoot.walkTopDown().count { it.isFile && it != manifestFile }

		assertContains(manifest, "\"version\": \"1.0.1\"")
		assertContains(manifest, "f52d42c6127d0ad981a2c67634113541b17ae01e")
		assertContains(manifest, "\"version\": \"3.11.174\"")
		assertContains(manifest, "ce87167432819f85df49b6b16c7a78556e9a4ee0")
		assertContains(manifest, "f287f540ed3ed393e137c9ff7a2e98f6e73ea527")
		assertEquals(
			vendorFileCount,
			Regex("\\\"sha256\\\": \\\"[0-9a-f]{64}\\\"").findAll(manifest).count(),
			"Every vendored reader file must have one SHA-256 entry."
		)
	}

	@Test
	fun androidBuildVerifiesSourceGovernanceAndPackagedApkAssets() {
		val workflow = repoFile(".github/workflows/build.yml").readText()
		val verifier = repoFile("scripts/verify-reader-vendor-assets.ps1").readText()
		val procedure = repoFile("docs/architecture/reader-vendor-assets.md").readText()

		assertContains(workflow, "scripts/test-reader-vendor-assets-verifier.ps1")
		assertContains(workflow, "scripts/verify-reader-vendor-assets.ps1 -ApkPath")
		assertContains(verifier, "Unmanifested vendor files")
		assertContains(verifier, "Built APK has unmanifested reader vendor files")
		assertContains(procedure, "Review And Update Procedure")
		assertContains(procedure, "GitHub Security Advisories")
	}

	private fun repoFile(path: String): File = listOf(File(path), File("../$path"))
		.firstOrNull { it.isFile }
		?: error("Could not locate repository file: $path")
}

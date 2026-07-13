package paige.navic.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemotePackageBoundarySourceTest {
	@Test
	fun aurralAndBinderyTransportCodeIsFeatureOwnedByDataRemote() {
		val repositoryDirectory = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/domain/repositories"
		)
		val transportPrefixes = listOf("Aurral", "Bindery")
		repositoryDirectory.listFiles().orEmpty()
			.filter { file -> transportPrefixes.any(file.name::startsWith) }
			.forEach { file ->
				val source = file.readText()
				assertFalse("io.ktor." in source, "${file.name} must not own Ktor transport code")
			}
		listOf(
			"AurralApiClient.kt",
			"AurralDtos.kt",
			"AurralDtoMapping.kt",
			"BinderyApiClient.kt",
			"BinderyDtoMapping.kt"
		).forEach { fileName ->
			assertFalse(
				repositoryDirectory.resolve(fileName).exists(),
				"$fileName must not be declared in domain.repositories"
			)
		}

		listOf(
			"aurral/AurralApiClient.kt",
			"aurral/AurralDtos.kt",
			"aurral/AurralSerialization.kt",
			"bindery/BinderyApiClient.kt",
			"bindery/BinderyDtoMapping.kt",
			"bindery/BinderySerialization.kt"
		).forEach { relativePath ->
			assertTrue(
				sourceFile("composeApp/src/commonMain/kotlin/paige/navic/data/remote/$relativePath").isFile,
				"$relativePath must be owned by data.remote"
			)
		}
	}

	private fun sourceFile(path: String): File =
		listOf(File(path), File("../$path")).firstOrNull(File::exists)
			?: error("Unable to locate $path")
}

package paige.navic.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyFileConsolidationSourceTest {
	@Test
	fun tinySameFeaturePoliciesAreConsolidatedByResponsibility() {
		listOf(
			"ui/screens/collection/CollectionDetailPolicy.kt",
			"ui/screens/library/MostPlayedShortcutPolicy.kt"
		).forEach { relativePath ->
			assertTrue(commonMain(relativePath).isFile, "$relativePath must own the consolidated policy")
		}

		listOf(
			"ui/screens/collection/CollectionDetailDeleteNavigationPolicy.kt",
			"ui/screens/collection/CollectionDetailRowLayoutPolicy.kt",
			"ui/screens/library/MostPlayedShortcutNavigationPolicy.kt",
			"ui/screens/library/MostPlayedShortcutEntityResolutionPolicy.kt"
		).forEach { relativePath ->
			assertFalse(commonMain(relativePath).exists(), "$relativePath must remain consolidated")
		}
	}

	private fun commonMain(relativePath: String): File {
		val path = "composeApp/src/commonMain/kotlin/paige/navic/$relativePath"
		return listOf(File(path), File("../$path")).firstOrNull { candidate ->
			candidate.exists() || candidate.parentFile?.exists() == true
		} ?: error("Unable to locate source root for $path")
	}
}

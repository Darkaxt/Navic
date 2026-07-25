package paige.navic.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class NavigationViewModelLifecycleSourceTest {
	@Test
	fun navDisplayScopesStateAndViewModelsToEntries() {
		val source = projectFile("composeApp/src/commonMain/kotlin/paige/navic/App.kt").readText()

		assertContains(source, "rememberSaveableStateHolderNavEntryDecorator")
		assertContains(source, "rememberViewModelStoreNavEntryDecorator")
		assertContains(source, "entryDecorators = listOf(")
	}

	@Test
	fun lifecycleBundleUsesNavigation3Integration() {
		val catalog = projectFile("gradle/libs.versions.toml").readText()

		assertContains(
			catalog,
			"org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3"
		)
	}

	@Test
	fun portDoesNotIntroduceProcessWideViewModelRetention() {
		val navigationDirectory = projectFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/navigation"
		)
		assertFalse(
			navigationDirectory.walkTopDown().any { file ->
				file.isFile && "PersistentViewModelStoreOwner" in file.readText()
			}
		)
	}

	private fun projectFile(relativePath: String): File = listOf(
		File(relativePath),
		File("../$relativePath")
	).firstOrNull(File::exists) ?: error("Unable to locate $relativePath")
}

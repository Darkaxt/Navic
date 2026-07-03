package paige.navic.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class AppBackHandlerSourceTest {
	@Test
	fun appInstallsNavicBackHandlerForSingleEntryDetailRoots() {
		val source = sourceFile("App.kt").readText()

		assertContains(
			source,
			"import androidx.navigationevent.compose.NavigationBackHandler",
			message = "App must install its own back handler because Navigation3 stack popping is not enough for single-entry synthetic detail roots."
		)
		assertContains(
			source,
			"isBackEnabled = scrollManager.isTriggered || canNavigateBack(backStack)",
			message = "The app-level back handler must use Navic's policy, not raw backStack size."
		)
		assertContains(
			source,
			"scrollManager.tryHandleBackToTop()",
			message = "The app-level back handler must preserve the existing back-to-top behavior before navigation."
		)
		assertContains(
			source,
			"backStack.performNavicBack()",
			message = "System back and the top-left root back button must execute the same one-level-up policy."
		)
	}

	@Test
	fun searchTopBarUsesNavicBackPolicyForNestedAndRootSearch() {
		val source = sourceFile("ui/screens/search/components/TopBar.kt").readText()

		assertContains(
			source,
			"shouldShowRootBackForScreen",
			message = "Search must show its top-left back arrow from Navic's visible-back policy, not from the nested flag only."
		)
		assertContains(
			source,
			"backStack.performNavicBack()",
			message = "Search back arrow must use the same one-level-up policy as system back and RootTopBar."
		)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../composeApp/src/commonMain/kotlin/paige/navic/$path"),
			File("composeApp/src/commonMain/kotlin/paige/navic/$path")
		).firstOrNull { it.isFile }
			?: error("Could not locate composeApp App.kt")
}

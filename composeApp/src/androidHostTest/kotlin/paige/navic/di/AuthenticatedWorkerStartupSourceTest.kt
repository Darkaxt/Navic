package paige.navic.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AuthenticatedWorkerStartupSourceTest {
	@Test
	fun backgroundWorkersAreNotStartedByKoinGraphConstruction() {
		val module = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/di/ManagerModule.kt").readText()

		assertFalse(module.contains("createdAtStart = true"))
		assertFalse(module.contains("startPeriodicSync()"))
		assertFalse(module.contains(".apply {\n\t\t\tstart()"))
	}

	@Test
	fun authenticatedAppStateExplicitlyStartsWorkers() {
		val app = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/App.kt").readText()

		assertContains(app, "LaunchedEffect(isLoggedIn")
		assertContains(app, "if (isLoggedIn)")
		assertContains(app, "syncManager.startPeriodicSync()")
		assertContains(app, "downloadQueueNotificationCoordinator.start()")
	}

	private fun sourceFile(path: String): File =
		listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}

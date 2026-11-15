package paige.navic.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AuthenticatedWorkerStartupSourceTest {
	@Test
	fun backgroundWorkersAreNotStartedByKoinGraphConstruction() {
		val module = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/di/ManagerModule.kt").readText()

		val eagerSingletons = Regex("""single\(createdAtStart = true\) \{ (\w+)\(""").findAll(module)
			.map { it.groupValues[1] }.toList()
		assertEquals(listOf("DownloadAccountIdentity"), eagerSingletons)
		assertEquals(1, Regex("createdAtStart = true").findAll(module).count())
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

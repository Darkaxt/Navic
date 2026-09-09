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

		// Capture persisted migration ownership before Room opens, without starting workers.
		val eagerIdentity = "single(createdAtStart = true) { DownloadAccountIdentity(get()) }"
		assertEquals(1, Regex(Regex.escape(eagerIdentity)).findAll(module).count())
		assertFalse(Regex("createdAtStart\\s*=\\s*true").containsMatchIn(module.replace(eagerIdentity, "")))
		assertContains(module, "singleOf(::SyncManager)")
		assertContains(module, "singleOf(::DownloadManager)")
		assertContains(module, "singleOf(::DownloadQueueNotificationCoordinator)")
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

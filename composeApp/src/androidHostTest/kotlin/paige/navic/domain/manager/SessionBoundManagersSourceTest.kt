package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class SessionBoundManagersSourceTest {
	@Test
	fun syncAndDownloadWorkersUseReplaceableSessionLifetime() {
		val sync = sourceFile("SyncManager.kt").readText()
		val downloads = sourceFile("DownloadManager.kt").readText()
		val login = sourceFile("../../ui/screens/login/viewmodels/LoginViewModel.kt").readText()

		assertContains(sync, "sessionLifetime.repeatInSession")
		assertContains(sync, "sessionLifetime.currentScope()?.launch")
		assertContains(downloads, "sessionLifetime.repeatInSession")
		assertContains(downloads, "withContext(NonCancellable)")
		assertContains(downloads, "cleanupSessionWork")
		assertContains(sync, "suspend fun syncNow()")
		assertContains(login, "syncManager.syncNow()")
		kotlin.test.assertFalse("repository.syncEverything" in login)
	}

	private fun sourceFile(name: String): File = listOf(
		File("src/commonMain/kotlin/paige/navic/domain/manager/$name").normalize(),
		File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/$name").normalize()
	).firstOrNull { it.isFile }
		?: error("Unable to locate $name")
}

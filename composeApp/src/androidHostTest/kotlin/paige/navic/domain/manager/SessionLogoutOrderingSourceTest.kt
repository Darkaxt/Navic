package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SessionLogoutOrderingSourceTest {
	@Test
	fun logoutJoinsSessionBeforeClearingQueueAndCredentials() {
		val source = sourceFile().readText()
		val logout = source.substringAfter("suspend fun logout()")
			.substringBefore("private suspend fun clearOutgoingSyncState()")
		val endSession = logout.indexOf("sessionLifetime.endSession()")
		val clearQueue = logout.indexOf("clearOutgoingSyncState()")
		val clearUsername = logout.indexOf("settings[\"username\"] = null")

		assertTrue(endSession >= 0)
		assertTrue(clearQueue > endSession)
		assertTrue(clearUsername > clearQueue)
		assertTrue("syncActionDao.clearAllActions()" in source)
		assertTrue("withContext(NonCancellable)" in logout)
	}

	private fun sourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/domain/manager/SessionManager.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/SessionManager.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate SessionManager.kt")
}

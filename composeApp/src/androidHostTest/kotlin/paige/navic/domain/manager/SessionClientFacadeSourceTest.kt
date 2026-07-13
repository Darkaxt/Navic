package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SessionClientFacadeSourceTest {
	@Test
	fun subsonicClientIsNotExposedAsPublicMutableState() {
		val session = source("domain/manager/SessionManager.kt")
		val common = sourceRoot().walkTopDown()
			.filter { it.isFile && it.extension == "kt" }
			.joinToString("\n") { it.readText() }

		assertFalse("var api:" in session)
		assertContains(session, "SessionResourceSlot")
		assertContains(session, "withApi")
		assertContains(session, "SubsonicClientFactory")
		assertFalse("SubsonicClient.Companion" in session)
		assertFalse("sessionManager.api" in common)
	}

	private fun source(relative: String): String = File(sourceRoot(), relative).readText()

	private fun sourceRoot(): File = listOf(
		File("src/commonMain/kotlin/paige/navic"),
		File("composeApp/src/commonMain/kotlin/paige/navic")
	).firstOrNull { it.isDirectory }
		?: error("Unable to locate common source root")
}

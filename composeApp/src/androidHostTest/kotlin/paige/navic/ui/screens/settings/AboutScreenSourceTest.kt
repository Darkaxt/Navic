package paige.navic.ui.screens.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class AboutScreenSourceTest {
	@Test
	fun aboutScreenDoesNotLinkToOriginalDiscordChat() {
		val source = aboutScreenSourceFile().readText()

		assertFalse(source.contains("discord.gg"), "Fork About screen must not link to the original Discord server.")
		assertFalse(source.contains("title_chat"), "Fork About screen must not expose the Chat row.")
	}

	private fun aboutScreenSourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/settings/AboutScreen.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/AboutScreen.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate AboutScreen.kt")
}

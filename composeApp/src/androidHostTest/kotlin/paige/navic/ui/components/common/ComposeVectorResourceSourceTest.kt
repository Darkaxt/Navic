package paige.navic.ui.components.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ComposeVectorResourceSourceTest {
	@Test
	fun composeVectorDrawablesUseLiteralTransparentColors() {
		val offenders = drawableResourceRoot()
			.walkTopDown()
			.filter { file -> file.isFile && file.extension == "xml" }
			.filter { file -> file.readText().contains("@android:color/transparent") }
			.map { file -> file.relativeTo(drawableResourceRoot()).invariantSeparatorsPath }
			.toList()

		assertTrue(
			offenders.isEmpty(),
			"Compose vector parser rejects @android:color/transparent in common resources; use #00000000 instead. Offenders: $offenders"
		)
	}

	private fun drawableResourceRoot(): File =
		listOf(
			File("src/commonMain/composeResources/drawable"),
			File("composeApp/src/commonMain/composeResources/drawable")
		).firstOrNull { it.isDirectory }
			?: error("Could not locate common compose drawable resources")
}

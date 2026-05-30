package paige.navic.androidApp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class AndroidCoilBootstrapSourceTest {
	@Test
	fun androidApplicationProvidesCoilSingletonFactoryBeforeComposeStarts() {
		val source = androidApplicationSource().readText()

		assertContains(source, "import coil3.SingletonImageLoader")
		assertContains(source, "class Application : android.app.Application(), SingletonImageLoader.Factory")
		assertContains(source, "override fun newImageLoader(context: android.content.Context)")
		assertContains(source, "initializeSingletonImageLoader(context)")
	}

	private fun androidApplicationSource(): File =
		listOf(
			File("../androidApp/src/main/kotlin/paige/navic/androidApp/Application.kt"),
			File("androidApp/src/main/kotlin/paige/navic/androidApp/Application.kt")
		).firstOrNull { it.isFile }
			?: error("Could not locate androidApp Application.kt")
}

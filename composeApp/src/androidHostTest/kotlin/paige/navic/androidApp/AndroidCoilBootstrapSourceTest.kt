package paige.navic.androidApp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AndroidCoilBootstrapSourceTest {
	@Test
	fun androidApplicationProvidesCoilSingletonFactoryBeforeComposeStarts() {
		val source = androidApplicationSource().readText()

		assertContains(source, "import coil3.SingletonImageLoader")
		assertContains(source, "class Application : android.app.Application(), SingletonImageLoader.Factory")
		assertContains(source, "override fun newImageLoader(context: android.content.Context)")
		assertContains(source, "initializeSingletonImageLoader(context)")
	}

	@Test
	fun commonAppDoesNotRegisterCoilSingletonFactoryOnAndroid() {
		val source = commonAppSource().readText()

		assertFalse(
			source.contains("setSingletonImageLoaderFactory"),
			"Android must rely on Application's SingletonImageLoader.Factory so early Coil users cannot create a singleton before App() tries to register one."
		)
	}

	private fun androidApplicationSource(): File =
		listOf(
			File("../androidApp/src/main/kotlin/paige/navic/androidApp/Application.kt"),
			File("androidApp/src/main/kotlin/paige/navic/androidApp/Application.kt")
		).firstOrNull { it.isFile }
			?: error("Could not locate androidApp Application.kt")

	private fun commonAppSource(): File =
		listOf(
			File("../composeApp/src/commonMain/kotlin/paige/navic/App.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/App.kt")
		).firstOrNull { it.isFile }
			?: error("Could not locate composeApp App.kt")
}

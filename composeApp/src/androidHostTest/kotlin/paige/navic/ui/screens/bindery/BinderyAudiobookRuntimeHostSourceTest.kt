package paige.navic.ui.screens.bindery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class BinderyAudiobookRuntimeHostSourceTest {
	@Test
	fun binderyAudiobookHostAutostartsAndPublishesRawPositions() {
		val source = runtimeHostSourceFile().readText()

		assertContains(source, "controller.load(plan, playWhenReady = true)")
		assertContains(source, "currentOnPlaybackPosition.value(position)")
	}

	private fun runtimeHostSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookRuntimeHost.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookRuntimeHost.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate BinderyAudiobookRuntimeHost.android.kt")
}

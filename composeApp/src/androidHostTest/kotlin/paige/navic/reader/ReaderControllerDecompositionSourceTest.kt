package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderControllerDecompositionSourceTest {
	@Test
	fun controllerDelegatesConcernStateTransitions() {
		val controller = readerSource("ReaderController.kt").readText()
		assertTrue(controller.lines().size < 700, "ReaderController must remain below 700 lines")
		listOf(
			"ReaderSearchReducer",
			"ReaderSelectionReducer",
			"ReaderAnnotationReducer",
			"ReaderProgressReducer",
			"ReaderOverlayReducer",
			"ReaderWhispersyncReducer"
		).forEach { reducer -> assertContains(controller, reducer) }
		assertFalse("data class ReaderControllerState" in controller)
		assertFalse("data class ReaderSelection" in controller)
	}

	@Test
	fun coordinatorOwnsDispatchAndEngineOrchestrationWithoutForwardingSurface() {
		val coordinator = readerSource("ReaderCoordinator.kt").readText()
		assertTrue(coordinator.lines().size < 120, "ReaderCoordinator must remain below 120 lines")
		assertContains(coordinator, "fun dispatch(action: ReaderController.() -> ReaderControllerStep)")
		assertContains(coordinator, "fun dispatchBack(action: ReaderController.() -> ReaderControllerBackStep)")
		assertContains(coordinator, "private fun applyEngineCommand")
		assertFalse("fun search(query:" in coordinator)
		assertFalse("fun applySettings(settings:" in coordinator)
	}

	@Test
	fun everyPlannedReaderConcernHasAnOwnedReducerFile() {
		listOf(
			"ReaderSearchReducer.kt",
			"ReaderSelectionReducer.kt",
			"ReaderAnnotationReducer.kt",
			"ReaderProgressReducer.kt",
			"ReaderOverlayReducer.kt",
			"ReaderWhispersyncReducer.kt"
		).forEach { name -> assertTrue(readerSource(name).isFile, "$name must own its reader concern") }
	}

	private fun readerSource(name: String): File {
		val path = "composeApp/src/commonMain/kotlin/paige/navic/reader/$name"
		return listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
	}
}

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
		assertContains(
			controller,
			"ReaderPresentationControllerReducer.onPresentationEvent(this, event)"
		)
		assertContains(
			controller,
			"ReaderPresentationControllerReducer.onViewerAction(\n\t\tthis,\n\t\taction,\n\t\tlegacyLiveCompatibilityContext"
		)
		val presentationController = readerSource("ReaderPresentationController.kt")
		assertTrue(presentationController.isFile)
		val presentationControllerSource = presentationController.readText()
		assertContains(presentationControllerSource, "fun readerViewerActionIsAdmitted(")
		assertContains(presentationControllerSource, "internal class ReaderPresentationEffectQueue")
	}

	@Test
	fun coordinatorOwnsDispatchAndEngineOrchestrationWithoutForwardingSurface() {
		val coordinator = readerSource("ReaderCoordinator.kt").readText()
		assertTrue(coordinator.lines().size < 130, "ReaderCoordinator must remain below 130 lines")
		assertContains(coordinator, "fun dispatch(action: ReaderController.() -> ReaderControllerStep)")
		assertContains(coordinator, "fun dispatchBack(action: ReaderController.() -> ReaderControllerBackStep)")
		assertContains(coordinator, "private fun applyEngineCommand")
		assertFalse("fun search(query:" in coordinator)
		assertFalse("fun applySettings(settings:" in coordinator)
	}

	@Test
	fun readerOrchestratorsKeepWhispersyncHelpersInFocusedOwners() {
		val whispersyncNavigationFile = readerSourceCandidate("ReaderWhispersyncNavigation.kt")
		val coordinatorWordSyncFile = readerSourceCandidate("ReaderCoordinatorWordSync.kt")
		assertTrue(whispersyncNavigationFile.isFile, "ReaderWhispersyncNavigation.kt must own navigation helpers")
		assertTrue(coordinatorWordSyncFile.isFile, "ReaderCoordinatorWordSync.kt must own WordSync forwarding")

		val controller = readerSource("ReaderController.kt").readText()
		val whispersyncNavigation = whispersyncNavigationFile.readText()
		val coordinator = readerSource("ReaderCoordinator.kt").readText()
		val coordinatorWordSync = coordinatorWordSyncFile.readText()

		assertFalse("fun ReaderOverlayFragment.isOutsideWhispersyncVisibleRange" in controller)
		assertContains(whispersyncNavigation, "fun ReaderOverlayFragment.isOutsideWhispersyncVisibleRange")
		assertContains(whispersyncNavigation, "fun ReaderWhispersyncSessionState.audioSeekTargetForActiveOverlay")
		assertFalse("internal fun configureWordSync" in coordinator)
		assertContains(coordinatorWordSync, "internal fun ReaderCoordinator.configureWordSync")
		assertContains(coordinatorWordSync, "internal fun ReaderCoordinator.onWordSyncChapterVerified")
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

	private fun readerSourceCandidate(name: String): File {
		val path = "composeApp/src/commonMain/kotlin/paige/navic/reader/$name"
		return listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: File(path)
	}

	private fun readerSource(name: String): File {
		val candidate = readerSourceCandidate(name)
		return candidate.takeIf(File::isFile)
			?: error("Unable to locate ${candidate.path}")
	}
}

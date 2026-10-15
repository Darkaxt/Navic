package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Wiring checks only; runtime qualification remains the original producer host tests. */
class ReaderInstalledTurnOwningIngressSourceTest {
    private fun source(suffix: String): String {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "composeApp/src").isDirectory }
        return File(root, "composeApp/src/$suffix").readText()
    }
    @Test fun screenUsesOwningCoordinatorIngressRatherThanPresentationOnlyReduction() {
        val screen = source("commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
        assertTrue(screen.contains("installedPageTurnModelIngress ="))
        assertTrue(screen.contains("coordinator.onInstalledPageTurnRelocation(event, predecessor, origin)"))
        assertTrue(screen.contains("applyCoordinatorStep(step, observePresentationReceipt = false)"))
    }
    @Test fun nativePointerClaimBindsOriginalSourceBeforeRendererDown() {
        val native = source("androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt")
        assertTrue(native.contains("admitInstalledPointerTurn(event, originalDown, route.gestureId)"))
        assertTrue(native.contains("originalInstalledCurlGestureSource(deck, intent)"))
        assertTrue(native.contains("createInstalledPageTurnSemanticRequest"))
    }
    @Test fun exactTerminalProducerRequiresOriginalCompletionAndSuccessorDeck() {
        val controller = source("androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt")
        assertTrue(controller.contains("requestCurlSettlementTerminalPresentedFrame("))
        assertTrue(controller.contains("terminal.rendererCompletion ?: return null"))
        assertTrue(controller.contains("specification.deckTarget.deckGeneration"))
    }
}

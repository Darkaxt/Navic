package paige.navic.ui.screens.reader

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Wiring guardrails only; actual Root/Engine protocol has separate host tests. */
class ReaderInitialSemanticIngressWiringSourceTest {
    private val project = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt")) }
    private fun common(name: String) = Files.readString(project.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/$name.kt"))
    @Test fun initialIngressHasSeparateTypedFailClosedCompositionLocal() {
        val source = common("ReaderPlatformHosts")
        assertTrue(source.contains("internal typealias ReaderInitialSemanticReceiptIngress = (ReaderPresentationEvent.FoliateRelocated, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?"))
        assertTrue(source.contains("LocalReaderInitialSemanticReceiptIngress = staticCompositionLocalOf<ReaderInitialSemanticReceiptIngress?> { null }"))
        assertTrue(source.contains("ReaderSemanticReceiptIngress = (ReaderPresentationEvent.CurrentDestinationConfirmed"))
    }
    @Test fun screenPublishesPureInitialCommonModelBeforeReturningItsReceipt() {
        val source = common("ReaderScreen")
        val body = source.substringAfter("rememberUpdatedState<ReaderInitialSemanticReceiptIngress>(", "")
            .substringBefore("KomikkuReaderRoot(")
        assertTrue(body.contains("coordinator.reduceSemanticDestination(event, origin)"))
        assertTrue(body.indexOf("coordinator = step.coordinator") in 0 until body.indexOf("step.presentationReceipt"))
        assertFalse(body.contains("applyCoordinatorStep(")); assertFalse(body.contains("onPresentationEvent("))
        assertTrue(source.contains("initialSemanticReceiptIngress = { event, origin -> currentInitialSemanticReceiptIngress(event, origin) }"))
    }
    @Test fun commonRootProvidesTheOptionalInitialIngressWithoutChangingBoundConfirmation() {
        val source = common("ReaderRoot")
        assertTrue(source.contains("initialSemanticReceiptIngress: ReaderInitialSemanticReceiptIngress? = null"))
        assertTrue(source.contains("LocalReaderInitialSemanticReceiptIngress provides initialSemanticReceiptIngress"))
        assertTrue(source.contains("LocalReaderSemanticReceiptIngress provides semanticReceiptIngress"))
    }
    @Test fun actualNativeCompositionCapturesInitialIngressForTheActualRootConsumer() {
        val source = Files.readString(project.resolve("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"))
        assertTrue(source.contains("rememberUpdatedState(LocalReaderInitialSemanticReceiptIngress.current)"))
        assertTrue(source.contains("LocalReaderInitialSemanticReceiptIngress provides currentInitialSemanticReceiptIngress"))
        assertTrue(source.contains("setInitialSemanticReceiptIngress(presentationVersion, currentInitialSemanticReceiptIngress)"))
        assertTrue(source.contains("fun createComposedInitialDestinationBootstrapRequest("))
    }
}

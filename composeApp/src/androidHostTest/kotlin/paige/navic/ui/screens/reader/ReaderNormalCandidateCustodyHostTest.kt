package paige.navic.ui.screens.reader

import android.os.Build
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import java.time.Duration
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Original normal composition; no Foliate or physical-frame success is asserted. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [
    Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
    Task398CanonicalStoragePathShadow::class])
class ReaderNormalCandidateCustodyHostTest {
    @Test fun explicitCompositionDisposalCannotReselectTheClosedNormalCandidate() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val compose = ComposeView(activity.get())
        val gateway = ReaderTransitionGateway()
        val controller = ReaderController().open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-candidate-custody", resourceHref = "fixture.epub"),
            url = "https://example.invalid/fixture.epub")).controller
        var entries = 0
        var disposals = 0
        try {
            activity.get().setContentView(compose)
            compose.setContent {
                DisposableEffect(Unit) { entries++; onDispose { disposals++ } }
                CompositionLocalProvider(LocalReaderTransitionGateway provides gateway) {
                    val state = controller.state
                    KomikkuReaderNativeFrameHost(
                        navigator = KomikkuReaderNavigator(KomikkuDisabledNavigation()),
                        navigationOverlayVisible = false, chromeOverlayVisible = false,
                        presentationDecision = state.presentationDecision, presentationState = state.presentation,
                        presentationVersion = controller.presentationVersion, presentationShellCoverVisible = false,
                        legacyLiveCompatibilityContext = ReaderLegacyLiveCompatibilityContext.Denied(),
                        presentationEffects = emptyList(), onPresentationEffectHandled = {},
                        onPresentationEvent = { null }, destinationCommitIdentity = null,
                        shellCoverUrl = null, shellCoverTitle = "Public synthetic custody", coverBackdropEnabled = false,
                        viewerKey = ReaderViewerKey(ReaderViewerKind.WebViewPublication, "public-synthetic-candidate-custody"),
                        grayscaleEnabled = false, invertedColors = false, verticalPageDragPreview = false,
                        pageTurnCanvasEnabled = true, pageTurnReadingDirection = "ltr", pageTurnBitmapQuality = "balanced",
                        pageTurnSnapshotKey = 0, pageTurnContentReadyKey = null, pageTurnPaginationStatus = null,
                        pageTurnVisualPageIndex = null, pageTurnVisualLocationReason = null,
                        pageTurnFoliateSessionId = null, pageTurnSettlementAck = null,
                        whispersyncOverlayActive = false, whispersyncAnchorReceipt = null,
                        whispersyncHighlightColorArgb = 0, whispersyncCueMapState = ReaderWhispersyncCueMapState(),
                        onWhispersyncCueMapHoldOutcome = { _, _ -> }, onWhispersyncCueMapSeekRequested = {},
                        onStartupShellPrepared = {}, onViewerAction = { null }, onPageTurnBoundary = { null },
                        onReadableDragPreview = { _, _, _, _, _ -> }, onContentLongPress = { _, _, _, _ -> },
                        modifier = Modifier.fillMaxSize(), viewerContent = {}, composeOverlay = {}
                    )
                }
            }
            compose.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            compose.layout(0, 0, 800, 600)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(64))
            assertNotNull(gateway.captureSelectedSemanticEpoch())
            assertEquals(1, entries, "Only one original normal composition may be installed")
            compose.disposeComposition()
            assertEquals(1, disposals, "Explicit disposal must consume the original composition immediately")
            assertNull(gateway.captureSelectedSemanticEpoch(), "Original candidate must retire at composition disposal")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(64))
            // ComposeView may legally recreate its OUTER composition from queued layout.
            // Reader custody, not that framework behavior, must remain terminal.
            assertFalse(gateway.admitsNormalSession(controller.state.readerSessionGeneration),
                "Disposed normal session cannot regain production admission")
            assertNull(gateway.captureSelectedSemanticEpoch(), "Queued layout cannot reselect the retired normal candidate")
        } finally {
            compose.disposeComposition()
            activity.pause().stop().destroy()
        }
    }
}

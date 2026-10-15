package paige.navic.ui.screens.reader

import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.State
import java.time.Duration
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Actual Engine before JS Ready. Synthetic bridge ingress exercises transport only;
 * it is not Foliate execution or complete Native installation/display acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    shadows = [Task398CanonicalStoragePathShadow::class])
class ReaderEngineInitialUnreadyProducerHostTest {
    @Test fun bootstrapOwnsRealOpeningAndQueryBeforeReadyWithoutDispatchOrSemanticConfirmation() {
        val f = EarlyEngineFixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            val pending = f.pending()
            assertEquals(2, pending.pendingCommands.size)
            assertTrue(pending.pendingCommands[0].dispatch.command is ReaderBridgeCommand.OpenPublication)
            assertTrue(pending.pendingCommands[1].dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination)
            assertTrue(pending.pendingCommands.all { it.lastDispatchedGeneration == null })
            assertTrue(pending.pendingCommands[1].currentDestinationContinuation != null)
            assertTrue(shadowOf(f.engine()).lastEvaluatedJavascript == null)
            f.post("commandAck", pending.pendingCommands[1].dispatch.id)
            f.post("commandAck", pending.pendingCommands[0].dispatch.id)
            assertEquals(2, f.pending().pendingCommands.size, "Undispatched opening ACK cannot bypass actual opening custody")
            assertEquals(0, f.receipts.size)
            f.post("ready")
            assertTrue(f.scriptType() == "openPublication")
            f.post("commandAck", f.pending().pendingCommands.first().dispatch.id)
            assertTrue(f.scriptType() == "resolveCurrentDestination")
            f.resolve()
            assertEquals(1, f.receipts.size)
            assertEquals(0, f.executor.activeSlotCount)
            assertEquals(0, f.registry.activeHandleCount)
            assertTrue(f.pending().pendingCommands.isEmpty())
        } finally { f.close() }
    }

    @Test fun cancellationBeforeReadyRetiresOnlyExactQueryAndDoesNotExecuteItOnLaterOpen() {
        val f = EarlyEngineFixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.registration.retire()
            f.drain()
            assertEquals(0, f.executor.activeSlotCount)
            assertEquals(0, f.registry.activeHandleCount)
            assertTrue(f.pending().pendingCommands.none { it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination })
            f.post("ready")
            assertTrue(f.scriptType() == "openPublication")
            f.post("commandAck", f.pending().pendingCommands.first().dispatch.id)
            assertTrue(f.scriptType() == "openPublication")
            assertEquals(0, f.receipts.size)
            assertTrue(f.pending().pendingCommands.isEmpty())
        } finally { f.close() }
    }

    @Test fun closeBeforeReadyRetiresOwningTransportWithoutBootstrapReceipt() {
        val f = EarlyEngineFixture()
        try {
            assertEquals(ReaderSemanticCommandResult.Accepted, f.start())
            f.compose.disposeComposition()
            f.drain()
            assertEquals(0, f.executor.activeSlotCount)
            assertEquals(0, f.receipts.size)
            assertEquals(0, f.registry.activeHandleCount)
        } finally { f.close() }
    }

    private class EarlyEngineFixture {
        private val activity = run {
            val key = "robolectric.areWindowsMarkedVisible"; val old = System.getProperty(key)
            System.setProperty(key, "true")
            try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
            finally { if (old == null) System.clearProperty(key) else System.setProperty(key, old) }
        }
        val compose = ComposeView(activity.get())
        val opened = ReaderController().open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-early", resourceHref = "fixture.epub"),
            url = "https://example.invalid/fixture.epub")).controller
        var model = ReaderCoordinator(controller = opened)
        val registry = ReaderSemanticExecutableRequestRegistry(opened.state.readerSessionGeneration)
        val executor = ReaderSemanticCommandExecutor(registry)
        val receipts = mutableListOf<ReaderPresentationEventReceipt>()
        lateinit var registration: ReaderSemanticCommandRegistration
        lateinit var request: ReaderSemanticExecutableRequest
        val handle = registry.register { origin, context, callback -> request(origin, context, callback) }
        init {
            activity.get().setContentView(compose)
            compose.setContent {
                ReaderEngineWebViewHost(publicationUrl = "https://example.invalid/fixture.epub", title = "Public synthetic early",
                    kind = ReaderPublicationKind.Ebook, mediaOverlayEnabled = false, externalShellCover = false,
                    suppressWebShellCover = false, nativeShellCoverTint = null, settings = ReaderSettings(),
                    startCfi = null, startHref = null, startProgress = null, onEvent = {}, modifier = Modifier.fillMaxSize())
            }
            drain()
            assertTrue(engine().url == ReaderWebRuntime.entrypointUrl)
            val source = engine() as ReaderEngineCurrentDestinationQuerySource
            request = source.createInitialDestinationSemanticRequest(ReaderReservedNeutralBootstrapRequest(handle, opened.state.readerSessionGeneration),
                opened.presentationVersion, 1L, 1L, 0L, { true }) { event, origin ->
                val step = model.reduceSemanticDestination(event, origin); model = step.coordinator; step.presentationReceipt
            }
        }
        fun start(): ReaderSemanticCommandResult {
            val id = ReaderTransitionId(opened.state.readerSessionGeneration, 1L, 1L, ReaderTransitionOperation.BootstrapNativePage,
                ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
            return executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(id, ReaderBootstrapNativePageIntent(handle), handle),
                { registration = it; it.bindAsyncOutcome { registration.retire() } }, receipts::add)
        }
        fun engine(): WebView {
            fun visit(v: View): WebView? {
                if (v is WebView) return v
                if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))?.let { return it }
                return null
            }
            return checkNotNull(visit(compose))
        }
        fun pending(): ReaderWebCommandDispatchState = engine().webViewClient.javaClass.declaredFields.firstNotNullOfOrNull {
            val v = it.apply { isAccessible = true }.get(engine().webViewClient)
            (v as? State<*>)?.value as? ReaderWebCommandDispatchState
        } ?: error("Actual owning Engine ledger missing")
        fun drain() {
            compose.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            compose.layout(0, 0, 800, 600); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(48))
        }
        fun post(type: String, id: String? = null) = post(buildJsonObject { put("type", type); id?.let { put("commandId", it) } })
        fun post(message: JsonObject) {
            val bridge = shadowOf(engine()).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName) as ReaderJavascriptBridge
            bridge.postMessage(message.toString()); drain()
        }
        fun scriptType(): String? {
            val script = shadowOf(engine()).lastEvaluatedJavascript ?: return null
            return (Json.parseToJsonElement(script.removePrefix("window.NavicReaderBridge.dispatch(").removeSuffix(");")) as JsonObject)["type"]?.jsonPrimitive?.content
        }
        fun resolve() {
            val head = pending().pendingCommands.first().dispatch
            val query = head.command as ReaderBridgeCommand.ResolveCurrentDestination
            post(buildJsonObject {
                put("type", "currentDestinationResolved"); put("commandId", head.id)
                put("foliateSessionId", query.foliateSessionId); put("destinationFoliateSessionId", query.foliateSessionId)
                put("destinationCommitSequence", 1L); put("pageIndex", 0)
            })
        }
        fun close() {
            executor.clear(); registry.clear(); compose.disposeComposition()
            shadowOf(Looper.getMainLooper()).idle(); activity.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle()
        }
    }
}

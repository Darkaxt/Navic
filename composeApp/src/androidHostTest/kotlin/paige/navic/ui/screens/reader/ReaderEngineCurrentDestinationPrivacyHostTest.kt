package paige.navic.ui.screens.reader

import android.net.Uri
import android.os.Build
import android.webkit.ReaderSyntheticWebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import paige.navic.reader.ReaderBridgeCommand
import paige.navic.reader.ReaderWebRuntime

/** Synthetic in-memory host logs only; no device logcat, real publication, or live renderer. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
	shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class, Task398CanonicalStoragePathShadow::class])
class ReaderEngineCurrentDestinationPrivacyHostTest {
	@Test
	fun actualDispatchSkipDiagnosticDoesNotExposeCurrentUrlTail() = runTest {
		withCurrentDestinationEngine { bench ->
			val view = bench.webView()
			view.loadUrl("https://synthetic.invalid/SYNTHETIC_SKIP_URL_204")
			val beforeDispatch = bench.lastScript()
			val start = logCount()
			bench.submit(ReaderBridgeCommand.PreviousPage)
			// Update rejects a foreign URL silently. A synthetic Ready replay through the
			// installed bridge reaches the logged inner guard without enqueueing the command.
			bench.post(bench.envelope("ready"))
			val messages = messagesAfter(start)
			assertTrue(messages.any { it == "Skipping reader command dispatch: ready=true entrypoint=false" },
				"Synthetic Ready replay must exercise the actual non-entrypoint dispatch-skip diagnostic")
			assertTrue(bench.lastScript() == beforeDispatch && bench.pending().pendingCommands.isEmpty(),
				"A non-entrypoint URL must not dispatch the requested reader command")
			assertFalse(messages.any { it.contains("SYNTHETIC_SKIP_URL_204") },
				"Dispatch-skip diagnostic must not expose URL payload")
		}
	}

	@Test
	fun actualPageFinishedRejectsCallbackUrlTailWithoutInitializationOrDiagnostic() = runTest {
		withCurrentDestinationEngine { bench ->
			bench.retireRenderer()
			val view = bench.webView() as ReaderEngineWebView
			assertTrue(view.url == ReaderWebRuntime.entrypointUrl)
			assertFalse(view.runtimeInitializationCompleted)
			val rejectedStart = logCount()
			view.webViewClient.onPageFinished(view, "https://synthetic.invalid/SYNTHETIC_FINISHED_URL_204")
			bench.drain()
			assertFalse(view.runtimeInitializationCompleted,
				"A callback URL that is not the exact entrypoint must not initialize the runtime")
			assertTrue(shadowOf(view).lastEvaluatedJavascript == null,
				"A rejected callback must not dispatch before the real entrypoint and Javascript Ready")
			assertTrue(messagesAfter(rejectedStart).isEmpty(),
				"A rejected callback must remain diagnostic-silent rather than log its private URL")

			view.webViewClient.onPageFinished(view, ReaderWebRuntime.entrypointUrl)
			assertTrue(view.runtimeInitializationCompleted)
			assertTrue(shadowOf(view).lastEvaluatedJavascript == null,
				"Entrypoint completion alone must not dispatch before distinct Javascript Ready")
			val admittedStart = logCount()
			bench.post(bench.envelope("ready"))
			bench.expectDispatch("openPublication")
			val admittedMessages = messagesAfter(admittedStart)
			assertTrue(admittedMessages.any { it.startsWith("Dispatching reader engine command:") },
				"An admitted entrypoint plus real Javascript Ready must retain safe positive command diagnostics")
			assertFalse(admittedMessages.any { it.contains("SYNTHETIC_FINISHED_URL_204") },
				"Admitted runtime diagnostics must not retain the rejected callback URL")
		}
	}

	@Test
	fun actualResourceErrorDiagnosticKeepsScalarsWithoutUrlOrDescription() = runTest {
		withCurrentDestinationEngine { bench ->
			val view = bench.webView()
			val start = logCount()
			view.webViewClient.onReceivedError(view, request("SYNTHETIC_ERROR_URL_204"),
				ReaderSyntheticWebResourceError(-2, "SYNTHETIC_ERROR_DESCRIPTION_204"))
			val messages = messagesAfter(start)
			assertTrue(messages.any { it.startsWith("Reader engine WebView error") && it.contains("code=-2") },
				"Fixture must exercise actual resource-error scalar logging")
			assertFalse(messages.any { it.contains("SYNTHETIC_ERROR_URL_204") },
				"Resource-error diagnostic must not expose URL payload")
			assertFalse(messages.any { it.contains("SYNTHETIC_ERROR_DESCRIPTION_204") },
				"Resource-error diagnostic must not expose error-description payload")
		}
	}

	@Test
	fun actualHttpErrorDiagnosticKeepsStatusWithoutUrlOrReason() = runTest {
		withCurrentDestinationEngine { bench ->
			val view = bench.webView()
			val start = logCount()
			view.webViewClient.onReceivedHttpError(view, request("SYNTHETIC_HTTP_URL_204"),
				WebResourceResponse("text/plain", "UTF-8", 503, "SYNTHETIC_HTTP_REASON_204", emptyMap(), null))
			val messages = messagesAfter(start)
			assertTrue(messages.any { it.startsWith("Reader engine WebView HTTP error") && it.contains("status=503") },
				"Fixture must exercise actual HTTP-error scalar logging")
			assertFalse(messages.any { it.contains("SYNTHETIC_HTTP_URL_204") },
				"HTTP-error diagnostic must not expose URL payload")
			assertFalse(messages.any { it.contains("SYNTHETIC_HTTP_REASON_204") },
				"HTTP-error diagnostic must not expose reason payload")
		}
	}

	@Test
	fun actualRendererRetirementDiagnosticDoesNotEmitPublicationHash() = runTest {
		withCurrentDestinationEngine { bench ->
			val start = logCount()
			bench.retireRenderer()
			val messages = messagesAfter(start)
			assertTrue(messages.any { it.startsWith("Reader engine WebView render process gone") &&
				it.contains("didCrash=false") && it.contains("priorityAtExit=0") },
				"Fixture must exercise actual renderer-retirement scalar logging")
			assertFalse(messages.any { Regex("publication=-?\\d+").containsMatchIn(it) },
				"Renderer-retirement diagnostic must not emit a publication-derived hash")
		}
	}

	@Test
	fun sourceGuardrailRejectsOwningCallsitePayloadInterpolation() {
		// Supplemental source guardrail only, not runtime privacy proof or a required new projector API.
		val relative = "src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt"
		val file = listOf(File(relative), File("composeApp/$relative")).firstOrNull { it.isFile }
			?: error("Engine privacy source guardrail cannot locate its owning source")
		val source = file.readText()
		assertTrue(source.contains("actual fun ReaderEngineWebViewHost("), "Source guardrail must inspect the owning Android Engine")
		val payloadExpressions = listOf(
			"url?.engineUrlLabel()", "request.url.toString().engineUrlLabel()",
			"description=\${error.description}", "reason=\${errorResponse.reasonPhrase}",
			"publication=\${currentPublicationKey.hashCode()}"
		)
		assertFalse(payloadExpressions.any(source::contains),
			"Source guardrail: owning diagnostics cannot interpolate protected URL/error/reason/publication payload")
	}

	private fun logCount(): Int = ShadowLog.getLogsForTag("ReaderEngineWebViewHost").size

	private fun messagesAfter(start: Int): List<String> =
		ShadowLog.getLogsForTag("ReaderEngineWebViewHost").drop(start).map { it.msg }

	private fun request(marker: String): WebResourceRequest = object : WebResourceRequest {
		override fun getUrl(): Uri = Uri.parse("https://synthetic.invalid/$marker")
		override fun isForMainFrame() = true
		override fun isRedirect() = false
		override fun hasGesture() = false
		override fun getMethod() = "GET"
		override fun getRequestHeaders(): Map<String, String> = emptyMap()
	}
}

package paige.navic.ui.screens.reader

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import paige.navic.reader.ReaderBridgeCommand
import paige.navic.reader.ReaderBridgeEvent
import paige.navic.reader.ReaderJavascriptBridge
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.ReaderWebCommandDispatchState
import paige.navic.reader.commandsForReadyReaderRuntime
import paige.navic.reader.shouldDispatchReaderCommandsToWebRuntime
import paige.navic.util.core.Logger

private const val ReaderWebViewHostTag = "ReaderWebViewHost"

@Composable
actual fun ReaderWebViewHost(
	publicationUrl: String,
	title: String,
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean,
	settings: ReaderSettings,
	startCfi: String?,
	startHref: String?,
	command: ReaderBridgeCommand?,
	commandKey: Long,
	onEvent: (ReaderBridgeEvent) -> Unit,
	modifier: Modifier
) {
	val context = LocalContext.current
	val currentOnEvent by rememberUpdatedState(onEvent)
	val publicationKey = remember(publicationUrl, mediaOverlayEnabled, startCfi, startHref) {
		listOf(
			publicationUrl,
			mediaOverlayEnabled.toString(),
			startCfi.orEmpty(),
			startHref.orEmpty()
		).joinToString("|")
	}
	val openCommand = remember(publicationUrl, mediaOverlayEnabled, settings, startCfi, startHref) {
		ReaderBridgeCommand.OpenPublication(
			url = publicationUrl,
			mediaOverlayEnabled = mediaOverlayEnabled,
			startLocator = ReaderLocator(
				cfi = startCfi,
				href = startHref
			).takeIf { it.cfi != null || it.href != null },
			settings = settings
		)
	}
	val currentPublicationKey by rememberUpdatedState(publicationKey)
	val currentOpenCommand by rememberUpdatedState(openCommand)
	val currentCommand by rememberUpdatedState(command)
	val currentCommandKey by rememberUpdatedState(commandKey)
	var webView by remember { mutableStateOf<WebView?>(null) }
	var commandDispatchState by remember { mutableStateOf(ReaderWebCommandDispatchState()) }
	var readerRuntimeReady by remember { mutableStateOf(false) }
	fun handleReaderBridgeEvent(event: ReaderBridgeEvent) {
		Logger.i(ReaderWebViewHostTag, "Reader bridge event: ${event.debugLabel()}")
		if (event == ReaderBridgeEvent.Ready) {
			readerRuntimeReady = true
		}
		currentOnEvent(event)
	}
	val bridge = remember {
		ReaderJavascriptBridge(
			onEvent = { event ->
				webView?.post { handleReaderBridgeEvent(event) } ?: handleReaderBridgeEvent(event)
			},
			onRawMessage = { message ->
				Logger.i(ReaderWebViewHostTag, "Reader bridge raw: ${message.take(500)}")
			}
		)
	}

	fun WebView.dispatchReadyReaderCommands() {
		if (
			!shouldDispatchReaderCommandsToWebRuntime(
				runtimeReady = readerRuntimeReady,
				currentUrl = url,
				entrypointUrl = ReaderWebRuntime.entrypointUrl
			)
		) {
			Logger.i(
				ReaderWebViewHostTag,
				"Skipping reader command dispatch: ready=$readerRuntimeReady url=${url?.readerUrlLabel().orEmpty()}"
			)
			return
		}
		val step = commandDispatchState.commandsForReadyReaderRuntime(
			publicationKey = currentPublicationKey,
			openCommand = currentOpenCommand,
			command = currentCommand,
			commandKey = currentCommandKey
		)
		commandDispatchState = step.state
		step.commands.forEach { readerCommand ->
			Logger.i(
				ReaderWebViewHostTag,
				"Dispatching reader command: ${readerCommand.debugLabel()} " +
					"publication=${currentPublicationKey.hashCode()} key=$currentCommandKey"
			)
			evaluateJavascript(ReaderWebRuntime.commandScript(readerCommand), null)
		}
	}

	DisposableEffect(Unit) {
		onDispose {
			webView?.destroy()
			webView = null
		}
	}

	AndroidView(
		modifier = modifier,
		factory = {
			WebView(context).apply {
				webView = this
				webChromeClient = object : WebChromeClient() {
					override fun onConsoleMessage(message: ConsoleMessage): Boolean {
						val logMessage =
							"Reader console ${message.messageLevel()}: ${message.message()} " +
								"@ ${message.sourceId()}:${message.lineNumber()}"
						when (message.messageLevel()) {
							ConsoleMessage.MessageLevel.ERROR -> Logger.e(ReaderWebViewHostTag, logMessage)
							ConsoleMessage.MessageLevel.WARNING -> Logger.w(ReaderWebViewHostTag, logMessage)
							else -> Logger.i(ReaderWebViewHostTag, logMessage)
						}
						return true
					}
				}
				webViewClient = object : WebViewClient() {
					override fun onPageFinished(view: WebView, url: String?) {
						Logger.i(ReaderWebViewHostTag, "Reader page finished: ${url?.readerUrlLabel().orEmpty()}")
						view.dispatchReadyReaderCommands()
					}

					override fun onReceivedError(
						view: WebView,
						request: WebResourceRequest,
						error: WebResourceError
					) {
						Logger.e(
							ReaderWebViewHostTag,
							"Reader WebView error main=${request.isForMainFrame} " +
								"url=${request.url.toString().readerUrlLabel()} " +
								"code=${error.errorCode} description=${error.description}"
						)
					}

					override fun onReceivedHttpError(
						view: WebView,
						request: WebResourceRequest,
						errorResponse: WebResourceResponse
					) {
						Logger.w(
							ReaderWebViewHostTag,
							"Reader WebView HTTP error main=${request.isForMainFrame} " +
								"url=${request.url.toString().readerUrlLabel()} " +
								"status=${errorResponse.statusCode} reason=${errorResponse.reasonPhrase}"
						)
					}
				}
				ReaderWebRuntime.configure(this, bridge)
			}
		},
		update = { view ->
			if (
				shouldDispatchReaderCommandsToWebRuntime(
					runtimeReady = readerRuntimeReady,
					currentUrl = view.url,
					entrypointUrl = ReaderWebRuntime.entrypointUrl
				)
			) {
				view.dispatchReadyReaderCommands()
			}
		}
	)
}

private fun ReaderBridgeCommand.debugLabel(): String =
	when (this) {
		is ReaderBridgeCommand.OpenPublication ->
			"openPublication(url=${url.readerUrlLabel()}, overlay=$mediaOverlayEnabled)"
		is ReaderBridgeCommand.GoToCfi -> "goToCfi"
		is ReaderBridgeCommand.GoToHref -> "goToHref(${href.readerUrlLabel()})"
		is ReaderBridgeCommand.ApplyHighlight -> "applyHighlight"
		is ReaderBridgeCommand.ApplyHighlights -> "applyHighlights(count=${highlights.size})"
		is ReaderBridgeCommand.ApplyOverlayFragment -> "applyOverlayFragment(${fragment.fragmentId.orEmpty()})"
		ReaderBridgeCommand.ClearOverlay -> "clearOverlay"
		is ReaderBridgeCommand.ApplySettings -> "applySettings"
		is ReaderBridgeCommand.Search -> "search"
	}

private fun ReaderBridgeEvent.debugLabel(): String =
	when (this) {
		ReaderBridgeEvent.Ready -> "ready"
		ReaderBridgeEvent.PublicationReady -> "publicationReady"
		is ReaderBridgeEvent.LocationChanged -> "locationChanged(${locator.href?.readerUrlLabel().orEmpty()})"
		is ReaderBridgeEvent.CfiChanged -> "cfiChanged"
		is ReaderBridgeEvent.TocItemChanged -> "tocItemChanged(${href?.readerUrlLabel().orEmpty()})"
		is ReaderBridgeEvent.SelectionChanged -> "selectionChanged"
		is ReaderBridgeEvent.OverlayFragmentActive -> "overlayFragmentActive(${fragment.fragmentId.orEmpty()})"
		is ReaderBridgeEvent.OverlayFragmentInactive -> "overlayFragmentInactive(${fragmentId.orEmpty()})"
		is ReaderBridgeEvent.SearchResults -> "searchResults(count=${results.size})"
		is ReaderBridgeEvent.Toc -> "toc(count=${items.size})"
		is ReaderBridgeEvent.Error -> "error(code=${code.orEmpty()}, message=${message.take(120)})"
	}

private fun String.readerUrlLabel(): String {
	val scheme = substringBefore(":", missingDelimiterValue = "").takeIf { it.isNotBlank() }
	val tail = substringAfterLast('/').take(80)
	return when {
		scheme != null && tail.isNotBlank() -> "$scheme:$tail"
		scheme != null -> scheme
		else -> take(80)
	}
}

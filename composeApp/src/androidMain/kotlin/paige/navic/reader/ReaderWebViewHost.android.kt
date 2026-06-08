package paige.navic.ui.screens.reader

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
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.ReaderWebCommandDispatchState
import paige.navic.reader.commandsForReadyReaderRuntime

@Composable
actual fun ReaderWebViewHost(
	publicationUrl: String,
	title: String,
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean,
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
	val openCommand = remember(publicationUrl, mediaOverlayEnabled, startCfi, startHref) {
		ReaderBridgeCommand.OpenPublication(
			url = publicationUrl,
			mediaOverlayEnabled = mediaOverlayEnabled,
			startLocator = ReaderLocator(
				cfi = startCfi,
				href = startHref
			).takeIf { it.cfi != null || it.href != null }
		)
	}
	val currentPublicationKey by rememberUpdatedState(publicationKey)
	val currentOpenCommand by rememberUpdatedState(openCommand)
	val currentCommand by rememberUpdatedState(command)
	val currentCommandKey by rememberUpdatedState(commandKey)
	val bridge = remember {
		ReaderJavascriptBridge(
			onEvent = { event -> currentOnEvent(event) }
		)
	}
	var webView by remember { mutableStateOf<WebView?>(null) }
	var commandDispatchState by remember { mutableStateOf(ReaderWebCommandDispatchState()) }

	fun WebView.dispatchReadyReaderCommands() {
		val step = commandDispatchState.commandsForReadyReaderRuntime(
			publicationKey = currentPublicationKey,
			openCommand = currentOpenCommand,
			command = currentCommand,
			commandKey = currentCommandKey
		)
		commandDispatchState = step.state
		step.commands.forEach { readerCommand ->
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
				webViewClient = object : WebViewClient() {
					override fun onPageFinished(view: WebView, url: String?) {
						view.dispatchReadyReaderCommands()
					}
				}
				ReaderWebRuntime.configure(this, bridge)
			}
		},
		update = { view ->
			if (view.url == ReaderWebRuntime.entrypointUrl) {
				view.dispatchReadyReaderCommands()
			}
		}
	)
}

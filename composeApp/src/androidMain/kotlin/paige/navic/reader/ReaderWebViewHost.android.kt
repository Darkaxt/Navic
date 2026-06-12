package paige.navic.ui.screens.reader

import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import paige.navic.reader.ReaderBridgeCommand
import paige.navic.reader.ReaderBridgeEvent
import paige.navic.reader.ReaderJavascriptBridge
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.ReaderWebCommandDispatchState
import paige.navic.reader.commandsForReadyReaderRuntime
import paige.navic.reader.readerPublicationCacheRoot
import paige.navic.reader.readerTapZoneActionAt
import paige.navic.reader.readerTapZonePageTurnCommand
import paige.navic.reader.shouldDispatchReaderCommandsToWebRuntime
import paige.navic.util.core.Logger
import kotlin.math.abs

private const val ReaderWebViewHostTag = "ReaderWebViewHost"

@Composable
actual fun ReaderWebViewHost(
	publicationUrl: String,
	title: String,
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean,
	externalShellCover: Boolean,
	settings: ReaderSettings,
	startCfi: String?,
	startHref: String?,
	startProgress: Double?,
	command: ReaderBridgeCommand?,
	commandKey: Long,
	onEvent: (ReaderBridgeEvent) -> Unit,
	modifier: Modifier
) {
	val context = LocalContext.current
	val readerAssetLoader = remember(context) {
		WebViewAssetLoader.Builder()
			.setDomain(ReaderWebRuntime.AssetLoaderDomain)
			.addPathHandler(
				ReaderWebRuntime.AssetLoaderAssetsPathPrefix,
				WebViewAssetLoader.AssetsPathHandler(context)
			)
			.addPathHandler(
				ReaderPublicationCachePathPrefix,
				WebViewAssetLoader.InternalStoragePathHandler(context, readerPublicationCacheRoot(context))
			)
			.build()
	}
	val currentOnEvent by rememberUpdatedState(onEvent)
	val publicationKey = remember(publicationUrl, mediaOverlayEnabled, externalShellCover, startCfi, startHref, startProgress) {
		listOf(
			publicationUrl,
			mediaOverlayEnabled.toString(),
			externalShellCover.toString(),
			startCfi.orEmpty(),
			startHref.orEmpty(),
			startProgress?.toString().orEmpty()
		).joinToString("|")
	}
	val openCommand = remember(publicationUrl, mediaOverlayEnabled, externalShellCover, settings, startCfi, startHref, startProgress) {
		ReaderBridgeCommand.OpenPublication(
			url = publicationUrl,
			mediaOverlayEnabled = mediaOverlayEnabled,
			externalShellCover = externalShellCover,
			startLocator = ReaderLocator(
				cfi = startCfi,
				href = startHref,
				progress = startProgress
			).takeIf { it.cfi != null || it.href != null || it.progress != null },
			settings = settings.copy(nativeTapZones = true)
		)
	}
	val currentPublicationKey by rememberUpdatedState(publicationKey)
	val currentOpenCommand by rememberUpdatedState(openCommand)
	val currentCommand by rememberUpdatedState(command.withAndroidNativeTapZones())
	val currentCommandKey by rememberUpdatedState(commandKey)
	val currentSettings by rememberUpdatedState(settings)
	var webView by remember { mutableStateOf<WebView?>(null) }
	var webViewGeneration by remember { mutableStateOf(0) }
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

	fun WebView.dispatchReaderTapZoneCommand(command: ReaderBridgeCommand) {
		if (
			!shouldDispatchReaderCommandsToWebRuntime(
				runtimeReady = readerRuntimeReady,
				currentUrl = url,
				entrypointUrl = ReaderWebRuntime.entrypointUrl
			)
		) {
			return
		}
		Logger.i(ReaderWebViewHostTag, "Dispatching native reader tap-zone command: ${command.debugLabel()}")
		evaluateJavascript(ReaderWebRuntime.commandScript(command), null)
	}

	val readerTapZoneObserver = remember(context) {
		ReaderAndroidTapZoneObserver(
			touchSlop = ViewConfiguration.get(context).scaledTouchSlop,
			currentSettings = { currentSettings },
			dispatchReaderTapZoneCommand = { command -> webView?.dispatchReaderTapZoneCommand(command) },
			onCenterTap = { handleReaderBridgeEvent(ReaderBridgeEvent.CenterTap) }
		)
	}

	DisposableEffect(Unit) {
		onDispose {
			webView?.destroy()
			webView = null
		}
	}

	key(webViewGeneration) {
		AndroidView(
			modifier = modifier,
			factory = {
				WebView(context).apply {
					webView = this
					setOnTouchListener { view, event ->
						readerTapZoneObserver.onTouch(view as WebView, event)
						return@setOnTouchListener false
					}
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
						override fun shouldInterceptRequest(
							view: WebView,
							request: WebResourceRequest
						): WebResourceResponse? =
							readerAssetLoader.shouldInterceptRequest(request.url)
								?: super.shouldInterceptRequest(view, request)

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

						override fun onRenderProcessGone(
							view: WebView,
							detail: RenderProcessGoneDetail
						): Boolean {
							Logger.e(
								ReaderWebViewHostTag,
								"Reader WebView render process gone didCrash=${detail.didCrash()} " +
									"priorityAtExit=${detail.rendererPriorityAtExit()} " +
									"publication=${currentPublicationKey.hashCode()}"
							)
							if (webView === view) webView = null
							commandDispatchState = ReaderWebCommandDispatchState()
							readerRuntimeReady = false
							currentOnEvent(
								ReaderBridgeEvent.Error(
									message = "Reader WebView renderer stopped.",
									code = "webview_render_process_gone"
								)
							)
							view.destroy()
							webViewGeneration += 1
							return true
						}
					}
					ReaderWebRuntime.configure(
						this,
						bridge,
						enableDebugging = settings.webContentsDebuggingEnabled == true
					)
				}
			},
			update = { view ->
				view.keepScreenOn = settings.keepScreenOn == true
				ReaderWebRuntime.setWebContentsDebuggingEnabled(settings.webContentsDebuggingEnabled == true)
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
}

private fun ReaderBridgeCommand.debugLabel(): String =
	when (this) {
		is ReaderBridgeCommand.OpenPublication ->
			"openPublication(url=${url.readerUrlLabel()}, overlay=$mediaOverlayEnabled)"
		is ReaderBridgeCommand.GoToCfi -> "goToCfi"
		is ReaderBridgeCommand.GoToHref -> "goToHref(${href.readerUrlLabel()})"
		is ReaderBridgeCommand.GoToProgress -> "goToProgress(${progress.coerceIn(0.0, 1.0)})"
		ReaderBridgeCommand.NextPage -> "nextPage"
		ReaderBridgeCommand.PreviousPage -> "previousPage"
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
		ReaderBridgeEvent.CenterTap -> "readerCenterTap"
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

private fun ReaderBridgeCommand?.withAndroidNativeTapZones(): ReaderBridgeCommand? =
	when (this) {
		is ReaderBridgeCommand.OpenPublication -> copy(
			settings = (settings ?: ReaderSettings()).copy(nativeTapZones = true)
		)
		is ReaderBridgeCommand.ApplySettings -> copy(
			settings = settings.copy(nativeTapZones = true)
		)
		else -> this
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

private class ReaderAndroidTapZoneObserver(
	private val touchSlop: Int,
	private val currentSettings: () -> ReaderSettings,
	private val dispatchReaderTapZoneCommand: (ReaderBridgeCommand) -> Unit,
	private val onCenterTap: () -> Unit
) {
	private data class TouchState(
		val pointerId: Int,
		val startX: Float,
		val startY: Float,
		var moved: Boolean = false
	)

	private var touchState: TouchState? = null

	fun onTouch(webView: WebView, event: MotionEvent) {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				touchState = TouchState(
					pointerId = event.getPointerId(event.actionIndex),
					startX = event.x,
					startY = event.y
				)
			}
			MotionEvent.ACTION_POINTER_DOWN,
			MotionEvent.ACTION_CANCEL -> {
				touchState = null
			}
			MotionEvent.ACTION_MOVE -> {
				val state = touchState ?: return
				val pointerIndex = event.findPointerIndex(state.pointerId)
				if (pointerIndex < 0) {
					touchState = null
					return
				}
				if (
					abs(event.getX(pointerIndex) - state.startX) > touchSlop ||
					abs(event.getY(pointerIndex) - state.startY) > touchSlop
				) {
					state.moved = true
				}
			}
			MotionEvent.ACTION_UP -> {
				val state = touchState ?: return
				touchState = null
				if (state.moved || readerWebViewHitTestShouldStayInContent(webView.hitTestResult)) return
				val width = webView.width.takeIf { it > 0 } ?: return
				val height = webView.height.takeIf { it > 0 } ?: return
				val settings = currentSettings()
				val action = readerTapZoneActionAt(
					tapZone = settings.tapZone,
					xFraction = (event.x / width).coerceIn(0f, 1f),
					yFraction = (event.y / height).coerceIn(0f, 1f),
					smallerTapZone = settings.smallerTapZone == true,
					flowMode = settings.flowMode
				)
				val command = readerTapZonePageTurnCommand(action, settings.direction)
				if (command == null) {
					onCenterTap()
				} else {
					dispatchReaderTapZoneCommand(command)
				}
			}
		}
	}
}

private fun readerWebViewHitTestShouldStayInContent(result: WebView.HitTestResult?): Boolean =
	when (result?.type) {
		WebView.HitTestResult.SRC_ANCHOR_TYPE,
		WebView.HitTestResult.IMAGE_TYPE,
		WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
		WebView.HitTestResult.EMAIL_TYPE,
		WebView.HitTestResult.PHONE_TYPE,
		WebView.HitTestResult.GEO_TYPE -> true
		else -> false
	}

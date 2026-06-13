package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import paige.navic.reader.ReaderTapZoneAction
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.ReaderWebCommandDispatchState
import paige.navic.reader.commandsForReadyReaderRuntime
import paige.navic.reader.readerTapZoneActionAt
import paige.navic.reader.readerTapZonePageTurnCommand
import paige.navic.reader.readerPublicationCacheRoot
import paige.navic.reader.shouldDispatchReaderCommandsToWebRuntime
import paige.navic.util.core.Logger
import java.util.concurrent.atomic.AtomicReference

private const val ReaderWebViewHostTag = "ReaderWebViewHost"
private const val ReaderContentTapHandledSuppressMs = 1000L
private const val ReaderCenterTapDelayMs = 700L

@Composable
actual fun ReaderWebViewHost(
	publicationUrl: String,
	title: String,
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean,
	externalShellCover: Boolean,
	nativeShellCoverUrl: String?,
	canReturnToShellCover: Boolean,
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
	val currentNativeShellCoverUrl by rememberUpdatedState(nativeShellCoverUrl)
	val currentCanReturnToShellCover by rememberUpdatedState(canReturnToShellCover)
	var webView by remember { mutableStateOf<WebView?>(null) }
	var coverWebView by remember { mutableStateOf<WebView?>(null) }
	var webViewGeneration by remember { mutableStateOf(0) }
	var commandDispatchState by remember { mutableStateOf(ReaderWebCommandDispatchState()) }
	var readerRuntimeReady by remember { mutableStateOf(false) }
	val surfaceHostRef = remember { AtomicReference<ReaderSurfaceHost?>(null) }
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
				if (event == ReaderBridgeEvent.ContentTapHandled) {
					surfaceHostRef.get()?.markContentTapHandled()
					Logger.i(ReaderWebViewHostTag, "Reader bridge event: ${event.debugLabel()}")
				} else {
					webView?.post { handleReaderBridgeEvent(event) } ?: handleReaderBridgeEvent(event)
				}
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
			surfaceHostRef.set(null)
			webView?.destroy()
			webView = null
			coverWebView?.destroy()
			coverWebView = null
		}
	}

	key(webViewGeneration) {
		AndroidView(
			modifier = modifier,
			factory = {
				val readerWebView = WebView(context).apply {
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
				val readerCoverWebView = WebView(context).apply {
					coverWebView = this
					setBackgroundColor(Color.BLACK)
					isVerticalScrollBarEnabled = false
					isHorizontalScrollBarEnabled = false
					webViewClient = object : WebViewClient() {
						override fun shouldInterceptRequest(
							view: WebView,
							request: WebResourceRequest
						): WebResourceResponse? =
							readerAssetLoader.shouldInterceptRequest(request.url)
								?: super.shouldInterceptRequest(view, request)
					}
				}
				ReaderSurfaceHost(context).apply {
					surfaceHostRef.set(this)
					this.readerWebView = readerWebView
					this.shellCoverWebView = readerCoverWebView
					readerSettings = currentSettings
					readerWideTapsEnabled = true
					this.canReturnToShellCover = currentCanReturnToShellCover
					updateShellCover(currentNativeShellCoverUrl, title)
					onReaderCommand = { readerCommand ->
						readerWebView.post {
							Logger.i(
								ReaderWebViewHostTag,
								"Dispatching reader surface command: ${readerCommand.debugLabel()} " +
									"publication=${currentPublicationKey.hashCode()}"
							)
							readerWebView.evaluateJavascript(ReaderWebRuntime.commandScript(readerCommand), null)
						}
					}
					onReaderCenterTap = {
						readerWebView.post { handleReaderBridgeEvent(ReaderBridgeEvent.CenterTap) }
					}
					addView(
						readerWebView,
						FrameLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT,
							ViewGroup.LayoutParams.MATCH_PARENT
						)
					)
					addView(
						readerCoverWebView,
						FrameLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT,
							ViewGroup.LayoutParams.MATCH_PARENT
						)
					)
				}
			},
			update = { view ->
				view.readerSettings = settings
				view.readerWideTapsEnabled = true
				view.canReturnToShellCover = canReturnToShellCover
				view.updateShellCover(nativeShellCoverUrl, title)
				view.keepScreenOn = settings.keepScreenOn == true
				view.onReaderCommand = { readerCommand ->
					webView?.post {
						Logger.i(
							ReaderWebViewHostTag,
							"Dispatching reader surface command: ${readerCommand.debugLabel()} " +
								"publication=${currentPublicationKey.hashCode()}"
						)
						webView?.evaluateJavascript(ReaderWebRuntime.commandScript(readerCommand), null)
					}
				}
				view.onReaderCenterTap = {
					webView?.post { handleReaderBridgeEvent(ReaderBridgeEvent.CenterTap) }
						?: handleReaderBridgeEvent(ReaderBridgeEvent.CenterTap)
				}
				val activeWebView = webView ?: return@AndroidView
				activeWebView.keepScreenOn = settings.keepScreenOn == true
				ReaderWebRuntime.setWebContentsDebuggingEnabled(settings.webContentsDebuggingEnabled == true)
				if (
					shouldDispatchReaderCommandsToWebRuntime(
						runtimeReady = readerRuntimeReady,
						currentUrl = activeWebView.url,
						entrypointUrl = ReaderWebRuntime.entrypointUrl
					)
				) {
					activeWebView.dispatchReadyReaderCommands()
				}
			}
		)
	}
}

private class ReaderSurfaceHost(context: Context) : FrameLayout(context) {
	var readerWebView: WebView? = null
	var shellCoverWebView: WebView? = null
	var readerSettings: ReaderSettings = ReaderSettings()
	var readerWideTapsEnabled: Boolean = true
	var canReturnToShellCover: Boolean = false
	var onReaderCommand: (ReaderBridgeCommand) -> Unit = {}
	var onReaderCenterTap: () -> Unit = {}
	private var shellCoverUrl: String? = null
	private var shellCoverTitle: String = ""
	private var shellCoverVisible: Boolean = false
	private val tapSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
	private val shellCoverSwipeThresholdPx = ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat()
	private var tapCandidatePointerId: Int = MotionEvent.INVALID_POINTER_ID
	private var tapDownX: Float = 0f
	private var tapDownY: Float = 0f
	private var tapCandidate: Boolean = false
	@Volatile
	private var contentTapHandledUntilMs: Long = 0L
	private var pendingCenterTap: Runnable? = null

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		if (readerWideTapsEnabled && shellCoverVisible) {
			handleReaderSurfaceTouch(event)
			return true
		}
		val childHandled = super.dispatchTouchEvent(event)
		if (readerWideTapsEnabled) {
			handleReaderSurfaceTouch(event)
		}
		return childHandled
	}

	private fun handleReaderSurfaceTouch(event: MotionEvent) {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				cancelPendingReaderCenterTap()
				tapCandidatePointerId = event.getPointerId(0)
				tapDownX = event.x
				tapDownY = event.y
				tapCandidate = true
				Logger.i(
					ReaderWebViewHostTag,
					"Reader surface touch down x=${event.x.toInt()} y=${event.y.toInt()} " +
						"shellCover=$shellCoverVisible"
				)
			}
			MotionEvent.ACTION_POINTER_DOWN -> {
				tapCandidate = false
				tapCandidatePointerId = MotionEvent.INVALID_POINTER_ID
			}
			MotionEvent.ACTION_MOVE -> {
				if (tapCandidatePointerId == MotionEvent.INVALID_POINTER_ID) return
				val pointerIndex = event.findPointerIndex(tapCandidatePointerId)
				if (pointerIndex < 0) {
					clearTapCandidate()
					return
				}
				val dx = event.getX(pointerIndex) - tapDownX
				val dy = event.getY(pointerIndex) - tapDownY
				if (dispatchReaderShellCoverSwipe(dx, dy)) {
					clearTapCandidate()
					return
				}
				if ((dx * dx) + (dy * dy) > tapSlopPx * tapSlopPx) {
					tapCandidate = false
				}
			}
			MotionEvent.ACTION_UP -> {
				if (tapCandidate) {
					dispatchReaderWideTap(event)
				} else if (tapCandidatePointerId != MotionEvent.INVALID_POINTER_ID) {
					dispatchReaderShellCoverSwipe(
						deltaX = event.x - tapDownX,
						deltaY = event.y - tapDownY
					)
				}
				clearTapCandidate()
			}
			MotionEvent.ACTION_CANCEL -> clearTapCandidate()
		}
	}

	private fun clearTapCandidate() {
		tapCandidatePointerId = MotionEvent.INVALID_POINTER_ID
		tapCandidate = false
	}

	private fun dispatchReaderShellCoverSwipe(deltaX: Float, deltaY: Float): Boolean {
		if (!shellCoverVisible) return false
		if (kotlin.math.abs(deltaX) < shellCoverSwipeThresholdPx) return false
		if (kotlin.math.abs(deltaX) <= kotlin.math.abs(deltaY)) return false
		val action = if (deltaX < 0f) {
			ReaderTapZoneAction.Right
		} else {
			ReaderTapZoneAction.Left
		}
		val command = readerTapZonePageTurnCommand(action, readerSettings.direction) ?: return false
		Logger.i(
			ReaderWebViewHostTag,
			"Reader shell cover swipe action=$action command=${command.debugLabel()} " +
				"delta=${deltaX.toInt()},${deltaY.toInt()}"
		)
		dispatchReaderPageTurnCommand(command)
		return true
	}

	private fun dispatchReaderWideTap(event: MotionEvent) {
		if (width <= 0 || height <= 0) return
		val contentHitType = readerWebView?.hitTestResult?.type ?: WebView.HitTestResult.UNKNOWN_TYPE
		if (!shellCoverVisible && readerContentTapHandled()) {
			Logger.i(
				ReaderWebViewHostTag,
				"Reader surface tap ignored for explicit content handler " +
					"x=${event.x.toInt()} y=${event.y.toInt()} hitType=$contentHitType"
			)
			return
		}
		if (!shellCoverVisible && readerContentHandledTap(contentHitType)) {
			Logger.i(
				ReaderWebViewHostTag,
				"Reader surface tap ignored for content hitType=$contentHitType " +
					"x=${event.x.toInt()} y=${event.y.toInt()}"
			)
			return
		}
		val action = readerTapZoneActionAt(
			tapZone = readerSettings.tapZone,
			xFraction = event.x / width.toFloat(),
			yFraction = event.y / height.toFloat(),
			smallerTapZone = readerSettings.smallerTapZone == true,
			flowMode = readerSettings.flowMode
		)
		val command = readerTapZonePageTurnCommand(action, readerSettings.direction)
		Logger.i(
			ReaderWebViewHostTag,
			"Reader surface tap action=$action command=${command?.debugLabel().orEmpty()} " +
				"x=${event.x.toInt()} y=${event.y.toInt()} " +
				"fractions=${event.x / width.toFloat()},${event.y / height.toFloat()} " +
				"hitType=$contentHitType shellCover=$shellCoverVisible"
		)
		if (command != null) {
			cancelPendingReaderCenterTap()
			dispatchReaderPageTurnCommand(command)
		} else if (!shellCoverVisible && readerContentHandledCenterTap(contentHitType)) {
			Logger.i(
				ReaderWebViewHostTag,
				"Reader surface center tap ignored for content hitType=$contentHitType " +
					"x=${event.x.toInt()} y=${event.y.toInt()}"
			)
		} else {
			scheduleReaderCenterTap(event.x.toInt(), event.y.toInt(), contentHitType)
		}
	}

	fun markContentTapHandled() {
		contentTapHandledUntilMs = SystemClock.uptimeMillis() + ReaderContentTapHandledSuppressMs
		cancelPendingReaderCenterTap()
	}

	private fun readerContentTapHandled(): Boolean =
		SystemClock.uptimeMillis() <= contentTapHandledUntilMs

	private fun readerContentHandledCenterTap(hitType: Int): Boolean =
		readerContentHandledTap(hitType) || hitType == WebView.HitTestResult.IMAGE_TYPE

	private fun scheduleReaderCenterTap(x: Int, y: Int, hitType: Int) {
		cancelPendingReaderCenterTap()
		val pending = Runnable {
			pendingCenterTap = null
			if (!shellCoverVisible && readerContentTapHandled()) {
				Logger.i(
					ReaderWebViewHostTag,
					"Reader surface tap ignored for explicit content handler x=$x y=$y hitType=$hitType"
				)
				return@Runnable
			}
			Logger.i(ReaderWebViewHostTag, "Reader surface dispatch center tap x=$x y=$y hitType=$hitType")
			onReaderCenterTap()
		}
		pendingCenterTap = pending
		postDelayed(pending, ReaderCenterTapDelayMs)
	}

	private fun cancelPendingReaderCenterTap() {
		pendingCenterTap?.let(::removeCallbacks)
		pendingCenterTap = null
	}

	fun updateShellCover(coverUrl: String?, title: String) {
		val nextCoverUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() }
		if (nextCoverUrl == null) {
			shellCoverUrl = null
			shellCoverTitle = ""
			hideShellCover()
			shellCoverWebView?.loadUrl("about:blank")
			return
		}
		val changed = shellCoverUrl != nextCoverUrl || shellCoverTitle != title
		shellCoverUrl = nextCoverUrl
		shellCoverTitle = title
		if (changed) {
			shellCoverVisible = true
			shellCoverWebView?.visibility = View.VISIBLE
			shellCoverWebView?.loadDataWithBaseURL(
				ReaderWebRuntime.AssetLoaderOrigin,
				readerShellCoverHtml(nextCoverUrl, title),
				"text/html",
				"UTF-8",
				null
			)
		} else if (shellCoverVisible) {
			shellCoverWebView?.visibility = View.VISIBLE
		}
	}

	private fun dispatchReaderPageTurnCommand(command: ReaderBridgeCommand) {
		if (shellCoverVisible) {
			Logger.i(
				ReaderWebViewHostTag,
				"Reader shell cover command=${command.debugLabel()} " +
					"canReturn=$canReturnToShellCover"
			)
			when (command) {
				ReaderBridgeCommand.NextPage -> hideShellCover()
				ReaderBridgeCommand.PreviousPage -> Unit
				else -> onReaderCommand(command)
			}
			return
		}
		if (command == ReaderBridgeCommand.PreviousPage && canReturnToShellCover && !shellCoverUrl.isNullOrBlank()) {
			Logger.i(ReaderWebViewHostTag, "Reader surface returning to shell cover")
			showShellCover()
			return
		}
		Logger.i(ReaderWebViewHostTag, "Reader surface dispatch command=${command.debugLabel()}")
		onReaderCommand(command)
	}

	private fun showShellCover() {
		shellCoverVisible = !shellCoverUrl.isNullOrBlank()
		shellCoverWebView?.visibility = if (shellCoverVisible) View.VISIBLE else View.GONE
	}

	private fun hideShellCover() {
		shellCoverVisible = false
		shellCoverWebView?.visibility = View.GONE
	}

	private fun readerContentHandledTap(hitType: Int): Boolean =
		when (hitType) {
			WebView.HitTestResult.PHONE_TYPE,
			WebView.HitTestResult.GEO_TYPE,
			WebView.HitTestResult.EMAIL_TYPE,
			WebView.HitTestResult.SRC_ANCHOR_TYPE,
			WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
			WebView.HitTestResult.EDIT_TEXT_TYPE -> true
			else -> false
		}
}

private fun readerShellCoverHtml(coverUrl: String, title: String): String =
	"""
	<!doctype html>
	<html>
	<head>
	  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
	  <style>
	    html, body {
	      width: 100%;
	      height: 100%;
	      margin: 0;
	      padding: 0;
	      overflow: hidden;
	      background: #000000;
	    }
	    body {
	      display: flex;
	      align-items: center;
	      justify-content: center;
	    }
	    img {
	      display: block;
	      width: auto;
	      height: auto;
	      max-width: 100vw;
	      max-height: 100vh;
	      object-fit: contain;
	    }
	  </style>
	</head>
	<body>
	  <img src="${coverUrl.readerHtmlAttributeEscape()}" alt="${title.readerHtmlAttributeEscape()}">
	</body>
	</html>
	""".trimIndent()

private fun String.readerHtmlAttributeEscape(): String =
	buildString(length) {
		this@readerHtmlAttributeEscape.forEach { char ->
			when (char) {
				'&' -> append("&amp;")
				'"' -> append("&quot;")
				'\'' -> append("&#39;")
				'<' -> append("&lt;")
				'>' -> append("&gt;")
				else -> append(char)
			}
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
		ReaderBridgeEvent.ContentTapHandled -> "contentTapHandled"
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

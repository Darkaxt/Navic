package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.GestureDetector
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
import paige.navic.reader.readerShellCoverSwipeAction
import paige.navic.reader.shouldDispatchReaderCommandsToWebRuntime
import paige.navic.util.core.Logger
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.min

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
					val surfaceHost = surfaceHostRef.get()
					if (surfaceHost == null) {
						Logger.i(ReaderWebViewHostTag, "Reader bridge event: ${event.debugLabel()} surface=missing")
					} else {
						surfaceHost.post {
							surfaceHost.markContentTapHandled()
							Logger.i(ReaderWebViewHostTag, "Reader bridge event: ${event.debugLabel()} surface=marked")
						}
					}
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
				val readerShellCoverView = ReaderShellCoverView(context)
				ReaderSurfaceHost(context).apply {
					surfaceHostRef.set(this)
					this.readerWebView = readerWebView
					this.shellCoverView = readerShellCoverView
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
						readerShellCoverView,
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
	var shellCoverView: ReaderShellCoverView? = null
	var readerSettings: ReaderSettings = ReaderSettings()
	var readerWideTapsEnabled: Boolean = true
	var canReturnToShellCover: Boolean = false
	var onReaderCommand: (ReaderBridgeCommand) -> Unit = {}
	var onReaderCenterTap: () -> Unit = {}
	private var shellCoverUrl: String? = null
	private var shellCoverTitle: String = ""
	private var shellCoverVisible: Boolean = false
	private val tapSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
	private val shellCoverSwipeThresholdPx = tapSlopPx
	private var tapCandidatePointerId: Int = MotionEvent.INVALID_POINTER_ID
	private var tapDownX: Float = 0f
	private var tapDownY: Float = 0f
	private var tapCandidate: Boolean = false
	private var shellCoverDragDiagnosticLogged: Boolean = false
	@Volatile
	private var contentTapHandledUntilMs: Long = 0L
	private var pendingCenterTap: Runnable? = null
	private var centerTapSequence: Long = 0L

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		val shellCoverWasVisible = shellCoverVisible
		if (readerWideTapsEnabled) {
			cancelPendingReaderCenterTapOnNewGesture(event)
		}
		val childHandled = super.dispatchTouchEvent(event)
		if (readerWideTapsEnabled) {
			handleReaderSurfaceTouch(event)
			readerGestureDetector.onTouchEvent(event)
		}
		return if (readerWideTapsEnabled && shellCoverWasVisible) {
			true
		} else {
			childHandled
		}
	}

	private val readerGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
		override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
			dispatchReaderWideTap(event)
			return true
		}
	})

	private fun handleReaderSurfaceTouch(event: MotionEvent) {
		if (!shellCoverVisible) {
			if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
				clearTapCandidate()
			}
			return
		}
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				tapCandidatePointerId = event.getPointerId(0)
				tapDownX = event.x
				tapDownY = event.y
				tapCandidate = true
				shellCoverDragDiagnosticLogged = false
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
				logReaderShellCoverDragCandidate(dx, dy)
				if (dispatchReaderShellCoverSwipe(dx, dy)) {
					clearTapCandidate()
					return
				}
				if ((dx * dx) + (dy * dy) > tapSlopPx * tapSlopPx) {
					tapCandidate = false
				}
			}
			MotionEvent.ACTION_UP -> {
				if (!tapCandidate && tapCandidatePointerId != MotionEvent.INVALID_POINTER_ID) {
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

	private fun cancelPendingReaderCenterTapOnNewGesture(event: MotionEvent) {
		if (event.actionMasked == MotionEvent.ACTION_DOWN) {
			cancelPendingReaderCenterTap()
		}
	}

	private fun clearTapCandidate() {
		tapCandidatePointerId = MotionEvent.INVALID_POINTER_ID
		tapCandidate = false
	}

	private fun logReaderShellCoverDragCandidate(deltaX: Float, deltaY: Float) {
		if (!shellCoverVisible || shellCoverDragDiagnosticLogged) return
		if (abs(deltaX) <= tapSlopPx && abs(deltaY) <= tapSlopPx) return
		shellCoverDragDiagnosticLogged = true
		val action = readerShellCoverSwipeAction(deltaX, deltaY, shellCoverSwipeThresholdPx)
		Logger.i(
			ReaderWebViewHostTag,
			"Reader shell cover drag candidate action=${action?.name.orEmpty()} " +
				"delta=${deltaX.toInt()},${deltaY.toInt()} " +
				"threshold=${shellCoverSwipeThresholdPx.toInt()}"
		)
	}

	private fun dispatchReaderShellCoverSwipe(deltaX: Float, deltaY: Float): Boolean {
		if (!shellCoverVisible) return false
		val action = readerShellCoverSwipeAction(deltaX, deltaY, shellCoverSwipeThresholdPx) ?: return false
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
		val centerTapToken = ++centerTapSequence
		val webView = readerWebView
		if (webView != null) {
			queryReaderContentActionAtPoint(webView, x, y) { handled ->
				if (centerTapToken != centerTapSequence) return@queryReaderContentActionAtPoint
				if (!handled || shellCoverVisible) return@queryReaderContentActionAtPoint
				val callbackHitType = readerWebView?.hitTestResult?.type ?: hitType
				contentTapHandledUntilMs = SystemClock.uptimeMillis() + ReaderContentTapHandledSuppressMs
				pendingCenterTap?.let(::removeCallbacks)
				pendingCenterTap = null
				Logger.i(
					ReaderWebViewHostTag,
					"Reader surface center tap ignored for immediate runtime content hit x=$x y=$y hitType=$callbackHitType"
				)
			}
		}
		val pending = Runnable {
			if (centerTapToken != centerTapSequence) return@Runnable
			pendingCenterTap = null
			val latestHitType = readerWebView?.hitTestResult?.type ?: hitType
			if (!shellCoverVisible && readerContentTapHandled()) {
				Logger.i(
					ReaderWebViewHostTag,
					"Reader surface tap ignored for explicit content handler x=$x y=$y hitType=$latestHitType"
				)
				return@Runnable
			}
			if (!shellCoverVisible && readerContentHandledCenterTap(latestHitType)) {
				Logger.i(
					ReaderWebViewHostTag,
					"Reader surface delayed center tap ignored for content hitType=$latestHitType x=$x y=$y"
				)
				return@Runnable
			}
			if (webView == null) {
				Logger.i(ReaderWebViewHostTag, "Reader surface dispatch center tap x=$x y=$y hitType=$latestHitType")
				onReaderCenterTap()
				return@Runnable
			}
			queryReaderContentActionAtPoint(webView, x, y) { handled ->
				if (centerTapToken != centerTapSequence) return@queryReaderContentActionAtPoint
				val callbackHitType = readerWebView?.hitTestResult?.type ?: latestHitType
				if (!shellCoverVisible && readerContentTapHandled()) {
					Logger.i(
						ReaderWebViewHostTag,
						"Reader surface tap ignored for explicit content handler x=$x y=$y hitType=$callbackHitType"
					)
					return@queryReaderContentActionAtPoint
				}
				if (!shellCoverVisible && readerContentHandledCenterTap(callbackHitType)) {
					Logger.i(
						ReaderWebViewHostTag,
						"Reader surface delayed center tap ignored for content hitType=$callbackHitType x=$x y=$y"
					)
					return@queryReaderContentActionAtPoint
				}
				if (!shellCoverVisible && handled) {
					Logger.i(
						ReaderWebViewHostTag,
						"Reader surface delayed center tap ignored for runtime content hit x=$x y=$y hitType=$callbackHitType"
					)
					return@queryReaderContentActionAtPoint
				}
				Logger.i(ReaderWebViewHostTag, "Reader surface dispatch center tap x=$x y=$y hitType=$callbackHitType")
				onReaderCenterTap()
			}
		}
		pendingCenterTap = pending
		postDelayed(pending, ReaderCenterTapDelayMs)
	}

	private fun queryReaderContentActionAtPoint(
		webView: WebView,
		x: Int,
		y: Int,
		onResult: (Boolean) -> Unit
	) {
		webView.evaluateJavascript(
			"Boolean(window.NavicReaderBridge && " +
				"window.NavicReaderBridge.readerContentActionAtPoint && " +
				"window.NavicReaderBridge.readerContentActionAtPoint($x,$y,${webView.width},${webView.height}))"
		) { value ->
			onResult(value.equals("true", ignoreCase = true))
		}
	}

	private fun cancelPendingReaderCenterTap() {
		centerTapSequence += 1
		pendingCenterTap?.let(::removeCallbacks)
		pendingCenterTap = null
	}

	fun updateShellCover(coverUrl: String?, title: String) {
		val nextCoverUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() }
		if (nextCoverUrl == null) {
			shellCoverUrl = null
			shellCoverTitle = ""
			hideShellCover()
			shellCoverView?.updateCover(null, "")
			return
		}
		val changed = shellCoverUrl != nextCoverUrl || shellCoverTitle != title
		shellCoverUrl = nextCoverUrl
		shellCoverTitle = title
		if (changed) {
			shellCoverVisible = true
			shellCoverView?.visibility = View.VISIBLE
			shellCoverView?.updateCover(nextCoverUrl, title)
		} else if (shellCoverVisible) {
			shellCoverView?.visibility = View.VISIBLE
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
		shellCoverView?.visibility = if (shellCoverVisible) View.VISIBLE else View.GONE
	}

	private fun hideShellCover() {
		shellCoverVisible = false
		shellCoverView?.visibility = View.GONE
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

private class ReaderShellCoverView(context: Context) : View(context) {
	private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
		isDither = true
	}
	private val bitmapDestination = RectF()
	private var coverUrl: String? = null
	private var coverTitle: String = ""
	private var coverBitmap: Bitmap? = null

	init {
		setBackgroundColor(Color.BLACK)
		visibility = GONE
	}

	fun updateCover(coverUrl: String?, title: String) {
		val nextCoverUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() }
		if (this.coverUrl == nextCoverUrl && coverTitle == title) return
		this.coverUrl = nextCoverUrl
		coverTitle = title
		contentDescription = title.takeIf { it.isNotBlank() } ?: "Book cover"
		coverBitmap?.recycle()
		coverBitmap = nextCoverUrl
			?.let { readerPublicationCacheFileForAssetUrl(context, it) }
			?.takeIf { it.isFile && it.length() > 0L }
			?.let { coverFile ->
				BitmapFactory.decodeFile(coverFile.absolutePath).also { decoded ->
					if (decoded == null) {
						Logger.w(
							ReaderWebViewHostTag,
							"Reader shell cover decode failed file=${coverFile.name} url=${nextCoverUrl.readerUrlLabel()}"
						)
					}
				}
			}
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawColor(Color.BLACK)
		val bitmap = coverBitmap ?: return
		if (width <= 0 || height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return
		val scale = min(
			width.toFloat() / bitmap.width.toFloat(),
			height.toFloat() / bitmap.height.toFloat()
		)
		val drawWidth = bitmap.width.toFloat() * scale
		val drawHeight = bitmap.height.toFloat() * scale
		val left = (width.toFloat() - drawWidth) / 2f
		val top = (height.toFloat() - drawHeight) / 2f
		bitmapDestination.set(left, top, left + drawWidth, top + drawHeight)
		canvas.drawBitmap(bitmap, null, bitmapDestination, bitmapPaint)
	}

	override fun onDetachedFromWindow() {
		coverBitmap?.recycle()
		coverBitmap = null
		super.onDetachedFromWindow()
	}
}

private fun readerPublicationCacheFileForAssetUrl(context: Context, coverUrl: String): File? {
	val prefix = ReaderWebRuntime.AssetLoaderOrigin + ReaderPublicationCachePathPrefix
	if (!coverUrl.startsWith(prefix)) return null
	val encodedRelativePath = coverUrl
		.removePrefix(prefix)
		.substringBefore('?')
		.substringBefore('#')
		.trimStart('/')
		.takeIf { it.isNotBlank() }
		?: return null
	val relativePath = URLDecoder
		.decode(encodedRelativePath, StandardCharsets.UTF_8.name())
		.replace('\\', '/')
	if (
		relativePath == ".." ||
		relativePath.startsWith("../") ||
		relativePath.contains("/../")
	) {
		Logger.w(ReaderWebViewHostTag, "Reader shell cover rejected unsafe path=${relativePath.take(120)}")
		return null
	}
	val cacheRoot = readerPublicationCacheRoot(context).canonicalFile
	val coverFile = File(cacheRoot, relativePath).canonicalFile
	val cacheRootPrefix = cacheRoot.path + File.separator
	if (coverFile != cacheRoot && !coverFile.path.startsWith(cacheRootPrefix)) {
		Logger.w(ReaderWebViewHostTag, "Reader shell cover rejected outside cache path=${coverFile.path.take(160)}")
		return null
	}
	return coverFile
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

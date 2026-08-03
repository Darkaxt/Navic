package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
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
import paige.navic.reader.ReaderEngineHostCommand
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderJavascriptBridge
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderOverlayCoordinateMode
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderUnboundFoliateSessionId
import paige.navic.reader.ReaderWebCommandDispatchState
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.commandsForReadyReaderRuntime
import paige.navic.reader.readerManagedStorageRoot
import paige.navic.reader.shouldDispatchReaderCommandsToWebRuntime
import paige.navic.util.core.Logger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val ReaderEngineWebViewHostTag = "ReaderEngineWebViewHost"

private class ReaderEngineWebView(context: Context) : WebView(context) {
	var windowVisibilityListener: ((Int) -> Unit)? = null

	override fun onWindowVisibilityChanged(visibility: Int) {
		super.onWindowVisibilityChanged(visibility)
		windowVisibilityListener?.invoke(visibility)
	}
}

private object ReaderWebViewReleaseQueue {
	private val handler = Handler(Looper.getMainLooper())

	fun enqueue(release: () -> Unit) {
		handler.post { release() }
	}
}

@Composable
actual fun ReaderEngineWebViewHost(
	publicationUrl: String,
	title: String,
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean,
	externalShellCover: Boolean,
	suppressWebShellCover: Boolean,
	nativeShellCoverTint: String?,
	settings: ReaderSettings,
	startCfi: String?,
	startHref: String?,
	startProgress: Double?,
	command: ReaderEngineHostCommand?,
	commandKey: Long,
	onEvent: (ReaderEngineHostEvent) -> Unit,
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
				WebViewAssetLoader.InternalStoragePathHandler(context, readerManagedStorageRoot(context))
			)
			.build()
	}
	val currentOnEvent by rememberUpdatedState(onEvent)
	val publicationKey = remember(
		publicationUrl,
		mediaOverlayEnabled,
		externalShellCover,
		suppressWebShellCover,
		nativeShellCoverTint,
		startCfi,
		startHref,
		startProgress
	) {
		listOf(
			publicationUrl,
			mediaOverlayEnabled.toString(),
			externalShellCover.toString(),
			suppressWebShellCover.toString(),
			nativeShellCoverTint.orEmpty(),
			startCfi.orEmpty(),
			startHref.orEmpty(),
			startProgress?.toString().orEmpty()
		).joinToString("|")
	}
	val openCommand = remember(
		publicationUrl,
		mediaOverlayEnabled,
		externalShellCover,
		suppressWebShellCover,
		nativeShellCoverTint,
		settings,
		startCfi,
		startHref,
		startProgress
	) {
		ReaderBridgeCommand.OpenPublication(
			url = publicationUrl,
			foliateSessionId = ReaderUnboundFoliateSessionId,
			mediaOverlayEnabled = mediaOverlayEnabled,
			externalShellCover = externalShellCover,
			suppressWebShellCover = suppressWebShellCover,
			nativeShellCoverTint = nativeShellCoverTint,
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
	val currentCommand by rememberUpdatedState(command.toReaderBridgeCommandWithEngineNativeTapZones())
	val currentCommandKey by rememberUpdatedState(commandKey)
	var webView by remember { mutableStateOf<WebView?>(null) }
	var webViewGeneration by remember { mutableStateOf(0) }
	var commandDispatchState by remember { mutableStateOf(ReaderWebCommandDispatchState()) }
	var readerRuntimeReady by remember { mutableStateOf(false) }
	val runtimeRecovery = remember { ReaderEngineRuntimeRecovery() }

	fun restartInterruptedReaderRuntime(
		generation: Int,
		retireGeneration: () -> Boolean
	) {
		if (generation != webViewGeneration || !retireGeneration()) return
		runtimeRecovery.reset()
		readerRuntimeReady = false
		Logger.i(
			ReaderEngineWebViewHostTag,
			"Restarting interrupted reader runtime after window visibility restored"
		)
		webViewGeneration += 1
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
				ReaderEngineWebViewHostTag,
				"Skipping reader command dispatch: ready=$readerRuntimeReady url=${url?.engineUrlLabel().orEmpty()}"
			)
			return
		}
		val step = commandDispatchState.commandsForReadyReaderRuntime(
			runtimeGeneration = webViewGeneration,
			publicationKey = currentPublicationKey,
			openCommand = currentOpenCommand,
			command = currentCommand,
			commandKey = currentCommandKey
		)
		commandDispatchState = step.state
		step.commands.forEach { dispatch ->
			Logger.i(
				ReaderEngineWebViewHostTag,
				"Dispatching reader engine command: ${dispatch.command.engineDebugLabel()} " +
					"id=${dispatch.id} generation=$webViewGeneration " +
					"publication=${currentPublicationKey.hashCode()} key=$currentCommandKey"
			)
			evaluateJavascript(ReaderWebRuntime.commandScript(dispatch), null)
		}
	}

	fun handleReaderBridgeEvent(event: ReaderBridgeEvent) {
		Logger.i(ReaderEngineWebViewHostTag, "Reader bridge event: ${event.engineDebugLabel()}")
		when (event) {
			ReaderBridgeEvent.Ready -> {
				runtimeRecovery.onRuntimeReady()
				readerRuntimeReady = true
				webView?.dispatchReadyReaderCommands()
			}
			is ReaderBridgeEvent.CommandAcknowledged -> {
				commandDispatchState = commandDispatchState.acknowledge(event.commandId)
				webView?.dispatchReadyReaderCommands()
				return
			}
			is ReaderBridgeEvent.LocationChanged -> {
				commandDispatchState = commandDispatchState.observeLocator(event.locator)
			}
			else -> Unit
		}
		currentOnEvent(ReaderEngineHostEvent.FoliateBridge(event))
	}

	key(webViewGeneration) {
		val generation = webViewGeneration
		val generationDisposed = remember(generation) { AtomicBoolean(false) }
		val generationReleased = remember(generation) { AtomicBoolean(false) }
		val generationWebView = remember(generation) { AtomicReference<WebView?>(null) }
		val bridge = remember(generation) {
			ReaderJavascriptBridge(
				onEvent = bridgeEvent@{ event ->
					val targetView = generationWebView.get() ?: return@bridgeEvent
					targetView.post {
						if (
							!generationDisposed.get() &&
							generation == webViewGeneration &&
							webView === targetView
						) {
							handleReaderBridgeEvent(event)
						}
					}
				}
			)
		}
		val retireGeneration: () -> Boolean = {
			if (generationDisposed.compareAndSet(false, true)) {
				bridge.deactivate()
				true
			} else {
				false
			}
		}
		val releaseGeneration: (WebView?) -> Boolean = { requestedView ->
			retireGeneration()
			if (!generationReleased.compareAndSet(false, true)) {
				false
			} else {
				val targetView = generationWebView.getAndSet(null) ?: requestedView
				targetView?.removeJavascriptInterface(ReaderWebRuntime.AndroidBridgeName)
				if (webView === targetView) webView = null
				targetView?.destroy()
				true
			}
		}

		val runtimeStartGate = remember(generation) { ReaderEngineRuntimeStartGate() }
		fun WebView.startReaderRuntimeIfVisible() {
			if (
				generationDisposed.get() ||
				generation != webViewGeneration ||
				webView !== this ||
				!runtimeStartGate.startIfVisible(windowVisibility == View.VISIBLE)
			) return
			runtimeRecovery.onRuntimeLoadStarted()
			ReaderWebRuntime.configure(
				this,
				bridge,
				enableDebugging = settings.webContentsDebuggingEnabled == true
			)
		}

		DisposableEffect(bridge, generation) {
			onDispose {
				retireGeneration()
			}
		}

		AndroidView(
			modifier = modifier,
			factory = {
				ReaderEngineWebView(context).apply {
					webView = this
					generationWebView.set(this)
					windowVisibilityListener = { visibility ->
						val visible = visibility == View.VISIBLE
						if (runtimeRecovery.onWindowVisibilityChanged(visible)) {
							restartInterruptedReaderRuntime(
								generation = generation,
								retireGeneration = retireGeneration
							)
						} else if (visible) {
							startReaderRuntimeIfVisible()
						}
					}
					isLongClickable = false
					setOnLongClickListener {
						Logger.i(
							ReaderEngineWebViewHostTag,
							"Reader WebView native long-click suppressed; native frame owns selection actions"
						)
						true
					}
					webChromeClient = object : WebChromeClient() {
						override fun onConsoleMessage(message: ConsoleMessage): Boolean {
							val logMessage =
								"Reader console ${message.messageLevel()}: ${message.message()} " +
									"@ ${message.sourceId()}:${message.lineNumber()}"
							when (message.messageLevel()) {
								ConsoleMessage.MessageLevel.ERROR -> Logger.e(ReaderEngineWebViewHostTag, logMessage)
								ConsoleMessage.MessageLevel.WARNING -> Logger.w(ReaderEngineWebViewHostTag, logMessage)
								else -> Logger.i(ReaderEngineWebViewHostTag, logMessage)
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
							Logger.i(
								ReaderEngineWebViewHostTag,
								"Reader engine page finished: ${url?.engineUrlLabel().orEmpty()}"
							)
							view.dispatchReadyReaderCommands()
						}

						override fun onReceivedError(
							view: WebView,
							request: WebResourceRequest,
							error: WebResourceError
						) {
							Logger.e(
								ReaderEngineWebViewHostTag,
								"Reader engine WebView error main=${request.isForMainFrame} " +
									"url=${request.url.toString().engineUrlLabel()} " +
									"code=${error.errorCode} description=${error.description}"
							)
						}

						override fun onReceivedHttpError(
							view: WebView,
							request: WebResourceRequest,
							errorResponse: WebResourceResponse
						) {
							Logger.w(
								ReaderEngineWebViewHostTag,
								"Reader engine WebView HTTP error main=${request.isForMainFrame} " +
									"url=${request.url.toString().engineUrlLabel()} " +
									"status=${errorResponse.statusCode} reason=${errorResponse.reasonPhrase}"
							)
						}

						override fun onRenderProcessGone(
							view: WebView,
							detail: RenderProcessGoneDetail
						): Boolean {
							if (
								generationDisposed.get() ||
								generation != webViewGeneration ||
								webView !== view
							) return true
							Logger.e(
								ReaderEngineWebViewHostTag,
								"Reader engine WebView render process gone didCrash=${detail.didCrash()} " +
									"priorityAtExit=${detail.rendererPriorityAtExit()} " +
									"publication=${currentPublicationKey.hashCode()}"
							)
							runtimeRecovery.reset()
							readerRuntimeReady = false
							currentOnEvent(
								ReaderEngineHostEvent.FoliateBridge(
									ReaderBridgeEvent.Error(
										message = "Reader WebView renderer stopped.",
										code = "webview_render_process_gone"
									)
								)
							)
							if (retireGeneration() && generation == webViewGeneration) {
								webViewGeneration += 1
							}
							return true
						}
					}
					post { startReaderRuntimeIfVisible() }
				}
			},
			onRelease = { view ->
				view.windowVisibilityListener = null
				ReaderWebViewReleaseQueue.enqueue {
					releaseGeneration(view)
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

private fun ReaderEngineHostCommand?.toReaderBridgeCommandWithEngineNativeTapZones(): ReaderBridgeCommand? =
	when (this) {
		is ReaderEngineHostCommand.FoliateBridge -> command.withEngineNativeTapZones()
		null -> null
	}

private fun ReaderBridgeCommand.withEngineNativeTapZones(): ReaderBridgeCommand =
	when (this) {
		is ReaderBridgeCommand.OpenPublication -> copy(settings = (settings ?: ReaderSettings()).copy(nativeTapZones = true))
		is ReaderBridgeCommand.ApplySettings -> copy(settings = settings.copy(nativeTapZones = true))
		else -> this
	}

private fun ReaderBridgeCommand.engineDebugLabel(): String =
	when (this) {
		is ReaderBridgeCommand.OpenPublication ->
			"openPublication(url=${url.engineUrlLabel()}, overlay=$mediaOverlayEnabled, " +
				"tint=${if (nativeShellCoverTint.isNullOrBlank()) "missing" else "present"})"
		is ReaderBridgeCommand.GoToLocator -> "goToLocator(reason=$reason)"
		is ReaderBridgeCommand.GoToCfi -> "goToCfi"
		is ReaderBridgeCommand.GoToHref -> "goToHref(${href.engineUrlLabel()})"
		is ReaderBridgeCommand.GoToProgress -> "goToProgress(${progress.coerceIn(0.0, 1.0)})"
		is ReaderBridgeCommand.GoToChapterProgress ->
			"goToChapterProgress(${href.engineUrlLabel()}, ${progress.coerceIn(0.0, 1.0)})"
		ReaderBridgeCommand.NextPage -> "nextPage"
		ReaderBridgeCommand.PreviousPage -> "previousPage"
		is ReaderBridgeCommand.PreviewPageDrag -> "previewPageDrag(${phase.name.lowercase()})"
		is ReaderBridgeCommand.ScrollViewport -> "scrollViewport(${direction.name.lowercase()})"
		is ReaderBridgeCommand.ContentLongPressAt -> "contentLongPressAt"
		is ReaderBridgeCommand.ApplyHighlight -> "applyHighlight"
		is ReaderBridgeCommand.ApplyHighlights -> {
			val noteCount = highlights.count { it.note?.trim()?.isNotEmpty() == true }
			"applyHighlights(count=${highlights.size}, notes=$noteCount)"
		}
		is ReaderBridgeCommand.InstallRawTextProvenance -> "installRawTextProvenance"
		is ReaderBridgeCommand.ApplyOverlayFragment ->
			if (fragment.coordinateMode == ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8) {
				"applyOverlayFragment(raw)"
			} else {
				"applyOverlayFragment(${fragment.fragmentId.orEmpty()})"
			}
		is ReaderBridgeCommand.UpdateOverlayFragmentProgress ->
			if (fragment.coordinateMode == ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8) {
				"updateOverlayFragmentProgress(raw)"
			} else {
				"updateOverlayFragmentProgress(${fragment.fragmentId.orEmpty()}, ${fragment.textProgressEnd ?: "n/a"})"
			}
		is ReaderBridgeCommand.RequestVisibleTextRange -> "requestVisibleTextRange"
		ReaderBridgeCommand.ClearOverlay -> "clearOverlay"
		is ReaderBridgeCommand.ApplySettings -> "applySettings"
		is ReaderBridgeCommand.Search -> "search"
		ReaderBridgeCommand.ClearSearch -> "clearSearch"
	}

private fun ReaderBridgeEvent.engineDebugLabel(): String =
	when (this) {
		ReaderBridgeEvent.Ready -> "ready"
		is ReaderBridgeEvent.CommandAcknowledged -> "commandAck($commandId)"
		ReaderBridgeEvent.PublicationReady -> "publicationReady"
		ReaderBridgeEvent.CenterTap -> "readerCenterTap"
		is ReaderBridgeEvent.ContentTapHandled -> "contentTapHandled(${action.name.lowercase()})"
		is ReaderBridgeEvent.InternalLinkRequested ->
			"internalLink(${href?.engineUrlLabel().orEmpty()}, prevented=$prevented, source=${source.orEmpty()})"
		is ReaderBridgeEvent.ExternalLink -> "externalLink(${href?.engineUrlLabel().orEmpty()})"
		is ReaderBridgeEvent.LocationChanged ->
			"locationChanged(${locator.href?.engineUrlLabel().orEmpty()}, " +
				"reason=${locator.reason.orEmpty()}, " +
				"rangeCfi=${locator.rangeCfi?.take(80).orEmpty()})"
		is ReaderBridgeEvent.CfiChanged -> "cfiChanged"
		is ReaderBridgeEvent.TocItemChanged -> "tocItemChanged(${href?.engineUrlLabel().orEmpty()})"
		is ReaderBridgeEvent.PaginationProfileStatusChanged -> "paginationProfileStatus(${profile.status})"
		is ReaderBridgeEvent.SelectionChanged ->
			"selectionChanged(footnote=${footnote ?: false}, " +
				"pos=${posLeft ?: ""},${posTop ?: ""},${posRight ?: ""},${posBottom ?: ""})"
		ReaderBridgeEvent.SelectionCleared -> "selectionCleared()"
		is ReaderBridgeEvent.AnnotationClick -> "annotationClick(index=${index ?: ""}, value=${value?.take(80).orEmpty()})"
		is ReaderBridgeEvent.AnnotationDrawn -> "annotationDrawn(index=${index ?: ""}, value=${value?.take(80).orEmpty()})"
		is ReaderBridgeEvent.OverlayCreated -> "overlayCreated(index=${index ?: ""})"
		is ReaderBridgeEvent.LoadDoc -> "loadDoc(index=${index ?: ""}, href=${href?.engineUrlLabel().orEmpty()})"
		is ReaderBridgeEvent.FootnoteOpen -> "footnoteOpen(${href?.engineUrlLabel().orEmpty()}, type=${noteType.orEmpty()})"
		ReaderBridgeEvent.FootnoteClose -> "footnoteClose()"
		is ReaderBridgeEvent.PullUp -> "pullUp(source=${source.orEmpty()})"
		is ReaderBridgeEvent.VisibleTextRange ->
			if (rawProvenanceId != null) {
				"visibleTextRange(raw)"
			} else {
				"visibleTextRange(${textHref.engineUrlLabel()}, $visibleStart-$visibleEnd, source=${source.orEmpty()})"
			}
		is ReaderBridgeEvent.TextPoint ->
			if (rawProvenanceId != null) {
				"textPoint(raw)"
			} else {
				"textPoint(${textHref.engineUrlLabel()}, $textOffset, source=${source.orEmpty()})"
			}
		is ReaderBridgeEvent.RawTextProvenanceStatusChanged ->
			"rawTextProvenanceStatus(${status.name.lowercase()}, ${reason?.name?.lowercase().orEmpty()})"
		is ReaderBridgeEvent.OverlayFragmentActive -> "overlayFragmentActive(${fragment.fragmentId.orEmpty()})"
		is ReaderBridgeEvent.OverlayFragmentInactive -> "overlayFragmentInactive(${fragmentId.orEmpty()})"
		is ReaderBridgeEvent.SearchResults ->
			"searchResults(count=${results.size}, progress=${progress ?: ""}, complete=$complete)"
		is ReaderBridgeEvent.Toc -> "toc(count=${items.size})"
		is ReaderBridgeEvent.Error -> "error(code=${code.orEmpty()}, message=${message.take(120)})"
	}

private fun String.engineUrlLabel(): String {
	val scheme = substringBefore(":", missingDelimiterValue = "").takeIf { it.isNotBlank() }
	val tail = substringAfterLast('/').take(80)
	return when {
		scheme != null && tail.isNotBlank() -> "$scheme:$tail"
		scheme != null -> scheme
		else -> take(80)
	}
}

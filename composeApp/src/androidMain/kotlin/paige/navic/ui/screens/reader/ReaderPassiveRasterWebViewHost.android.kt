package paige.navic.ui.screens.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Presentation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.readerManagedStorageRoot
import paige.navic.util.core.Logger

private const val ReaderPassiveRasterWebViewHostTag = "ReaderPassiveRasterWebViewHost"
private const val ReaderPassiveRasterAssetDomain = "appassets.androidplatform.net"
private const val ReaderPassiveRasterAssetsPathPrefix = "/assets/"
private const val ReaderPassiveRasterAssetPath =
	"reader/passive-raster-prototype/index.html"
private const val ReaderPassiveRasterAssetUrl =
	"https://$ReaderPassiveRasterAssetDomain$ReaderPassiveRasterAssetsPathPrefix$ReaderPassiveRasterAssetPath"
private const val ReaderPassiveRasterMaximumResultPolls = 480

internal enum class ReaderPassiveRasterWebViewPreconditionFailure {
	NotReady,
	GeometryMismatch,
	UnsupportedPlatform,
	StaleVisualState,
	ViewSizeMismatch,
	WindowBounds,
	BitmapAllocation
}

internal class ReaderPassiveRasterUncertainCommitRetirement {
	private var retired = false

	fun retireBeforeCompletion(
		retireRuntime: () -> Unit,
		reportCompletion: () -> Unit
	) {
		if (retired) return
		retired = true
		retireRuntime()
		reportCompletion()
	}
}

internal data class ReaderPassiveRasterWebViewCaptureMetrics(
	val captureRequests: Int,
	val preconditionFailures: Int,
	val pixelCopyAttempts: Int,
	val pixelCopySuccesses: Int,
	val pixelCopyFailures: Int,
	val lastPixelCopyResult: Int?,
	val lastCaptureLatencyMillis: Long?,
	val maximumCaptureLatencyMillis: Long,
	val lastPreconditionFailure: ReaderPassiveRasterWebViewPreconditionFailure?,
	val lastGeometryWidthDelta: Int,
	val lastGeometryHeightDelta: Int
)

internal fun readerPassiveRasterPhysicalGeometry(
	configuredGeometry: ReaderPassiveRasterGeometry,
	measuredWidth: Int,
	measuredHeight: Int
): ReaderPassiveRasterGeometry? = if (
	measuredWidth == configuredGeometry.viewportWidth &&
	measuredHeight == configuredGeometry.viewportHeight
) {
	ReaderPassiveRasterGeometry(
		viewportWidth = measuredWidth,
		viewportHeight = measuredHeight,
		captureLeft = configuredGeometry.captureLeft,
		captureTop = configuredGeometry.captureTop,
		captureRight = configuredGeometry.captureRight,
		captureBottom = configuredGeometry.captureBottom
	)
} else {
	null
}

internal fun readerPassiveRasterCanonicalFullFrameGeometry(
	observedGeometry: ReaderPassiveRasterGeometry,
	measuredWidth: Int,
	measuredHeight: Int
): ReaderPassiveRasterGeometry? {
	if (measuredWidth <= 0 || measuredHeight <= 0) return null
	val observedIsFullFrame =
		observedGeometry.captureLeft == 0 &&
			observedGeometry.captureTop == 0 &&
			observedGeometry.captureRight == observedGeometry.viewportWidth &&
			observedGeometry.captureBottom == observedGeometry.viewportHeight
	val widthDelta = kotlin.math.abs(
		observedGeometry.viewportWidth.toLong() - measuredWidth.toLong()
	)
	val heightDelta = kotlin.math.abs(
		observedGeometry.viewportHeight.toLong() - measuredHeight.toLong()
	)
	if (!observedIsFullFrame || widthDelta > 1L || heightDelta > 1L) return null
	return ReaderPassiveRasterGeometry(
		viewportWidth = measuredWidth,
		viewportHeight = measuredHeight,
		captureLeft = 0,
		captureTop = 0,
		captureRight = measuredWidth,
		captureBottom = measuredHeight
	)
}

internal fun readerPassiveRasterCanonicalCaptureGeometry(
	configuredGeometry: ReaderPassiveRasterGeometry,
	measuredWidth: Int,
	measuredHeight: Int,
	runtimeObservedGeometry: ReaderPassiveRasterGeometry
): ReaderPassiveRasterGeometry? {
	val physicalGeometry = readerPassiveRasterPhysicalGeometry(
		configuredGeometry = configuredGeometry,
		measuredWidth = measuredWidth,
		measuredHeight = measuredHeight
	) ?: return null
	if (runtimeObservedGeometry == physicalGeometry) return runtimeObservedGeometry
	val configuredIsFullFrame =
		physicalGeometry.captureLeft == 0 &&
			physicalGeometry.captureTop == 0 &&
			physicalGeometry.captureRight == physicalGeometry.viewportWidth &&
			physicalGeometry.captureBottom == physicalGeometry.viewportHeight
	if (!configuredIsFullFrame) return null
	return readerPassiveRasterCanonicalFullFrameGeometry(
		observedGeometry = runtimeObservedGeometry,
		measuredWidth = measuredWidth,
		measuredHeight = measuredHeight
	)?.takeIf { it == physicalGeometry }
}

internal fun readerPassiveRasterCreateBitmap(
	geometry: ReaderPassiveRasterGeometry
): Bitmap? = try {
	Bitmap.createBitmap(
		geometry.captureWidth,
		geometry.captureHeight,
		Bitmap.Config.ARGB_8888
	)
} catch (_: Throwable) {
	null
}

private class ReaderPassiveRasterOffscreenWindow(
	activity: Activity,
	geometry: ReaderPassiveRasterGeometry,
	mainHandler: Handler
) : AutoCloseable {
	private val imageReader = ImageReader.newInstance(
		geometry.viewportWidth,
		geometry.viewportHeight,
		PixelFormat.RGBA_8888,
		2
	)
	private val virtualDisplay = checkNotNull(
		activity.getSystemService(DisplayManager::class.java).createVirtualDisplay(
			"Navic passive raster",
			geometry.viewportWidth,
			geometry.viewportHeight,
			activity.resources.displayMetrics.densityDpi,
			imageReader.surface,
			DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
				DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
		)
	)
	private val presentation = Presentation(activity, virtualDisplay.display)
	val container: FrameLayout
	val captureWindow: Window
	private var closed = false

	init {
		imageReader.setOnImageAvailableListener({ reader ->
			runCatching { reader.acquireLatestImage()?.close() }
		}, mainHandler)
		presentation.requestWindowFeature(Window.FEATURE_NO_TITLE)
		presentation.setCancelable(false)
		container = FrameLayout(presentation.context).apply {
			setBackgroundColor(Color.BLACK)
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
		}
		presentation.setContentView(
			container,
			ViewGroup.LayoutParams(geometry.viewportWidth, geometry.viewportHeight)
		)
		captureWindow = checkNotNull(presentation.window).apply {
			setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
			clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
			addFlags(
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
					WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
			)
			attributes = attributes.apply { windowAnimations = 0 }
		}
		presentation.show()
		captureWindow.setLayout(geometry.viewportWidth, geometry.viewportHeight)
	}

	override fun close() {
		if (closed) return
		closed = true
		container.removeAllViews()
		imageReader.setOnImageAvailableListener(null, null)
		if (presentation.isShowing) presentation.dismiss()
		virtualDisplay.release()
		imageReader.close()
	}
}

@SuppressLint("SetJavaScriptEnabled")
internal class ReaderPassiveRasterWebViewHost(
	private val activity: Activity,
	override val passiveSessionId: String,
	private val viewportGeometry: ReaderPassiveRasterGeometry,
	private val mainHandler: Handler = Handler(Looper.getMainLooper()),
	private val onRuntimeReady: () -> Unit = { },
	private val onRendererGone: () -> Unit = { }
) : ReaderPassiveRasterRuntimePort<Bitmap> {
	private class ActiveCommit(
		val generation: Long,
		val onCommitted: (ReaderPassiveRasterCaptureReceipt?) -> Unit,
		val startedAtMillis: Long = SystemClock.elapsedRealtime(),
		var operationId: String? = null,
		var cancellationRequested: Boolean = false,
		val onDrained: MutableList<() -> Unit> = mutableListOf()
	)

	private val callbackSequence = AtomicLong()
	private val offscreenWindow = ReaderPassiveRasterOffscreenWindow(
		activity,
		viewportGeometry,
		mainHandler
	)
	private val container = offscreenWindow.container
	private val assetLoader = WebViewAssetLoader.Builder()
		.setDomain(ReaderPassiveRasterAssetDomain)
		.addPathHandler(
			ReaderPassiveRasterAssetsPathPrefix,
			WebViewAssetLoader.AssetsPathHandler(activity)
		)
		.addPathHandler(
			ReaderPublicationCachePathPrefix,
			WebViewAssetLoader.InternalStoragePathHandler(
				activity,
				readerManagedStorageRoot(activity)
			)
		)
		.build()
	private var callbackGeneration = 1L
	private var activeCommit: ActiveCommit? = null
	private val uncertainCommitRetirement =
		ReaderPassiveRasterUncertainCommitRetirement()
	private var destroyed = false
	@Volatile
	private var runtimeReady = false
	private var captureRequests = 0
	private var preconditionFailures = 0
	private var pixelCopyAttempts = 0
	private var pixelCopySuccesses = 0
	private var pixelCopyFailures = 0
	private var lastPixelCopyResult: Int? = null
	private var lastCaptureLatencyMillis: Long? = null
	private var maximumCaptureLatencyMillis = 0L
	private var lastPreconditionFailure: ReaderPassiveRasterWebViewPreconditionFailure? = null
	private var lastGeometryWidthDelta = 0
	private var lastGeometryHeightDelta = 0
	private var runtimeLoadStartedAtMillis = 0L
	private val webView: WebView

	private fun diagnostic(
		phase: ReaderWebViewDiagnosticPhase,
		state: ReaderWebViewDiagnosticState,
		reason: ReaderWebViewDiagnosticReason = ReaderWebViewDiagnosticReason.None,
		startedAtMillis: Long = runtimeLoadStartedAtMillis,
		code: Int? = null,
		mainFrame: Boolean? = null,
		didCrash: Boolean? = null,
		priorityAtExit: Int? = null
	) {
		runCatching {
			Logger.i(
				ReaderPassiveRasterWebViewHostTag,
				ReaderWebViewPhaseDiagnostic.format(
					ReaderWebViewDiagnosticSource.PassiveRaster, phase, state, reason,
					SystemClock.elapsedRealtime() - startedAtMillis,
					code, mainFrame, didCrash, priorityAtExit
				)
			)
		}
	}

	override val isReady: Boolean
		get() = runtimeReady && !destroyed && webView.isAttachedToWindow

	override val isRetired: Boolean
		get() = destroyed

	init {
		require(passiveSessionId.isNotBlank())
		check(Looper.myLooper() == Looper.getMainLooper()) {
			"Passive raster WebView creation requires the main thread"
		}
		webView = WebView(container.context).apply {
			isFocusable = false
			isFocusableInTouchMode = false
			isClickable = false
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
			settings.javaScriptEnabled = true
			settings.domStorageEnabled = false
			settings.cacheMode = WebSettings.LOAD_NO_CACHE
			settings.useWideViewPort = false
			settings.loadWithOverviewMode = false
			settings.textZoom = 100
			settings.allowFileAccess = false
			settings.allowContentAccess = false
			settings.blockNetworkLoads = true
			webViewClient = object : WebViewClient() {
				override fun shouldInterceptRequest(
					view: WebView,
					request: WebResourceRequest
				): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
					?: super.shouldInterceptRequest(view, request)

				override fun onPageFinished(view: WebView, url: String?) {
					if (url != ReaderPassiveRasterAssetUrl || destroyed) return
					pollRuntimeReady(callbackGeneration, 0)
				}

				override fun onReceivedError(
					view: WebView,
					request: WebResourceRequest,
					error: WebResourceError
				) {
					diagnostic(ReaderWebViewDiagnosticPhase.RuntimeLoad, ReaderWebViewDiagnosticState.Failed,
						ReaderWebViewDiagnosticReason.WebViewError, code = error.errorCode,
						mainFrame = request.isForMainFrame)
					super.onReceivedError(view, request, error)
				}

				override fun onReceivedHttpError(
					view: WebView,
					request: WebResourceRequest,
					errorResponse: WebResourceResponse
				) {
					diagnostic(ReaderWebViewDiagnosticPhase.RuntimeLoad, ReaderWebViewDiagnosticState.Failed,
						ReaderWebViewDiagnosticReason.HttpError, code = errorResponse.statusCode,
						mainFrame = request.isForMainFrame)
					super.onReceivedHttpError(view, request, errorResponse)
				}

				override fun onRenderProcessGone(
					view: WebView,
					detail: RenderProcessGoneDetail
				): Boolean {
					retireAfterRendererLoss(ReaderWebViewDiagnosticReason.RendererGone,
						didCrash = detail.didCrash(), priorityAtExit = detail.rendererPriorityAtExit())
					return true
				}
			}
		}
		container.addView(
			webView,
			ViewGroup.LayoutParams(
				viewportGeometry.viewportWidth,
				viewportGeometry.viewportHeight
			)
		)
		runtimeLoadStartedAtMillis = SystemClock.elapsedRealtime()
		diagnostic(ReaderWebViewDiagnosticPhase.RuntimeLoad, ReaderWebViewDiagnosticState.Started)
		try {
			webView.loadUrl(ReaderPassiveRasterAssetUrl)
		} catch (failure: Throwable) {
			diagnostic(ReaderWebViewDiagnosticPhase.RuntimeLoad, ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.RuntimeLoadThrew)
			throw failure
		}
	}

	override fun commit(
		manifest: ReaderPassiveRasterCaptureManifest,
		captureTarget: String,
		passiveCommitSequence: Long,
		onCommitted: (ReaderPassiveRasterCaptureReceipt?) -> Unit
	) {
		if (!isReady || activeCommit != null) {
			diagnostic(ReaderWebViewDiagnosticPhase.Commit, ReaderWebViewDiagnosticState.Rejected,
				ReaderWebViewDiagnosticReason.CommitUnavailable, SystemClock.elapsedRealtime())
			onCommitted(null)
			return
		}
		val commit = ActiveCommit(
			generation = callbackGeneration,
			onCommitted = onCommitted
		)
		activeCommit = commit
		diagnostic(ReaderWebViewDiagnosticPhase.Commit, ReaderWebViewDiagnosticState.Started,
			startedAtMillis = commit.startedAtMillis)
		val payload = readerPassiveRasterCommitJson(
			manifest = manifest,
			captureTarget = captureTarget,
			passiveSessionId = passiveSessionId,
			passiveCommitSequence = passiveCommitSequence
		)
		try {
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicPassiveRasterPrototype?.startCapture?.($payload) ?? null)"
			) { encoded ->
				if (!commitIsCurrent(commit)) return@evaluateJavascript
				val operationId = readerPassiveRasterOperationId(encoded)
				if (operationId == null) {
					finishCommit(commit, null, ReaderWebViewDiagnosticReason.OperationIdInvalid)
					return@evaluateJavascript
				}
				commit.operationId = operationId
				if (commit.cancellationRequested) dispatchCommitCancellation(commit)
				pollCommitResult(commit, pollCount = 0)
			}
		} catch (_: Throwable) {
			finishCommit(commit, null, ReaderWebViewDiagnosticReason.JavascriptSubmissionThrew)
		}
	}

	override fun cancelActiveCommit(onDrained: () -> Unit) {
		val commit = activeCommit
		if (commit == null) {
			onDrained()
			return
		}
		commit.onDrained += onDrained
		if (commit.cancellationRequested) return
		commit.cancellationRequested = true
		if (commit.operationId != null) dispatchCommitCancellation(commit)
	}

	private fun dispatchCommitCancellation(commit: ActiveCommit) {
		if (!commitIsCurrent(commit)) return
		val operationId = commit.operationId ?: return
		val quotedOperationId = JSONObject.quote(operationId)
		try {
			webView.evaluateJavascript(
				"window.NavicPassiveRasterPrototype?.cancelOperation?.($quotedOperationId) === true"
			) { }
		} catch (_: Throwable) {
			retireAfterRendererLoss(ReaderWebViewDiagnosticReason.JavascriptSubmissionThrew)
		}
	}

	private fun pollRuntimeReady(generation: Long, pollCount: Int) {
		if (!callbackIsCurrent(generation)) return
		if (pollCount >= ReaderPassiveRasterMaximumResultPolls) {
			retireAfterRendererLoss(ReaderWebViewDiagnosticReason.RuntimeReadyPollBudget)
			return
		}
		try {
			webView.evaluateJavascript(
				"window.NavicPassiveRasterPrototype?.ready === true"
			) { encoded ->
				if (!callbackIsCurrent(generation)) return@evaluateJavascript
				if (encoded == "true") {
					if (!runtimeReady) {
						runtimeReady = true
						diagnostic(ReaderWebViewDiagnosticPhase.RuntimeLoad, ReaderWebViewDiagnosticState.Completed)
						onRuntimeReady()
					}
				} else {
					webView.postOnAnimation {
						pollRuntimeReady(generation, pollCount + 1)
					}
				}
			}
		} catch (failure: Throwable) {
			diagnostic(ReaderWebViewDiagnosticPhase.RuntimeLoad, ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.JavascriptSubmissionThrew)
			throw failure
		}
	}

	private fun pollCommitResult(commit: ActiveCommit, pollCount: Int) {
		if (!commitIsCurrent(commit)) return
		if (pollCount >= ReaderPassiveRasterMaximumResultPolls) {
			retireAfterRendererLoss(ReaderWebViewDiagnosticReason.CommitPollBudget)
			return
		}
		val operationId = commit.operationId ?: return
		val quotedOperationId = JSONObject.quote(operationId)
		try {
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicPassiveRasterPrototype?.readOperationResult?.(" +
					"$quotedOperationId, true) ?? null)"
			) { encoded ->
				if (!commitIsCurrent(commit)) return@evaluateJavascript
				when (val result = readerPassiveRasterOperationResult(encoded)) {
					ReaderPassiveRasterOperationResult.Pending -> webView.postOnAnimation {
						pollCommitResult(commit, pollCount + 1)
					}
					is ReaderPassiveRasterOperationResult.Complete -> {
						val runtimeObservedGeometry =
							readerPassiveRasterObservedGeometry(result.value)
						val captureGeometry = runtimeObservedGeometry?.let { observedGeometry ->
							readerPassiveRasterCanonicalCaptureGeometry(
								configuredGeometry = viewportGeometry,
								measuredWidth = webView.width,
								measuredHeight = webView.height,
								runtimeObservedGeometry = observedGeometry
							)
						}
						finishCommit(
							commit,
							captureGeometry?.let { geometry ->
								readerPassiveRasterReceipt(result.value, geometry)
							}
						)
					}
					ReaderPassiveRasterOperationResult.Failed ->
						finishCommit(commit, null, ReaderWebViewDiagnosticReason.OperationFailed)
				}
			}
		} catch (failure: Throwable) {
			diagnostic(ReaderWebViewDiagnosticPhase.Commit, ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.JavascriptSubmissionThrew, commit.startedAtMillis)
			throw failure
		}
	}

	private fun commitIsCurrent(commit: ActiveCommit): Boolean =
		activeCommit === commit && callbackIsCurrent(commit.generation)

	private fun finishCommit(
		commit: ActiveCommit,
		receipt: ReaderPassiveRasterCaptureReceipt?,
		reason: ReaderWebViewDiagnosticReason = if (receipt == null)
			ReaderWebViewDiagnosticReason.ReceiptRejected else ReaderWebViewDiagnosticReason.None
	) {
		if (activeCommit !== commit) return
		activeCommit = null
		diagnostic(
			ReaderWebViewDiagnosticPhase.Commit,
			when {
				commit.cancellationRequested -> ReaderWebViewDiagnosticState.Cancelled
				receipt != null -> ReaderWebViewDiagnosticState.Completed
				else -> ReaderWebViewDiagnosticState.Failed
			},
			if (commit.cancellationRequested) ReaderWebViewDiagnosticReason.Cancelled else reason,
			commit.startedAtMillis
		)
		if (commit.cancellationRequested) {
			val callbacks = commit.onDrained.toList()
			commit.onDrained.clear()
			callbacks.forEach { callback -> callback() }
		} else {
			commit.onCommitted(receipt)
		}
	}

	private fun retireActiveCommit(reason: ReaderWebViewDiagnosticReason) {
		val commit = activeCommit ?: return
		finishCommit(commit, null, reason)
	}

	fun captureMetrics(): ReaderPassiveRasterWebViewCaptureMetrics =
		ReaderPassiveRasterWebViewCaptureMetrics(
			captureRequests = captureRequests,
			preconditionFailures = preconditionFailures,
			pixelCopyAttempts = pixelCopyAttempts,
			pixelCopySuccesses = pixelCopySuccesses,
			pixelCopyFailures = pixelCopyFailures,
			lastPixelCopyResult = lastPixelCopyResult,
			lastCaptureLatencyMillis = lastCaptureLatencyMillis,
			maximumCaptureLatencyMillis = maximumCaptureLatencyMillis,
			lastPreconditionFailure = lastPreconditionFailure,
			lastGeometryWidthDelta = lastGeometryWidthDelta,
			lastGeometryHeightDelta = lastGeometryHeightDelta
		)

	override fun capture(
		geometry: ReaderPassiveRasterGeometry,
		onCaptured: (Bitmap?) -> Unit
	) {
		captureRequests = captureRequests.incrementPassiveRasterCounter()
		lastGeometryWidthDelta = geometry.viewportWidth - viewportGeometry.viewportWidth
		lastGeometryHeightDelta = geometry.viewportHeight - viewportGeometry.viewportHeight
		val preconditionFailure = when {
			!isReady -> ReaderPassiveRasterWebViewPreconditionFailure.NotReady
			geometry != viewportGeometry ->
				ReaderPassiveRasterWebViewPreconditionFailure.GeometryMismatch
			Build.VERSION.SDK_INT < Build.VERSION_CODES.O ->
				ReaderPassiveRasterWebViewPreconditionFailure.UnsupportedPlatform
			else -> null
		}
		if (preconditionFailure != null) {
			recordPreconditionFailure(preconditionFailure)
			onCaptured(null)
			return
		}
		val generation = callbackGeneration
		val requestId = callbackSequence.incrementAndGet()
		webView.postVisualStateCallback(
			requestId,
			object : WebView.VisualStateCallback() {
				override fun onComplete(requestId: Long) {
					if (!callbackIsCurrent(generation)) {
						recordPreconditionFailure(
							ReaderPassiveRasterWebViewPreconditionFailure.StaleVisualState
						)
						onCaptured(null)
						return
					}
					webView.postOnAnimation {
						webView.postOnAnimation {
							requestPixelCopy(generation, geometry, onCaptured)
						}
					}
				}
			}
		)
	}

	override fun pause() {
		if (destroyed) return
		retireActiveCommit(ReaderWebViewDiagnosticReason.Paused)
		callbackGeneration += 1L
		runtimeReady = false
		webView.onPause()
	}

	override fun resume() {
		if (destroyed) return
		callbackGeneration += 1L
		webView.onResume()
		val generation = callbackGeneration
		pollRuntimeReady(generation, 0)
	}

	override fun destroy() {
		if (destroyed) return
		retireActiveCommit(ReaderWebViewDiagnosticReason.Destroyed)
		destroyed = true
		callbackGeneration += 1L
		runtimeReady = false
		diagnostic(ReaderWebViewDiagnosticPhase.Retirement, ReaderWebViewDiagnosticState.Retired,
			ReaderWebViewDiagnosticReason.Destroyed)
		val cleanupStartedAtMillis = SystemClock.elapsedRealtime()
		diagnostic(ReaderWebViewDiagnosticPhase.Cleanup, ReaderWebViewDiagnosticState.Started,
			startedAtMillis = cleanupStartedAtMillis)
		try {
			container.removeView(webView)
			webView.stopLoading()
			webView.onPause()
			webView.removeAllViews()
			webView.destroy()
			offscreenWindow.close()
		} catch (failure: Throwable) {
			diagnostic(ReaderWebViewDiagnosticPhase.Cleanup, ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.CleanupThrew, cleanupStartedAtMillis)
			throw failure
		}
		diagnostic(ReaderWebViewDiagnosticPhase.Cleanup, ReaderWebViewDiagnosticState.Completed,
			startedAtMillis = cleanupStartedAtMillis)
	}

	private fun retireAfterRendererLoss(
		reason: ReaderWebViewDiagnosticReason,
		didCrash: Boolean? = null,
		priorityAtExit: Int? = null
	) {
		if (destroyed) return
		diagnostic(ReaderWebViewDiagnosticPhase.Retirement, ReaderWebViewDiagnosticState.Retired,
			reason, didCrash = didCrash, priorityAtExit = priorityAtExit)
		uncertainCommitRetirement.retireBeforeCompletion(
			retireRuntime = {
				destroyed = true
				callbackGeneration += 1L
				runtimeReady = false
				val cleanupStartedAtMillis = SystemClock.elapsedRealtime()
				diagnostic(ReaderWebViewDiagnosticPhase.Cleanup, ReaderWebViewDiagnosticState.Started,
					startedAtMillis = cleanupStartedAtMillis)
				val removal = runCatching { container.removeView(webView) }
				val destruction = runCatching { webView.destroy() }
				val close = runCatching { offscreenWindow.close() }
				if (removal.isFailure || destruction.isFailure || close.isFailure) {
					diagnostic(ReaderWebViewDiagnosticPhase.Cleanup, ReaderWebViewDiagnosticState.Failed,
						ReaderWebViewDiagnosticReason.CleanupThrew, cleanupStartedAtMillis)
				} else {
					diagnostic(ReaderWebViewDiagnosticPhase.Cleanup, ReaderWebViewDiagnosticState.Completed,
						startedAtMillis = cleanupStartedAtMillis)
				}
			},
			reportCompletion = {
				retireActiveCommit(reason)
				onRendererGone()
			}
		)
	}

	private fun requestPixelCopy(
		generation: Long,
		geometry: ReaderPassiveRasterGeometry,
		onCaptured: (Bitmap?) -> Unit
	) {
		if (!callbackIsCurrent(generation)) {
			recordPreconditionFailure(
				ReaderPassiveRasterWebViewPreconditionFailure.StaleVisualState
			)
			onCaptured(null)
			return
		}
		if (
			webView.width != geometry.viewportWidth ||
			webView.height != geometry.viewportHeight
		) {
			recordPreconditionFailure(
				ReaderPassiveRasterWebViewPreconditionFailure.ViewSizeMismatch
			)
			onCaptured(null)
			return
		}
		val location = IntArray(2)
		webView.getLocationInWindow(location)
		val source = Rect(
			location[0] + geometry.captureLeft,
			location[1] + geometry.captureTop,
			location[0] + geometry.captureRight,
			location[1] + geometry.captureBottom
		)
		val captureWindow = offscreenWindow.captureWindow
		val windowBounds = captureWindow.decorView.run { Rect(0, 0, width, height) }
		if (!windowBounds.contains(source)) {
			recordPreconditionFailure(
				ReaderPassiveRasterWebViewPreconditionFailure.WindowBounds
			)
			onCaptured(null)
			return
		}
		val startedAtMillis = SystemClock.elapsedRealtime()
		val bitmap = readerPassiveRasterCreateBitmap(geometry)
		if (bitmap == null) {
			recordPreconditionFailure(
				ReaderPassiveRasterWebViewPreconditionFailure.BitmapAllocation
			)
			recordCaptureLatency(startedAtMillis)
			onCaptured(null)
			return
		}
		pixelCopyAttempts = pixelCopyAttempts.incrementPassiveRasterCounter()
		diagnostic(ReaderWebViewDiagnosticPhase.PixelCopy, ReaderWebViewDiagnosticState.Started,
			startedAtMillis = startedAtMillis)
		try {
			PixelCopy.request(
				captureWindow,
				source,
				bitmap,
				{ result ->
					recordCaptureLatency(startedAtMillis)
					lastPixelCopyResult = result
					diagnostic(ReaderWebViewDiagnosticPhase.PixelCopy,
						if (result == PixelCopy.SUCCESS) ReaderWebViewDiagnosticState.Completed
						else ReaderWebViewDiagnosticState.Failed,
						ReaderWebViewDiagnosticReason.PixelCopyResult, startedAtMillis, code = result)
					if (result == PixelCopy.SUCCESS) {
						pixelCopySuccesses = pixelCopySuccesses.incrementPassiveRasterCounter()
						if (callbackIsCurrent(generation)) {
							onCaptured(bitmap)
						} else {
							recordPreconditionFailure(
								ReaderPassiveRasterWebViewPreconditionFailure.StaleVisualState
							)
							bitmap.recycle()
							onCaptured(null)
						}
					} else {
						pixelCopyFailures = pixelCopyFailures.incrementPassiveRasterCounter()
						bitmap.recycle()
						onCaptured(null)
					}
				},
				mainHandler
			)
		} catch (_: Throwable) {
			recordCaptureLatency(startedAtMillis)
			lastPixelCopyResult = null
			pixelCopyFailures = pixelCopyFailures.incrementPassiveRasterCounter()
			diagnostic(ReaderWebViewDiagnosticPhase.PixelCopy, ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.PixelCopySubmissionThrew, startedAtMillis)
			bitmap.recycle()
			onCaptured(null)
		}
	}

	private fun recordCaptureLatency(startedAtMillis: Long) {
		val latency = (SystemClock.elapsedRealtime() - startedAtMillis).coerceAtLeast(0L)
		lastCaptureLatencyMillis = latency
		maximumCaptureLatencyMillis = maxOf(maximumCaptureLatencyMillis, latency)
	}

	private fun recordPreconditionFailure(
		failure: ReaderPassiveRasterWebViewPreconditionFailure
	) {
		preconditionFailures = preconditionFailures.incrementPassiveRasterCounter()
		lastPreconditionFailure = failure
		val reason = when (failure) {
			ReaderPassiveRasterWebViewPreconditionFailure.NotReady -> ReaderWebViewDiagnosticReason.NotReady
			ReaderPassiveRasterWebViewPreconditionFailure.GeometryMismatch -> ReaderWebViewDiagnosticReason.GeometryMismatch
			ReaderPassiveRasterWebViewPreconditionFailure.UnsupportedPlatform -> ReaderWebViewDiagnosticReason.UnsupportedPlatform
			ReaderPassiveRasterWebViewPreconditionFailure.StaleVisualState -> ReaderWebViewDiagnosticReason.StaleVisualState
			ReaderPassiveRasterWebViewPreconditionFailure.ViewSizeMismatch -> ReaderWebViewDiagnosticReason.ViewSizeMismatch
			ReaderPassiveRasterWebViewPreconditionFailure.WindowBounds -> ReaderWebViewDiagnosticReason.WindowBounds
			ReaderPassiveRasterWebViewPreconditionFailure.BitmapAllocation -> ReaderWebViewDiagnosticReason.BitmapAllocation
		}
		diagnostic(ReaderWebViewDiagnosticPhase.PixelCopy, ReaderWebViewDiagnosticState.Rejected,
			reason, SystemClock.elapsedRealtime())
	}

	private fun callbackIsCurrent(generation: Long): Boolean =
		!destroyed && callbackGeneration == generation
}

private fun readerPassiveRasterCommitJson(
	manifest: ReaderPassiveRasterCaptureManifest,
	captureTarget: String,
	passiveSessionId: String,
	passiveCommitSequence: Long
): String = JSONObject().apply {
	put("manifest", manifest.toJson())
	put("captureTarget", captureTarget)
	put("passiveSessionId", passiveSessionId)
	put("passiveCommitSequence", passiveCommitSequence)
}.toString()

internal fun ReaderPassiveRasterCaptureManifest.toJson(): JSONObject = JSONObject().apply {
	put("manifestSequence", manifestSequence)
	put("captureEpoch", captureEpoch)
	put("liveFoliateSessionId", liveFoliateSessionId)
	put("publicationSessionGeneration", publicationSessionGeneration)
	if (captureBasis != null) put("captureBasis", captureBasis.toJson())
	else put("destinationCommitToken", destinationCommitToken)
	put("opaqueCaptureTarget", opaqueCaptureTarget)
	put("visualPageOrdinal", visualPageOrdinal)
	put("profileAuthority", profileAuthority.serializedValue)
	put("rasterProfileKey", rasterProfileKey)
	put("paginationFingerprint", paginationFingerprint)
	put("layoutFingerprint", layoutFingerprint)
	put("decorationFingerprint", decorationFingerprint)
	put("viewportAndCaptureGeometry", viewportAndCaptureGeometry.toJson())
	put("rasterGeneration", rasterGeneration)
}

internal fun ReaderInitialCurrentDestinationCaptureBasis.toJson(): JSONObject = JSONObject().apply {
	put("kind", kind)
	put("commandId", commandId)
	put("destinationCommitIdentity", JSONObject().apply {
		put("foliateSessionId", destinationCommitIdentity.foliateSessionId)
		put("commitSequence", destinationCommitIdentity.commitSequence)
	})
	put("resolvedLayout", JSONObject().apply {
		put("layoutGeneration", resolvedLayout.layoutGeneration)
		put("viewGeneration", resolvedLayout.viewGeneration)
		put("navigationGeneration", resolvedLayout.navigationGeneration)
		put("sectionIndex", resolvedLayout.sectionIndex)
		put("pageIndex", resolvedLayout.pageIndex)
		put("pageCount", resolvedLayout.pageCount)
		put("width", resolvedLayout.width)
		put("height", resolvedLayout.height)
	})
}

/** Strict wire metadata: numeric strings and fractional/truncated integers are not authority. */
internal fun JSONObject.readerPassiveRasterSafeInteger(key: String, minimum: Long = 0L): Long {
	val number = get(key) as? Number ?: error("Invalid numeric metadata")
	val value = number.toDouble()
	require(value.isFinite() && value >= minimum.toDouble() && value <= 9_007_199_254_740_991.0 && value == kotlin.math.floor(value))
	return number.toLong()
}

internal fun JSONObject.readerPassiveRasterSafeInt(key: String, minimum: Long = 0L): Int =
	readerPassiveRasterSafeInteger(key, minimum).also { require(it <= Int.MAX_VALUE) }.toInt()

internal fun readerInitialCurrentDestinationCaptureBasis(json: JSONObject): ReaderInitialCurrentDestinationCaptureBasis {
	require(json.get("kind") == "initial-current-destination")
	val destination = json.getJSONObject("destinationCommitIdentity")
	val layout = json.getJSONObject("resolvedLayout")
	fun index(key: String, minimum: Long = 0L): Int = layout.readerPassiveRasterSafeInteger(key, minimum).also {
		require(it <= Int.MAX_VALUE)
	}.toInt()
	fun dimension(key: String): Double = (layout.get(key) as? Number)?.toDouble()?.also {
		require(it.isFinite() && it > 0.0)
	} ?: error("Invalid geometry metadata")
	return ReaderInitialCurrentDestinationCaptureBasis(
		json.get("commandId") as String,
		paige.navic.reader.ReaderDestinationCommitIdentity(destination.get("foliateSessionId") as String,
			destination.readerPassiveRasterSafeInteger("commitSequence", 1L)),
		paige.navic.reader.ReaderFoliateResolvedLayout(
			layout.readerPassiveRasterSafeInteger("layoutGeneration", 1L),
			layout.readerPassiveRasterSafeInteger("viewGeneration", 1L),
			layout.readerPassiveRasterSafeInteger("navigationGeneration"),
			index("sectionIndex"), index("pageIndex"), index("pageCount", 1L),
			dimension("width"), dimension("height"))
	)
}

private fun ReaderPassiveRasterGeometry.toJson(): JSONObject = JSONObject().apply {
	put("viewportWidth", viewportWidth)
	put("viewportHeight", viewportHeight)
	put("captureLeft", captureLeft)
	put("captureTop", captureTop)
	put("captureRight", captureRight)
	put("captureBottom", captureBottom)
}

private sealed interface ReaderPassiveRasterOperationResult {
	data object Pending : ReaderPassiveRasterOperationResult
	data class Complete(val value: JSONObject) : ReaderPassiveRasterOperationResult
	data object Failed : ReaderPassiveRasterOperationResult
}

private fun readerPassiveRasterOperationId(encoded: String?): String? =
	readerPassiveRasterJsonObject(encoded)
		?.optString("operationId")
		?.takeIf(String::isNotBlank)

private fun readerPassiveRasterOperationResult(
	encoded: String?
): ReaderPassiveRasterOperationResult {
	val result = readerPassiveRasterJsonObject(encoded)
		?: return ReaderPassiveRasterOperationResult.Failed
	return when (result.optString("state")) {
		"pending", "cancelling" -> ReaderPassiveRasterOperationResult.Pending
		"complete" -> result.optJSONObject("value")
			?.let(ReaderPassiveRasterOperationResult::Complete)
			?: ReaderPassiveRasterOperationResult.Failed
		else -> ReaderPassiveRasterOperationResult.Failed
	}
}

private fun readerPassiveRasterJsonObject(encoded: String?): JSONObject? = runCatching {
	val jsonText = JSONTokener(encoded).nextValue() as? String ?: return null
	JSONObject(jsonText)
}.getOrNull()

private fun readerPassiveRasterObservedGeometry(
	json: JSONObject
): ReaderPassiveRasterGeometry? = runCatching {
	val geometry = json.getJSONObject("observedViewportAndCaptureGeometry")
	ReaderPassiveRasterGeometry(
		viewportWidth = geometry.getInt("viewportWidth"),
		viewportHeight = geometry.getInt("viewportHeight"),
		captureLeft = geometry.getInt("captureLeft"),
		captureTop = geometry.getInt("captureTop"),
		captureRight = geometry.getInt("captureRight"),
		captureBottom = geometry.getInt("captureBottom")
	)
}.getOrNull()

internal fun readerPassiveRasterReceipt(
	json: JSONObject,
	observedCaptureGeometry: ReaderPassiveRasterGeometry
): ReaderPassiveRasterCaptureReceipt? = runCatching {
	require(json.has("echoedCaptureBasis") != json.has("echoedDestinationCommitToken"))
	val basis = if (json.has("echoedCaptureBasis"))
		readerInitialCurrentDestinationCaptureBasis(json.getJSONObject("echoedCaptureBasis")) else null
	val token = if (basis == null) (json.get("echoedDestinationCommitToken") as String).also { require(it.isNotBlank()) } else null
	require(basis == null || basis.destinationCommitIdentity.foliateSessionId == json.get("echoedLiveFoliateSessionId"))
	ReaderPassiveRasterCaptureReceipt(
		passiveSessionId = json.getString("passiveSessionId"),
		echoedManifestSequence = json.readerPassiveRasterSafeInteger("echoedManifestSequence"),
		echoedCaptureEpoch = json.readerPassiveRasterSafeInteger("echoedCaptureEpoch"),
		echoedLiveFoliateSessionId = json.getString("echoedLiveFoliateSessionId"),
		echoedPublicationSessionGeneration =
			json.readerPassiveRasterSafeInteger("echoedPublicationSessionGeneration"),
		echoedDestinationCommitToken = token,
		echoedCaptureBasis = basis,
		observedCaptureTarget = json.getString("observedCaptureTarget"),
		observedVisualPageOrdinal = json.readerPassiveRasterSafeInt("observedVisualPageOrdinal"),
		observedRasterProfileKey = json.getString("observedRasterProfileKey"),
		observedPaginationFingerprint = json.getString("observedPaginationFingerprint"),
		observedLayoutFingerprint = json.getString("observedLayoutFingerprint"),
		observedDecorationFingerprint = json.getString("observedDecorationFingerprint"),
		observedViewportAndCaptureGeometry = observedCaptureGeometry,
		echoedRasterGeneration = json.readerPassiveRasterSafeInteger("echoedRasterGeneration"),
		passiveCommitSequence = json.readerPassiveRasterSafeInteger("passiveCommitSequence")
	)
}.getOrNull()

private fun Int.incrementPassiveRasterCounter(): Int =
	if (this == Int.MAX_VALUE) this else this + 1

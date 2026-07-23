package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import karacken.curl.PageChange
import kotlinx.coroutines.Deferred
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.ReaderPageDragPreviewPhase
import paige.navic.reader.ReaderPageGestureLifecycle
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPageOperationPolicy
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPagePointerOwnership
import paige.navic.reader.ReaderPagePointerRoute
import paige.navic.reader.ReaderPagePointerRouter
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.reader.ReaderTapZoneAction
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.readerNativeReaderSwipeAction
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.reader.readerPublicationCacheRoot
import paige.navic.reader.readerShellCoverSwipeAction
import paige.navic.reader.readerTapZonePageTurnDirectionFor
import paige.navic.reader.withReadiness
import paige.navic.util.core.Logger
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val KomikkuReaderNativeFrameHostTag = "KomikkuReaderNativeFrameHost"
private const val PageTurnPrewarmRequiredStableFrames = 2
private const val AndroidGestureDoubleTapMinTimeMillis = 40L
private val ReaderPageDiagnosticSessionIds = AtomicLong()

@Composable
actual fun KomikkuReaderNativeFrameHost(
	navigator: KomikkuReaderNavigator,
	navigationOverlayVisible: Boolean,
	chromeOverlayVisible: Boolean,
	shellCoverVisible: Boolean,
	shellCoverUrl: String?,
	shellCoverTitle: String,
	coverBackdropEnabled: Boolean,
	viewerKey: ReaderViewerKey,
	grayscaleEnabled: Boolean,
	invertedColors: Boolean,
	verticalPageDragPreview: Boolean,
	pageTurnCanvasEnabled: Boolean,
	pageTurnReadingDirection: String?,
	pageTurnBitmapQuality: String?,
	pageTurnSnapshotKey: Int,
	pageTurnContentReadyKey: String?,
	pageTurnPaginationStatus: String?,
	pageTurnVisualPageIndex: Int?,
	pageTurnVisualLocationReason: String?,
	pageTurnFoliateSessionId: String?,
	pageTurnSettlementAck: ReaderPageTurnSettlementAck?,
	pagePreparationCoverVisible: Boolean,
	pageOperationPolicy: ReaderPageOperationPolicy,
	pagePreparationRetryKey: Int,
	onPagePreparationStateChange: (ReaderPagePreparationState) -> Unit,
	onViewerAction: (KomikkuNavigationRegion) -> Unit,
	onReadableDragPreview: (deltaX: Float, deltaY: Float, viewWidth: Int, viewHeight: Int, phase: ReaderPageDragPreviewPhase) -> Unit,
	onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit,
	modifier: Modifier,
	viewerContent: @Composable () -> Unit,
	composeOverlay: @Composable () -> Unit
) {
	val currentViewerContent by rememberUpdatedState(viewerContent)
	val currentComposeOverlay by rememberUpdatedState(composeOverlay)
	val currentOnViewerAction by rememberUpdatedState(onViewerAction)
	val currentOnReadableDragPreview by rememberUpdatedState(onReadableDragPreview)
	val currentOnContentLongPress by rememberUpdatedState(onContentLongPress)
	val currentOnPagePreparationStateChange by rememberUpdatedState(onPagePreparationStateChange)

	AndroidView(
		modifier = modifier,
		factory = { context ->
			KomikkuReaderNativeFrameRoot(context).apply {
				setOnPagePreparationStateChange { state -> currentOnPagePreparationStateChange(state) }
				setPageOperationPolicy(pageOperationPolicy)
				setViewerContent(viewerKey) { currentViewerContent() }
				setComposeOverlay { currentComposeOverlay() }
				setChromeOverlayVisible(chromeOverlayVisible)
				setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle, coverBackdropEnabled)
				setViewerLayerPaint(grayscaleEnabled, invertedColors)
				setVerticalPageDragPreview(verticalPageDragPreview)
				setPageTurnBitmapQuality(pageTurnBitmapQuality)
				setPageTurnCanvasEnabled(pageTurnCanvasEnabled)
				setPageTurnReadingDirection(pageTurnReadingDirection)
				setPageTurnSnapshotKey(pageTurnSnapshotKey)
				setPageTurnContentReadyKey(pageTurnContentReadyKey)
				setPageTurnPaginationStatus(pageTurnPaginationStatus)
				pageTurnFoliateSessionId?.let { sessionId ->
					setPageTurnVisualLocation(
						pageTurnVisualPageIndex,
						pageTurnVisualLocationReason,
						sessionId,
						pageTurnSettlementAck
					)
				}
				setPagePreparationCoverVisible(pagePreparationCoverVisible)
				setPagePreparationRetryKey(pagePreparationRetryKey)
				setOnViewerAction { action -> currentOnViewerAction(action) }
				setOnReadableDragPreview { deltaX, deltaY, width, height, phase ->
					currentOnReadableDragPreview(deltaX, deltaY, width, height, phase)
				}
				setOnContentLongPress { x, y, width, height -> currentOnContentLongPress(x, y, width, height) }
			}
		},
		update = { root ->
			root.setOnPagePreparationStateChange { state -> currentOnPagePreparationStateChange(state) }
			root.setPageOperationPolicy(pageOperationPolicy)
			root.setNavigation(navigator)
			root.setNavigationOverlayVisible(navigationOverlayVisible)
			root.setChromeOverlayVisible(chromeOverlayVisible)
			root.setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle, coverBackdropEnabled)
			root.setViewerLayerPaint(grayscaleEnabled, invertedColors)
			root.setVerticalPageDragPreview(verticalPageDragPreview)
			root.setPageTurnBitmapQuality(pageTurnBitmapQuality)
			root.setPageTurnCanvasEnabled(pageTurnCanvasEnabled)
			root.setPageTurnReadingDirection(pageTurnReadingDirection)
			root.setPageTurnSnapshotKey(pageTurnSnapshotKey)
			root.setPageTurnContentReadyKey(pageTurnContentReadyKey)
			root.setPageTurnPaginationStatus(pageTurnPaginationStatus)
			pageTurnFoliateSessionId?.let { sessionId ->
				root.setPageTurnVisualLocation(
					pageTurnVisualPageIndex,
					pageTurnVisualLocationReason,
					sessionId,
					pageTurnSettlementAck
				)
			}
			root.setPagePreparationCoverVisible(pagePreparationCoverVisible)
			root.setPagePreparationRetryKey(pagePreparationRetryKey)
			root.setViewerContent(viewerKey) { currentViewerContent() }
			root.setComposeOverlay { currentComposeOverlay() }
			root.setOnViewerAction { action -> currentOnViewerAction(action) }
			root.setOnReadableDragPreview { deltaX, deltaY, width, height, phase ->
				currentOnReadableDragPreview(deltaX, deltaY, width, height, phase)
			}
			root.setOnContentLongPress { x, y, width, height -> currentOnContentLongPress(x, y, width, height) }
		},
		onRelease = { root -> root.closeReader() }
	)
}

private class KomikkuReaderNativeFrameRoot(context: Context) : FrameLayout(context) {
	private val readerContainer = FrameLayout(context)
	private val viewerContainer = KomikkuReaderNativeViewerContainer(context)
	private val shellCoverView = KomikkuReaderNativeShellCoverView(context)
	private val navigationOverlay = KomikkuReaderNativeNavigationOverlayView(context)
	private val composeOverlay = ComposeView(context)
	private var currentViewerKey: ReaderViewerKey? = null
	private var currentViewerComposeView: ComposeView? = null
	private var shellCoverVisible: Boolean = false
	private var pagePreparationCoverVisible: Boolean = false
	private var lastNativeCoverVisibilityTrace: String? = null

	init {
		setBackgroundColor(Color.rgb(32, 35, 41))

		viewerContainer.setShellCoverView(shellCoverView)
		readerContainer.addView(
			viewerContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)

		navigationOverlay.isClickable = false
		navigationOverlay.isFocusable = false
		navigationOverlay.visibility = GONE
		shellCoverView.isClickable = true
		shellCoverView.isFocusable = false
		shellCoverView.visibility = GONE
		composeOverlay.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		composeOverlay.isClickable = false
		composeOverlay.isFocusable = false
		composeOverlay.elevation = 32f
		composeOverlay.translationZ = 32f

		addView(
			readerContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			navigationOverlay,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			composeOverlay,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun setNavigation(navigator: KomikkuReaderNavigator) {
		viewerContainer.navigator = navigator
		navigationOverlay.setNavigation(navigator)
	}

	fun setNavigationOverlayVisible(visible: Boolean) {
		navigationOverlay.visibility = if (visible) VISIBLE else GONE
	}

	fun setChromeOverlayVisible(visible: Boolean) {
		viewerContainer.chromeOverlayVisible = visible
	}

	fun setShellCover(visible: Boolean, coverUrl: String?, title: String, coverBackdropEnabled: Boolean) {
		shellCoverView.setShellCover(coverUrl = coverUrl, title = title, coverBackdropEnabled = coverBackdropEnabled)
		shellCoverVisible = visible
		viewerContainer.setShellCoverVisible(visible)
		updateNativeCoverVisibility()
	}

	fun setPagePreparationCoverVisible(visible: Boolean) {
		if (pagePreparationCoverVisible == visible) return
		pagePreparationCoverVisible = visible
		updateNativeCoverVisibility()
	}

	private fun updateNativeCoverVisibility() {
		val visible = shellCoverVisible || pagePreparationCoverVisible
		shellCoverView.visibility = if (visible) VISIBLE else GONE
		val trace =
			"visible=$visible shell=$shellCoverVisible preparation=$pagePreparationCoverVisible"
		if (lastNativeCoverVisibilityTrace != trace) {
			lastNativeCoverVisibilityTrace = trace
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader native cover visibility $trace"
			)
		}
	}

	fun setPageOperationPolicy(policy: ReaderPageOperationPolicy) {
		viewerContainer.setPageOperationPolicy(policy)
	}

	fun setPagePreparationRetryKey(retryKey: Int) {
		viewerContainer.setPagePreparationRetryKey(retryKey)
	}

	fun setOnPagePreparationStateChange(onChange: (ReaderPagePreparationState) -> Unit) {
		viewerContainer.onPagePreparationStateChange = onChange
	}

	fun setViewerLayerPaint(grayscaleEnabled: Boolean, invertedColors: Boolean) {
		val paint = if (grayscaleEnabled || invertedColors) {
			getCombinedReaderLayerPaint(grayscale = grayscaleEnabled, invertedColors = invertedColors)
		} else {
			null
		}
		viewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
		composeOverlay.bringToFront()
	}

	fun setComposeOverlay(content: @Composable () -> Unit) {
		composeOverlay.setContent(content)
		composeOverlay.bringToFront()
	}

	fun setOnViewerAction(onAction: (KomikkuNavigationRegion) -> Unit) {
		viewerContainer.onAction = onAction
	}

	fun setVerticalPageDragPreview(verticalPageDragPreview: Boolean) {
		viewerContainer.setVerticalPageDragPreview(verticalPageDragPreview)
	}

	fun setPageTurnCanvasEnabled(enabled: Boolean) {
		viewerContainer.setPageTurnCanvasEnabled(enabled)
	}

	fun setPageTurnReadingDirection(direction: String?) {
		viewerContainer.setPageTurnReadingDirection(direction)
	}

	fun setPageTurnBitmapQuality(value: String?) {
		viewerContainer.setPageTurnBitmapQuality(value)
	}

	fun setPageTurnSnapshotKey(snapshotKey: Int) {
		viewerContainer.setPageTurnSnapshotKey(snapshotKey)
	}

	fun setPageTurnContentReadyKey(contentReadyKey: String?) {
		viewerContainer.setPageTurnContentReadyKey(contentReadyKey)
	}

	fun setPageTurnPaginationStatus(status: String?) {
		viewerContainer.setPageTurnPaginationStatus(status)
	}

	fun setPageTurnVisualLocation(
		pageIndex: Int?,
		reason: String?,
		foliateSessionId: String,
		acknowledgement: ReaderPageTurnSettlementAck?
	) {
		viewerContainer.setPageTurnVisualLocation(
			pageIndex,
			reason,
			foliateSessionId,
			acknowledgement
		)
	}

	fun setOnReadableDragPreview(
		onReadableDragPreview: (
			deltaX: Float,
			deltaY: Float,
			viewWidth: Int,
			viewHeight: Int,
			phase: ReaderPageDragPreviewPhase
		) -> Unit
	) {
		viewerContainer.onReadableDragPreview = onReadableDragPreview
	}

	fun setOnContentLongPress(onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit) {
		viewerContainer.onContentLongPress = onContentLongPress
	}

	fun setViewerContent(viewerKey: ReaderViewerKey, content: @Composable () -> Unit) {
		if (currentViewerKey != viewerKey || currentViewerComposeView == null) {
			currentViewerComposeView?.disposeComposition()
			currentViewerComposeView = ComposeView(context).also { viewerView ->
				viewerView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
				viewerView.setContent(content)
				viewerContainer.replaceViewerContent(viewerView)
			}
			currentViewerKey = viewerKey
		} else {
			currentViewerComposeView?.setContent(content)
		}
	}

	fun closeReader() {
		viewerContainer.closeReader()
		currentViewerComposeView?.disposeComposition()
		composeOverlay.disposeComposition()
		currentViewerComposeView = null
		currentViewerKey = null
	}

	override fun onDetachedFromWindow() {
		currentViewerComposeView?.disposeComposition()
		composeOverlay.disposeComposition()
		currentViewerComposeView = null
		currentViewerKey = null
		super.onDetachedFromWindow()
	}
}

private class KomikkuReaderNativeShellCoverView(context: Context) : View(context) {
	private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val backdropImagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
		alpha = 124
		colorFilter = ColorMatrixColorFilter(
			ColorMatrix().apply {
				setSaturation(0.78f)
			}
		)
	}
	private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(142, 6, 5, 4)
	}
	private val source = Rect()
	private val destination = RectF()
	private val backdropDestination = RectF()
	private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		textSize = 42f
	}
	private var coverUrl: String? = null
	private var title: String = ""
	private var coverBackdropEnabled: Boolean = true
	private var bitmap: Bitmap? = null

	fun setShellCover(coverUrl: String?, title: String, coverBackdropEnabled: Boolean) {
		if (this.coverUrl == coverUrl && this.title == title && this.coverBackdropEnabled == coverBackdropEnabled) return
		this.coverUrl = coverUrl
		this.title = title
		this.coverBackdropEnabled = coverBackdropEnabled
		bitmap = coverUrl
			?.let { context.readerShellCoverFileFor(it) }
			?.absolutePath
			?.let { path -> BitmapFactory.decodeFile(path) }
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawColor(Color.rgb(16, 14, 10))
		val currentBitmap = bitmap
		if (currentBitmap == null || currentBitmap.width <= 0 || currentBitmap.height <= 0) {
			val shellGeometry = resolveNativeReaderShellCoverGeometry(
				viewWidth = width,
				viewHeight = height,
				bitmapWidth = 720,
				bitmapHeight = 1000
			)
			canvas.drawText(title.ifBlank { "Cover" }, width / 2f, height / 2f, titlePaint)
			return
		}
		val shellGeometry = resolveNativeReaderShellCoverGeometry(
			viewWidth = width,
			viewHeight = height,
			bitmapWidth = currentBitmap.width,
			bitmapHeight = currentBitmap.height
		)
		if (coverBackdropEnabled) {
			drawDiffuseCoverBackdrop(canvas, currentBitmap, shellGeometry)
		}
		drawContainedNativeShellCover(canvas, currentBitmap, shellGeometry)
	}

	private fun drawDiffuseCoverBackdrop(
		canvas: Canvas,
		currentBitmap: Bitmap,
		shellGeometry: NativeReaderShellCoverGeometry
	) {
		val backdropRect = shellGeometry.backdropRect
		val scale = max(
			backdropRect.width() / currentBitmap.width.toFloat(),
			backdropRect.height() / currentBitmap.height.toFloat()
		)
		val drawWidth = currentBitmap.width * scale
		val drawHeight = currentBitmap.height * scale
		val left = backdropRect.centerX() - drawWidth / 2f
		val top = backdropRect.centerY() - drawHeight / 2f
		backdropDestination.set(left, top, left + drawWidth, top + drawHeight)
		source.set(0, 0, currentBitmap.width, currentBitmap.height)
		val checkpoint = canvas.save()
		canvas.clipRect(backdropRect)
		canvas.drawBitmap(currentBitmap, source, backdropDestination, backdropImagePaint)
		canvas.restoreToCount(checkpoint)
		canvas.drawRect(backdropRect, dimPaint)
	}

	private fun drawContainedNativeShellCover(
		canvas: Canvas,
		currentBitmap: Bitmap,
		shellGeometry: NativeReaderShellCoverGeometry
	) {
		destination.set(shellGeometry.foregroundImageRect)
		canvas.drawBitmap(currentBitmap, null, destination, imagePaint)
	}
}

private data class NativeReaderShellCoverGeometry(
	val backdropRect: RectF,
	val foregroundRect: RectF,
	val foregroundImageRect: RectF
)

private fun resolveNativeReaderShellCoverGeometry(
	viewWidth: Int,
	viewHeight: Int,
	bitmapWidth: Int,
	bitmapHeight: Int
): NativeReaderShellCoverGeometry {
	val resolvedWidth = max(1, viewWidth).toFloat()
	val resolvedHeight = max(1, viewHeight).toFloat()
	val foregroundRect = nativeShellCoverForegroundRect(
		viewWidth = resolvedWidth,
		viewHeight = resolvedHeight,
		bitmapWidth = max(1, bitmapWidth).toFloat(),
		bitmapHeight = max(1, bitmapHeight).toFloat()
	)
	return NativeReaderShellCoverGeometry(
		backdropRect = RectF(0f, 0f, resolvedWidth, resolvedHeight),
		foregroundRect = foregroundRect,
		foregroundImageRect = foregroundRect
	)
}

private fun nativeShellCoverForegroundRect(
	viewWidth: Float,
	viewHeight: Float,
	bitmapWidth: Float,
	bitmapHeight: Float
): RectF {
	val landscape = viewWidth > viewHeight
	val maxWidth = if (landscape) viewWidth * 0.38f else viewWidth * 0.72f
	val maxHeight = if (landscape) viewHeight * 0.86f else viewHeight * 0.78f
	val scale = min(
		maxWidth / bitmapWidth,
		maxHeight / bitmapHeight
	)
	val drawWidth = bitmapWidth * scale
	val drawHeight = bitmapHeight * scale
	val left = (viewWidth - drawWidth) / 2f
	val top = (viewHeight - drawHeight) / 2f
	return RectF(left, top, left + drawWidth, top + drawHeight)
}

private fun getCombinedReaderLayerPaint(grayscale: Boolean, invertedColors: Boolean): Paint =
	Paint().apply {
		colorFilter = ColorMatrixColorFilter(
			ColorMatrix().apply {
				if (grayscale) {
					setSaturation(0f)
				}
				if (invertedColors) {
					postConcat(
						ColorMatrix(
							floatArrayOf(
								-1f, 0f, 0f, 0f, 255f,
								0f, -1f, 0f, 0f, 255f,
								0f, 0f, -1f, 0f, 255f,
								0f, 0f, 0f, 1f, 0f
							)
						)
					)
				}
			}
		)
	}

private fun Context.readerShellCoverFileFor(coverUrl: String): File? {
	val expectedPrefix = "${ReaderWebRuntime.AssetLoaderOrigin}$ReaderPublicationCachePathPrefix"
	if (!coverUrl.startsWith(expectedPrefix)) return null
	val relativePath = coverUrl
		.removePrefix(expectedPrefix)
		.substringBefore("?")
		.substringBefore("#")
		.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
	val file = File(readerPublicationCacheRoot(this), relativePath)
	return file.takeIf { it.isFile }
}

private data class ReaderPageGestureDiagnosticContext(
	val startedAtMillis: Long,
	val downX: Float,
	var owner: ReaderPagePointerOwnership,
	var physicalDirection: ReaderPagePhysicalDirection? = null,
	var logicalDirection: ReaderPageTurnDirection? = null
)

private enum class ReaderPagePhysicalDispatchMode {
	Legacy,
	PlayLikeCurl
}

private class KomikkuReaderNativeViewerContainer(context: Context) : FrameLayout(context) {
	private val viewerContentContainer = FrameLayout(context)
	private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
	private val readablePageDragSlopPx = ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat()
	private val shellCoverNavigator = KomikkuReaderNavigator(KomikkuRightAndLeftNavigation())
	var navigator: KomikkuReaderNavigator = KomikkuReaderNavigator(KomikkuDisabledNavigation())
	var onAction: (KomikkuNavigationRegion) -> Unit = {}
	private var verticalPageDragPreview: Boolean = false
	var chromeOverlayVisible: Boolean = false
	var onReadableDragPreview: (
		deltaX: Float,
		deltaY: Float,
		viewWidth: Int,
		viewHeight: Int,
		phase: ReaderPageDragPreviewPhase
	) -> Unit = { _, _, _, _, _ -> }
	var onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit = { _, _, _, _ -> }
	var onPagePreparationStateChange: (ReaderPagePreparationState) -> Unit = {}
	private var shellCoverView: View? = null
	private var swipeStartX: Float = 0f
	private var swipeStartY: Float = 0f
	private var horizontalSwipeDispatched: Boolean = false
	private var shellCoverDragDiagnosticLogged: Boolean = false
	private var nativeDragPreviewDiagnosticLogged: Boolean = false
	private var nativeTapCandidate: Boolean = false
	private var nativeTapCancelledByDrag: Boolean = false
	private var nativeTapLongConfirmed: Boolean = false
	private var nativeSwipeIntercepted: Boolean = false
	private var playLikeCurlGestureOwned: Boolean = false
	private var retainedContentDown: MotionEvent? = null
	private var physicalDispatchMode: ReaderPagePhysicalDispatchMode? = null
	private var pageTurnCanvasEnabled: Boolean = false
	private var pageTurnReadingDirection: String? = null
	private var pageTurnBitmapQuality = ReaderPageBitmapQuality.Balanced
	private var pageTurnSnapshotKey: Int = Int.MIN_VALUE
	private var pageTurnContentReadyKey: String? = null
	private var pageTurnPaginationStatus: String? = null
	private var pageTurnVisualPageIndex: Int? = null
	private var pageTurnVisualLocationReason: String? = null
	private var pageTurnFoliateSessionId: String? = null
	private var pageTurnSettlementAck: ReaderPageTurnSettlementAck? = null
	private var shellCoverVisible: Boolean = false
	private var pageOperationPolicy = readerPageOperationPolicy(ReaderPageReadinessState())
	private var pagePreparationRetryKey: Int = Int.MIN_VALUE
	private var pageTurnPrewarmLayoutListener: ViewTreeObserver.OnPreDrawListener? = null
	private var pageTurnPrewarmLayoutSignature: ReaderPageLayoutSignature? = null
	private var pageTurnPrewarmStableFrameCount: Int = 0
	private var rasterProfileEpoch: Long? = null
	private var latestRasterPreparationState = ReaderPagePreparationState()
	private var latestRendererReadinessState = ReaderPageRendererReadinessState()
	private val ownershipMainHandler = Handler(Looper.getMainLooper())
	private val ownershipRetryPosted = AtomicBoolean()
	private val readerDiagnosticSession = ReaderPageDiagnosticSessionIds.incrementAndGet()
	private val readerRuntimeDiagnostics = ReaderPageRuntimeDiagnostics(
		readerSession = readerDiagnosticSession,
		emit = { message -> Logger.i(KomikkuReaderNativeFrameHostTag, message) }
	)
	private val gestureDiagnostics = mutableMapOf<Long, ReaderPageGestureDiagnosticContext>()
	private val pendingOwnershipDiagnosticPhases = linkedSetOf<ReaderPageOwnershipPhase>()
	private var ownershipDiagnosticInFlight = false
	private var ownershipDiagnosticRetryPending = false
	private var coldOwnershipAdmitted = false
	private val applicationOwnershipEpoch = ReaderPageApplicationOwnershipEpoch {
		scheduleApplicationOwnershipRetry()
	}
	private val pageTurnBundleSource = ReaderPageTurnBundleSource(
		diagnostics = readerRuntimeDiagnostics,
		onOwnershipMutated = applicationOwnershipEpoch::ownerMutationCommitted
	)
	private val pagePointerRouter = ReaderPagePointerRouter(
		lifecycle = ReaderPageGestureLifecycle(),
		onStarted = { gestureId, downX, _ ->
			check(
				gestureDiagnostics.put(
					gestureId,
					ReaderPageGestureDiagnosticContext(
						startedAtMillis = SystemClock.uptimeMillis(),
						downX = downX,
						owner = ReaderPagePointerOwnership.Pending
					)
				) == null
			) { "Gesture diagnostics already own gesture $gestureId" }
		},
		publishTerminal = { gestureId, outcome ->
			emitGestureDiagnostic(gestureId, outcome)
		}
	)
	private val pageInputSettlementHostController: ReaderPageInputSettlementHostController =
		ReaderPageInputSettlementHostController(
		initialPolicy = pageOperationPolicy,
		pointerRouter = pagePointerRouter,
		cancellationPort = object : ReaderPageHostCancellationPort {
			override fun cancelForPointerInterruption(gestureId: Long) {
				dispatchContentCancel()
				if (playLikeCurlGestureOwned) {
					playLikeCurlController.cancelGesture(gestureId)
				} else {
					cancelReadableViewerDragPreview()
				}
				playLikeCurlGestureOwned = false
				recycleRetainedContentDown()
				clearPlayLikeCurlPointerTapFlagsAfterUp()
			}

			override fun clearCompletedPointerOwnership(gestureId: Long) {
				playLikeCurlGestureOwned = false
				recycleRetainedContentDown()
				clearPlayLikeCurlPointerTapFlagsAfterUp()
			}

			override fun cancelActiveRendererGesture(reason: ReaderPageLifecycleCancellationReason) {
				playLikeCurlController.cancelActiveGesture(reason)
				playLikeCurlGestureOwned = false
			}

			override fun cancelReadableViewerDragPreview(reason: ReaderPageLifecycleCancellationReason) {
				this@KomikkuReaderNativeViewerContainer.cancelReadableViewerDragPreview()
			}

			override fun clearNativeTapState(reason: ReaderPageLifecycleCancellationReason) {
				clearPlayLikeCurlNativeTapState(reason)
			}

			override fun clearSwipeTouchState(reason: ReaderPageLifecycleCancellationReason) {
				this@KomikkuReaderNativeViewerContainer.clearSwipeTouchState()
			}
		},
		publishLifecycleCancellation = { gestureId, reason ->
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				ReaderPageDiagnostic.lifecycleCancellation(
					readerDiagnosticSession,
					gestureId,
					reason
				)
			)
		}
	)
	private val playLikeCurlController: ReaderPlayLikeCurlFoliateController =
		ReaderPlayLikeCurlFoliateController(
		host = this,
		webViewProvider = { viewerContentContainer.findDescendantWebView() },
		bundleSource = pageTurnBundleSource,
		diagnostics = readerRuntimeDiagnostics,
		onRequestPrewarm = ::requestPageTurnPrewarmWhenReady,
		onRequestRasterRepair = ::requestPageRasterRepair,
		onGestureTerminal = { gestureId, outcome, detail ->
			completePageGesture(gestureId, outcome, detail)
		},
		onRasterProfileEpochChanged = ::onRasterProfileEpochChanged,
		onPreparedActiveDeckChanged = { deck ->
			pageRasterPreparationController.onPreparedActiveDeckChanged(deck)
		},
		onPaginationReadinessChanged = { readiness ->
			pageRasterHostEventController.paginationReadinessChanged(readiness)
		},
		onProfileBootstrapFailed = {
			removePageTurnPrewarmLayoutListener()
			pageRasterPreparationController.onProfileBootstrapFailed()
		},
		onReadinessStateChange = ::onRendererReadinessChanged,
		onUnsafeLifecycleEvent = { event ->
			require(
				event == ReaderPageHostLifecycleEvent.UnsafeContextLost ||
					event == ReaderPageHostLifecycleEvent.GlFailed
			)
			dispatchPageHostLifecycleEvent(event)
		},
		onOwnershipMutated = applicationOwnershipEpoch::ownerMutationCommitted,
		onOwnershipAvailabilityEdge = ::retryOwnershipAdmission,
		onOwnershipDiagnosticRequested = ::requestOwnershipDiagnostic
	)
	private val ownershipProbe = ReaderPageOwnershipProbe(
		applicationSnapshot = ::captureApplicationOwnershipSnapshot,
		rendererHost = playLikeCurlController
	)
	private val coldOwnershipAdmission = ReaderPageColdOwnershipAdmission(
		ownershipProbe = ownershipProbe,
		rendererHost = playLikeCurlController,
		acceptsColdBaseline = { snapshot ->
			emitOwnershipDiagnostic(
				ReaderPageOwnershipPhase.ColdStartBaseline,
				snapshot
			)
			snapshot.withinBounds() && snapshot.isClosedBaseline()
		},
		onUnavailable = { reason ->
			emitOwnershipUnavailable(
				ReaderPageOwnershipPhase.ColdStartBaseline,
				reason
			)
		},
		onAdmitted = {
			coldOwnershipAdmitted = true
			requestPageTurnPrewarmWhenReady()
		},
		onCallbackCapacityAvailable = ::retryOwnershipDiagnostics
	)
	private val tapTurnController = ReaderPageTapTurnControllerFacade(
		port = playLikeCurlController,
		publishTerminal = ::completePageGesture
	)
	private val pageRasterPreparationController: ReaderPageRasterPreparationController =
		ReaderPageRasterPreparationController(
		host = this,
		webViewProvider = { viewerContentContainer.findDescendantWebView() },
		bundleSource = pageTurnBundleSource,
		diagnostics = readerRuntimeDiagnostics,
		closeRendererAndAdapter = {
			playLikeCurlController.destroyAndJoin()
		},
		onRequestPrewarm = ::requestPageTurnPrewarmWhenReady,
		canStartPreparation = { coldOwnershipAdmitted },
		onAwaitHostEvent = { reason ->
			if (reason == ReaderPageRasterDeferralReason.LayoutUnstable) {
				pageRasterHostEventController.layoutStabilityInvalidated()
			}
			if (
				reason == ReaderPageRasterDeferralReason.LayoutUnstable ||
				reason == ReaderPageRasterDeferralReason.WebViewDetached
			) {
				requestPageTurnPrewarmWhenReady()
			}
		},
		onPreparationStateChange = { state ->
			latestRasterPreparationState = state
			playLikeCurlController.onPreparationStateChanged(state)
			publishMergedPagePreparationState()
		}
	)
	private val pageRasterHostEventController: ReaderPageRasterHostEventController =
		ReaderPageRasterHostEventController(
			onRetryEvent = pageRasterPreparationController::onRetryEvent,
			cancelAllDeferredRetries = pageRasterPreparationController::cancelAllDeferredRetries,
			onWebViewAttachmentChanged = { attached ->
				pageRasterPreparationController.onWebViewAttachmentChanged(attached)
				playLikeCurlController.onWebViewAttachmentChanged(attached)
			}
		)

	private fun scheduleApplicationOwnershipRetry() {
		if (!ownershipRetryPosted.compareAndSet(false, true)) return
		val accepted = ownershipMainHandler.post {
			ownershipRetryPosted.set(false)
			retryOwnershipAdmission()
			retryOwnershipDiagnostics()
		}
		if (!accepted) ownershipRetryPosted.set(false)
	}

	private fun retryOwnershipAdmission() {
		if (
			task4ResourceTeardownStarted ||
			!pageTurnCanvasEnabled ||
			!isAttachedToWindow
		) return
		coldOwnershipAdmission.retryOnOwnershipEdge()
	}

	private fun requestOwnershipDiagnostic(phase: ReaderPageOwnershipPhase) {
		if (
			task4ResourceTeardownStarted ||
			phase == ReaderPageOwnershipPhase.ColdStartBaseline ||
			phase == ReaderPageOwnershipPhase.AfterClose
		) return
		pendingOwnershipDiagnosticPhases += phase
		retryOwnershipDiagnostics()
	}

	private fun retryOwnershipDiagnostics() {
		if (task4ResourceTeardownStarted || pendingOwnershipDiagnosticPhases.isEmpty()) {
			return
		}
		if (ownershipDiagnosticInFlight) {
			ownershipDiagnosticRetryPending = true
			return
		}
		val phase = pendingOwnershipDiagnosticPhases.first()
		ownershipDiagnosticInFlight = true
		ownershipDiagnosticRetryPending = false
		ownershipProbe.request { result ->
			ownershipDiagnosticInFlight = false
			result.fold(
				onSuccess = { snapshot ->
					pendingOwnershipDiagnosticPhases.remove(phase)
					emitOwnershipDiagnostic(phase, snapshot)
				},
				onFailure = { unavailable ->
					emitOwnershipUnavailable(
						phase,
						(unavailable as ReaderPageOwnershipUnavailableException).reason
					)
				}
			)
			if (
				ownershipDiagnosticRetryPending ||
					(result.isSuccess && pendingOwnershipDiagnosticPhases.isNotEmpty())
			) {
				ownershipDiagnosticRetryPending = false
				retryOwnershipDiagnostics()
			}
		}
	}

	private fun captureApplicationOwnershipSnapshot():
		ReaderPageApplicationOwnershipSnapshot? =
		applicationOwnershipEpoch.captureStable { ownershipEpoch ->
			val controller = playLikeCurlController.applicationOwnershipMetrics()
			val residency = controller.rasterResidency
			val bundle = pageTurnBundleSource.ownershipMetrics()
			val cache = bundle.rasterCache
			val relocation = controller.relocation
			check(residency.pendingValueReleases <= residency.uniqueDecodedBitmaps)
			check(cache.pendingDecodedReleases <= cache.uniqueDecodedBitmaps)
			check(cache.activeEncodePins >= cache.encodePinnedIdentities)
			check(relocation.occupied == relocation.reserved + relocation.queued)
			ReaderPageApplicationOwnershipSnapshot(
				ownershipEpoch = ownershipEpoch,
				adapterResidents = residency.residentEntries,
				adapterResidentLimit = residency.residentEntryLimit,
				adapterDecodedBitmaps = residency.uniqueDecodedBitmaps,
				adapterDecodedBitmapLimit = residency.uniqueDecodedBitmapLimit,
				cacheDecodedBitmaps = cache.uniqueDecodedBitmaps,
				cacheDecodedBitmapLimit = cache.uniqueDecodedBitmapLimit,
				stagedPublications = bundle.stagedPublications,
				stagedPublicationLimit = bundle.stagedPublicationLimit,
				pendingCallbacks =
					bundle.pendingPublicationCallbacks +
						controller.pendingVisualCallbacks,
				pendingCallbackLimit =
					bundle.pendingPublicationCallbackLimit +
						controller.pendingVisualCallbackLimit,
				relocationReservations = relocation.reserved,
				queuedRelocations = relocation.queued,
				relocationTokens = relocation.occupied,
				relocationTokenLimit = relocation.capacity
			)
		}

	private fun emitOwnershipDiagnostic(
		phase: ReaderPageOwnershipPhase,
		snapshot: ReaderPageOwnershipSnapshot
	) {
		val cacheMetrics = pageTurnBundleSource.rasterCacheMetrics()
		if (phase == ReaderPageOwnershipPhase.AfterClose) {
			check(cacheMetrics.activeEncodePins == 0)
			check(cacheMetrics.encodePinnedIdentities == 0)
		}
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.ownership(readerDiagnosticSession, phase, snapshot)
		)
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.residency(
				readerDiagnosticSession,
				playLikeCurlController.rasterResidencyMetrics()
			)
		)
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.rasterCache(
				readerDiagnosticSession,
				phase,
				cacheMetrics
			)
		)
	}

	private fun emitOwnershipUnavailable(
		phase: ReaderPageOwnershipPhase,
		reason: ReaderPageOwnershipUnavailableReason
	) {
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.ownershipUnavailable(
				readerDiagnosticSession,
				phase,
				reason
			)
		)
	}

	private fun onRasterProfileEpochChanged(epoch: Long?) {
		rasterProfileEpoch = epoch
		pageRasterPreparationController.onRasterProfileEpochChanged(epoch)
		if (epoch == null && !task4ResourceTeardownStarted) {
			removePageTurnPrewarmLayoutListener()
			requestPageTurnPrewarmWhenReady()
		}
	}

	private fun requestPageRasterRepair(
		pageIndex: Int,
		onComplete: (ReaderPageRasterRepairResult) -> Unit
	) {
		pageRasterPreparationController.repairRasterPage(pageIndex, onComplete)
	}

	private fun onRendererReadinessChanged(state: ReaderPageRendererReadinessState) {
		latestRendererReadinessState = state
		publishMergedPagePreparationState()
	}

	private fun publishMergedPagePreparationState() {
		val raster = latestRasterPreparationState
		val renderer = latestRendererReadinessState
		val merged = raster.withReadiness(
			raster.readiness.copy(
				textureDeck = renderer.textureDeck,
				pendingTextureDeck = renderer.pendingTextureDeck,
				interaction = renderer.interaction
			)
		)
		setPageOperationPolicy(merged.operationPolicy)
		onPagePreparationStateChange(merged)
	}

	init {
		descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
		addView(
			viewerContentContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			playLikeCurlController.surfaceView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun setShellCoverView(shellCoverView: View) {
		this.shellCoverView = shellCoverView
		(shellCoverView.parent as? ViewGroup)?.removeView(shellCoverView)
		addView(
			shellCoverView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun replaceViewerContent(viewerView: View) {
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.RendererReplaced
		)
		playLikeCurlController.invalidate("viewer-replaced")
		pageRasterPreparationController.invalidate(
			reason = "viewer-replaced",
			clearVisualPageIndex = true
		)
		removePageTurnPrewarmLayoutListener()
		pageRasterHostEventController.webViewAttachmentChanged(false)
		viewerContentContainer.removeAllViews()
		viewerContentContainer.addView(
			viewerView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		pageRasterHostEventController.webViewAttachmentChanged(
			viewerContentContainer.findDescendantWebView()?.isAttachedToWindow == true
		)
		requestPageTurnPrewarmWhenReady()
	}

	fun setVerticalPageDragPreview(value: Boolean) {
		if (verticalPageDragPreview == value) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ReaderSettingsChanged
		)
		verticalPageDragPreview = value
	}

	fun setPageTurnCanvasEnabled(enabled: Boolean) {
		val supported = enabled && pageTurnBundleSource.isAvailable
		if (pageTurnCanvasEnabled == supported) return
		if (!supported) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.CanvasDisabled
			)
		}
		pageTurnCanvasEnabled = supported
		playLikeCurlController.setEnabled(supported)
		if (supported) {
			requestPageTurnPrewarmWhenReady()
		} else {
			removePageTurnPrewarmLayoutListener()
			pageRasterPreparationController.invalidate("canvas-disabled")
		}
	}

	fun setPageTurnReadingDirection(direction: String?) {
		val normalized = direction?.trim()?.lowercase()
		if (pageTurnReadingDirection == normalized) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ReaderSettingsChanged
		)
		pageTurnReadingDirection = normalized
		playLikeCurlController.invalidate(
			reason = "reading-direction",
			profileRegeneration = true
		)
		pageRasterPreparationController.invalidate("reading-direction")
		requestPageTurnPrewarmWhenReady()
	}

	fun setPageTurnBitmapQuality(value: String?) {
		val normalized = normalizeReaderPageBitmapQuality(value)
		if (pageTurnBitmapQuality == normalized) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ReaderSettingsChanged
		)
		pageTurnBitmapQuality = normalized
		playLikeCurlController.updateBitmapQuality(normalized.persistedValue)
		pageRasterPreparationController.updateBitmapQuality(normalized.persistedValue)
	}

	fun setPageTurnSnapshotKey(snapshotKey: Int) {
		if (pageTurnSnapshotKey == snapshotKey) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ReaderSettingsChanged
		)
		pageTurnSnapshotKey = snapshotKey
		playLikeCurlController.setSnapshotKey(snapshotKey)
		pageRasterPreparationController.invalidate("settings-changed")
		requestPageTurnPrewarmWhenReady()
	}

	fun setPageTurnContentReadyKey(contentReadyKey: String?) {
		if (pageTurnContentReadyKey == contentReadyKey) return
		pageTurnContentReadyKey = contentReadyKey
		pageRasterHostEventController.contentReadyKeyChanged(contentReadyKey)
		if (contentReadyKey == null) return
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Page-turn pagination content ready key=${contentReadyKey.hashCode()}"
		)
		playLikeCurlController.onHostContentReady()
	}

	fun setPageTurnPaginationStatus(status: String?) {
		if (pageTurnPaginationStatus == status) return
		pageTurnPaginationStatus = status
		playLikeCurlController.updatePaginationReadiness(
			readerPagePaginationReadiness(status)
		)
	}

	fun setPageTurnVisualLocation(
		pageIndex: Int?,
		reason: String?,
		currentFoliateSessionId: String,
		acknowledgement: ReaderPageTurnSettlementAck?
	) {
		require(currentFoliateSessionId.isNotBlank())
		val normalized = pageIndex?.takeIf { it >= 0 }
		if (
			pageTurnVisualPageIndex == normalized &&
			pageTurnVisualLocationReason == reason &&
			pageTurnFoliateSessionId == currentFoliateSessionId &&
			pageTurnSettlementAck == acknowledgement
		) return

		val sessionChanged =
			pageTurnFoliateSessionId != null &&
				pageTurnFoliateSessionId != currentFoliateSessionId
		if (sessionChanged) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.ExternalRelocation
			)
		}
		pageTurnFoliateSessionId = currentFoliateSessionId
		playLikeCurlController.setFoliateSessionId(currentFoliateSessionId)
		val origin = if (sessionChanged) {
			ReaderPageVisualLocationOrigin.External
		} else {
			playLikeCurlController.visualLocationOrigin(
				normalized,
				acknowledgement
			)
		}
		if (!sessionChanged && origin == ReaderPageVisualLocationOrigin.External) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.ExternalRelocation
			)
		}
		pageTurnVisualPageIndex = normalized
		pageTurnVisualLocationReason = reason
		pageTurnSettlementAck = acknowledgement
		playLikeCurlController.synchronizeVisualPageIndex(
			normalized,
			reason,
			acknowledgement
		)
		pageRasterPreparationController.synchronizeVisualPageIndex(normalized, reason)
	}

	fun setShellCoverVisible(visible: Boolean) {
		if (shellCoverVisible == visible) return
		if (visible) {
			dispatchPageHostLifecycleEvent(
				ReaderPageHostLifecycleEvent.ShellCoverShown
			)
		}
		shellCoverVisible = visible
		if (visible) {
			removePageTurnPrewarmLayoutListener()
			playLikeCurlController.invalidate("shell-cover-visible")
			pageRasterPreparationController.invalidate("shell-cover-visible")
		} else {
			pageRasterPreparationController.invalidateCurrentVisualSnapshot("shell-cover-hidden")
		}
		requestPageTurnPrewarmWhenReady()
	}

	fun setPageOperationPolicy(policy: ReaderPageOperationPolicy) {
		pageOperationPolicy = policy
		pageInputSettlementHostController.updateOperationPolicy(policy)
		playLikeCurlController.setPageOperationPolicy(policy)
	}

	fun setPagePreparationRetryKey(retryKey: Int) {
		if (pagePreparationRetryKey == retryKey) return
		val shouldRetry = pagePreparationRetryKey != Int.MIN_VALUE && retryKey > pagePreparationRetryKey
		pagePreparationRetryKey = retryKey
		if (shouldRetry) pageRasterPreparationController.retryPreparation()
	}

	private fun requestPageTurnPrewarmWhenReady() {
		if (!pageTurnCanvasEnabled || !isAttachedToWindow) return
		if (!coldOwnershipAdmitted) {
			coldOwnershipAdmission.requestColdBaseline()
			return
		}
		if (
			pageTurnVisualPageIndex == null ||
			pageTurnPrewarmLayoutListener != null
		) return
		pageTurnPrewarmLayoutSignature = null
		pageTurnPrewarmStableFrameCount = 0
		val listener = ViewTreeObserver.OnPreDrawListener {
			if (!pageTurnCanvasEnabled || !isAttachedToWindow) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			val webView = viewerContentContainer.findDescendantWebView()
			pageRasterHostEventController.webViewAttachmentChanged(
				webView?.isAttachedToWindow == true
			)
			if (
				webView == null ||
				!webView.isAttachedToWindow ||
				width <= 0 ||
				height <= 0 ||
				webView.width <= 0 ||
				webView.height <= 0 ||
				isLayoutRequested ||
				webView.isLayoutRequested
			) return@OnPreDrawListener true
			val profileEpoch = rasterProfileEpoch
			val signature = pageTurnPrewarmLayoutSignature(webView, profileEpoch ?: 0L)
			if (profileEpoch != null) {
				pageRasterHostEventController.layoutSignatureMeasured(signature)
			}
			if (signature == pageTurnPrewarmLayoutSignature) {
				pageTurnPrewarmStableFrameCount += 1
			} else {
				pageTurnPrewarmLayoutSignature = signature
				pageTurnPrewarmStableFrameCount = 1
			}
			if (pageTurnPrewarmStableFrameCount < PageTurnPrewarmRequiredStableFrames) {
				postInvalidateOnAnimation()
				return@OnPreDrawListener true
			}
			if (pageTurnPrewarmStableFrameCount == PageTurnPrewarmRequiredStableFrames) {
				playLikeCurlController.onHostContentReady()
			}
			if (profileEpoch == null) {
				postInvalidateOnAnimation()
				return@OnPreDrawListener true
			}
			if (pageRasterPreparationController.prewarmAdjacent()) {
				removePageTurnPrewarmLayoutListener()
			}
			true
		}
		pageTurnPrewarmLayoutListener = listener
		viewTreeObserver.addOnPreDrawListener(listener)
		postInvalidateOnAnimation()
	}

	private fun pageTurnPrewarmLayoutSignature(
		webView: WebView,
		profileEpoch: Long
	): ReaderPageLayoutSignature = ReaderPageLayoutSignature(
		widthPx = webView.width,
		heightPx = webView.height,
		layoutDirection = layoutDirection,
		rasterProfileEpoch = profileEpoch
	)

	private fun removePageTurnPrewarmLayoutListener() {
		val listener = pageTurnPrewarmLayoutListener ?: return
		pageTurnPrewarmLayoutListener = null
		pageTurnPrewarmLayoutSignature = null
		pageTurnPrewarmStableFrameCount = 0
		if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(listener)
	}

	private var finalHostLifecycleEvent: ReaderPageHostLifecycleEvent? = null
	private var physicalPointerDeliveryClosed = false
	private var task4ResourceTeardownStarted = false
	private var task4Teardown: Deferred<Unit>? = null
	private var observedHostLifecycle: Lifecycle? = null

	private val hostLifecycleObserver = object : DefaultLifecycleObserver {
		override fun onResume(owner: LifecycleOwner) {
			pageRasterHostEventController.lifecycleResumedChanged(true)
			playLikeCurlController.onHostResumedChanged(true)
			requestPageTurnPrewarmWhenReady()
		}

		override fun onPause(owner: LifecycleOwner) {
			pageRasterHostEventController.lifecycleResumedChanged(false)
			playLikeCurlController.onHostResumedChanged(false)
		}

		override fun onStop(owner: LifecycleOwner) {
			pageRasterHostEventController.lifecycleResumedChanged(false)
			playLikeCurlController.onHostResumedChanged(false)
		}

		override fun onDestroy(owner: LifecycleOwner) {
			pageRasterHostEventController.lifecycleResumedChanged(false)
			playLikeCurlController.onHostResumedChanged(false)
			beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.Destroyed)
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		observedHostLifecycle = findViewTreeLifecycleOwner()?.lifecycle
			?.also { lifecycle ->
				lifecycle.addObserver(hostLifecycleObserver)
				val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
				pageRasterHostEventController.lifecycleResumedChanged(resumed)
				playLikeCurlController.onHostResumedChanged(resumed)
			}
		pageRasterHostEventController.webViewAttachmentChanged(
			viewerContentContainer.findDescendantWebView()?.isAttachedToWindow == true
		)
		playLikeCurlController.onHostAttached()
		requestPageTurnPrewarmWhenReady()
	}

	private val legacyGestureDetector = KomikkuGestureDetectorWithLongTap(
		context,
		object : KomikkuGestureDetectorWithLongTap.Listener() {
			override fun onDown(event: MotionEvent): Boolean = true

			override fun onSingleTapConfirmed(event: MotionEvent): Boolean =
				onLegacySingleTapConfirmed(event)

			override fun onDoubleTap(event: MotionEvent): Boolean =
				onLegacySingleTapConfirmed(event)

			override fun onDoubleTapEvent(event: MotionEvent): Boolean =
				if (event.actionMasked == MotionEvent.ACTION_UP) {
					onLegacySingleTapConfirmed(event)
				} else {
					true
				}

			override fun onLongTapConfirmed(event: MotionEvent) {
				nativeTapLongConfirmed = true
				logReaderLongTap()
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
				onContentLongPress(event.x, event.y, width, height)
			}
		}
	)

	private val playLikeCurlGestureDetector = KomikkuGestureDetectorWithLongTap(
		context,
		object : KomikkuGestureDetectorWithLongTap.Listener() {
			override fun onDown(event: MotionEvent): Boolean = true

			override fun onSingleTapConfirmed(event: MotionEvent): Boolean =
				onPlayLikeCurlSingleTapConfirmed(downTimeMillis = event.downTime)

			override fun onSingleTapSuperseded(event: MotionEvent): Boolean =
				onPlayLikeCurlSingleTapConfirmed(downTimeMillis = event.downTime)

			override fun onDoubleTap(event: MotionEvent): Boolean =
				onPlayLikeCurlFirstDoubleTapConfirmed()

			override fun onDoubleTapEvent(event: MotionEvent): Boolean =
				if (event.actionMasked == MotionEvent.ACTION_UP) {
					onPlayLikeCurlSingleTapConfirmed(downTimeMillis = event.downTime)
				} else {
					true
				}

			override fun onLongTapConfirmed(event: MotionEvent) {
				val dispatch = pageInputSettlementHostController.claimContentAction(
					downTimeMillis = event.downTime
				)
				if (dispatch.route != ReaderPagePointerRoute.Content) return
				nativeTapLongConfirmed = true
				logReaderLongTap()
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
				onContentLongPress(event.x, event.y, width, height)
			}
		}
	)

	private fun logReaderTapAction(action: KomikkuNavigationRegion) {
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native tap action=$action"
		)
	}

	private fun logReaderLongTap() {
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native long tap"
		)
	}

	private fun onLegacySingleTapConfirmed(event: MotionEvent): Boolean {
		if (width <= 0 || height <= 0) return false
		val point = KomikkuPoint(
			x = (event.x / width.toFloat()).coerceIn(0f, 1f),
			y = (event.y / height.toFloat()).coerceIn(0f, 1f)
		)
		val action = if (shellCoverVisible) {
			shellCoverNavigator.getAction(point)
		} else {
			navigator.getAction(point)
		}
		logReaderTapAction(action)
		if (chromeOverlayVisible && action != KomikkuNavigationRegion.MENU) return true
		dispatchLegacySingleTapAction(action)
		return true
	}

	private fun onPlayLikeCurlSingleTapConfirmed(downTimeMillis: Long): Boolean {
		val tap = pageInputSettlementHostController.takeDelayedTap(
			downTimeMillis = downTimeMillis
		) ?: return false
		return dispatchPlayLikeCurlDelayedTap(tap)
	}

	private fun onPlayLikeCurlFirstDoubleTapConfirmed(): Boolean {
		val tap = pageInputSettlementHostController.takeOldestDelayedTap()
			?: return false
		return dispatchPlayLikeCurlDelayedTap(tap)
	}

	private fun dispatchPlayLikeCurlDelayedTap(
		tap: ReaderPageContentGestureToken
	): Boolean {
		if (width <= 0 || height <= 0) {
			completeHostGesture(
				tap.gestureId,
				ReaderPageGestureTerminalOutcome.CancelledByUser
			)
			return false
		}
		val point = KomikkuPoint(
			x = (tap.x / width.toFloat()).coerceIn(0f, 1f),
			y = (tap.y / height.toFloat()).coerceIn(0f, 1f)
		)
		val action = navigator.getAction(point)
		logReaderTapAction(action)
		if (chromeOverlayVisible && action != KomikkuNavigationRegion.MENU) {
			completeHostDelayedTap(
				tap.gestureId,
				ReaderPageGestureTerminalOutcome.CompletedTapAction
			)
			return true
		}
		when (
			val result = dispatchPlayLikeCurlSingleTapAction(
				action = action,
				gestureId = tap.gestureId
			)
		) {
			ReaderPageTapDispatchResult.Settling,
			ReaderPageTapDispatchResult.TerminalPublished -> Unit
			is ReaderPageTapDispatchResult.CompleteInHost -> {
				completeHostDelayedTap(
					tap.gestureId,
					result.outcome
				)
			}
		}
		return true
	}

	private fun dispatchLegacySingleTapAction(action: KomikkuNavigationRegion) {
		if (action != KomikkuNavigationRegion.MENU) {
			onAction(action)
			return
		}
		if (shellCoverVisible) {
			onAction(action)
			return
		}
		onAction(KomikkuNavigationRegion.MENU)
	}

	private fun playLikeCurlPageChangeFor(action: KomikkuNavigationRegion): PageChange? {
		val direction = when (action) {
			KomikkuNavigationRegion.NEXT -> ReaderPageTurnDirection.Next
			KomikkuNavigationRegion.PREV -> ReaderPageTurnDirection.Previous
			KomikkuNavigationRegion.RIGHT -> readerTapZonePageTurnDirectionFor(
				ReaderTapZoneAction.Right,
				pageTurnReadingDirection
			)
			KomikkuNavigationRegion.LEFT -> readerTapZonePageTurnDirectionFor(
				ReaderTapZoneAction.Left,
				pageTurnReadingDirection
			)
			KomikkuNavigationRegion.MENU -> null
		}
		return when (direction) {
			ReaderPageTurnDirection.Next -> PageChange.NEXT
			ReaderPageTurnDirection.Previous -> PageChange.PREVIOUS
			null -> null
		}
	}

	private fun dispatchPlayLikeCurlSingleTapAction(
		action: KomikkuNavigationRegion,
		gestureId: Long
	): ReaderPageTapDispatchResult {
		val pageChange = playLikeCurlPageChangeFor(action)
		if (pageChange != null) {
			return when (tapTurnController.turn(gestureId, pageChange)) {
				ReaderPageTurnStartResult.Settling ->
					ReaderPageTapDispatchResult.Settling
				is ReaderPageTurnStartResult.TerminalPublished ->
					ReaderPageTapDispatchResult.TerminalPublished
			}
		}

		onAction(action)
		return ReaderPageTapDispatchResult.CompleteInHost(
			ReaderPageGestureTerminalOutcome.CompletedTapAction
		)
	}

	override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
		if (physicalDispatchMode == ReaderPagePhysicalDispatchMode.PlayLikeCurl) {
			return false
		}
		return interceptLegacyReaderPointerEvent(event)
	}

	private fun interceptLegacyReaderPointerEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				nativeTapCandidate = true
				nativeTapCancelledByDrag = false
				nativeTapLongConfirmed = false
				nativeSwipeIntercepted = false
				swipeStartX = event.x
				swipeStartY = event.y
				if (!shellCoverVisible) return true
				return false
			}
			MotionEvent.ACTION_MOVE -> {
				if (nativeTapMovedBeyondSlop(event.x, event.y)) {
					nativeTapCandidate = false
				}
				if (
					!horizontalSwipeDispatched &&
					nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)
				) {
					nativeSwipeIntercepted = true
					return true
				}
				return false
			}
			MotionEvent.ACTION_UP -> return nativeSwipeIntercepted
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				clearLegacyNativeTapState()
				return false
			}
			else -> return false
		}
	}

	override fun onTouchEvent(event: MotionEvent): Boolean = true

	private fun shouldUsePlayLikeCurlPointerRouter(): Boolean =
		pageTurnCanvasEnabled &&
			!verticalPageDragPreview &&
			!shellCoverVisible

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		if (event.actionMasked == MotionEvent.ACTION_DOWN) {
			check(physicalDispatchMode == null) {
				"A physical pointer dispatch mode is already active"
			}
			physicalDispatchMode = if (shouldUsePlayLikeCurlPointerRouter()) {
				ReaderPagePhysicalDispatchMode.PlayLikeCurl
			} else {
				ReaderPagePhysicalDispatchMode.Legacy
			}
			pageRasterPreparationController.onPointerInteractionChanged(true)
		}

		val handled = when (physicalDispatchMode) {
			ReaderPagePhysicalDispatchMode.PlayLikeCurl ->
				dispatchPlayLikeCurlPointerEvent(event)
			ReaderPagePhysicalDispatchMode.Legacy ->
				dispatchLegacyReaderPointerEvent(event)
			null -> super.dispatchTouchEvent(event)
		}

		if (
			event.actionMasked == MotionEvent.ACTION_UP ||
			event.actionMasked == MotionEvent.ACTION_CANCEL
		) {
			if (physicalDispatchMode != ReaderPagePhysicalDispatchMode.PlayLikeCurl) {
				pageRasterPreparationController.onPointerInteractionChanged(false)
			}
			physicalDispatchMode = null
		}
		return handled
	}

	private fun dispatchLegacyReaderPointerEvent(event: MotionEvent): Boolean {
		val handled = super.dispatchTouchEvent(event)
		handleSwipeTouchEvent(event)
		if (
			!horizontalSwipeDispatched &&
			!nativeSwipeIntercepted &&
			!nativeTapCancelledByDrag
		) {
			legacyGestureDetector.onTouchEvent(event)
		}
		val consumed = handled || nativeSwipeIntercepted || horizontalSwipeDispatched
		if (
			event.actionMasked == MotionEvent.ACTION_UP ||
			event.actionMasked == MotionEvent.ACTION_CANCEL
		) {
			clearLegacyNativeTapState()
		}
		return consumed
	}

	private fun dispatchPlayLikeCurlPointerEvent(event: MotionEvent): Boolean {
		val pointerEvent: ReaderPageHostPointerEvent? = when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> ReaderPageHostPointerEvent.Down(
				x = event.x,
				y = event.y,
				downTimeMillis = event.downTime
			)
			MotionEvent.ACTION_MOVE -> ReaderPageHostPointerEvent.Move(
				x = event.x,
				y = event.y,
				touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
			)
			MotionEvent.ACTION_UP -> ReaderPageHostPointerEvent.Up
			MotionEvent.ACTION_CANCEL -> ReaderPageHostPointerEvent.Cancel
			MotionEvent.ACTION_POINTER_DOWN ->
				ReaderPageHostPointerEvent.SecondaryPointerDown
			MotionEvent.ACTION_POINTER_UP ->
				ReaderPageHostPointerEvent.SecondaryPointerUp
			else -> null
		}
		val pointerDispatch = pointerEvent?.let(
			pageInputSettlementHostController::dispatchPointer
		)
		return if (pointerDispatch != null) {
			applyPointerRoute(event, pointerDispatch)
		} else {
			viewerContentContainer.dispatchTouchEvent(event)
		}
	}

	private fun applyPointerRoute(
		event: MotionEvent,
		dispatch: ReaderPageHostPointerDispatchResult
	): Boolean {
		updateGestureDiagnostic(event, dispatch)
		return when (val route = dispatch.route) {
		ReaderPagePointerRoute.Content -> {
			if (event.actionMasked == MotionEvent.ACTION_DOWN) {
				retainedContentDown?.recycle()
				retainedContentDown = MotionEvent.obtain(event)
			}
			viewerContentContainer.dispatchTouchEvent(event)
			playLikeCurlGestureDetector.onTouchEvent(event)
			if (event.actionMasked == MotionEvent.ACTION_UP) {
				recycleRetainedContentDown()
				clearPlayLikeCurlPointerTapFlagsAfterUp()
			}
			true
		}
		is ReaderPagePointerRoute.ContentTerminal -> {
			val handled = viewerContentContainer.dispatchTouchEvent(event)
			playLikeCurlGestureDetector.onTouchEvent(event)
			completeHostGesture(
				route.gestureId,
				route.outcome
			)
			recycleRetainedContentDown()
			clearPlayLikeCurlPointerTapFlagsAfterUp()
			handled
		}
		is ReaderPagePointerRoute.ClaimCurl -> {
			dispatchContentCancel(event)
			playLikeCurlController.showSurfaceForGesture()
			val originalDown = checkNotNull(retainedContentDown) {
				"Curl claim has no retained content DOWN"
			}
			val downResult = playLikeCurlController.onPageTouchEvent(
				originalDown,
				route.gestureId
			)
			recycleRetainedContentDown()
			when (downResult) {
				ReaderPageCurlDispatchResult.Accepted -> {
					val moveResult = playLikeCurlController.onPageTouchEvent(
						event,
						route.gestureId
					)
					when (moveResult) {
						ReaderPageCurlDispatchResult.Accepted -> {
							playLikeCurlGestureOwned = true
						}
						ReaderPageCurlDispatchResult.TerminalPublished -> {
							playLikeCurlGestureOwned = false
							clearPlayLikeCurlPointerTapFlagsAfterUp()
						}
					}
				}
				ReaderPageCurlDispatchResult.TerminalPublished -> {
					playLikeCurlGestureOwned = false
					clearPlayLikeCurlPointerTapFlagsAfterUp()
				}
			}
			true
		}
		is ReaderPagePointerRoute.Curl -> {
			playLikeCurlController.onPageTouchEvent(event, route.gestureId)
			if (
				event.actionMasked == MotionEvent.ACTION_UP ||
				event.actionMasked == MotionEvent.ACTION_CANCEL
			) {
				playLikeCurlGestureOwned = false
				clearPlayLikeCurlPointerTapFlagsAfterUp()
			}
			true
		}
		is ReaderPagePointerRoute.Terminal -> {
			dispatchContentCancel(event)
			recycleRetainedContentDown()
			playLikeCurlGestureOwned = false
			true
		}
		ReaderPagePointerRoute.Consume -> true
		ReaderPagePointerRoute.Ignore -> true
		}
	}

	private fun updateGestureDiagnostic(
		event: MotionEvent,
		dispatch: ReaderPageHostPointerDispatchResult
	) {
		val gestureId = dispatch.gestureId ?: return
		val context = gestureDiagnostics[gestureId] ?: return
		when (dispatch.route) {
			ReaderPagePointerRoute.Content,
			is ReaderPagePointerRoute.ContentTerminal ->
				context.owner = ReaderPagePointerOwnership.Content
			is ReaderPagePointerRoute.ClaimCurl,
			is ReaderPagePointerRoute.Curl -> {
				context.owner = ReaderPagePointerOwnership.Curl
				if (context.physicalDirection == null && event.x != context.downX) {
					val physical = if (event.x < context.downX) {
						ReaderPagePhysicalDirection.Left
					} else {
						ReaderPagePhysicalDirection.Right
					}
					context.physicalDirection = physical
					context.logicalDirection = when {
						pageTurnReadingDirection == "rtl" &&
							physical == ReaderPagePhysicalDirection.Left ->
							ReaderPageTurnDirection.Previous
						pageTurnReadingDirection == "rtl" ->
							ReaderPageTurnDirection.Next
						physical == ReaderPagePhysicalDirection.Left ->
							ReaderPageTurnDirection.Next
						else -> ReaderPageTurnDirection.Previous
					}
				}
			}
			else -> Unit
		}
	}

	private fun dispatchContentCancel(source: MotionEvent? = null) {
		val retainedDown = retainedContentDown ?: return
		val cancel = MotionEvent.obtain(source ?: retainedDown).apply {
			action = MotionEvent.ACTION_CANCEL
		}
		try {
			viewerContentContainer.dispatchTouchEvent(cancel)
			playLikeCurlGestureDetector.cancelForDrag(cancel)
		} finally {
			cancel.recycle()
		}
	}

	private fun recycleRetainedContentDown() {
		retainedContentDown?.recycle()
		retainedContentDown = null
	}

	private fun clearPlayLikeCurlPointerTapFlagsAfterUp() {
		nativeTapCandidate = false
		nativeTapLongConfirmed = false
	}

	private fun handleSwipeTouchEvent(event: MotionEvent) {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				swipeStartX = event.x
				swipeStartY = event.y
				horizontalSwipeDispatched = false
				nativeTapCancelledByDrag = false
				shellCoverDragDiagnosticLogged = false
				nativeDragPreviewDiagnosticLogged = false
			}
			MotionEvent.ACTION_MOVE -> {
				if (!horizontalSwipeDispatched) {
					val dx = event.x - swipeStartX
					val dy = event.y - swipeStartY
					cancelPendingLongTapForDrag(dx, dy, event)
					logReaderDragCandidate(dx, dy)
					if (nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)) {
						nativeTapCancelledByDrag = true
						if (shellCoverVisible) {
							updateShellCoverDragOffset(dx)
						} else {
							updateReadableViewerDragOffset(dx, dy, ReaderPageDragPreviewPhase.Update)
							logReaderReadableDragPreview(dx, dy)
						}
					}
				}
			}
			MotionEvent.ACTION_UP -> {
				if (!horizontalSwipeDispatched) {
					val dx = event.x - swipeStartX
					val dy = event.y - swipeStartY
					cancelPendingLongTapForDrag(dx, dy, event)
					logReaderDragCandidate(dx, dy)
					if (nativeTapCancelledByDrag || nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)) {
						if (shellCoverVisible) {
							updateShellCoverDragOffset(dx)
							dispatchHorizontalSwipeViewerAction(
								deltaX = dx,
								deltaY = dy
							)
						} else {
							logReaderReadableDragPreview(dx, dy)
							val readableSwipeAction = readableSwipeAction(
								deltaX = dx,
								deltaY = dy,
								thresholdPx = readableDragActivationSlopPx()
							)
							updateReadableViewerDragOffset(
								deltaX = dx,
								deltaY = dy,
								phase = if (readableSwipeAction != null) {
									ReaderPageDragPreviewPhase.Release
								} else {
									ReaderPageDragPreviewPhase.Cancel
								}
							)
							dispatchHorizontalSwipeViewerAction(
								deltaX = dx,
								deltaY = dy
							)
						}
					}
				}
				clearSwipeTouchState()
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				if (!shellCoverVisible) {
					cancelReadableViewerDragPreview()
				}
				clearSwipeTouchState()
			}
		}
	}

	private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {
		val thresholdPx = readerSwipeThresholdPx(shellCoverVisible)
		val action = if (shellCoverVisible) {
			readerShellCoverSwipeAction(deltaX, deltaY, thresholdPx)
		} else {
			readableSwipeAction(deltaX, deltaY, thresholdPx)
		} ?: return false
		horizontalSwipeDispatched = true
		nativeTapCandidate = false
		nativeTapCancelledByDrag = true
		nativeSwipeIntercepted = true
		if (shellCoverVisible) {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover swipe action=$action dx=$deltaX dy=$deltaY threshold=$thresholdPx"
			)
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover command action=$action"
			)
		} else {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader native readable swipe action=$action dx=$deltaX dy=$deltaY threshold=$thresholdPx"
			)
		}
		if (shellCoverVisible) {
			when (action) {
				ReaderTapZoneAction.Right -> onAction(KomikkuNavigationRegion.NEXT)
				ReaderTapZoneAction.Left -> onAction(KomikkuNavigationRegion.PREV)
				else -> return false
			}
		} else {
			when (action) {
				ReaderTapZoneAction.Right -> onAction(KomikkuNavigationRegion.RIGHT)
				ReaderTapZoneAction.Left -> onAction(KomikkuNavigationRegion.LEFT)
				else -> return false
			}
		}
		return true
	}

	private fun updateShellCoverDragOffset(deltaX: Float) {
		shellCoverView?.translationX = deltaX
	}

	private fun updateReadableViewerDragOffset(
		deltaX: Float,
		deltaY: Float,
		phase: ReaderPageDragPreviewPhase
	) {
		onReadableDragPreview(deltaX, deltaY, width, height, phase)
	}

	private fun cancelReadableViewerDragPreview() {
		onReadableDragPreview(0f, 0f, width, height, ReaderPageDragPreviewPhase.Cancel)
	}

	private fun logReaderDragCandidate(deltaX: Float, deltaY: Float) {
		val thresholdPx = readerSwipeThresholdPx(shellCoverVisible = shellCoverVisible)
		val magnitude = if (shellCoverVisible || !verticalPageDragPreview) {
			abs(deltaX)
		} else {
			abs(deltaY)
		}
		if (shellCoverDragDiagnosticLogged || magnitude <= thresholdPx) return
		shellCoverDragDiagnosticLogged = true
		val label = if (shellCoverVisible) {
			"Reader shell cover drag candidate"
		} else {
			"Reader native drag candidate"
		}
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"$label dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
		)
	}

	private fun logReaderReadableDragPreview(deltaX: Float, deltaY: Float) {
		val magnitude = if (verticalPageDragPreview) abs(deltaY) else abs(deltaX)
		if (nativeDragPreviewDiagnosticLogged || magnitude <= touchSlopPx) return
		nativeDragPreviewDiagnosticLogged = true
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native drag preview dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
		)
	}

	private fun dispatchPageHostLifecycleEvent(
		event: ReaderPageHostLifecycleEvent
	): List<Long> = pageInputSettlementHostController.onLifecycleEvent(event).also { cancelled ->
		if (cancelled.isNotEmpty()) {
			pageRasterPreparationController.onPointerInteractionChanged(false)
		}
	}

	private fun completeHostGesture(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean = pageInputSettlementHostController.complete(
		gestureId,
		outcome
	).also { won ->
		if (won) {
			pageRasterPreparationController.onPointerInteractionChanged(false)
		}
	}

	private fun emitGestureDiagnostic(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	) {
		val context = gestureDiagnostics.remove(gestureId)
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			ReaderPageDiagnostic.gesture(
				readerSession = readerDiagnosticSession,
				gestureId = gestureId,
				outcome = outcome,
				owner = context?.owner ?: ReaderPagePointerOwnership.Terminal,
				rasterGeneration = playLikeCurlController.diagnosticRasterGeneration(),
				textureGeneration = playLikeCurlController.diagnosticTextureGeneration(),
				physicalDirection = context?.physicalDirection,
				logicalDirection = context?.logicalDirection,
				durationMs = context?.let {
					(SystemClock.uptimeMillis() - it.startedAtMillis).coerceAtLeast(0L)
				} ?: 0L
			)
		)
	}

	private fun completeHostDelayedTap(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome
	): Boolean = pageInputSettlementHostController.completeDelayedTap(
		gestureId,
		outcome
	).also { won ->
		if (won) {
			pageRasterPreparationController.onPointerInteractionChanged(false)
		}
	}

	private fun completePageGesture(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail
	): Boolean {
		val won = completeHostGesture(
			gestureId,
			outcome
		)
		logGestureTerminal(
			gestureId = gestureId,
			outcome = outcome,
			detail = detail,
			won = won
		)
		return won
	}

	private fun logGestureTerminal(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail,
		won: Boolean
	) {
		val message = "Reader gesture terminal gestureId=$gestureId " +
			"outcome=$outcome won=$won detail=$detail"
		if (won) {
			Logger.i(KomikkuReaderNativeFrameHostTag, message)
		} else {
			Logger.w(
				KomikkuReaderNativeFrameHostTag,
				"Reader gesture terminal replay $message"
			)
		}
	}

	private fun cancelPendingLongTapForDrag(deltaX: Float, deltaY: Float, event: MotionEvent) {
		if (abs(deltaX) <= touchSlopPx && abs(deltaY) <= touchSlopPx) return
		if (!nativeTapCancelledByDrag) legacyGestureDetector.cancelForDrag(event)
		nativeTapCandidate = false
		nativeTapCancelledByDrag = true
	}

	private fun nativeTapMovedBeyondSlop(x: Float, y: Float): Boolean =
		abs(x - swipeStartX) > touchSlopPx || abs(y - swipeStartY) > touchSlopPx

	private fun nativeHorizontalSwipeMovedBeyondSlop(x: Float, y: Float): Boolean =
		if (shellCoverVisible) {
			readerShellCoverSwipeAction(
				deltaX = x - swipeStartX,
				deltaY = y - swipeStartY,
				thresholdPx = touchSlopPx
			) != null
		} else {
			readableSwipeAction(
				deltaX = x - swipeStartX,
				deltaY = y - swipeStartY,
				thresholdPx = readableDragActivationSlopPx()
			) != null
		}

	private fun readableDragActivationSlopPx(): Float = when {
		pageTurnCanvasEnabled && !verticalPageDragPreview -> touchSlopPx
		else -> readablePageDragSlopPx
	}

	private fun readableSwipeAction(
		deltaX: Float,
		deltaY: Float,
		thresholdPx: Float
	): ReaderTapZoneAction? =
		readerNativeReaderSwipeAction(
			deltaX = deltaX,
			deltaY = deltaY,
			thresholdPx = thresholdPx,
			verticalPageDragPreview = verticalPageDragPreview
		)

	private fun readerSwipeThresholdPx(shellCoverVisible: Boolean): Float =
		if (shellCoverVisible) {
			touchSlopPx
		} else {
			readablePageDragSlopPx
		}

	private fun clearLegacyNativeTapState(
		reason: ReaderPageLifecycleCancellationReason? = null
	) {
		if (reason == null) {
			legacyGestureDetector.cancelPendingLongTap()
		} else {
			legacyGestureDetector.cancel()
		}
		nativeTapCandidate = false
		nativeTapCancelledByDrag = false
		nativeTapLongConfirmed = false
		nativeSwipeIntercepted = false
	}

	private fun clearPlayLikeCurlNativeTapState(
		reason: ReaderPageLifecycleCancellationReason
	) {
		dispatchContentCancel()
		playLikeCurlGestureDetector.cancel()
		recycleRetainedContentDown()
		clearPlayLikeCurlPointerTapFlagsAfterUp()
		nativeTapCancelledByDrag = false
		nativeSwipeIntercepted = false
	}

	private fun clearSwipeTouchState() {
		shellCoverView?.translationX = 0f
		horizontalSwipeDispatched = false
		swipeStartX = 0f
		swipeStartY = 0f
		nativeDragPreviewDiagnosticLogged = false
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		if (oldw <= 0 || oldh <= 0 || (w == oldw && h == oldh)) return
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.ViewportChanged
		)
		playLikeCurlController.onHostSizeChanged()
		pageRasterPreparationController.invalidate("size-changed")
		requestPageTurnPrewarmWhenReady()
	}

	override fun onWindowVisibilityChanged(visibility: Int) {
		super.onWindowVisibilityChanged(visibility)
		if (!pageTurnCanvasEnabled) return
		if (visibility == VISIBLE) {
			val resumed =
				observedHostLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
			pageRasterHostEventController.lifecycleResumedChanged(resumed)
			playLikeCurlController.onHostResumedChanged(resumed)
			requestPageTurnPrewarmWhenReady()
			return
		}
		pageRasterHostEventController.lifecycleResumedChanged(false)
		playLikeCurlController.onHostResumedChanged(false)
		dispatchPageHostLifecycleEvent(
			ReaderPageHostLifecycleEvent.WindowHidden
		)
		removePageTurnPrewarmLayoutListener()
		playLikeCurlController.onHostWindowHidden()
		pageRasterPreparationController.invalidate("window-hidden")
	}

	override fun onDetachedFromWindow() {
		pageRasterHostEventController.webViewAttachmentChanged(false)
		pageRasterHostEventController.lifecycleResumedChanged(false)
		playLikeCurlController.onHostResumedChanged(false)
		beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.Detached)
		closePhysicalPointerDelivery()
		teardownTask4Resources()
		observedHostLifecycle?.removeObserver(hostLifecycleObserver)
		observedHostLifecycle = null
		super.onDetachedFromWindow()
	}

	fun closeReader() {
		beginFinalHostLifecycle(ReaderPageHostLifecycleEvent.ReaderClosed)
		closePhysicalPointerDelivery()
		teardownTask4Resources()
	}

	private fun beginFinalHostLifecycle(event: ReaderPageHostLifecycleEvent) {
		require(event in readerPageFinalHostLifecycleEvents) {
			"Non-final host event passed to final lifecycle gate: $event"
		}
		if (finalHostLifecycleEvent != null) return
		finalHostLifecycleEvent = event
		val reason = event.cancellationReason()
		dispatchPageHostLifecycleEvent(event)
		clearLegacyNativeTapState(reason)
	}

	private fun closePhysicalPointerDelivery() {
		if (physicalPointerDeliveryClosed) return
		physicalPointerDeliveryClosed = true
		val event = checkNotNull(finalHostLifecycleEvent)
		val reason = event.cancellationReason()
		pageInputSettlementHostController.abandonPhysicalPointerStream(reason)
		physicalDispatchMode = null
	}

	private fun teardownTask4Resources() {
		if (task4ResourceTeardownStarted) return
		task4ResourceTeardownStarted = true
		coldOwnershipAdmission.close()
		removePageTurnPrewarmLayoutListener()
		pageRasterHostEventController.close()
		val teardown = pageRasterPreparationController.destroy()
		task4Teardown = teardown
		teardown.invokeOnCompletion { failure ->
			ownershipMainHandler.post {
				if (failure == null) {
					ownershipProbe.request { result ->
						result.fold(
							onSuccess = { snapshot ->
								emitOwnershipDiagnostic(
									ReaderPageOwnershipPhase.AfterClose,
									snapshot
								)
							},
							onFailure = { unavailable ->
								emitOwnershipUnavailable(
									ReaderPageOwnershipPhase.AfterClose,
									(unavailable as
										ReaderPageOwnershipUnavailableException).reason
								)
							}
						)
					}
					return@post
				}
				val typedFailure = failure as? ReaderPageTeardownException
					?: ReaderPageTeardownException(
						ReaderPageTeardownStage.BundleOwners,
						cause = failure
					)
				Logger.e(
					KomikkuReaderNativeFrameHostTag,
					ReaderPageDiagnostic.teardownFailure(
						readerDiagnosticSession,
						typedFailure
					)
				)
			}
		}
	}
}

private fun View.findDescendantWebView(): WebView? {
	if (this is WebView) return this
	if (this !is ViewGroup) return null
	for (index in 0 until childCount) {
		getChildAt(index).findDescendantWebView()?.let { return it }
	}
	return null
}

internal class KomikkuGestureDetectorWithLongTap(
	context: Context,
	private val listener: Listener
) : GestureDetector(context, listener) {
	private val handler = Handler(Looper.getMainLooper())
	private val slop = ViewConfiguration.get(context).scaledTouchSlop
	private val doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
	private val longTapTime = ViewConfiguration.getLongPressTimeout().toLong()
	private val doubleTapTime = ViewConfiguration.getDoubleTapTimeout().toLong()
	private val doubleTapMinTime = AndroidGestureDoubleTapMinTimeMillis
	private var downX = 0f
	private var downY = 0f
	private var lastUp = 0L
	private var currentTapEligible = false
	private var previousTapEligible = false
	private var lastDownEvent: MotionEvent? = null
	private val longTapFn = Runnable {
		currentTapEligible = false
		lastDownEvent?.let(listener::onLongTapConfirmed)
	}

	fun cancelPendingLongTap() {
		handler.removeCallbacks(longTapFn)
	}

	fun cancel() {
		val event = lastDownEvent
		if (event == null) {
			resetTracking()
			return
		}
		cancelForDrag(event)
	}

	fun cancelForDrag(event: MotionEvent) {
		val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
		resetTracking()
		try {
			super.onTouchEvent(cancel)
		} finally {
			cancel.recycle()
		}
	}

	private fun resetTracking() {
		handler.removeCallbacks(longTapFn)
		currentTapEligible = false
		previousTapEligible = false
		lastDownEvent?.recycle()
		lastDownEvent = null
	}

	override fun onTouchEvent(ev: MotionEvent): Boolean {
		when (ev.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				val previousDown = lastDownEvent
				val elapsedSinceUp = ev.eventTime - lastUp
				val distanceX = previousDown?.let { ev.x - it.x } ?: 0f
				val distanceY = previousDown?.let { ev.y - it.y } ?: 0f
				val withinDoubleTapDistance =
					distanceX * distanceX + distanceY * distanceY <= doubleTapSlop * doubleTapSlop
				val isDoubleTapCandidate =
					previousTapEligible &&
						previousDown != null &&
						elapsedSinceUp in doubleTapMinTime..doubleTapTime &&
						withinDoubleTapDistance
				if (
					previousTapEligible &&
					previousDown != null &&
					elapsedSinceUp in 0L..doubleTapTime &&
					!isDoubleTapCandidate
				) {
					listener.onSingleTapSuperseded(previousDown)
				}
				previousDown?.recycle()
				lastDownEvent = MotionEvent.obtain(ev)
				currentTapEligible = true
				previousTapEligible = false
				if (!isDoubleTapCandidate) {
					downX = ev.x
					downY = ev.y
					handler.postDelayed(longTapFn, longTapTime)
				}
			}
			MotionEvent.ACTION_MOVE -> {
				if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) {
					currentTapEligible = false
					handler.removeCallbacks(longTapFn)
				}
			}
			MotionEvent.ACTION_UP -> {
				lastUp = ev.eventTime
				previousTapEligible = currentTapEligible
				currentTapEligible = false
				handler.removeCallbacks(longTapFn)
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				currentTapEligible = false
				previousTapEligible = false
				handler.removeCallbacks(longTapFn)
			}
		}
		return super.onTouchEvent(ev)
	}

	open class Listener : SimpleOnGestureListener() {
		open fun onSingleTapSuperseded(event: MotionEvent): Boolean = false

		open fun onLongTapConfirmed(event: MotionEvent) {
		}
	}
}

private class KomikkuReaderNativeNavigationOverlayView(context: Context) : View(context) {
	private var navigator: KomikkuReaderNavigator? = null
	private val regionPaint = Paint()
	private val textPaint = Paint().apply {
		textAlign = Paint.Align.CENTER
		color = Color.WHITE
		textSize = 48f
	}
	private val textBorderPaint = Paint().apply {
		textAlign = Paint.Align.CENTER
		color = Color.BLACK
		textSize = 48f
		style = Paint.Style.STROKE
		strokeWidth = 6f
	}

	fun setNavigation(navigator: KomikkuReaderNavigator) {
		this.navigator = navigator
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		navigator?.getRegions()?.forEach { region ->
			val rect = region.rectF
			val left = width * rect.left
			val top = height * rect.top
			val right = width * rect.right
			val bottom = height * rect.bottom
			regionPaint.color = region.type.colorArgb.toLong().toInt()
			canvas.drawRect(left, top, right, bottom, regionPaint)

			val centerX = left + (width * abs(rect.left - rect.right) / 2f)
			val centerY = top + (height * abs(rect.top - rect.bottom) / 2f)
			canvas.drawText(region.type.label, centerX, centerY, textBorderPaint)
			canvas.drawText(region.type.label, centerX, centerY, textPaint)
		}
	}
}

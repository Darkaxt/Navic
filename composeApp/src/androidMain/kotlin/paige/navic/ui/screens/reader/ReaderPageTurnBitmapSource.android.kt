package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.ViewTreeObserver
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.util.core.Logger
import kotlin.coroutines.resume
import kotlin.math.abs

private const val ReaderPageTurnBitmapSourceTag = "ReaderPageTurnBitmapSource"
private const val PageTurnCaptureSampleColumns = 48
private const val PageTurnCaptureSampleRows = 32
private const val PageTurnCapturePrimarySamplePhase = 0.5f
private const val PageTurnCaptureShiftedSamplePhase = 0f
private const val PageTurnCaptureMinimumLuminanceRange = 32
private const val PageTurnCaptureForegroundDistance = 24
private const val PageTurnCaptureMinimumLowContrastLuminanceRange = 16
private const val PageTurnCaptureLowContrastForegroundDistance = 8
private const val PageTurnCaptureMinimumForegroundSamples = 3
private const val PageTurnCaptureForegroundSampleDivisor = 384
private const val LegacyLiveCaptureMaximumFrameAttempts = 2

internal data class ReaderPageTurnCaptureResult(
	val bitmap: Bitmap,
	val sourceRectInWindow: Rect,
	val geometry: ReaderPageTurnCaptureGeometry,
	val elapsedMs: Long
)

internal data class ReaderPageTurnLiveCaptureResult(
	val captured: ReaderPageTurnCaptureResult,
	val acceptedReceipt: ReaderPageTurnPresentationReceipt
)

internal class ReaderPageTurnLiveCaptureOwnership<T : Any>(
	private val release: (T) -> Unit
) : ReaderPageRelocationContentValidationHandle {
	internal data class Completion<T>(val candidate: T?)

	private enum class State {
		Open,
		Cancelled,
		Terminal
	}

	private val lock = Any()
	private var state = State.Open
	private var candidate: T? = null
	private var candidateRetained = false
	private var externalWriteInFlight = false

	val retainedCandidateCount: Int
		get() = synchronized(lock) { if (candidateRetained) 1 else 0 }

	val isOpen: Boolean
		get() = synchronized(lock) { state == State.Open }

	fun retain(candidate: T): Boolean = synchronized(lock) {
		if (state != State.Open || candidateRetained) return@synchronized false
		this.candidate = candidate
		candidateRetained = true
		true
	}

	fun beginExternalWrite(): Boolean = synchronized(lock) {
		if (
			state != State.Open ||
			!candidateRetained ||
			externalWriteInFlight
		) {
			return@synchronized false
		}
		externalWriteInFlight = true
		true
	}

	fun externalWriteIsCurrent(): Boolean = synchronized(lock) {
		state == State.Open && externalWriteInFlight
	}

	fun runIfExternalWriteCurrent(action: () -> Unit): Boolean = synchronized(lock) {
		if (state != State.Open || !externalWriteInFlight) return@synchronized false
		action()
		true
	}

	fun endExternalWrite(): Boolean {
		var deferredRelease: T? = null
		val current = synchronized(lock) {
			if (!externalWriteInFlight) return false
			externalWriteInFlight = false
			if (state == State.Cancelled) {
				state = State.Terminal
				deferredRelease = takeCandidateLocked()
				false
			} else {
				state == State.Open
			}
		}
		deferredRelease?.let(release)
		return current
	}

	fun finish(accepted: Boolean): Completion<T>? {
		var rejected: T? = null
		val completion = synchronized(lock) {
			if (state != State.Open || externalWriteInFlight) return null
			state = State.Terminal
			val owned = takeCandidateLocked()
			if (accepted) Completion<T>(owned) else {
				rejected = owned
				Completion<T>(null)
			}
		}
		rejected?.let(release)
		return completion
	}

	override fun cancel(): Boolean {
		var retained: T? = null
		val cancelled = synchronized(lock) {
			if (state != State.Open) return false
			if (externalWriteInFlight) {
				state = State.Cancelled
			} else {
				state = State.Terminal
				retained = takeCandidateLocked()
			}
			true
		}
		retained?.let(release)
		return cancelled
	}

	private fun takeCandidateLocked(): T? {
		val retained = candidate.takeIf { candidateRetained }
		candidate = null
		candidateRetained = false
		return retained
	}
}

internal fun readerPageTurnLiveCapturePreflightAccepted(
	activityAvailable: Boolean,
	webViewAttached: Boolean,
	decorAttached: Boolean,
	isStillCurrent: Boolean,
	geometryMatches: Boolean,
	left: Int,
	top: Int,
	right: Int,
	bottom: Int,
	decorWidth: Int,
	decorHeight: Int
): Boolean = activityAvailable &&
	webViewAttached &&
	decorAttached &&
	isStillCurrent &&
	geometryMatches &&
	decorWidth > 0 &&
	decorHeight > 0 &&
	left >= 0 &&
	top >= 0 &&
	right > left &&
	bottom > top &&
	right <= decorWidth &&
	bottom <= decorHeight

internal class ReaderPageTurnPresentedCaptureOwnership<T : Any>(
	private val release: (T) -> Unit
) : ReaderPageRelocationContentValidationHandle {
	internal data class Completion<T>(val candidate: T?)

	private enum class State {
		Open,
		Completed,
		Cancelled
	}

	private val lock = Any()
	private var state = State.Open
	private var candidate: T? = null
	private var candidateRetained = false

	val retainedCandidateCount: Int
		get() = synchronized(lock) {
			if (candidateRetained) 1 else 0
		}

	fun retain(candidate: T?): Boolean = synchronized(lock) {
		if (state != State.Open || candidateRetained) return@synchronized false
		this.candidate = candidate
		candidateRetained = candidate != null
		true
	}

	fun complete(): Completion<T>? = synchronized(lock) {
		if (state != State.Open) return@synchronized null
		state = State.Completed
		val completed = Completion(candidate)
		candidate = null
		candidateRetained = false
		completed
	}

	override fun cancel(): Boolean {
		val retained = synchronized(lock) {
			if (state != State.Open) return false
			state = State.Cancelled
			val owned = candidate.takeIf { candidateRetained }
			candidate = null
			candidateRetained = false
			owned
		}
		retained?.let(release)
		return true
	}
}

internal class ReaderPageTurnBitmapSource(
	private var bitmapQuality: ReaderPageBitmapQuality = ReaderPageBitmapQuality.Balanced
) {
	private var visualStateRequestId = 0L
	private var liveCaptureEpoch = 0L
	private val mainHandler = Handler(Looper.getMainLooper())

	val isAvailable: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

	fun updateBitmapQuality(quality: ReaderPageBitmapQuality) {
		bitmapQuality = quality
	}

	fun capturePage(
		webView: WebView,
		direction: ReaderPageTurnPhysicalDirection,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) = capture(webView, onCaptured) { geometry, location ->
		geometry.sourceRectInWindow(
			direction = direction,
			webViewWindowLeft = location[0],
			webViewWindowTop = location[1],
			webViewWidth = webView.width,
			webViewHeight = webView.height
		)
	}

	fun captureSurface(
		webView: WebView,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) = captureSurface(webView, allowStableLowContrast = false, onCaptured)

	private fun captureSurface(
		webView: WebView,
		allowStableLowContrast: Boolean,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) = capture(webView, onCaptured, allowStableLowContrast) { geometry, location ->
		geometry.surfaceRectInWindow(
			webViewWindowLeft = location[0],
			webViewWindowTop = location[1],
			webViewWidth = webView.width,
			webViewHeight = webView.height
		)
	}

	fun captureSurface(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) = captureResolvedGeometry(webView, geometry, onCaptured) { resolved, location ->
		resolved.surfaceRectInWindow(
			webViewWindowLeft = location[0],
			webViewWindowTop = location[1],
			webViewWidth = webView.width,
			webViewHeight = webView.height
		)
	}

	fun capturePresentedSurface(
		webView: WebView,
		target: ReaderPageTurnPresentationTarget,
		isStillCurrent: () -> Boolean = { true },
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	): ReaderPageRelocationContentValidationHandle {
		val ownership = ReaderPageTurnPresentedCaptureOwnership(
			release = { rejected: ReaderPageTurnCaptureResult ->
				rejected.bitmap.takeUnless { it.isRecycled }?.recycle()
			}
		)
		if (!isStillCurrent() || !canCapture(webView)) {
			ownership.complete()
			onCaptured(null)
			return ownership
		}
		queryPresentationReceipt(webView, target) initial@{ initialReceipt ->
			if (!isStillCurrent() || initialReceipt?.matches(target) != true) {
				if (ownership.complete() != null) onCaptured(null)
				return@initial
			}
			captureSurface(webView, allowStableLowContrast = true) { candidate ->
				if (!ownership.retain(candidate)) {
					candidate?.bitmap?.takeUnless { it.isRecycled }?.recycle()
					return@captureSurface
				}
				queryPresentationReceipt(webView, target) { finalReceipt ->
					val completed = ownership.complete() ?: return@queryPresentationReceipt
					onCaptured(
						readerPageTurnPresentedSurfaceCandidate(
							target = target,
							initialReceipt = initialReceipt,
							finalReceipt = finalReceipt,
							candidate = completed.candidate,
							foregroundSuccess = completed.candidate != null,
							isStillCurrent = isStillCurrent(),
							recycle = { rejected ->
								rejected.bitmap
									.takeUnless { it.isRecycled }
									?.recycle()
							}
						)
					)
				}
			}
		}
		return ownership
	}

	fun captureLiveCompositedSurface(
		webView: WebView,
		target: ReaderPageTurnPresentationTarget.Live,
		expectedBitmapWidth: Int,
		expectedBitmapHeight: Int,
		isStillCurrent: () -> Boolean = { true },
		onCaptured: (ReaderPageTurnLiveCaptureResult?) -> Unit
	): ReaderPageRelocationContentValidationHandle {
		val ownership = ReaderPageTurnLiveCaptureOwnership(
			release = { rejected: ReaderPageTurnCaptureResult ->
				rejected.bitmap.takeUnless { it.isRecycled }?.recycle()
			}
		)
		var startRunnable: Runnable? = null
		var frameCommitObserver: ViewTreeObserver? = null
		var frameCommitCallback: Runnable? = null
		var legacyObserver: ViewTreeObserver? = null
		var legacyPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
		var legacyAnimation: Runnable? = null
		var legacyFallback: Runnable? = null
		var legacyPixelCopy: Runnable? = null
		var callbackView: android.view.View? = null

		fun clearFrameCallbacks() {
			startRunnable?.let { callback -> runCatching { mainHandler.removeCallbacks(callback) } }
			startRunnable = null
			frameCommitCallback?.let { callback ->
				frameCommitObserver?.takeIf { it.isAlive }?.let { observer ->
					runCatching { observer.unregisterFrameCommitCallback(callback) }
				}
			}
			frameCommitObserver = null
			frameCommitCallback = null
			legacyPreDrawListener?.let { listener ->
				legacyObserver?.takeIf { it.isAlive }?.let { observer ->
					runCatching { observer.removeOnPreDrawListener(listener) }
				}
			}
			legacyObserver = null
			legacyPreDrawListener = null
			callbackView?.let { view ->
				legacyAnimation?.let { callback -> runCatching { view.removeCallbacks(callback) } }
				legacyFallback?.let { callback -> runCatching { view.removeCallbacks(callback) } }
				legacyPixelCopy?.let { callback -> runCatching { view.removeCallbacks(callback) } }
			}
			legacyAnimation = null
			legacyFallback = null
			legacyPixelCopy = null
			callbackView = null
		}

		fun reject() {
			val completion = ownership.finish(accepted = false) ?: return
			clearFrameCallbacks()
			check(completion.candidate == null)
			onCaptured(null)
		}

		val start = Runnable start@{
			startRunnable = null
			if (
				!ownership.isOpen ||
				expectedBitmapWidth <= 0 ||
				expectedBitmapHeight <= 0 ||
				!canCapture(webView)
			) {
				reject()
				return@start
			}
			val requestEpoch = ++liveCaptureEpoch
			val captureQuality = bitmapQuality
			val startedAt = SystemClock.uptimeMillis()
			fun requestCurrent(): Boolean = ownership.isOpen &&
				requestEpoch == liveCaptureEpoch &&
				captureQuality == bitmapQuality &&
				runCatching(isStillCurrent).getOrDefault(false)

			queryPresentationReceipt(webView, target) initial@{ initialReceipt ->
				if (!requestCurrent() || initialReceipt?.matches(target) != true) {
					reject()
					return@initial
				}
				try {
					webView.evaluateJavascript(
						"JSON.stringify(window.NavicReaderBridge?.pageTurnCaptureGeometry?.() ?? null)"
					) geometry@{ encodedGeometry ->
					val geometry = parseGeometry(encodedGeometry)
					if (!requestCurrent() || geometry == null) {
						reject()
						return@geometry
					}
					val activity = runCatching { webView.context.findActivity() }.getOrNull()
					if (activity == null) {
						reject()
						return@geometry
					}
					val resolvedEnvironment = runCatching {
						val decor = activity.window.decorView
						val location = IntArray(2)
						webView.getLocationInWindow(location)
						val pixelRect = geometry.surfaceRectInWindow(
							webViewWindowLeft = location[0],
							webViewWindowTop = location[1],
							webViewWidth = webView.width,
							webViewHeight = webView.height
						) ?: return@runCatching null
						decor to Rect(
							pixelRect.left,
							pixelRect.top,
							pixelRect.right,
							pixelRect.bottom
						)
					}.getOrNull()
					if (resolvedEnvironment == null) {
						reject()
						return@geometry
					}
					val (decorView, sourceRectInWindow) = resolvedEnvironment
					callbackView = decorView
					fun environmentCurrent(): Boolean = requestCurrent() &&
						readerPageTurnLiveCaptureEnvironmentMatches(
							webView = webView,
							activity = activity,
							decorView = decorView,
							geometry = geometry,
							expectedRect = sourceRectInWindow
						)
					if (!environmentCurrent()) {
						reject()
						return@geometry
					}
					val bitmap = runCatching {
						Bitmap.createBitmap(
							expectedBitmapWidth,
							expectedBitmapHeight,
							Bitmap.Config.ARGB_8888
						)
					}.getOrNull()
					if (bitmap == null) {
						reject()
						return@geometry
					}
					val candidate = ReaderPageTurnCaptureResult(
						bitmap = bitmap,
						sourceRectInWindow = Rect(sourceRectInWindow),
						geometry = geometry,
						elapsedMs = 0L
					)
					if (!ownership.retain(candidate)) {
						bitmap.takeUnless { it.isRecycled }?.recycle()
						return@geometry
					}

					var pixelCopyStarted = false
					fun requestPixelCopy() {
						if (pixelCopyStarted) return
						if (!environmentCurrent()) {
							reject()
							return
						}
						pixelCopyStarted = true
						clearFrameCallbacks()
						if (!ownership.beginExternalWrite()) return
						try {
							PixelCopy.request(
								activity.window,
								sourceRectInWindow,
								bitmap,
								{ copyResult ->
									val copyAccepted = try {
										copyResult == PixelCopy.SUCCESS &&
											environmentCurrent() &&
											ownership.runIfExternalWriteCurrent {
												bitmap.setHasAlpha(false)
												bitmap.setPremultiplied(true)
											}
									} catch (_: Throwable) {
										false
									}
									if (!ownership.endExternalWrite()) return@request
									if (!copyAccepted) {
										reject()
										return@request
									}
									queryPresentationReceipt(webView, target) final@{ finalReceipt ->
										val accepted = environmentCurrent() &&
											readerPageTurnPresentationReceiptAccepted(
												target = target,
												initialReceipt = initialReceipt,
												finalReceipt = finalReceipt,
												foregroundSuccess = true
											)
										val completion = ownership.finish(accepted) ?: return@final
										clearFrameCallbacks()
										val captured = completion.candidate
										if (captured == null || finalReceipt == null) {
											onCaptured(null)
										} else {
											onCaptured(
												ReaderPageTurnLiveCaptureResult(
													captured = captured.copy(
														elapsedMs = SystemClock.uptimeMillis() - startedAt
													),
													acceptedReceipt = finalReceipt
												)
											)
										}
									}
								},
								mainHandler
							)
						} catch (_: Throwable) {
							if (ownership.endExternalWrite()) reject()
						}
					}

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
						val observer = decorView.viewTreeObserver
						if (
							!observer.isAlive ||
							!decorView.isHardwareAccelerated ||
							!webView.isHardwareAccelerated
						) {
							reject()
							return@geometry
						}
						lateinit var committedFrame: Runnable
						committedFrame = Runnable {
							if (frameCommitCallback !== committedFrame) return@Runnable
							runCatching { observer.unregisterFrameCommitCallback(committedFrame) }
							frameCommitObserver = null
							frameCommitCallback = null
							requestPixelCopy()
						}
						frameCommitObserver = observer
						frameCommitCallback = committedFrame
						runCatching { observer.registerFrameCommitCallback(committedFrame) }
							.mapCatching {
								webView.postInvalidateOnAnimation()
								decorView.postInvalidateOnAnimation()
							}
							.onFailure { reject() }
					} else {
						var attempts = 0
						lateinit var scheduleLegacyFrame: () -> Unit
						scheduleLegacyFrame = schedule@{
							if (!environmentCurrent()) {
								reject()
								return@schedule
							}
							if (attempts >= LegacyLiveCaptureMaximumFrameAttempts) {
								reject()
								return@schedule
							}
							attempts += 1
							val animation = Runnable animation@{
								legacyAnimation = null
								if (!environmentCurrent()) {
									reject()
									return@animation
								}
								val observer = decorView.viewTreeObserver
								if (!observer.isAlive) {
									reject()
									return@animation
								}
								var observed = false
								lateinit var listener: ViewTreeObserver.OnPreDrawListener
								listener = ViewTreeObserver.OnPreDrawListener {
									if (legacyPreDrawListener !== listener) return@OnPreDrawListener true
									observed = true
									observer.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
									legacyObserver = null
									legacyPreDrawListener = null
									val pixelCopy = Runnable {
										legacyPixelCopy = null
										requestPixelCopy()
									}
									legacyPixelCopy = pixelCopy
									runCatching { decorView.postOnAnimation(pixelCopy) }
										.onFailure { reject() }
									true
								}
								legacyObserver = observer
								legacyPreDrawListener = listener
								val fallback = Runnable {
									legacyFallback = null
									if (observed) return@Runnable
									observer.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
									legacyObserver = null
									legacyPreDrawListener = null
									scheduleLegacyFrame()
								}
								legacyFallback = fallback
								runCatching { observer.addOnPreDrawListener(listener) }
									.mapCatching {
										webView.postInvalidateOnAnimation()
										decorView.postInvalidateOnAnimation()
										decorView.postOnAnimation(fallback)
									}
									.onFailure { reject() }
							}
							legacyAnimation = animation
							runCatching { decorView.postOnAnimation(animation) }
								.onFailure { reject() }
						}
						scheduleLegacyFrame()
					}
				}
				} catch (_: Throwable) {
					reject()
				}
			}
		}
		startRunnable = start
		if (Looper.myLooper() == Looper.getMainLooper()) {
			start.run()
		} else if (!mainHandler.post(start)) {
			reject()
		}
		return ReaderPageRelocationContentValidationHandle {
			val cancelled = ownership.cancel()
			if (cancelled) {
				if (Looper.myLooper() == Looper.getMainLooper()) clearFrameCallbacks()
				else mainHandler.post { clearFrameCallbacks() }
			}
			cancelled
		}
	}

	private fun readerPageTurnLiveCaptureEnvironmentMatches(
		webView: WebView,
		activity: Activity,
		decorView: android.view.View,
		geometry: ReaderPageTurnCaptureGeometry,
		expectedRect: Rect
	): Boolean = runCatching {
		val location = IntArray(2)
		if (
			activity.isFinishing ||
			activity.isDestroyed ||
			activity.window.decorView !== decorView ||
			webView.rootView !== decorView
		) {
			return@runCatching false
		}
		webView.getLocationInWindow(location)
		val currentRect = geometry.surfaceRectInWindow(
			webViewWindowLeft = location[0],
			webViewWindowTop = location[1],
			webViewWidth = webView.width,
			webViewHeight = webView.height
		)?.let { Rect(it.left, it.top, it.right, it.bottom) }
		readerPageTurnLiveCapturePreflightAccepted(
			activityAvailable = true,
			webViewAttached = webView.isAttachedToWindow,
			decorAttached = decorView.isAttachedToWindow,
			isStillCurrent = true,
			geometryMatches = currentRect == expectedRect,
			left = expectedRect.left,
			top = expectedRect.top,
			right = expectedRect.right,
			bottom = expectedRect.bottom,
			decorWidth = decorView.width,
			decorHeight = decorView.height
		)
	}.getOrDefault(false)

	fun confirmLivePresentationReceipt(
		webView: WebView,
		target: ReaderPageTurnPresentationTarget.Live,
		acceptedReceipt: ReaderPageTurnPresentationReceipt,
		isStillCurrent: () -> Boolean,
		onReceipt: (ReaderPageTurnPresentationReceipt?) -> Unit
	): ReaderPageRelocationContentValidationHandle {
		val gate = ReaderPageTurnLiveCaptureOwnership<Unit> { }
		check(gate.retain(Unit))
		val query = Runnable {
			if (
				!gate.isOpen ||
				!webView.isAttachedToWindow ||
				!runCatching(isStillCurrent).getOrDefault(false) ||
				!acceptedReceipt.matches(target)
			) {
				if (gate.finish(accepted = true) != null) onReceipt(null)
				return@Runnable
			}
			queryPresentationReceipt(webView, target) { currentReceipt ->
				val completion = gate.finish(accepted = true) ?: return@queryPresentationReceipt
				check(completion.candidate === Unit)
				onReceipt(
					currentReceipt.takeIf {
						runCatching(isStillCurrent).getOrDefault(false) &&
							webView.isAttachedToWindow &&
							it == acceptedReceipt &&
							it.matches(target)
					}
				)
			}
		}
		if (Looper.myLooper() == Looper.getMainLooper()) query.run()
		else if (!mainHandler.post(query) && gate.finish(accepted = true) != null) onReceipt(null)
		return ReaderPageRelocationContentValidationHandle {
			val cancelled = gate.cancel()
			if (cancelled) mainHandler.removeCallbacks(query)
			cancelled
		}
	}

	suspend fun captureSurfaceAwait(webView: WebView): ReaderPageTurnCaptureResult? =
		suspendCancellableCoroutine { continuation ->
			captureSurface(webView) { result ->
				if (continuation.isActive) {
					continuation.resume(result)
				} else {
					result?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				}
			}
		}

	private fun queryPresentationReceipt(
		webView: WebView,
		target: ReaderPageTurnPresentationTarget,
		onReceipt: (ReaderPageTurnPresentationReceipt?) -> Unit
	) {
		if (!webView.isAttachedToWindow) {
			onReceipt(null)
			return
		}
		val getter = when (target) {
			is ReaderPageTurnPresentationTarget.Preview ->
				"pageTurnPreviewPresentationReceipt"
			is ReaderPageTurnPresentationTarget.Live ->
				"pageTurnLivePresentationReceipt"
		}
		try {
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?.$getter?.() ?? null)"
			) { encodedReceipt ->
				onReceipt(readerPageTurnPresentationReceipt(encodedReceipt))
			}
		} catch (_: Throwable) {
			onReceipt(null)
		}
	}

	private fun canCapture(webView: WebView): Boolean =
		isAvailable &&
			webView.isAttachedToWindow &&
			webView.width > 0 &&
			webView.height > 0

	private fun capture(
		webView: WebView,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		allowStableLowContrast: Boolean = false,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?
	) {
		if (!canCapture(webView)) {
			onCaptured(null)
			return
		}
		val startedAt = SystemClock.uptimeMillis()
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnCaptureGeometry?.() ?? null)"
		) { encodedGeometry ->
			val geometry = parseGeometry(encodedGeometry)
			if (geometry == null) {
				Logger.i(
					ReaderPageTurnBitmapSourceTag,
					"Page-turn capture unavailable reason=geometry-unavailable"
				)
				onCaptured(null)
				return@evaluateJavascript
			}
			captureResolvedGeometry(
				webView = webView,
				geometry = geometry,
				startedAt = startedAt,
				onCaptured = onCaptured,
				allowStableLowContrast = allowStableLowContrast,
				resolveRect = resolveRect
			)
		}
	}

	private fun captureResolvedGeometry(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		allowStableLowContrast: Boolean = false,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?
	) {
		if (!canCapture(webView)) {
			onCaptured(null)
			return
		}
		captureResolvedGeometry(
			webView = webView,
			geometry = geometry,
			startedAt = SystemClock.uptimeMillis(),
			onCaptured = onCaptured,
			allowStableLowContrast = allowStableLowContrast,
			resolveRect = resolveRect
		)
	}

	private fun captureResolvedGeometry(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		startedAt: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		allowStableLowContrast: Boolean,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?
	) {
		if (!webView.isAttachedToWindow) {
			onCaptured(null)
			return
		}
		val requestId = ++visualStateRequestId
		webView.postVisualStateCallback(requestId, object : WebView.VisualStateCallback() {
			override fun onComplete(requestId: Long) {
				if (!webView.isAttachedToWindow) {
					onCaptured(null)
					return
				}
				webView.postOnAnimation {
					captureVisualState(
						webView = webView,
						geometry = geometry,
						startedAt = startedAt,
						onCaptured = onCaptured,
						resolveRect = resolveRect,
						allowStableLowContrast = allowStableLowContrast
					)
				}
			}
		})
	}

	private fun captureVisualState(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		startedAt: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit,
		resolveRect: (ReaderPageTurnCaptureGeometry, IntArray) -> paige.navic.reader.ReaderPageTurnPixelRect?,
		allowStableLowContrast: Boolean,
		previousRejectedSignature: ReaderPageTurnRejectedForegroundSignature? = null
	) {
		if (!webView.isAttachedToWindow) {
			onCaptured(null)
			return
		}
		val location = IntArray(2)
		webView.getLocationInWindow(location)
		val pixelRect = resolveRect(geometry, location)
		if (pixelRect == null) {
			onCaptured(null)
			return
		}
		val sourceRect = Rect(pixelRect.left, pixelRect.top, pixelRect.right, pixelRect.bottom)
		val bitmap = runCatching {
			Bitmap.createBitmap(
				readerPageTurnAnimationBitmapDimension(pixelRect.width, bitmapQuality),
				readerPageTurnAnimationBitmapDimension(pixelRect.height, bitmapQuality),
				Bitmap.Config.ARGB_8888
			)
		}.getOrElse { error ->
			Logger.w(ReaderPageTurnBitmapSourceTag, "Page-turn bitmap allocation failed", error)
			onCaptured(null)
			return
		}
		val backgroundColor = readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb)
		bitmap.eraseColor(backgroundColor)
		val drawn = drawWebViewIntoBitmap(
			webView,
			location,
			sourceRect,
			bitmap,
			backgroundColor
		)
		val foreground = if (drawn) bitmap.analyzeRenderableForeground() else null
		val rejectedSignature = foreground?.settlementSignature(allowStableLowContrast)
		val settledRejected = previousRejectedSignature != null &&
			rejectedSignature == previousRejectedSignature
		if (foreground?.renderable == true || settledRejected) {
			bitmap.setHasAlpha(false)
			bitmap.setPremultiplied(true)
			val elapsedMs = SystemClock.uptimeMillis() - startedAt
			Logger.i(
				ReaderPageTurnBitmapSourceTag,
				"Page-turn capture success method=webview-draw rect=$sourceRect " +
					"bitmap=${bitmap.width}x${bitmap.height} " +
					"settledRejected=$settledRejected elapsedMs=$elapsedMs"
			)
			onCaptured(ReaderPageTurnCaptureResult(bitmap, sourceRect, geometry, elapsedMs))
			return
		}

		bitmap.recycle()
		if (rejectedSignature != null && previousRejectedSignature == null) {
			Logger.i(
				ReaderPageTurnBitmapSourceTag,
				"Page-turn capture awaiting stable ${rejectedSignature.kind.logValue} surface " +
					"rect=$sourceRect samples=${foreground.sampleCount} " +
					"range=${foreground.luminanceRange} " +
					"distant=${foreground.distantSampleCount} " +
					"required=${foreground.requiredDistantSampleCount}"
			)
			webView.postOnAnimation {
				captureVisualState(
					webView = webView,
					geometry = geometry,
					startedAt = startedAt,
					onCaptured = onCaptured,
					resolveRect = resolveRect,
					allowStableLowContrast = allowStableLowContrast,
					previousRejectedSignature = rejectedSignature
				)
			}
			return
		}
		if (previousRejectedSignature != null) {
			Logger.i(
				ReaderPageTurnBitmapSourceTag,
				"Page-turn rejected surface changed before second observation"
			)
		}

		Logger.i(
			ReaderPageTurnBitmapSourceTag,
			"Page-turn capture rejected unpainted surface rect=$sourceRect " +
				"samples=${foreground?.sampleCount ?: 0} " +
				"range=${foreground?.luminanceRange ?: 0} " +
				"distant=${foreground?.distantSampleCount ?: 0} " +
				"required=${foreground?.requiredDistantSampleCount ?: 0}"
		)
		onCaptured(null)
	}

	internal fun parseGeometry(encoded: String?): ReaderPageTurnCaptureGeometry? =
		readerPageTurnCaptureGeometry(encoded)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
	else -> null
}

internal fun <T : Any> readerPageTurnPresentedSurfaceCandidate(
	target: ReaderPageTurnPresentationTarget,
	initialReceipt: ReaderPageTurnPresentationReceipt?,
	finalReceipt: ReaderPageTurnPresentationReceipt?,
	candidate: T?,
	foregroundSuccess: Boolean,
	isStillCurrent: Boolean,
	recycle: (T) -> Unit
): T? {
	val accepted = readerPageTurnPresentationReceiptAccepted(
		target = target,
		initialReceipt = initialReceipt,
		finalReceipt = finalReceipt,
		foregroundSuccess = foregroundSuccess
	) && isStillCurrent
	if (!accepted) candidate?.let(recycle)
	return candidate?.takeIf { accepted }
}

internal fun readerPageTurnCaptureGeometry(
	encoded: String?
): ReaderPageTurnCaptureGeometry? = runCatching {
	val raw = encoded.orEmpty().trim()
	val firstPass = Json.parseToJsonElement(raw)
	val json = if (raw.startsWith('"')) {
		Json.parseToJsonElement(firstPass.jsonPrimitive.contentOrNull.orEmpty()).jsonObject
	} else {
		firstPass.jsonObject
	}
	val viewportWidth = json["viewportWidth"]?.jsonPrimitive?.doubleOrNull ?: return null
	val viewportHeight = json["viewportHeight"]?.jsonPrimitive?.doubleOrNull ?: return null
	if (!viewportWidth.isFinite() || !viewportHeight.isFinite()) return null
	val pages = json["pages"]?.jsonArray.orEmpty().mapNotNull { element ->
		val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
		val left = item["left"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
		val top = item["top"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
		val width = item["width"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
		val height = item["height"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
		ReaderPageTurnPageRect(
			role = when (item["role"]?.jsonPrimitive?.contentOrNull) {
				"left" -> ReaderPageTurnPageRole.Left
				"right" -> ReaderPageTurnPageRole.Right
				else -> ReaderPageTurnPageRole.Full
			},
			left = left,
			top = top,
			width = width,
			height = height
		)
	}
	ReaderPageTurnCaptureGeometry(
		viewportWidth = viewportWidth,
		viewportHeight = viewportHeight,
		mode = if (json["mode"]?.jsonPrimitive?.contentOrNull == "spread") {
			ReaderPageTurnLayoutMode.Spread
		} else {
			ReaderPageTurnLayoutMode.Single
		},
		pages = pages,
		reverseFaceColorArgb = json["reverseFaceColorArgb"]?.jsonPrimitive?.longOrNull
	)
}.getOrNull()

private fun drawWebViewIntoBitmap(
	webView: WebView,
	webViewLocationInWindow: IntArray,
	sourceRectInWindow: Rect,
	bitmap: Bitmap,
	backgroundColorArgb: Int
): Boolean = runCatching {
	bitmap.eraseColor(backgroundColorArgb)
	val canvas = Canvas(bitmap)
	val checkpoint = canvas.save()
	canvas.scale(
		bitmap.width.toFloat() / sourceRectInWindow.width(),
		bitmap.height.toFloat() / sourceRectInWindow.height()
	)
	canvas.translate(
		webViewLocationInWindow[0] - sourceRectInWindow.left.toFloat(),
		webViewLocationInWindow[1] - sourceRectInWindow.top.toFloat()
	)
	webView.draw(canvas)
	canvas.restoreToCount(checkpoint)
	true
}.getOrElse { failure ->
	Logger.w(
		ReaderPageTurnBitmapSourceTag,
		"Page-turn WebView draw fallback failed failureClass=${failure::class.simpleName ?: "unknown"}"
	)
	false
}

private enum class ReaderPageTurnRejectedForegroundKind(val logValue: String) {
	Sparse("sparse"),
	LowContrast("low-contrast")
}

private data class ReaderPageTurnRejectedForegroundSignature(
	val kind: ReaderPageTurnRejectedForegroundKind,
	val pixelHash: Int
)

private data class ReaderPageTurnForegroundAnalysis(
	val sampleCount: Int,
	val luminanceRange: Int,
	val distantSampleCount: Int,
	val requiredDistantSampleCount: Int,
	val renderable: Boolean,
	val sparseSignature: Int?,
	val lowContrastSignature: Int?
) {
	fun settlementSignature(
		allowStableLowContrast: Boolean
	): ReaderPageTurnRejectedForegroundSignature? = when {
		sparseSignature != null -> ReaderPageTurnRejectedForegroundSignature(
			ReaderPageTurnRejectedForegroundKind.Sparse,
			sparseSignature
		)
		allowStableLowContrast && lowContrastSignature != null ->
			ReaderPageTurnRejectedForegroundSignature(
				ReaderPageTurnRejectedForegroundKind.LowContrast,
				lowContrastSignature
			)
		else -> null
	}
}

private fun Bitmap.analyzeRenderableForeground(): ReaderPageTurnForegroundAnalysis =
	readerPageTurnCaptureForegroundAnalysis(width, height) { x, y -> getPixel(x, y) }

private fun readerPageTurnCaptureForegroundAnalysis(
	width: Int,
	height: Int,
	pixelAt: (Int, Int) -> Int
): ReaderPageTurnForegroundAnalysis {
	val primary = readerPageTurnSampledForegroundAnalysis(
		width = width,
		height = height,
		samplePhase = PageTurnCapturePrimarySamplePhase,
		pixelAt = pixelAt
	)
	if (primary.renderable) return primary
	val shifted = readerPageTurnSampledForegroundAnalysis(
		width = width,
		height = height,
		samplePhase = PageTurnCaptureShiftedSamplePhase,
		pixelAt = pixelAt
	)
	if (shifted.renderable) return shifted
	return when {
		primary.sparseSignature != null && shifted.sparseSignature == null -> primary
		shifted.sparseSignature != null && primary.sparseSignature == null -> shifted
		primary.lowContrastSignature != null && shifted.lowContrastSignature == null -> primary
		shifted.lowContrastSignature != null && primary.lowContrastSignature == null -> shifted
		shifted.luminanceRange > primary.luminanceRange -> shifted
		shifted.luminanceRange == primary.luminanceRange &&
			shifted.distantSampleCount > primary.distantSampleCount -> shifted
		else -> primary
	}
}

private fun readerPageTurnSampledForegroundAnalysis(
	width: Int,
	height: Int,
	samplePhase: Float,
	pixelAt: (Int, Int) -> Int
): ReaderPageTurnForegroundAnalysis {
	val columns = PageTurnCaptureSampleColumns.coerceAtMost(width)
	val rows = PageTurnCaptureSampleRows.coerceAtMost(height)
	if (columns <= 0 || rows <= 0) {
		return ReaderPageTurnForegroundAnalysis(0, 0, 0, 0, false, null, null)
	}
	val pixels = ArrayList<Int>(columns * rows)
	for (row in 0 until rows) {
		val y = ((row + samplePhase) * height / rows).toInt().coerceIn(0, height - 1)
		if (y < height * 0.06f || y > height * 0.94f) continue
		for (column in 0 until columns) {
			val xFraction = (column + samplePhase) / columns
			if (xFraction < 0.06f || xFraction > 0.94f || xFraction in 0.47f..0.53f) continue
			val x = ((column + samplePhase) * width / columns).toInt().coerceIn(0, width - 1)
			pixels += pixelAt(x, y)
		}
	}
	return readerPageTurnForegroundAnalysis(pixels.toIntArray())
}

internal fun readerPageTurnCaptureContainsForeground(
	width: Int,
	height: Int,
	pixelAt: (Int, Int) -> Int
): Boolean = readerPageTurnCaptureForegroundAnalysis(width, height, pixelAt).renderable

private fun readerPageTurnForegroundAnalysis(pixels: IntArray): ReaderPageTurnForegroundAnalysis {
	if (pixels.isEmpty()) return ReaderPageTurnForegroundAnalysis(0, 0, 0, 0, false, null, null)
	val luminance = IntArray(pixels.size) { index ->
		val color = pixels[index]
		val red = color ushr 16 and 0xff
		val green = color ushr 8 and 0xff
		val blue = color and 0xff
		(red * 54 + green * 183 + blue * 19) ushr 8
	}
	val sorted = luminance.sortedArray()
	val baseline = sorted[sorted.size / 2]
	val range = sorted.last() - sorted.first()
	val requiredDistantSamples = maxOf(
		PageTurnCaptureMinimumForegroundSamples,
		pixels.size / PageTurnCaptureForegroundSampleDivisor
	)
	val distantSamples = luminance.count { value ->
		abs(value - baseline) >= PageTurnCaptureForegroundDistance
	}
	val lowContrastSamples = luminance.count { value ->
		abs(value - baseline) >= PageTurnCaptureLowContrastForegroundDistance
	}
	val renderable =
		range >= PageTurnCaptureMinimumLuminanceRange &&
			distantSamples >= requiredDistantSamples
	val sparseSignature = if (
		!renderable &&
		range >= PageTurnCaptureMinimumLuminanceRange &&
		distantSamples > 0
	) {
		luminance.contentHashCode()
	} else {
		null
	}
	val lowContrastSignature = if (
		!renderable &&
		range in PageTurnCaptureMinimumLowContrastLuminanceRange until
			PageTurnCaptureMinimumLuminanceRange &&
		lowContrastSamples >= requiredDistantSamples
	) {
		luminance.contentHashCode()
	} else {
		null
	}
	return ReaderPageTurnForegroundAnalysis(
		sampleCount = pixels.size,
		luminanceRange = range,
		distantSampleCount = distantSamples,
		requiredDistantSampleCount = requiredDistantSamples,
		renderable = renderable,
		sparseSignature = sparseSignature,
		lowContrastSignature = lowContrastSignature
	)
}

internal fun readerPageTurnPixelsContainForeground(pixels: IntArray): Boolean =
	readerPageTurnForegroundAnalysis(pixels).renderable

internal fun readerPageTurnRejectedForegroundSettled(
	previousPixels: IntArray,
	currentPixels: IntArray,
	allowStableLowContrast: Boolean
): Boolean {
	val previous = readerPageTurnForegroundAnalysis(previousPixels)
		.settlementSignature(allowStableLowContrast)
	return previous != null &&
		readerPageTurnForegroundAnalysis(currentPixels)
			.settlementSignature(allowStableLowContrast) == previous
}

internal fun readerPageTurnSparseForegroundSettled(
	previousPixels: IntArray,
	currentPixels: IntArray
): Boolean {
	val previous = readerPageTurnForegroundAnalysis(previousPixels).sparseSignature
	return previous != null &&
		readerPageTurnForegroundAnalysis(currentPixels).sparseSignature == previous
}

internal fun readerPageTurnLowContrastForegroundSettled(
	previousPixels: IntArray,
	currentPixels: IntArray
): Boolean {
	val previous = readerPageTurnForegroundAnalysis(previousPixels).lowContrastSignature
	return previous != null &&
		readerPageTurnForegroundAnalysis(currentPixels).lowContrastSignature == previous
}

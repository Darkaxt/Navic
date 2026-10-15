package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import paige.navic.reader.ReaderWhispersyncAnchorReceipt

private const val ReaderPageInlineRasterShieldTimeoutMillis = 1_000L

internal class ReaderPageInlineRasterShield(
	private val host: ViewGroup,
	private val onOwnershipMutated: () -> Unit = {},
	private val onPresentationOwnershipStarted: () -> Unit = {}
) {
	private val rasterView = ImageView(host.context).apply {
		scaleType = ImageView.ScaleType.FIT_XY
	}
	private val overlayView = ImageView(host.context).apply {
		scaleType = ImageView.ScaleType.FIT_XY
		visibility = View.GONE
	}
	val view = FrameLayout(host.context).apply {
		isClickable = false
		isFocusable = false
		importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		visibility = View.GONE
		addView(
			rasterView,
			FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
		)
		addView(
			overlayView,
			FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
		)
	}

	val pendingCallbackLimit: Int = 1

	private var nextRequest = 0L
	private var activeRequest: Long? = null
	private var timeout: Runnable? = null
	private var firstFrame: Runnable? = null
	private var committedFrame: Runnable? = null
	private var activeFadeRequest: Long? = null
	private var ownedSnapshot: ReaderPageSlideSnapshot? = null
	private var ownedWhispersyncOverlay: Bitmap? = null
	private var presentationCallback: ((Boolean) -> Unit)? = null
	private var fadeCallback: ((Boolean) -> Unit)? = null

	fun present(
		snapshot: ReaderPageSlideSnapshot,
		onPresented: (Boolean) -> Unit
	) {
		dismiss()
		if (
			!view.isAttachedToWindow ||
			snapshot.bitmap.isRecycled ||
			!applySnapshotBounds(snapshot)
		) {
			snapshot.release()
			onPresented(false)
			return
		}

		val request = Math.incrementExact(nextRequest).also { nextRequest = it }
		activeRequest = request
		ownedSnapshot = snapshot
		presentationCallback = onPresented
		rasterView.setImageBitmap(snapshot.bitmap)
		view.alpha = 1f
		view.visibility = View.VISIBLE
		onOwnershipMutated()

		val timeoutAction = Runnable { complete(request, false) }
		timeout = timeoutAction
		view.postDelayed(timeoutAction, ReaderPageInlineRasterShieldTimeoutMillis)
		awaitCommittedFrame(request)
		onPresentationOwnershipStarted()
	}

	fun fadeOut(
		durationMillis: Long,
		onExposedFrameCommitted: (Boolean) -> Unit
	) {
		require(durationMillis >= 0L)
		if (!ownsPresentation() || !host.isAttachedToWindow) {
			onExposedFrameCommitted(false)
			return
		}
		cancelFade()
		val request = Math.incrementExact(nextRequest).also { nextRequest = it }
		activeFadeRequest = request
		fadeCallback = onExposedFrameCommitted
		onOwnershipMutated()
		val timeoutDelay = if (
			durationMillis > Long.MAX_VALUE - ReaderPageInlineRasterShieldTimeoutMillis
		) {
			Long.MAX_VALUE
		} else {
			durationMillis + ReaderPageInlineRasterShieldTimeoutMillis
		}
		val timeoutAction = Runnable { completeFade(request, false) }
		timeout = timeoutAction
		host.postDelayed(timeoutAction, timeoutDelay)
		view.animate()
			.alpha(0f)
			.setDuration(durationMillis)
			.withEndAction {
				if (activeFadeRequest != request) return@withEndAction
				awaitExposedWebViewFrame(request)
			}
			.start()
	}

	fun dismiss(legacyConsequencesAdmitted: () -> Boolean = { true }) {
		// Move only this invocation's resource ownership out before cancellation can
		// synchronously present a successor. Release is independent of visual admission.
		val snapshot = ownedSnapshot
		val overlay = ownedWhispersyncOverlay
		val request = nextRequest
		val fadeRequest = activeFadeRequest
		ownedSnapshot = null
		ownedWhispersyncOverlay = null
		val capturedPresentationIsCurrent = { nextRequest == request && ownedSnapshot == null }
		// A recipient failure cannot orphan the resources already moved into this
		// invocation. Finish each independent predecessor cleanup before rethrowing.
		var failure: Throwable? = null
		fun finishCapturedCleanup(action: () -> Unit) {
			try {
				action()
			} catch (cleanupFailure: Throwable) {
				val originalFailure = failure
				if (originalFailure == null) failure = cleanupFailure
				else if (originalFailure !== cleanupFailure) originalFailure.addSuppressed(cleanupFailure)
			}
		}
		finishCapturedCleanup { cancelPendingPresentation() }
		finishCapturedCleanup {
			if (activeFadeRequest == fadeRequest) {
				cancelFade(
					legacyConsequencesAdmitted,
					capturedPresentationIsCurrent,
					ownsCapturedPresentation = {
						snapshot?.bitmap?.isRecycled == false && view.visibility == View.VISIBLE
					}
				)
			}
		}
		finishCapturedCleanup { clearPresentation(snapshot, overlay, request, legacyConsequencesAdmitted) }
		failure?.let { throw it }
	}

	fun ownsPresentation(): Boolean =
		ownedSnapshot?.bitmap?.isRecycled == false &&
			view.visibility == View.VISIBLE

	val hasWhispersyncOverlayPresentation: Boolean
		get() = ownedWhispersyncOverlay?.isRecycled == false && overlayView.visibility == View.VISIBLE

	fun setWhispersyncOverlay(
		receipt: ReaderWhispersyncAnchorReceipt?,
		colorArgb: Int
	): Boolean {
		clearWhispersyncOverlay()
		if (receipt == null) return true
		val snapshot = ownedSnapshot
			?.takeIf { candidate ->
				ownsPresentation() &&
					candidate.key.visualPageIndex == receipt.visualPageOrdinal &&
					!candidate.bitmap.isRecycled
			}
			?: return false
		val overlay = readerWhispersyncViewportHighlightMask(
			receipt = receipt,
			bitmapWidth = snapshot.bitmap.width,
			bitmapHeight = snapshot.bitmap.height,
			colorArgb = colorArgb
		) ?: return false
		ownedWhispersyncOverlay = overlay
		overlayView.setImageBitmap(overlay)
		overlayView.visibility = View.VISIBLE
		overlayView.invalidate()
		return true
	}

	fun pendingCallbackCount(): Int =
		(if (activeRequest != null) 1 else 0) +
			(if (activeFadeRequest != null) 1 else 0)

	private fun applySnapshotBounds(snapshot: ReaderPageSlideSnapshot): Boolean {
		val rect = snapshot.surfaceRectInWindow
		if (rect.width() <= 0 || rect.height() <= 0) return false
		val hostLocation = IntArray(2)
		host.getLocationInWindow(hostLocation)
		val left = rect.left - hostLocation[0]
		val top = rect.top - hostLocation[1]
		val right = left + rect.width()
		val bottom = top + rect.height()
		if (
			left < 0 ||
			top < 0 ||
			right > host.width ||
			bottom > host.height
		) {
			return false
		}
		val params = (view.layoutParams as? FrameLayout.LayoutParams)
			?: FrameLayout.LayoutParams(rect.width(), rect.height())
		params.width = rect.width()
		params.height = rect.height()
		params.leftMargin = left
		params.topMargin = top
		params.gravity = Gravity.TOP or Gravity.START
		view.layoutParams = params
		return true
	}

	private fun awaitCommittedFrame(request: Long) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val observer = view.viewTreeObserver
			if (!observer.isAlive) {
				complete(request, false)
				return
			}
			observer.registerFrameCommitCallback {
				if (activeRequest != request) return@registerFrameCommitCallback
				awaitDisplayLatch(request)
			}
			view.postInvalidateOnAnimation()
			return
		}

		awaitDisplayLatch(request)
		view.postInvalidateOnAnimation()
	}

	private fun awaitDisplayLatch(request: Long) {
		val first = Runnable {
			if (activeRequest != request) return@Runnable
			firstFrame = null
			val committed = Runnable { complete(request, true) }
			committedFrame = committed
			view.postOnAnimation(committed)
		}
		firstFrame = first
		view.postOnAnimation(first)
	}

	private fun awaitExposedWebViewFrame(request: Long) {
		if (
			activeFadeRequest != request ||
			!host.isAttachedToWindow ||
			!ownsPresentation()
		) {
			completeFade(request, false)
			return
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val observer = host.viewTreeObserver
			if (!observer.isAlive) {
				completeFade(request, false)
				return
			}
			observer.registerFrameCommitCallback {
				completeFade(request, true)
			}
			host.invalidate()
			return
		}

		val first = Runnable {
			if (activeFadeRequest != request) return@Runnable
			firstFrame = null
			val committed = Runnable { completeFade(request, true) }
			committedFrame = committed
			host.postOnAnimation(committed)
		}
		firstFrame = first
		host.postOnAnimation(first)
		host.invalidate()
	}

	private fun completeFade(request: Long, exposedFrameCommitted: Boolean) {
		if (activeFadeRequest != request) return
		firstFrame?.let(host::removeCallbacks)
		committedFrame?.let(host::removeCallbacks)
		timeout?.let(host::removeCallbacks)
		firstFrame = null
		committedFrame = null
		timeout = null
		activeFadeRequest = null
		val callback = fadeCallback
		fadeCallback = null
		val effectiveCommit =
			exposedFrameCommitted &&
				host.isAttachedToWindow &&
				view.isAttachedToWindow &&
				ownsPresentation()
		if (!effectiveCommit) {
			view.animate().cancel()
			if (ownsPresentation()) view.alpha = 1f
		}
		onOwnershipMutated()
		callback?.invoke(effectiveCommit)
		if (effectiveCommit && ownsPresentation() && view.alpha == 0f) {
			view.alpha = 1f
			onOwnershipMutated()
		}
	}

	private fun cancelPendingPresentation() {
		if (activeRequest == null) {
			check(presentationCallback == null)
			return
		}
		val first = firstFrame
		val committed = committedFrame
		val timeoutAction = timeout
		val callback = presentationCallback
		firstFrame = null
		committedFrame = null
		timeout = null
		activeRequest = null
		presentationCallback = null
		first?.let { view.removeCallbacks(it); host.removeCallbacks(it) }
		committed?.let { view.removeCallbacks(it); host.removeCallbacks(it) }
		timeoutAction?.let { view.removeCallbacks(it); host.removeCallbacks(it) }
		callback?.invoke(false)
		onOwnershipMutated()
	}

	private fun cancelFade(
		legacyConsequencesAdmitted: () -> Boolean = { true },
		capturedPresentationIsCurrent: () -> Boolean = { true },
		ownsCapturedPresentation: () -> Boolean = ::ownsPresentation
	) {
		if (activeFadeRequest == null) {
			check(fadeCallback == null)
			return
		}
		val first = firstFrame
		val committed = committedFrame
		val timeoutAction = timeout
		val callback = fadeCallback
		firstFrame = null
		committedFrame = null
		timeout = null
		activeFadeRequest = null
		fadeCallback = null
		first?.let(host::removeCallbacks)
		committed?.let(host::removeCallbacks)
		timeoutAction?.let(host::removeCallbacks)
		val animation = view.animate()
		if (capturedPresentationIsCurrent()) animation.cancel()
		val ownsPresentation = ownsCapturedPresentation()
		if (capturedPresentationIsCurrent() && legacyConsequencesAdmitted() && ownsPresentation) view.alpha = 1f
		onOwnershipMutated()
		callback?.invoke(false)
	}

	private fun complete(request: Long, presented: Boolean) {
		if (activeRequest != request) return
		firstFrame?.let(view::removeCallbacks)
		committedFrame?.let(view::removeCallbacks)
		timeout?.let(view::removeCallbacks)
		firstFrame = null
		committedFrame = null
		timeout = null
		activeRequest = null
		val callback = presentationCallback
		presentationCallback = null
		val effectivePresentation =
			presented &&
				view.isAttachedToWindow &&
				view.isShown &&
				ownedSnapshot?.bitmap?.isRecycled == false
		if (!effectivePresentation) clearPresentation()
		onOwnershipMutated()
		callback?.invoke(effectivePresentation)
	}

	private fun clearWhispersyncOverlay() {
		overlayView.setImageDrawable(null)
		overlayView.visibility = View.GONE
		ownedWhispersyncOverlay?.takeUnless(Bitmap::isRecycled)?.recycle()
		ownedWhispersyncOverlay = null
	}

	private fun clearPresentation(
		snapshot: ReaderPageSlideSnapshot? = ownedSnapshot,
		overlay: Bitmap? = ownedWhispersyncOverlay,
		request: Long = nextRequest,
		legacyConsequencesAdmitted: () -> Boolean = { true }
	) {
		val hadPresentation = snapshot != null || view.visibility != View.GONE
		if (ownedSnapshot === snapshot) ownedSnapshot = null
		if (ownedWhispersyncOverlay === overlay) ownedWhispersyncOverlay = null
		val capturedPresentationIsCurrent = { nextRequest == request && ownedSnapshot == null }
		clearWhispersyncOverlay(overlay, request, legacyConsequencesAdmitted)
		val drawable = rasterView.drawable as? android.graphics.drawable.BitmapDrawable
		if (capturedPresentationIsCurrent() && (snapshot == null || drawable?.bitmap === snapshot.bitmap)) {
			// Sever only the obsolete snapshot's borrowing reference, not visual authority.
			rasterView.setImageDrawable(null)
		}
		if (capturedPresentationIsCurrent() && legacyConsequencesAdmitted()) view.alpha = 1f
		if (capturedPresentationIsCurrent() && legacyConsequencesAdmitted()) view.visibility = View.GONE
		snapshot?.release()
		if (hadPresentation) onOwnershipMutated()
	}

	private fun clearWhispersyncOverlay(
		overlay: Bitmap?, request: Long, legacyConsequencesAdmitted: () -> Boolean
	) {
		val drawable = overlayView.drawable as? android.graphics.drawable.BitmapDrawable
		val capturedOverlayIsCurrent = { nextRequest == request && ownedWhispersyncOverlay == null }
		if (capturedOverlayIsCurrent() && (overlay == null || drawable?.bitmap === overlay)) {
			overlayView.setImageDrawable(null)
		}
		if (capturedOverlayIsCurrent() && legacyConsequencesAdmitted()) overlayView.visibility = View.GONE
		overlay?.takeUnless(Bitmap::isRecycled)?.recycle()
	}
}

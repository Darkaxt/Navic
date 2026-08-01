package paige.navic.ui.screens.reader

import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

private const val ReaderPageInlineRasterShieldTimeoutMillis = 1_000L

internal class ReaderPageInlineRasterShield(
	private val host: ViewGroup,
	private val onOwnershipMutated: () -> Unit = {},
	private val onPresentationOwnershipStarted: () -> Unit = {}
) {
	val view = ImageView(host.context).apply {
		scaleType = ImageView.ScaleType.FIT_XY
		isClickable = false
		isFocusable = false
		importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		visibility = View.GONE
	}

	val pendingCallbackLimit: Int = 1

	private var nextRequest = 0L
	private var activeRequest: Long? = null
	private var timeout: Runnable? = null
	private var firstFrame: Runnable? = null
	private var committedFrame: Runnable? = null
	private var activeFadeRequest: Long? = null
	private var ownedSnapshot: ReaderPageSlideSnapshot? = null
	private var presentationCallback: ((Boolean) -> Unit)? = null

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
		view.setImageBitmap(snapshot.bitmap)
		view.alpha = 1f
		view.visibility = View.VISIBLE
		onOwnershipMutated()

		val timeoutAction = Runnable { complete(request, false) }
		timeout = timeoutAction
		view.postDelayed(timeoutAction, ReaderPageInlineRasterShieldTimeoutMillis)
		awaitCommittedFrame(request)
		onPresentationOwnershipStarted()
	}

	fun fadeOut(durationMillis: Long) {
		require(durationMillis >= 0L)
		if (!ownsPresentation()) return
		cancelFade()
		val request = Math.incrementExact(nextRequest).also { nextRequest = it }
		activeFadeRequest = request
		onOwnershipMutated()
		view.animate()
			.alpha(0f)
			.setDuration(durationMillis)
			.withEndAction {
				if (activeFadeRequest != request) return@withEndAction
				activeFadeRequest = null
				clearPresentation()
				onOwnershipMutated()
			}
			.start()
	}

	fun dismiss() {
		cancelPendingPresentation()
		cancelFade()
		clearPresentation()
	}

	fun ownsPresentation(): Boolean =
		ownedSnapshot?.bitmap?.isRecycled == false &&
			view.visibility == View.VISIBLE

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
				val committed = Runnable { complete(request, true) }
				committedFrame = committed
				view.postOnAnimation(committed)
			}
			view.postInvalidateOnAnimation()
			return
		}

		val first = Runnable {
			if (activeRequest != request) return@Runnable
			firstFrame = null
			val committed = Runnable { complete(request, true) }
			committedFrame = committed
			view.postOnAnimation(committed)
		}
		firstFrame = first
		view.postOnAnimation(first)
		view.postInvalidateOnAnimation()
	}

	private fun cancelPendingPresentation() {
		firstFrame?.let(view::removeCallbacks)
		committedFrame?.let(view::removeCallbacks)
		timeout?.let(view::removeCallbacks)
		firstFrame = null
		committedFrame = null
		timeout = null
		val hadRequest = activeRequest != null
		activeRequest = null
		val callback = presentationCallback
		presentationCallback = null
		callback?.invoke(false)
		if (hadRequest) onOwnershipMutated()
	}

	private fun cancelFade() {
		val hadFade = activeFadeRequest != null
		activeFadeRequest = null
		view.animate().cancel()
		if (ownsPresentation()) view.alpha = 1f
		if (hadFade) onOwnershipMutated()
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

	private fun clearPresentation() {
		val hadPresentation = ownedSnapshot != null || view.visibility != View.GONE
		view.setImageDrawable(null)
		view.alpha = 1f
		view.visibility = View.GONE
		ownedSnapshot?.release()
		ownedSnapshot = null
		if (hadPresentation) onOwnershipMutated()
	}
}

package paige.navic.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRelocationReservationResult
import paige.navic.reader.ReaderPageRelocationTransferResult
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderPageInlineRasterShieldTest {
	@Test
	fun api28FadeReleasesLiveOwnershipOnlyAfterBothExposedFrameCallbacks() {
		val fixture = ShieldFixture.create()
		try {
			val presented = mutableListOf<Boolean>()
			fixture.shield.present(fixture.snapshot(), presented::add)
			fixture.drainCurrentMainTasks()
			assertEquals(listOf(true), presented)

			val handoff = fixture.beginLiveHandoff()
			var finalized = 0
			val fadeResults = mutableListOf<Boolean>()
			fixture.shield.fadeOut(durationMillis = 0L) { exposedFrameCommitted ->
				fadeResults += exposedFrameCommitted
				if (!exposedFrameCommitted) return@fadeOut
				fixture.shield.dismiss()
				assertTrue(fixture.queue.completeHandoff(handoff.request.token.value))
				assertTrue(handoff.dispatch.complete(handoff.request))
				finalized += 1
			}

			assertEquals(0f, fixture.shield.view.alpha)
			assertTrue(fadeResults.isEmpty())
			assertNull(fixture.tryAcquirePassive())
			assertEquals(handoff.request, fixture.queue.head())
			assertEquals(1, fixture.ownership.snapshot().liveClaims)

			fixture.runOneMainTask()
			assertTrue(fadeResults.isEmpty())
			assertEquals(handoff.request, fixture.queue.head())
			assertEquals(1, fixture.ownership.snapshot().liveClaims)
			assertNull(fixture.tryAcquirePassive())

			fixture.runOneMainTask()
			assertTrue(fadeResults.isEmpty())
			assertEquals(handoff.request, fixture.queue.head())
			assertEquals(1, fixture.ownership.snapshot().liveClaims)
			assertNull(fixture.tryAcquirePassive())

			fixture.runOneMainTask()
			assertEquals(listOf(true), fadeResults)
			assertEquals(1, finalized)
			assertNull(fixture.queue.head())
			assertEquals(0, fixture.ownership.snapshot().liveClaims)
			assertEquals(0, fixture.ownership.snapshot().passiveOwners)
			assertFalse(handoff.dispatch.complete(handoff.request))

			fixture.runOneMainTask()
			fixture.shield.dismiss()
			assertEquals(1, finalized)
			assertEquals(0, fixture.ownership.snapshot().liveClaims)
			assertEquals(0, fixture.ownership.snapshot().passiveOwners)
		} finally {
			fixture.close()
		}
	}

	@Test
	fun api28DetachedFadeFailsClosedAndRetainsTheShieldUntilExplicitDismissal() {
		val fixture = ShieldFixture.create()
		try {
			val presented = mutableListOf<Boolean>()
			fixture.shield.present(fixture.snapshot(), presented::add)
			fixture.runOneMainTask()
			fixture.runOneMainTask()
			assertEquals(listOf(true), presented)

			val handoff = fixture.beginLiveHandoff()
			val fadeResults = mutableListOf<Boolean>()
			fixture.shield.fadeOut(durationMillis = 0L, fadeResults::add)
			fixture.host.removeView(fixture.shield.view)

			fixture.runOneMainTask()
			fixture.runOneMainTask()
			fixture.runOneMainTask()
			assertEquals(listOf(false), fadeResults)
			assertTrue(fixture.shield.ownsPresentation())
			assertEquals(handoff.request, fixture.queue.head())
			assertEquals(1, fixture.ownership.snapshot().liveClaims)
			assertNull(fixture.tryAcquirePassive())

			fixture.shield.dismiss()
			assertTrue(
				handoff.dispatch.fail(
					handoff.request,
					ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
				)
			)
			assertEquals(
				listOf(ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated),
				handoff.rejections
			)
			fixture.queue.cancelAll()
			assertEquals(0, fixture.ownership.snapshot().passiveOwners)
			assertEquals(0, fixture.ownership.snapshot().liveClaims)
			assertEquals(0, fixture.ownership.snapshot().restorationCallbacks)
		} finally {
			fixture.close()
		}
	}

	private class ShieldFixture private constructor(
		private val activityController: org.robolectric.android.controller.ActivityController<Activity>,
		val host: FrameLayout,
		val shield: ReaderPageInlineRasterShield,
		val ownership: ReaderForegroundWebViewOwnership,
		val queue: ReaderPageRelocationQueue
	) {
		data class LiveHandoff(
			val request: ReaderPageRelocationRequest,
			val dispatch: ReaderPageRelocationLiveDispatchCoordinator,
			val rejections: MutableList<ReaderPageRelocationDiagnosticRejectionReason>
		)

		fun snapshot(): ReaderPageSlideSnapshot {
			val hostLocation = IntArray(2)
			host.getLocationInWindow(hostLocation)
			return ReaderPageSlideSnapshot(
				key = ReaderPageSlideSnapshotKey(
					visualPageIndex = 2,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					bitmapQuality = ReaderPageBitmapQuality.Balanced,
					bitmapWidth = 8,
					bitmapHeight = 8,
					surfaceWidth = 8,
					surfaceHeight = 8
				),
				bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
				surfaceRectInWindow = Rect(
					hostLocation[0],
					hostLocation[1],
					hostLocation[0] + 8,
					hostLocation[1] + 8
				),
				leafGeometry = ReaderPageTurnLeafGeometry(
					fullLeafRect = ReaderPageTurnPixelRect(0, 0, 8, 8),
					leftLeafRect = ReaderPageTurnPixelRect(0, 0, 3, 8),
					gutterRect = ReaderPageTurnPixelRect(3, 0, 5, 8),
					rightLeafRect = ReaderPageTurnPixelRect(5, 0, 8, 8)
				),
				reverseFaceColor = 0xffead9ae.toInt()
			).also { snapshot ->
				snapshot.retain()
				snapshot.releaseCacheOwnership()
			}
		}

		fun beginLiveHandoff(): LiveHandoff {
			val reservation = (queue.reserve(gestureId = 29L) as
				ReaderPageRelocationReservationResult.Reserved).reservation
			val claim = ownership.acquireLive(reservation.gestureId)
			val request = (queue.enqueueReserved(
				reservation = reservation,
				rasterGeneration = 7L,
				textureGeneration = 11L,
				sourceOrdinal = 0,
				destinationOrdinal = 2,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = "synthetic-shield"
			) as ReaderPageRelocationTransferResult.Enqueued).request
			val rejectionReasons = mutableListOf<ReaderPageRelocationDiagnosticRejectionReason>()
			val dispatch = ReaderPageRelocationLiveDispatchCoordinator(
				foregroundWebViewOwnership = ownership,
				isDispatchCurrent = { candidate -> candidate == request },
				dispatchExact = { exactRequest, generation ->
					assertEquals(0, exactRequest.sourceOrdinal)
					assertEquals(2, exactRequest.destinationOrdinal)
					assertTrue(ownership.isCurrent(claim, generation))
					ReaderPageRelocationExactDispatchResult.Dispatched
				},
				onRejected = { _, reason -> rejectionReasons += reason }
			)
			assertTrue(dispatch.transfer(request, claim))
			assertTrue(dispatch.dispatch(request))
			assertEquals(request, queue.commandToDispatch())
			assertTrue(
				queue.acknowledge(
					request.token.value,
					request.destinationOrdinal,
					request.foliateSessionId,
					request.rasterGeneration,
					request.textureGeneration
				)
			)
			return LiveHandoff(request, dispatch, rejectionReasons)
		}

		fun tryAcquirePassive(): ReaderForegroundWebViewPassiveLease? =
			ownership.tryAcquirePassive(sessionId = 91L) {
				error("Live handoff must reject passive acquisition")
			}

		fun runOneMainTask() {
			Shadows.shadowOf(Looper.getMainLooper()).runOneTask()
		}

		fun drainCurrentMainTasks() {
			Shadows.shadowOf(Looper.getMainLooper()).idle()
		}

		fun close() {
			shield.dismiss()
			ownership.close()
			activityController.destroy()
		}

		companion object {
			fun create(): ShieldFixture {
				val activityController = Robolectric.buildActivity(Activity::class.java).setup()
				val activity = activityController.get()
				val host = FrameLayout(activity)
				activity.setContentView(host)
				host.measure(
					View.MeasureSpec.makeMeasureSpec(8, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(8, View.MeasureSpec.EXACTLY)
				)
				host.layout(0, 0, 8, 8)
				val shield = ReaderPageInlineRasterShield(host)
				host.addView(
					shield.view,
					FrameLayout.LayoutParams(8, 8)
				)
				Shadows.shadowOf(Looper.getMainLooper()).idle()
				check(host.isAttachedToWindow)
				check(shield.view.isAttachedToWindow)
				return ShieldFixture(
					activityController = activityController,
					host = host,
					shield = shield,
					ownership = ReaderForegroundWebViewOwnership(),
					queue = ReaderPageRelocationQueue()
				)
			}
		}
	}
}

package paige.navic.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarScrollManagerTest {
	@Test
	fun backRestoresChromeWhenNoScreenScrollHandlerConsumesIt() {
		val manager = BottomBarScrollManager(thresholdPx = 50f)
		manager.isTriggered = true

		assertTrue(manager.tryHandleBackToTop())
		assertFalse(manager.isTriggered)
		assertFalse(manager.tryHandleBackToTop())
	}

	@Test
	fun backUsesMostRecentScreenScrollHandlerBeforePoppingNavigation() {
		val manager = BottomBarScrollManager(thresholdPx = 50f)
		var firstCalls = 0
		var secondCalls = 0
		val firstHandler = {
			firstCalls += 1
			false
		}
		val secondHandler = {
			secondCalls += 1
			true
		}

		manager.registerBackToTopHandler(firstHandler)
		manager.registerBackToTopHandler(secondHandler)

		assertTrue(manager.tryHandleBackToTop())
		assertEquals(0, firstCalls)
		assertEquals(1, secondCalls)
	}

	@Test
	fun unregisteringScreenScrollHandlerPreventsStaleRoutesFromConsumingBack() {
		val manager = BottomBarScrollManager(thresholdPx = 50f)
		var calls = 0
		val handler = {
			calls += 1
			true
		}

		manager.registerBackToTopHandler(handler)
		manager.unregisterBackToTopHandler(handler)

		assertFalse(manager.tryHandleBackToTop())
		assertEquals(0, calls)
	}
}

package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageTurnPresentationReceiptTest {
	@Test
	fun parserCreatesTypedPreviewAndLiveReceiptsFromNeutralJson() {
		val preview = readerPageTurnPresentationReceipt(
			"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":11,"presentationSequence":21}"""
		)
		val live = readerPageTurnPresentationReceipt(
			"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":17,"presentationSequence":22}"""
		)

		assertEquals(ReaderPageTurnPresentationScope.Preview, preview?.scope)
		assertEquals("preview-token-alpha", preview?.token)
		assertEquals(7L, preview?.pageIndex)
		assertEquals(11L, preview?.previewGeneration)
		assertEquals(21L, preview?.presentationSequence)
		assertNull(preview?.foliateSessionId)
		assertEquals(ReaderPageTurnPresentationScope.Live, live?.scope)
		assertEquals("session-alpha", live?.foliateSessionId)
		assertEquals(13L, live?.rasterGeneration)
		assertEquals(17L, live?.textureGeneration)
		assertEquals(22L, live?.presentationSequence)
		assertNull(live?.previewGeneration)
	}

	@Test
	fun parserRejectsIncompleteInvalidAndExtendedShapes() {
		assertNull(readerPageTurnPresentationReceipt(null))
		assertNull(readerPageTurnPresentationReceipt("not-json"))
		assertNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"presentationSequence":21}"""
			)
		)
		assertNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":11,"presentationSequence":21,"unexpected":"value"}"""
			)
		)
		assertNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":17,"presentationSequence":0}"""
			)
		)
	}

	@Test
	fun previewMatcherRequiresExactTargetIdentity() {
		val receipt = assertNotNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":11,"presentationSequence":21}"""
			)
		)
		val target = ReaderPageTurnPresentationTarget.Preview(
			token = "preview-token-alpha",
			pageIndex = 7,
			previewGeneration = 11
		)

		assertTrue(receipt.matches(target))
		assertFalse(receipt.matches(target.copy(token = "preview-token-beta")))
		assertFalse(receipt.matches(target.copy(pageIndex = 8)))
		assertFalse(receipt.matches(target.copy(previewGeneration = 12)))
		assertFalse(
			receipt.matches(
				ReaderPageTurnPresentationTarget.Live(
					token = target.token,
					pageIndex = target.pageIndex,
					foliateSessionId = "session-alpha",
					rasterGeneration = 13,
					textureGeneration = 17
				)
			)
		)
	}

	@Test
	fun liveMatcherRequiresExactTargetIdentity() {
		val receipt = assertNotNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":17,"presentationSequence":22}"""
			)
		)
		val target = ReaderPageTurnPresentationTarget.Live(
			token = "live-token-alpha",
			pageIndex = 8,
			foliateSessionId = "session-alpha",
			rasterGeneration = 13,
			textureGeneration = 17
		)

		assertTrue(receipt.matches(target))
		assertFalse(receipt.matches(target.copy(token = "live-token-beta")))
		assertFalse(receipt.matches(target.copy(pageIndex = 9)))
		assertFalse(receipt.matches(target.copy(foliateSessionId = "session-beta")))
		assertFalse(receipt.matches(target.copy(rasterGeneration = 14)))
		assertFalse(receipt.matches(target.copy(textureGeneration = 18)))
	}

	@Test
	fun parserAcceptsTheJavascriptMaximumSafeIntegerForEveryLongWireField() {
		val maximum = 9_007_199_254_740_991L
		val preview = readerPageTurnPresentationReceipt(
			"""{"scope":"preview","token":"preview-token-alpha","pageIndex":$maximum,"previewGeneration":$maximum,"presentationSequence":$maximum}"""
		)
		val live = readerPageTurnPresentationReceipt(
			"""{"scope":"live","token":"live-token-alpha","pageIndex":$maximum,"foliateSessionId":"session-alpha","rasterGeneration":$maximum,"textureGeneration":$maximum,"presentationSequence":$maximum}"""
		)

		assertNotNull(preview)
		assertEquals(maximum, preview.pageIndex)
		assertEquals(maximum, preview.previewGeneration)
		assertEquals(maximum, preview.presentationSequence)
		assertNotNull(live)
		assertEquals(maximum, live.pageIndex)
		assertEquals(maximum, live.rasterGeneration)
		assertEquals(maximum, live.textureGeneration)
		assertEquals(maximum, live.presentationSequence)
	}

	@Test
	fun parserRejectsEveryLongWireFieldAboveTheJavascriptSafeIntegerRange() {
		val aboveMaximum = 9_007_199_254_740_992L
		val longMaximum = Long.MAX_VALUE
		val previewTemplate =
			"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":%s,"presentationSequence":%s}"""
		val liveTemplate =
			"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":%s,"textureGeneration":%s,"presentationSequence":%s}"""

		listOf(aboveMaximum, longMaximum).forEach { rejected ->
			assertNull(
				readerPageTurnPresentationReceipt(
					"""{"scope":"preview","token":"preview-token-alpha","pageIndex":$rejected,"previewGeneration":11,"presentationSequence":21}"""
				)
			)
			assertNull(readerPageTurnPresentationReceipt(previewTemplate.format(rejected, 21)))
			assertNull(readerPageTurnPresentationReceipt(previewTemplate.format(11, rejected)))
			assertNull(readerPageTurnPresentationReceipt(liveTemplate.format(rejected, 17, 22)))
			assertNull(readerPageTurnPresentationReceipt(liveTemplate.format(13, rejected, 22)))
			assertNull(readerPageTurnPresentationReceipt(liveTemplate.format(13, 17, rejected)))
		}
	}

	@Test
	fun targetTypesEnforceJavascriptSafeIntegerAndIdentityInvariants() {
		val maximum = 9_007_199_254_740_991L
		assertEquals(
			maximum,
			ReaderPageTurnPresentationTarget.Preview(
				token = "preview-token-alpha",
				pageIndex = maximum,
				previewGeneration = maximum
			).previewGeneration
		)
		assertEquals(
			maximum,
			ReaderPageTurnPresentationTarget.Live(
				token = "live-token-alpha",
				pageIndex = maximum,
				foliateSessionId = "session-alpha",
				rasterGeneration = maximum,
				textureGeneration = maximum
			).textureGeneration
		)

		listOf(9_007_199_254_740_992L, Long.MAX_VALUE).forEach { rejected ->
			assertFailsWith<IllegalArgumentException> {
				ReaderPageTurnPresentationTarget.Preview(
					token = "preview-token-alpha",
					pageIndex = rejected,
					previewGeneration = 11
				)
			}
			assertFailsWith<IllegalArgumentException> {
				ReaderPageTurnPresentationTarget.Preview(
					token = "preview-token-alpha",
					pageIndex = 7,
					previewGeneration = rejected
				)
			}
			assertFailsWith<IllegalArgumentException> {
				ReaderPageTurnPresentationTarget.Live(
					token = "live-token-alpha",
					pageIndex = 8,
					foliateSessionId = "session-alpha",
					rasterGeneration = rejected,
					textureGeneration = 17
				)
			}
			assertFailsWith<IllegalArgumentException> {
				ReaderPageTurnPresentationTarget.Live(
					token = "live-token-alpha",
					pageIndex = 8,
					foliateSessionId = "session-alpha",
					rasterGeneration = 13,
					textureGeneration = rejected
				)
			}
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderPageTurnPresentationTarget.Preview("preview-token-alpha", -1, 11)
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderPageTurnPresentationTarget.Live("live-token-alpha", 8, "", 13, 17)
		}
	}

	@Test
	fun acceptanceRequiresTargetMatchStableReceiptAndForegroundSuccess() {
		val initial = assertNotNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":17,"presentationSequence":22}"""
			)
		)
		val target = ReaderPageTurnPresentationTarget.Live(
			token = "live-token-alpha",
			pageIndex = 8,
			foliateSessionId = "session-alpha",
			rasterGeneration = 13,
			textureGeneration = 17
		)

		assertTrue(
			readerPageTurnPresentationReceiptAccepted(
				target = target,
				initialReceipt = initial,
				finalReceipt = initial.copy(),
				foregroundSuccess = true
			)
		)
		assertFalse(
			readerPageTurnPresentationReceiptAccepted(
				target = target,
				initialReceipt = initial,
				finalReceipt = initial,
				foregroundSuccess = false
			)
		)
		assertFalse(
			readerPageTurnPresentationReceiptAccepted(
				target = target,
				initialReceipt = initial,
				finalReceipt = initial.copy(presentationSequence = 23),
				foregroundSuccess = true
			)
		)
		assertFalse(
			readerPageTurnPresentationReceiptAccepted(
				target = target.copy(pageIndex = 9),
				initialReceipt = initial,
				finalReceipt = initial,
				foregroundSuccess = true
			)
		)
	}
}

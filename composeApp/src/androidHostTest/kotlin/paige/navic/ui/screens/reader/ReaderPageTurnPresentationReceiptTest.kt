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
		val preview = readerPageTurnPresentationReceipt(previewJson())
		val live = readerPageTurnPresentationReceipt(liveJson())

		assertEquals(ReaderPageTurnPresentationScope.Preview, preview?.scope)
		assertEquals("preview-token-alpha", preview?.token)
		assertEquals(7L, preview?.pageIndex)
		assertEquals(11L, preview?.previewGeneration)
		assertEquals(41L, preview?.foregroundMutationGeneration)
		assertEquals(21L, preview?.presentationSequence)
		assertNull(preview?.foliateSessionId)
		assertEquals(ReaderPageTurnPresentationScope.Live, live?.scope)
		assertEquals("session-alpha", live?.foliateSessionId)
		assertEquals(13L, live?.rasterGeneration)
		assertEquals(17L, live?.textureGeneration)
		assertEquals(41L, live?.foregroundMutationGeneration)
		assertEquals(22L, live?.presentationSequence)
		assertNull(live?.previewGeneration)
	}

	@Test
	fun parserRejectsIncompleteInvalidExtendedAndLegacyShapes() {
		assertNull(readerPageTurnPresentationReceipt(null))
		assertNull(readerPageTurnPresentationReceipt("not-json"))
		assertNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"presentationSequence":21}"""
			)
		)
		assertNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":11,"foregroundMutationGeneration":41,"presentationSequence":21,"unexpected":"value"}"""
			)
		)
		assertNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":17,"foregroundMutationGeneration":41,"presentationSequence":0}"""
			)
		)
		assertNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":11,"presentationSequence":21}"""
			)
		)
		assertNull(
			readerPageTurnPresentationReceipt(
				"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":17,"presentationSequence":22}"""
			)
		)
	}

	@Test
	fun parserRequiresMutationGenerationToBeAPositiveJavascriptSafeInteger() {
		listOf("0", "-1", "1.5", "\"41\"", "9007199254740992", Long.MAX_VALUE.toString())
			.forEach { rejected ->
				assertNull(readerPageTurnPresentationReceipt(previewJson(rejected)))
				assertNull(readerPageTurnPresentationReceipt(liveJson(rejected)))
			}
		val maximum = ReaderPageTurnPresentationMaximumSafeInteger.toString()
		assertEquals(
			ReaderPageTurnPresentationMaximumSafeInteger,
			assertNotNull(readerPageTurnPresentationReceipt(previewJson(maximum)))
				.foregroundMutationGeneration
		)
		assertEquals(
			ReaderPageTurnPresentationMaximumSafeInteger,
			assertNotNull(readerPageTurnPresentationReceipt(liveJson(maximum)))
				.foregroundMutationGeneration
		)
	}

	@Test
	fun previewMatcherRequiresExactTargetIdentity() {
		val receipt = assertNotNull(readerPageTurnPresentationReceipt(previewJson()))
		val target = ReaderPageTurnPresentationTarget.Preview(
			token = "preview-token-alpha",
			pageIndex = 7,
			previewGeneration = 11,
			foregroundMutationGeneration = 41
		)

		assertTrue(receipt.matches(target))
		assertFalse(receipt.matches(target.copy(token = "preview-token-beta")))
		assertFalse(receipt.matches(target.copy(pageIndex = 8)))
		assertFalse(receipt.matches(target.copy(previewGeneration = 12)))
		assertFalse(receipt.matches(target.copy(foregroundMutationGeneration = 42)))
		assertFalse(
			receipt.matches(
				ReaderPageTurnPresentationTarget.Live(
					token = target.token,
					pageIndex = target.pageIndex,
					foliateSessionId = "session-alpha",
					rasterGeneration = 13,
					textureGeneration = 17,
					foregroundMutationGeneration = 41
				)
			)
		)
	}

	@Test
	fun liveMatcherRequiresExactTargetIdentity() {
		val receipt = assertNotNull(readerPageTurnPresentationReceipt(liveJson()))
		val target = liveTarget()

		assertTrue(receipt.matches(target))
		assertFalse(receipt.matches(target.copy(token = "live-token-beta")))
		assertFalse(receipt.matches(target.copy(pageIndex = 9)))
		assertFalse(receipt.matches(target.copy(foliateSessionId = "session-beta")))
		assertFalse(receipt.matches(target.copy(rasterGeneration = 14)))
		assertFalse(receipt.matches(target.copy(textureGeneration = 18)))
		assertFalse(receipt.matches(target.copy(foregroundMutationGeneration = 42)))
	}

	@Test
	fun parserAcceptsTheJavascriptMaximumSafeIntegerForEveryLongWireField() {
		val maximum = ReaderPageTurnPresentationMaximumSafeInteger
		val preview = readerPageTurnPresentationReceipt(
			"""{"scope":"preview","token":"preview-token-alpha","pageIndex":$maximum,"previewGeneration":$maximum,"foregroundMutationGeneration":$maximum,"presentationSequence":$maximum}"""
		)
		val live = readerPageTurnPresentationReceipt(
			"""{"scope":"live","token":"live-token-alpha","pageIndex":$maximum,"foliateSessionId":"session-alpha","rasterGeneration":$maximum,"textureGeneration":$maximum,"foregroundMutationGeneration":$maximum,"presentationSequence":$maximum}"""
		)

		assertNotNull(preview)
		assertEquals(maximum, preview.pageIndex)
		assertEquals(maximum, preview.previewGeneration)
		assertEquals(maximum, preview.foregroundMutationGeneration)
		assertEquals(maximum, preview.presentationSequence)
		assertNotNull(live)
		assertEquals(maximum, live.pageIndex)
		assertEquals(maximum, live.rasterGeneration)
		assertEquals(maximum, live.textureGeneration)
		assertEquals(maximum, live.foregroundMutationGeneration)
		assertEquals(maximum, live.presentationSequence)
	}

	@Test
	fun parserRejectsEveryLongWireFieldAboveTheJavascriptSafeIntegerRange() {
		val aboveMaximum = 9_007_199_254_740_992L
		val longMaximum = Long.MAX_VALUE
		listOf(aboveMaximum, longMaximum).forEach { rejected ->
			assertNull(
				readerPageTurnPresentationReceipt(
					"""{"scope":"preview","token":"preview-token-alpha","pageIndex":$rejected,"previewGeneration":11,"foregroundMutationGeneration":41,"presentationSequence":21}"""
				)
			)
			assertNull(
				readerPageTurnPresentationReceipt(
					"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":$rejected,"foregroundMutationGeneration":41,"presentationSequence":21}"""
				)
			)
			assertNull(
				readerPageTurnPresentationReceipt(
					"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":11,"foregroundMutationGeneration":41,"presentationSequence":$rejected}"""
				)
			)
			assertNull(
				readerPageTurnPresentationReceipt(
					"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":$rejected,"textureGeneration":17,"foregroundMutationGeneration":41,"presentationSequence":22}"""
				)
			)
			assertNull(
				readerPageTurnPresentationReceipt(
					"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":$rejected,"foregroundMutationGeneration":41,"presentationSequence":22}"""
				)
			)
			assertNull(
				readerPageTurnPresentationReceipt(
					"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":17,"foregroundMutationGeneration":41,"presentationSequence":$rejected}"""
				)
			)
		}
	}

	@Test
	fun targetTypesEnforceJavascriptSafeIntegerAndIdentityInvariants() {
		val maximum = ReaderPageTurnPresentationMaximumSafeInteger
		assertEquals(
			maximum,
			ReaderPageTurnPresentationTarget.Preview(
				token = "preview-token-alpha",
				pageIndex = maximum,
				previewGeneration = maximum,
				foregroundMutationGeneration = maximum
			).foregroundMutationGeneration
		)
		assertEquals(
			maximum,
			ReaderPageTurnPresentationTarget.Live(
				token = "live-token-alpha",
				pageIndex = maximum,
				foliateSessionId = "session-alpha",
				rasterGeneration = maximum,
				textureGeneration = maximum,
				foregroundMutationGeneration = maximum
			).foregroundMutationGeneration
		)

		listOf(0L, -1L, 9_007_199_254_740_992L, Long.MAX_VALUE).forEach { rejected ->
			assertFailsWith<IllegalArgumentException> {
				ReaderPageTurnPresentationTarget.Preview(
					token = "preview-token-alpha",
					pageIndex = 7,
					previewGeneration = 11,
					foregroundMutationGeneration = rejected
				)
			}
			assertFailsWith<IllegalArgumentException> {
				ReaderPageTurnPresentationTarget.Live(
					token = "live-token-alpha",
					pageIndex = 8,
					foliateSessionId = "session-alpha",
					rasterGeneration = 13,
					textureGeneration = 17,
					foregroundMutationGeneration = rejected
				)
			}
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderPageTurnPresentationTarget.Preview("preview-token-alpha", -1, 11, 41)
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderPageTurnPresentationTarget.Live("live-token-alpha", 8, "", 13, 17, 41)
		}
	}

	@Test
	fun acceptanceRequiresTargetMatchStableReceiptAndForegroundSuccess() {
		val initial = assertNotNull(readerPageTurnPresentationReceipt(liveJson()))
		val target = liveTarget()

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
				target = target,
				initialReceipt = initial,
				finalReceipt = initial.copy(foregroundMutationGeneration = 42),
				foregroundSuccess = true
			)
		)
		assertFalse(
			readerPageTurnPresentationReceiptAccepted(
				target = target.copy(foregroundMutationGeneration = 42),
				initialReceipt = initial,
				finalReceipt = initial,
				foregroundSuccess = true
			)
		)
	}

	private fun previewJson(foregroundMutationGeneration: String = "41"): String =
		"""{"scope":"preview","token":"preview-token-alpha","pageIndex":7,"previewGeneration":11,"foregroundMutationGeneration":$foregroundMutationGeneration,"presentationSequence":21}"""

	private fun liveJson(foregroundMutationGeneration: String = "41"): String =
		"""{"scope":"live","token":"live-token-alpha","pageIndex":8,"foliateSessionId":"session-alpha","rasterGeneration":13,"textureGeneration":17,"foregroundMutationGeneration":$foregroundMutationGeneration,"presentationSequence":22}"""

	private fun liveTarget() = ReaderPageTurnPresentationTarget.Live(
		token = "live-token-alpha",
		pageIndex = 8,
		foliateSessionId = "session-alpha",
		rasterGeneration = 13,
		textureGeneration = 17,
		foregroundMutationGeneration = 41
	)
}

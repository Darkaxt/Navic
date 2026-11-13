package paige.navic.reader

import paige.navic.ui.screens.reader.ReaderEngineLogProjector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderJavascriptBridgeTest {
	@Test
	fun javascriptBridgeReportsTypedFailureAndSurfacesPersistentProtocolError() {
		val events = mutableListOf<ReaderBridgeEvent>()
		val rejections = mutableListOf<ReaderBridgeDecodeResult.Rejected>()
		val bridge = ReaderJavascriptBridge(
			onEvent = events::add,
			onRejected = rejections::add
		)

		repeat(3) { bridge.postMessage("{") }

		assertEquals(1, rejections.size)
		assertEquals(ReaderBridgeDecodeFailure.MalformedJson, rejections.single().failure)
		assertEquals("[redacted-reader-bridge-payload]", rejections.single().rawMessage)
		assertEquals(
			"reader_bridge_protocol",
			assertIs<ReaderBridgeEvent.Error>(events.single()).code
		)
	}

	@Test
	fun readerEngineLogProjectionDropsProtectedCanaries() {
		val urlCanary = "CANARY_URL_213"
		val hrefCanary = "CANARY_HREF_213"
		val cfiCanary = "CANARY_CFI_213"
		val publicationCanary = "CANARY_PUBLICATION_213"
		val dispatchCanary = "CANARY_DISPATCH_213"
		val userOrBookCanary = "CANARY_USER_OR_BOOK_213"
		val messageCanary = "CANARY_MESSAGE_213"
		val sourceCanary = "CANARY_SOURCE_213"
		val protectedCanaries = listOf(
			urlCanary,
			hrefCanary,
			cfiCanary,
			publicationCanary,
			dispatchCanary,
			userOrBookCanary,
			messageCanary,
			sourceCanary
		)
		data class ProjectionCase(
			val description: String,
			val output: String,
			val expected: String
		)

		val cases = listOf(
			ProjectionCase(
				description = "publication command",
				output = ReaderEngineLogProjector.command(
					ReaderBridgeDispatchCommand(
						id = dispatchCanary,
						command = ReaderBridgeCommand.OpenPublication(
							url = "https://$urlCanary/$publicationCanary/$userOrBookCanary.epub",
							foliateSessionId = userOrBookCanary
						)
					)
				),
				expected = "Dispatching reader engine command: openPublication"
			),
			ProjectionCase(
				description = "locator command",
				output = ReaderEngineLogProjector.command(
					ReaderBridgeDispatchCommand(
						id = dispatchCanary,
						command = ReaderBridgeCommand.GoToLocator(
							locator = ReaderLocator(
								href = "chapter-$hrefCanary.xhtml",
								cfi = "epubcfi(/$cfiCanary)"
							),
							reason = messageCanary
						)
					)
				),
				expected = "Dispatching reader engine command: goToLocator"
			),
			ProjectionCase(
				description = "location event",
				output = ReaderEngineLogProjector.event(
					ReaderBridgeEvent.LocationChanged(
						locator = ReaderLocator(
							href = "chapter-$hrefCanary.xhtml",
							cfi = "epubcfi(/$cfiCanary)",
							rangeCfi = "epubcfi(/$cfiCanary/range)",
							reason = messageCanary
						),
						foliateSessionId = userOrBookCanary,
						tocTitle = publicationCanary
					)
				),
				expected = "Reader bridge event: locationChanged"
			),
			ProjectionCase(
				description = "link event",
				output = ReaderEngineLogProjector.event(
					ReaderBridgeEvent.InternalLinkRequested(
						href = "https://$urlCanary/$hrefCanary",
						source = sourceCanary
					)
				),
				expected = "Reader bridge event: internalLink"
			),
			ProjectionCase(
				description = "error event",
				output = ReaderEngineLogProjector.event(
					ReaderBridgeEvent.Error(
						message = messageCanary,
						code = userOrBookCanary
					)
				),
				expected = "Reader bridge event: error"
			),
			ProjectionCase(
				description = "approved console diagnostic",
				output = ReaderEngineLogProjector.console(
					level = "DEBUG",
					message = "[NavicReader] dispatch $messageCanary $userOrBookCanary",
					sourceId = sourceCanary
				),
				expected = "Reader console DEBUG: dispatch"
			),
			ProjectionCase(
				description = "unknown console diagnostic",
				output = ReaderEngineLogProjector.console(
					level = "ERROR",
					message = "[NavicReader] $messageCanary $urlCanary $cfiCanary",
					sourceId = sourceCanary
				),
				expected = "Reader console ERROR: [redacted-reader-console]"
			),
			ProjectionCase(
				description = "word sync anchor rejection guard",
				output = ReaderEngineLogProjector.console(
					level = "DEBUG",
					message = "[NavicReader] wordsync-anchor-reject:page-position $messageCanary",
					sourceId = sourceCanary
				),
				expected = "Reader console DEBUG: wordsync-anchor-reject:page-position"
			),
			ProjectionCase(
				description = "word sync anchor numeric geometry",
				output = ReaderEngineLogProjector.console(
					level = "DEBUG",
					message = "[NavicReader] wordsync-anchor-geometry:120,128;0,0;-1240,160,-1180,200;0,0,1280,1600|1280,0,1280,1600 $messageCanary",
					sourceId = sourceCanary
				),
				expected = "Reader console DEBUG: wordsync-anchor-geometry:120,128;0,0;-1240,160,-1180,200;0,0,1280,1600|1280,0,1280,1600"
			),
			ProjectionCase(
				description = "word sync numeric segment trace",
				output = ReaderEngineLogProjector.console(
					level = "DEBUG",
					message = "[NavicReader] wordsync-segment:412,-1,9120,9388,1834500,9001 $messageCanary",
					sourceId = sourceCanary
				),
				expected = "Reader console DEBUG: wordsync-segment:412,-1,9120,9388,1834500,9001"
			),
			ProjectionCase(
				description = "word sync segment trace carrying text is redacted",
				output = ReaderEngineLogProjector.console(
					level = "DEBUG",
					message = "[NavicReader] wordsync-segment:412,sand,1,2,3",
					sourceId = sourceCanary
				),
				expected = "Reader console DEBUG: [redacted-reader-console]"
			),
			ProjectionCase(
				description = "word sync anchor geometry carrying text is redacted",
				output = ReaderEngineLogProjector.console(
					level = "DEBUG",
					message = "[NavicReader] wordsync-anchor-geometry:12,sand",
					sourceId = sourceCanary
				),
				expected = "Reader console DEBUG: [redacted-reader-console]"
			),
			ProjectionCase(
				description = "word sync anchor rejection with unapproved guard text",
				output = ReaderEngineLogProjector.console(
					level = "DEBUG",
					message = "[NavicReader] wordsync-anchor-reject:$urlCanary",
					sourceId = sourceCanary
				),
				expected = "Reader console DEBUG: [redacted-reader-console]"
			)
		)

		cases.forEach { case ->
			assertEquals(case.expected, case.output, case.description)
			protectedCanaries.forEach { canary ->
				assertTrue(
					canary !in case.output,
					"${case.description} leaked $canary in '${case.output}'"
				)
			}
		}
	}

	@Test
	fun deactivatedBridgeDropsEventsAndRejections() {
		val events = mutableListOf<ReaderBridgeEvent>()
		val rejections = mutableListOf<ReaderBridgeDecodeResult.Rejected>()
		val bridge = ReaderJavascriptBridge(
			onEvent = events::add,
			onRejected = rejections::add
		)

		bridge.deactivate()
		bridge.postMessage("""{"type":"ready"}""")
		bridge.postMessage("{")

		assertTrue(events.isEmpty())
		assertTrue(rejections.isEmpty())
	}
}

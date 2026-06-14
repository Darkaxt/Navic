package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReaderBridgeProtocolTest {
	@Test
	fun openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge() {
		val script = ReaderBridgeCommand.OpenPublication(
			url = "https://bindery.local/opds/books/3693/resources/readaloud-1?title=\"Alcatraz\"",
			mediaOverlayEnabled = true,
			externalShellCover = true,
			startLocator = ReaderLocator(cfi = "epubcfi(/6/2!/4/1:0)"),
				settings = ReaderSettings(
					fontFamily = ReaderBookFontFamily,
					fontSource = ReaderFontSourceCustom,
					customFontFamily = "Storyteller Serif",
					customFontUrl = "https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf",
					fontSizePercent = 112,
					lineHeight = 1.7,
				paragraphSpacingPercent = 75,
				marginPercent = 8,
				dimOverlayPercent = 30,
				orientation = ReaderOrientationLockedLandscape,
				theme = ReaderDuskTheme,
				direction = ReaderDirectionRtl,
				paged = false,
				tapZone = ReaderTapZoneKindle,
				smallerTapZone = true,
				showTapZones = true,
				publisherStyles = true,
				fullscreen = false,
				keepScreenOn = true,
				readaloudSyncEnabled = false,
				webContentsDebuggingEnabled = true
			)
		).toJavaScript()

		assertContains(script, "window.NavicReaderBridge.dispatch")
		assertContains(script, "\\\"Alcatraz\\\"")
		assertContains(script, "epubcfi(/6/2!/4/1:0)")
		assertContains(script, "\"type\":\"openPublication\"")
		assertContains(script, "\"mediaOverlayEnabled\":true")
		assertContains(script, "\"externalShellCover\":true")
		assertContains(script, "Navic Literata")
		assertContains(script, "Bookerly, Georgia, serif")
		assertContains(script, "\"fontSource\":\"custom\"")
		assertContains(script, "\"customFontFamily\":\"Storyteller Serif\"")
		assertContains(script, "\"customFontUrl\":\"https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf\"")
		assertContains(script, "\"fontSizePercent\":112")
		assertContains(script, "\"lineHeight\":1.7")
		assertContains(script, "\"paragraphSpacingPercent\":75")
		assertContains(script, "\"marginPercent\":8")
		assertContains(script, "\"dimOverlayPercent\":30")
		assertContains(script, "\"orientation\":\"locked-landscape\"")
		assertContains(script, "\"theme\":\"dusk\"")
		assertContains(script, "\"direction\":\"rtl\"")
		assertContains(script, "\"paged\":false")
		assertContains(script, "\"tapZone\":\"kindle\"")
		assertContains(script, "\"smallerTapZone\":true")
		assertContains(script, "\"showTapZones\":true")
		assertContains(script, "\"publisherStyles\":true")
		assertContains(script, "\"fullscreen\":false")
		assertContains(script, "\"keepScreenOn\":true")
		assertContains(script, "\"readaloudSyncEnabled\":false")
		assertContains(script, "\"webContentsDebuggingEnabled\":true")
	}

	@Test
	fun applyOverlayFragmentCommandDispatchesFragmentMetadata() {
		val script = ReaderBridgeCommand.ApplyOverlayFragment(
			ReaderOverlayFragment(
				resourceHref = "EPUB/Audio/chapter1.mp3",
				fragmentId = "frag-1",
				textHref = "EPUB/Text/chapter1.xhtml",
				clipBeginSeconds = 1.25,
				clipEndSeconds = 3.5,
				label = "Chapter 1 / Paragraph 1"
			)
		).toJavaScript()

		assertContains(script, "\"type\":\"applyOverlayFragment\"")
		assertContains(script, "\"resourceHref\":\"EPUB/Audio/chapter1.mp3\"")
		assertContains(script, "\"fragmentId\":\"frag-1\"")
		assertContains(script, "\"textHref\":\"EPUB/Text/chapter1.xhtml\"")
		assertContains(script, "\"clipBeginSeconds\":1.25")
		assertContains(script, "\"clipEndSeconds\":3.5")
		assertContains(script, "\"label\":\"Chapter 1 / Paragraph 1\"")
	}

	@Test
	fun goToCfiCommandDispatchesSearchResultNavigationTarget() {
		val script = ReaderBridgeCommand.GoToCfi("epubcfi(/6/8!/4/1:0)").toJavaScript()

		assertContains(script, "\"type\":\"goToCfi\"")
		assertContains(script, "\"cfi\":\"epubcfi(/6/8!/4/1:0)\"")
	}

	@Test
	fun pageTurnCommandsDispatchReaderPaginationIntents() {
		val nextScript = ReaderBridgeCommand.NextPage.toJavaScript()
		val previousScript = ReaderBridgeCommand.PreviousPage.toJavaScript()

		assertContains(nextScript, "window.NavicReaderBridge.dispatch")
		assertContains(nextScript, "\"type\":\"nextPage\"")
		assertContains(previousScript, "window.NavicReaderBridge.dispatch")
		assertContains(previousScript, "\"type\":\"previousPage\"")
	}

	@Test
	fun viewportScrollCommandDispatchesReaderScrollIntent() {
		val downScript = ReaderBridgeCommand.ScrollViewport(ReaderViewportScrollDirection.Down).toJavaScript()
		val upScript = ReaderBridgeCommand.ScrollViewport(ReaderViewportScrollDirection.Up).toJavaScript()

		assertContains(downScript, "window.NavicReaderBridge.dispatch")
		assertContains(downScript, "\"type\":\"scrollViewport\"")
		assertContains(downScript, "\"direction\":\"down\"")
		assertContains(upScript, "window.NavicReaderBridge.dispatch")
		assertContains(upScript, "\"type\":\"scrollViewport\"")
		assertContains(upScript, "\"direction\":\"up\"")
	}

	@Test
	fun progressSeekCommandDispatchesClampedFractionNavigationIntent() {
		val script = ReaderBridgeCommand.GoToProgress(1.4).toJavaScript()

		assertContains(script, "window.NavicReaderBridge.dispatch")
		assertContains(script, "\"type\":\"goToProgress\"")
		assertContains(script, "\"progress\":1.0")
	}

	@Test
	fun applyHighlightsCommandDispatchesPersistedAnnotationBatch() {
		val script = ReaderBridgeCommand.ApplyHighlights(
			listOf(
				ReaderAnnotation(
					id = "book-1|epubcfi(/6/8!/4/1:0)",
					bookId = "book-1",
					bookTitle = "Storyteller Book",
					cfi = "epubcfi(/6/8!/4/1:0)",
					text = "The highlighted sentence",
					color = "#f4d35e",
					sectionTitle = "Chapter 1"
				)
			)
		).toJavaScript()

		assertContains(script, "\"type\":\"applyHighlights\"")
		assertContains(script, "\"highlights\"")
		assertContains(script, "\"cfi\":\"epubcfi(/6/8!/4/1:0)\"")
		assertContains(script, "\"color\":\"#f4d35e\"")
	}

	@Test
	fun bridgeEventsDecodeReaderLocationAndOverlayEvents() {
		val location = decodeReaderBridgeEvent(
			"""
			{
			  "type": "locationChanged",
			  "href": "chapter-01.xhtml",
			  "cfi": "epubcfi(/6/2!/4/1:0)",
			  "progress": 0.24,
			  "tocTitle": "Chapter 1"
			}
			""".trimIndent()
		)
		val overlay = decodeReaderBridgeEvent(
			"""
			{
			  "type": "overlayFragmentActive",
			  "resourceHref": "audio/part01.mp3",
			  "fragmentId": "frag-1",
			  "textHref": "chapter-01.xhtml#frag-1",
			  "clipBeginSeconds": 12.4,
			  "clipEndSeconds": 16.9,
			  "label": "Chapter 1 / Paragraph 4"
			}
			""".trimIndent()
		)

		val locationChanged = assertIs<ReaderBridgeEvent.LocationChanged>(location)
		assertEquals("chapter-01.xhtml", locationChanged.locator.href)
		assertEquals("epubcfi(/6/2!/4/1:0)", locationChanged.locator.cfi)
		assertEquals(0.24, locationChanged.locator.progress)
		assertEquals("Chapter 1", locationChanged.tocTitle)

		val active = assertIs<ReaderBridgeEvent.OverlayFragmentActive>(overlay)
		assertEquals("audio/part01.mp3", active.fragment.resourceHref)
		assertEquals("frag-1", active.fragment.fragmentId)
		assertEquals("chapter-01.xhtml#frag-1", active.fragment.textHref)
		assertEquals(12.4, active.fragment.clipBeginSeconds)
		assertEquals(16.9, active.fragment.clipEndSeconds)
		assertEquals("Chapter 1 / Paragraph 4", active.fragment.label)
	}

	@Test
	fun bridgeEventsDecodeFixedLayoutPagePosition() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "locationChanged",
			  "progress": 0.05,
			  "pageIndex": 6,
			  "pageCount": 120
			}
			""".trimIndent()
		)

		val locationChanged = assertIs<ReaderBridgeEvent.LocationChanged>(event)
		assertEquals(
			ReaderLocator(
				progress = 0.05,
				pageIndex = 6,
				pageCount = 120
			),
			locationChanged.locator
		)
	}

	@Test
	fun bridgeEventsDecodeSearchResultsWithCfiHrefAndExcerpt() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "searchResults",
			  "query": "alcatraz",
			  "results": [
			    {
			      "id": "search-0",
			      "cfi": "epubcfi(/6/8!/4/1:0)",
			      "href": "EPUB/Text/chapter-01.xhtml",
			      "excerpt": "The word Alcatraz appeared here",
			      "sectionTitle": "Chapter 1"
			    }
			  ]
			}
			""".trimIndent()
		)

		val results = assertIs<ReaderBridgeEvent.SearchResults>(event)
		assertEquals("alcatraz", results.query)
		assertEquals(
			ReaderSearchResult(
				id = "search-0",
				cfi = "epubcfi(/6/8!/4/1:0)",
				href = "EPUB/Text/chapter-01.xhtml",
				excerpt = "The word Alcatraz appeared here",
				sectionTitle = "Chapter 1"
			),
			results.results.single()
		)
	}

	@Test
	fun bridgeEventsDecodeFlattenedTocItemsWithDepth() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "toc",
			  "items": [
			    {
			      "id": "toc-0",
			      "title": "Part One",
			      "href": "EPUB/Text/part-01.xhtml",
			      "level": 0
			    },
			    {
			      "id": "toc-0-0",
			      "title": "Chapter 1",
			      "href": "EPUB/Text/chapter-01.xhtml",
			      "level": 1
			    }
			  ]
			}
			""".trimIndent()
		)

		val toc = assertIs<ReaderBridgeEvent.Toc>(event)
		assertEquals(
			listOf(
				ReaderTocItem(
					id = "toc-0",
					title = "Part One",
					href = "EPUB/Text/part-01.xhtml",
					level = 0
				),
				ReaderTocItem(
					id = "toc-0-0",
					title = "Chapter 1",
					href = "EPUB/Text/chapter-01.xhtml",
					level = 1
				)
			),
			toc.items
		)
	}

	@Test
	fun bridgeEventsDecodePublicationReadyAfterOpen() {
		assertIs<ReaderBridgeEvent.PublicationReady>(
			decodeReaderBridgeEvent("""{"type":"publicationReady"}""")
		)
	}

	@Test
	fun bridgeEventsDecodeReaderCenterTap() {
		assertIs<ReaderBridgeEvent.CenterTap>(
			decodeReaderBridgeEvent("""{"type":"readerCenterTap"}""")
		)
	}

	@Test
	fun bridgeEventsDecodeTypedContentActionClaims() {
		assertEquals(
			ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.Link),
			decodeReaderBridgeEvent("""{"type":"readerContentTapHandled","source":"link"}""")
		)
		assertEquals(
			ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.Link),
			decodeReaderBridgeEvent("""{"type":"readerContentTapHandled","source":"link-touch"}""")
		)
		assertEquals(
			ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.Image),
			decodeReaderBridgeEvent("""{"type":"readerContentTapHandled","source":"image"}""")
		)
		assertEquals(
			ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.MediaControl),
			decodeReaderBridgeEvent("""{"type":"readerContentTapHandled","source":"media-touch"}""")
		)
		assertEquals(
			ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.MediaControl),
			decodeReaderBridgeEvent("""{"type":"readerContentTapHandled","source":"media-anchor"}""")
		)
		assertEquals(
			ReaderBridgeEvent.ContentTapHandled(ReaderContentAction.Generic),
			decodeReaderBridgeEvent("""{"type":"readerContentTapHandled","source":"unknown"}""")
		)
	}

	@Test
	fun bridgeEventDecodeIgnoresMalformedOrUnknownMessages() {
		assertNull(decodeReaderBridgeEvent("not-json"))
		assertNull(decodeReaderBridgeEvent("""{"type":"unknown"}"""))
	}
}

package paige.navic.reader

import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderBridgeProtocolTest {
	@Test
	fun causalCommandsSerializeOpaqueSequence() {
		val navigation = ReaderBridgeCommand.CausalNextPage(causalSequence = 17L).toJavaScript()
		val selection = ReaderBridgeCommand.ContentLongPressAt(
			x = 10.0,
			y = 20.0,
			causalSequence = 18L
		).toJavaScript()

		assertContains(navigation, "\"type\":\"nextPage\"")
		assertContains(navigation, "\"causalSequence\":17")
		assertContains(selection, "\"type\":\"contentLongPressAt\"")
		assertContains(selection, "\"causalSequence\":18")
	}

	@Test
	fun causalPresentationEventsDecodeSequenceAndDestinationIdentity() {
		val location = assertIs<ReaderBridgeEvent.LocationChanged>(
			decodeReaderBridgeEvent(
				"""{"type":"locationChanged","href":"Text/chapter.xhtml","foliateSessionId":"session-a","causalSequence":17,"destinationCommitSequence":41}"""
			)
		)
		val range = assertIs<ReaderBridgeEvent.VisibleTextRange>(
			decodeReaderBridgeEvent(
				"""{"type":"visibleTextRange","textHref":"Text/chapter.xhtml","visibleStart":10,"visibleEnd":30,"causalSequence":17,"destinationFoliateSessionId":"session-a","destinationCommitSequence":41}"""
			)
		)
		val point = assertIs<ReaderBridgeEvent.TextPoint>(
			decodeReaderBridgeEvent(
				"""{"type":"textPoint","textHref":"Text/chapter.xhtml","textOffset":12,"causalSequence":18,"destinationFoliateSessionId":"session-a","destinationCommitSequence":41}"""
			)
		)
		val identity = ReaderDestinationCommitIdentity("session-a", 41L)

		assertEquals(17L, location.causalSequence)
		assertEquals(identity, location.destinationCommitIdentity)
		assertEquals(17L, range.causalSequence)
		assertEquals(identity, range.destinationCommitIdentity)
		assertEquals(18L, point.causalSequence)
		assertEquals(identity, point.destinationCommitIdentity)
	}

	@Test
	fun acknowledgedCommandEnvelopeCarriesStableIdBesideExistingPayload() {
		val script = ReaderBridgeDispatchCommand(
			id = "reader-open-1",
			command = ReaderBridgeCommand.OpenPublication(
				url = "https://bindery.local/books/1.epub",
				foliateSessionId = "session-a",
				startLocator = ReaderLocator(cfi = "epubcfi(/6/2!/4/1:0)")
			)
		).toJavaScript()

		assertContains(script, "window.NavicReaderBridge.dispatch")
		assertContains(script, "\"commandId\":\"reader-open-1\"")
		assertContains(script, "\"type\":\"openPublication\"")
		assertContains(script, "\"url\":\"https://bindery.local/books/1.epub\"")
		assertContains(script, "\"foliateSessionId\":\"session-a\"")
		assertContains(script, "epubcfi(/6/2!/4/1:0)")
	}

	@Test
	fun openPublicationRequiresBoundFoliateSessionBeforeSerialization() {
		val command = ReaderBridgeCommand.OpenPublication(
			url = "https://bindery.local/books/1.epub",
			foliateSessionId = ReaderUnboundFoliateSessionId
		)

		assertFailsWith<IllegalStateException> { command.toJsonObject() }
	}

	@Test
	fun commandAcknowledgementDecodesOnlyWithNonBlankStableId() {
		val decoded = decodeReaderBridgeMessage(
			"""{"type":"commandAck","commandId":"reader-command-1-7"}"""
		)

		assertEquals(
			ReaderBridgeDecodeResult.Decoded(
				ReaderBridgeEvent.CommandAcknowledged("reader-command-1-7")
			),
			decoded
		)
		listOf(
			"""{"type":"commandAck"}""",
			"""{"type":"commandAck","commandId":""}""",
			"""{"type":"commandAck","commandId":"   "}"""
		).forEach { message ->
			val rejected = assertIs<ReaderBridgeDecodeResult.Rejected>(
				decodeReaderBridgeMessage(message)
			)
			assertEquals(ReaderBridgeDecodeFailure.InvalidPayload, rejected.failure)
		}
	}

	@Test
	fun commandFailureDecodesOnlyWithNonBlankStableId() {
		val decoded = decodeReaderBridgeMessage(
			"""{"type":"commandFailed","commandId":"reader-command-1-7"}"""
		)

		assertEquals(
			ReaderBridgeDecodeResult.Decoded(
				ReaderBridgeEvent.CommandFailed("reader-command-1-7")
			),
			decoded
		)
		val rejected = assertIs<ReaderBridgeDecodeResult.Rejected>(
			decodeReaderBridgeMessage("""{"type":"commandFailed"}""")
		)
		assertEquals(ReaderBridgeDecodeFailure.InvalidPayload, rejected.failure)
	}

	@Test
	fun openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge() {
		val script = ReaderBridgeCommand.OpenPublication(
			url = "https://bindery.local/opds/books/3693/resources/readaloud-1?title=\"Alcatraz\"",
			foliateSessionId = "session-a",
			mediaOverlayEnabled = true,
			externalShellCover = true,
			suppressWebShellCover = true,
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
				fontWeight = 650.0,
				letterSpacing = 1.25,
				wordSpacing = 2.5,
				sideMargin = 12.0,
				topMargin = 80.0,
				bottomMargin = 60.0,
				indent = 1.5,
				headingFontSize = 1.25,
				maxColumnCount = 2,
				columnThreshold = 840.0,
				dimOverlayPercent = 30,
				colorFilterEnabled = true,
				colorFilterArgb = 0x66336699,
				colorFilterMode = ReaderColorFilterModeMultiply,
				grayscaleEnabled = true,
				invertedColors = true,
				orientation = ReaderOrientationLockedLandscape,
				theme = ReaderDuskTheme,
				direction = ReaderDirectionRtl,
				navBarType = ReaderNavBarTypeBottom,
				dragAnimationMode = ReaderDragAnimationNone,
				pageBitmapQuality = ReaderPageBitmapQuality.High.persistedValue,
				paged = false,
				tapZone = ReaderTapZoneKindle,
				tapZoneInvertMode = ReaderTapZoneInvertHorizontal,
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
		assertContains(script, "\"foliateSessionId\":\"session-a\"")
		assertContains(script, "\"mediaOverlayEnabled\":true")
		assertContains(script, "\"externalShellCover\":true")
		assertContains(script, "\"suppressWebShellCover\":true")
		assertContains(script, "Navic Literata")
		assertContains(script, "Bookerly, Georgia, serif")
		assertContains(script, "\"fontSource\":\"custom\"")
		assertContains(script, "\"customFontFamily\":\"Storyteller Serif\"")
		assertContains(script, "\"customFontUrl\":\"https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf\"")
		assertContains(script, "\"fontSizePercent\":112")
		assertContains(script, "\"lineHeight\":1.7")
		assertContains(script, "\"paragraphSpacingPercent\":75")
		assertContains(script, "\"marginPercent\":8")
		assertContains(script, "\"fontWeight\":650.0")
		assertContains(script, "\"letterSpacing\":1.25")
		assertContains(script, "\"wordSpacing\":2.5")
		assertContains(script, "\"sideMargin\":12.0")
		assertContains(script, "\"topMargin\":80.0")
		assertContains(script, "\"bottomMargin\":60.0")
		assertContains(script, "\"indent\":1.5")
		assertContains(script, "\"headingFontSize\":1.25")
		assertContains(script, "\"maxColumnCount\":2")
		assertContains(script, "\"columnThreshold\":840.0")
		assertContains(script, "\"dimOverlayPercent\":30")
		assertContains(script, "\"pageBitmapQuality\":\"75\"")
		assertContains(script, "\"colorFilterEnabled\":true")
		assertContains(script, "\"colorFilterArgb\":1714644633")
		assertContains(script, "\"colorFilterMode\":\"multiply\"")
		assertContains(script, "\"grayscaleEnabled\":true")
		assertContains(script, "\"invertedColors\":true")
		assertContains(script, "\"orientation\":\"locked-landscape\"")
		assertContains(script, "\"theme\":\"dusk\"")
		assertContains(script, "\"direction\":\"rtl\"")
		assertContains(script, "\"navBarType\":\"bottom\"")
		assertContains(script, "\"dragAnimationMode\":\"none\"")
		assertContains(script, "\"paged\":false")
		assertContains(script, "\"tapZone\":\"kindle\"")
		assertContains(script, "\"tapZoneInvertMode\":\"horizontal\"")
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
				overlayRequestId = 47L,
				fragmentId = "frag-1",
				textHref = "EPUB/Text/chapter1.xhtml",
				clipBeginSeconds = 1.25,
				clipEndSeconds = 3.5,
				textStart = 10,
				textEnd = 42,
				textProgressEnd = 24,
				spokenText = "I am not a good person.",
				ebookText = "Alcatraz Versus the Evil Librarian AUTHOR’S FOREWORD. I AM NOT A GOOD PERSON",
				nextTextHref = "EPUB/Text/chapter1.xhtml",
				nextTextStart = 81,
				nextTextEnd = 121,
				nextEbookText = "OH, I KNOW WHAT THE STORIES SAY ABOUT ME",
				label = "Chapter 1 / Paragraph 1"
			)
		).toJavaScript()

		assertContains(script, "\"type\":\"applyOverlayFragment\"")
		assertContains(script, "\"resourceHref\":\"EPUB/Audio/chapter1.mp3\"")
		assertContains(script, "\"overlayRequestId\":47")
		assertContains(script, "\"fragmentId\":\"frag-1\"")
		assertContains(script, "\"textHref\":\"EPUB/Text/chapter1.xhtml\"")
		assertContains(script, "\"clipBeginSeconds\":1.25")
		assertContains(script, "\"clipEndSeconds\":3.5")
		assertContains(script, "\"textStart\":10")
		assertContains(script, "\"textEnd\":42")
		assertContains(script, "\"textProgressEnd\":24")
		assertContains(script, "\"spokenText\":\"I am not a good person.\"")
		assertContains(script, "\"ebookText\":\"Alcatraz Versus the Evil Librarian AUTHOR")
		assertContains(script, "\"nextTextHref\":\"EPUB/Text/chapter1.xhtml\"")
		assertContains(script, "\"nextTextStart\":81")
		assertContains(script, "\"nextTextEnd\":121")
		assertContains(script, "\"nextEbookText\":\"OH, I KNOW WHAT THE STORIES SAY ABOUT ME\"")
		assertContains(script, "\"label\":\"Chapter 1 / Paragraph 1\"")
	}

	@Test
	fun updateOverlayProgressCommandDispatchesFragmentProgressOnly() {
		val fragment = ReaderOverlayFragment(
			resourceHref = "EPUB/Audio/chapter1.mp3",
			overlayRequestId = 48L,
			fragmentId = "frag-1",
			textHref = "EPUB/Text/chapter1.xhtml",
			clipBeginSeconds = 1.25,
			clipEndSeconds = 3.5,
			textStart = 10,
			textEnd = 42,
			textProgressEnd = 24,
			label = "Chapter 1 / Paragraph 1"
		)

		val script = ReaderBridgeCommand.UpdateOverlayFragmentProgress(fragment).toJavaScript()

		assertContains(script, "\"type\":\"updateOverlayFragmentProgress\"")
		assertContains(script, "\"fragment\"")
		assertContains(script, "\"overlayRequestId\":48")
		assertContains(script, "\"textStart\":10")
		assertContains(script, "\"textEnd\":42")
		assertContains(script, "\"textProgressEnd\":24")
	}

	@Test
	fun requestVisibleTextRangeCommandDispatchesFreshObservationSource() {
		val script = ReaderBridgeCommand.RequestVisibleTextRange(
			source = "shell-cover-dismissed"
		).toJavaScript()

		assertContains(script, "\"type\":\"requestVisibleTextRange\"")
		assertContains(script, "\"source\":\"shell-cover-dismissed\"")
	}

	@Test
	fun productionCueMapCommandCarriesVisibleProductionRangesAndOrdinalOnlyStates() {
		val script = ReaderBridgeCommand.ReplaceWhispersyncCueMap(
			ReaderWhispersyncCueMapPresentation(
				enabled = true,
				revisionDigest = "5f04c2a19e7d",
				presentationGeneration = 8L,
				destinationCommitIdentity = ReaderDestinationCommitIdentity("session-a", 41L),
				cues = listOf(
					ReaderWhispersyncCueMapCue(
						sourceOrdinal = 7,
						textHref = "Text/chapter.xhtml",
						textStart = 10,
						textEnd = 30,
						ebookText = "Ephemeral production mapper input"
					)
				),
				preparedSourceOrdinal = 7,
				requestedSourceOrdinal = 7,
				audioActiveSourceOrdinal = 9,
				renderedHighlightSourceOrdinal = 11,
				transportAcknowledgementPending = true
			)
		).toJavaScript()

		assertContains(script, "\"type\":\"replaceWhispersyncCueMap\"")
		assertContains(script, "\"sourceOrdinal\":7")
		assertContains(script, "\"revisionDigest\":\"5f04c2a19e7d\"")
		assertContains(script, "\"preparedSourceOrdinal\":7")
		assertContains(script, "\"requestedSourceOrdinal\":7")
		assertContains(script, "\"audioActiveSourceOrdinal\":9")
		assertContains(script, "\"renderedHighlightSourceOrdinal\":11")
		assertContains(script, "\"transportAcknowledgementPending\":true")
	}

	@Test
	fun canonicalBinderyCueBypassesDomOffsetPrefilterAndSerializesRawProvenance() {
		val href = "OEBPS/Text/public-synthetic.xhtml"
		val sourceXhtml = "<html><body><p>Café &amp; café</p><p>Only synthetic cue locator</p></body></html>"
		val extractedText = "Café & café. Only synthetic cue locator."
		val locator = "Only synthetic cue locator"
		val characterStart = extractedText.indexOf(locator)
		val rawStart = extractedText.substring(0, characterStart).encodeUtf8().size
		val rawEnd = rawStart + locator.encodeUtf8().size
		val tokenCount = Regex("[A-Za-z0-9]+(?:['’][A-Za-z0-9]+)?")
			.findAll(extractedText)
			.count()
		val sourceHash = "sha256:${sourceXhtml.encodeUtf8().sha256().hex()}"
		val extractedTextHash = "sha256:${extractedText.encodeUtf8().sha256().hex()}"
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "coordinateBasis": {
			    "extractor": "bindery-epub-text",
			    "extractorVersion": "1",
			    "normalization": "raw-extracted-text-offsets",
			    "unit": "utf8-byte",
			    "scope": "spine",
			    "spines": [{
			      "href": "$href",
			      "spineIndex": 2,
			      "sourceHash": "$sourceHash",
			      "extractedTextHash": "$extractedTextHash",
			      "byteLength": ${extractedText.encodeUtf8().size},
			      "tokenCount": $tokenCount
			    }]
			  },
			  "cues": [{
			    "id": 1040,
			    "audioHref": "Audio/public-synthetic.mp3",
			    "audioStart": 1,
			    "audioEnd": 2,
			    "ebookHref": "$href",
			    "spineIndex": 2,
			    "ebookStart": $rawStart,
			    "ebookEnd": $rawEnd,
			    "ebookText": "$locator"
			  }]
			}
			""".trimIndent()
		)
		val destination = ReaderDestinationCommitIdentity("synthetic-session", 41L)
		val cueMap = ReaderWhispersyncCueMapState(
			enabled = true,
			presentationGeneration = 1L
		)
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val canonicalCue = ReaderWhispersyncCueMapCue(
			sourceOrdinal = 0,
			textHref = href,
			textStart = rawStart,
			textEnd = rawEnd,
			ebookText = locator,
			coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
			rawProvenanceId = descriptor.id,
			rawSpineIndex = descriptor.spineIndex,
			rawByteStart = rawStart,
			rawByteEnd = rawEnd
		)
		val state = ReaderControllerState(
			destinationCommitIdentity = destination,
			rawTextProvenanceById = mapOf(
				descriptor.id to RawTextProvenanceState(RawTextProvenanceStatus.Ready)
			),
			whispersync = ReaderWhispersyncSessionState(
				sidecar = sidecar,
				visibleTextRange = ReaderWhispersyncVisibleTextRange(
					textHref = href,
					visibleStart = 0,
					visibleEnd = 8,
					rawProvenanceId = descriptor.id,
					rawSpineIndex = descriptor.spineIndex,
					rawByteStart = 0,
					rawByteEnd = descriptor.byteLength,
					destinationCommitIdentity = destination
				),
				canonicalPreflightGeneration = 1L,
				canonicalPreflight = ReaderWhispersyncCanonicalPreflightState(
					request = ReaderWhispersyncCanonicalPreflightRequest(
						revisionDigest = sidecar.revisionDigest,
						validationGeneration = 1L,
						destinationCommitIdentity = destination,
						provenanceId = descriptor.id,
						rawSpineIndex = descriptor.spineIndex,
						cues = listOf(canonicalCue)
					),
					status = ReaderWhispersyncCanonicalPreflightStatus.Ready
				),
				cueMap = cueMap
			)
		)

		val presentation = requireNotNull(cueMap.presentation(state))
		assertEquals(
			listOf(0),
			presentation.cues.map(ReaderWhispersyncCueMapCue::sourceOrdinal),
			"Kotlin must not compare Bindery UTF-8 bytes with Foliate DOM-visible offsets"
		)
		val script = ReaderBridgeCommand.ReplaceWhispersyncCueMap(presentation).toJavaScript()
		assertContains(script, "\"rawProvenanceId\":")
		assertContains(script, "\"rawSpineIndex\":2")
		assertContains(script, "\"rawByteStart\":$rawStart")
		assertContains(script, "\"rawByteEnd\":$rawEnd")
	}

	@Test
	fun cueMapEventsDecodeOnlyBoundedOrdinalGenerationAndOutcomeEvidence() {
		val rendered = assertIs<ReaderBridgeEvent.WhispersyncCueMapRendered>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type":"whispersyncCueMapRendered",
				  "sourceOrdinals":[7,3,9],
				  "revisionDigest":"5f04c2a19e7d",
				  "presentationGeneration":8,
				  "destinationFoliateSessionId":"session-a",
				  "destinationCommitSequence":41,
				  "markerReceipts":[{
				    "sourceOrdinal":7,
				    "prepared":true,
				    "requested":false,
				    "audioActive":true,
				    "renderedHighlight":false,
				    "anchorReceipt":{
				      "foliateSessionId":"session-a",
				      "destinationCommitToken":"opaque-presentation-token",
				      "visualPageOrdinal":9,
				      "spineIndex":7,
				      "rasterGeneration":12,
				      "textureGeneration":13,
				      "presentationMutationGeneration":14,
				      "presentationSequence":15,
				      "anchorGeneration":16,
				      "boundarySequence":7,
				      "layoutGeneration":17,
				      "viewGeneration":18,
				      "commitSequence":41,
				      "committedSpineIndex":7,
				      "committedChapterPageIndex":9,
				      "committedChapterPageCount":127,
				      "paginationFingerprint":"pagination-safe",
				      "layoutFingerprint":"layout-safe",
				      "readerSettingsRasterKey":"settings-safe",
				      "captureGeometry":{
				        "viewportWidth":1200,
				        "viewportHeight":800,
				        "mode":"single",
				        "pages":[{"role":"full","left":0,"top":0,"width":1200,"height":800}]
				      },
				      "pageLocalRects":[{"role":"full","left":80,"top":120,"width":180,"height":24}]
				    }
				  }],
				  "text":"must-not-survive",
				  "href":"must-not-survive",
				  "bookId":"must-not-survive"
				}
				""".trimIndent()
			)
		)
		val seek = assertIs<ReaderBridgeEvent.WhispersyncCueMapSeekRequested>(
			decodeReaderBridgeEvent(
				"""{"type":"whispersyncCueMapSeekRequested","sourceOrdinal":3,"revisionDigest":"5f04c2a19e7d","presentationGeneration":8,"destinationFoliateSessionId":"session-a","destinationCommitSequence":41,"payload":"must-not-survive"}"""
			)
		)
		val cancelled = assertIs<ReaderBridgeEvent.WhispersyncCueMapHoldOutcome>(
			decodeReaderBridgeEvent(
				"""{"type":"whispersyncCueMapHoldOutcome","sourceOrdinal":3,"revisionDigest":"5f04c2a19e7d","presentationGeneration":8,"outcome":"cancelled-curl-start"}"""
			)
		)

		assertEquals(listOf(7, 3, 9), rendered.sourceOrdinalsInDomReadingOrder)
		assertEquals(listOf(7), rendered.markerReceipts.map { marker -> marker.sourceOrdinal })
		assertEquals(7L, rendered.markerReceipts.single().anchorReceipt.boundarySequence)
		assertTrue(rendered.markerReceipts.single().prepared)
		assertTrue(rendered.markerReceipts.single().audioActive)
		assertEquals(3, seek.sourceOrdinal)
		assertEquals(ReaderWhispersyncCueMapHoldOutcome.CancelledCurlStart, cancelled.outcome)
		assertFalse(rendered.toString().contains("must-not-survive"))
		assertFalse(seek.toString().contains("must-not-survive"))
		assertTrue(rendered.sourceOrdinalsInDomReadingOrder.size <= 32)
	}

	@Test
	fun cueMapRenderedDecodesCompleteVisibleMarkerGeometryBeyondBoundedOrdinalEvidence() {
		val markerReceipt = { sourceOrdinal: Int ->
			"""
				{"sourceOrdinal":$sourceOrdinal,"anchorReceipt":{"foliateSessionId":"session-a","destinationCommitToken":"opaque-presentation-token","visualPageOrdinal":9,"spineIndex":7,"rasterGeneration":12,"textureGeneration":13,"presentationMutationGeneration":14,"presentationSequence":15,"anchorGeneration":16,"boundarySequence":$sourceOrdinal,"layoutGeneration":17,"viewGeneration":18,"commitSequence":41,"committedSpineIndex":7,"committedChapterPageIndex":9,"committedChapterPageCount":127,"paginationFingerprint":"pagination-safe","layoutFingerprint":"layout-safe","readerSettingsRasterKey":"settings-safe","captureGeometry":{"viewportWidth":1200,"viewportHeight":800,"mode":"single","pages":[{"role":"full","left":0,"top":0,"width":1200,"height":800}]},"pageLocalRects":[{"role":"full","left":80,"top":120,"width":180,"height":24}]}}
			""".trimIndent()
		}
		val rendered = assertIs<ReaderBridgeEvent.WhispersyncCueMapRendered>(
			decodeReaderBridgeEvent(
				"""
				{"type":"whispersyncCueMapRendered","sourceOrdinals":[${(5..36).joinToString()}],"revisionDigest":"5f04c2a19e7d","presentationGeneration":8,"destinationFoliateSessionId":"session-a","destinationCommitSequence":41,"markerReceipts":[${(5..49).joinToString(separator = ",") { markerReceipt(it) }}]}
				""".trimIndent()
			)
		)

		assertEquals((5..36).toList(), rendered.sourceOrdinalsInDomReadingOrder)
		assertEquals((5..49).toList(), rendered.markerReceipts.map { marker -> marker.sourceOrdinal })
		assertEquals((5..49).map(Int::toLong), rendered.markerReceipts.map { marker ->
			marker.anchorReceipt.boundarySequence
		})
	}

	@Test
	fun applySettingsCommandDispatchesWhispersyncListeningOverlaySettings() {
		val script = ReaderBridgeCommand.ApplySettings(
			ReaderSettings(
				readaloudSyncEnabled = true,
				whispersyncHighlightLeadMs = 750,
				whispersyncHighlightColorArgb = 0x66F6C343,
				whispersyncHighlightLoading = "persistent-played-text",
				whispersyncHighlightStyle = "marker",
				paperTextureEnabled = false,
				pageEdgesEnabled = false,
				paperStainsEnabled = false,
				coverBackdropEnabled = false
			)
		).toJavaScript()

		assertContains(script, "\"type\":\"applySettings\"")
		assertContains(script, "\"readaloudSyncEnabled\":true")
		assertContains(script, "\"whispersyncHighlightLeadMs\":750")
		assertContains(script, "\"whispersyncHighlightColorArgb\":1727447875")
		assertContains(script, "\"whispersyncHighlightLoading\":\"persistent-played-text\"")
		assertContains(script, "\"whispersyncHighlightStyle\":\"marker\"")
		assertContains(script, "\"paperTextureEnabled\":false")
		assertContains(script, "\"pageEdgesEnabled\":false")
		assertContains(script, "\"paperStainsEnabled\":false")
		assertContains(script, "\"coverBackdropEnabled\":false")
	}

	@Test
	fun goToCfiCommandDispatchesSearchResultNavigationTarget() {
		val script = ReaderBridgeCommand.GoToCfi("epubcfi(/6/8!/4/1:0)").toJavaScript()

		assertContains(script, "\"type\":\"goToCfi\"")
		assertContains(script, "\"cfi\":\"epubcfi(/6/8!/4/1:0)\"")
	}

	@Test
	fun goToLocatorCommandCarriesControlledRelocationReason() {
		val script = ReaderBridgeCommand.GoToLocator(
			locator = ReaderLocator(
				href = "Text/chapter1.xhtml",
				pageIndex = 42,
				pageCount = 373
			),
			reason = "shell-cover-dismiss:7"
		).toJavaScript()

		assertContains(script, "\"type\":\"goToLocator\"")
		assertContains(script, "\"href\":\"Text/chapter1.xhtml\"")
		assertContains(script, "\"pageIndex\":42")
		assertContains(script, "\"reason\":\"shell-cover-dismiss:7\"")
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
	fun clearSearchCommandDispatchesAnxSearchClearIntent() {
		val script = ReaderBridgeCommand.ClearSearch.toJavaScript()

		assertContains(script, "window.NavicReaderBridge.dispatch")
		assertContains(script, "\"type\":\"clearSearch\"")
	}

	@Test
	fun pageDragPreviewCommandDispatchesRendererPreviewIntent() {
		val updateScript = ReaderBridgeCommand.PreviewPageDrag(
			deltaX = -184.0,
			deltaY = -96.0,
			viewWidth = 1440.0,
			viewHeight = 2200.0,
			phase = ReaderPageDragPreviewPhase.Update
		).toJavaScript()
		val releaseScript = ReaderBridgeCommand.PreviewPageDrag(
			deltaX = -512.0,
			deltaY = -256.0,
			viewWidth = 1440.0,
			viewHeight = 2200.0,
			phase = ReaderPageDragPreviewPhase.Release
		).toJavaScript()
		val cancelScript = ReaderBridgeCommand.PreviewPageDrag(
			deltaX = Double.NaN,
			deltaY = Double.NaN,
			viewWidth = Double.POSITIVE_INFINITY,
			viewHeight = Double.NEGATIVE_INFINITY,
			phase = ReaderPageDragPreviewPhase.Cancel
		).toJavaScript()

		assertContains(updateScript, "window.NavicReaderBridge.dispatch")
		assertContains(updateScript, "\"type\":\"previewPageDrag\"")
		assertContains(updateScript, "\"deltaX\":-184.0")
		assertContains(updateScript, "\"deltaY\":-96.0")
		assertContains(updateScript, "\"viewWidth\":1440.0")
		assertContains(updateScript, "\"viewHeight\":2200.0")
		assertContains(updateScript, "\"phase\":\"update\"")
		assertContains(releaseScript, "\"phase\":\"release\"")
		assertContains(cancelScript, "\"deltaX\":0.0")
		assertContains(cancelScript, "\"deltaY\":0.0")
		assertContains(cancelScript, "\"phase\":\"cancel\"")
		assertFalse(cancelScript.contains("\"viewWidth\""))
		assertFalse(cancelScript.contains("\"viewHeight\""))
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
	fun contentLongPressCommandDispatchesNativeCoordinateIntent() {
		val script = ReaderBridgeCommand.ContentLongPressAt(
			x = 250.0,
			y = 500.0,
			viewWidth = 500.0,
			viewHeight = 1000.0
		).toJavaScript()

		assertContains(script, "window.NavicReaderBridge.dispatch")
		assertContains(script, "\"type\":\"contentLongPressAt\"")
		assertContains(script, "\"x\":250.0")
		assertContains(script, "\"y\":500.0")
		assertContains(script, "\"viewWidth\":500.0")
		assertContains(script, "\"viewHeight\":1000.0")
		assertContains(script, "\"selectText\":true")
	}

	@Test
	fun contentLongPressCommandCanRequestWhispersyncTextPointWithoutSelection() {
		val script = ReaderBridgeCommand.ContentLongPressAt(
			x = 250.0,
			y = 500.0,
			viewWidth = 500.0,
			viewHeight = 1000.0,
			selectText = false
		).toJavaScript()

		assertContains(script, "\"type\":\"contentLongPressAt\"")
		assertContains(script, "\"selectText\":false")
	}

	@Test
	fun selectionChangedDecodesAnxSelectionEndPayload() {
		val selection = assertIs<ReaderBridgeEvent.SelectionChanged>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "selectionChanged",
				  "text": "selected",
				  "cfi": "epubcfi(/6/8!/4/2:12)",
				  "href": "chapter-01.xhtml",
				  "footnote": true,
				  "contextText": "The selected sentence and its surrounding context.",
				  "pos": {
				    "left": 10.5,
				    "top": 20.25,
				    "right": 120.75,
				    "bottom": 140.0
				  }
				}
				""".trimIndent()
			)
		)

		assertEquals("selected", selection.text)
		assertEquals("epubcfi(/6/8!/4/2:12)", selection.cfi)
		assertEquals("chapter-01.xhtml", selection.href)
		assertEquals(true, selection.footnote)
		assertEquals("The selected sentence and its surrounding context.", selection.contextText)
		assertEquals(10.5, selection.posLeft)
		assertEquals(20.25, selection.posTop)
		assertEquals(120.75, selection.posRight)
		assertEquals(140.0, selection.posBottom)
	}

	@Test
	fun progressSeekCommandDispatchesClampedFractionNavigationIntent() {
		val script = ReaderBridgeCommand.GoToProgress(1.4).toJavaScript()

		assertContains(script, "window.NavicReaderBridge.dispatch")
		assertContains(script, "\"type\":\"goToProgress\"")
		assertContains(script, "\"progress\":1.0")
	}

	@Test
	fun chapterProgressSeekCommandDispatchesExactNativeRailTarget() {
		val script = ReaderBridgeCommand.GoToChapterProgress(
			href = "chapter-01.xhtml",
			progress = 0.375,
			chapterPageIndex = 3,
			chapterPageCount = 9
		).toJavaScript()

		assertContains(script, "window.NavicReaderBridge.dispatch")
		assertContains(script, "\"type\":\"goToChapterProgress\"")
		assertContains(script, "\"href\":\"chapter-01.xhtml\"")
		assertContains(script, "\"progress\":0.375")
		assertContains(script, "\"chapterPageIndex\":3")
		assertContains(script, "\"chapterPageCount\":9")
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
					note = "Remember this scene later",
					sectionTitle = "Chapter 1"
				)
			)
		).toJavaScript()

		assertContains(script, "\"type\":\"applyHighlights\"")
		assertContains(script, "\"highlights\"")
		assertContains(script, "\"cfi\":\"epubcfi(/6/8!/4/1:0)\"")
		assertContains(script, "\"color\":\"#f4d35e\"")
		assertContains(script, "\"note\":\"Remember this scene later\"")
	}

	@Test
	fun locationChangedRequiresIndependentSessionAndValidatesSettlementMetadata() {
		val decoded = assertIs<ReaderBridgeEvent.LocationChanged>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "locationChanged",
				  "foliateSessionId": "session-a",
				  "pageIndex": 7,
				  "pageTurnSettleToken": "settle-1",
				  "pageTurnSettleSessionId": "session-a",
				  "pageTurnSettleRasterGeneration": 11,
				  "pageTurnSettleTextureGeneration": 13
				}
				""".trimIndent()
			)
		)

		assertEquals("session-a", decoded.foliateSessionId)
		assertEquals("settle-1", decoded.pageTurnSettleToken)
		assertEquals("session-a", decoded.pageTurnSettleSessionId)
		assertEquals(11L, decoded.pageTurnSettleRasterGeneration)
		assertEquals(13L, decoded.pageTurnSettleTextureGeneration)

		val missingSession = assertIs<ReaderBridgeDecodeResult.Rejected>(
			decodeReaderBridgeMessage("""{"type":"locationChanged","pageIndex":7}""")
		)
		assertEquals(ReaderBridgeDecodeFailure.InvalidPayload, missingSession.failure)

		val mismatched = assertIs<ReaderBridgeEvent.LocationChanged>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "locationChanged",
				  "foliateSessionId": "session-a",
				  "pageIndex": 7,
				  "pageTurnSettleToken": "settle-1",
				  "pageTurnSettleSessionId": "session-b",
				  "pageTurnSettleRasterGeneration": 11,
				  "pageTurnSettleTextureGeneration": 13
				}
				""".trimIndent()
			)
		)
		assertNull(mismatched.pageTurnSettleToken)
		assertNull(mismatched.pageTurnSettleSessionId)
		assertNull(mismatched.pageTurnSettleRasterGeneration)
		assertNull(mismatched.pageTurnSettleTextureGeneration)

		val incomplete = assertIs<ReaderBridgeEvent.LocationChanged>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "locationChanged",
				  "foliateSessionId": "session-a",
				  "pageIndex": 7,
				  "pageTurnSettleToken": "settle-1",
				  "pageTurnSettleSessionId": "session-a",
				  "pageTurnSettleRasterGeneration": 11
				}
				""".trimIndent()
			)
		)
		assertNull(incomplete.pageTurnSettleToken)
		assertNull(incomplete.pageTurnSettleSessionId)
		assertNull(incomplete.pageTurnSettleRasterGeneration)
		assertNull(incomplete.pageTurnSettleTextureGeneration)
	}

	@Test
	fun bridgeEventsDecodeReaderLocationAndOverlayEvents() {
		val location = decodeReaderBridgeEvent(
			"""
			{
			  "type": "locationChanged",
			  "foliateSessionId": "session-a",
			  "href": "chapter-01.xhtml",
			  "cfi": "epubcfi(/6/2!/4/1:0)",
			  "progress": 0.24,
			  "rangeCfi": "epubcfi(/6/2!/4/1:0,/1:0,/1:12)",
			  "reason": "page",
			  "fraction": 0.42,
			  "size": 0.08,
			  "tocItemLabel": "Chapter 1",
			  "pageItemLabel": "Page 7",
			  "tocTitle": "Chapter 1"
			}
			""".trimIndent()
		)
		val overlay = decodeReaderBridgeEvent(
			"""
			{
			  "type": "overlayFragmentActive",
			  "resourceHref": "audio/part01.mp3",
			  "overlayRequestId": 47,
			  "fragmentId": "frag-1",
			  "textHref": "chapter-01.xhtml#frag-1",
			  "clipBeginSeconds": 12.4,
			  "clipEndSeconds": 16.9,
			  "textStart": 120,
			  "textEnd": 180,
			  "textProgressEnd": 144,
			  "label": "Chapter 1 / Paragraph 4"
			}
			""".trimIndent()
		)

		val locationChanged = assertIs<ReaderBridgeEvent.LocationChanged>(location)
		assertEquals("chapter-01.xhtml", locationChanged.locator.href)
		assertEquals("epubcfi(/6/2!/4/1:0)", locationChanged.locator.cfi)
		assertEquals(0.24, locationChanged.locator.progress)
		assertEquals("epubcfi(/6/2!/4/1:0,/1:0,/1:12)", locationChanged.locator.rangeCfi)
		assertEquals("page", locationChanged.locator.reason)
		assertEquals(0.42, locationChanged.locator.fraction)
		assertEquals(0.08, locationChanged.locator.size)
		assertEquals("Chapter 1", locationChanged.locator.tocItemLabel)
		assertEquals("Page 7", locationChanged.locator.pageItemLabel)
		assertEquals("Chapter 1", locationChanged.tocTitle)

		val active = assertIs<ReaderBridgeEvent.OverlayFragmentActive>(overlay)
		assertEquals("audio/part01.mp3", active.fragment.resourceHref)
		assertEquals(47L, active.fragment.overlayRequestId)
		assertEquals("frag-1", active.fragment.fragmentId)
		assertEquals("chapter-01.xhtml#frag-1", active.fragment.textHref)
		assertEquals(12.4, active.fragment.clipBeginSeconds)
		assertEquals(16.9, active.fragment.clipEndSeconds)
		assertEquals(120, active.fragment.textStart)
		assertEquals(180, active.fragment.textEnd)
		assertEquals(144, active.fragment.textProgressEnd)
		assertEquals("Chapter 1 / Paragraph 4", active.fragment.label)
	}

	@Test
	fun bridgeEventsDecodeOverlayRejectionIdentityAndReason() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "overlayFragmentInactive",
			  "fragmentId": "frag-1",
			  "overlayRequestId": 47,
			  "coordinateMode": "wordsync-v1-extracted-utf8",
			  "reason": "paint-rejected"
			}
			""".trimIndent()
		)

		val inactive = assertIs<ReaderBridgeEvent.OverlayFragmentInactive>(event)
		assertEquals("frag-1", inactive.fragmentId)
		assertEquals(47L, inactive.overlayRequestId)
		assertEquals(ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8, inactive.coordinateMode)
		assertEquals("paint-rejected", inactive.reason)
	}

	@Test
	fun bridgeEventsDecodeVisibleTextRangeForWhispersync() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "visibleTextRange",
			  "textHref": "Text/chapter-01.xhtml",
			  "visibleStart": 80,
			  "visibleEnd": 140,
			  "rangeCfi": "epubcfi(/6/2!/4/4,/1:0,/1:24)",
			  "source": "media-overlay-follow"
			}
			""".trimIndent()
		)

		val range = assertIs<ReaderBridgeEvent.VisibleTextRange>(event)
		assertEquals("Text/chapter-01.xhtml", range.textHref)
		assertEquals(80, range.visibleStart)
		assertEquals(140, range.visibleEnd)
		assertEquals("epubcfi(/6/2!/4/4,/1:0,/1:24)", range.rangeCfi)
		assertEquals("media-overlay-follow", range.source)
	}

	@Test
	fun bridgeEventsDecodeTextPointForWhispersync() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "textPoint",
			  "textHref": "OEBPS/Text/authorsforeword.xhtml",
			  "textOffset": 82,
			  "rangeCfi": "epubcfi(/6/12!/4/8,/1:0,/1:8)",
			  "source": "native-long-press-command"
			}
			""".trimIndent()
		)

		val point = assertIs<ReaderBridgeEvent.TextPoint>(event)
		assertEquals("OEBPS/Text/authorsforeword.xhtml", point.textHref)
		assertEquals(82, point.textOffset)
		assertEquals("epubcfi(/6/12!/4/8,/1:0,/1:8)", point.rangeCfi)
		assertEquals("native-long-press-command", point.source)
	}

	@Test
	fun bridgeEventsDecodeFixedLayoutPagePosition() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "locationChanged",
			  "foliateSessionId": "session-a",
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
	fun bridgeEventsDecodeChapterLocalPagePositionForKomikkuRail() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "locationChanged",
			  "foliateSessionId": "session-a",
			  "href": "chapter-01.xhtml",
			  "progress": 0.12,
			  "pageIndex": 42,
			  "pageCount": 411,
			  "chapterPageIndex": 3,
			  "chapterPageCount": 18
			}
			""".trimIndent()
		)

		val locationChanged = assertIs<ReaderBridgeEvent.LocationChanged>(event)
		assertEquals(
			ReaderLocator(
				href = "chapter-01.xhtml",
				progress = 0.12,
				pageIndex = 42,
				pageCount = 411,
				chapterPageIndex = 3,
				chapterPageCount = 18
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
			  "progress": 0.5,
			  "complete": false,
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
		assertEquals(0.5, results.progress)
		assertEquals(false, results.complete)
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
	fun bridgeEventsDecodeAnxSearchProcessAsProgressAndCompletion() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "searchResults",
			  "query": "alcatraz",
			  "process": 1.0,
			  "results": []
			}
			""".trimIndent()
		)

		val results = assertIs<ReaderBridgeEvent.SearchResults>(event)
		assertEquals("alcatraz", results.query)
		assertEquals(1.0, results.progress)
		assertEquals(true, results.complete)
		assertEquals(emptyList(), results.results)
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
	fun bridgeEventsDecodePaginationProfileStatus() {
		val event = decodeReaderBridgeEvent(
			"""
			{
			  "type": "paginationProfileStatus",
			  "status": "measuring",
			  "fingerprint": "navic-pagination-v1:123",
			  "completedSections": 3,
			  "totalSections": 6,
			  "pageCount": 392,
			  "message": "profiling"
			}
			""".trimIndent()
		)

		val status = assertIs<ReaderBridgeEvent.PaginationProfileStatusChanged>(event)
		assertEquals(
			ReaderPaginationProfileStatus(
				status = "measuring",
				fingerprint = "navic-pagination-v1:123",
				completedSections = 3,
				totalSections = 6,
				pageCount = 392,
				message = "profiling"
			),
			status.profile
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
	fun bridgeEventsDecodeInternalLinkRequestsWithSuppressionMetadata() {
		val prevented = assertIs<ReaderBridgeEvent.InternalLinkRequested>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "internalLink",
				  "href": "EPUB/Text/chapter-02.xhtml#door",
				  "prevented": true,
				  "source": "native-short-tap"
				}
				""".trimIndent()
			)
		)
		val allowed = assertIs<ReaderBridgeEvent.InternalLinkRequested>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "internalLink",
				  "href": "EPUB/Text/chapter-02.xhtml#door",
				  "prevented": false,
				  "source": "link-long-press"
				}
				""".trimIndent()
			)
		)

		assertEquals("EPUB/Text/chapter-02.xhtml#door", prevented.href)
		assertEquals(true, prevented.prevented)
		assertEquals("native-short-tap", prevented.source)
		assertEquals("EPUB/Text/chapter-02.xhtml#door", allowed.href)
		assertEquals(false, allowed.prevented)
		assertEquals("link-long-press", allowed.source)
	}

	@Test
	fun bridgeEventsDecodePhase3AnxBridgeEvents() {
		val externalLink = assertIs<ReaderBridgeEvent.ExternalLink>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "externalLink",
				  "href": "https://example.test/notes",
				  "anchorHref": "../Text/chapter-01.xhtml#note"
				}
				""".trimIndent()
			)
		)
		val annotationClick = assertIs<ReaderBridgeEvent.AnnotationClick>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "annotationClick",
				  "value": "epubcfi(/6/8!/4/2:12)",
				  "index": 3,
				  "rangeCfi": "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
				}
				""".trimIndent()
			)
		)
		val annotationDrawn = assertIs<ReaderBridgeEvent.AnnotationDrawn>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "annotationDrawn",
				  "value": "epubcfi(/6/8!/4/2:12)",
				  "index": 3,
				  "rangeCfi": "epubcfi(/6/8!/4/2:12,/1:0,/1:8)"
				}
				""".trimIndent()
			)
		)
		val overlayCreated = assertIs<ReaderBridgeEvent.OverlayCreated>(
			decodeReaderBridgeEvent("""{"type":"overlayCreated","index":3}""")
		)
		val loadDoc = assertIs<ReaderBridgeEvent.LoadDoc>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "loadDoc",
				  "index": 3,
				  "href": "EPUB/Text/chapter-01.xhtml",
				  "title": "Chapter 1",
				  "sectionId": "chapter-01"
				}
				""".trimIndent()
				)
			)

		assertEquals("https://example.test/notes", externalLink.href)
		assertEquals("../Text/chapter-01.xhtml#note", externalLink.anchorHref)
		assertIs<ReaderBridgeEvent.SelectionCleared>(
			decodeReaderBridgeEvent("""{"type":"selectionCleared"}""")
		)
		assertEquals("epubcfi(/6/8!/4/2:12)", annotationClick.value)
		assertEquals(3, annotationClick.index)
		assertEquals("epubcfi(/6/8!/4/2:12,/1:0,/1:8)", annotationClick.rangeCfi)
		assertEquals(annotationClick.value, annotationDrawn.value)
		assertEquals(annotationClick.index, annotationDrawn.index)
		assertEquals(annotationClick.rangeCfi, annotationDrawn.rangeCfi)
		assertEquals(3, overlayCreated.index)
		assertEquals(3, loadDoc.index)
		assertEquals("EPUB/Text/chapter-01.xhtml", loadDoc.href)
		assertEquals("Chapter 1", loadDoc.title)
		assertEquals("chapter-01", loadDoc.sectionId)
		val footnoteOpen = assertIs<ReaderBridgeEvent.FootnoteOpen>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "footnoteOpen",
				  "href": "Text/chapter-01.xhtml#fn1",
				  "text": "This is the footnote body.",
				  "noteType": "footnote",
				  "hidden": true
				}
				""".trimIndent()
			)
		)
		assertEquals("Text/chapter-01.xhtml#fn1", footnoteOpen.href)
		assertEquals("This is the footnote body.", footnoteOpen.text)
		assertEquals("footnote", footnoteOpen.noteType)
		assertEquals(true, footnoteOpen.hidden)
		assertIs<ReaderBridgeEvent.FootnoteClose>(
			decodeReaderBridgeEvent("""{"type":"footnoteClose"}""")
		)
		val defaultPullUp = assertIs<ReaderBridgeEvent.PullUp>(
			decodeReaderBridgeEvent("""{"type":"pullUp"}""")
		)
		assertNull(defaultPullUp.source)
		val scrolledEdgePullUp = assertIs<ReaderBridgeEvent.PullUp>(
			decodeReaderBridgeEvent("""{"type":"pullUp","source":"$ReaderPullUpSourceScrolledEdgeSwipe"}""")
		)
		assertEquals(ReaderPullUpSourceScrolledEdgeSwipe, scrolledEdgePullUp.source)
	}

	@Test
	fun bridgeEventsDecodeTypedContentActionClaims() {
		assertContentActionClaim(source = "link", action = ReaderContentAction.Link)
		assertContentActionClaim(source = "link-touch", action = ReaderContentAction.Link)
		assertContentActionClaim(source = "image", action = ReaderContentAction.Image)
		assertContentActionClaim(source = "media-touch", action = ReaderContentAction.MediaControl)
		assertContentActionClaim(source = "media-anchor", action = ReaderContentAction.MediaControl)
		assertContentActionClaim(source = "unknown", action = ReaderContentAction.Generic)
	}

	@Test
	fun bridgeEventsDecodeContentActionClaimMetadataFromFoliateLikeClicks() {
		val link = assertIs<ReaderBridgeEvent.ContentTapHandled>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "readerContentTapHandled",
				  "action": "link",
				  "source": "link",
				  "href": "EPUB/Text/chapter-02.xhtml#door",
				  "text": "Chapter II"
				}
				""".trimIndent()
			)
		)
		val image = assertIs<ReaderBridgeEvent.ContentTapHandled>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "readerContentTapHandled",
				  "action": "image",
				  "source": "click-image",
				  "src": "EPUB/Images/map.jpg",
				  "alt": "Thror's map",
				  "x": 120.5,
				  "y": 420.0
				}
				""".trimIndent()
			)
		)

		assertEquals(
			ReaderContentActionClaim(
				action = ReaderContentAction.Link,
				source = "link",
				href = "EPUB/Text/chapter-02.xhtml#door",
				text = "Chapter II"
			),
			link.claim
		)
		assertEquals(
			ReaderContentActionClaim(
				action = ReaderContentAction.Image,
				source = "click-image",
				src = "EPUB/Images/map.jpg",
				text = "Thror's map",
				x = 120.5,
				y = 420.0
			),
			image.claim
		)
	}

	@Test
	fun bridgeEventDecodeClassifiesRejectedMessages() {
		assertEquals(
			ReaderBridgeDecodeFailure.MalformedJson,
			assertIs<ReaderBridgeDecodeResult.Rejected>(decodeReaderBridgeMessage("{")).failure
		)
		assertEquals(
			ReaderBridgeDecodeFailure.NonObjectPayload,
			assertIs<ReaderBridgeDecodeResult.Rejected>(decodeReaderBridgeMessage("[]")).failure
		)
		assertEquals(
			ReaderBridgeDecodeFailure.MissingType,
			assertIs<ReaderBridgeDecodeResult.Rejected>(decodeReaderBridgeMessage("{}")).failure
		)
		assertEquals(
			ReaderBridgeDecodeFailure.UnknownType,
			assertIs<ReaderBridgeDecodeResult.Rejected>(
				decodeReaderBridgeMessage("""{"type":"unknown"}""")
			).failure
		)
		assertEquals(
			ReaderBridgeDecodeFailure.InvalidPayload,
			assertIs<ReaderBridgeDecodeResult.Rejected>(
				decodeReaderBridgeMessage("""{"type":"cfiChanged"}""")
			).failure
		)
		assertEquals(
			ReaderBridgeEvent.Ready,
			assertIs<ReaderBridgeDecodeResult.Decoded>(
				decodeReaderBridgeMessage("""{"type":"ready"}""")
			).event
		)

		assertNull(decodeReaderBridgeEvent("not-json"))
		assertNull(decodeReaderBridgeEvent("""{"type":"unknown"}"""))
	}

	@Test
	fun bridgeEventDecodeUniversallyRedactsRejectedRawMessage() {
		val result = assertIs<ReaderBridgeDecodeResult.Rejected>(
			decodeReaderBridgeMessage("not-json\n\u0001" + "x".repeat(600))
		)

		assertEquals("[redacted-reader-bridge-payload]", result.rawMessage)
		assertFalse(result.rawMessage.contains("not-json"))
		assertFalse(result.rawMessage.contains('\n'))
		assertFalse(result.rawMessage.contains('\u0001'))
	}

	@Test
	fun duplicatePageDiagnosticDecodesOnlyOrdinalsAndComparisonBooleans() {
		val event = assertIs<ReaderBridgeEvent.DuplicatePageSuspected>(
			decodeReaderBridgeEvent(
				"""{"type":"duplicatePageSuspected","currentPageOrdinal":11,"previousPageOrdinal":7,"plainTextSame":true,"locatorSame":false}"""
			)
		)

		assertEquals(11, event.currentPageOrdinal)
		assertEquals(7, event.previousPageOrdinal)
		assertTrue(event.plainTextSame)
		assertFalse(event.locatorSame)
		assertNull(
			decodeReaderBridgeEvent(
				"""{"type":"duplicatePageSuspected","currentPageOrdinal":0,"previousPageOrdinal":7,"plainTextSame":true,"locatorSame":false}"""
			)
		)
		assertNull(
			decodeReaderBridgeEvent(
				"""{"type":"duplicatePageSuspected","currentPageOrdinal":11,"previousPageOrdinal":11,"plainTextSame":true,"locatorSame":false}"""
			)
		)
	}

	@Test
	fun cueOverlayModeSerializesExplicitlyWithoutChangingLegacyFields() {
		val fragment = ReaderOverlayFragment(
			resourceHref = "audio/chapter-01.mp3",
			fragmentId = "cue-1",
			textHref = "Text/chapter-01.xhtml",
			textStart = 10,
			textEnd = 24,
			textProgressEnd = 18,
			ebookText = "Exact legacy cue text"
		)

		assertEquals(ReaderOverlayCoordinateMode.CueV1DomUtf16, fragment.coordinateMode)
		val script = ReaderBridgeCommand.ApplyOverlayFragment(fragment).toJavaScript()
		assertContains(script, "\"coordinateMode\":\"cue-v1-dom-utf16\"")
		assertContains(script, "\"textHref\":\"Text/chapter-01.xhtml\"")
		assertContains(script, "\"textStart\":10")
		assertContains(script, "\"textEnd\":24")
		assertContains(script, "\"textProgressEnd\":18")
		assertContains(script, "\"ebookText\":\"Exact legacy cue text\"")
	}

	@Test
	fun rawProvenanceCommandAndOverlaySerializeExactCoordinates() {
		val descriptor = rawTextDescriptor()
		val installScript = ReaderBridgeCommand.InstallRawTextProvenance(descriptor).toJavaScript()
		val fragment = ReaderOverlayFragment(
			resourceHref = "Audio/chapter-01.mp3",
			coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
			textHref = descriptor.href,
			rawProvenanceId = descriptor.id,
			rawSpineIndex = descriptor.spineIndex,
			rawByteStart = 41,
			rawByteEnd = 54,
			rawProgressByteEnd = 49,
			rawProgressFraction = 0.625
		)
		val overlayScript = ReaderBridgeCommand.ApplyOverlayFragment(fragment).toJavaScript()

		assertContains(installScript, "\"type\":\"installRawTextProvenance\"")
		assertContains(installScript, "\"descriptor\"")
		assertContains(installScript, "\"id\":\"chapter-raw-1\"")
		assertContains(installScript, "\"href\":\"OPS/Text/chapter.xhtml\"")
		assertContains(installScript, "\"spineIndex\":3")
		assertContains(installScript, "\"sourceHash\":\"sha256:aaaaaaaa")
		assertContains(installScript, "\"extractedTextHash\":\"sha256:bbbbbbbb")
		assertContains(installScript, "\"byteLength\":144")
		assertContains(installScript, "\"tokenCount\":22")
		assertContains(overlayScript, "\"coordinateMode\":\"wordsync-v1-extracted-utf8\"")
		assertContains(overlayScript, "\"rawProvenanceId\":\"chapter-raw-1\"")
		assertContains(overlayScript, "\"rawSpineIndex\":3")
		assertContains(overlayScript, "\"rawByteStart\":41")
		assertContains(overlayScript, "\"rawByteEnd\":54")
		assertContains(overlayScript, "\"rawProgressByteEnd\":49")
		assertContains(overlayScript, "\"rawProgressFraction\":0.625")
		assertFalse(overlayScript.contains("\"textStart\""))
		assertFalse(overlayScript.contains("\"fragmentId\""))
	}

	@Test
	fun overlayCoordinatesRejectMixedUnknownAndFragmentFallbackPayloads() {
		assertFailsWith<IllegalArgumentException> {
			ReaderOverlayFragment(
				resourceHref = "audio.mp3",
				coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
				fragmentId = "legacy-fragment",
				textHref = "OPS/Text/chapter.xhtml",
				rawProvenanceId = "chapter-raw-1",
				rawSpineIndex = 3,
				rawByteStart = 1,
				rawByteEnd = 4
			)
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderOverlayFragment(
				resourceHref = "audio.mp3",
				coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
				textHref = "OPS/Text/chapter.xhtml",
				textStart = 1,
				textEnd = 4,
				rawProvenanceId = "chapter-raw-1",
				rawSpineIndex = 3,
				rawByteStart = 1,
				rawByteEnd = 4
			)
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderOverlayFragment(
				resourceHref = "audio.mp3",
				rawProvenanceId = "chapter-raw-1"
			)
		}

		listOf(
			"""{"type":"overlayFragmentActive","resourceHref":"audio.mp3","coordinateMode":"future-mode"}""",
			"""{"type":"overlayFragmentActive","resourceHref":"audio.mp3","coordinateMode":"wordsync-v1-extracted-utf8","textHref":"OPS/Text/chapter.xhtml","textStart":1,"textEnd":4,"rawProvenanceId":"chapter-raw-1","rawSpineIndex":3,"rawByteStart":1,"rawByteEnd":4}""",
			"""{"type":"overlayFragmentActive","resourceHref":"audio.mp3","coordinateMode":"cue-v1-dom-utf16","rawProvenanceId":"chapter-raw-1"}"""
		).forEach { payload ->
			val rejected = assertIs<ReaderBridgeDecodeResult.Rejected>(decodeReaderBridgeMessage(payload))
			assertEquals(ReaderBridgeDecodeFailure.InvalidPayload, rejected.failure)
			assertEquals("[redacted-reader-bridge-payload]", rejected.rawMessage)
		}
	}

	@Test
	fun rawStatusAndExactInverseCoordinatesDecodeWithoutChangingLegacyCoordinates() {
		val pending = assertIs<ReaderBridgeEvent.RawTextProvenanceStatusChanged>(
			decodeReaderBridgeEvent(
				"""{"type":"rawTextProvenanceStatus","provenanceId":"chapter-raw-1","status":"pending","reason":"content-not-loaded"}"""
			)
		)
		val ready = assertIs<ReaderBridgeEvent.RawTextProvenanceStatusChanged>(
			decodeReaderBridgeEvent(
				"""{"type":"rawTextProvenanceStatus","provenanceId":"chapter-raw-1","status":"ready"}"""
			)
		)
		val rejectedStatus = assertIs<ReaderBridgeEvent.RawTextProvenanceStatusChanged>(
			decodeReaderBridgeEvent(
				"""{"type":"rawTextProvenanceStatus","provenanceId":"chapter-raw-1","status":"rejected","reason":"source-hash-mismatch"}"""
			)
		)
		val visible = assertIs<ReaderBridgeEvent.VisibleTextRange>(
			decodeReaderBridgeEvent(
				"""{"type":"visibleTextRange","textHref":"OPS/Text/chapter.xhtml","visibleStart":12,"visibleEnd":31,"rawProvenanceId":"chapter-raw-1","rawSpineIndex":3,"rawByteStart":44,"rawByteEnd":69}"""
			)
		)
		val point = assertIs<ReaderBridgeEvent.TextPoint>(
			decodeReaderBridgeEvent(
				"""{"type":"textPoint","textHref":"OPS/Text/chapter.xhtml","textOffset":18,"rawProvenanceId":"chapter-raw-1","rawByteOffset":52}"""
			)
		)

		assertEquals(RawTextProvenanceStatus.Pending, pending.status)
		assertEquals(RawTextProvenanceReason.ContentNotLoaded, pending.reason)
		assertEquals(RawTextProvenanceStatus.Ready, ready.status)
		assertNull(ready.reason)
		assertEquals(RawTextProvenanceStatus.Rejected, rejectedStatus.status)
		assertEquals(RawTextProvenanceReason.SourceHashMismatch, rejectedStatus.reason)
		assertEquals(12, visible.visibleStart)
		assertEquals(31, visible.visibleEnd)
		assertEquals("chapter-raw-1", visible.rawProvenanceId)
		assertEquals(3, visible.rawSpineIndex)
		assertEquals(44, visible.rawByteStart)
		assertEquals(69, visible.rawByteEnd)
		assertEquals(18, point.textOffset)
		assertEquals("chapter-raw-1", point.rawProvenanceId)
		assertEquals(52, point.rawByteOffset)

		listOf(
			"""{"type":"rawTextProvenanceStatus","provenanceId":"chapter-raw-1","status":"future","reason":"source-hash-mismatch"}""",
			"""{"type":"rawTextProvenanceStatus","provenanceId":"chapter-raw-1","status":"rejected","reason":"OPS/Text/chapter.xhtml"}""",
			"""{"type":"visibleTextRange","textHref":"OPS/Text/chapter.xhtml","visibleStart":12,"visibleEnd":31,"rawProvenanceId":"chapter-raw-1","rawByteStart":44,"rawByteEnd":69}""",
			"""{"type":"textPoint","textHref":"OPS/Text/chapter.xhtml","textOffset":18,"rawProvenanceId":"chapter-raw-1"}"""
		).forEach { payload ->
			val rejected = assertIs<ReaderBridgeDecodeResult.Rejected>(decodeReaderBridgeMessage(payload))
			assertEquals(ReaderBridgeDecodeFailure.InvalidPayload, rejected.failure)
		}
	}

	private fun rawTextDescriptor(): ReaderRawTextProvenanceDescriptor =
		ReaderRawTextProvenanceDescriptor(
			id = "chapter-raw-1",
			href = "OPS/Text/chapter.xhtml",
			spineIndex = 3,
			sourceHash = "sha256:${"a".repeat(64)}",
			extractedTextHash = "sha256:${"b".repeat(64)}",
			byteLength = 144,
			tokenCount = 22
		)

	private fun assertContentActionClaim(
		source: String,
		action: ReaderContentAction
	) {
		val event = assertIs<ReaderBridgeEvent.ContentTapHandled>(
			decodeReaderBridgeEvent("""{"type":"readerContentTapHandled","source":"$source"}""")
		)

		assertEquals(action, event.claim.action)
		assertEquals(source, event.claim.source)
	}
}

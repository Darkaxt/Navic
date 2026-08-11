package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReaderWhispersyncAnchorReceiptTest {
	@Test
	fun exactOverlayDecodesCurrentPageAnchorReceiptWithoutPublicationContent() {
		val event = assertIs<ReaderBridgeEvent.OverlayFragmentActive>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "overlayFragmentActive",
				  "resourceHref": "audio.mp3",
				  "coordinateMode": "wordsync-v1-extracted-utf8",
				  "overlayRequestId": 19,
				  "textHref": "chapter.xhtml",
				  "rawProvenanceId": "raw-1",
				  "rawSpineIndex": 4,
				  "rawByteStart": 20,
				  "rawByteEnd": 28,
				  "rawProgressByteEnd": 24,
				  "anchorReceipt": {
				    "foliateSessionId": "foliate-7",
				    "destinationCommitToken": "settled-11",
				    "visualPageOrdinal": 12,
				    "spineIndex": 3,
				    "rasterGeneration": 31,
				    "textureGeneration": 32,
				    "presentationMutationGeneration": 8,
				    "presentationSequence": 9,
				    "anchorGeneration": 10,
				    "boundarySequence": 19,
				    "paginationFingerprint": "pagination-a",
				    "layoutFingerprint": "layout-a",
				    "readerSettingsRasterKey": "settings-a",
				    "captureGeometry": {
				      "viewportWidth": 1200,
				      "viewportHeight": 800,
				      "mode": "spread",
				      "pages": [
				        {"role":"left","left":12,"top":0,"width":570,"height":800},
				        {"role":"right","left":618,"top":0,"width":570,"height":800}
				      ]
				    },
				    "pageLocalRects": [
				      {"role":"right","left":18,"top":40,"width":62,"height":24}
				    ]
				  }
				}
				""".trimIndent()
			)
		)

		val receipt = requireNotNull(event.anchorReceipt)
		assertEquals("foliate-7", receipt.foliateSessionId)
		assertEquals("settled-11", receipt.destinationCommitToken)
		assertEquals(12, receipt.visualPageOrdinal)
		assertEquals(31L, receipt.rasterGeneration)
		assertEquals(19L, receipt.boundarySequence)
		assertEquals(ReaderPageTurnLayoutMode.Spread, receipt.captureGeometry.mode)
		assertEquals(
			listOf(
				ReaderWhispersyncPageLocalRect(
					role = ReaderPageTurnPageRole.Right,
					left = 18.0,
					top = 40.0,
					width = 62.0,
					height = 24.0
				)
			),
			receipt.pageLocalRects
		)
	}

	@Test
	fun malformedAnchorFailsClosedWithoutDiscardingSemanticOverlayConfirmation() {
		val event = assertIs<ReaderBridgeEvent.OverlayFragmentActive>(
			decodeReaderBridgeEvent(
				"""
				{
				  "type": "overlayFragmentActive",
				  "resourceHref": "audio.mp3",
				  "coordinateMode": "wordsync-v1-extracted-utf8",
				  "overlayRequestId": 19,
				  "textHref": "chapter.xhtml",
				  "rawProvenanceId": "raw-1",
				  "rawSpineIndex": 4,
				  "rawByteStart": 20,
				  "rawByteEnd": 28,
				  "anchorReceipt": {
				    "foliateSessionId": "foliate-7",
				    "destinationCommitToken": "settled-11",
				    "visualPageOrdinal": 12,
				    "spineIndex": 3,
				    "rasterGeneration": 31,
				    "textureGeneration": 32,
				    "presentationMutationGeneration": 8,
				    "presentationSequence": 9,
				    "anchorGeneration": 10,
				    "boundarySequence": 18,
				    "paginationFingerprint": "pagination-a",
				    "layoutFingerprint": "layout-a",
				    "readerSettingsRasterKey": "settings-a",
				    "captureGeometry": {
				      "viewportWidth": 600,
				      "viewportHeight": 800,
				      "mode": "single",
				      "pages": [{"role":"full","left":30,"top":0,"width":540,"height":800}]
				    },
				    "pageLocalRects": [{"role":"full","left":18,"top":40,"width":62,"height":24}]
				  }
				}
				""".trimIndent()
			)
		)

		assertEquals(19L, event.fragment.overlayRequestId)
		assertNull(event.anchorReceipt)
	}

	@Test
	fun pageLocalRectsClipAndScaleIntoPortraitAndLandscapeLeafMasks() {
		val portrait = anchorReceipt(
			geometry = ReaderPageTurnCaptureGeometry(
				viewportWidth = 100.0,
				viewportHeight = 100.0,
				mode = ReaderPageTurnLayoutMode.Single,
				pages = listOf(ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, 10.0, 0.0, 80.0, 100.0))
			),
			rects = listOf(
				ReaderWhispersyncPageLocalRect(ReaderPageTurnPageRole.Full, -10.0, 10.0, 30.0, 20.0)
			)
		)
		assertEquals(
			listOf(ReaderPageTurnPixelRect(0, 20, 50, 60)),
			portrait.maskRectsFor(ReaderPageTurnPageRole.Full, bitmapWidth = 200, bitmapHeight = 200)
		)

		val landscape = anchorReceipt(
			geometry = ReaderPageTurnCaptureGeometry(
				viewportWidth = 100.0,
				viewportHeight = 100.0,
				mode = ReaderPageTurnLayoutMode.Spread,
				pages = listOf(
					ReaderPageTurnPageRect(ReaderPageTurnPageRole.Left, 0.0, 0.0, 50.0, 100.0),
					ReaderPageTurnPageRect(ReaderPageTurnPageRole.Right, 50.0, 0.0, 50.0, 100.0)
				)
			),
			rects = listOf(
				ReaderWhispersyncPageLocalRect(ReaderPageTurnPageRole.Left, 45.0, 95.0, 10.0, 10.0),
				ReaderWhispersyncPageLocalRect(ReaderPageTurnPageRole.Right, 0.0, 20.0, 5.0, 10.0)
			)
		)
		assertEquals(
			listOf(ReaderPageTurnPixelRect(90, 190, 100, 200)),
			landscape.maskRectsFor(ReaderPageTurnPageRole.Left, bitmapWidth = 100, bitmapHeight = 200)
		)
		assertEquals(
			listOf(ReaderPageTurnPixelRect(0, 40, 10, 60)),
			landscape.maskRectsFor(ReaderPageTurnPageRole.Right, bitmapWidth = 100, bitmapHeight = 200)
		)
	}

	private fun anchorReceipt(
		geometry: ReaderPageTurnCaptureGeometry,
		rects: List<ReaderWhispersyncPageLocalRect>
	) = ReaderWhispersyncAnchorReceipt(
		foliateSessionId = "session",
		destinationCommitToken = "token",
		visualPageOrdinal = 4,
		spineIndex = 2,
		rasterGeneration = 3L,
		textureGeneration = 4L,
		presentationMutationGeneration = 5L,
		presentationSequence = 6L,
		anchorGeneration = 7L,
		boundarySequence = 8L,
		paginationFingerprint = "pagination",
		layoutFingerprint = "layout",
		readerSettingsRasterKey = "settings",
		captureGeometry = geometry,
		pageLocalRects = rects
	)
}

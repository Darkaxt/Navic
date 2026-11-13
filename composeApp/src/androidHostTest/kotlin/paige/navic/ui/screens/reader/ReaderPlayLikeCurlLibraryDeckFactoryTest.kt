package paige.navic.ui.screens.reader

import karacken.curl.LandscapePageDeck
import karacken.curl.PageChange
import karacken.curl.PageDisplayRect
import karacken.curl.PageImage
import karacken.curl.PortraitPageDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageMaximumForegroundPublicationEntries
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderWhispersyncAnchorReceipt
import paige.navic.reader.ReaderWhispersyncPageLocalRect

class ReaderPlayLikeCurlLibraryDeckFactoryTest {
	@Test
	fun explicitlyPlacedPageCanCarryBackingAndDeformedOverlayTogether() {
		val display = PageDisplayRect(20, 0, 220, 300)
		val backing = PageDisplayRect(220, 0, 228, 300)
		val page = PageImage(
			17L,
			"page-4",
			4,
			100,
			150,
			display,
			"base",
			"highlight-mask",
			backing,
			0xFFF5F2EA.toInt()
		)

		assertTrue(page.hasOverlay())
		assertEquals("highlight-mask", page.overlayContent)
		assertTrue(page.hasBacking())
		assertEquals(backing, page.backingRect)
	}

	@Test
	fun currentPortraitAnchorTargetsOnlyTheCurrentFullLeaf() {
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "book",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 9,
			rasterGeneration = 31L
		)
		val receipt = anchorReceipt(
			mode = ReaderPageTurnLayoutMode.Single,
			visualPageOrdinal = 4,
			roles = listOf(ReaderPageTurnPageRole.Full)
		)

		assertEquals(
			listOf(ReaderWhispersyncNativeOverlayTarget(ReaderPageTurnPageRole.Full, 4)),
			readerWhispersyncNativeOverlayTargets(
				receipt = receipt,
				presentationProof = receipt.nativePresentationProof(),
				foliateSessionId = "session",
				profile = profile,
				currentOrdinal = 4,
				textureGeneration = 32L
			)
		)
	}

	@Test
	fun currentLandscapeAnchorMapsBothPhysicalLeavesAtomically() {
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "book",
			orientation = ReaderPlayLikeCurlOrientation.Landscape,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 10,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
			rasterGeneration = 31L
		)
		val receipt = anchorReceipt(
			mode = ReaderPageTurnLayoutMode.Spread,
			visualPageOrdinal = 4,
			roles = listOf(
				ReaderPageTurnPageRole.Left,
				ReaderPageTurnPageRole.Right
			)
		)

		// Foliate spread 4 is presented by the curl leaves 8 (left) and 9 (right).
		assertEquals(
			listOf(
				ReaderWhispersyncNativeOverlayTarget(ReaderPageTurnPageRole.Left, 8),
				ReaderWhispersyncNativeOverlayTarget(ReaderPageTurnPageRole.Right, 9)
			),
			readerWhispersyncNativeOverlayTargets(
				receipt = receipt,
				presentationProof = receipt.nativePresentationProof(),
				foliateSessionId = "session",
				profile = profile,
				currentOrdinal = 4,
				textureGeneration = 32L
			)
		)
	}

	@Test
	fun staleAnchorIdentityFailsClosedBeforeMaskPublication() {
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "book",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 9,
			rasterGeneration = 31L
		)
		val receipt = anchorReceipt(
			mode = ReaderPageTurnLayoutMode.Single,
			visualPageOrdinal = 4,
			roles = listOf(ReaderPageTurnPageRole.Full)
		)
		val proof = receipt.nativePresentationProof()

		assertNull(
			readerWhispersyncNativeOverlayTargets(
				receipt = receipt,
				presentationProof = proof,
				foliateSessionId = "replacement-session",
				profile = profile,
				currentOrdinal = 4,
				textureGeneration = 32L
			)
		)
		assertNull(
			readerWhispersyncNativeOverlayTargets(
				receipt = receipt,
				presentationProof = proof,
				foliateSessionId = "session",
				profile = profile.copy(rasterGeneration = 99L),
				currentOrdinal = 4,
				textureGeneration = 32L
			)
		)
		assertNull(
			readerWhispersyncNativeOverlayTargets(
				receipt = receipt,
				presentationProof = proof,
				foliateSessionId = "session",
				profile = profile,
				currentOrdinal = 4,
				textureGeneration = 99L
			)
		)
		listOf(
			receipt.copy(destinationCommitToken = "replacement"),
			receipt.copy(presentationMutationGeneration = 40L),
			receipt.copy(presentationSequence = 50L),
			receipt.copy(anchorGeneration = 60L),
			receipt.copy(boundarySequence = 70L),
			receipt.copy(layoutGeneration = 80L),
			receipt.copy(viewGeneration = 90L),
			receipt.copy(commitSequence = 100L),
			receipt.copy(committedChapterPageIndex = 2),
			receipt.copy(paginationFingerprint = "replacement-pagination"),
			receipt.copy(layoutFingerprint = "replacement-layout"),
			receipt.copy(readerSettingsRasterKey = "replacement-settings")
		).forEach { stale ->
			assertNull(
				readerWhispersyncNativeOverlayTargets(
					receipt = stale,
					presentationProof = proof,
					foliateSessionId = "session",
					profile = profile,
					currentOrdinal = 4,
					textureGeneration = 32L
				)
			)
		}
	}

	@Test
	fun newerSameDeckPresentationAuthorityReauthorizesNativeOverlay() {
		val current = anchorReceipt(
			mode = ReaderPageTurnLayoutMode.Single,
			visualPageOrdinal = 4,
			roles = listOf(ReaderPageTurnPageRole.Full)
		).nativePresentationProof()
		val reauthorized = current.copy(
			presentationSequence = current.presentationSequence + 1L,
			anchorGeneration = current.anchorGeneration + 1L,
			boundarySequence = 0L
		)

		assertTrue(reauthorized.isAdmissibleReplacementFor(current))
		assertFalse(current.isAdmissibleReplacementFor(reauthorized))
	}

	@Test
	fun unchangedPresentationAuthorityKeepsAnchorBoundaryMonotonic() {
		val current = anchorReceipt(
			mode = ReaderPageTurnLayoutMode.Single,
			visualPageOrdinal = 4,
			roles = listOf(ReaderPageTurnPageRole.Full)
		).nativePresentationProof()

		assertTrue(
			current.copy(
				anchorGeneration = current.anchorGeneration + 1L,
				boundarySequence = 0L
			).isAdmissibleReplacementFor(current)
		)
		assertFalse(
			current.copy(boundarySequence = current.boundarySequence - 1L)
				.isAdmissibleReplacementFor(current)
		)
	}

	@Test
	fun portraitPreparedWindowKeepsTwoPagesInEachDirection() {
		assertEquals(
			listOf(2, 3, 4, 5, 6),
			readerPlayLikeCurlPreparedPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				currentOrdinal = 4,
				pageCount = 9
			)
		)
	}

	@Test
	fun landscapePreparedWindowKeepsTwoCompleteSpreadsInEachDirection() {
		// Foliate spreads 6..10 around spread 8, each contributing its two leaf ordinals.
		assertEquals(
			(12..21).toList(),
			readerPlayLikeCurlPreparedPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 8,
				pageCount = 20
			)
		)
	}

	@Test
	fun preparedWindowsNeverExceedForegroundPublicationBound() {
		ReaderPlayLikeCurlOrientation.values().forEach { orientation ->
			(1..40).forEach { pageCount ->
				(0 until pageCount).forEach { currentOrdinal ->
					val prepared = readerPlayLikeCurlPreparedPageIndices(
						orientation = orientation,
						currentOrdinal = currentOrdinal,
						pageCount = pageCount
					)
					assertTrue(
						prepared.size <=
							ReaderPageMaximumForegroundPublicationEntries
					)
				}
			}
		}
	}

	@Test
	fun settlementTargetUsesOneFoliateOrdinalWithoutCrossingBoundaries() {
		assertEquals(
			5,
			readerPlayLikeCurlSettlementTargetOrdinal(
				currentOrdinal = 4,
				pageCount = 9,
				pageChange = PageChange.NEXT
			)
		)
		assertEquals(
			3,
			readerPlayLikeCurlSettlementTargetOrdinal(
				currentOrdinal = 4,
				pageCount = 9,
				pageChange = PageChange.PREVIOUS
			)
		)
		assertEquals(
			null,
			readerPlayLikeCurlSettlementTargetOrdinal(
				currentOrdinal = 0,
				pageCount = 9,
				pageChange = PageChange.PREVIOUS
			)
		)
		assertEquals(
			null,
			readerPlayLikeCurlSettlementTargetOrdinal(
				currentOrdinal = 8,
				pageCount = 9,
				pageChange = PageChange.NEXT
			)
		)
		assertEquals(
			null,
			readerPlayLikeCurlSettlementTargetOrdinal(
				currentOrdinal = 4,
				pageCount = 9,
				pageChange = PageChange.NONE
			)
		)
	}

	@Test
	fun portraitDeckWindowUsesPreviousCurrentAndNextLogicalPages() {
		assertEquals(
			listOf(3, 4, 5),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				currentOrdinal = 4,
				pageCount = 9
			)
		)
	}

	@Test
	fun landscapeDeckWindowUsesThreeCompleteSpreads() {
		// Foliate spreads 3, 4 and 5 own leaf ordinals 6..11.
		assertEquals(
			listOf(6, 7, 8, 9, 10, 11),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 4,
				pageCount = 10
			)
		)
	}

	@Test
	fun deckWindowClampsAtPublicationBoundaries() {
		assertEquals(
			listOf(0, 1),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				currentOrdinal = 0,
				pageCount = 2
			)
		)
		assertEquals(
			listOf(0, 1, 2, 3),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 0,
				pageCount = 4
			)
		)
		assertEquals(
			listOf(4, 5, 6, 7),
			readerPlayLikeCurlLibraryDeckPageIndices(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				currentOrdinal = 3,
				pageCount = 4
			)
		)
	}

	@Test
	fun portraitMapsPreviousCurrentAndNextAroundTheRequestedOrdinal() {
		val deck = readerPlayLikeCurlLibraryDeck(
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			generationId = 7L,
			currentOrdinal = 3,
			pageCount = 8,
			page = ::page
		)

		val portrait = assertIs<PortraitPageDeck<String>>(deck)
		assertEquals(listOf(2, 3, 4), portrait.pages.map(PageImage<String>::getOrdinal))
		assertEquals(7L, portrait.generationId)
	}

	@Test
	fun portraitBoundariesUseFillersWithoutInventingNavigation() {
		val first = assertIs<PortraitPageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				generationId = 8L,
				currentOrdinal = 0,
				pageCount = 8
			)
		)
		val last = assertIs<PortraitPageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				generationId = 9L,
				currentOrdinal = 7,
				pageCount = 8
			)
		)

		assertTrue(first.previous.isFiller)
		assertFalse(first.canTurn(PageChange.PREVIOUS))
		assertTrue(first.canTurn(PageChange.NEXT))
		assertTrue(last.next.isFiller)
		assertTrue(last.canTurn(PageChange.PREVIOUS))
		assertFalse(last.canTurn(PageChange.NEXT))
	}

	@Test
	fun landscapeMapsThreeAdjacentWholeSpreadsAroundTheFoliateSpread() {
		val landscape = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 10L,
				currentOrdinal = 3,
				pageCount = 8
			)
		)

		assertEquals(listOf(4, 5, 6, 7, 8, 9), landscape.pages.map(PageImage<String>::getOrdinal))
		assertEquals(6, landscape.currentLeft.ordinal)
		assertEquals(7, landscape.currentRight.ordinal)
		assertEquals(4, landscape.getSettlementPage(PageChange.PREVIOUS).ordinal)
		assertEquals(6, landscape.getSettlementPage(PageChange.NONE).ordinal)
		assertEquals(8, landscape.getSettlementPage(PageChange.NEXT).ordinal)
	}

	@Test
	fun landscapeClampsTheFirstAndLastSpreadAtBookBoundaries() {
		val first = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 11L,
				currentOrdinal = 0,
				pageCount = 8
			)
		)
		val last = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 12L,
				currentOrdinal = 7,
				pageCount = 8
			)
		)

		assertEquals(
			listOf(
				"filler-Previous-0-Left",
				"filler-Previous-0-Right",
				"page-0",
				"page-1",
				"page-2",
				"page-3"
			),
			first.pages.map(PageImage<String>::getLogicalPageId)
		)
		assertTrue(first.previousLeft.isFiller)
		assertTrue(first.previousRight.isFiller)
		assertFalse(first.currentLeft.isFiller)
		assertFalse(first.currentRight.isFiller)
		assertFalse(first.canTurn(PageChange.PREVIOUS))
		assertTrue(first.canTurn(PageChange.NEXT))
		assertEquals(0, first.getSettlementPage(PageChange.NONE).ordinal)
		assertEquals(2, first.getSettlementPage(PageChange.NEXT).ordinal)
		assertEquals(
			listOf(
				"page-12",
				"page-13",
				"page-14",
				"page-15",
				"filler-Next-7-Left",
				"filler-Next-7-Right"
			),
			last.pages.map(PageImage<String>::getLogicalPageId)
		)
		assertTrue(last.canTurn(PageChange.PREVIOUS))
		assertFalse(last.canTurn(PageChange.NEXT))
		assertEquals(12, last.getSettlementPage(PageChange.PREVIOUS).ordinal)
		assertEquals(14, last.getSettlementPage(PageChange.NONE).ordinal)
		assertTrue(last.nextLeft.isFiller)
		assertTrue(last.nextRight.isFiller)
	}

	@Test
	fun rtlSpreadMirrorsLeavesAndSettlesOnTheReadingFirstRightLeaf() {
		val deck = assertIs<LandscapePageDeck<String>>(
			deck(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				generationId = 20L,
				currentOrdinal = 2,
				pageCount = 6,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl
			)
		)

		assertEquals(5, deck.currentLeft.ordinal)
		assertEquals(4, deck.currentRight.ordinal)
		assertSame(deck.currentRight, deck.getSettlementPage(PageChange.NONE))
		assertSame(deck.previousRight, deck.getSettlementPage(PageChange.PREVIOUS))
		assertSame(deck.nextRight, deck.getSettlementPage(PageChange.NEXT))
		assertEquals(2, deck.getSettlementPage(PageChange.PREVIOUS).ordinal)
		assertEquals(6, deck.getSettlementPage(PageChange.NEXT).ordinal)
		assertTrue(deck.canTurn(PageChange.PREVIOUS))
		assertTrue(deck.canTurn(PageChange.NEXT))
	}

	@Test
	fun landscapeSpreadTurnsStepOneWholeSpreadWithBoundaryFillers() {
		val spreadCount = 6
		ReaderPlayLikeCurlReaderDirection.values().forEach { direction ->
			val readingFirst = when (direction) {
				ReaderPlayLikeCurlReaderDirection.Ltr -> ReaderPlayLikeCurlFoliateLeaf.Left
				ReaderPlayLikeCurlReaderDirection.Rtl -> ReaderPlayLikeCurlFoliateLeaf.Right
			}
			val readingSecond = when (direction) {
				ReaderPlayLikeCurlReaderDirection.Ltr -> ReaderPlayLikeCurlFoliateLeaf.Right
				ReaderPlayLikeCurlReaderDirection.Rtl -> ReaderPlayLikeCurlFoliateLeaf.Left
			}
			fun request(leafOrdinal: Int) = readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = direction,
				logicalOrdinal = leafOrdinal,
				pageCount = spreadCount
			)
			fun assertSpreadLeaves(
				spread: Int,
				left: PageImage<String>,
				right: PageImage<String>
			) {
				assertFalse(left.isFiller)
				assertFalse(right.isFiller)
				assertEquals(spread, request(left.ordinal).sourcePageIndex)
				assertEquals(spread, request(right.ordinal).sourcePageIndex)
				assertEquals(ReaderPlayLikeCurlFoliateLeaf.Left, request(left.ordinal).leaf)
				assertEquals(ReaderPlayLikeCurlFoliateLeaf.Right, request(right.ordinal).leaf)
				val (readingFirstPage, readingSecondPage) = when (direction) {
					ReaderPlayLikeCurlReaderDirection.Ltr -> left to right
					ReaderPlayLikeCurlReaderDirection.Rtl -> right to left
				}
				assertEquals(2 * spread, readingFirstPage.ordinal)
				assertEquals(2 * spread + 1, readingSecondPage.ordinal)
				assertEquals(readingFirst, request(readingFirstPage.ordinal).leaf)
				assertEquals(readingSecond, request(readingSecondPage.ordinal).leaf)
			}

			(0 until spreadCount).forEach { current ->
				val landscape = assertIs<LandscapePageDeck<String>>(
					deck(
						orientation = ReaderPlayLikeCurlOrientation.Landscape,
						generationId = 30L + current,
						currentOrdinal = current,
						pageCount = spreadCount,
						readerDirection = direction
					)
				)
				assertSpreadLeaves(current, landscape.currentLeft, landscape.currentRight)
				assertEquals(
					current,
					readerPlayLikeCurlFoliateOrdinal(
						ReaderPlayLikeCurlOrientation.Landscape,
						landscape.getSettlementPage(PageChange.NONE).ordinal
					)
				)

				val hasPrevious = current > 0
				assertEquals(hasPrevious, landscape.canTurn(PageChange.PREVIOUS))
				if (hasPrevious) {
					assertSpreadLeaves(current - 1, landscape.previousLeft, landscape.previousRight)
					assertEquals(
						current - 1,
						readerPlayLikeCurlFoliateOrdinal(
							ReaderPlayLikeCurlOrientation.Landscape,
							landscape.getSettlementPage(PageChange.PREVIOUS).ordinal
						)
					)
				} else {
					assertTrue(landscape.previousLeft.isFiller)
					assertTrue(landscape.previousRight.isFiller)
				}
				assertEquals(
					(current - 1).takeIf { hasPrevious },
					readerPlayLikeCurlSettlementTargetOrdinal(
						currentOrdinal = current,
						pageCount = spreadCount,
						pageChange = PageChange.PREVIOUS
					)
				)

				val hasNext = current < spreadCount - 1
				assertEquals(hasNext, landscape.canTurn(PageChange.NEXT))
				if (hasNext) {
					assertSpreadLeaves(current + 1, landscape.nextLeft, landscape.nextRight)
					assertEquals(
						current + 1,
						readerPlayLikeCurlFoliateOrdinal(
							ReaderPlayLikeCurlOrientation.Landscape,
							landscape.getSettlementPage(PageChange.NEXT).ordinal
						)
					)
				} else {
					assertTrue(landscape.nextLeft.isFiller)
					assertTrue(landscape.nextRight.isFiller)
				}
				assertEquals(
					(current + 1).takeIf { hasNext },
					readerPlayLikeCurlSettlementTargetOrdinal(
						currentOrdinal = current,
						pageCount = spreadCount,
						pageChange = PageChange.NEXT
					)
				)
			}
		}
	}

	private fun ReaderWhispersyncAnchorReceipt.nativePresentationProof() =
		ReaderWhispersyncNativePresentationProof(
			foliateSessionId = foliateSessionId,
			destinationCommitToken = destinationCommitToken,
			visualPageOrdinal = visualPageOrdinal,
			spineIndex = spineIndex,
			rasterGeneration = rasterGeneration,
			textureGeneration = textureGeneration,
			presentationMutationGeneration = presentationMutationGeneration,
			presentationSequence = presentationSequence,
			anchorGeneration = anchorGeneration,
			boundarySequence = boundarySequence,
			layoutGeneration = layoutGeneration,
			viewGeneration = viewGeneration,
			commitSequence = commitSequence,
			committedSpineIndex = committedSpineIndex,
			committedChapterPageIndex = committedChapterPageIndex,
			committedChapterPageCount = committedChapterPageCount,
			paginationFingerprint = paginationFingerprint,
			layoutFingerprint = layoutFingerprint,
			readerSettingsRasterKey = readerSettingsRasterKey,
			captureGeometry = captureGeometry
		)

	private fun anchorReceipt(
		mode: ReaderPageTurnLayoutMode,
		visualPageOrdinal: Int,
		roles: List<ReaderPageTurnPageRole>
	): ReaderWhispersyncAnchorReceipt {
		val pages = when (mode) {
			ReaderPageTurnLayoutMode.Single -> listOf(
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, 0.0, 0.0, 600.0, 800.0)
			)
			ReaderPageTurnLayoutMode.Spread -> listOf(
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Left, 0.0, 0.0, 300.0, 800.0),
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Right, 300.0, 0.0, 300.0, 800.0)
			)
		}
		return ReaderWhispersyncAnchorReceipt(
			foliateSessionId = "session",
			destinationCommitToken = "settled",
			visualPageOrdinal = visualPageOrdinal,
			spineIndex = 2,
			rasterGeneration = 31L,
			textureGeneration = 32L,
			presentationMutationGeneration = 4L,
			presentationSequence = 5L,
			anchorGeneration = 6L,
			boundarySequence = 7L,
			layoutGeneration = 8L,
			viewGeneration = 9L,
			commitSequence = 10L,
			committedSpineIndex = 2,
			committedChapterPageIndex = 1,
			committedChapterPageCount = 3,
			paginationFingerprint = "pagination",
			layoutFingerprint = "layout",
			readerSettingsRasterKey = "settings",
			captureGeometry = ReaderPageTurnCaptureGeometry(
				viewportWidth = 600.0,
				viewportHeight = 800.0,
				mode = mode,
				pages = pages
			),
			pageLocalRects = roles.mapIndexed { index, role ->
				ReaderWhispersyncPageLocalRect(
					role = role,
					left = 20.0 + index,
					top = 30.0,
					width = 40.0,
					height = 20.0
				)
			}
		)
	}

	private fun deck(
		orientation: ReaderPlayLikeCurlOrientation,
		generationId: Long,
		currentOrdinal: Int,
		pageCount: Int,
		readerDirection: ReaderPlayLikeCurlReaderDirection = ReaderPlayLikeCurlReaderDirection.Ltr
	) = readerPlayLikeCurlLibraryDeck(
		orientation = orientation,
		generationId = generationId,
		currentOrdinal = currentOrdinal,
		pageCount = pageCount,
		readerDirection = readerDirection,
		filler = ::fillerPage,
		page = ::page
	)

	private fun page(generationId: Long, ordinal: Int): PageImage<String> = PageImage(
		generationId,
		"page-$ordinal",
		ordinal,
		120,
		180,
		"content-$ordinal"
	)

	private fun fillerPage(
		generationId: Long,
		deckSlotRole: ReaderPlayLikeCurlDeckSlotRole,
		sourcePageIndex: Int,
		physicalLeaf: ReaderPlayLikeCurlPhysicalLeaf,
		fallbackOrdinal: Int
	): PageImage<String> = PageImage.filler(
		generationId,
		"filler-${deckSlotRole.name}-$sourcePageIndex-${physicalLeaf.name}",
		fallbackOrdinal,
		120,
		180,
		"borrowed-content-$fallbackOrdinal",
		0xFFF5F2EA.toInt()
	)
}

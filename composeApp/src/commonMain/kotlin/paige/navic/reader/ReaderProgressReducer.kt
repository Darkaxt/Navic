package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress

data class ReaderChapterProgressState(
	val href: String? = null,
	val title: String? = null,
	val pageIndex: Int = 0,
	val pageCount: Int = 1,
	val progress: Double = 0.0
) {
	val displayPage: Int
		get() = (pageIndex + 1).coerceIn(1, pageCount.coerceAtLeast(1))
}

internal data class ReaderProgressReduction(
	val state: ReaderControllerState,
	val progressToSave: BinderyReadingProgress?
)

internal object ReaderProgressReducer {
	fun onRelocated(
		state: ReaderControllerState,
		event: ReaderEngineEvent.Relocated,
		decision: ReaderProgressSaveDecision,
		settlementReceipt: ReaderPageTurnSettlementAck?
	): ReaderProgressReduction {
		val nextChrome = state.chrome.onLocationChanged(
			locator = event.locator,
			tocTitle = event.tocTitle
		)
		val progress = state.publication?.let { publication ->
			decision.locatorToSave?.toBinderyReadingProgress(
				bookId = publication.bookId,
				resourceHref = publication.resourceHref,
				kind = publication.kind
			)
		}
		val nextReadingProgress = progress?.let(state.readingProgress::upsert) ?: state.readingProgress
		val dismissShellCover = readerRelocationAcknowledgesShellCoverDismissal(
			shellCoverVisible = state.shellCoverVisible,
			pendingRequest = state.pendingShellCoverDismissal,
			foliateSessionId = event.foliateSessionId,
			locator = event.locator
		)
		val nextNativeShellCoverReturnLocatorKey = state.nativeShellCoverReturnLocatorKey
			?: if (
				state.canReturnToShellCover &&
				readerShouldReturnToNativeShellCover(
					shellCoverUrl = state.nativeShellCoverUrl,
					shellCoverVisible = state.shellCoverVisible && !dismissShellCover,
					locator = event.locator
				)
			) {
				readerNativeShellCoverReturnLocatorKey(event.locator)
			} else {
				null
			}
		val sessionChanged = state.foliateSessionId != event.foliateSessionId
		val currentDestination = state.destinationCommitIdentity.takeUnless { sessionChanged }
		val eventDestination = event.destinationCommitIdentity?.takeIf {
			it.foliateSessionId == event.foliateSessionId
		}
		val nextDestination = when {
			eventDestination == null -> currentDestination
			currentDestination == null -> eventDestination
			eventDestination.commitSequence >= currentDestination.commitSequence -> eventDestination
			else -> currentDestination
		}
		return ReaderProgressReduction(
			state = state.copy(
				chrome = nextChrome,
				chapterProgress = state.chapterProgress.updatedFrom(event.locator, event.tocTitle),
				readingProgress = nextReadingProgress,
				foliateSessionId = event.foliateSessionId,
				pageTurnSettlementAck = settlementReceipt,
				destinationCommitIdentity = nextDestination,
				activeMediaOverlayAnchorReceipt =
					state.activeMediaOverlayAnchorReceipt.takeUnless { sessionChanged },
				nativeShellCoverReturnLocatorKey = nextNativeShellCoverReturnLocatorKey,
				shellCoverVisible = if (dismissShellCover) false else state.shellCoverVisible,
				pendingShellCoverDismissal = state.pendingShellCoverDismissal
					.takeUnless { dismissShellCover },
				menuVisible = if (dismissShellCover) false else state.menuVisible
			),
			progressToSave = progress
		)
	}

	fun onDocumentLoaded(
		controller: ReaderController,
		event: ReaderEngineEvent.DocLoaded
	): ReaderControllerStep {
		val document = ReaderLoadedDocument(
			index = event.index,
			href = event.href,
			title = event.title,
			sectionId = event.sectionId
		)
		return ReaderControllerStep(
			controller.copy(
				state = controller.state.copy(
					loadedDocument = document,
					chapterProgress = controller.state.chapterProgress.updatedFrom(document)
				)
			)
		)
	}

	fun navigateToChapterPage(controller: ReaderController, pageIndex: Int): ReaderControllerStep {
		val chapter = controller.state.chapterProgress
		val href = chapter.href?.takeIf { it.isNotBlank() }
			?: controller.state.chrome.currentLocator?.href?.takeIf { it.isNotBlank() }
			?: return ReaderControllerStep(controller)
		val pageCount = chapter.pageCount.coerceAtLeast(1)
		val targetPageIndex = pageIndex.coerceIn(0, pageCount - 1)
		val chapterProgress = if (pageCount > 1) {
			(targetPageIndex.toDouble() / (pageCount - 1)).coerceIn(0.0, 1.0)
		} else {
			0.0
		}
		return ReaderControllerStep(
			controller = controller,
			engineCommands = listOf(
				ReaderEngineCommand.NavigateTo(
					ReaderLocator(
						href = href,
						chapterProgress = chapterProgress,
						chapterPageIndex = targetPageIndex,
						chapterPageCount = pageCount
					)
				)
			)
		)
	}

	fun navigateToAdjacentChapter(controller: ReaderController, direction: Int): ReaderControllerStep {
		val targetHref = controller.state.adjacentTocChapter(direction)
			?.href
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?: return ReaderControllerStep(controller)
		return ReaderControllerStep(
			controller = controller,
			engineCommands = listOf(ReaderEngineCommand.NavigateTo(ReaderLocator(href = targetHref)))
		)
	}
}

internal fun ReaderChapterProgressState.updatedFrom(
	locator: ReaderLocator,
	tocTitle: String?
): ReaderChapterProgressState {
	val nextHref = locator.href?.trim()?.takeIf { it.isNotEmpty() }
	val hrefChanged = nextHref != null && readerTocHrefKey(nextHref) != readerTocHrefKey(href)
	val nextPageCount = locator.chapterPageCount?.takeIf { it > 0 }
		?: if (hrefChanged) 1 else pageCount
	val normalizedPageCount = nextPageCount.coerceAtLeast(1)
	val nextPageIndex = locator.chapterPageIndex?.takeIf { it >= 0 }
		?: if (hrefChanged) 0 else pageIndex
	val normalizedPageIndex = nextPageIndex.coerceIn(0, normalizedPageCount - 1)
	val normalizedProgress = locator.chapterProgress
		?.takeIf(Double::isFinite)
		?.coerceIn(0.0, 1.0)
		?: if (locator.chapterPageIndex != null && normalizedPageCount > 1) {
			(normalizedPageIndex.toDouble() / (normalizedPageCount - 1)).coerceIn(0.0, 1.0)
		} else if (hrefChanged) {
			0.0
		} else {
			progress
		}
	return copy(
		href = nextHref ?: href,
		title = tocTitle?.trim()?.takeIf { it.isNotEmpty() } ?: title,
		pageIndex = normalizedPageIndex,
		pageCount = normalizedPageCount,
		progress = normalizedProgress
	)
}

internal fun ReaderChapterProgressState.updatedFrom(
	document: ReaderLoadedDocument
): ReaderChapterProgressState {
	val nextHref = document.href?.trim()?.takeIf { it.isNotEmpty() }
	val nextTitle = document.title?.trim()?.takeIf { it.isNotEmpty() }
	if (nextHref == null) return copy(title = nextTitle ?: title)
	val documentChanged = readerTocHrefKey(nextHref) != null &&
		readerTocHrefKey(nextHref) != readerTocHrefKey(href)
	return if (documentChanged) {
		copy(href = nextHref, title = nextTitle ?: title, pageIndex = 0, pageCount = 1, progress = 0.0)
	} else {
		copy(href = nextHref, title = nextTitle ?: title)
	}
}

internal fun ReaderControllerState.adjacentTocChapter(direction: Int): ReaderTocItem? {
	val currentHref = readerTocHrefKey(chapterProgress.href)
		?: readerTocHrefKey(chrome.currentLocator?.href)
		?: return null
	val navigableItems = toc.filter { readerTocHrefKey(it.href) != null }
	val currentIndex = navigableItems.indexOfFirst { item -> readerTocHrefKey(item.href) == currentHref }
	if (currentIndex < 0) return null
	return navigableItems.getOrNull(currentIndex + direction)
}

internal fun readerTocHrefKey(href: String?): String? {
	val trimmed = href
		?.trim()
		?.replace('\\', '/')
		?.takeIf { it.isNotEmpty() }
		?: return null
	return trimmed
		.substringBefore('#')
		.substringBefore('?')
		.trim()
		.trimStart('.', '/')
		.takeIf { it.isNotEmpty() }
}

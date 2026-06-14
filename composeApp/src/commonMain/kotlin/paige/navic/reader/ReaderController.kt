package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress

data class ReaderSearchState(
	val query: String = "",
	val results: List<ReaderSearchResult> = emptyList(),
	val active: Boolean = false
)

data class ReaderSelection(
	val text: String? = null,
	val cfi: String? = null,
	val href: String? = null
)

enum class ReaderControllerDialog {
	Contents,
	ReadingMode,
	Settings
}

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

data class ReaderControllerState(
	val publication: ReaderPublicationIdentity? = null,
	val activeEngine: ReaderPublicationFormat? = null,
	val chrome: ReaderChromeState = ReaderChromeState(),
	val chapterProgress: ReaderChapterProgressState = ReaderChapterProgressState(),
	val shellCoverVisible: Boolean = false,
	val nativeShellCoverUrl: String? = null,
	val canReturnToShellCover: Boolean = false,
	val menuVisible: Boolean = false,
	val dialog: ReaderControllerDialog? = null,
	val search: ReaderSearchState = ReaderSearchState(),
	val toc: List<ReaderTocItem> = emptyList(),
	val selection: ReaderSelection? = null,
	val annotations: ReaderAnnotationState = ReaderAnnotationState(),
	val bookmarks: ReaderBookmarkState = ReaderBookmarkState(),
	val readingProgress: ReaderReadingProgressState = ReaderReadingProgressState(),
	val activeMediaOverlay: ReaderOverlayFragment? = null,
	val audioMetadataLabel: String? = null,
	val lastContentActionClaim: ReaderContentActionClaim? = null,
	val errorMessage: String? = null,
	val errorCode: String? = null
) {
	val canBookmarkCurrentLocation: Boolean
		get() {
			val currentPublication = publication ?: return false
			return readerBookmarkFromLocator(
				bookId = currentPublication.bookId,
				bookTitle = currentPublication.title,
				locator = chrome.currentLocator,
				sectionTitle = chrome.currentSectionTitle
			) != null
		}

	val currentLocationBookmarked: Boolean
		get() {
			val currentPublication = publication ?: return false
			return bookmarks.isBookmarked(currentPublication.bookId, chrome.currentLocator)
		}
}

data class ReaderControllerStep(
	val controller: ReaderController,
	val engineCommands: List<ReaderEngineCommand> = emptyList(),
	val progressToSave: BinderyReadingProgress? = null
)

data class ReaderController(
	val state: ReaderControllerState = ReaderControllerState(),
	private val progressSaveGate: ReaderProgressSaveGate = ReaderProgressSaveGate()
) {
	fun open(request: ReaderEngineOpenRequest): ReaderControllerStep {
		val normalizedRequest = request.copy(settings = request.settings.normalizedReaderSettings())
		return ReaderControllerStep(
			controller = copy(
				progressSaveGate = progressSaveGate.reset(),
				state = state.copy(
					publication = normalizedRequest.publication,
					activeEngine = normalizedRequest.publication.format,
					chrome = state.chrome.copy(
						currentLocator = normalizedRequest.startLocator,
						settings = normalizedRequest.settings
					),
					chapterProgress = normalizedRequest.startLocator
						?.let { state.chapterProgress.updatedFrom(it, tocTitle = null) }
						?: ReaderChapterProgressState(),
					shellCoverVisible = !normalizedRequest.nativeShellCoverUrl.isNullOrBlank(),
					nativeShellCoverUrl = normalizedRequest.nativeShellCoverUrl,
					canReturnToShellCover = normalizedRequest.canReturnToShellCover,
					menuVisible = false,
					dialog = null,
					lastContentActionClaim = null
				)
			),
			engineCommands = listOf(ReaderEngineCommand.OpenPublication(normalizedRequest))
		)
	}

	fun onEngineEvent(event: ReaderEngineEvent): ReaderControllerStep =
		when (event) {
			ReaderEngineEvent.PublicationReady -> {
				val decision = progressSaveGate.onEngineEvent(event)
				ReaderControllerStep(copy(progressSaveGate = decision.state))
			}
			is ReaderEngineEvent.Relocated -> {
				val nextChrome = state.chrome.onLocationChanged(
					locator = event.locator,
					tocTitle = event.tocTitle
				)
				val decision = progressSaveGate.onEngineEvent(event)
				val progress = state.publication?.let { publication ->
					decision.locatorToSave?.toBinderyReadingProgress(
						bookId = publication.bookId,
						resourceHref = publication.resourceHref,
						kind = publication.kind
					)
				}
				val nextReadingProgress = progress?.let(state.readingProgress::upsert) ?: state.readingProgress
				ReaderControllerStep(
					controller = copy(
						progressSaveGate = decision.state,
						state = state.copy(
							chrome = nextChrome,
							chapterProgress = state.chapterProgress.updatedFrom(event.locator, event.tocTitle),
							readingProgress = nextReadingProgress
						)
					),
					progressToSave = progress
				)
			}
			is ReaderEngineEvent.TocItemChanged -> ReaderControllerStep(
				copy(
					state = state.copy(
						chrome = state.chrome.onTocItemChanged(event.title)
					)
				)
			)
			is ReaderEngineEvent.ContentActionClaimed -> ReaderControllerStep(
				copy(state = state.copy(lastContentActionClaim = event.claim))
			)
			is ReaderEngineEvent.SearchResults -> ReaderControllerStep(
				copy(
					state = state.copy(
						search = ReaderSearchState(
							query = event.query,
							results = event.results,
							active = event.query.isNotBlank()
						)
					)
				)
			)
			is ReaderEngineEvent.Toc -> ReaderControllerStep(
				copy(state = state.copy(toc = event.items))
			)
			is ReaderEngineEvent.SelectionChanged -> ReaderControllerStep(
				copy(
					state = state.copy(
						selection = ReaderSelection(
							text = event.text,
							cfi = event.cfi,
							href = event.href
						)
					)
				)
			)
			is ReaderEngineEvent.MediaOverlayActive -> ReaderControllerStep(
				copy(
					state = state.copy(
						activeMediaOverlay = event.fragment,
						audioMetadataLabel = event.fragment.label
					)
				)
			)
			is ReaderEngineEvent.MediaOverlayInactive -> {
				val currentFragmentId = state.activeMediaOverlay?.fragmentId
				val shouldClear = event.fragmentId == null || event.fragmentId == currentFragmentId
				ReaderControllerStep(
					if (shouldClear) {
						copy(
							state = state.copy(
								activeMediaOverlay = null,
								audioMetadataLabel = null
							)
						)
					} else {
						this
					}
				)
			}
			is ReaderEngineEvent.Error -> ReaderControllerStep(
				copy(
					state = state.copy(
						errorMessage = event.message,
						errorCode = event.code
					)
				)
			)
		}

	fun search(query: String): ReaderControllerStep {
		val normalized = query.trim()
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					search = ReaderSearchState(
						query = normalized,
						results = emptyList(),
						active = normalized.isNotBlank()
					)
				)
			),
			engineCommands = listOf(ReaderEngineCommand.Search(normalized))
		)
	}

	fun navigateTo(locator: ReaderLocator): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(ReaderEngineCommand.NavigateTo(locator))
		)

	fun navigateToChapterPage(pageIndex: Int): ReaderControllerStep {
		val chapter = state.chapterProgress
		val href = chapter.href?.takeIf { it.isNotBlank() }
			?: state.chrome.currentLocator?.href?.takeIf { it.isNotBlank() }
			?: return ReaderControllerStep(this)
		val pageCount = chapter.pageCount.coerceAtLeast(1)
		val targetPageIndex = pageIndex.coerceIn(0, pageCount - 1)
		val chapterProgress = if (pageCount > 1) {
			(targetPageIndex.toDouble() / (pageCount - 1)).coerceIn(0.0, 1.0)
		} else {
			0.0
		}
		return navigateTo(
			ReaderLocator(
				href = href,
				chapterProgress = chapterProgress,
				chapterPageIndex = targetPageIndex,
				chapterPageCount = pageCount
			)
		)
	}

	fun applyMediaOverlay(fragment: ReaderOverlayFragment): ReaderControllerStep =
		ReaderControllerStep(
			controller = copy(
				state = state.copy(
					activeMediaOverlay = fragment,
					audioMetadataLabel = fragment.label
				)
			),
			engineCommands = listOf(ReaderEngineCommand.ApplyMediaOverlay(fragment))
		)

	fun addSelectionHighlight(color: String = DefaultReaderHighlightColor): ReaderControllerStep {
		val publication = state.publication ?: return ReaderControllerStep(this)
		val selection = state.selection ?: return ReaderControllerStep(this)
		val nextAnnotations = state.annotations.addSelectionHighlight(
			bookId = publication.bookId,
			bookTitle = publication.title,
			selectionText = selection.text,
			selectionCfi = selection.cfi,
			selectionHref = selection.href,
			sectionTitle = state.chrome.currentSectionTitle,
			color = color
		)
		if (nextAnnotations == state.annotations) {
			return ReaderControllerStep(this)
		}
		return ReaderControllerStep(
			controller = copy(state = state.copy(annotations = nextAnnotations)),
			engineCommands = listOf(
				ReaderEngineCommand.ApplyAnnotations(
					nextAnnotations.annotationsForBook(publication.bookId)
				)
			)
		)
	}

	fun toggleCurrentBookmark(): ReaderControllerStep {
		val publication = state.publication ?: return ReaderControllerStep(this)
		val nextBookmarks = state.bookmarks.toggleBookmark(
			bookId = publication.bookId,
			bookTitle = publication.title,
			locator = state.chrome.currentLocator,
			sectionTitle = state.chrome.currentSectionTitle
		)
		return if (nextBookmarks == state.bookmarks) {
			ReaderControllerStep(this)
		} else {
			ReaderControllerStep(copy(state = state.copy(bookmarks = nextBookmarks)))
		}
	}

	fun clearMediaOverlay(fragmentId: String? = null): ReaderControllerStep {
		val currentFragmentId = state.activeMediaOverlay?.fragmentId
		val shouldClear = state.activeMediaOverlay != null &&
			(fragmentId == null || fragmentId == currentFragmentId)
		return if (shouldClear) {
			ReaderControllerStep(
				controller = copy(
					state = state.copy(
						activeMediaOverlay = null,
						audioMetadataLabel = null
					)
				),
				engineCommands = listOf(ReaderEngineCommand.ClearMediaOverlay)
			)
		} else {
			ReaderControllerStep(this)
		}
	}

	fun onViewerAction(action: ReaderViewerAction): ReaderControllerStep {
		val actionController = if (state.lastContentActionClaim == null) {
			this
		} else {
			copy(state = state.copy(lastContentActionClaim = null))
		}

		if (actionController.state.shellCoverVisible) {
			return actionController.onShellCoverViewerAction(action)
		}

		return when (action) {
			ReaderViewerAction.Menu -> ReaderControllerStep(
				actionController.copy(
					state = actionController.state.copy(
						menuVisible = !actionController.state.menuVisible
					)
				)
			)
			is ReaderViewerAction.TurnPage -> actionController.turnPage(action.direction)
			is ReaderViewerAction.ScrollViewport -> actionController.scrollViewport(action.direction)
		}
	}

	fun applySettings(settings: ReaderSettings): ReaderControllerStep {
		val normalized = settings.normalizedReaderSettings()
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.copy(settings = normalized)
				)
			),
			engineCommands = listOf(
				ReaderEngineCommand.ApplySettings(normalized)
			)
		)
	}

	fun openContentsDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.Contents)

	fun openReadingModeDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.ReadingMode)

	fun openSettingsDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.Settings)

	private fun openDialog(dialog: ReaderControllerDialog): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					dialog = dialog,
					menuVisible = true
				)
			)
		)

	fun closeDialog(): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					dialog = null,
					menuVisible = true
				)
			)
		)

	private fun onShellCoverViewerAction(action: ReaderViewerAction): ReaderControllerStep {
		return when {
			action == ReaderViewerAction.Menu -> ReaderControllerStep(
				copy(state = state.copy(menuVisible = !state.menuVisible))
			)
			action == ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next) ||
				action == ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down) -> ReaderControllerStep(
				copy(state = state.copy(shellCoverVisible = false, menuVisible = false))
			)
			else -> ReaderControllerStep(this)
		}
	}

	private fun turnPage(direction: ReaderPageTurnDirection): ReaderControllerStep =
		if (
			direction == ReaderPageTurnDirection.Previous &&
			state.canReturnToShellCover &&
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = state.nativeShellCoverUrl,
				shellCoverVisible = state.shellCoverVisible,
				locator = state.chrome.currentLocator
			)
		) {
			ReaderControllerStep(
				copy(
					state = state.copy(
						shellCoverVisible = true,
						menuVisible = false,
						dialog = null
					)
				)
			)
		} else {
			ReaderControllerStep(
				controller = this,
				engineCommands = listOf(ReaderEngineCommand.TurnPage(direction))
			)
		}

	private fun scrollViewport(direction: ReaderViewportScrollDirection): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(ReaderEngineCommand.ScrollViewport(direction))
		)
}

private fun ReaderChapterProgressState.updatedFrom(
	locator: ReaderLocator,
	tocTitle: String?
): ReaderChapterProgressState {
	val nextPageCount = locator.chapterPageCount?.takeIf { it > 0 }
		?: locator.pageCount?.takeIf { it > 0 }
		?: pageCount
	val normalizedPageCount = nextPageCount.coerceAtLeast(1)
	val nextPageIndex = locator.chapterPageIndex?.takeIf { it >= 0 }
		?: locator.pageIndex?.takeIf { it >= 0 }
		?: pageIndex
	val normalizedPageIndex = nextPageIndex.coerceIn(0, normalizedPageCount - 1)
	val normalizedProgress = locator.chapterProgress
		?.takeIf(Double::isFinite)
		?.coerceIn(0.0, 1.0)
		?: if (normalizedPageCount > 1) {
			(normalizedPageIndex.toDouble() / (normalizedPageCount - 1)).coerceIn(0.0, 1.0)
		} else {
			0.0
		}
	return copy(
		href = locator.href?.trim()?.takeIf { it.isNotEmpty() } ?: href,
		title = tocTitle?.trim()?.takeIf { it.isNotEmpty() } ?: title,
		pageIndex = normalizedPageIndex,
		pageCount = normalizedPageCount,
		progress = normalizedProgress
	)
}

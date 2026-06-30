package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress
import kotlin.math.roundToLong

data class ReaderSearchState(
	val query: String = "",
	val results: List<ReaderSearchResult> = emptyList(),
	val active: Boolean = false,
	val progress: Double? = null,
	val complete: Boolean = false
) {
	val searching: Boolean
		get() = active && !complete
}

data class ReaderSelection(
	val text: String? = null,
	val cfi: String? = null,
	val href: String? = null,
	val footnote: Boolean? = null,
	val contextText: String? = null,
	val posLeft: Double? = null,
	val posTop: Double? = null,
	val posRight: Double? = null,
	val posBottom: Double? = null
)

data class ReaderSelectionActionState(
	val selectedText: String? = null,
	val selectedCfi: String? = null,
	val selectedHref: String? = null,
	val canCopy: Boolean = false,
	val canHighlight: Boolean = false,
	val canNote: Boolean = false
) {
	val visible: Boolean
		get() = canCopy || canHighlight || canNote
}

data class ReaderSelectionNoteDraft(
	val bookId: String,
	val bookTitle: String,
	val text: String,
	val cfi: String,
	val href: String? = null,
	val sectionTitle: String? = null,
	val note: String = ""
)

enum class ReaderControllerDialog {
	Contents,
	Search,
	Settings,
	WhispersyncPlayer
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

data class ReaderLoadedDocument(
	val index: Int? = null,
	val href: String? = null,
	val title: String? = null,
	val sectionId: String? = null
)

data class ReaderExternalLinkPromptState(
	val href: String,
	val anchorHref: String? = null
)

sealed interface ReaderLinkInteraction {
	data class Internal(
		val href: String? = null,
		val prevented: Boolean = false,
		val source: String? = null
	) : ReaderLinkInteraction

	data class External(
		val href: String? = null,
		val anchorHref: String? = null
	) : ReaderLinkInteraction
}

enum class ReaderAnnotationInteractionKind {
	Clicked,
	Drawn
}

data class ReaderAnnotationInteraction(
	val kind: ReaderAnnotationInteractionKind,
	val value: String? = null,
	val index: Int? = null,
	val rangeCfi: String? = null
)

data class ReaderAnnotationPopupState(
	val value: String? = null,
	val index: Int? = null,
	val rangeCfi: String? = null,
	val text: String? = null,
	val note: String? = null,
	val color: String? = null
) {
	val visible: Boolean
		get() = !value.isNullOrBlank() ||
			index != null ||
			!rangeCfi.isNullOrBlank() ||
			!text.isNullOrBlank() ||
			!note.isNullOrBlank()
}

data class ReaderFootnotePopupState(
	val href: String? = null,
	val text: String? = null,
	val noteType: String? = null,
	val hidden: Boolean = false
) {
	val visible: Boolean
		get() = !href.isNullOrBlank() ||
			!text.isNullOrBlank() ||
			!noteType.isNullOrBlank()
}

sealed interface ReaderOverlayInteraction {
	data class Created(val index: Int? = null) : ReaderOverlayInteraction
	data class FootnoteOpened(
		val href: String? = null,
		val noteType: String? = null
	) : ReaderOverlayInteraction
	data object FootnoteClosed : ReaderOverlayInteraction
	data object PullUp : ReaderOverlayInteraction
}

data class ReaderEngineNavigationState(
	val canGoBack: Boolean = false,
	val canGoForward: Boolean = false,
	val visible: Boolean = canGoBack || canGoForward
)

data class ReaderControllerState(
	val publication: ReaderPublicationIdentity? = null,
	val activeEngine: ReaderPublicationFormat? = null,
	val chrome: ReaderChromeState = ReaderChromeState(),
	val chapterProgress: ReaderChapterProgressState = ReaderChapterProgressState(),
	val loadedDocument: ReaderLoadedDocument? = null,
	val lastLinkInteraction: ReaderLinkInteraction? = null,
	val externalLinkPrompt: ReaderExternalLinkPromptState? = null,
	val lastAnnotationInteraction: ReaderAnnotationInteraction? = null,
	val annotationPopup: ReaderAnnotationPopupState? = null,
	val footnotePopup: ReaderFootnotePopupState? = null,
	val lastOverlayInteraction: ReaderOverlayInteraction? = null,
	val engineNavigation: ReaderEngineNavigationState = ReaderEngineNavigationState(),
	val shellCoverVisible: Boolean = false,
	val nativeShellCoverUrl: String? = null,
	val nativeShellCoverReturnLocatorKey: String? = null,
	val canReturnToShellCover: Boolean = false,
	val menuVisible: Boolean = false,
	val dialog: ReaderControllerDialog? = null,
	val search: ReaderSearchState = ReaderSearchState(),
	val toc: List<ReaderTocItem> = emptyList(),
	val selection: ReaderSelection? = null,
	val selectionNoteDraft: ReaderSelectionNoteDraft? = null,
	val annotations: ReaderAnnotationState = ReaderAnnotationState(),
	val bookmarks: ReaderBookmarkState = ReaderBookmarkState(),
	val readingProgress: ReaderReadingProgressState = ReaderReadingProgressState(),
	val paginationProfile: ReaderPaginationProfileStatus = ReaderPaginationProfileStatus(),
	val whispersync: ReaderWhispersyncSessionState = ReaderWhispersyncSessionState(),
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

	val canNavigateToPreviousChapter: Boolean
		get() = adjacentTocChapter(direction = -1) != null

	val canNavigateToNextChapter: Boolean
		get() = adjacentTocChapter(direction = 1) != null

	val selectionActions: ReaderSelectionActionState
		get() {
			val selectedText = selection?.text.normalizedReaderSelectionValue()
			val selectedCfi = selection?.cfi.normalizedReaderSelectionValue()
			val selectedHref = selection?.href.normalizedReaderSelectionValue()
			val canCopy = selectedText != null
			val canAnchorSelection = canCopy && selectedCfi != null && publication != null
			return ReaderSelectionActionState(
				selectedText = selectedText,
				selectedCfi = selectedCfi,
				selectedHref = selectedHref,
				canCopy = canCopy,
				canHighlight = canAnchorSelection,
				canNote = canAnchorSelection
			)
		}
}

data class ReaderControllerStep(
	val controller: ReaderController,
	val engineCommands: List<ReaderEngineCommand> = emptyList(),
	val progressToSave: BinderyReadingProgress? = null,
	val whispersyncAudioSeekTarget: WhispersyncAudioSeekTarget? = null
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
					loadedDocument = null,
					lastLinkInteraction = null,
					externalLinkPrompt = null,
					lastAnnotationInteraction = null,
					annotationPopup = null,
					footnotePopup = null,
					lastOverlayInteraction = null,
					engineNavigation = ReaderEngineNavigationState(),
					shellCoverVisible = !normalizedRequest.nativeShellCoverUrl.isNullOrBlank(),
					nativeShellCoverUrl = normalizedRequest.nativeShellCoverUrl,
					nativeShellCoverReturnLocatorKey = null,
					canReturnToShellCover = normalizedRequest.canReturnToShellCover,
					menuVisible = false,
					dialog = null,
					selection = null,
					selectionNoteDraft = null,
					paginationProfile = ReaderPaginationProfileStatus(),
					whispersync = ReaderWhispersyncSessionState(),
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
				val decision = if (event.locator.isWhispersyncAudioFollowRelocation()) {
					ReaderProgressSaveDecision(state = progressSaveGate)
				} else {
					progressSaveGate.onEngineEvent(event)
				}
				val progress = state.publication?.let { publication ->
					decision.locatorToSave?.toBinderyReadingProgress(
						bookId = publication.bookId,
						resourceHref = publication.resourceHref,
						kind = publication.kind
					)
				}
				val nextReadingProgress = progress?.let(state.readingProgress::upsert) ?: state.readingProgress
				val nextNativeShellCoverReturnLocatorKey = state.nativeShellCoverReturnLocatorKey
					?: if (
						state.canReturnToShellCover &&
						readerShouldReturnToNativeShellCover(
							shellCoverUrl = state.nativeShellCoverUrl,
							shellCoverVisible = state.shellCoverVisible,
							locator = event.locator
						)
					) {
						readerNativeShellCoverReturnLocatorKey(event.locator)
					} else {
						null
					}
				val dismissShellCover = readerExplicitReadableRelocationDismissesNativeShellCover(
					shellCoverVisible = state.shellCoverVisible,
					locator = event.locator
				)
				ReaderControllerStep(
					controller = copy(
						progressSaveGate = decision.state,
						state = state.copy(
							chrome = nextChrome,
							chapterProgress = state.chapterProgress.updatedFrom(event.locator, event.tocTitle),
							readingProgress = nextReadingProgress,
							nativeShellCoverReturnLocatorKey = nextNativeShellCoverReturnLocatorKey,
							shellCoverVisible = if (dismissShellCover) false else state.shellCoverVisible,
							menuVisible = if (dismissShellCover) false else state.menuVisible
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
			is ReaderEngineEvent.PaginationProfileStatusChanged -> ReaderControllerStep(
				copy(state = state.copy(paginationProfile = event.profile))
			)
			is ReaderEngineEvent.ContentActionClaimed -> ReaderControllerStep(
				copy(state = state.copy(lastContentActionClaim = event.claim))
			)
			is ReaderEngineEvent.InternalLinkRequested -> ReaderControllerStep(
				copy(
					state = state.copy(
						lastLinkInteraction = ReaderLinkInteraction.Internal(
							href = event.href,
							prevented = event.prevented,
							source = event.source
						)
					)
				)
			)
			is ReaderEngineEvent.ExternalLinkOpened -> ReaderControllerStep(
				copy(
					state = state.onExternalLinkOpened(event)
				)
			)
			is ReaderEngineEvent.AnnotationClicked -> ReaderControllerStep(
				copy(
					state = state.onAnnotationClicked(event)
				)
			)
			is ReaderEngineEvent.AnnotationDrawn -> ReaderControllerStep(
				copy(
					state = state.copy(
						lastAnnotationInteraction = ReaderAnnotationInteraction(
							kind = ReaderAnnotationInteractionKind.Drawn,
							value = event.value,
							index = event.index,
							rangeCfi = event.rangeCfi
						)
					)
				)
			)
			is ReaderEngineEvent.OverlayCreated -> ReaderControllerStep(
				copy(
					state = state.copy(
						lastOverlayInteraction = ReaderOverlayInteraction.Created(index = event.index)
					)
				)
			)
			is ReaderEngineEvent.DocLoaded -> ReaderControllerStep(
				copy(
					state = ReaderLoadedDocument(
						index = event.index,
						href = event.href,
						title = event.title,
						sectionId = event.sectionId
					).let { document ->
						state.copy(
							loadedDocument = document,
							chapterProgress = state.chapterProgress.updatedFrom(document)
						)
					}
				)
			)
			is ReaderEngineEvent.NavigationStateChanged -> ReaderControllerStep(
				copy(
					state = state.copy(
						engineNavigation = ReaderEngineNavigationState(
							canGoBack = event.canGoBack,
							canGoForward = event.canGoForward,
							visible = event.canGoBack || event.canGoForward
						)
					)
				)
			)
			is ReaderEngineEvent.FootnoteOpened -> ReaderControllerStep(
				copy(
					state = state.copy(
						footnotePopup = ReaderFootnotePopupState(
							href = event.href,
							text = event.text,
							noteType = event.noteType,
							hidden = event.hidden
						),
						lastOverlayInteraction = ReaderOverlayInteraction.FootnoteOpened(
							href = event.href,
							noteType = event.noteType
						)
					)
				)
			)
			ReaderEngineEvent.FootnoteClose -> ReaderControllerStep(
				copy(
					state = state.copy(
						footnotePopup = null,
						lastOverlayInteraction = ReaderOverlayInteraction.FootnoteClosed
					)
				)
			)
			is ReaderEngineEvent.PullUp -> ReaderControllerStep(
				copy(
					state = state.copy(
						lastOverlayInteraction = ReaderOverlayInteraction.PullUp,
						menuVisible = state.menuVisible
					)
				)
			)
			is ReaderEngineEvent.VisibleTextRange -> onVisibleTextRange(event)
			is ReaderEngineEvent.SearchResults -> ReaderControllerStep(
				copy(
					state = state.copy(
						search = ReaderSearchState(
							query = event.query,
							results = event.results,
							active = event.query.isNotBlank(),
							progress = event.progress,
							complete = event.complete
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
							href = event.href,
							footnote = event.footnote,
							contextText = event.contextText,
							posLeft = event.posLeft,
							posTop = event.posTop,
							posRight = event.posRight,
							posBottom = event.posBottom
						)
					)
				)
			)
			ReaderEngineEvent.SelectionCleared -> ReaderControllerStep(
				copy(state = state.copy(selection = null, selectionNoteDraft = null))
			)
			is ReaderEngineEvent.MediaOverlayActive -> {
				if (state.activeMediaOverlay == event.fragment) {
					return ReaderControllerStep(
						copy(
							state = state.copy(
								activeMediaOverlay = event.fragment,
								audioMetadataLabel = event.fragment.label
							)
						)
					)
				}
				val audioSeekTarget = state.whispersync.audioSeekTargetForActiveOverlay(event.fragment)
				ReaderControllerStep(
					copy(
						state = state.copy(
							whispersync = audioSeekTarget?.let { target ->
								state.whispersync.copy(
									audioSeekTarget = target,
									status = ReaderWhispersyncStatus(
										kind = ReaderWhispersyncStatusKind.SeekingAudio,
										label = "Syncing audiobook",
										detail = target.segment.label,
										audioResource = target.audioResource,
										positionMs = target.positionMs
									)
								)
							} ?: state.whispersync,
							activeMediaOverlay = event.fragment,
							audioMetadataLabel = event.fragment.label
						)
					),
					whispersyncAudioSeekTarget = audioSeekTarget
				)
			}
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
		if (normalized.isBlank()) {
			return clearSearch()
		}
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					search = ReaderSearchState(
						query = normalized,
						results = emptyList(),
						active = normalized.isNotBlank(),
						progress = 0.0
					)
				)
			),
			engineCommands = listOf(ReaderEngineCommand.Search(normalized))
		)
	}

	fun clearSearch(): ReaderControllerStep =
		ReaderControllerStep(
			controller = copy(
				state = state.copy(search = ReaderSearchState())
			),
			engineCommands = listOf(ReaderEngineCommand.ClearSearch)
		)

	fun navigateToSearchResult(result: ReaderSearchResult): ReaderControllerStep {
		val cfi = result.cfi.normalizedReaderSelectionValue()
		val href = result.href.normalizedReaderSelectionValue()
		if (cfi == null && href == null) return ReaderControllerStep(this)
		return navigateTo(
			ReaderLocator(
				href = href,
				cfi = cfi
			)
		)
	}

	fun navigateTo(locator: ReaderLocator): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(ReaderEngineCommand.NavigateTo(locator))
		)

	fun navigateToBookmark(bookmark: ReaderBookmark): ReaderControllerStep =
		navigateToSavedMark(bookmark.toLocator())

	fun navigateToAnnotation(annotation: ReaderAnnotation): ReaderControllerStep =
		navigateToSavedMark(annotation.toLocator())

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

	fun navigateToPreviousChapter(): ReaderControllerStep =
		navigateToAdjacentTocChapter(direction = -1)

	fun navigateToNextChapter(): ReaderControllerStep =
		navigateToAdjacentTocChapter(direction = 1)

	fun navigateHistoryBack(): ReaderControllerStep =
		navigateHistory(
			enabled = state.engineNavigation.canGoBack,
			direction = ReaderHistoryDirection.Back
		)

	fun navigateHistoryForward(): ReaderControllerStep =
		navigateHistory(
			enabled = state.engineNavigation.canGoForward,
			direction = ReaderHistoryDirection.Forward
		)

	fun dismissHistoryNavigation(): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					engineNavigation = state.engineNavigation.copy(visible = false)
				)
			)
		)

	private fun navigateHistory(
		enabled: Boolean,
		direction: ReaderHistoryDirection
	): ReaderControllerStep =
		if (enabled) {
			ReaderControllerStep(
				controller = this,
				engineCommands = listOf(ReaderEngineCommand.NavigateHistory(direction))
			)
		} else {
			ReaderControllerStep(this)
		}

	private fun navigateToAdjacentTocChapter(direction: Int): ReaderControllerStep {
		val targetHref = state.adjacentTocChapter(direction)
			?.href
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?: return ReaderControllerStep(this)
		return navigateTo(ReaderLocator(href = targetHref))
	}

	private fun navigateToSavedMark(locator: ReaderLocator): ReaderControllerStep =
		ReaderControllerStep(
			controller = copy(
				state = state.copy(
					dialog = null,
					menuVisible = true
				)
			),
			engineCommands = listOf(ReaderEngineCommand.NavigateTo(locator))
		)

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

	fun onReadaloudPlaybackState(playbackState: ReaderReadaloudPlaybackUiState): ReaderControllerStep {
		val currentWhispersync = state.whispersync
		val baseSync = if (currentWhispersync.sync.syncEnabled == playbackState.syncEnabled) {
			currentWhispersync.sync
		} else {
			currentWhispersync.sync.setSyncEnabled(playbackState.syncEnabled)
		}
		val playbackStep = if (!playbackState.isPlaying) {
			if (currentWhispersync.status.kind == ReaderWhispersyncStatusKind.Playing) {
				baseSync.onAudiobookPlaybackPausedStep(
					audioResource = playbackState.audioResource,
					positionMs = playbackState.positionMs,
					clearPlaybackOverlay = true
				)
			} else {
				ReaderWhispersyncPlaybackPositionStep(state = baseSync)
			}
		} else {
			playbackState.audioResource
				?.takeIf { it.isNotBlank() }
				?.let { audioResource ->
					baseSync.onAudiobookPlaybackPositionStep(
						timeline = currentWhispersync.timeline,
						audioResource = audioResource,
						audioTrackIndex = playbackState.trackIndex,
						positionMs = playbackState.positionMs
					)
				}
		}
		val syncState = playbackStep?.state ?: currentWhispersync.sync
		val command = syncState.engineCommand
			?.takeIf { syncState.engineCommandKey != currentWhispersync.sync.engineCommandKey }
		val overlayFragment = (command as? ReaderEngineCommand.ApplyMediaOverlay)?.fragment
		val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
		return ReaderControllerStep(
			copy(
				state = state.copy(
					chrome = state.chrome.onReadaloudPlaybackState(playbackState),
					whispersync = currentWhispersync.copy(
						sync = syncState,
						status = playbackStep?.status ?: currentWhispersync.status
					),
					activeMediaOverlay = when {
						overlayFragment != null -> overlayFragment
						shouldClearOverlay -> null
						else -> state.activeMediaOverlay
					},
					audioMetadataLabel = when {
						overlayFragment != null -> overlayFragment.label
						shouldClearOverlay -> null
						else -> state.audioMetadataLabel
					}
				)
			),
			engineCommands = listOfNotNull(command)
		)
	}

	fun loadWhispersyncSidecar(sidecar: WhispersyncSidecar): ReaderControllerStep {
		val currentWhispersync = state.whispersync
		val visibleRange = currentWhispersync.visibleTextRange
		val baseWhispersync = ReaderWhispersyncSessionState(
			sidecar = sidecar,
			visibleTextRange = visibleRange,
			status = readerWhispersyncReadyStatus(sidecar.timeline)
		)
		val syncStep = visibleRange?.let { range ->
			baseWhispersync.sync.onVisibleTextRange(
				timeline = sidecar.timeline,
				textHref = range.textHref,
				visibleStart = range.visibleStart,
				visibleEnd = range.visibleEnd
			)
		}
		val command = syncStep?.state?.engineCommand
		val overlayFragment = (command as? ReaderEngineCommand.ApplyMediaOverlay)?.fragment
		val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
		return ReaderControllerStep(
			copy(
				state = state.copy(
					whispersync = baseWhispersync.copy(
						sync = syncStep?.state ?: baseWhispersync.sync,
						audioSeekTarget = syncStep?.audioSeekTarget,
						status = syncStep?.status ?: baseWhispersync.status
					),
					activeMediaOverlay = when {
						overlayFragment != null -> overlayFragment
						shouldClearOverlay -> null
						else -> state.activeMediaOverlay
					},
					audioMetadataLabel = when {
						overlayFragment != null -> overlayFragment.label
						shouldClearOverlay -> null
						else -> state.audioMetadataLabel
					}
				)
			),
			engineCommands = listOfNotNull(command),
			whispersyncAudioSeekTarget = syncStep?.audioSeekTarget
		)
	}

	fun reportWhispersyncLoadFailure(label: String, detail: String? = null): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					whispersync = state.whispersync.copy(
						status = ReaderWhispersyncStatus(
							kind = ReaderWhispersyncStatusKind.LoadFailed,
							label = label.trim().takeIf { it.isNotEmpty() } ?: "Whispersync unavailable",
							detail = detail?.trim()?.takeIf { it.isNotEmpty() }
						)
					)
				)
			)
		)

	fun repairWhispersyncMismatch(): ReaderControllerStep {
		val currentWhispersync = state.whispersync
		if (!currentWhispersync.status.repairable) {
			return ReaderControllerStep(this)
		}
		val visibleRange = currentWhispersync.visibleTextRange
			?: return ReaderControllerStep(this)
		val syncStep = currentWhispersync.sync
			.copy(activeSegmentKey = null)
			.onVisibleTextRange(
				timeline = currentWhispersync.timeline,
				textHref = visibleRange.textHref,
				visibleStart = visibleRange.visibleStart,
				visibleEnd = visibleRange.visibleEnd
			)
		val command = syncStep.state.engineCommand
			?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
		val overlayFragment = (command as? ReaderEngineCommand.ApplyMediaOverlay)?.fragment
		val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
		val progress = syncStep.audioSeekTarget?.let {
			state.publication?.let { publication ->
				state.chrome.currentLocator?.toBinderyReadingProgress(
					bookId = publication.bookId,
					resourceHref = publication.resourceHref,
					kind = publication.kind
				)
			}
		}
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						sync = syncStep.state,
						audioSeekTarget = syncStep.audioSeekTarget,
						status = syncStep.status ?: currentWhispersync.status
					),
					activeMediaOverlay = when {
						overlayFragment != null -> overlayFragment
						shouldClearOverlay -> null
						else -> state.activeMediaOverlay
					},
					audioMetadataLabel = when {
						overlayFragment != null -> overlayFragment.label
						shouldClearOverlay -> null
						else -> state.audioMetadataLabel
					}
				)
			),
			engineCommands = listOfNotNull(command),
			progressToSave = progress,
			whispersyncAudioSeekTarget = syncStep.audioSeekTarget
		)
	}

	private fun onVisibleTextRange(event: ReaderEngineEvent.VisibleTextRange): ReaderControllerStep {
		val visibleRange = ReaderWhispersyncVisibleTextRange(
			textHref = event.textHref,
			visibleStart = event.visibleStart,
			visibleEnd = event.visibleEnd,
			rangeCfi = event.rangeCfi,
			source = event.source
		)
		val currentWhispersync = state.whispersync
		if (event.isWhispersyncAudioFollowRange()) {
			return ReaderControllerStep(
				controller = copy(
					state = state.copy(
						whispersync = currentWhispersync.copy(
							visibleTextRange = visibleRange
						)
					)
				)
			)
		}
		val syncStep = currentWhispersync.sync.onVisibleTextRange(
			timeline = currentWhispersync.timeline,
			textHref = event.textHref,
			visibleStart = event.visibleStart,
			visibleEnd = event.visibleEnd
		)
		val command = syncStep.state.engineCommand
			?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
		val overlayFragment = (command as? ReaderEngineCommand.ApplyMediaOverlay)?.fragment
		val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
		val progress = syncStep.audioSeekTarget?.let {
			state.publication?.let { publication ->
				state.chrome.currentLocator?.toBinderyReadingProgress(
					bookId = publication.bookId,
					resourceHref = publication.resourceHref,
					kind = publication.kind
				)
			}
		}
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						sync = syncStep.state,
						visibleTextRange = visibleRange,
						audioSeekTarget = syncStep.audioSeekTarget,
						status = syncStep.status ?: currentWhispersync.status
					),
					activeMediaOverlay = when {
						overlayFragment != null -> overlayFragment
						shouldClearOverlay -> null
						else -> state.activeMediaOverlay
					},
					audioMetadataLabel = when {
						overlayFragment != null -> overlayFragment.label
						shouldClearOverlay -> null
						else -> state.audioMetadataLabel
					}
				)
			),
			engineCommands = listOfNotNull(command),
			progressToSave = progress,
			whispersyncAudioSeekTarget = syncStep.audioSeekTarget
		)
	}

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
			controller = copy(state = state.copy(annotations = nextAnnotations, selection = null)),
			engineCommands = listOf(
				ReaderEngineCommand.ApplyAnnotations(
					nextAnnotations.annotationsForBook(publication.bookId)
				)
			)
		)
	}

	fun startSelectionNote(): ReaderControllerStep {
		val publication = state.publication ?: return ReaderControllerStep(this)
		val selectionActions = state.selectionActions
		val selectedText = selectionActions.selectedText ?: return ReaderControllerStep(this)
		val selectedCfi = selectionActions.selectedCfi ?: return ReaderControllerStep(this)
		return ReaderControllerStep(
			copy(
				state = state.copy(
					selection = null,
					selectionNoteDraft = ReaderSelectionNoteDraft(
						bookId = publication.bookId,
						bookTitle = publication.title,
						text = selectedText,
						cfi = selectedCfi,
						href = selectionActions.selectedHref,
						sectionTitle = state.chrome.currentSectionTitle?.trim()?.takeIf { it.isNotEmpty() }
					)
				)
			)
		)
	}

	fun saveSelectionNote(note: String): ReaderControllerStep {
		val publication = state.publication ?: return ReaderControllerStep(this)
		val draft = state.selectionNoteDraft ?: return ReaderControllerStep(this)
		val nextAnnotations = state.annotations.addSelectionNote(draft = draft, note = note)
		if (nextAnnotations == state.annotations) {
			return ReaderControllerStep(this)
		}
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					annotations = nextAnnotations,
					selection = null,
					selectionNoteDraft = null
				)
			),
			engineCommands = listOf(
				ReaderEngineCommand.ApplyAnnotations(
					nextAnnotations.annotationsForBook(publication.bookId)
				)
			)
		)
	}

	fun dismissSelectionActions(): ReaderControllerStep =
		ReaderControllerStep(copy(state = state.copy(selection = null)))

	fun dismissSelectionNote(): ReaderControllerStep =
		ReaderControllerStep(copy(state = state.copy(selectionNoteDraft = null)))

	fun dismissAnnotationPopup(): ReaderControllerStep =
		ReaderControllerStep(copy(state = state.copy(annotationPopup = null)))

	fun dismissFootnotePopup(): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					footnotePopup = null,
					lastOverlayInteraction = ReaderOverlayInteraction.FootnoteClosed
				)
			)
		)

	fun dismissExternalLinkPrompt(): ReaderControllerStep =
		ReaderControllerStep(copy(state = state.copy(externalLinkPrompt = null)))

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
		val controller = if (state.lastContentActionClaim != null) {
			copy(state = state.copy(lastContentActionClaim = null))
		} else {
			this
		}

		if (controller.state.shellCoverVisible) {
			return controller.onShellCoverViewerAction(action)
		}

		return when (action) {
			ReaderViewerAction.Menu -> ReaderControllerStep(
				controller.copy(
					state = controller.state.copy(
						menuVisible = !controller.state.menuVisible
					)
				)
			)
			is ReaderViewerAction.TurnPage -> controller.turnPage(action.direction)
			is ReaderViewerAction.PreviewPageDrag -> controller.previewPageDrag(action)
			is ReaderViewerAction.ScrollViewport -> controller.scrollViewport(action.direction)
			is ReaderViewerAction.NavigateTo -> controller.navigateTo(action.locator)
			is ReaderViewerAction.ContentLongPressAt -> controller.contentLongPressAt(action)
		}
	}

	fun applySettings(settings: ReaderSettings): ReaderControllerStep {
		val normalized = settings.normalizedReaderSettings()
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.copy(settings = normalized),
					nativeShellCoverReturnLocatorKey = null
				)
			),
			engineCommands = listOf(
				ReaderEngineCommand.ApplySettings(normalized)
			)
		)
	}

	fun openContentsDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.Contents)

	fun openSearchDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.Search)

	fun openSettingsDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.Settings)

	fun openWhispersyncPlayerDialog(): ReaderControllerStep =
		openDialog(ReaderControllerDialog.WhispersyncPlayer)

	private fun openDialog(dialog: ReaderControllerDialog): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					dialog = dialog,
					menuVisible = true
				)
			)
		)

	fun showMenus(): ReaderControllerStep =
		ReaderControllerStep(copy(state = state.copy(menuVisible = true)))

	fun hideMenus(): ReaderControllerStep =
		ReaderControllerStep(copy(state = state.copy(menuVisible = false)))

	fun closeDialog(): ReaderControllerStep =
		ReaderControllerStep(
			copy(
				state = state.copy(
					dialog = null,
					menuVisible = true
				)
			)
		)

	fun closeSearchDialog(): ReaderControllerStep =
		ReaderControllerStep(
			controller = copy(
				state = state.copy(
					dialog = null,
					menuVisible = true,
					search = ReaderSearchState()
				)
			),
			engineCommands = listOf(ReaderEngineCommand.ClearSearch)
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
			state.nativeShellCoverReturnLocatorKey == readerNativeShellCoverReturnLocatorKey(state.chrome.currentLocator) &&
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
						nativeShellCoverReturnLocatorKey = readerNativeShellCoverReturnLocatorKey(state.chrome.currentLocator),
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

	private fun previewPageDrag(action: ReaderViewerAction.PreviewPageDrag): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(
				ReaderEngineCommand.PreviewPageDrag(
					deltaX = action.deltaX,
					deltaY = action.deltaY,
					viewWidth = action.viewWidth,
					viewHeight = action.viewHeight,
					phase = action.phase
				)
			)
		)

	private fun contentLongPressAt(action: ReaderViewerAction.ContentLongPressAt): ReaderControllerStep =
		ReaderControllerStep(
			controller = this,
			engineCommands = listOf(
				ReaderEngineCommand.ContentLongPressAt(
					x = action.x,
					y = action.y,
					viewWidth = action.viewWidth,
					viewHeight = action.viewHeight
				)
			)
	)
}

private fun ReaderControllerState.onExternalLinkOpened(
	event: ReaderEngineEvent.ExternalLinkOpened
): ReaderControllerState {
	val interaction = ReaderLinkInteraction.External(
		href = event.href,
		anchorHref = event.anchorHref
	)
	val href = event.href?.trim()?.takeIf { it.isNotEmpty() }
	val anchorHref = event.anchorHref?.trim()?.takeIf { it.isNotEmpty() }
	return copy(
		lastLinkInteraction = interaction,
		externalLinkPrompt = href?.let {
			ReaderExternalLinkPromptState(
				href = it,
				anchorHref = anchorHref
			)
		}
	)
}

private fun ReaderControllerState.onAnnotationClicked(
	event: ReaderEngineEvent.AnnotationClicked
): ReaderControllerState {
	val value = event.value?.trim()?.takeIf { it.isNotEmpty() }
	val rangeCfi = event.rangeCfi?.trim()?.takeIf { it.isNotEmpty() }
	val savedAnnotation = savedAnnotationForClick(value = value, rangeCfi = rangeCfi)
	val interaction = ReaderAnnotationInteraction(
		kind = ReaderAnnotationInteractionKind.Clicked,
		value = event.value,
		index = event.index,
		rangeCfi = event.rangeCfi
	)
	val popup = ReaderAnnotationPopupState(
		value = value,
		index = event.index,
		rangeCfi = rangeCfi,
		text = savedAnnotation?.text?.trim()?.takeIf { it.isNotEmpty() },
		note = savedAnnotation?.note?.trim()?.takeIf { it.isNotEmpty() },
		color = savedAnnotation?.color?.trim()?.takeIf { it.isNotEmpty() }
	).takeIf { it.visible }
	return copy(
		lastAnnotationInteraction = interaction,
		annotationPopup = popup
	)
}

private fun ReaderControllerState.savedAnnotationForClick(
	value: String?,
	rangeCfi: String?
): ReaderAnnotation? {
	val bookId = publication?.bookId
	return annotations.annotations.firstOrNull { annotation ->
		(bookId == null || annotation.bookId == bookId) &&
			(annotation.cfi == value || annotation.cfi == rangeCfi)
	}
}

private fun readerNativeShellCoverReturnLocatorKey(locator: ReaderLocator?): String? {
	locator ?: return null
	val href = locator.href
		?.trim()
		?.replace('\\', '/')
		.orEmpty()
	val pageIndex = locator.pageIndex?.takeIf { it >= 0 }?.toString().orEmpty()
	val pageCount = locator.pageCount?.takeIf { it > 0 }?.toString().orEmpty()
	val chapterPageIndex = locator.chapterPageIndex?.takeIf { it >= 0 }?.toString().orEmpty()
	val chapterPageCount = locator.chapterPageCount?.takeIf { it > 0 }?.toString().orEmpty()
	return listOf(
		href.substringBefore('#').substringBefore('?'),
		pageIndex,
		pageCount,
		chapterPageIndex,
		chapterPageCount
	).joinToString("|")
}

private fun readerExplicitReadableRelocationDismissesNativeShellCover(
	shellCoverVisible: Boolean,
	locator: ReaderLocator
): Boolean {
	if (!shellCoverVisible) return false
	val reason = locator.reason?.trim().orEmpty()
	if (
		reason.isBlank() ||
		reason == "relocate-committed" ||
		reason == "initial-resume" ||
		reason.startsWith("pagination-profile-")
	) return false
	val href = locator.href?.trim().orEmpty()
	return href.isBlank() || !readerHrefLooksLikeNativeShellCoverBoundary(href)
}

private fun ReaderChapterProgressState.updatedFrom(
	locator: ReaderLocator,
	tocTitle: String?
): ReaderChapterProgressState {
	val nextHref = locator.href?.trim()?.takeIf { it.isNotEmpty() }
	val hrefChanged = nextHref != null &&
		readerTocHrefKey(nextHref) != readerTocHrefKey(href)
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

private fun ReaderChapterProgressState.updatedFrom(
	document: ReaderLoadedDocument
): ReaderChapterProgressState {
	val nextHref = document.href?.trim()?.takeIf { it.isNotEmpty() }
	val nextTitle = document.title?.trim()?.takeIf { it.isNotEmpty() }
	if (nextHref == null) {
		return copy(title = nextTitle ?: title)
	}
	val currentHrefKey = readerTocHrefKey(href)
	val nextHrefKey = readerTocHrefKey(nextHref)
	val documentChanged = nextHrefKey != null && nextHrefKey != currentHrefKey
	return if (documentChanged) {
		copy(
			href = nextHref,
			title = nextTitle ?: title,
			pageIndex = 0,
			pageCount = 1,
			progress = 0.0
		)
	} else {
		copy(
			href = nextHref,
			title = nextTitle ?: title
		)
	}
}

private fun ReaderControllerState.adjacentTocChapter(direction: Int): ReaderTocItem? {
	val currentHref = readerTocHrefKey(chapterProgress.href)
		?: readerTocHrefKey(chrome.currentLocator?.href)
		?: return null
	val navigableItems = toc.filter { readerTocHrefKey(it.href) != null }
	val currentIndex = navigableItems.indexOfFirst { item ->
		readerTocHrefKey(item.href) == currentHref
	}
	if (currentIndex < 0) {
		return null
	}
	return navigableItems.getOrNull(currentIndex + direction)
}

private fun readerTocHrefKey(href: String?): String? {
	val trimmed = href
		?.trim()
		?.replace('\\', '/')
		?.takeIf { it.isNotEmpty() }
		?: return null
	val withoutFragment = trimmed.substringBefore('#')
	val withoutQuery = withoutFragment.substringBefore('?')
	return withoutQuery
		.trim()
		.trimStart('.', '/')
		.takeIf { it.isNotEmpty() }
}

private fun ReaderEngineEvent.VisibleTextRange.isWhispersyncAudioFollowRange(): Boolean =
	source.equals("media-overlay-follow", ignoreCase = true)

private fun ReaderLocator.isWhispersyncAudioFollowRelocation(): Boolean =
	reason.equals("media-overlay-follow", ignoreCase = true)

private fun ReaderWhispersyncSessionState.audioSeekTargetForActiveOverlay(
	fragment: ReaderOverlayFragment
): WhispersyncAudioSeekTarget? {
	if (!available || !sync.syncEnabled) return null
	val clipBeginSeconds = fragment.clipBeginSeconds
		?.takeIf(Double::isFinite)
		?: return null
	val startMs = (clipBeginSeconds * 1000.0).roundToLong().coerceAtLeast(0L)
	val endMs = fragment.clipEndSeconds
		?.takeIf(Double::isFinite)
		?.let { (it * 1000.0).roundToLong().coerceAtLeast(startMs) }
		?: startMs
	val segment = WhispersyncSegment(
		id = fragment.fragmentId,
		audioResource = fragment.resourceHref,
		startMs = startMs,
		endMs = endMs,
		textHref = fragment.textHref?.trim().orEmpty(),
		fragmentId = fragment.fragmentId,
		textStart = fragment.textStart,
		textEnd = fragment.textEnd,
		label = fragment.label
	)
	return WhispersyncAudioSeekTarget(
		audioResource = segment.audioResource,
		positionMs = segment.startMs,
		segment = segment
	)
}

private fun String?.normalizedReaderSelectionValue(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

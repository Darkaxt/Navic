package paige.navic.reader

import paige.navic.domain.repositories.BinderyWordSyncReference

internal fun ReaderCoordinator.configureWordSync(
	reference: BinderyWordSyncReference?
): ReaderCoordinatorStep =
	ReaderCoordinatorStep(copy(wordSync = wordSync.configure(reference)))

internal fun ReaderCoordinator.acceptsWordSyncGeneration(generation: Long): Boolean =
	wordSync.reference != null && wordSync.generation == generation

internal fun ReaderCoordinator.onReadaloudPlaybackState(
	playbackState: ReaderReadaloudPlaybackUiState,
	playbackIdentity: ReaderWordSyncPlaybackIdentity?,
	publishOverlayProgress: Boolean = true
): ReaderCoordinatorStep = applyWordSyncDecision(
	wordSync.coordinate(
		controllerStep = controller.onReadaloudPlaybackState(
			playbackState = playbackState,
			publishOverlayProgress = publishOverlayProgress
		),
		playback = playbackIdentity
	)
)

internal fun ReaderCoordinator.wordSyncBoundaries(
	playbackIdentity: ReaderWordSyncPlaybackIdentity?
): List<ReaderWordSyncBoundary> = playbackIdentity
	?.let(wordSync::boundariesForPlayback)
	.orEmpty()

internal fun ReaderCoordinator.onWordSyncBoundary(
	dispatch: ReaderWordSyncBoundaryDispatch
): ReaderCoordinatorStep = applyWordSyncDecision(
	wordSync.coordinateBoundary(
		controller = controller,
		playback = ReaderWordSyncPlaybackIdentity(
			audioResourceId = dispatch.timeline.audioResourceId,
			audioTrackIndex = dispatch.timeline.audioTrackIndex,
			positionMs = dispatch.timeline.positionMs,
			playbackSpeed = dispatch.timeline.playbackSpeed
		),
		boundary = dispatch.boundary
	)
)

internal fun ReaderCoordinator.onWordSyncIndexVerified(
	generation: Long,
	index: WordSyncIndex,
	provenance: WordSyncPublicationProvenance
): ReaderCoordinatorStep = applyWordSyncDecision(
	wordSync.onIndexVerified(generation, index, provenance, controller)
)

internal fun ReaderCoordinator.onWordSyncIndexFailed(generation: Long): ReaderCoordinatorStep =
	applyWordSyncDecision(wordSync.onIndexFailed(generation, controller))

internal fun ReaderCoordinator.onWordSyncChapterVerified(
	generation: Long,
	chapter: WordSyncChapter
): ReaderCoordinatorStep = applyWordSyncDecision(
	wordSync.onChapterVerified(generation, chapter, controller)
)

internal fun ReaderCoordinator.onWordSyncChapterFailed(
	generation: Long,
	chapterKey: String
): ReaderCoordinatorStep =
	applyWordSyncDecision(wordSync.onChapterFailed(generation, chapterKey, controller))

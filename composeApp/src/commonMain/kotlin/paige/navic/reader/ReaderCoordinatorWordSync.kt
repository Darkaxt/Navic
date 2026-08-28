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

internal fun ReaderCoordinator.onWordSyncClear(
	timeline: ReaderWordSyncTimelineSnapshot
): ReaderCoordinatorStep = applyWordSyncDecision(
	wordSync.coordinateClear(
		controller = controller,
		playback = ReaderWordSyncPlaybackIdentity(
			audioResourceId = timeline.audioResourceId,
			audioTrackIndex = timeline.audioTrackIndex,
			positionMs = timeline.positionMs,
			playbackSpeed = timeline.playbackSpeed
		)
	)
)

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

internal class ReaderWordSyncBridgeCommandSequence {
	private val commands = mutableListOf<ReaderBridgeCommand>()

	fun capture(previous: ReaderEngineViewState, next: ReaderEngineViewState) {
		val previousCommandKey = (previous as? ReaderEngineViewState.WebViewPublication)?.commandKey
		val nextPublication = next as? ReaderEngineViewState.WebViewPublication ?: return
		if (nextPublication.commandKey == previousCommandKey) return
		when (val command = nextPublication.command) {
			is ReaderEngineHostCommand.FoliateBridge -> commands += command.command
			is ReaderEngineHostCommand.FoliateBridgeSequence -> commands += command.commands
			null -> Unit
		}
	}

	fun applyTo(coordinator: ReaderCoordinator): ReaderCoordinator {
		if (commands.size <= 1) return coordinator
		val publication = coordinator.viewState as? ReaderEngineViewState.WebViewPublication
			?: return coordinator
		return coordinator.copy(
			viewState = publication.copy(
				command = ReaderEngineHostCommand.FoliateBridgeSequence(commands)
			)
		)
	}
}

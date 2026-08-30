package paige.navic.reader

import paige.navic.domain.repositories.BinderyWordSyncReference

data class ReaderWordSyncPlaybackIdentity(
	val audioResourceId: String,
	val audioTrackIndex: Int,
	val positionMs: Long,
	val playbackSpeed: Float
) {
	init {
		require(audioResourceId.isNotBlank() && audioResourceId == audioResourceId.trim())
		require(audioTrackIndex >= 0)
		require(positionMs >= 0L)
	}
}

data class ReaderWordSyncRawPoint(
	val provenanceId: String,
	val byteOffset: Int
) {
	init {
		require(provenanceId.isNotBlank() && provenanceId == provenanceId.trim())
		require(byteOffset >= 0)
	}
}

sealed interface ReaderWordSyncEffect {
	val generation: Long

	data class LoadIndex(
		override val generation: Long,
		val reference: BinderyWordSyncReference
	) : ReaderWordSyncEffect

	data class LoadChapter(
		override val generation: Long,
		val identity: paige.navic.domain.repositories.BinderyWhispersyncIdentity,
		val summary: WordSyncChapterSummary
	) : ReaderWordSyncEffect
}

data class ReaderVerifiedWordSyncChapter(
	val chapter: WordSyncChapter,
	val descriptor: ReaderRawTextProvenanceDescriptor
)

data class ReaderWordSyncDecision(
	val coordinator: ReaderWordSyncPlaybackCoordinator,
	val controllerStep: ReaderControllerStep,
	val effects: List<ReaderWordSyncEffect> = emptyList()
)

internal enum class ReaderWordSyncDiagnosticIndexState(val logValue: String) {
	Missing("missing"),
	Pending("pending"),
	Ready("ready")
}

internal data class ReaderWordSyncBoundaryInputDiagnostic(
	val referencePresent: Boolean,
	val indexState: ReaderWordSyncDiagnosticIndexState,
	val loadedChapterCount: Int,
	val pendingChapterCount: Int,
	val failedChapterCount: Int,
	val matchingTrackCount: Int,
	val presentableWordCount: Int
)

data class ReaderWordSyncPlaybackCoordinator(
	val reference: BinderyWordSyncReference? = null,
	val generation: Long = 0L,
	val index: WordSyncIndex? = null,
	val provenance: WordSyncPublicationProvenance? = null,
	val chapters: Map<String, ReaderVerifiedWordSyncChapter> = emptyMap(),
	private val indexLoadPending: Boolean = false,
	private val pendingChapterKeys: Set<String> = emptySet(),
	private val failedChapterKeys: Set<String> = emptySet(),
	private val lastCueCommand: ReaderEngineCommand? = null,
	private val lastPlayback: ReaderWordSyncPlaybackIdentity? = null,
	private val fallbackByRequestId: Map<Long, ReaderEngineCommand.ApplyMediaOverlay> = emptyMap(),
	private val activeBoundaryRequestId: Long? = null,
	private val activeBoundarySequence: Long? = null,
	private val clearedThroughBoundaryByRequestId: Map<Long, Long> = emptyMap()
) {
	fun configure(nextReference: BinderyWordSyncReference?): ReaderWordSyncPlaybackCoordinator {
		if (nextReference == reference) return this
		return ReaderWordSyncPlaybackCoordinator(
			reference = nextReference,
			generation = generation + 1L
		)
	}

	fun coordinate(
		controllerStep: ReaderControllerStep,
		playback: ReaderWordSyncPlaybackIdentity? = null
	): ReaderWordSyncDecision = coordinate(
		controllerStep = controllerStep,
		playback = playback,
		rawPoint = null,
		readerEvent = false
	)

	fun coordinateReaderEvent(
		controllerStep: ReaderControllerStep,
		rawPoint: ReaderWordSyncRawPoint?
	): ReaderWordSyncDecision = coordinate(
		controllerStep = controllerStep,
		playback = null,
		rawPoint = rawPoint,
		readerEvent = true
	)

	private fun coordinate(
		controllerStep: ReaderControllerStep,
		playback: ReaderWordSyncPlaybackIdentity?,
		rawPoint: ReaderWordSyncRawPoint?,
		readerEvent: Boolean
	): ReaderWordSyncDecision {
		val cueCommand = controllerStep.engineCommands.singleOrNull().asCueOverlayCommand()
		val clearsOverlay = controllerStep.engineCommands.singleOrNull() == ReaderEngineCommand.ClearMediaOverlay
		val readerPreparedStart = controllerStep.controller.state.whispersync.playbackStartPending
		val preparedPlayback = controllerStep.readerPreparedWordSyncPlayback(
			playbackSpeed = playback?.playbackSpeed ?: lastPlayback?.playbackSpeed ?: 1f
		)
		val playbackForCue = playback ?: if (readerPreparedStart) preparedPlayback else lastPlayback
		val remembered = copy(
			lastCueCommand = when {
				clearsOverlay -> null
				cueCommand != null -> cueCommand
				else -> lastCueCommand
			},
			lastPlayback = playback ?: lastPlayback,
			fallbackByRequestId = if (clearsOverlay) emptyMap() else fallbackByRequestId,
			activeBoundaryRequestId = if (clearsOverlay) null else activeBoundaryRequestId,
			activeBoundarySequence = if (clearsOverlay) null else activeBoundarySequence,
			clearedThroughBoundaryByRequestId =
				if (clearsOverlay) emptyMap() else clearedThroughBoundaryByRequestId
		)
		val loadDecision = remembered.requestDataForDemand(
			controller = controllerStep.controller,
			cueCommand = cueCommand ?: remembered.lastCueCommand,
			playback = playbackForCue
		)
		val coordinatorWithDemand = loadDecision.coordinator
		if (cueCommand == null) {
			return ReaderWordSyncDecision(
				coordinator = coordinatorWithDemand,
				controllerStep = controllerStep,
				effects = loadDecision.effects
			)
		}
		val cueFragment = cueCommand.overlayFragmentOrNull() ?: return ReaderWordSyncDecision(
			coordinator = coordinatorWithDemand,
			controllerStep = controllerStep,
			effects = loadDecision.effects
		)
		val candidate = coordinatorWithDemand.rawCandidate(
			controller = controllerStep.controller,
			cueFragment = cueFragment,
			playback = playbackForCue,
			rawPoint = rawPoint,
			readerEvent = readerEvent
		)
		val requestId = cueFragment.overlayRequestId
		if (candidate == null || requestId == null) {
			return ReaderWordSyncDecision(
				coordinator = coordinatorWithDemand.copy(fallbackByRequestId = emptyMap()),
				controllerStep = controllerStep,
				effects = loadDecision.effects
			)
		}

		val boundarySequence = playbackForCue
			?.let(coordinatorWithDemand::boundariesForPlayback)
			?.singleOrNull { boundary -> boundary.word === candidate.word }
			?.sequence
		val rawFragment = candidate.toOverlayFragment(
			cueFragment = cueFragment,
			playback = playbackForCue,
			boundarySequence = boundarySequence
		)
		val rawCommand = when (cueCommand) {
			is ReaderEngineCommand.ApplyMediaOverlay -> ReaderEngineCommand.ApplyMediaOverlay(rawFragment)
			is ReaderEngineCommand.UpdateMediaOverlayProgress ->
				ReaderEngineCommand.UpdateMediaOverlayProgress(rawFragment)
			else -> return ReaderWordSyncDecision(coordinatorWithDemand, controllerStep, loadDecision.effects)
		}
		val fallback = ReaderEngineCommand.ApplyMediaOverlay(cueFragment)
		val exactStep = controllerStep.withExactWordSyncSeek(candidate.word, rawCommand)
		return ReaderWordSyncDecision(
			coordinator = coordinatorWithDemand.copy(
				fallbackByRequestId = mapOf(requestId to fallback),
				activeBoundaryRequestId = requestId.takeIf { boundarySequence != null },
				activeBoundarySequence = boundarySequence
			),
			controllerStep = exactStep,
			effects = loadDecision.effects
		)
	}

	internal fun hasExactBoundaryPresentation(
		controller: ReaderController,
		playback: ReaderWordSyncPlaybackIdentity?
	): Boolean {
		if (reference == null || playback == null) return false
		val cueFragment = lastCueCommand.asCueOverlayCommand()
			?.overlayFragmentOrNull()
			?: return false
		return rawCandidate(
			controller = controller,
			cueFragment = cueFragment,
			playback = playback,
			rawPoint = null,
			readerEvent = false
		) != null
	}

	internal fun boundaryInputDiagnostic(
		playback: ReaderWordSyncPlaybackIdentity?
	): ReaderWordSyncBoundaryInputDiagnostic {
		val matchingTracks = playback?.let { identity ->
			chapters.values.flatMap { verified ->
				verified.chapter.tracks.filter { track ->
					track.audioResourceId == identity.audioResourceId &&
						track.audioTrackIndex == identity.audioTrackIndex
				}
			}
		}.orEmpty()
		return ReaderWordSyncBoundaryInputDiagnostic(
			referencePresent = reference != null,
			indexState = when {
				index != null -> ReaderWordSyncDiagnosticIndexState.Ready
				indexLoadPending -> ReaderWordSyncDiagnosticIndexState.Pending
				else -> ReaderWordSyncDiagnosticIndexState.Missing
			},
			loadedChapterCount = chapters.size,
			pendingChapterCount = pendingChapterKeys.size,
			failedChapterCount = failedChapterKeys.size,
			matchingTrackCount = matchingTracks.size,
			presentableWordCount = matchingTracks.sumOf { track ->
				track.words.count { word -> word.status in 1..4 }
			}
		)
	}

	internal fun boundariesForPlayback(
		playback: ReaderWordSyncPlaybackIdentity
	): List<ReaderWordSyncBoundary> = chapters.values
		.flatMap { verified ->
			verified.chapter.tracks
				.filter { track ->
					track.audioResourceId == playback.audioResourceId &&
						track.audioTrackIndex == playback.audioTrackIndex
				}
				.flatMap(WordSyncTrack::readerWordSyncBoundaries)
		}
		.sortedWith(
			compareBy<ReaderWordSyncBoundary> { it.audioStartMs }
				.thenBy { it.word.spineIndex }
				.thenBy { it.word.ebookStart }
		)
		.mapIndexed { index, boundary -> boundary.copy(sequence = index.toLong()) }

	internal fun coordinateClear(
		controller: ReaderController,
		playback: ReaderWordSyncPlaybackIdentity
	): ReaderWordSyncDecision {
		val requestId = activeBoundaryRequestId
		val boundarySequence = activeBoundarySequence
		if (requestId == null || boundarySequence == null) {
			return ReaderWordSyncDecision(copy(lastPlayback = playback), ReaderControllerStep(controller))
		}
		val clearedThrough = maxOf(
			clearedThroughBoundaryByRequestId[requestId] ?: -1L,
			boundarySequence
		)
		return ReaderWordSyncDecision(
			coordinator = copy(
				lastPlayback = playback,
				activeBoundaryRequestId = null,
				activeBoundarySequence = null,
				clearedThroughBoundaryByRequestId =
					clearedThroughBoundaryByRequestId + (requestId to clearedThrough)
			),
			controllerStep = ReaderControllerStep(
				controller = controller.copy(
					state = controller.state.copy(
						activeMediaOverlay = null,
						activeMediaOverlayAnchorReceipt = null,
						audioMetadataLabel = null
					)
				),
				engineCommands = listOf(
					ReaderEngineCommand.ClearMediaOverlayPresentation(
						overlayRequestId = requestId,
						clearedThroughBoundarySequence = clearedThrough
					)
				)
			)
		)
	}

	internal fun coordinateBoundary(
		controller: ReaderController,
		playback: ReaderWordSyncPlaybackIdentity,
		boundary: ReaderWordSyncBoundary
	): ReaderWordSyncDecision {
		val remembered = copy(lastPlayback = playback)
		val word = boundary.word
		if (
			word.audioResourceId != playback.audioResourceId ||
			word.audioTrackIndex != playback.audioTrackIndex ||
			word.audioStartMs > playback.positionMs
		) {
			return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		}
		val cueFragment = lastCueCommand.asCueOverlayCommand()
			?.overlayFragmentOrNull()
			?: return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		val verified = chapters.values.singleOrNull { candidate ->
			candidate.chapter.tracks.any { track -> word in track.words }
		} ?: return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		if (
			word.status !in 1..4 ||
			word.ebookHref != cueFragment.textHref ||
			controller.state.rawTextProvenanceById[verified.descriptor.id]?.status !=
				RawTextProvenanceStatus.Ready
		) {
			return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		}
		val requestId = cueFragment.overlayRequestId
			?: return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		val candidate = ReaderWordSyncCandidate(verified, word)
		val rawFragment = candidate.toOverlayFragment(
			cueFragment = cueFragment,
			playback = playback,
			boundarySequence = boundary.sequence
		)
		return ReaderWordSyncDecision(
			coordinator = remembered.copy(
				fallbackByRequestId = mapOf(
					requestId to ReaderEngineCommand.ApplyMediaOverlay(cueFragment)
				),
				activeBoundaryRequestId = requestId,
				activeBoundarySequence = boundary.sequence
			),
			controllerStep = ReaderControllerStep(
				controller = controller,
				engineCommands = listOf(
					ReaderEngineCommand.UpdateMediaOverlayProgress(rawFragment)
				)
			)
		)
	}

	fun onIndexVerified(
		generation: Long,
		index: WordSyncIndex,
		provenance: WordSyncPublicationProvenance,
		controller: ReaderController
	): ReaderWordSyncDecision {
		val expectedReference = reference
		if (
			generation != this.generation ||
			expectedReference == null ||
			index.identity != expectedReference.identity ||
			provenance.coordinateBasis != index.coordinateBasis
		) {
			return ReaderWordSyncDecision(this, ReaderControllerStep(controller))
		}
		val indexed = copy(
			index = index,
			provenance = provenance,
			indexLoadPending = false
		)
		val loadDecision = indexed.requestDataForDemand(
			controller = controller,
			cueCommand = lastCueCommand,
			playback = lastPlayback
		)
		return ReaderWordSyncDecision(
			coordinator = loadDecision.coordinator,
			controllerStep = ReaderControllerStep(controller),
			effects = loadDecision.effects
		)
	}

	fun onIndexFailed(generation: Long, controller: ReaderController): ReaderWordSyncDecision =
		if (generation != this.generation) {
			ReaderWordSyncDecision(this, ReaderControllerStep(controller))
		} else {
			ReaderWordSyncDecision(
				copy(indexLoadPending = false, reference = null),
				ReaderControllerStep(controller)
			)
		}

	fun onChapterVerified(
		generation: Long,
		chapter: WordSyncChapter,
		controller: ReaderController
	): ReaderWordSyncDecision {
		val summary = index?.chapters?.singleOrNull { it.chapterKey == chapter.chapterKey }
		val chapterProvenance = provenance?.chapters?.singleOrNull {
			it.chapterKey == chapter.chapterKey &&
				it.ebookHref == chapter.ebookHref &&
				it.spineIndex == chapter.spineIndex
		}
		if (
			generation != this.generation ||
			reference?.identity != chapter.identity ||
			summary == null ||
			chapterProvenance == null
		) {
			return ReaderWordSyncDecision(this, ReaderControllerStep(controller))
		}
		val descriptor = chapterProvenance.toReaderRawTextProvenanceDescriptor()
		val installed = controller.installRawTextProvenance(descriptor)
		return ReaderWordSyncDecision(
			coordinator = copy(
				chapters = chapters + (
					chapter.chapterKey to ReaderVerifiedWordSyncChapter(chapter, descriptor)
				),
				pendingChapterKeys = pendingChapterKeys - chapter.chapterKey,
				failedChapterKeys = failedChapterKeys - chapter.chapterKey
			),
			controllerStep = installed
		)
	}

	fun onChapterFailed(
		generation: Long,
		chapterKey: String,
		controller: ReaderController
	): ReaderWordSyncDecision =
		if (generation != this.generation || chapterKey !in pendingChapterKeys) {
			ReaderWordSyncDecision(this, ReaderControllerStep(controller))
		} else {
			ReaderWordSyncDecision(
				coordinator = copy(
					pendingChapterKeys = pendingChapterKeys - chapterKey,
					failedChapterKeys = failedChapterKeys + chapterKey
				),
				controllerStep = ReaderControllerStep(controller)
			)
		}

	fun onEngineEvent(
		controller: ReaderController,
		event: ReaderEngineEvent
	): ReaderWordSyncDecision {
		if (event is ReaderEngineEvent.MediaOverlayActive) {
			val requestId = event.fragment.overlayRequestId
			val boundarySequence = event.fragment.wordBoundarySequence
			val clearedThrough = requestId?.let(clearedThroughBoundaryByRequestId::get)
			if (
				requestId != null &&
				boundarySequence != null &&
				clearedThrough != null &&
				boundarySequence <= clearedThrough
			) {
				return ReaderWordSyncDecision(this, ReaderControllerStep(controller))
			}
		}
		if (
			event is ReaderEngineEvent.MediaOverlayInactive &&
			event.coordinateMode == ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8
		) {
			val requestId = event.overlayRequestId
			val fallback = requestId?.let(fallbackByRequestId::get)
			if (requestId != null && fallback != null) {
				return ReaderWordSyncDecision(
					coordinator = copy(fallbackByRequestId = fallbackByRequestId - requestId),
					controllerStep = ReaderControllerStep(
						controller = controller.copy(
							state = controller.state.copy(
								activeMediaOverlay = null,
								activeMediaOverlayAnchorReceipt = null,
								audioMetadataLabel = null
							)
						),
						engineCommands = listOf(fallback)
					)
				)
			}
		}

		val reduced = controller.onEngineEvent(event)
		val rawPoint = when (event) {
			is ReaderEngineEvent.TextPoint -> event.rawPointOrNull()
			is ReaderEngineEvent.VisibleTextRange -> event.rawPointOrNull()
			else -> null
		}
		return when (event) {
			is ReaderEngineEvent.TextPoint,
			is ReaderEngineEvent.VisibleTextRange -> coordinateReaderEvent(reduced, rawPoint)
			else -> coordinate(reduced)
		}
	}

	private fun requestDataForDemand(
		controller: ReaderController,
		cueCommand: ReaderEngineCommand?,
		playback: ReaderWordSyncPlaybackIdentity?
	): ReaderWordSyncLoadDecision {
		val activeReference = reference ?: return ReaderWordSyncLoadDecision(this)
		if (index == null) {
			if (indexLoadPending || cueCommand == null) return ReaderWordSyncLoadDecision(this)
			return ReaderWordSyncLoadDecision(
				coordinator = copy(indexLoadPending = true),
				effects = listOf(ReaderWordSyncEffect.LoadIndex(generation, activeReference))
			)
		}
		val summary = demandedSummary(cueCommand, playback) ?: return ReaderWordSyncLoadDecision(this)
		if (
			summary.chapterKey in chapters ||
			summary.chapterKey in pendingChapterKeys ||
			summary.chapterKey in failedChapterKeys
		) {
			return ReaderWordSyncLoadDecision(this)
		}
		return ReaderWordSyncLoadDecision(
			coordinator = copy(pendingChapterKeys = pendingChapterKeys + summary.chapterKey),
			effects = listOf(
				ReaderWordSyncEffect.LoadChapter(
					generation = generation,
					identity = activeReference.identity,
					summary = summary
				)
			)
		)
	}

	private fun demandedSummary(
		cueCommand: ReaderEngineCommand?,
		playback: ReaderWordSyncPlaybackIdentity?
	): WordSyncChapterSummary? {
		val chapters = index?.chapters.orEmpty()
		val audioSummary = playback?.let { demand ->
			chapters.firstOrNull { summary ->
				summary.audioRanges.any { range ->
					range.audioResourceId == demand.audioResourceId &&
						range.audioTrackIndex == demand.audioTrackIndex &&
						demand.positionMs >= range.startMs && demand.positionMs < range.endMs
				}
			}
		}
		if (audioSummary != null) return audioSummary
		val textHref = cueCommand.overlayFragmentOrNull()?.textHref ?: return null
		return chapters.firstOrNull { it.ebookHref == textHref }
	}

	private fun rawCandidate(
		controller: ReaderController,
		cueFragment: ReaderOverlayFragment,
		playback: ReaderWordSyncPlaybackIdentity?,
		rawPoint: ReaderWordSyncRawPoint?,
		readerEvent: Boolean
	): ReaderWordSyncCandidate? {
		val candidate = (
			if (readerEvent) {
				rawPoint?.let(::candidateForRawPoint)
			} else {
				playback?.let(::candidateForPlayback)
			}
		) ?: return null
		if (candidate.word.status !in 1..4) return null
		if (candidate.word.ebookHref != cueFragment.textHref) return null
		val status = controller.state.rawTextProvenanceById[candidate.verified.descriptor.id]
		if (status?.status != RawTextProvenanceStatus.Ready) return null
		return candidate
	}

	private fun candidateForPlayback(playback: ReaderWordSyncPlaybackIdentity): ReaderWordSyncCandidate? =
		chapters.values.firstNotNullOfOrNull { verified ->
			verified.chapter.wordAtAudioPosition(
				audioResourceId = playback.audioResourceId,
				audioTrackIndex = playback.audioTrackIndex,
				positionMs = playback.positionMs
			)?.let { word -> ReaderWordSyncCandidate(verified, word) }
		}

	private fun candidateForRawPoint(point: ReaderWordSyncRawPoint): ReaderWordSyncCandidate? {
		val verified = chapters.values.singleOrNull { it.descriptor.id == point.provenanceId } ?: return null
		val aggregateOffset = verified.chapter.ebookStart.toLong() + point.byteOffset
		if (aggregateOffset > Int.MAX_VALUE) return null
		val word = verified.chapter.wordAtEbookOffset(aggregateOffset.toInt()) ?: return null
		return ReaderWordSyncCandidate(verified, word)
	}
}

private data class ReaderWordSyncLoadDecision(
	val coordinator: ReaderWordSyncPlaybackCoordinator,
	val effects: List<ReaderWordSyncEffect> = emptyList()
)

private data class ReaderWordSyncCandidate(
	val verified: ReaderVerifiedWordSyncChapter,
	val word: WordSyncWord
) {
	fun toOverlayFragment(
		cueFragment: ReaderOverlayFragment,
		playback: ReaderWordSyncPlaybackIdentity?,
		boundarySequence: Long? = null
	): ReaderOverlayFragment {
		val chapterStart = verified.chapter.ebookStart
		val byteStart = word.ebookStart - chapterStart
		val byteEnd = word.ebookEnd - chapterStart
		val progress = playback
			?.takeIf {
				it.audioResourceId == word.audioResourceId &&
					it.audioTrackIndex == word.audioTrackIndex &&
					word.audioEndMs > word.audioStartMs
			}
			?.let {
				((it.positionMs - word.audioStartMs).toDouble() /
					(word.audioEndMs - word.audioStartMs).toDouble()).coerceIn(0.0, 1.0)
			}
		return ReaderOverlayFragment(
			resourceHref = word.audioResourceId,
			coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
			overlayRequestId = cueFragment.overlayRequestId,
			wordBoundarySequence = boundarySequence,
			textHref = word.ebookHref,
			clipBeginSeconds = word.audioStartMs / 1000.0,
			clipEndSeconds = word.audioEndMs / 1000.0,
			playbackSpeed = playback?.playbackSpeed ?: cueFragment.playbackSpeed,
			label = cueFragment.label,
			rawProvenanceId = verified.descriptor.id,
			rawSpineIndex = word.spineIndex,
			rawByteStart = byteStart,
			rawByteEnd = byteEnd,
			rawProgressFraction = progress
		)
	}
}

private fun ReaderControllerStep.readerPreparedWordSyncPlayback(
	playbackSpeed: Float
): ReaderWordSyncPlaybackIdentity? {
	val whispersync = controller.state.whispersync
	if (!whispersync.playbackStartPending) return null
	val target = whispersync.pendingAudioSeek?.target ?: return null
	val trackIndex = target.audioTrackIndex ?: target.segment.audioTrackIndex ?: return null
	return ReaderWordSyncPlaybackIdentity(
		audioResourceId = target.audioResource,
		audioTrackIndex = trackIndex,
		positionMs = target.positionMs,
		playbackSpeed = playbackSpeed
	)
}

private fun ReaderControllerStep.withExactWordSyncSeek(
	word: WordSyncWord,
	rawCommand: ReaderEngineCommand
): ReaderControllerStep {
	val requestId = rawCommand.overlayFragmentOrNull()?.overlayRequestId
	val pending = controller.state.whispersync.pendingAudioSeek
	val exactPending = pending
		?.takeIf { it.overlayRequestId == requestId }
		?.let { pendingSeek ->
			pendingSeek.copy(
				target = pendingSeek.target.copy(
					audioResource = word.audioResourceId,
					positionMs = word.audioStartMs,
					audioTrackIndex = word.audioTrackIndex
				)
			)
		}
		?: pending
	val exactImmediate = whispersyncAudioSeekTarget
		?.copy(
			audioResource = word.audioResourceId,
			positionMs = word.audioStartMs,
			audioTrackIndex = word.audioTrackIndex
		)
	val rawFragment = rawCommand.overlayFragmentOrNull()
	val nextController = controller.copy(
		state = controller.state.copy(
			whispersync = controller.state.whispersync.copy(pendingAudioSeek = exactPending),
			activeMediaOverlay = when (rawCommand) {
				is ReaderEngineCommand.UpdateMediaOverlayProgress -> rawFragment
				else -> controller.state.activeMediaOverlay
			},
			audioMetadataLabel = when (rawCommand) {
				is ReaderEngineCommand.UpdateMediaOverlayProgress -> rawFragment?.label
				else -> controller.state.audioMetadataLabel
			}
		)
	)
	return copy(
		controller = nextController,
		engineCommands = listOf(rawCommand),
		whispersyncAudioSeekTarget = exactImmediate
	)
}

private fun ReaderEngineCommand?.asCueOverlayCommand(): ReaderEngineCommand? = when (this) {
	is ReaderEngineCommand.ApplyMediaOverlay -> takeIf {
		fragment.coordinateMode == ReaderOverlayCoordinateMode.CueV1DomUtf16
	}
	is ReaderEngineCommand.UpdateMediaOverlayProgress -> takeIf {
		fragment.coordinateMode == ReaderOverlayCoordinateMode.CueV1DomUtf16
	}
	else -> null
}

private fun WordSyncPublicationChapterProvenance.toReaderRawTextProvenanceDescriptor() =
	ReaderRawTextProvenanceDescriptor(
		id = "wordsync-v1-spine-$spineIndex",
		href = ebookHref,
		spineIndex = spineIndex,
		sourceHash = sourceHash,
		extractedTextHash = extractedTextHash,
		byteLength = extractedByteLength,
		tokenCount = tokenCount
	)

private fun ReaderEngineEvent.TextPoint.rawPointOrNull(): ReaderWordSyncRawPoint? {
	val id = rawProvenanceId ?: return null
	val offset = rawByteOffset ?: return null
	return ReaderWordSyncRawPoint(id, offset)
}

private fun ReaderEngineEvent.VisibleTextRange.rawPointOrNull(): ReaderWordSyncRawPoint? {
	val id = rawProvenanceId ?: return null
	val offset = rawByteStart ?: return null
	return ReaderWordSyncRawPoint(id, offset)
}

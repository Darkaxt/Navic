package paige.navic.reader

internal fun ReaderController.withReadyNativePresentationFixture(): ReaderController {
	val session = state.foliateSessionId ?: "fixture-session"
	val binding = readerPresentationFixtureBinding(session)
	val proof = ReaderNativePagePresentationProof(
		binding = binding,
		transitionToken = null,
		presentedFrame = 9L,
		viewportWidth = 1200,
		viewportHeight = 800,
		rasterGeneration = requireNotNull(binding.rasterGeneration),
		textureGeneration = requireNotNull(binding.textureGeneration)
	)
	return copy(
		state = state.copy(
			presentation = ReaderPresentationState(
				authority = ReaderPresentationAuthority.SettledNativePage(
					ReaderPresentationFrameOwner.NativePage(proof)
				),
				binding = binding,
				preparationFacts = readerReadyPresentationFixtureFacts()
			)
		)
	)
}

internal fun ReaderController.withCommittedShellCoverPresentationFixture(): ReaderController {
	val session = state.foliateSessionId ?: "fixture-session"
	val binding = readerPresentationFixtureBinding(session)
	val proof = ReaderShellCoverCommitProof(
		token = ReaderPresentationToken(1L),
		binding = binding,
		coverGeneration = 7L,
		presentedFrame = 8L,
		viewportWidth = 1200,
		viewportHeight = 800
	)
	return copy(
		state = state.copy(
			shellCoverVisible = true,
			presentation = ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(proof),
				binding = binding,
				preparationFacts = readerReadyPresentationFixtureFacts(),
				nextTokenValue = 2L
			)
		)
	)
}

private fun ReaderController.readerPresentationFixtureBinding(
	session: String
): ReaderPresentationBinding = ReaderPresentationBinding(
	foliateSessionId = session,
	publicationGeneration = 1L,
	viewportGeneration = 2L,
	profileGeneration = 3L,
	destinationCommitIdentity = state.destinationCommitIdentity
		?.takeIf { it.foliateSessionId == session }
		?: ReaderDestinationCommitIdentity(session, 1L),
	rasterGeneration = 4L,
	textureGeneration = 5L,
	preparationGeneration = 6L
)

private fun readerReadyPresentationFixtureFacts() = ReaderPagePreparationFacts(
	phase = ReaderPagePreparationPhase.Ready,
	generation = 6L,
	readiness = ReaderPageReadinessState(
		rasterGeneration = ReaderChapterRasterGenerationState.Ready,
		decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
		textureDeck = ReaderTextureDeckState.Ready,
		interaction = ReaderPageInteractionState.Ready
	)
)

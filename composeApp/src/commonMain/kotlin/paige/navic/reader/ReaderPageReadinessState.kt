package paige.navic.reader

enum class ReaderChapterRasterGenerationState {
	NotScheduled,
	Generating,
	Ready,
	Failed,
	Invalidated
}

enum class ReaderDecodedWorkingSetState {
	Empty,
	Hydrating,
	Ready,
	Recovering,
	Failed
}

enum class ReaderTextureDeckState {
	Empty,
	Preparing,
	Ready,
	Settling,
	Failed
}

enum class ReaderPageInteractionState {
	BlockingInitialPreparation,
	Ready,
	Settling,
	BackgroundPrefetch,
	RefillingWorkingSet,
	BlockingProfileRegeneration,
	Failed
}

data class ReaderPageReadinessState(
	val rasterGeneration: ReaderChapterRasterGenerationState =
		ReaderChapterRasterGenerationState.NotScheduled,
	val decodedWorkingSet: ReaderDecodedWorkingSetState = ReaderDecodedWorkingSetState.Empty,
	val textureDeck: ReaderTextureDeckState = ReaderTextureDeckState.Empty,
	val pendingTextureDeck: ReaderTextureDeckState = ReaderTextureDeckState.Empty,
	val interaction: ReaderPageInteractionState =
		ReaderPageInteractionState.BlockingInitialPreparation
)

data class ReaderPageRendererReadinessState(
	val textureDeck: ReaderTextureDeckState = ReaderTextureDeckState.Empty,
	val pendingTextureDeck: ReaderTextureDeckState = ReaderTextureDeckState.Empty,
	val interaction: ReaderPageInteractionState =
		ReaderPageInteractionState.BlockingInitialPreparation
)

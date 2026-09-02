package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderPresentationBinding

internal data class ReaderPresentationHostBindingSnapshot(
	val pageTurnCanvasEnabled: Boolean,
	val windowVisible: Boolean?,
	val foliateSessionId: String?,
	val publicationGeneration: Long,
	val viewportGeneration: Long,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val profileGeneration: Long?,
	val destinationCommitIdentity: ReaderDestinationCommitIdentity?,
	val preparationGeneration: Long,
	val visualPageIndex: Int?,
	val preparedDeck: ReaderPagePreparedActiveDeck?,
	val preparedDeckAdmitted: Boolean
)

internal fun readerPresentationHostBinding(
	snapshot: ReaderPresentationHostBindingSnapshot
): ReaderPresentationBinding? {
	if (!snapshot.pageTurnCanvasEnabled || snapshot.windowVisible == false) return null
	val foliateSessionId = snapshot.foliateSessionId
		?.takeIf(String::isNotBlank)
		?: return null
	val profileGeneration = snapshot.profileGeneration?.takeIf { it > 0L } ?: return null
	val visualPageIndex = snapshot.visualPageIndex?.takeIf { it >= 0 } ?: return null
	if (
		snapshot.publicationGeneration <= 0L ||
		snapshot.viewportGeneration <= 0L ||
		snapshot.viewportWidth <= 0 ||
		snapshot.viewportHeight <= 0 ||
		snapshot.preparationGeneration < 0L ||
		(snapshot.destinationCommitIdentity?.foliateSessionId != null &&
			snapshot.destinationCommitIdentity.foliateSessionId != foliateSessionId)
	) {
		return null
	}
	val exactDeck = snapshot.preparedDeck?.takeIf { deck ->
		snapshot.preparedDeckAdmitted &&
			deck.rasterProfileEpoch == profileGeneration &&
			deck.sourceCenterPageIndex == visualPageIndex &&
			deck.preparationGeneration == snapshot.preparationGeneration
	}
	return ReaderPresentationBinding(
		foliateSessionId = foliateSessionId,
		publicationGeneration = snapshot.publicationGeneration,
		viewportGeneration = snapshot.viewportGeneration,
		profileGeneration = profileGeneration,
		destinationCommitIdentity = snapshot.destinationCommitIdentity,
		rasterGeneration = exactDeck?.rasterEpoch,
		textureGeneration = exactDeck?.generationId,
		preparationGeneration = snapshot.preparationGeneration
	)
}

package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderPresentationBinding

internal sealed interface ReaderPresentationHostProfileIdentity {
	val generation: Long

	data object Provisional : ReaderPresentationHostProfileIdentity {
		override val generation: Long = 0L
	}

	data class Resolved(
		override val generation: Long
	) : ReaderPresentationHostProfileIdentity {
		init {
			require(generation > 0L)
		}
	}
}

internal data class ReaderPresentationHostBindingSnapshot(
	val pageTurnCanvasEnabled: Boolean,
	val windowVisible: Boolean?,
	val foliateSessionId: String?,
	val publicationGeneration: Long,
	val viewportGeneration: Long,
	val viewportWidth: Int,
	val viewportHeight: Int,
	val profileIdentity: ReaderPresentationHostProfileIdentity,
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
	val profileGeneration = snapshot.profileIdentity.generation
	val exactDeck = snapshot.preparedDeck?.takeIf { deck ->
		snapshot.profileIdentity is ReaderPresentationHostProfileIdentity.Resolved &&
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

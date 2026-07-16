package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import karacken.curl.DeckRejectionReason
import karacken.curl.DeckReleaseReason
import karacken.curl.PageChange
import karacken.curl.PageImage
import karacken.curl.PageSurfaceListener
import karacken.curl.PageSurfaceView
import karacken.curl.RenderCapabilities
import karacken.curl.RenderFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** ReaderDev harness backed exclusively by the imported PlayLikeCurl production surface. */
class ReaderPlayLikeCurlReferenceView(
	context: Context,
	mode: ReaderPlayLikeCurlReferenceMode = ReaderPlayLikeCurlReferenceMode.Reference
) : PageSurfaceView(context) {
	private class PreparedPages(
		val profile: ReaderPlayLikeCurlRasterProfile,
		val deck: ReaderPlayLikeCurlRasterDeck<Bitmap>
	) {
		val generations = mutableSetOf<Long>()
		var obsolete = false
	}

	private val bitmapSource: ReaderPlayLikeCurlBitmapSource = when (mode) {
		ReaderPlayLikeCurlReferenceMode.Reference -> ReaderPlayLikeCurlAssetBitmapSource(context)
		ReaderPlayLikeCurlReferenceMode.Diagnostic -> ReaderPlayLikeCurlDiagnosticBitmapSource()
	}
	private val rasterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val rasterAdapter = ReaderPlayLikeCurlRasterAdapter(
		scope = rasterScope,
		loader = bitmapSource,
		release = Bitmap::recycle
	)
	private val generationOwners = mutableMapOf<Long, PreparedPages>()
	private val preparedPageSets = mutableSetOf<PreparedPages>()
	private var activePages: PreparedPages? = null
	private var requestedProfile: ReaderPlayLikeCurlRasterProfile? = null
	private var capabilitiesAvailable = false
	private var initialDeckSubmitted = false
	private var interactionReady = false
	private var disposedByOwner = false
	private var currentOrdinal = 0
	private var nextGenerationId = 1L

	var onPreparationProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
	var onPreparationCoverReady: (Bitmap) -> Unit = {}
	var onInteractionReadyChanged: (Boolean) -> Unit = {}
	var onRenderFailure: (RenderFailure) -> Unit = {}

	init {
		setPageSurfaceListener(object : PageSurfaceListener {
			override fun onCapabilitiesAvailable(capabilities: RenderCapabilities) {
				capabilitiesAvailable = true
				submitInitialDeckIfReady()
			}

			override fun onDeckPrepared(generationId: Long) {
				if (generationOwners[generationId] === activePages) {
					setInteractionReady(true)
				}
			}

			override fun onDeckRejected(generationId: Long, reason: DeckRejectionReason) {
				val wasActive = generationOwners[generationId] === activePages
				releaseGeneration(generationId)
				if (reason == DeckRejectionReason.SESSION_DETACHED) {
					initialDeckSubmitted = false
				}
				if (wasActive) {
					setInteractionReady(false)
				}
			}

			override fun onDeckReleased(generationId: Long, reason: DeckReleaseReason) {
				releaseGeneration(generationId)
			}

			override fun onSettlementCompleted(
				generationId: Long,
				currentLogicalPageId: String,
				currentPageOrdinal: Int,
				pageChange: PageChange
			) {
				if (pageChange == PageChange.NONE) return
				currentOrdinal = currentPageOrdinal
				activePages?.let(::submitLibraryDeck)
			}

			override fun onRenderFailure(failure: RenderFailure) {
				this@ReaderPlayLikeCurlReferenceView.onRenderFailure(failure)
			}
		})
	}

	fun resumeReference() {
		setVisible(true)
		attach()
		submitInitialDeckIfReady()
	}

	fun pauseReference() {
		setVisible(false)
		detach()
	}

	override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
		super.onSizeChanged(width, height, oldWidth, oldHeight)
		if (width <= 0 || height <= 0 || (width == oldWidth && height == oldHeight)) return
		prepareRasterDeck(
			if (height > width) ReaderPlayLikeCurlOrientation.Portrait
			else ReaderPlayLikeCurlOrientation.Landscape
		)
	}

	fun disposeReference() {
		if (disposedByOwner) return
		disposedByOwner = true
		dispose()
		preparedPageSets.forEach { pages ->
			pages.obsolete = true
			closeIfUnused(pages)
		}
		rasterAdapter.close()
		rasterScope.cancel()
	}

	private fun prepareRasterDeck(orientation: ReaderPlayLikeCurlOrientation) {
		val profile = bitmapSource.profile(orientation)
		if (requestedProfile == profile && activePages != null) return
		requestedProfile = profile
		activePages?.let { previous ->
			previous.obsolete = true
			closeIfUnused(previous)
		}
		activePages = null
		initialDeckSubmitted = false
		setInteractionReady(false)

		val preparation = rasterAdapter.prepare(
			profile = profile,
			pageIndices = (0 until bitmapSource.pageCount).toList()
		) { progress ->
			post {
				if (requestedProfile == profile && !disposedByOwner) {
					onPreparationProgress(progress.completed, progress.total)
				}
			}
		}
		rasterScope.launch {
			val deck = preparation.await() ?: return@launch
			if (requestedProfile != profile || disposedByOwner) {
				deck.close()
				return@launch
			}
			post {
				if (requestedProfile != profile || disposedByOwner) {
					deck.close()
					return@post
				}
				val pages = PreparedPages(profile, deck)
				preparedPageSets += pages
				activePages = pages
				currentOrdinal = currentOrdinal.coerceIn(0, bitmapSource.pageCount - 1)
				deck.value(0)?.let(onPreparationCoverReady)
				submitInitialDeckIfReady()
			}
		}
	}

	private fun submitInitialDeckIfReady() {
		val pages = activePages ?: return
		if (!capabilitiesAvailable || initialDeckSubmitted || disposedByOwner) return
		initialDeckSubmitted = true
		submitLibraryDeck(pages)
	}

	private fun submitLibraryDeck(pages: PreparedPages) {
		if (pages !== activePages || disposedByOwner) return
		val generationId = nextGenerationId++
		val deck = readerPlayLikeCurlLibraryDeck(
			orientation = pages.profile.orientation,
			generationId = generationId,
			currentOrdinal = currentOrdinal,
			pageCount = bitmapSource.pageCount,
			page = { pageGenerationId, ordinal -> pages.page(pageGenerationId, ordinal) }
		)
		pages.generations += generationId
		generationOwners[generationId] = pages
		submitDeck(deck)
	}

	private fun PreparedPages.page(
		generationId: Long,
		ordinal: Int
	): PageImage<Bitmap> {
		val bitmap = checkNotNull(deck.value(ordinal)) {
			"Missing prepared PlayLikeCurl page $ordinal"
		}
		return PageImage(
			generationId,
			"${profile.sourceIdentity}:${profile.orientation.name.lowercase()}:$ordinal",
			ordinal,
			bitmap.width,
			bitmap.height,
			bitmap
		)
	}

	private fun releaseGeneration(generationId: Long) {
		val pages = generationOwners.remove(generationId) ?: return
		pages.generations -= generationId
		closeIfUnused(pages)
	}

	private fun closeIfUnused(pages: PreparedPages) {
		if (!pages.obsolete || pages.generations.isNotEmpty()) return
		preparedPageSets -= pages
		pages.deck.close()
	}

	private fun setInteractionReady(ready: Boolean) {
		if (interactionReady == ready) return
		interactionReady = ready
		onInteractionReadyChanged(ready)
	}
}

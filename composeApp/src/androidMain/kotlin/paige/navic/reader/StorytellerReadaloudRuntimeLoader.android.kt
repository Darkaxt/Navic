package paige.navic.reader

import java.io.File

data class StorytellerReadaloudRuntime(
	val publicationUrl: String,
	val playbackPlan: ReadaloudPlaybackPlan,
	val timeline: MediaOverlayTimeline
)

class StorytellerReadaloudRuntimeLoader(
	private val fetchResourceBytes: suspend (String) -> ByteArray,
	private val cacheRoot: File
) {
	suspend fun load(request: ReaderPublicationResourceRequest): StorytellerReadaloudRuntime {
		require(request.kind == ReaderPublicationKind.Readaloud) {
			"Storyteller readaloud runtime requires a readaloud publication."
		}
		val epubBytes = fetchResourceBytes(request.safeResourceHref())
		val readaloudPackage = StorytellerMediaOverlayParser.parsePackage(epubBytes)
		val cache = StorytellerReadaloudAudioCache.materialize(
			sessionId = request.readerPublicationCacheKey(),
			epubBytes = epubBytes,
			readaloudPackage = readaloudPackage,
			cacheRoot = cacheRoot
		)
		val session = readaloudPackage.toReadaloudAudioSession(
			id = request.bookId,
			title = request.title,
			audioHrefResolver = cache::audioHrefResolver
		)
		return StorytellerReadaloudRuntime(
			publicationUrl = cache.publicationUri,
			playbackPlan = session.toReadaloudPlaybackPlan(),
			timeline = readaloudPackage.timeline
		)
	}
}

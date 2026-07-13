package paige.navic.reader

import java.io.File

data class StorytellerReadaloudRuntime(
	val publicationUrl: String,
	val playbackPlan: ReadaloudPlaybackPlan,
	val timeline: MediaOverlayTimeline,
	val cacheKey: String,
	val fromCache: Boolean,
	val sessionLease: ReaderSessionLease
)

class StorytellerReadaloudRuntimeLoader(
	private val fetchResourceBytes: suspend (String) -> ByteArray,
	private val cacheRoot: File
) {
	suspend fun load(request: ReaderPublicationResourceRequest): StorytellerReadaloudRuntime {
		require(request.kind == ReaderPublicationKind.Readaloud) {
			"Storyteller readaloud runtime requires a readaloud publication."
		}
		val resolved = BinderyReaderPublicationResolver(
			fetchResourceBytes = fetchResourceBytes,
			cacheRoot = cacheRoot
		).resolve(request)
		val epubBytes = resolved.publicationFile.readBytes()
		val readaloudPackage = StorytellerMediaOverlayParser.parsePackage(epubBytes)
		val cache = StorytellerReadaloudAudioCache.materialize(
			sessionId = resolved.cacheKey,
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
			publicationUrl = cache.publicationUrl,
			playbackPlan = session.toReadaloudPlaybackPlan(),
			timeline = readaloudPackage.timeline,
			cacheKey = resolved.cacheKey,
			fromCache = resolved.fromCache,
			sessionLease = resolved.sessionLease + cache.sessionLease
		)
	}
}

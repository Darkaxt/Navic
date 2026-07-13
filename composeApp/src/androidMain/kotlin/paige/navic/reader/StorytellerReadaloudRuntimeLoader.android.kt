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

class StorytellerReadaloudRuntimeLoader internal constructor(
	private val fetchResourceBytes: suspend (String) -> ByteArray,
	private val cacheRoot: File,
	private val archiveReadObserver: (StorytellerArchiveReadMetrics) -> Unit = {}
) {
	suspend fun load(request: ReaderPublicationResourceRequest): StorytellerReadaloudRuntime {
		require(request.kind == ReaderPublicationKind.Readaloud) {
			"Storyteller readaloud runtime requires a readaloud publication."
		}
		val resolved = BinderyReaderPublicationResolver(
			fetchResourceBytes = fetchResourceBytes,
			cacheRoot = cacheRoot
		).resolve(request)
		val archiveMetrics = StorytellerArchiveReadMetrics()
		val (readaloudPackage, cache) = try {
			StorytellerEpubArchive.open(resolved.publicationFile, archiveMetrics).use { archive ->
				val parsed = StorytellerMediaOverlayParser.parsePackage(archive)
				parsed to StorytellerReadaloudAudioCache.materialize(
					sessionId = resolved.cacheKey,
					archive = archive,
					publicationFile = resolved.publicationFile,
					publicationUrl = resolved.publicationUrl,
					readaloudPackage = parsed,
					cacheRoot = cacheRoot
				)
			}
		} finally {
			archiveReadObserver(archiveMetrics)
		}
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

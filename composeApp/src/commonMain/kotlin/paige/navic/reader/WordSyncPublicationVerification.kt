package paige.navic.reader

enum class WordSyncPublicationVerificationFailure {
	InvalidArchive,
	ResourceLimit,
	IndexMismatch,
	ShardMismatch
}

class WordSyncPublicationVerificationException(
	val failure: WordSyncPublicationVerificationFailure
) : IllegalArgumentException("Bindery WordSync publication verification failed.")

data class WordSyncPublicationProvenance(
	val coordinateBasis: WordSyncCoordinateBasis,
	val chapters: List<WordSyncPublicationChapterProvenance>
)

data class WordSyncPublicationChapterProvenance(
	val chapterKey: String?,
	val ebookHref: String,
	val spineIndex: Int,
	val sourceHash: String,
	val extractedTextHash: String,
	val extractedByteLength: Int,
	val tokenCount: Int
)

data class WordSyncVerifiedChapter(
	val chapterKey: String,
	val ebookHref: String,
	val spineIndex: Int,
	val wordCount: Int,
	val unmatchedEbookWordCount: Int
)

interface WordSyncPublicationVerifier {
	fun verify(index: WordSyncIndex): WordSyncPublicationVerificationSession
}

interface WordSyncPublicationVerificationSession {
	val provenance: WordSyncPublicationProvenance

	fun verifyChapter(chapter: WordSyncChapter): WordSyncVerifiedChapter
}

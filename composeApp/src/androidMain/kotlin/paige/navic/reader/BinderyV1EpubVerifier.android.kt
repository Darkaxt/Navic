package paige.navic.reader

import java.io.File

internal fun androidWordSyncPublicationVerifierOrNull(
	publicationFile: File,
	format: ReaderPublicationFormat
): WordSyncPublicationVerifier? =
	publicationFile
		.takeIf { format == ReaderPublicationFormat.Epub && it.isFile }
		?.let { verifiedFile ->
			object : WordSyncPublicationVerifier {
				override fun verify(index: WordSyncIndex): WordSyncPublicationVerificationSession =
					BinderyV1EpubVerifier().verify(verifiedFile, index)
			}
		}

internal class BinderyV1EpubVerifier(
	private val limits: BinderyV1EpubVerificationLimits = BinderyV1EpubVerificationLimits()
) {
	fun verify(
		publicationFile: File,
		index: WordSyncIndex
	): WordSyncPublicationVerificationSession = try {
		val documents = BinderyV1EpubLoader(limits).load(publicationFile)
		validateBinderyV1Index(index, documents)
		BinderyV1VerificationSession(index, documents)
	} catch (error: WordSyncPublicationVerificationException) {
		throw error
	} catch (_: Exception) {
		wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
	}
}

private class BinderyV1VerificationSession(
	private val index: WordSyncIndex,
	private val documents: List<BinderyV1EpubDocument>
) : WordSyncPublicationVerificationSession {
	private val documentByTarget = documents.associateBy { document ->
		BinderyChapterTarget(document.href, document.spineIndex)
	}
	private val summaryByKey = index.chapters.associateBy(WordSyncChapterSummary::chapterKey)
	private val summaryByTarget = index.chapters.associateBy { summary ->
		BinderyChapterTarget(summary.ebookHref, summary.spineIndex)
	}

	override val provenance = WordSyncPublicationProvenance(
		coordinateBasis = index.coordinateBasis,
		chapters = documents.map { document ->
			WordSyncPublicationChapterProvenance(
				chapterKey = summaryByTarget[
					BinderyChapterTarget(document.href, document.spineIndex)
				]?.chapterKey,
				ebookHref = document.href,
				spineIndex = document.spineIndex,
				sourceHash = document.content.sourceHash,
				extractedTextHash = document.content.textHash,
				extractedByteLength = document.content.byteLength,
				tokenCount = document.content.tokenCount
			)
		}
	)

	override fun verifyChapter(chapter: WordSyncChapter): WordSyncVerifiedChapter {
		val summary = summaryByKey[chapter.chapterKey]
			?: wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
		if (
			chapter.identity != index.identity ||
			chapter.ebookHref != summary.ebookHref ||
			chapter.spineIndex != summary.spineIndex ||
			chapter.ebookStart != summary.ebookStart ||
			chapter.ebookEnd != summary.ebookEnd ||
			chapter.timeScale != index.timeScale
		) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
		}
		val document = documentByTarget[
			BinderyChapterTarget(chapter.ebookHref, chapter.spineIndex)
		] ?: wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
		val words = chapter.tracks.flatMap(WordSyncTrack::words)
		if (
			words.size != summary.audioWordCount ||
			chapter.unmatchedEbook.size != summary.unmatchedEbookWordCount
		) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
		}
		words.forEach { word ->
			if (
				word.ebookHref != chapter.ebookHref ||
				word.spineIndex != chapter.spineIndex ||
				word.ebookStart < chapter.ebookStart ||
				word.ebookEnd > chapter.ebookEnd ||
				!document.content.containsTokenRange(word.ebookStart, word.ebookEnd)
			) {
				wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
			}
		}
		chapter.unmatchedEbook.forEach { unmatched ->
			val end = unmatched.ebookStart.toLong() + unmatched.ebookLen.toLong()
			if (
				unmatched.ebookStart < chapter.ebookStart ||
				end > chapter.ebookEnd.toLong() ||
				end > Int.MAX_VALUE ||
				!document.content.containsTokenRange(unmatched.ebookStart, end.toInt())
			) {
				wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
			}
		}
		validateLookup(chapter, words.size)
		return WordSyncVerifiedChapter(
			chapterKey = chapter.chapterKey,
			ebookHref = chapter.ebookHref,
			spineIndex = chapter.spineIndex,
			wordCount = words.size,
			unmatchedEbookWordCount = chapter.unmatchedEbook.size
		)
	}

	private fun validateLookup(chapter: WordSyncChapter, wordCount: Int) {
		if (chapter.ebookLookup.size != wordCount) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
		}
		val seen = mutableSetOf<Pair<Int, Int>>()
		var previousStart = -1
		chapter.ebookLookup.forEach { entry ->
			val word = chapter.tracks.getOrNull(entry.trackIndex)
				?.words
				?.getOrNull(entry.wordIndex)
				?: wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
			if (
				entry.ebookStart != word.ebookStart ||
				entry.ebookStart < previousStart ||
				!seen.add(entry.trackIndex to entry.wordIndex)
			) {
				wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ShardMismatch)
			}
			previousStart = entry.ebookStart
		}
	}
}

private fun validateBinderyV1Index(
	index: WordSyncIndex,
	documents: List<BinderyV1EpubDocument>
) {
	val basis = index.coordinateBasis
	if (
		index.timeScale != WordSyncTimeScale ||
		basis.extractor != BinderyV1Extractor ||
		basis.extractorVersion != BinderyV1ExtractorVersion ||
		basis.normalization != BinderyV1Normalization ||
		!BinderyCanonicalSha256.matches(basis.ebookTextHash) ||
		basis.ebookTextHash != binderyAggregateTextHash(documents)
	) {
		wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.IndexMismatch)
	}
	if (
		index.chapters.map(WordSyncChapterSummary::chapterKey).distinct().size != index.chapters.size ||
		index.chapters.map { summary ->
			BinderyChapterTarget(summary.ebookHref, summary.spineIndex)
		}.distinct().size != index.chapters.size
	) {
		wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.IndexMismatch)
	}
	val documentByTarget = documents.associateBy { document ->
		BinderyChapterTarget(document.href, document.spineIndex)
	}
	index.chapters.forEach { summary ->
		val document = documentByTarget[
			BinderyChapterTarget(summary.ebookHref, summary.spineIndex)
		] ?: wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.IndexMismatch)
		if (
			summary.ebookStart < 0 ||
			summary.ebookEnd <= summary.ebookStart ||
			summary.ebookEnd > document.content.byteLength
		) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.IndexMismatch)
		}
	}
}

internal fun wordSyncVerificationFailure(
	failure: WordSyncPublicationVerificationFailure
): Nothing = throw WordSyncPublicationVerificationException(failure)

private data class BinderyChapterTarget(
	val href: String,
	val spineIndex: Int
)

private val BinderyCanonicalSha256 = Regex("sha256:[0-9a-f]{64}")
private const val BinderyV1Extractor = "bindery-epub-text"
private const val BinderyV1ExtractorVersion = "1"
private const val BinderyV1Normalization = "raw-extracted-text-offsets"

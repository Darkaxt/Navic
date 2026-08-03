package paige.navic.reader

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import paige.navic.domain.repositories.BinderyWhispersyncIdentity

internal object BinderyV1EpubTestFixtures {
	const val ChapterHref = "OPS/Text/chapter.xhtml"
	const val ChapterKey = "spine-003-chapter"
	const val ExtractedByteLength = 41
	const val ExtractedTokenCount = 7
	const val SourceHash = "sha256:f4d5eaee5ec9c964c79856d1228441aa2c6100af6422f97dfb289656f6741593"
	const val ExtractedTextHash = "sha256:7b54d9d79d0bf63eae54a525d6c1dbe6d7cf1b28d8c685b93cd46745c92b5759"
	const val AggregateHash = "sha256:1b5b87fda5b9a3306b37c5318bb4fedd76c999c380e7e65c17fc792387e5c4ed"
	const val EmptyAggregateHash = "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

	private const val ChapterBody = "<html><head><style>x</style><script>y</script></head><body><h1>Café &amp; Tea</h1><p>Don’t stop<br/>now</p><div>One&#x20;two&nbsp;</div></body></html>"

	fun publication(
		container: String = defaultContainer,
		opf: String = defaultOpf,
		chapterBody: String = ChapterBody,
		chapterEntry: String = ChapterHref,
		extraEntries: List<Pair<String, ByteArray>> = emptyList()
	): File {
		val bytes = ByteArrayOutputStream().use { output ->
			ZipOutputStream(output).use { zip ->
				listOf(
					"meta-inf/container.xml" to "<not-the-container/>".encodeToByteArray(),
					"META-INF/container.xml" to container.encodeToByteArray(),
					"OPS/package.opf" to opf.encodeToByteArray(),
					chapterEntry to chapterBody.encodeToByteArray(),
					"OPS/Text/upper.xhtml" to "<p>Must be skipped</p>".encodeToByteArray()
				).plus(extraEntries).forEach { (path, body) ->
					zip.putNextEntry(ZipEntry(path))
					zip.write(body)
					zip.closeEntry()
				}
			}
			output.toByteArray()
		}
		return createTempFile("bindery-v1-", ".epub").toFile().apply { writeBytes(bytes) }
	}

	fun index(
		hash: String = AggregateHash,
		chapters: List<WordSyncChapterSummary> = listOf(summary())
	): WordSyncIndex = WordSyncIndex(
		identity = identity,
		generatedAt = null,
		timeScale = WordSyncTimeScale,
		coordinateBasis = WordSyncCoordinateBasis(
			extractor = "bindery-epub-text",
			extractorVersion = "1",
			normalization = "raw-extracted-text-offsets",
			ebookTextHash = hash
		),
		statusEnum = emptyMap(),
		methodEnum = emptyMap(),
		chapters = chapters,
		unplaced = null
	)

	fun summary(
		spineIndex: Int = 3,
		ebookHref: String = ChapterHref,
		ebookStart: Int = 0,
		ebookEnd: Int = 38
	): WordSyncChapterSummary = WordSyncChapterSummary(
		chapterKey = ChapterKey,
		spineIndex = spineIndex,
		ebookHref = ebookHref,
		path = "$ChapterKey.wsyncw",
		href = "/synthetic/index/$ChapterKey",
		opdsHref = null,
		ebookStart = ebookStart,
		ebookEnd = ebookEnd,
		audioRanges = listOf(
			WordSyncAudioRange(
				audioResourceId = "audio",
				audioTrackIndex = 0,
				audioHref = "Audio/chapter.mp3",
				startMs = 900,
				endMs = 1_300
			)
		),
		audioWordCount = 3,
		matchedAudioWordCount = 3,
		reviewAudioWordCount = 0,
		unmatchedAudioWordCount = 0,
		unmatchedEbookWordCount = 1,
		minConfidence = 98,
		meanConfidence = 98
	)

	fun chapter(
		words: List<WordSyncWord> = validWords,
		unmatchedEbook: List<WordSyncUnmatchedEbook> = listOf(
			WordSyncUnmatchedEbook(ebookStart = 26, ebookLen = 3, reason = "not-linked")
		)
	): WordSyncChapter {
		val track = WordSyncTrack(
			audioResourceId = "audio",
			audioTrackIndex = 0,
			audioHref = "Audio/chapter.mp3",
			baseStartMs = 900,
			words = words
		)
		return WordSyncChapter(
			identity = identity,
			chapterKey = ChapterKey,
			ebookHref = ChapterHref,
			spineIndex = 3,
			ebookStart = 0,
			ebookEnd = 38,
			timeScale = WordSyncTimeScale,
			tracks = listOf(track),
			ebookLookup = words.mapIndexed { index, word ->
				WordSyncEbookLookupEntry(
					ebookStart = word.ebookStart,
					trackIndex = 0,
					wordIndex = index
				)
			},
			unmatchedEbook = unmatchedEbook
		)
	}

	val validWords: List<WordSyncWord>
		get() = listOf(
			word(ebookStart = 0, ebookEnd = 3, audioStartMs = 900, audioEndMs = 950),
			word(ebookStart = 13, ebookEnd = 20, audioStartMs = 1_000, audioEndMs = 1_100),
			word(ebookStart = 35, ebookEnd = 38, audioStartMs = 1_200, audioEndMs = 1_300)
		)

	private fun word(
		ebookStart: Int,
		ebookEnd: Int,
		audioStartMs: Long,
		audioEndMs: Long
	): WordSyncWord = WordSyncWord(
		audioResourceId = "audio",
		audioTrackIndex = 0,
		audioHref = "Audio/chapter.mp3",
		audioStartMs = audioStartMs,
		audioEndMs = audioEndMs,
		ebookHref = ChapterHref,
		spineIndex = 3,
		ebookStart = ebookStart,
		ebookEnd = ebookEnd,
		cueId = 1,
		status = 1,
		confidence = 98,
		method = 0,
		flags = 0
	)

	private val identity = BinderyWhispersyncIdentity(
		bookId = 7,
		ebookBookFileId = 11,
		audiobookBookFileId = 13,
		artifactId = 17
	)

	private val defaultContainer = """
		<?xml version="1.0" encoding="UTF-8"?>
		<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
			<rootfile full-path="OPS/package.opf"/>
			<rootfile full-path="OPS/missing.opf"/>
		</container>
	""".trimIndent()

	private val defaultOpf = """
		<?xml version="1.0" encoding="UTF-8"?>
		<package xmlns="http://www.idpf.org/2007/opf">
			<item id="chapter" href="Text/wrong.xhtml" media-type="application/xhtml+xml"/>
			<item id="ignored-case" href="Text/upper.xhtml" media-type="APPLICATION/XHTML+XML"/>
			<item id="chapter" href="Text/../Text/chapter.xhtml" media-type="application/xhtml+xml"/>
			<itemref idref="ignored-case"/>
			<itemref idref="chapter"/>
			<itemref idref="missing"/>
			<itemref idref="chapter"/>
		</package>
	""".trimIndent()
}

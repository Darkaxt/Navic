package paige.navic.ui.screens.bindery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind

class BinderyReaderOpenPolicyTest {
	private val english = edition(id = "/opds/books/7/resources/ebook-1", title = "Unwin Hyman", language = "eng")
	private val englishOther = edition(id = "/opds/books/7/resources/ebook-2", title = "HarperCollins", language = "eng")
	private val spanish = edition(id = "/opds/books/7/resources/ebook-3", title = "Minotauro", language = "spa")
	private val editions = listOf(english, englishOther, spanish)

	@Test
	fun languagesAreDistinctAndSorted() {
		assertEquals(listOf("eng", "spa"), binderyEditionLanguages(editions))
		assertEquals(listOf("eng"), binderyEditionLanguages(listOf(english, englishOther)))
	}

	@Test
	fun switchingLanguagePrefersTheMostRecentlyReadEdition() {
		val progresses = listOf(progress(englishOther.id, 0.4, updatedAt = 20L), progress(english.id, 0.1, updatedAt = 10L))
		assertEquals(englishOther, binderyEditionForLanguage(editions, "eng", spanish, progresses))
		assertEquals(english, binderyEditionForLanguage(editions, "eng", english, progresses))
		assertEquals(spanish, binderyEditionForLanguage(editions, "spa", english, progresses))
		assertEquals(english, binderyEditionForLanguage(editions, "fra", english, progresses))
	}

	@Test
	fun audioMatchesFilterByKnownLanguageOnly() {
		val en = match("Andy Serkis", "eng")
		val es = match("Narrador", "spa")
		assertEquals(listOf("eng", "spa"), binderyMatchLanguages(listOf(en, es)))
		assertEquals(listOf(es), binderyMatchesForLanguage(listOf(en, es), "spa"))
		assertEquals(listOf(en, es), binderyMatchesForLanguage(listOf(en, es), null))
		assertEquals(listOf(en, es), binderyMatchesForLanguage(listOf(en, es), "deu"))
	}

	@Test
	fun suggestsImportingFromAMoreRecentlyReadEdition() {
		val progresses = listOf(progress(english.id, 0.42, updatedAt = 20L))
		val suggestion = binderyProgressImportSuggestion(spanish, editions, progresses)!!
		assertEquals(0.42, suggestion.fraction)
		assertEquals("Unwin Hyman · English", suggestion.sourceTitle)
		assertTrue(suggestion.selectedByDefault)
	}

	@Test
	fun ownMoreRecentProgressSuppressesTheSuggestionAndOlderOwnProgressKeepsItOptIn() {
		val newerOwn = listOf(progress(english.id, 0.42, updatedAt = 20L), progress(spanish.id, 0.1, updatedAt = 30L))
		assertNull(binderyProgressImportSuggestion(spanish, editions, newerOwn))
		val olderOwn = listOf(progress(english.id, 0.42, updatedAt = 40L), progress(spanish.id, 0.1, updatedAt = 30L))
		assertFalse(binderyProgressImportSuggestion(spanish, editions, olderOwn)!!.selectedByDefault)
		val samePlace = listOf(progress(english.id, 0.42, updatedAt = 40L), progress(spanish.id, 0.421, updatedAt = 30L))
		assertNull(binderyProgressImportSuggestion(spanish, editions, samePlace))
		assertNull(binderyProgressImportSuggestion(english, editions, listOf(progress(english.id, 0.42, updatedAt = 40L))))
	}

	@Test
	fun languageChoicesRoundTripPerBook() {
		val json = binderyBookLanguageChoicesJsonWithUpdate("", "7", BinderyBookLanguageChoice(ebook = "spa", audio = "eng"))
		val both = binderyBookLanguageChoicesJsonWithUpdate(json, "8", BinderyBookLanguageChoice(ebook = "eng", audiobook = false))
		assertEquals(BinderyBookLanguageChoice(ebook = "spa", audio = "eng"), binderyBookLanguageChoice(both, "7"))
		assertEquals(BinderyBookLanguageChoice(ebook = "eng", audiobook = false), binderyBookLanguageChoice(both, "8"))
		assertEquals(BinderyBookLanguageChoice(), binderyBookLanguageChoice(both, "9"))
		assertEquals(BinderyBookLanguageChoice(), binderyBookLanguageChoice("not json", "7"))
	}

	private fun edition(id: String, title: String, language: String) =
		BinderyBookVersionRow(id = id, kind = BinderyBookVersionKind.Ebook, title = title, subtitle = null, language = language)

	private fun match(title: String, language: String) =
		BinderyWhispersyncMatch(
			oppositeTitle = title,
			oppositeKind = BinderyBookVersionKind.Audiobook,
			artifactId = title,
			sidecarHref = "/sidecar/$title",
			coveragePercent = null,
			scorePercent = null,
			oppositeLanguage = language
		)

	private fun progress(href: String, fraction: Double, updatedAt: Long) =
		BinderyReadingProgress(
			bookId = "7",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = href,
			progressFraction = fraction,
			updatedAt = updatedAt.toString()
		)
}

package paige.navic.ui.screens.bindery

import kotlinx.coroutines.test.runTest
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyPublication
import kotlin.test.Test
import kotlin.test.assertEquals

// The Whispersync row comes from Bindery's whispersync=ready feed, followed through every page.
class BinderyHubAudiobookPagesTest {
	private fun page(ids: List<String>, next: String?) = BinderyCatalog(
		title = "Audiobooks",
		links = listOfNotNull(next?.let { BinderyLink(href = it, rel = listOf("next")) }),
		publications = ids.map { BinderyPublication(id = it, title = it) }
	)

	@Test
	fun followsEveryNextPage() = runTest {
		val pages = mapOf(
			"/p2" to page(listOf("c", "d"), "/p3"),
			"/p3" to page(listOf("e"), null)
		)
		val merged = page(listOf("a", "b"), "/p2").withFollowingPages { pages[it] }
		assertEquals(listOf("a", "b", "c", "d", "e"), merged.publications.map { it.id })
	}

	@Test
	fun whispersyncFeedIsTheFilteredAudiobookFeedWithAllItsPages() = runTest {
		val row = BinderyHubRow(BinderyHubRowKind.Audiobooks, "/opds/formats/audiobook", "Audiobooks")
		val hub = BinderyHubState(
			root = BinderyCatalog(title = ""),
			rows = listOf(BinderyHubCatalogRow(row, page(listOf("a"), null)))
		)
		val fetched = mutableListOf<String>()
		val loaded = hub.withWhispersyncCatalog(languageFilter = null) { path ->
			fetched += path
			if ("offset=50" !in path) page(listOf("w1"), "/opds/formats/audiobook?offset=50&whispersync=ready")
			else page(listOf("w2"), null)
		}
		assertEquals("/opds/formats/audiobook?whispersync=ready", fetched.first())
		assertEquals(listOf("w1", "w2"), loaded.whispersync?.publications?.map { it.id })
		// An older Bindery (400) leaves the hub as it was: the Audiobooks row stands in.
		assertEquals(hub, hub.withWhispersyncCatalog(languageFilter = null) { null })
	}

	@Test
	fun stopsOnFailureLoopsAndThePageLimit() = runTest {
		val failed = page(listOf("a"), "/p2").withFollowingPages { null }
		assertEquals(listOf("a"), failed.publications.map { it.id })
		val looping = page(listOf("a"), "/p2").withFollowingPages { page(listOf("x"), "/p2") }
		assertEquals(listOf("a", "x"), looping.publications.map { it.id })
		var fetched = 0
		page(listOf("a"), "/p").withFollowingPages(maxPages = 3) { fetched++; page(listOf("x"), "/p$fetched") }
		assertEquals(2, fetched)
	}
}

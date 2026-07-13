package paige.navic.domain.repositories

import paige.navic.data.remote.bindery.*

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.manager.PreferenceManager

class BinderyRepositoryProviderCoverTest {
	@Test
	fun audiobookBayProviderCoverParserUsesProviderPageContentImage() {
		val html = """
			<html>
			  <body>
			    <img src="/images/search.gif">
			    <div class="postContent">
			      <img alt="The Hobbit cover" src="http://image.bayimg.com/cbdfb4170db50aa30c5bb9a3cbe7c4ea6bb6ff0d.jpg">
			    </div>
			    <img src="https://www.gravatar.com/avatar/ad516503a11cd5ca435acc9bb6523536?s=40">
			  </body>
			</html>
		""".trimIndent()

		assertEquals(
			"https://image.bayimg.com/cbdfb4170db50aa30c5bb9a3cbe7c4ea6bb6ff0d.jpg",
			binderyAudioBookBayProviderCoverUrl(
				sourceUrl = "https://audiobookbay.lu/abss/the-hobbit/",
				html = html
			)
		)
	}

	@Test
	fun audiobookBayProviderCoverParserHandlesUnquotedMetadataAttributes() {
		val html = """
			<html>
			  <head>
			    <meta PROPERTY=og:image content=https://image.bayimg.com/unquoted-cover.jpg>
			  </head>
			  <body><div><p>Malformed tail
		""".trimIndent()

		assertEquals(
			"https://image.bayimg.com/unquoted-cover.jpg",
			binderyAudioBookBayProviderCoverUrl(
				sourceUrl = "https://audiobookbay.lu/abss/the-hobbit/",
				html = html
			)
		)
	}

	@Test
	fun audiobookBayProviderCoverParserRejectsInternalAndOffDomainImages() {
		val html = """
			<html>
			  <head><meta property="og:image" content="https://192.168.1.1/private-cover.jpg"></head>
			  <body>
			    <img src="https://user:password@image.bayimg.com/credentialed-cover.jpg">
			    <img src="https://evil.example/plausible-cover.jpg">
			    <img src="https://[fd00::1]/private-cover.jpg">
			  </body>
			</html>
		""".trimIndent()

		assertNull(
			binderyAudioBookBayProviderCoverUrl(
				sourceUrl = "https://audiobookbay.lu/abss/the-hobbit/",
				html = html
			)
		)
	}

	@Test
	fun audiobookBayProviderCoverParserRejectsUnapprovedSourceOrigin() {
		val html = """<img src="https://image.bayimg.com/otherwise-valid.jpg">"""

		assertNull(
			binderyAudioBookBayProviderCoverUrl(
				sourceUrl = "https://evil.example/forged-provider-page",
				html = html
			)
		)
		assertNull(
			binderyAudioBookBayProviderCoverUrl(
				sourceUrl = "http://audiobookbay.lu/abss/the-hobbit/",
				html = html
			)
		)
	}

	@Test
	fun findingProviderCoverFetchesAudiobookBaySourceWithoutBinderyHeadersAndCachesResult() = runBlocking {
		val apiClient = FakeBinderyApiClient(
			externalTextByUrl = mapOf(
				"https://audiobookbay.lu/abss/the-hobbit/" to """
					<html>
					  <body>
					    <img src="/images/search.gif">
					    <img src="https://image.bayimg.com/hobbit.jpg">
					  </body>
					</html>
				""".trimIndent()
			)
		)
		val metadataCache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 5_000L }
		)

		val coverUrl = repository.getFindingProviderCoverUrl(
			BinderyFindingMetadata(
				findingId = "87",
				providerKind = "audiobookbay",
				sourceUrl = "https://audiobookbay.lu/abss/the-hobbit/",
				coverUrl = "https://assets.hardcover.app/edition/book-cover.jpg"
			)
		).getOrThrow()
		val cachedCoverUrl = repository.getFindingProviderCoverUrl(
			BinderyFindingMetadata(
				findingId = "87",
				providerKind = "audiobookbay",
				sourceUrl = "https://audiobookbay.lu/abss/the-hobbit/"
			)
		).getOrThrow()

		assertEquals("https://image.bayimg.com/hobbit.jpg", coverUrl)
		assertEquals("https://image.bayimg.com/hobbit.jpg", cachedCoverUrl)
		assertEquals(listOf("https://audiobookbay.lu/abss/the-hobbit/"), apiClient.externalTextUrls)
		assertEquals(
			listOf(ExternalTextPurpose.AudioBookBayProviderCover),
			apiClient.externalTextPurposes
		)
		val cached = metadataCache.records.values.single()
		assertEquals(BinderyMetadataPayloadType.ProviderCover, cached.payloadType)
		assertEquals("https://audiobookbay.lu/abss/the-hobbit/", cached.path)
		assertEquals(5_000L, cached.updatedAtMillis)
	}

	@Test
	fun findingProviderCoverCachesCorruptAudiobookBaySourceAsMissingCover() = runBlocking {
		val apiClient = FakeBinderyApiClient()
		val metadataCache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(
			apiClient = apiClient,
			metadataCache = metadataCache,
			currentTimeMillis = { 6_000L }
		)
		val finding = BinderyFindingMetadata(
			findingId = "87",
			providerKind = "audiobookbay",
			sourceUrl = "https://audiobookbay.lu/abss/thze-hobbit-j-r-r-tolkien-2/"
		)

		val coverUrl = repository.getFindingProviderCoverUrl(finding).getOrThrow()
		val cachedCoverUrl = repository.getFindingProviderCoverUrl(finding).getOrThrow()

		assertEquals(null, coverUrl)
		assertEquals(null, cachedCoverUrl)
		assertEquals(listOf("https://audiobookbay.lu/abss/thze-hobbit-j-r-r-tolkien-2/"), apiClient.externalTextUrls)
		val cached = metadataCache.records.values.single()
		assertEquals(BinderyMetadataPayloadType.ProviderCover, cached.payloadType)
		assertEquals("https://audiobookbay.lu/abss/thze-hobbit-j-r-r-tolkien-2/", cached.path)
		assertEquals(6_000L, cached.updatedAtMillis)
	}

}

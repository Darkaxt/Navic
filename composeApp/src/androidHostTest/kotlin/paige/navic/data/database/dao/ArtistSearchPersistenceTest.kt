package paige.navic.data.database.dao

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import paige.navic.data.database.CacheDatabase
import paige.navic.data.database.entities.ArtistEntity
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ArtistSearchPersistenceTest {
	private lateinit var database: CacheDatabase
	private lateinit var dao: ArtistDao

	@Before
	fun setUp() {
		database = Room.inMemoryDatabaseBuilder<CacheDatabase>(RuntimeEnvironment.getApplication())
			.setDriver(AndroidSQLiteDriver()).build()
		dao = database.artistDao()
	}

	@After
	fun tearDown() { database.close() }

	@Test
	fun newSearchArtistsRetainAllIncomingFields(): Unit = runBlocking {
		val artists = listOf(-1, 0, 4).map { count ->
			cachedArtist("new-$count", count)
		}

		dao.insertSearchArtists(artists)

		artists.forEach { assertEquals(it, dao.getArtistById(it.artistId)) }
	}

	@Test
	fun positiveSearchCountsRepairOnlyMatchingNonpositiveCounts(): Unit = runBlocking {
		val zero = cachedArtist("zero", 0)
		val negative = cachedArtist("negative", -1)
		val unrelated = cachedArtist("unrelated", 0)
		dao.insertArtists(listOf(zero, negative, unrelated))

		dao.insertSearchArtists(listOf(
			ArtistEntity("zero", "Remote name", albumCount = 7),
			ArtistEntity("negative", "Other remote name", albumCount = 3)
		))

		assertEquals(zero.copy(albumCount = 7), dao.getArtistById("zero"))
		assertEquals(negative.copy(albumCount = 3), dao.getArtistById("negative"))
		assertEquals(unrelated, dao.getArtistById("unrelated"))
	}

	@Test
	fun ambiguousNonpositiveIncomingCountsLeaveExistingRowsUnchanged(): Unit = runBlocking {
		for (cachedCount in listOf(-1, 0, 5)) {
			for (incomingCount in listOf(-1, 0)) {
				val cached = cachedArtist("$cachedCount-$incomingCount", cachedCount)
				dao.insertArtist(cached)

				dao.insertSearchArtists(listOf(
					ArtistEntity(cached.artistId, "Remote name", albumCount = incomingCount)
				))

				assertEquals(cached, dao.getArtistById(cached.artistId))
			}
		}
	}

	@Test
	fun positiveCachedCountIsNotReplacedByAnotherPositiveSearchCount(): Unit = runBlocking {
		val cached = cachedArtist("positive", 5)
		dao.insertArtist(cached)

		for (incomingCount in listOf(2, 9)) {
			dao.insertSearchArtists(listOf(
				ArtistEntity(cached.artistId, "Remote name", albumCount = incomingCount)
			))
			assertEquals(cached, dao.getArtistById(cached.artistId))
		}
	}

	@Test
	fun localEditsMadeWhileSearchIsPendingSurviveCountRepair(): Unit = runBlocking {
		val cached = cachedArtist("pending", 0)
		dao.insertArtist(cached)
		val responseReady = CompletableDeferred<Unit>()
		val applyResponse = CompletableDeferred<Unit>()
		val search = async {
			val incoming = cached.copy(albumCount = 8)
			responseReady.complete(Unit)
			applyResponse.await()
			dao.insertSearchArtists(listOf(incoming))
		}
		try {
			responseReady.await()
			val edited = cached.copy(
				name = "Locally edited name",
				starredAt = null,
				userRating = 1,
				biography = "Locally edited biography",
				similarArtistIds = listOf("new-similar")
			)
			dao.insertArtist(edited)
			applyResponse.complete(Unit)
			search.await()

			assertEquals(edited.copy(albumCount = 8), dao.getArtistById(cached.artistId))
		} finally {
			applyResponse.complete(Unit)
		}
	}

	@Test
	fun emptySearchDoesNotChangeCachedArtists(): Unit = runBlocking {
		val cached = cachedArtist("existing", 0)
		dao.insertArtist(cached)

		dao.insertSearchArtists(emptyList())

		assertEquals(listOf(cached), dao.getAllArtistsList())
	}

	private fun cachedArtist(id: String, count: Int) = ArtistEntity(
		artistId = id,
		name = "Koji Kondo",
		albumCount = count,
		coverArtId = "local-cover",
		artistImageUrl = "https://example.test/artist.jpg",
		starredAt = Instant.fromEpochMilliseconds(1_000),
		userRating = 5,
		sortName = "Kondo, Koji",
		musicBrainzId = "local-mbid",
		lastFmUrl = "https://example.test/artist",
		roles = listOf("composer", "artist"),
		biography = "Cached biography",
		similarArtistIds = listOf("similar-a", "similar-b")
	)
}

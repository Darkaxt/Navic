package paige.navic.domain.repositories

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MusicLayoutDataLoadingSourceTest {
	@Test
	fun genreRoutesDoNotMaterializeTheWholeAlbumSongGraph() {
		val repository = sourceFile("domain/repositories/GenreRepository.kt").readText()
		val listViewModel = sourceFile("ui/screens/genre/viewmodels/GenreListViewModel.kt").readText()

		assertFalse("getAllAlbumsList()" in repository)
		assertContains(repository, "observeGenreByName")
		assertContains(repository, "observeGenreSummaries")
		assertFalse("DomainGenre>" in listViewModel)
		assertContains(listViewModel, "DomainGenreSummary")
	}

	@Test
	fun songAndArtistRoutesUseTargetedMetadataQueries() {
		val songRepository = sourceFile("domain/repositories/SongRepository.kt").readText()
		val artistViewModel = sourceFile("ui/screens/artist/viewmodels/ArtistDetailViewModel.kt").readText()

		assertFalse("albumDao.getAllAlbums()" in songRepository)
		assertContains(songRepository, "observeSongSortMetadata")
		assertFalse("songRepository.getAllSongs()" in artistViewModel)
		assertContains(artistViewModel, "getSongsByArtistCredit")
	}

	@Test
	fun mostPlayedArtworkDoesNotObserveUnfilteredLibraryTables() {
		val source = sourceFile("ui/screens/library/MostPlayedShortcutsViewModel.kt").readText()

		assertFalse("artistDao.getAllArtists()" in source)
		assertFalse("albumDao.observeAlbumArtistArtwork()" in source)
		assertFalse("songDao.observeArtistSongArtwork()" in source)
		assertContains(source, "flatMapLatest")
	}

	@Test
	fun albumSyncCreatesOnlyAFixedWorkerSet() {
		val source = sourceFile("domain/repositories/DbRepository.kt").readText()

		assertFalse("allAlbumSummaries.map { summary ->" in source)
		assertContains(source, "repeat(LIBRARY_SYNC_NETWORK_CONCURRENCY)")
		assertContains(source, "summaryChannel")
	}

	private fun sourceFile(relativePath: String): File = listOf(
		File("src/commonMain/kotlin/paige/navic/$relativePath"),
		File("composeApp/src/commonMain/kotlin/paige/navic/$relativePath")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $relativePath")
}

package paige.navic.domain.repositories

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistSongIntegritySourceTest {
	@Test
	fun songRefreshUsesUpsertInsteadOfReplace() {
		val source = commonMainFile("data/database/dao/SongDao.kt").readText()

		assertContains(source, "import androidx.room3.Upsert")
		assertTrue(source.windowed("@Upsert".length).count { it == "@Upsert" } >= 2)
		assertFalse("@Insert(onConflict = OnConflictStrategy.REPLACE)\n\tsuspend fun insertSong" in source)
	}

	@Test
	fun playlistRefreshUpsertsSoTheCascadeKeepsCachedSongs() {
		val source = commonMainFile("data/database/dao/PlaylistDao.kt").readText()
		val dao = source.substringBefore("suspend fun insertPlaylistSongCrossRefs")

		assertContains(source, "import androidx.room3.Upsert")
		assertFalse("OnConflictStrategy.REPLACE)\n\tsuspend fun insertPlaylist(" in dao)
		assertFalse("OnConflictStrategy.REPLACE)\n\tsuspend fun insertPlaylists(" in dao)
		assertContains(dao, "@Upsert\n\tsuspend fun insertPlaylist(playlist: PlaylistEntity)")
		assertContains(dao, "@Upsert\n\tsuspend fun insertPlaylists(playlists: List<PlaylistEntity>)")
	}

	@Test
	fun oneFailingPlaylistDoesNotAbortTheOtherPlaylistSongRefreshes() {
		val repository = commonMainFile("domain/repositories/PlaylistRepository.kt").readText()
		val db = commonMainFile("domain/repositories/DbRepository.kt").readText()

		assertFalse("syncPlaylistSongs(playlist.playlistId).getOrThrow()" in repository)
		assertFalse("syncPlaylistSongs(playlist.playlistId).getOrThrow()" in db)
		assertContains(repository, ".firstNotNullOfOrNull { result -> result.exceptionOrNull() }")
		assertContains(db, ".firstNotNullOfOrNull { result -> result.exceptionOrNull() }")
	}

	@Test
	fun playlistDaoExposesAllMembershipAndTransactionalReplacement() {
		val source = commonMainFile("data/database/dao/PlaylistDao.kt").readText()

		assertContains(source, "SELECT DISTINCT songId FROM PlaylistSongCrossRef\"")
		assertContains(source, "suspend fun getAllPlaylistSongIds(): List<String>")
		assertContains(source, "suspend fun replacePlaylistSongs")
		assertContains(source, "@Transaction")
	}

	@Test
	fun repositoryReplacesCompleteMembershipOnceIncludingEmptyPlaylists() {
		val source = commonMainFile("domain/repositories/DbRepository.kt").readText()
		val method = source.substringAfter("suspend fun syncPlaylistSongs(")
			.substringBefore("\n\tsuspend fun syncGenres(")

		assertFalse("playlistDao.deletePlaylistSongCrossRefs" in method)
		assertFalse("if (songEntities.isNotEmpty())" in method)
		assertContains(method, "playlistDao.replacePlaylistSongs(playlistId, crossRefs)")
		assertTrue(
			method.indexOf("songDao.insertSongs") < method.indexOf("playlistDao.replacePlaylistSongs")
		)
	}

	@Test
	fun libraryDeletionIncludesCurrentPlaylistMembership() {
		val source = commonMainFile("domain/repositories/DbRepository.kt").readText()

		assertContains(source, "playlistDao.getAllPlaylistSongIds().toSet()")
		assertContains(source, ".withRetainedPlaylistSongs(")
	}

	private fun commonMainFile(relativePath: String): File = listOf(
		File("src/commonMain/kotlin/paige/navic/$relativePath"),
		File("composeApp/src/commonMain/kotlin/paige/navic/$relativePath")
	).firstOrNull(File::isFile) ?: error("Unable to locate $relativePath")
}

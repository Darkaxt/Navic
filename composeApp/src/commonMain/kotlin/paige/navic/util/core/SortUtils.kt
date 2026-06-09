package paige.navic.util.core

import androidx.room3.RoomRawQuery
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.models.QuickPicksDefaultSize
import paige.navic.domain.models.quickPickSongs

private const val AlbumYearDescendingOrder =
	"CASE WHEN year IS NULL THEN 1 ELSE 0 END ASC, year DESC, LOWER(name) ASC"

// TODO: sort with sql instead
fun ImmutableList<DomainSong>.sortedByListType(
	listType: DomainSongListType,
	downloads: List<DownloadEntity>,
	albums: List<DomainAlbum>,
	quickPicksEnabled: Boolean = true,
	quickPicksLimit: Int = QuickPicksDefaultSize,
	quickPicksMinDurationSeconds: Int = 0
): ImmutableList<DomainSong> {
	return when (listType) {
		DomainSongListType.QuickPicks -> quickPickSongs(
			songs = this,
			albums = albums,
			enabled = quickPicksEnabled,
			limit = quickPicksLimit,
			minDurationSeconds = quickPicksMinDurationSeconds
		)
		DomainSongListType.FrequentlyPlayed -> sortedByDescending { it.playCount }
		DomainSongListType.Newest -> sortedByDescending {
			albums
				.firstOrNull { album -> album.id == it.albumId }
				?.createdAt
		}
		DomainSongListType.Starred -> filter { it.starredAt != null }.sortedBy { it.starredAt }
		DomainSongListType.Random -> shuffled()
		DomainSongListType.Downloaded -> filter { song ->
			downloads
				.filter { it.status == DownloadStatus.DOWNLOADED }
				.any { it.songId == song.id }
		}
		DomainSongListType.Rating -> sortedByDescending { it.userRating ?: 0 }
		DomainSongListType.Year -> sortedByDescending { it.year }
	}.toImmutableList()
}

internal data class AlbumSqlQueryParts(
	val where: String?,
	val orderBy: String,
	val args: List<Any> = emptyList()
)

internal fun DomainAlbumListType.toSqlQueryParts(): AlbumSqlQueryParts {
	return when (this) {
		DomainAlbumListType.AlphabeticalByArtist ->
			AlbumSqlQueryParts(null, "LOWER(artistName) ASC")
		DomainAlbumListType.AlphabeticalByName ->
			AlbumSqlQueryParts(null, "LOWER(name) ASC")
		DomainAlbumListType.Year ->
			AlbumSqlQueryParts(null, AlbumYearDescendingOrder)
		DomainAlbumListType.Frequent ->
			AlbumSqlQueryParts("playCount != 0", "playCount DESC")
		DomainAlbumListType.Highest ->
			AlbumSqlQueryParts(null, "userRating DESC")
		DomainAlbumListType.Newest ->
			AlbumSqlQueryParts(null, "createdAt DESC")
		DomainAlbumListType.Random ->
			AlbumSqlQueryParts(null, "RANDOM()")
		DomainAlbumListType.Downloaded,
		DomainAlbumListType.Recent ->
			AlbumSqlQueryParts(null, "lastPlayedAt DESC")
		DomainAlbumListType.Starred ->
			AlbumSqlQueryParts("starredAt IS NOT NULL", "starredAt ASC")
		is DomainAlbumListType.ByGenre ->
			AlbumSqlQueryParts(
				where = "(genre = ? OR genres = ? OR genres LIKE ? OR genres LIKE ? OR genres LIKE ?)",
				orderBy = AlbumYearDescendingOrder,
				args = listOf(genre, genre, "$genre||%", "%||$genre||%", "%||$genre")
			)
		is DomainAlbumListType.ByYear -> when {
			fromYear != null && toYear != null ->
				AlbumSqlQueryParts("COALESCE(year, 0) BETWEEN ? AND ?", "year DESC, LOWER(name) ASC", listOf(fromYear, toYear))
			fromYear != null ->
				AlbumSqlQueryParts("COALESCE(year, 0) >= ?", "year DESC, LOWER(name) ASC", listOf(fromYear))
			toYear != null ->
				AlbumSqlQueryParts("COALESCE(year, 0) <= ?", "year DESC, LOWER(name) ASC", listOf(toYear))
			else ->
				AlbumSqlQueryParts(null, AlbumYearDescendingOrder)
		}
	}
}

fun DomainAlbumListType.toSqlQuery(): RoomRawQuery {
	val (where, orderBy, args) = toSqlQueryParts()

	val whereClause = where?.let { " WHERE $it" } ?: ""
	val sql = "SELECT * FROM AlbumEntity$whereClause ORDER BY $orderBy"

	return RoomRawQuery(sql) { statement ->
		args.forEachIndexed { index, arg ->
			val bindIndex = index + 1
			when (arg) {
				is String -> statement.bindText(bindIndex, arg)
				is Int -> statement.bindInt(bindIndex, arg)
				is Long -> statement.bindLong(bindIndex, arg)
				is Float -> statement.bindFloat(bindIndex, arg)
				is Double -> statement.bindDouble(bindIndex, arg)
				is Boolean -> statement.bindInt(bindIndex, if (arg) 1 else 0)
			}
		}
	}
}

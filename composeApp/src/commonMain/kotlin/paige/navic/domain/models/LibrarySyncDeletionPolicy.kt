package paige.navic.domain.models

data class LibrarySyncDeletionPlan(
	val albumIdsToKeep: Set<String>,
	val songIdsToKeep: Set<String>?
)

fun librarySyncDeletionPlan(
	authoritativeAlbumIds: Set<String>,
	fetchedAlbumIds: Set<String>,
	fetchedSongIds: Set<String>
): LibrarySyncDeletionPlan = LibrarySyncDeletionPlan(
	albumIdsToKeep = authoritativeAlbumIds,
	songIdsToKeep = fetchedSongIds.takeIf {
		fetchedAlbumIds.containsAll(authoritativeAlbumIds)
	}
)

fun LibrarySyncDeletionPlan.withRetainedPlaylistSongs(
	playlistSongIds: Set<String>
): LibrarySyncDeletionPlan = copy(
	songIdsToKeep = songIdsToKeep?.plus(playlistSongIds)
)

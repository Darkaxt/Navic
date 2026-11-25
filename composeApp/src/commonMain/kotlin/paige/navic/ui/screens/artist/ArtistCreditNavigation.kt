package paige.navic.ui.screens.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainContributor
import paige.navic.domain.models.DomainSong
import paige.navic.LocalNavStack
import paige.navic.ui.navigation.Screen

@Composable
fun rememberArtistCreditDestinationResolver(): suspend (
	artistId: String?,
	artistName: String?,
	albumCredit: Boolean
) -> Screen? {
	val artistDao = koinInject<ArtistDao>()
	val preferenceManager = koinInject<PreferenceManager>()
	return remember(artistDao, preferenceManager) {
		{ artistId, artistName, albumCredit ->
			val localArtists = artistDao.getAllArtistsList().map { it.toDomainModel() }
			if (albumCredit) {
				albumArtistCreditRoute(
					artistId = artistId,
					artistName = artistName,
					localArtists = localArtists,
					aurralEnabled = preferenceManager.aurralEnabled
				)
			} else {
				artistCreditRoute(
					artistId = artistId,
					artistName = artistName,
					localArtists = localArtists,
					aurralEnabled = preferenceManager.aurralEnabled
				)
			}
		}
	}
}

/** The song menu's "View artist" actions: one per credited artist, else the display credit. */
class SongArtistNavigation(
	val viewArtist: () -> Unit,
	val viewCreditedArtist: (DomainContributor) -> Unit
)

@Composable
fun rememberSongArtistNavigation(song: DomainSong): SongArtistNavigation {
	val backStack = LocalNavStack.current
	val scope = rememberCoroutineScope()
	val resolveArtistCreditDestination = rememberArtistCreditDestinationResolver()
	val viewArtist = dropUnlessResumed {
		scope.launch {
			resolveArtistCreditDestination(song.artistId, song.artistName, false)
				?.let(backStack::add)
		}
	}
	return SongArtistNavigation(
		viewArtist = viewArtist,
		viewCreditedArtist = { artist -> backStack.add(structuredArtistCreditRoute(artist)) }
	)
}

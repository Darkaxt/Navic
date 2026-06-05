package paige.navic.ui.screens.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.koinInject
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.PreferenceManager
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

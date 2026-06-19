package paige.navic.shared

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.PlatformContext as CoilPlatformContext
import coil3.imageLoader
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.DomainSong
import paige.navic.util.core.Logger
import java.io.File

internal class AndroidMediaItemFactory(
	private val sessionManager: SessionManager,
	private val downloadManager: DownloadManager,
	private val platformContext: CoilPlatformContext,
	private val streamUriForSongId: (String) -> Uri
) {
	fun toMediaItem(song: DomainSong): MediaItem {
		val metadataBuilder = MediaMetadata.Builder()
			.setTitle(song.title)
			.setSubtitle(song.artistName)
			.setArtist(song.artistName)
			.setAlbumTitle(song.albumTitle)
			.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

		val artworkData = song.coverArtId?.let { coverId ->
			val snapshot = platformContext.imageLoader.diskCache?.openSnapshot(coverId) ?: return@let null
			try {
				snapshot.use { it.data.toFile().readBytes() }
			} catch (error: Exception) {
				Logger.w("MediaPlayer", "Could not read cached artwork data", error)
				null
			}
		}

		if (artworkData != null) {
			metadataBuilder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
		} else {
			metadataBuilder.setArtworkUri(
				song.coverArtId?.let { sessionManager.getCoverArtUrl(it).toUri() }
			)
		}

		val builder = MediaItem.Builder()
			.setUri(song.mediaUri())
			.setMediaId(song.id)
			.setMediaMetadata(metadataBuilder.build())

		if (song.id.startsWith("radio_")) {
			builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
		}

		return builder.build()
	}

	private fun DomainSong.mediaUri(): Uri =
		when {
			id.startsWith(AurralFlowSongIdPrefix) && !filePath.isNullOrEmpty() -> {
				filePath.toUri()
			}

			id.startsWith("radio_") && !filePath.isNullOrEmpty() -> {
				filePath.toUri()
			}

			else -> {
				val localPath = downloadManager.getDownloadedFilePath(id)
				if (localPath != null) {
					File(localPath).toUri()
				} else {
					streamUriForSongId(id)
				}
			}
		}
}

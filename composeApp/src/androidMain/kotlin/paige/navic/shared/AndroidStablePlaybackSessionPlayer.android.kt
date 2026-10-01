package paige.navic.shared

import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@androidx.annotation.OptIn(UnstableApi::class)
internal fun stablePlaybackSessionPlayer(player: ExoPlayer): Player =
    object : ForwardingPlayer(player) {
        override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
            replaceMediaItems(index, index + 1, listOf(mediaItem))
        }

        override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
            val sameIdentities = fromIndex >= 0 && toIndex <= player.mediaItemCount &&
                toIndex - fromIndex == mediaItems.size && mediaItems.indices.all {
                    player.getMediaItemAt(fromIndex + it).mediaId == mediaItems[it].mediaId
                }
            // Media3 reinserts sources whose URI changed, otherwise randomizing their shuffle positions.
            val shuffleOrder = if (sameIdentities) player.shuffleOrder else null
            player.replaceMediaItems(fromIndex, toIndex, mediaItems)
            if (shuffleOrder != null && player.shuffleOrder !== shuffleOrder) {
                player.setShuffleOrder(shuffleOrder)
            }
        }
    }

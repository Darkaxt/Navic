package paige.navic.shared

import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@androidx.annotation.OptIn(UnstableApi::class)
internal fun stablePlaybackSessionPlayer(
    player: ExoPlayer,
    allowsResumptionMutation: () -> Boolean = { true },
    onPlaylistInstalled: (List<MediaItem>) -> Unit = {},
    allowsPlaybackStart: () -> Boolean = { true }
): Player =
    object : ForwardingPlayer(player) {
        override fun play() {
            if (allowsResumptionMutation()) {
                if (allowsPlaybackStart()) super.play() else super.pause()
            }
        }
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (!playWhenReady || allowsResumptionMutation()) {
                super.setPlayWhenReady(playWhenReady && allowsPlaybackStart())
            }
        }
        override fun prepare() { if (allowsResumptionMutation()) super.prepare() }
        override fun seekToDefaultPosition() { if (allowsResumptionMutation()) super.seekToDefaultPosition() }
        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            if (allowsResumptionMutation()) super.seekToDefaultPosition(mediaItemIndex)
        }
        override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            if (!allowsResumptionMutation()) return
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
            onPlaylistInstalled(mediaItems)
        }

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

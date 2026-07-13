package paige.navic.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import org.koin.compose.koinInject
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.rememberPlaybackArtworkUiState
import paige.navic.ui.components.common.rememberResolvedArtworkColorScheme
import paige.navic.ui.components.sheets.ModalBottomSheet
import paige.navic.ui.navigation.NowPlayingSceneStrategy.Companion.bottomSheet
import paige.navic.ui.theme.NavicTheme

/** An [OverlayScene] that renders an [entry] within a [ModalBottomSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
internal class NowPlayingScene<T : Any>(
	override val key: T,
	override val previousEntries: List<NavEntry<T>>,
	override val overlaidEntries: List<NavEntry<T>>,
	private val entry: NavEntry<T>,
	private val modalBottomSheetProperties: ModalBottomSheetProperties,
	private val sheetMaxWidth: Dp,
	private val onBack: () -> Unit,
	private val isTransparent: Boolean
) : OverlayScene<T> {

	override val entries: List<NavEntry<T>> = listOf(entry)

	override val content: @Composable (() -> Unit) = {
		NavicTheme(colorSchemeForCurrentSong()) {
			val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

			ModalBottomSheet(
				containerColor = if (isTransparent) {
					Color.Transparent
				} else {
					MaterialTheme.colorScheme.surface
				},
				onDismissRequest = onBack,
				properties = modalBottomSheetProperties,
				sheetState = sheetState,
				sheetMaxWidth = sheetMaxWidth,
				contentWindowInsets = { WindowInsets() },
				dragHandle = null,
				shape = if (sheetState.targetValue == SheetValue.Expanded)
					RectangleShape
				else BottomSheetDefaults.ExpandedShape
			) {
				Box(Modifier.fillMaxSize()) {
					entry.Content()
				}
			}
		}
	}
}

/**
 * A [SceneStrategy] that displays entries that have added [bottomSheet] to their [NavEntry.metadata]
 * within a [ModalBottomSheet] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
@OptIn(ExperimentalMaterial3Api::class)
class NowPlayingSceneStrategy<T : Any> : SceneStrategy<T> {

	override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
		val lastEntry = entries.lastOrNull() ?: return null
		val metadata = lastEntry.metadata[MetadataKey] ?: return null
		return NowPlayingScene(
				key = lastEntry.sceneKey(),
				previousEntries = entries.dropLast(1),
				overlaidEntries = entries.dropLast(1),
				entry = lastEntry,
				modalBottomSheetProperties = metadata.properties,
				sheetMaxWidth = metadata.maxWidth,
				onBack = onBack,
				isTransparent = metadata.isTransparent
			)
	}

	companion object {
		/**
		 * Function to be called on the [NavEntry.metadata] to mark this entry as something that
		 * should be displayed within a [ModalBottomSheet].
		 *
		 * @param modalBottomSheetProperties properties that should be passed to the containing
		 * [ModalBottomSheet].
		 */
		@OptIn(ExperimentalMaterial3Api::class)
		fun bottomSheet(
			modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
			maxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
			isTransparent: Boolean = false
		) = metadata {
			put(
				MetadataKey,
				NowPlayingBottomSheetMetadata(
					properties = modalBottomSheetProperties,
					maxWidth = maxWidth,
					isTransparent = isTransparent
				)
			)
		}

		object MetadataKey : NavMetadataKey<NowPlayingBottomSheetMetadata>
	}
}

@OptIn(ExperimentalMaterial3Api::class)
data class NowPlayingBottomSheetMetadata(
	val properties: ModalBottomSheetProperties,
	val maxWidth: Dp,
	val isTransparent: Boolean
)

@Composable
private fun colorSchemeForCurrentSong(): ColorScheme? {
	val player = koinInject<MediaPlayerViewModel>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val playerState by player.uiState.collectAsState()
	val musicBrainzArtworkBySongId by musicBrainzArtworkRepository.artworkBySongId.collectAsState()
	val serverCoverLoadFailedSongIds by musicBrainzArtworkRepository.serverCoverLoadFailedSongIds.collectAsState()
	val song = playerState.currentSong
	val serverCoverLoadFailed = song?.id?.let { it in serverCoverLoadFailedSongIds } == true
	val musicBrainzArtwork = song?.id?.let(musicBrainzArtworkBySongId::get)
	val playbackArtwork = rememberPlaybackArtworkUiState(
		song = song,
		musicBrainzArtworkUrl = musicBrainzArtwork?.imageUrl,
		musicBrainzArtworkCacheKey = musicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
		serverCoverLoadFailed = serverCoverLoadFailed
	)
	return rememberResolvedArtworkColorScheme(playbackArtwork = playbackArtwork)
}

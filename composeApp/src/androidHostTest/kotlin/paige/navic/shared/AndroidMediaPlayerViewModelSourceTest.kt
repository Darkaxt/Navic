package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AndroidMediaPlayerViewModelSourceTest {
	@Test
	fun mediaItemMetadataMappingLivesOutsideAndroidMediaPlayerViewModel() {
		val viewModel = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt")
		val mediaItemFactory = androidSharedSourceFile("AndroidMediaItemFactory.android.kt")
		val viewModelText = viewModel.readText()
		val factoryText = mediaItemFactory.readText()

		assertTrue(
			viewModel.readLines().size < 1_500,
			"AndroidMediaPlayerViewModel should not own Media3 metadata construction and stream/download URI selection."
		)
		assertContains(viewModelText, "private val mediaItemFactory = AndroidMediaItemFactory(")
		assertContains(viewModelText, "private fun DomainSong.toMediaItem(): MediaItem =")
		assertContains(factoryText, "internal class AndroidMediaItemFactory")
		assertContains(factoryText, "fun toMediaItem(song: DomainSong): MediaItem")
		assertContains(factoryText, "MediaMetadata.Builder()")
		assertContains(factoryText, "downloadManager.getDownloadedFilePath(id)")
	}

	@Test
	fun nowPlayingBroadcastStateLivesOutsideAndroidMediaPlayerViewModel() {
		val viewModel = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt")
		val broadcaster = androidSharedSourceFile("AndroidNowPlayingBroadcaster.android.kt")
		val viewModelText = viewModel.readText()
		val broadcasterText = broadcaster.readText()

		assertTrue(
			viewModel.readLines().size < 1_450,
			"AndroidMediaPlayerViewModel should not own widget broadcast dedupe state or artwork URL assembly."
		)
		assertContains(viewModelText, "private val nowPlayingBroadcaster = AndroidNowPlayingBroadcaster(")
		assertContains(viewModelText, "nowPlayingBroadcaster.send(")
		assertContains(broadcasterText, "internal class AndroidNowPlayingBroadcaster")
		assertContains(broadcasterText, "fun send(")
		assertContains(broadcasterText, "shouldSendNowPlayingWidgetUpdate(")
		assertContains(broadcasterText, "activeArtworkUrl(")
	}

	@Test
	fun playbackFadeJobStateLivesOutsideAndroidMediaPlayerViewModel() {
		val viewModel = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt")
		val fader = androidSharedSourceFile("AndroidPlaybackVolumeFader.android.kt")
		val viewModelText = viewModel.readText()
		val faderText = fader.readText()

		assertTrue(
			viewModel.readLines().size < 1_410,
			"AndroidMediaPlayerViewModel should not own playback fade coroutine state."
		)
		assertContains(viewModelText, "private val playbackVolumeFader = AndroidPlaybackVolumeFader(")
		assertContains(viewModelText, "playbackVolumeFader.start(")
		assertContains(viewModelText, "playbackVolumeFader.cancel(")
		assertContains(faderText, "internal class AndroidPlaybackVolumeFader")
		assertContains(faderText, "private var fadeJob: Job? = null")
		assertContains(faderText, "restoreVolumeOnCancel")
	}

	@Test
	fun playbackOriginCheckpointStateLivesOutsideAndroidMediaPlayerViewModel() {
		val viewModel = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt")
		val recorder = androidSharedSourceFile("AndroidPlaybackOriginRecorder.android.kt")
		val viewModelText = viewModel.readText()
		val recorderText = recorder.readText()

		assertTrue(
			viewModel.readLines().size < 1_370,
			"AndroidMediaPlayerViewModel should not own playback-origin tracker/checkpoint state."
		)
		assertContains(viewModelText, "private val playbackOriginRecorder = AndroidPlaybackOriginRecorder(")
		assertContains(viewModelText, "playbackOriginRecorder.onPlaybackState(")
		assertContains(viewModelText, "playbackOriginRecorder.checkpointIfNeeded(")
		assertContains(recorderText, "private val tracker = PlaybackOriginTracker()")
		assertContains(recorderText, "private const val PlaybackOriginCheckpointIntervalMs")
		assertContains(recorderText, "repository.credit(")
	}
}

private fun androidSharedSourceFile(fileName: String): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/shared/$fileName"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android shared source file $fileName")

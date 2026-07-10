package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
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
		assertContains(broadcasterText, "playbackArtworkForSong(song)")
		assertFalse(
			broadcasterText.contains("activeArtworkUrl(") ||
				broadcasterText.contains("externalFallbackArtworkUrl("),
			"Now-playing broadcasts must use the shared Aurral-first playback artwork resolver, not server-first fallback helpers."
		)
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
			viewModel.readLines().size < 1_360,
			"AndroidMediaPlayerViewModel should not own playback-origin tracker/checkpoint state."
		)
		assertContains(viewModelText, "private val playbackOriginRecorder = AndroidPlaybackOriginRecorder(")
		assertContains(viewModelText, "playbackOriginRecorder.onPlaybackState(")
		assertContains(viewModelText, "playbackOriginRecorder.checkpointIfNeeded(")
		assertContains(recorderText, "private val tracker = PlaybackOriginTracker()")
		assertContains(recorderText, "private const val PlaybackOriginCheckpointIntervalMs")
		assertContains(recorderText, "repository.credit(")
	}

	@Test
	fun radioMediaItemConstructionLivesOutsideAndroidMediaPlayerViewModel() {
		val viewModel = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt")
		val radioFactory = androidSharedSourceFile("AndroidRadioMediaItemFactory.android.kt")
		val viewModelText = viewModel.readText()
		val radioFactoryText = radioFactory.readText()

		assertTrue(
			viewModel.readLines().size < 1_360,
			"AndroidMediaPlayerViewModel should not own radio dummy-song and Media3 item construction."
		)
		assertContains(viewModelText, "private val radioMediaItemFactory = AndroidRadioMediaItemFactory()")
		assertContains(viewModelText, "private val playbackArtworkResolver = AndroidPlaybackArtworkResolver(")
		assertContains(viewModelText, "val radioItem = radioMediaItemFactory.create(radio)")
		assertContains(radioFactoryText, "internal class AndroidRadioMediaItemFactory")
		assertContains(radioFactoryText, "data class AndroidRadioMediaItem")
		assertContains(radioFactoryText, "DomainExplicitStatus.Unknown")
		assertContains(radioFactoryText, "MediaMetadata.Builder()")
	}

	@Test
	fun playbackAssetPrefetchStateLivesOutsideAndroidMediaPlayerViewModel() {
		val viewModel = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt")
		val prefetcher = androidSharedSourceFile("AndroidPlaybackAssetPrefetcher.android.kt")
		val viewModelText = viewModel.readText()
		val prefetcherText = prefetcher.readText()

		assertFalse(
			viewModelText.contains("private var lastCurrentArtworkPrefetchSongId") ||
				viewModelText.contains("private var lastUpcomingPrefetchSignature"),
			"AndroidMediaPlayerViewModel should not own playback asset prefetch dedupe state."
		)
		assertContains(viewModelText, "private val playbackAssetPrefetcher = AndroidPlaybackAssetPrefetcher(")
		assertContains(viewModelText, "playbackAssetPrefetcher.prefetchCurrentSongArtwork(")
		assertContains(viewModelText, "playbackAssetPrefetcher.prefetchUpcomingPlaybackAssets(")
		assertContains(prefetcherText, "internal class AndroidPlaybackAssetPrefetcher")
		assertContains(prefetcherText, "private var lastCurrentArtworkPrefetchSongId: String? = null")
		assertContains(prefetcherText, "private var lastUpcomingPrefetchSignature: String? = null")
		assertContains(prefetcherText, "musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(song)")
		assertFalse(prefetcherText.contains("DownloadManager"))
		assertFalse(prefetcherText.contains("prefetchPlaybackSongs"))
	}

	@Test
	fun queueAutoFillJobStateLivesOutsideAndroidMediaPlayerViewModel() {
		val viewModel = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt")
		val queueAutoFiller = androidSharedSourceFile("AndroidQueueAutoFiller.android.kt")
		val viewModelText = viewModel.readText()
		val queueAutoFillerText = queueAutoFiller.readText()

		assertFalse(
			viewModelText.contains("private var autoFillQueueJob"),
			"AndroidMediaPlayerViewModel should not own queue auto-fill job state."
		)
		assertContains(viewModelText, "private val queueAutoFiller = AndroidQueueAutoFiller(")
		assertContains(viewModelText, "queueAutoFiller.maybeAutoFillQueue()")
		assertContains(viewModelText, "queueAutoFiller.cancel()")
		assertContains(queueAutoFillerText, "internal class AndroidQueueAutoFiller")
		assertContains(queueAutoFillerText, "private var autoFillQueueJob: Job? = null")
		assertContains(queueAutoFillerText, "fun maybeAutoFillQueue()")
		assertContains(queueAutoFillerText, "fun cancel()")
		assertContains(queueAutoFillerText, "shouldAutoFillQueue(")
	}

	@Test
	fun playbackRecoveryRefreshesInPlaceAndNeverMutatesQueueOrder() {
		val viewModelText = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt").readText()
		val recoveryText = androidSharedSourceFile("AndroidStablePlaybackRecoveryCoordinator.android.kt").readText()

		assertContains(viewModelText, "private val playbackRecovery = AndroidStablePlaybackRecoveryCoordinator(")
		assertContains(viewModelText, "playbackRecovery.handlePlayerError")
		assertContains(recoveryText, "refreshCurrentRemoteMediaItem")
		assertContains(recoveryText, "playbackFailureTargetIndex(")
		assertContains(recoveryText, "skipMediaOnError()")
		assertFalse(viewModelText.contains("AndroidPlaybackDownloadRecoveryCoordinator"))
		assertFalse(viewModelText.contains("prefetchPlaybackSongs"))
		assertFalse(viewModelText.contains("promoteReadyDeferredDownloads"))
		assertFalse(viewModelText.contains("moveUiQueueItem"))
		assertFalse(recoveryText.contains("prefetchPlaybackSongs"))
		assertFalse(recoveryText.contains("moveMediaItem"))
	}

	@Test
	fun bufferingDoesNotOverwriteUserPlaybackIntent() {
		val viewModelText = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt").readText()

		assertContains(viewModelText, "isPaused = !playWhenReady")
		assertContains(viewModelText, "isPaused = !controller.playWhenReady")
		assertFalse(viewModelText.contains("it.copy(isPaused = !isPlaying)"))
		assertFalse(viewModelText.contains("isPaused = !controller.isPlaying"))
	}
}

private fun androidSharedSourceFile(fileName: String): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/shared/$fileName"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android shared source file $fileName")

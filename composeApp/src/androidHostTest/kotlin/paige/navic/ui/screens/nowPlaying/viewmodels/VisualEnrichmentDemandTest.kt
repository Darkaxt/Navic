package paige.navic.ui.screens.nowPlaying.viewmodels

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.shared.PlaybackStartPhase
import paige.navic.shared.PlaybackStartStatus
import paige.navic.ui.core.PlayerUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VisualEnrichmentDemandTest {
	@Test
	fun unlockingRequestsTheSamePausedSongWithoutAPlaybackTick() = runTest {
		val playback = MutableStateFlow(PlayerUiState(currentSong = song("a"), isPaused = true))
		val visible = MutableStateFlow(false)
		val startup = MutableStateFlow<PlaybackStartStatus?>(null)
		val observed = mutableListOf<VisualEnrichmentDemand>()
		backgroundScope.launch { visualEnrichmentDemand(playback, visible, startup).collect(observed::add) }
		runCurrent()
		assertFalse(observed.last().canRequest)
		visible.value = true
		runCurrent()
		assertTrue(observed.last().canRequest)
		assertEquals("a", observed.last().song?.id)
		visible.value = false
		runCurrent()
		visible.value = true
		runCurrent()
		assertEquals(listOf("a", "a"), observed.filter { it.canRequest }.map { it.song?.id })
	}

	@Test
	fun hiddenSongChangesInvalidateDisplayButUnlockOnlyRequestsTheLatestSong() = runTest {
		val playback = MutableStateFlow(PlayerUiState(currentSong = song("a")))
		val visible = MutableStateFlow(false)
		val startup = MutableStateFlow<PlaybackStartStatus?>(null)
		val observed = mutableListOf<VisualEnrichmentDemand>()
		backgroundScope.launch { visualEnrichmentDemand(playback, visible, startup).collect(observed::add) }
		runCurrent()
		playback.value = playback.value.copy(currentSong = song("b"))
		runCurrent()
		playback.value = playback.value.copy(currentSong = song("c"))
		runCurrent()
		assertEquals("c", observed.last().song?.id)
		assertTrue(observed.none { it.canRequest })
		visible.value = true
		runCurrent()
		assertEquals(listOf("c"), observed.filter { it.canRequest }.map { it.song?.id })
	}

	@Test
	fun hiddenProgressDoesNotCauseWorkAndStartupCompletionResumesVisibleDemand() = runTest {
		val playback = MutableStateFlow(PlayerUiState(currentSong = song("a")))
		val visible = MutableStateFlow(false)
		val startup = MutableStateFlow<PlaybackStartStatus?>(PlaybackStartStatus(PlaybackStartPhase.Preparing))
		val observed = mutableListOf<VisualEnrichmentDemand>()
		backgroundScope.launch { visualEnrichmentDemand(playback, visible, startup).collect(observed::add) }
		runCurrent()
		playback.value = playback.value.copy(progress = 0.5f)
		runCurrent()
		assertEquals(1, observed.size)
		visible.value = true
		runCurrent()
		assertFalse(observed.last().canRequest)
		startup.value = null
		runCurrent()
		assertTrue(observed.last().canRequest)
		assertEquals(0.5f, observed.last().progress)
	}

	@Test
	fun startupCompletionWhileLockedDoesNotConsumeTheUnlockRequest() = runTest {
		val playback = MutableStateFlow(PlayerUiState(currentSong = song("a")))
		val visible = MutableStateFlow(false)
		val startup = MutableStateFlow<PlaybackStartStatus?>(PlaybackStartStatus(PlaybackStartPhase.Starting))
		val observed = mutableListOf<VisualEnrichmentDemand>()
		backgroundScope.launch { visualEnrichmentDemand(playback, visible, startup).collect(observed::add) }
		runCurrent()
		startup.value = null
		runCurrent()
		assertTrue(observed.none { it.canRequest })
		visible.value = true
		runCurrent()
		assertTrue(observed.last().canRequest)
	}

	private fun song(id: String) = DomainSong(
		id = id, title = id, artistName = "Artist", artistId = null,
		albumTitle = null, albumId = null, parentId = null, comment = null,
		trackNumber = null, discNumber = null, isrc = emptyList(), year = null,
		genre = null, genres = emptyList(), moods = emptyList(), duration = 180.seconds,
		bpm = null, contributors = emptyList(), userRating = null, averageRating = null,
		bitRate = null, bitDepth = null, sampleRate = null, audioChannelCount = null,
		replayGain = null, fileSize = 0, fileExtension = "mp3", mimeType = "audio/mpeg",
		filePath = null, starredAt = null, coverArtId = null, musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown
	)
}

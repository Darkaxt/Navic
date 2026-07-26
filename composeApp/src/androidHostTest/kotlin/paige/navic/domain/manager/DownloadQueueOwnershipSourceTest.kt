package paige.navic.domain.manager

import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DownloadQueueOwnershipSourceTest {
	@Test
	fun roomOwnsQueuedSongsAndChannelIsOnlyABoundedWakeupSignal() {
		val source = sourceFile().readText()

		assertFalse("Channel<DomainSong>" in source)
		assertFalse("Channel.UNLIMITED" in source)
		assertFalse("getSongsByIds(queuedSongIds)" in source)
		assertContains(source, "Channel<Unit>(capacity = 1)")
		assertContains(source, "claimNextQueuedDownload")
	}

	@Test
	fun largeQueueProducesAtMostOneInMemoryWakeup() {
		val wakeups = Channel<Unit>(capacity = 1)

		repeat(100_000) { wakeups.trySend(Unit) }

		assertEquals(Unit, wakeups.tryReceive().getOrNull())
		assertFalse(wakeups.tryReceive().isSuccess)
	}

	@Test
	fun playbackRecoveryDownloadRequestReturnsAConclusiveSchedulingResult() {
		val source = sourceFile().readText()

		assertContains(source, "suspend fun requestPlaybackRecoveryDownload(")
		assertContains(source, "sessionLifetime.currentScope() == null")
		assertContains(source, "songDao.getSongById(song.id)")
		assertContains(source, "PlaybackDownloadRequestResult.InactiveSession")
		assertContains(source, "PlaybackDownloadRequestResult.MissingCatalogEntry")
		assertContains(source, "PlaybackDownloadRequestResult.AlreadyDownloaded")
		assertContains(source, "PlaybackDownloadRequestResult.AlreadyActive")
		assertContains(source, "PlaybackDownloadRequestResult.Enqueued")
	}

	private fun sourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/domain/manager/DownloadManager.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/DownloadManager.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate DownloadManager.kt")
}

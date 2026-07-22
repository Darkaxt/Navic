package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class OfflineAwareDownloadQueueSourceTest {
	@Test
	fun workersWaitForEffectiveOnlineStateAndPreserveQueuedIntent() {
		val manager = sourceFile("domain/manager/DownloadManager.kt").readText()
		val dao = sourceFile("data/database/dao/DownloadDao.kt").readText()

		assertContains(manager, "private val connectivityManager: ConnectivityManager")
		assertContains(manager, "private val navidromeAvailabilityManager: NavidromeAvailabilityManager")
		assertContains(manager, "connectivityManager.isOnline.first { it }")
		assertContains(manager, "suspendActiveDownloadsForOffline")
		assertContains(manager, "NavidromeOutageTrigger.Download")
		assertContains(manager, "downloadDao.requeueIfCurrent")
		assertContains(dao, "suspend fun requeueIfCurrent")
	}

	@Test
	fun serviceRecoveryReplacesTheFixedRetryLoop() {
		val manager = sourceFile("domain/manager/DownloadManager.kt").readText()

		assertFalse("HOSTED_DOWNLOAD_RETRY_DELAY_MS" in manager)
		assertFalse("Download retry queued" in manager)
	}

	private fun sourceFile(relativePath: String): File = listOf(
		File("src/commonMain/kotlin/paige/navic/$relativePath"),
		File("composeApp/src/commonMain/kotlin/paige/navic/$relativePath")
	).firstOrNull(File::isFile) ?: error("Unable to locate $relativePath")
}

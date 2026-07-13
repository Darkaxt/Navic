package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SyncManagerActorSourceTest {
	@Test
	fun allTriggersUseOneBoundedActorWithoutCheckThenAct() {
		val source = sourceFile().readText()

		assertFalse("syncMutex.isLocked" in source)
		assertFalse("Channel.UNLIMITED" in source)
		assertContains(source, "Channel<Unit>(capacity = 1)")
		assertContains(source, "requestSync(")
		assertContains(source, "processDueQueueActions")
		assertContains(source, "scheduleNextRetryWakeup")
		assertContains(source, "Sync cycle failed; actor remains available.")
	}

	private fun sourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/domain/manager/SyncManager.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/SyncManager.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate SyncManager.kt")
}

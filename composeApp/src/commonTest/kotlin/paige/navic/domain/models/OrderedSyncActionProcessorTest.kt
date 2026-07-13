package paige.navic.domain.models

import kotlinx.coroutines.runBlocking
import paige.navic.data.database.entities.SyncActionEntity
import paige.navic.data.database.entities.SyncActionType
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderedSyncActionProcessorTest {
	@Test
	fun failedActionDoesNotBlockLaterReadyActions() = runBlocking {
		val actions = listOf(
			SyncActionEntity(id = 1, actionType = SyncActionType.STAR, itemId = "poison"),
			SyncActionEntity(id = 2, actionType = SyncActionType.STAR, itemId = "ready")
		)
		val attempted = mutableListOf<String>()
		val succeeded = mutableListOf<Int>()
		val failed = mutableListOf<Int>()

		processOrderedSyncActions(
			actions = actions,
			execute = { action ->
				attempted += action.itemId
				if (action.itemId == "poison") error("terminal")
			},
			onSuccess = { succeeded += it.id },
			onFailure = { action, _ -> failed += action.id }
		)

		assertEquals(listOf("poison", "ready"), attempted)
		assertEquals(listOf(1), failed)
		assertEquals(listOf(2), succeeded)
	}
}

package paige.navic.domain.repositories

import paige.navic.data.remote.aurral.*

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AurralRepositorySourceTest {
	@Test
	fun repositoryDelegatesConfirmationAuthFlowMutationAndSupportHelpers() {
		val repository = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt")
		val confirmationManager =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralConfirmationQueueManager.kt")
		val auth =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepositoryAuth.kt")
		val flowActions =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralFlowRepositoryActions.kt")
		val mutationActions =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralMutationRepositoryActions.kt")
		val localState =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepositoryLocalState.kt")
		val support = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepositorySupport.kt")
		val repositoryText = repository.readText()
		val confirmationText = confirmationManager.readText()
		val authText = auth.readText()
		val flowText = flowActions.readText()
		val mutationText = mutationActions.readText()
		val localStateText = localState.readText()
		val supportText = support.readText()

		assertTrue(
			repository.readLines().size < 1_400,
			"AurralRepository should delegate confirmation, auth/session, Flow, mutations, and cache-key helpers."
		)
		assertContains(repositoryText, "private val localState = AurralRepositoryLocalState(")
		assertContains(repositoryText, "private val auth = AurralRepositoryAuth(")
		assertContains(repositoryText, "private val flowActions = AurralFlowRepositoryActions(")
		assertContains(repositoryText, "private val mutations = AurralMutationRepositoryActions(")
		assertFalse("private var authenticatedHeadersCache" in repositoryText)
		assertFalse("apiClient.fetchFlowJobs(" in repositoryText)
		assertFalse("apiClient.requestAlbum(" in repositoryText)
		assertFalse("apiClient.monitorArtist(" in repositoryText)
		assertFalse("apiClient.cancelAcquisitionRequest(" in repositoryText)
		assertContains(confirmationText, "internal class AurralConfirmationQueueManager")
		assertContains(confirmationText, "private val confirmationJobs = mutableMapOf<String, Job>()")
		assertContains(confirmationText, "fun startArtistMonitoringConfirmationWorker(")
		assertContains(authText, "internal class AurralRepositoryAuth")
		assertContains(authText, "private var authenticatedHeadersCache")
		assertContains(authText, "fun bearerTokenFromHeaders(")
		assertContains(flowText, "internal class AurralFlowRepositoryActions")
		assertContains(flowText, "fun getFlowPlayableSongs(")
		assertContains(flowText, "apiClient.fetchFlowJobs(")
		assertContains(mutationText, "internal class AurralMutationRepositoryActions")
		assertContains(mutationText, "apiClient.requestAlbum(")
		assertContains(mutationText, "apiClient.monitorArtist(")
		assertContains(mutationText, "apiClient.cancelAcquisitionRequest(")
		assertContains(mutationText, "confirmationQueueManager.upsert(")
		assertContains(localStateText, "internal class AurralRepositoryLocalState")
		assertContains(localStateText, "private var libraryArtistsCache: AurralLibraryArtistsCacheEntry?")
		assertContains(supportText, "internal data class AurralLibraryArtistsCacheEntry")
		assertContains(supportText, "internal fun aurralLibraryArtistsCacheKey(")
		assertContains(supportText, "internal fun String?.normalizedAurralSearchName(): String?")
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull { it.isFile }
			?: error("Could not locate $path")
}

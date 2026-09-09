package paige.navic.ui.screens.search

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MusicSearchWiringTest {
	@Test
	fun viewModelUsesIndependentLocalAndGatedNetworkSources() {
		val source = commonSource("ui/screens/search/viewmodels/SearchViewModel.kt")
		assertContains(source, "musicSearchStates(")
		assertContains(source, "if (result.query == searchQuery.text.toString().trim())")
		assertContains(source, "put(MusicSearchSource.Library) { repository.searchLocal(query) }")
		assertContains(source, "if (isOnline.value) put(MusicSearchSource.Navidrome, eligibleMusicSearchRequest")
		assertContains(source, "if (shouldSearchAurral())")
		assertContains(source, "aurralRepository.searchArtists(query).getOrThrow()")
		assertContains(source, "aurralRepository.searchAlbums(query).getOrThrow()")
		assertFalse(source.contains(".await()"))
		assertFalse(source.contains(".debounce("))
		assertFalse(commonSource("domain/repositories/SearchRepository.kt")
			.substringAfter("suspend fun searchLocal").contains("sessionManager.withApi"))
	}

	@Test
	fun partialResultsUseCategoryStateWithoutReanimatingEveryCompletion() {
		val source = commonSource("ui/screens/search/SearchScreen.kt")
		assertContains(source, "state.contentState(selectedCategory)")
		assertContains(source, "contentKey = { it::class }")
		assertContains(source, "state.isLoading(selectedCategory)")
		assertContains(source, "state.failure(selectedCategory)")
		assertContains(source, "onRetry = viewModel::retrySearch")
		assertContains(source, "height(4.dp)")
		assertContains(source, "showUnknownAlbumCount = false")
		assertContains(source, "selected = artist.id == artistListSelection?.id")
		assertContains(source, "selected = album.id == albumListSelection?.id")
		assertContains(commonSource("domain/repositories/SearchRepository.kt"), "artistDao.insertSearchArtists(")
	}

	@Test
	fun artistMonitoringConsumesConfirmationWithoutWaitingForEnrichment() {
		val source = commonSource("ui/screens/artist/viewmodels/ArtistDetailViewModel.kt")
		assertContains(source, "_artistState, aurralRepository.confirmationQueue, aurralRepository.libraryArtistMonitorStates")
		assertContains(source, "state.data.withConfirmedAurralMonitoring(queue, knownMonitoring)")
		val action = source.substringAfter("fun setArtistMonitoringInAurral(").substringBefore("fun clearAurralError")
		assertContains(action, "if (_monitoringInAurral.value ||")
		assertContains(action, "AurralConfirmationStatus.Pending")
		assertContains(action, "finally {")
		assertContains(action, "_monitoringInAurral.value = false")
		assertContains(action, "if (error is CancellationException) throw error")
		assertFalse(".onSuccess" in action)
		assertFalse("aurralMonitored = monitored" in action)
		assertContains(commonSource("ui/screens/artist/ArtistDetailScreen.kt"), "aurralMonitoringSubmissionIsVisible(monitoringRequestInAurral, artistMonitorConfirmation?.status)")
		assertContains(commonSource("ui/screens/aurral/AurralArtistScreen.kt"), "monitoring = observedMonitoringState.monitoring")
	}

	private fun commonSource(path: String): String = listOf(
		File("src/commonMain/kotlin/paige/navic/$path"),
		File("composeApp/src/commonMain/kotlin/paige/navic/$path")
	).first { it.isFile }.readText()
}

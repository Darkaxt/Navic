package paige.navic.ui.screens.bindery

import com.russhwolf.settings.MapSettings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import paige.navic.data.remote.bindery.BinderyApiClient
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyFindingMetadata
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.FakeBinderyApiClient
import paige.navic.ui.core.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BinderyCatalogActionLifecycleTest {
	private val monitorLink = BinderyLink(
		href = "/opds/discover/authors/hc%3Ageorge-orwell/monitor",
		rel = listOf(BINDERY_MONITOR_REL)
	)
	private val unmonitorLink = BinderyLink(
		href = "/opds/authors/28/unmonitor",
		rel = listOf(BINDERY_UNMONITOR_REL)
	)
	private val monitorCatalog = BinderyCatalog(title = "George Orwell", links = listOf(monitorLink))
	private val monitoredCatalog = BinderyCatalog(title = "George Orwell", links = listOf(unmonitorLink))
	private val publicationMonitorLink = BinderyLink(
		href = "/api/v1/books/84/monitor",
		rel = listOf(BINDERY_MONITOR_REL)
	)
	private val publicationUnmonitorLink = BinderyLink(
		href = "/api/v1/books/84/unmonitor",
		rel = listOf(BINDERY_UNMONITOR_REL)
	)
	private val downloadRequestLink = BinderyLink(
		href = "/api/v1/findings/894/acquire",
		rel = listOf(BINDERY_DOWNLOAD_REQUEST_REL)
	)
	private val publicationMonitorCatalog = monitorCatalog.copy(
		publications = listOf(
			BinderyPublication(
				id = "urn:bindery:book:84",
				title = "Nineteen Eighty-Four",
				links = listOf(publicationMonitorLink)
			)
		)
	)
	private val publicationMonitoredCatalog = monitorCatalog.copy(
		publications = listOf(
			BinderyPublication(
				id = "urn:bindery:book:84",
				title = "Nineteen Eighty-Four",
				links = listOf(publicationUnmonitorLink)
			)
		)
	)
	private val findingDownloadCatalog = BinderyCatalog(
		title = "Findings",
		publications = listOf(
			BinderyPublication(
				id = "urn:bindery:finding:894",
				title = "Nineteen Eighty-Four",
				links = listOf(
					BinderyLink(href = "/opds/findings/894", rel = listOf("self")),
					downloadRequestLink
				),
				finding = BinderyFindingMetadata(findingId = "894")
			)
		)
	)
	private val nextPageLink = BinderyLink(href = "/opds/authors?page=2", rel = listOf("next"))
	private val pagedMonitorCatalog = monitorCatalog.copy(links = listOf(monitorLink, nextPageLink))

	@Test
	fun monitorStaysPendingThroughAuthoritativeRefreshAndSuppressesDuplicateRequest() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(monitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			assertEquals(1, api.actionCalls.get())
			assertTrue(monitorLink.href in viewModel.actionInFlight.value)

			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			assertTrue(
				monitorLink.href in viewModel.actionInFlight.value,
				"Monitor must remain disabled while the forced catalog refresh confirms the mutation."
			)
			assertIs<UiState.Success<BinderyCatalog>>(
				viewModel.catalogState.value,
				"Action confirmation must not flash the separate Bindery catalog loading indicator."
			)

			api.completeCatalog(Result.success(monitoredCatalog))
			viewModel.actionInFlight.await { it.isEmpty() }
			assertEquals(BinderyOpdsActionType.Unmonitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertEquals(null, viewModel.actionError.value)
		}
	}

	@Test
	fun refreshFailureRestoresMonitorAndPublishesActionError() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(monitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			api.completeCatalog(Result.failure(IllegalStateException("refresh failed")))

			viewModel.actionInFlight.await { it.isEmpty() }
			assertIs<UiState.Success<BinderyCatalog>>(viewModel.catalogState.value)
			assertEquals(BinderyOpdsActionType.Monitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertNotNull(viewModel.actionError.value)
		}
	}

	@Test
	fun unconfirmedRefreshRestoresMonitorAndPublishesActionError() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(monitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			api.completeCatalog(Result.success(monitorCatalog))

			viewModel.actionInFlight.await { it.isEmpty() }
			assertEquals(BinderyOpdsActionType.Monitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertNotNull(viewModel.actionError.value)
		}
	}

	@Test
	fun postFailureRestoresMonitorAndPublishesPrivacySafeActionError() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(monitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			api.completeAction(Result.failure(IllegalStateException("private endpoint detail")))

			viewModel.actionInFlight.await { it.isEmpty() }
			assertEquals(BinderyOpdsActionType.Monitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertEquals(
				"Bindery could not complete this action. Try again.",
				viewModel.actionError.value?.message
			)
			assertEquals(1, api.catalogCalls.get(), "A failed POST must not start a confirmation refresh.")
		}
	}

	@Test
	fun paginationIsRejectedWhileMutationOwnsCatalog() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(pagedMonitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			try {
				viewModel.loadNextPage()

				assertEquals(1, api.catalogCalls.get(), "Pagination must not call the repository during mutation.")
				assertTrue(!viewModel.isLoadingNextPage.value)
				assertTrue(monitorLink.href in viewModel.actionInFlight.value)
			} finally {
				if (api.catalogCalls.get() > 1) {
					api.completeCatalog(Result.failure(IllegalStateException("cleanup")))
				}
				api.completeAction(Result.failure(IllegalStateException("cleanup")))
				viewModel.actionInFlight.await { it.isEmpty() }
			}
		}
	}

	@Test
	fun nonCooperativePaginationSuccessCannotCommitAfterMutationClaim() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(pagedMonitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			api.ignoreCancellationForNextCatalogRequest()
			viewModel.loadNextPage()
			api.awaitCatalogRequest()
			assertTrue(viewModel.isLoadingNextPage.value)
			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			assertIs<UiState.Success<BinderyCatalog>>(viewModel.catalogState.value)
			assertTrue(viewModel.hasNextPage.value)
			assertTrue(!viewModel.isLoadingNextPage.value)
			assertTrue(monitorLink.href in viewModel.actionInFlight.value)

			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			api.completeCatalog(Result.success(monitoredCatalog))
			viewModel.actionInFlight.await { it.isEmpty() }
			assertEquals(BinderyOpdsActionType.Unmonitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertTrue(!viewModel.hasNextPage.value)

			api.completeNonCooperativeCatalog(
				Result.success(
					BinderyCatalog(
						title = "Stale page",
						navigation = listOf(BinderyLink(href = "/opds/authors/stale", title = "Stale"))
					)
				)
			)
			assertEquals(BinderyOpdsActionType.Unmonitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertTrue(viewModel.catalogState.value.data?.navigation.orEmpty().isEmpty())
			assertTrue(!viewModel.hasNextPage.value)
			assertTrue(!viewModel.isLoadingNextPage.value)
		}
	}

	@Test
	fun nonCooperativePaginationErrorCannotCommitAfterMutationClaim() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(pagedMonitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			api.ignoreCancellationForNextCatalogRequest()
			viewModel.loadNextPage()
			api.awaitCatalogRequest()
			assertTrue(viewModel.isLoadingNextPage.value)
			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			assertIs<UiState.Success<BinderyCatalog>>(viewModel.catalogState.value)
			assertTrue(viewModel.hasNextPage.value)
			assertTrue(!viewModel.isLoadingNextPage.value)
			assertTrue(monitorLink.href in viewModel.actionInFlight.value)

			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			api.completeCatalog(Result.success(monitoredCatalog))
			viewModel.actionInFlight.await { it.isEmpty() }
			assertEquals(BinderyOpdsActionType.Unmonitor, viewModel.catalogState.value.data?.primaryAction()?.type)

			api.completeNonCooperativeCatalog(Result.failure(IllegalStateException("stale pagination failure")))
			assertIs<UiState.Success<BinderyCatalog>>(viewModel.catalogState.value)
			assertEquals(BinderyOpdsActionType.Unmonitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertTrue(!viewModel.hasNextPage.value)
			assertTrue(!viewModel.isLoadingNextPage.value)
		}
	}

	@Test
	fun distinctActionIsRejectedUntilActiveMutationReleasesItsLease() = runTest {
		withViewModel { viewModel, api ->
			val firstMonitor = BinderyLink("/api/v1/books/1/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val firstUnmonitor = BinderyLink("/api/v1/books/1/unmonitor", rel = listOf(BINDERY_UNMONITOR_REL))
			val secondMonitor = BinderyLink("/api/v1/books/2/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val secondUnmonitor = BinderyLink("/api/v1/books/2/unmonitor", rel = listOf(BINDERY_UNMONITOR_REL))
			val initial = BinderyCatalog(
				title = "Books",
				publications = listOf(
					BinderyPublication("book-1", "First", links = listOf(firstMonitor)),
					BinderyPublication("book-2", "Second", links = listOf(secondMonitor))
				)
			)
			val firstConfirmed = initial.copy(
				publications = listOf(
					BinderyPublication("book-1", "First", links = listOf(firstUnmonitor)),
					BinderyPublication("book-2", "Second", links = listOf(secondMonitor))
				)
			)

			api.completeCatalog(Result.success(initial))
			viewModel.refreshCatalog(fullRefresh = true)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			viewModel.performAction(firstMonitor)
			assertEquals(firstMonitor.href, api.awaitActionRequest())
			viewModel.performAction(secondMonitor)
			assertEquals(1, api.actionCalls.get(), "A second path must not POST while one mutation owns the catalog.")
			assertEquals(setOf(firstMonitor.href), viewModel.actionInFlight.value)

			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			api.completeCatalog(Result.success(firstConfirmed))
			viewModel.actionInFlight.await { it.isEmpty() }
			assertEquals(
				BinderyOpdsActionType.Unmonitor,
				viewModel.catalogState.value.data?.publications?.first()?.primaryAction()?.type
			)

			viewModel.performAction(secondMonitor)
			assertEquals(secondMonitor.href, api.awaitActionRequest())
			assertEquals(setOf(secondMonitor.href), viewModel.actionInFlight.value)
			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			api.completeCatalog(
				Result.success(
					firstConfirmed.copy(
						publications = listOf(
							BinderyPublication("book-1", "First", links = listOf(firstUnmonitor)),
							BinderyPublication("book-2", "Second", links = listOf(secondUnmonitor))
						)
					)
				)
			)
			viewModel.actionInFlight.await { it.isEmpty() }
			assertEquals(2, api.actionCalls.get(), "The rejected action must be claimable after terminal cleanup.")
		}
	}

	@Test
	fun pageTwoRefreshWithoutNextPrunesPreviouslyLoadedPageThree() = runTest {
		withViewModel { viewModel, api ->
			val pageTwoPath = "/opds/books?page=2"
			val pageThreePath = "/opds/books?page=3"
			val pageTwoMonitor = BinderyLink("/api/v1/books/2/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val pageTwoUnmonitor = BinderyLink("/api/v1/books/2/unmonitor", rel = listOf(BINDERY_UNMONITOR_REL))
			val pageOne = BinderyCatalog(
				title = "Books",
				links = listOf(BinderyLink(pageTwoPath, rel = listOf("next"))),
				publications = listOf(BinderyPublication("book-1", "First"))
			)
			val pageTwo = BinderyCatalog(
				title = "Books page 2",
				links = listOf(BinderyLink(pageThreePath, rel = listOf("next"))),
				publications = listOf(BinderyPublication("book-2", "Second", links = listOf(pageTwoMonitor)))
			)
			val pageThree = BinderyCatalog(
				title = "Books page 3",
				publications = listOf(BinderyPublication("book-3", "Third"))
			)

			api.completeCatalog(Result.success(pageOne))
			viewModel.refreshCatalog(fullRefresh = true)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }
			viewModel.loadNextPage()
			assertEquals(pageTwoPath, api.awaitCatalogRequest())
			api.completeCatalog(Result.success(pageTwo))
			viewModel.isLoadingNextPage.await { !it }
			viewModel.loadNextPage()
			assertEquals(pageThreePath, api.awaitCatalogRequest())
			api.completeCatalog(Result.success(pageThree))
			viewModel.isLoadingNextPage.await { !it }
			assertEquals(listOf("book-1", "book-2", "book-3"), viewModel.catalogState.value.data?.publications?.map { it.id })

			viewModel.performAction(pageTwoMonitor)
			api.awaitActionRequest()
			api.completeAction(Result.success(Unit))
			assertEquals(pageTwoPath, api.awaitCatalogRequest())
			api.completeCatalog(
				Result.success(
					pageTwo.copy(
						links = emptyList(),
						publications = listOf(BinderyPublication("book-2", "Second", links = listOf(pageTwoUnmonitor)))
					)
				)
			)
			viewModel.actionInFlight.await { it.isEmpty() }

			assertEquals(listOf("book-1", "book-2"), viewModel.catalogState.value.data?.publications?.map { it.id })
			assertTrue(!viewModel.hasNextPage.value)
			assertTrue(!viewModel.isLoadingNextPage.value)
			assertEquals(null, viewModel.actionError.value)
		}
	}

	@Test
	fun pageTwoRefreshRedirectToUnloadedPageFourPrunesPageThreeAndRetainsLoadMore() = runTest {
		withViewModel { viewModel, api ->
			val pageTwoPath = "/opds/books?page=2"
			val pageThreePath = "/opds/books?page=3"
			val pageFourPath = "/opds/books?page=4"
			val pageTwoMonitor = BinderyLink("/api/v1/books/2/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val pageTwoUnmonitor = BinderyLink("/api/v1/books/2/unmonitor", rel = listOf(BINDERY_UNMONITOR_REL))
			val pageOne = BinderyCatalog(
				title = "Books",
				links = listOf(BinderyLink(pageTwoPath, rel = listOf("next"))),
				publications = listOf(BinderyPublication("book-1", "First"))
			)
			val pageTwo = BinderyCatalog(
				title = "Books page 2",
				links = listOf(BinderyLink(pageThreePath, rel = listOf("next"))),
				publications = listOf(BinderyPublication("book-2", "Second", links = listOf(pageTwoMonitor)))
			)
			val pageThree = BinderyCatalog(
				title = "Books page 3",
				publications = listOf(BinderyPublication("book-3", "Third"))
			)

			api.completeCatalog(Result.success(pageOne))
			viewModel.refreshCatalog(fullRefresh = true)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }
			viewModel.loadNextPage()
			api.awaitCatalogRequest()
			api.completeCatalog(Result.success(pageTwo))
			viewModel.isLoadingNextPage.await { !it }
			viewModel.loadNextPage()
			api.awaitCatalogRequest()
			api.completeCatalog(Result.success(pageThree))
			viewModel.isLoadingNextPage.await { !it }

			viewModel.performAction(pageTwoMonitor)
			api.awaitActionRequest()
			api.completeAction(Result.success(Unit))
			assertEquals(pageTwoPath, api.awaitCatalogRequest())
			api.completeCatalog(
				Result.success(
					pageTwo.copy(
						links = listOf(BinderyLink(pageFourPath, rel = listOf("next"))),
						publications = listOf(BinderyPublication("book-2", "Second", links = listOf(pageTwoUnmonitor)))
					)
				)
			)
			viewModel.actionInFlight.await { it.isEmpty() }

			assertEquals(listOf("book-1", "book-2"), viewModel.catalogState.value.data?.publications?.map { it.id })
			assertTrue(viewModel.hasNextPage.value)
			viewModel.loadNextPage()
			assertEquals(pageFourPath, api.awaitCatalogRequest())
			api.completeCatalog(Result.failure(IllegalStateException("cleanup")))
			viewModel.isLoadingNextPage.await { !it }
		}
	}

	@Test
	fun cyclicLoadedPageChainDoesNotExposeAnotherLoad() = runTest {
		withViewModel { viewModel, api ->
			val rootPath = "/opds/discover/authors/hc%3Ageorge-orwell"
			val pageTwoPath = "/opds/books?page=2"
			val pageOne = BinderyCatalog(
				title = "Books",
				links = listOf(BinderyLink(pageTwoPath, rel = listOf("next"))),
				publications = listOf(BinderyPublication("book-1", "First"))
			)
			val pageTwo = BinderyCatalog(
				title = "Books page 2",
				links = listOf(BinderyLink(rootPath, rel = listOf("next"))),
				publications = listOf(BinderyPublication("book-2", "Second"))
			)

			api.completeCatalog(Result.success(pageOne))
			viewModel.refreshCatalog(fullRefresh = true)
			assertEquals(rootPath, api.awaitCatalogRequest())
			viewModel.catalogState.await { it is UiState.Success }
			viewModel.loadNextPage()
			assertEquals(pageTwoPath, api.awaitCatalogRequest())
			api.completeCatalog(Result.success(pageTwo))
			viewModel.isLoadingNextPage.await { !it }

			assertEquals(listOf("book-1", "book-2"), viewModel.catalogState.value.data?.publications?.map { it.id })
			assertTrue(!viewModel.hasNextPage.value, "A loaded cycle must fail closed instead of exposing the root as load-more.")
		}
	}

	@Test
	fun paginatedPublicationConfirmsFromItsOriginPageAndPreservesMergedCatalog() = runTest {
		withViewModel { viewModel, api ->
			val pageTwoMonitor = BinderyLink("/api/v1/books/2/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val pageTwoUnmonitor = BinderyLink("/api/v1/books/2/unmonitor", rel = listOf(BINDERY_UNMONITOR_REL))
			val pageOne = BinderyCatalog(
				title = "Books",
				links = listOf(nextPageLink),
				publications = listOf(BinderyPublication("book-1", "First"))
			)
			val pageTwo = BinderyCatalog(
				title = "Books page 2",
				publications = listOf(BinderyPublication("book-2", "Second", links = listOf(pageTwoMonitor)))
			)
			val refreshedPageTwo = pageTwo.copy(
				publications = listOf(BinderyPublication("book-2", "Second", links = listOf(pageTwoUnmonitor)))
			)

			api.completeCatalog(Result.success(pageOne))
			viewModel.refreshCatalog(fullRefresh = true)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }
			viewModel.loadNextPage()
			assertEquals(nextPageLink.href, api.awaitCatalogRequest())
			api.completeCatalog(Result.success(pageTwo))
			viewModel.isLoadingNextPage.await { !it }
			assertEquals(2, viewModel.catalogState.value.data?.publications?.size)

			viewModel.performAction(pageTwoMonitor)
			assertEquals(pageTwoMonitor.href, api.awaitActionRequest())
			api.completeAction(Result.success(Unit))
			val confirmationPath = api.awaitCatalogRequest()
			api.completeCatalog(Result.success(refreshedPageTwo))
			viewModel.actionInFlight.await { it.isEmpty() }

			assertEquals(nextPageLink.href, confirmationPath, "Confirmation must refresh the action's origin page.")
			assertEquals(listOf("book-1", "book-2"), viewModel.catalogState.value.data?.publications?.map { it.id })
			assertEquals(
				BinderyOpdsActionType.Unmonitor,
				viewModel.catalogState.value.data?.publications?.last()?.primaryAction()?.type
			)
			assertEquals(null, viewModel.actionError.value)
		}
	}

	@Test
	fun inverseOnDifferentNavigationCardCannotConfirmActedOnCard() = runTest {
		withViewModel { viewModel, api ->
			val firstMonitor = BinderyLink("/api/v1/authors/1/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val secondMonitor = BinderyLink("/api/v1/authors/2/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val secondUnmonitor = BinderyLink("/api/v1/authors/2/unmonitor", rel = listOf(BINDERY_UNMONITOR_REL))
			val initial = BinderyCatalog(
				title = "Authors",
				navigation = listOf(
					BinderyLink("/opds/authors/1", title = "First", links = listOf(firstMonitor)),
					BinderyLink("/opds/authors/2", title = "Second", links = listOf(secondMonitor))
				)
			)
			val wrongTargetInverse = initial.copy(
				navigation = listOf(
					BinderyLink("/opds/authors/1", title = "First", links = listOf(firstMonitor)),
					BinderyLink("/opds/authors/2", title = "Second", links = listOf(secondUnmonitor))
				)
			)

			api.completeCatalog(Result.success(initial))
			viewModel.refreshCatalog(fullRefresh = true)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }
			viewModel.performAction(firstMonitor)
			api.awaitActionRequest()
			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			api.completeCatalog(Result.success(wrongTargetInverse))
			viewModel.actionInFlight.await { it.isEmpty() }

			assertEquals(initial, viewModel.catalogState.value.data, "A different card's inverse must not replace the catalog.")
			assertNotNull(viewModel.actionError.value)
		}
	}

	@Test
	fun inverseOnSameNavigationCardConfirmsActedOnCard() = runTest {
		withViewModel { viewModel, api ->
			val firstMonitor = BinderyLink("/api/v1/authors/1/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val firstUnmonitor = BinderyLink("/api/v1/authors/1/unmonitor", rel = listOf(BINDERY_UNMONITOR_REL))
			val secondMonitor = BinderyLink("/api/v1/authors/2/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val initial = BinderyCatalog(
				title = "Authors",
				navigation = listOf(
					BinderyLink("/opds/authors/1", title = "First", links = listOf(firstMonitor)),
					BinderyLink("/opds/authors/2", title = "Second", links = listOf(secondMonitor))
				)
			)
			val confirmed = initial.copy(
				navigation = listOf(
					BinderyLink("/opds/authors/1", title = "First", links = listOf(firstUnmonitor)),
					BinderyLink("/opds/authors/2", title = "Second", links = listOf(secondMonitor))
				)
			)

			api.completeCatalog(Result.success(initial))
			viewModel.refreshCatalog(fullRefresh = true)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }
			viewModel.performAction(firstMonitor)
			api.awaitActionRequest()
			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			api.completeCatalog(Result.success(confirmed))
			viewModel.actionInFlight.await { it.isEmpty() }

			assertEquals(confirmed, viewModel.catalogState.value.data)
			assertEquals(null, viewModel.actionError.value)
		}
	}

	@Test
	fun duplicateActionHrefAcrossTargetsFailsClosedBeforePost() = runTest {
		withViewModel { viewModel, api ->
			val sharedMonitor = BinderyLink("/api/v1/shared/monitor", rel = listOf(BINDERY_MONITOR_REL))
			val ambiguous = BinderyCatalog(
				title = "Authors",
				navigation = listOf(
					BinderyLink("/opds/authors/1", title = "First", links = listOf(sharedMonitor)),
					BinderyLink("/opds/authors/2", title = "Second", links = listOf(sharedMonitor))
				)
			)
			api.completeCatalog(Result.success(ambiguous))
			viewModel.refreshCatalog(fullRefresh = true)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }
			api.completeAction(Result.failure(IllegalStateException("must not be consumed")))

			viewModel.performAction(sharedMonitor)
			viewModel.actionInFlight.await { it.isEmpty() }

			assertEquals(0, api.actionCalls.get(), "An href shared by distinct targets must not be guessed.")
			assertNotNull(viewModel.actionError.value)
		}
	}

	@Test
	fun publicationMonitorUsesItsOwnAuthoritativeTargetAndSuppressesDuplicateRequest() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(publicationMonitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }
			assertEquals(BinderyOpdsActionType.Monitor, viewModel.catalogState.value.data?.primaryAction()?.type)

			viewModel.performAction(publicationMonitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			viewModel.performAction(publicationMonitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			assertEquals(1, api.actionCalls.get())
			assertTrue(publicationMonitorLink.href in viewModel.actionInFlight.value)

			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			assertTrue(
				publicationMonitorLink.href in viewModel.actionInFlight.value,
				"The publication action must remain pending through its confirmation refresh."
			)
			api.completeCatalog(Result.success(publicationMonitoredCatalog))
			viewModel.actionInFlight.await { it.isEmpty() }

			val publicationAction = viewModel.catalogState.value.data
				?.publications
				?.single()
				?.primaryAction()
			assertEquals(BinderyOpdsActionType.Unmonitor, publicationAction?.type)
			assertEquals(BinderyOpdsActionType.Monitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertEquals(null, viewModel.actionError.value)
		}
	}

	@Test
	fun findingDownloadRequestPostsAndCommitsSuccessfulAuthoritativeRefresh() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(findingDownloadCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			viewModel.performAction(downloadRequestLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			assertTrue(downloadRequestLink.href in viewModel.actionInFlight.value)
			api.completeAction(Result.success(Unit))
			api.awaitCatalogRequest()
			assertTrue(downloadRequestLink.href in viewModel.actionInFlight.value)

			val refreshedCatalog = findingDownloadCatalog.copy(title = "Refreshed findings")
			api.completeCatalog(Result.success(refreshedCatalog))
			viewModel.actionInFlight.await { it.isEmpty() }

			assertEquals("Refreshed findings", viewModel.catalogState.value.data?.title)
			assertEquals(BinderyOpdsActionType.DownloadRequest, binderyCatalogCards(
				catalog = viewModel.catalogState.value.data ?: error("Missing refreshed catalog"),
				tab = BinderyCatalogTab.Findings
			).single().primaryAction()?.type)
			assertEquals(null, viewModel.actionError.value)
		}
	}

	@Test
	fun nonMemberMonitorLinkFailsClosedWithoutPosting() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(monitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }
			val staleLink = monitorLink.copy(href = "/opds/discover/authors/stale/monitor")
			api.completeAction(Result.failure(IllegalStateException("must not be consumed")))

			viewModel.performAction(staleLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			viewModel.actionInFlight.await { it.isEmpty() }

			assertEquals(0, api.actionCalls.get(), "A stale/non-member Monitor link must not be posted.")
			assertEquals(BinderyOpdsActionType.Monitor, viewModel.catalogState.value.data?.primaryAction()?.type)
			assertEquals("Bindery did not confirm this action. Try again.", viewModel.actionError.value?.message)
		}
	}

	@Test
	fun nonCooperativeOlderSuccessCannotOverwriteCatalogAfterMutationClaim() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(monitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			api.ignoreCancellationForNextCatalogRequest()
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			try {
				api.completeNonCooperativeCatalog(Result.success(monitorCatalog.copy(title = "Stale author")))

				assertIs<UiState.Success<BinderyCatalog>>(viewModel.catalogState.value)
				assertEquals("George Orwell", viewModel.catalogState.value.data?.title)
				assertTrue(monitorLink.href in viewModel.actionInFlight.value)
			} finally {
				api.completeAction(Result.failure(IllegalStateException("cleanup")))
				viewModel.actionInFlight.await { it.isEmpty() }
			}
		}
	}

	@Test
	fun nonCooperativeOlderFailureCannotReplacePreservedCatalogAfterMutationClaim() = runTest {
		withViewModel { viewModel, api ->
			api.completeCatalog(Result.success(monitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			api.ignoreCancellationForNextCatalogRequest()
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitCatalogRequest()
			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			api.awaitActionRequest()
			try {
				api.completeNonCooperativeCatalog(Result.failure(IllegalStateException("stale failure")))

				assertIs<UiState.Success<BinderyCatalog>>(viewModel.catalogState.value)
				assertEquals("George Orwell", viewModel.catalogState.value.data?.title)
				assertTrue(monitorLink.href in viewModel.actionInFlight.value)
			} finally {
				api.completeAction(Result.failure(IllegalStateException("cleanup")))
				viewModel.actionInFlight.await { it.isEmpty() }
			}
		}
	}

	@Test
	fun ordinaryRefreshQueuedBeforeMutationCannotStartAfterMutationClaim() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		withViewModel(dispatcher) { viewModel, api ->
			api.completeCatalog(Result.success(monitorCatalog))
			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			dispatcher.scheduler.runCurrent()
			api.awaitCatalogRequest()
			viewModel.catalogState.await { it is UiState.Success }

			viewModel.refreshCatalog(fullRefresh = true, queryMode = BinderyAvailabilityQueryMode.Detail)
			viewModel.performAction(monitorLink, queryMode = BinderyAvailabilityQueryMode.Detail)
			dispatcher.scheduler.runCurrent()
			api.awaitActionRequest()
			try {
				assertEquals(
					1,
					api.catalogCalls.get(),
					"An ordinary refresh queued before claim must lose ownership before it starts."
				)
				assertIs<UiState.Success<BinderyCatalog>>(viewModel.catalogState.value)
				assertTrue(monitorLink.href in viewModel.actionInFlight.value)
			} finally {
				if (api.catalogCalls.get() > 1) {
					api.completeCatalog(Result.failure(IllegalStateException("cleanup")))
				}
				api.completeAction(Result.failure(IllegalStateException("cleanup")))
				dispatcher.scheduler.runCurrent()
				viewModel.actionInFlight.await { it.isEmpty() }
			}
		}
	}

	@Test
	fun actionPathIsClaimedBeforeCoroutineLaunch() {
		val source = commonMain("paige/navic/ui/screens/bindery/BinderyCatalogViewModel.kt")
		val performAction = source.substringAfter("fun performAction(").substringBefore("fun clearActionError()")
		val claim = performAction.indexOf("claimMutation(actionPath)")
		val launch = performAction.indexOf("viewModelScope.launch(actionDispatcher)")

		assertTrue(claim >= 0, "performAction must atomically claim the action path.")
		assertTrue(claim < launch, "The action path must be claimed before coroutine launch.")
	}

	private suspend fun withViewModel(
		testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
		block: suspend (BinderyCatalogViewModel, ControlledBinderyApiClient) -> Unit
	) {
		Dispatchers.setMain(testDispatcher)
		try {
			val api = ControlledBinderyApiClient()
			val preferences = PreferenceManager(MapSettings()).apply {
				binderyEnabled = true
				binderyOpdsBaseUrl = "https://bindery.example.com/opds/"
				binderyApiKey = "test-key"
			}
			val viewModel = BinderyCatalogViewModel(
				path = "/opds/discover/authors/hc%3Ageorge-orwell",
				repository = BinderyRepository(preferences, api),
				actionDispatcher = testDispatcher,
				catalogDispatcher = testDispatcher
			)
			block(viewModel, api)
		} finally {
			Dispatchers.resetMain()
		}
	}

	private suspend fun <T> kotlinx.coroutines.flow.StateFlow<T>.await(predicate: (T) -> Boolean): T =
		withContext(Dispatchers.Default.limitedParallelism(1)) {
			withTimeout(5_000) { filter(predicate).first() }
		}

	private fun commonMain(path: String): String =
		listOf(
			File("../composeApp/src/commonMain/kotlin/$path"),
			File("composeApp/src/commonMain/kotlin/$path"),
			File("src/commonMain/kotlin/$path")
		).firstOrNull { it.isFile }?.readText()
			?: error("Could not locate commonMain source $path")
}

private class ControlledBinderyApiClient(
	private val delegate: BinderyApiClient = FakeBinderyApiClient()
) : BinderyApiClient by delegate {
	private val catalogRequests = Channel<String>(Channel.UNLIMITED)
	private val catalogResults = Channel<Result<BinderyCatalog>>(Channel.UNLIMITED)
	private val nonCooperativeCatalogResults = Channel<Result<BinderyCatalog>>(Channel.UNLIMITED)
	private val actionRequests = Channel<String>(Channel.UNLIMITED)
	private val actionResults = Channel<Result<Unit>>(Channel.UNLIMITED)
	private val ignoreNextCatalogCancellation = AtomicBoolean(false)
	val actionCalls = AtomicInteger()
	val catalogCalls = AtomicInteger()

	override suspend fun fetchCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyCatalog {
		catalogCalls.incrementAndGet()
		catalogRequests.send(path)
		return if (ignoreNextCatalogCancellation.compareAndSet(true, false)) {
			withContext(NonCancellable) { nonCooperativeCatalogResults.receive().getOrThrow() }
		} else {
			catalogResults.receive().getOrThrow()
		}
	}

	override suspend fun performAction(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) {
		actionCalls.incrementAndGet()
		actionRequests.send(path)
		actionResults.receive().getOrThrow()
	}

	suspend fun awaitCatalogRequest(): String =
		withContext(Dispatchers.Default.limitedParallelism(1)) {
			withTimeout(5_000) { catalogRequests.receive() }
		}

	suspend fun completeCatalog(result: Result<BinderyCatalog>) {
		catalogResults.send(result)
	}

	suspend fun completeNonCooperativeCatalog(result: Result<BinderyCatalog>) {
		nonCooperativeCatalogResults.send(result)
	}

	fun ignoreCancellationForNextCatalogRequest() {
		ignoreNextCatalogCancellation.set(true)
	}

	suspend fun awaitActionRequest(): String =
		withContext(Dispatchers.Default.limitedParallelism(1)) {
			withTimeout(5_000) { actionRequests.receive() }
		}

	suspend fun completeAction(result: Result<Unit>) {
		actionResults.send(result)
	}
}

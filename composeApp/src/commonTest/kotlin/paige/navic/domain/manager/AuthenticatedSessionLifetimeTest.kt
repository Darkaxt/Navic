package paige.navic.domain.manager

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticatedSessionLifetimeTest {
	@Test
	fun endingSessionCancelsAndJoinsOldWorkBeforeReplacement() = runBlocking {
		val lifetime = AuthenticatedSessionLifetime()
		lifetime.activateInitialSession()
		val firstScope = lifetime.currentScope()!!
		val started = CompletableDeferred<Unit>()
		val finished = CompletableDeferred<Unit>()
		firstScope.launch {
			try {
				started.complete(Unit)
				awaitCancellation()
			} finally {
				finished.complete(Unit)
			}
		}
		started.await()

		lifetime.endSession()

		assertTrue(finished.isCompleted)
		lifetime.startSession()
		assertNotSame(firstScope, lifetime.currentScope())
	}

	@Test
	fun repeatingWorkRestartsForEachAuthenticatedSession() = runBlocking {
		val lifetime = AuthenticatedSessionLifetime()
		val firstStart = CompletableDeferred<Unit>()
		val secondStart = CompletableDeferred<Unit>()
		var startCount = 0
		val repeating = lifetime.repeatInSession {
			startCount += 1
			if (startCount == 1) firstStart.complete(Unit) else secondStart.complete(Unit)
			awaitCancellation()
		}

		lifetime.startSession()
		firstStart.await()
		lifetime.endSession()
		lifetime.startSession()
		secondStart.await()

		assertEquals(2, startCount)
		repeating.cancel()
		lifetime.endSession()
	}

	@Test
	fun runInSessionIsCancelledAndJoinedByLogoutBoundary() = runBlocking {
		supervisorScope {
			val lifetime = AuthenticatedSessionLifetime()
			lifetime.startSession()
			val started = CompletableDeferred<Unit>()
			val request = async {
				lifetime.runInSession {
					started.complete(Unit)
					awaitCancellation()
				}
			}
			started.await()

			lifetime.endSession()

			assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
		}
	}
}

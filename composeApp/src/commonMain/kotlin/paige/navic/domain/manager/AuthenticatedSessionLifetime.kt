package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthenticatedSessionLifetime {
	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val sessionMutex = Mutex()
	private val _sessionScope = MutableStateFlow<CoroutineScope?>(null)
	val sessionScope = _sessionScope.asStateFlow()

	fun activateInitialSession() {
		if (_sessionScope.value == null) {
			_sessionScope.value = newSessionScope()
		}
	}

	suspend fun startSession() {
		val previous = sessionMutex.withLock {
			val old = _sessionScope.value
			_sessionScope.value = null
			old
		}
		previous?.coroutineContext?.get(Job)?.cancelAndJoin()
		sessionMutex.withLock {
			_sessionScope.value = newSessionScope()
		}
	}

	suspend fun endSession() {
		val previous = sessionMutex.withLock {
			val old = _sessionScope.value
			_sessionScope.value = null
			old
		}
		previous?.coroutineContext?.get(Job)?.cancelAndJoin()
	}

	fun currentScope(): CoroutineScope? = _sessionScope.value

	suspend fun <T> runInSession(block: suspend CoroutineScope.() -> T): T {
		val scope = currentScope() ?: throw CancellationException("No authenticated session")
		return scope.async { block(scope) }.await()
	}

	fun repeatInSession(block: suspend CoroutineScope.() -> Unit): Job = applicationScope.launch {
		sessionScope.filterNotNull().collect { scope ->
			scope.launch { block(scope) }.join()
		}
	}

	private fun newSessionScope(): CoroutineScope =
		CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

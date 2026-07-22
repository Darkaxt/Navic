package paige.navic.domain.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import paige.navic.domain.models.isOnlineForOfflineMode
import android.net.ConnectivityManager as AndroidConnectivityManager

private data class NetworkStatus(
	val isOnline: Boolean = false,
	val isCellular: Boolean = false
) {
	companion object {
		fun fromCaps(
			caps: NetworkCapabilities
		) = NetworkStatus(
			isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				&& caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
			isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
				|| !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
		)
	}
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
actual class ConnectivityManager(
	context: Context,
	private val offlineModeCoordinator: OfflineModeCoordinator
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val dispatcher = Dispatchers.IO
	private val started = SharingStarted.WhileSubscribed(5000)
	private val connectivityManager =
		context.getSystemService(Context.CONNECTIVITY_SERVICE) as AndroidConnectivityManager

	private val networkStatus = callbackFlow {
		val callback = object : AndroidConnectivityManager.NetworkCallback() {
			override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
				super.onCapabilitiesChanged(network, caps)
				trySend(NetworkStatus.fromCaps(caps))
			}

			override fun onLost(network: Network) {
				super.onLost(network)
				trySend(NetworkStatus())
			}
		}

		val request = NetworkRequest.Builder()
			.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
			.build()

		connectivityManager.registerNetworkCallback(request, callback)

		trySend(
			connectivityManager
				.getNetworkCapabilities(connectivityManager.activeNetwork)
				?.let { NetworkStatus.fromCaps(it) }
				?: NetworkStatus()
		)

		awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
	}
		.flowOn(dispatcher)
		.conflate()
		.stateIn(scope, started, NetworkStatus())

	actual val isCellular = networkStatus
		.map { it.isCellular }
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, false)

	actual val isNetworkAvailable = networkStatus
		.map { it.isOnline }
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, false)

	actual val isOnline = combine(networkStatus, offlineModeCoordinator.state) { status, offline ->
		isOnlineForOfflineMode(
			isNetworkAvailable = status.isOnline,
			isCellular = status.isCellular,
			offlineMode = offline.effectiveMode
		)
	}
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, true)
}

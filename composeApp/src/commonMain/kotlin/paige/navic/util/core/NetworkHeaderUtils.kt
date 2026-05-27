package paige.navic.util.core

import coil3.network.NetworkHeaders

fun Map<String, String>.toNetworkHeaders(): NetworkHeaders =
	NetworkHeaders.Builder().apply {
		forEach { (key, value) -> add(key, value) }
	}.build()

package paige.navic.ui.navigation

import androidx.navigation3.runtime.NavKey

sealed interface NavBackAction {
	data object Pop : NavBackAction
	data class ReplaceRoot(val destination: Screen) : NavBackAction
	data object Stay : NavBackAction
}

fun navBackActionFor(backStack: List<NavKey>): NavBackAction {
	if (backStack.size > 1) return NavBackAction.Pop
	val current = backStack.lastOrNull() as? Screen ?: return NavBackAction.Stay
	return fallbackRootDestinationFor(current)?.let(NavBackAction::ReplaceRoot) ?: NavBackAction.Stay
}

fun canNavigateBack(backStack: List<NavKey>): Boolean =
	navBackActionFor(backStack) !is NavBackAction.Stay

fun fallbackRootDestinationFor(screen: Screen): Screen? =
	screen.destinationMetadata().let { metadata ->
		metadata.visibleRootBackDestination ?: metadata.areaRootDestination
	}

fun shouldShowRootBackForScreen(screen: Screen?): Boolean =
	screen?.destinationMetadata()?.visibleRootBackDestination != null

fun navBackStackAfterTabSelection(backStack: List<NavKey>, destination: Screen): List<NavKey> {
	return listOf(destination)
}

fun MutableList<NavKey>.performNavicBack(): Boolean =
	when (val action = navBackActionFor(this)) {
		NavBackAction.Pop -> {
			removeLastOrNull()
			true
		}

		is NavBackAction.ReplaceRoot -> {
			clear()
			add(action.destination)
			true
		}

		NavBackAction.Stay -> false
	}

fun MutableList<NavKey>.selectNavicRootTab(destination: Screen) {
	val nextStack = navBackStackAfterTabSelection(this, destination)
	clear()
	addAll(nextStack)
}

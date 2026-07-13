package paige.navic.ui.navigation

import androidx.navigation3.runtime.NavEntry

// Pinned contract: gradle/libs.versions.toml androidx-navigation3 = 1.1.0-beta01.
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> NavEntry<T>.sceneKey(): T = contentKey as T

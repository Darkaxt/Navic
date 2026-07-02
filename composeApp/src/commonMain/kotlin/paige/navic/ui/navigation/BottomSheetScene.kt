package paige.navic.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.kyant.capsule.ContinuousCapsule
import paige.navic.ui.components.sheets.ModalBottomSheet
import paige.navic.util.ui.LocalSheetState

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

	override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
		val entry = entries.lastOrNull() ?: return null
		val properties = entry.metadata[MetadataKey] ?: return null

		return object : OverlayScene<T> {
			@Suppress("UNCHECKED_CAST")
			override val key = entry.contentKey as T
			override val entries = listOf(entry)
			override val previousEntries = entries.dropLast(1)
			override val overlaidEntries = entries.dropLast(1)

			override val content = @Composable {
				val lifecycleOwner = rememberLifecycleOwner()
				val sheetState = rememberModalBottomSheetState()
				ModalBottomSheet(
					onDismissRequest = onBack,
					sheetState = sheetState,
					properties = properties,
					dragHandle = {
						Surface(
							modifier = Modifier.padding(vertical = 5.dp),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							shape = ContinuousCapsule,
						) {
							Box(Modifier.size(width = 32.dp, height = 4.dp))
						}
					}
				) {
					CompositionLocalProvider(
						LocalLifecycleOwner provides lifecycleOwner,
						LocalSheetState provides sheetState
					) {
						entry.Content()
					}
				}
			}

			// could use onRemove for sheet animations but that will
			// make sheets close if you layer them on top of each other
		}
	}

	companion object {
		object MetadataKey : NavMetadataKey<ModalBottomSheetProperties>

		fun bottomSheet(
			sheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties()
		) = metadata { put(MetadataKey, sheetProperties) }
	}
}

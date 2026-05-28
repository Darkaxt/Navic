package paige.navic.ui.components.layouts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.ToolbarPosition
import paige.navic.domain.models.shouldShowSheetToolbarBottom
import paige.navic.domain.models.shouldShowSheetToolbarTop

@Composable
fun SheetScaffold(
	toolbar: @Composable (windowInsets: WindowInsets) -> Unit,
	toolbarPosition: ToolbarPosition? = null,
	floatingActionButton: @Composable () -> Unit = {},
	content: @Composable (contentPadding: PaddingValues) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val toolbarPosition = toolbarPosition ?: preferenceManager.nowPlayingToolbarPosition
	Scaffold(
		topBar = {
			if (shouldShowSheetToolbarTop(toolbarPosition)) {
				toolbar(WindowInsets.systemBars.only(
					WindowInsetsSides.Horizontal + WindowInsetsSides.Top
				))
			}
		},
		bottomBar = {
			if (shouldShowSheetToolbarBottom(toolbarPosition)) {
				toolbar(WindowInsets.systemBars.only(
					WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
				))
			}
		},
		floatingActionButton = floatingActionButton,
		containerColor = Color.Transparent,
		content = content
	)
}

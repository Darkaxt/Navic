package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

private const val KomikkuTabletUiRequiredScreenWidthDp = 720

@Composable
@ReadOnlyComposable
actual fun komikkuReaderIsTabletUi(): Boolean =
	LocalConfiguration.current.smallestScreenWidthDp >= KomikkuTabletUiRequiredScreenWidthDp

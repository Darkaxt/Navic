package paige.navic.ui.screens.reader

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

private const val KomikkuTabletUiRequiredScreenWidthDp = 720

@Composable
@ReadOnlyComposable
actual fun komikkuReaderIsTabletUi(): Boolean =
	LocalConfiguration.current.smallestScreenWidthDp >= KomikkuTabletUiRequiredScreenWidthDp

@Composable
@ReadOnlyComposable
actual fun komikkuReaderDebugToolsEnabled(): Boolean =
	(LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

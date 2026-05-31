package paige.navic.di

import androidx.compose.runtime.Composable
import coil3.compose.setSingletonImageLoaderFactory

@Composable
actual fun InstallSingletonImageLoaderFactory() {
	setSingletonImageLoaderFactory { platformContext ->
		initializeSingletonImageLoader(platformContext)
	}
}

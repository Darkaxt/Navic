package paige.navic.di

import androidx.compose.runtime.Composable

@Composable
actual fun InstallSingletonImageLoaderFactory() {
	// Android registers Coil through Application.SingletonImageLoader.Factory before Compose starts.
}

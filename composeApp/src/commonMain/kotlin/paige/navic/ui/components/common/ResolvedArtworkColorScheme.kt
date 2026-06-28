package paige.navic.ui.components.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import coil3.compose.LocalPlatformContext
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.kmpalette.color
import com.kmpalette.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import paige.navic.di.getStaticImageLoader
import paige.navic.domain.manager.ArtworkColorManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.dominantColorArtworkUrl
import paige.navic.domain.models.settings.ThemeMode
import paige.navic.util.color.toComposeImageBitmap
import paige.navic.util.core.toNetworkHeaders

@Composable
fun rememberResolvedArtworkColorScheme(
	playbackArtwork: PlaybackArtworkUiState,
	enabled: Boolean = true
): ColorScheme? =
	rememberResolvedArtworkColorScheme(
		coverArtId = playbackArtwork.coverArtId,
		imageUrl = playbackArtwork.imageUrl,
		imageCacheKey = playbackArtwork.imageCacheKey,
		imageRequestHeaders = playbackArtwork.imageRequestHeaders,
		enabled = enabled
	)

@Composable
fun rememberResolvedArtworkColorScheme(
	coverArtId: String?,
	imageUrl: String? = null,
	imageCacheKey: String? = null,
	imageRequestHeaders: Map<String, String> = emptyMap(),
	enabled: Boolean = true
): ColorScheme? {
	val sessionManager = koinInject<SessionManager>()
	val colorManager = koinInject<ArtworkColorManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val inDarkTheme = isSystemInDarkTheme()
	val isDark = remember(preferenceManager.themeMode, inDarkTheme) {
		when (preferenceManager.themeMode) {
			ThemeMode.System -> inDarkTheme
			ThemeMode.Dark -> true
			ThemeMode.Light -> false
		}
	}
	val serverCoverUri = remember(coverArtId) {
		coverArtId?.let(sessionManager::getCoverArtUrl)
	}
	val sourceUrl = remember(imageUrl, serverCoverUri) {
		dominantColorArtworkUrl(
			serverArtworkUrl = serverCoverUri,
			externalArtworkUrl = imageUrl
		)
	}
	val artworkKey = remember(coverArtId, imageUrl, imageCacheKey) {
		artworkColorCacheKey(
			coverArtId = coverArtId,
			imageUrl = imageUrl,
			imageCacheKey = imageCacheKey
		)
	}
	val requestHeaders = remember(
		sourceUrl,
		imageUrl,
		imageRequestHeaders,
		preferenceManager.customHeaders,
		preferenceManager.reverseProxyBasicAuthEnabled,
		preferenceManager.reverseProxyBasicAuthUsername,
		preferenceManager.reverseProxyBasicAuthPassword
	) {
		if (imageUrl.nonBlankOrNull() == null && sourceUrl != null) {
			preferenceManager.serverRequestHeadersMap()
		} else {
			imageRequestHeaders
		}
	}
	val coilContext = LocalPlatformContext.current
	val imageLoader = remember(coilContext) { getStaticImageLoader(coilContext) }
	val effectiveEnabled = enabled && preferenceManager.dynamicThemes
	val dominantColor by produceState<Color?>(
		initialValue = null,
		effectiveEnabled,
		artworkKey,
		sourceUrl,
		requestHeaders,
		imageCacheKey
	) {
		if (!effectiveEnabled || artworkKey == null || sourceUrl == null) {
			value = null
			return@produceState
		}

		colorManager.getColor(artworkKey)?.let { cachedColor ->
			value = cachedColor
			return@produceState
		}

		val extractedColor = runCatching {
			withContext(Dispatchers.IO) {
				val request = ImageRequest.Builder(coilContext)
					.data(sourceUrl)
					.size(128)
					.memoryCacheKey("${imageCacheKey ?: artworkKey}:color")
					.diskCacheKey(imageCacheKey ?: artworkKey)
					.diskCachePolicy(CachePolicy.ENABLED)
					.memoryCachePolicy(CachePolicy.ENABLED)
					.apply {
						if (requestHeaders.isNotEmpty()) {
							httpHeaders(requestHeaders.toNetworkHeaders())
						}
					}
					.build()
				val result = imageLoader.execute(request)
				if (result !is SuccessResult) return@withContext null
				val image = result.image.toComposeImageBitmap(coilContext)
				withContext(Dispatchers.Default) {
					Palette.from(image).generate().dominantSwatch?.color
				}
			}
		}.getOrNull()

		extractedColor?.let { color ->
			value = color
			colorManager.putColor(artworkKey, color)
		}
	}

	val color = dominantColor ?: return null
	return rememberDynamicColorScheme(
		seedColor = color,
		isDark = isDark,
		style = PaletteStyle.Content,
		specVersion = ColorSpec.SpecVersion.SPEC_2021
	)
}

internal fun artworkColorCacheKey(
	coverArtId: String?,
	imageUrl: String?,
	imageCacheKey: String?
): String? {
	val externalKey = imageCacheKey.nonBlankOrNull() ?: imageUrl.nonBlankOrNull()
	return when {
		imageUrl.nonBlankOrNull() != null && externalKey != null -> "external:$externalKey"
		coverArtId.nonBlankOrNull() != null -> "server:${coverArtId.nonBlankOrNull()}"
		else -> null
	}
}

private fun String?.nonBlankOrNull(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

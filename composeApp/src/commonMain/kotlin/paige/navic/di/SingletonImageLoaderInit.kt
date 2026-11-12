package paige.navic.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import okio.FileSystem
import org.koin.mp.KoinPlatform
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DefaultImageCacheSizeMb
import paige.navic.domain.models.cacheSizeMbToBytes
import coil3.PlatformContext as CoilPlatformContext

private var sharedMemoryCache: MemoryCache? = null
private var sharedDiskCache: DiskCache? = null
private const val SHARED_IMAGE_MEMORY_CACHE_PERCENT = 0.08

private fun getMemoryCache(context: CoilPlatformContext): MemoryCache {
	return sharedMemoryCache ?: MemoryCache.Builder()
		.maxSizePercent(context, SHARED_IMAGE_MEMORY_CACHE_PERCENT)
		.build().also { sharedMemoryCache = it }
}

private fun getDiskCache(): DiskCache {
	return sharedDiskCache ?: DiskCache.Builder()
		.directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image_cache")
		.maxSizeBytes(configuredImageDiskCacheMaxBytes())
		.build().also { sharedDiskCache = it }
}

fun initializeSingletonImageLoader(context: CoilPlatformContext): ImageLoader {
	return ImageLoader.Builder(context)
		.components {
			add(KtorNetworkFetcherFactory())
		}
		.memoryCache { getMemoryCache(context) }
		.diskCache { getDiskCache() }
		.crossfade(true)
		.build()
}

fun getStaticImageLoader(context: CoilPlatformContext): ImageLoader {
	return ImageLoader.Builder(context)
		.serviceLoaderEnabled(false)
		.components {
			add(KtorNetworkFetcherFactory())
		}
		.memoryCache { getMemoryCache(context) }
		.diskCache { getDiskCache() }
		.crossfade(true)
		.build()
}

/** Coil fixes the disk cache size at construction, so a changed limit applies after an app restart. */
private fun configuredImageDiskCacheMaxBytes(): Long = cacheSizeMbToBytes(
	KoinPlatform.getKoinOrNull()?.getOrNull<PreferenceManager>()?.imageCacheSizeMb
		?: DefaultImageCacheSizeMb
)

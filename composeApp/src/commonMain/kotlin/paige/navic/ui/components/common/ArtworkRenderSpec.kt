package paige.navic.ui.components.common

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainGenreCollection
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.displayName
import paige.navic.domain.models.playlistArtworkLabel
import paige.navic.domain.models.playlistDisplayName
import paige.navic.domain.models.playlistFallbackKind
import paige.navic.domain.models.visibleCollectionCoverArtId
import paige.navic.domain.models.visiblePlaylistCoverArtId

@Immutable
enum class GeneratedArtworkVariant {
	GridCard,
	CarouselCard,
	DetailHero,
	SheetThumbnail,
	NowPlayingDisc
}

@Immutable
data class GeneratedArtworkSpec(
	val kindLabel: String?,
	val primaryLabel: String?,
	val secondaryLabel: String? = null,
	val seed: String?,
	val variant: GeneratedArtworkVariant = GeneratedArtworkVariant.GridCard
)

@Immutable
data class ArtworkRenderSpec(
	val coverArtId: String?,
	val imageUrl: String?,
	val imageCacheKey: String? = null,
	val imageRequestHeaders: Map<String, String> = emptyMap(),
	val contentDescription: String?,
	val generatedArtwork: GeneratedArtworkSpec
)

fun DomainPlaylist.playlistArtworkRenderSpec(
	imageRequestHeaders: Map<String, String> = emptyMap(),
	variant: GeneratedArtworkVariant = GeneratedArtworkVariant.GridCard
): ArtworkRenderSpec {
	val display = playlistDisplayName()
	return ArtworkRenderSpec(
		coverArtId = visiblePlaylistCoverArtId(),
		imageUrl = null,
		imageRequestHeaders = imageRequestHeaders,
		contentDescription = display,
		generatedArtwork = GeneratedArtworkSpec(
			kindLabel = playlistFallbackKind(),
			primaryLabel = playlistArtworkLabel(),
			seed = id.ifBlank { name },
			variant = variant
		)
	)
}

fun DomainSongCollection.collectionArtworkRenderSpec(
	displayTitle: String? = null,
	externalImageUrl: String? = null,
	imageRequestHeaders: Map<String, String> = emptyMap(),
	variant: GeneratedArtworkVariant = GeneratedArtworkVariant.DetailHero
): ArtworkRenderSpec {
	val display = displayTitle?.trim()?.takeIf { it.isNotEmpty() } ?: displayName()
	val external = externalImageUrl?.trim()?.takeIf { it.isNotEmpty() }
	val generated = when (this) {
		is DomainPlaylist -> GeneratedArtworkSpec(
			kindLabel = playlistFallbackKind(),
			primaryLabel = playlistArtworkLabel(),
			seed = id.ifBlank { name },
			variant = variant
		)
		is DomainAlbum -> GeneratedArtworkSpec(
			kindLabel = "Album",
			primaryLabel = display,
			secondaryLabel = artistName,
			seed = id.ifBlank { name },
			variant = variant
		)
		is DomainGenreCollection -> GeneratedArtworkSpec(
			kindLabel = "Genre",
			primaryLabel = display,
			seed = id.ifBlank { name },
			variant = variant
		)
	}
	return ArtworkRenderSpec(
		coverArtId = if (external != null) null else visibleCollectionCoverArtId(),
		imageUrl = external,
		imageCacheKey = external,
		imageRequestHeaders = imageRequestHeaders,
		contentDescription = display,
		generatedArtwork = generated
	)
}

fun aurralAlbumArtworkRenderSpec(
	id: String?,
	title: String,
	coverUrl: String?,
	primaryType: String?,
	imageRequestHeaders: Map<String, String> = emptyMap(),
	variant: GeneratedArtworkVariant = GeneratedArtworkVariant.GridCard
): ArtworkRenderSpec {
	val trimmedCoverUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() }
	val trimmedTitle = title.trim().ifEmpty { "Album" }
	val seed = id?.trim()?.takeIf { it.isNotEmpty() } ?: trimmedTitle
	return ArtworkRenderSpec(
		coverArtId = null,
		imageUrl = trimmedCoverUrl,
		imageCacheKey = id?.trim()?.takeIf { it.isNotEmpty() }?.let { "aurral-release-group-$it" } ?: trimmedCoverUrl,
		imageRequestHeaders = imageRequestHeaders,
		contentDescription = trimmedTitle,
		generatedArtwork = GeneratedArtworkSpec(
			kindLabel = primaryType?.trim()?.takeIf { it.isNotEmpty() } ?: "Album",
			primaryLabel = trimmedTitle,
			seed = seed,
			variant = variant
		)
	)
}

fun generatedArtworkSpec(
	kindLabel: String?,
	primaryLabel: String?,
	seed: String?,
	variant: GeneratedArtworkVariant = GeneratedArtworkVariant.GridCard
): GeneratedArtworkSpec =
	GeneratedArtworkSpec(
		kindLabel = kindLabel,
		primaryLabel = primaryLabel?.trim()?.takeIf { it.isNotEmpty() },
		seed = seed?.trim()?.takeIf { it.isNotEmpty() },
		variant = variant
	)

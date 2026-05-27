package paige.navic.domain.models

data class DomainLidaClip(
	val id: Int,
	val navidromeSongId: String?,
	val title: String,
	val artist: String?,
	val album: String?,
	val track: String?,
	val durationSeconds: Int?,
	val mimeType: String?,
	val score: Float?,
	val qualityTier: String?,
	val fileName: String?,
	val streamUrl: String
)

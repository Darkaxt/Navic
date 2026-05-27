package paige.navic.domain.models

private const val ANDROID_O = 26
private const val ANDROID_S = 31

fun supportsLidaClipsPictureInPicture(
	sdkInt: Int,
	hasPictureInPictureFeature: Boolean
): Boolean = sdkInt >= ANDROID_O && hasPictureInPictureFeature

fun shouldUseLidaClipsAutoPictureInPictureParams(
	enabled: Boolean,
	sdkInt: Int,
	hasPictureInPictureFeature: Boolean
): Boolean =
	enabled &&
		sdkInt >= ANDROID_S &&
		supportsLidaClipsPictureInPicture(sdkInt, hasPictureInPictureFeature)

fun shouldEnterLidaClipsPictureInPictureOnUserLeave(
	enabled: Boolean,
	videoActive: Boolean,
	alreadyInPictureInPicture: Boolean,
	sdkInt: Int,
	hasPictureInPictureFeature: Boolean
): Boolean =
	enabled &&
		videoActive &&
		!alreadyInPictureInPicture &&
		sdkInt in ANDROID_O until ANDROID_S &&
		supportsLidaClipsPictureInPicture(sdkInt, hasPictureInPictureFeature)

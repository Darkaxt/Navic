package paige.navic.ui.screens.lidaClips

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import paige.navic.domain.models.shouldEnterLidaClipsPictureInPictureOnUserLeave
import paige.navic.domain.models.shouldUseLidaClipsAutoPictureInPictureParams
import paige.navic.domain.models.supportsLidaClipsPictureInPicture
import paige.navic.util.core.Logger

object LidaClipPictureInPictureCoordinator {
	private const val TAG = "LidaClipPiP"

	private var activeActivity: Activity? = null
	private var enabled = false
	private var sourceRect: Rect? = null

	fun register(activity: Activity, enabled: Boolean) {
		activeActivity = activity
		this.enabled = enabled
		applyPictureInPictureParams(activity)
	}

	fun updateSourceRect(activity: Activity, sourceRect: Rect) {
		if (activeActivity !== activity) return

		this.sourceRect = sourceRect
		applyPictureInPictureParams(activity)
	}

	fun unregister(activity: Activity) {
		if (activeActivity !== activity) return

		activeActivity = null
		enabled = false
		sourceRect = null
		applyPictureInPictureParams(activity)
	}

	fun onUserLeaveHint(activity: Activity) {
		if (activeActivity !== activity) return

		val sdkInt = Build.VERSION.SDK_INT
		val hasFeature = activity.hasPictureInPictureFeature()
		if (!shouldEnterLidaClipsPictureInPictureOnUserLeave(
				enabled = enabled,
				videoActive = activeActivity === activity,
				alreadyInPictureInPicture = activity.isInPictureInPictureMode,
				sdkInt = sdkInt,
				hasPictureInPictureFeature = hasFeature
			)
		) {
			return
		}

		runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				activity.enterPictureInPictureMode(buildParams(autoEnter = false))
			}
		}.onFailure { error ->
			Logger.w(TAG, "Failed to enter Picture-in-Picture", error)
		}
	}

	private fun applyPictureInPictureParams(activity: Activity) {
		val sdkInt = Build.VERSION.SDK_INT
		if (!supportsLidaClipsPictureInPicture(sdkInt, activity.hasPictureInPictureFeature())) return
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

		val autoEnter = shouldUseLidaClipsAutoPictureInPictureParams(
			enabled = enabled && activeActivity === activity,
			sdkInt = sdkInt,
			hasPictureInPictureFeature = activity.hasPictureInPictureFeature()
		)

		runCatching {
			activity.setPictureInPictureParams(buildParams(autoEnter))
		}.onFailure { error ->
			Logger.w(TAG, "Failed to update Picture-in-Picture params", error)
		}
	}

	private fun buildParams(autoEnter: Boolean): PictureInPictureParams {
		val builder = PictureInPictureParams.Builder()
			.setAspectRatio(Rational(16, 9))

		sourceRect?.let(builder::setSourceRectHint)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			builder
				.setAutoEnterEnabled(autoEnter)
				.setSeamlessResizeEnabled(true)
		}

		return builder.build()
	}

	private fun Activity.hasPictureInPictureFeature(): Boolean =
		packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}

package paige.navic.shared

import android.net.Uri
import androidx.core.net.toUri
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager

internal fun playbackStreamUri(
	id: String,
	sessionManager: SessionManager,
	preferenceManager: PreferenceManager,
	isCellular: Boolean
): Uri {
	val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
		if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
	} else {
		if (isCellular) preferenceManager.streamingQualityCellular.bitrateAndroid else preferenceManager.streamingQualityWifi.bitrateAndroid
	}
	val container = if (isCellular) preferenceManager.streamingQualityCellular.containerAndroid else preferenceManager.streamingQualityWifi.containerAndroid
	return sessionManager.getStreamUrl(id, bitrate, container).toUri().buildUpon()
		.appendQueryParameter("estimateContentLength", "true").build()
}

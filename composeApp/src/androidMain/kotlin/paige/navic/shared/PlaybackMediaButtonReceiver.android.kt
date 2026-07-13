package paige.navic.shared

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class PlaybackMediaButtonReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
		val serviceIntent = Intent(intent).apply {
			component = ComponentName(context, PlaybackService::class.java)
		}
		ContextCompat.startForegroundService(context, serviceIntent)
	}
}

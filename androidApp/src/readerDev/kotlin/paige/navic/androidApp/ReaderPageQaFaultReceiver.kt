package paige.navic.androidApp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.Log
import paige.navic.ui.screens.reader.ReaderPageQaFaultCommand
import paige.navic.ui.screens.reader.ReaderPageQaFaultCommandDecoder
import paige.navic.ui.screens.reader.ReaderPageQaFaultControl

class ReaderPageQaFaultReceiver : BroadcastReceiver() {
	private companion object {
		const val Tag = "ReaderPageQaFault"
	}

	override fun onReceive(context: Context, intent: Intent) {
		check(Looper.myLooper() === Looper.getMainLooper()) {
			"Reader QA fault commands are Main-thread owned"
		}
		if (!ReaderPageQaFaultCommandDecoder.acceptsAction(intent.action)) {
			Log.i(
				Tag,
				"requestId=invalid accepted=false reason=" +
					ReaderPageQaFaultCommand.Rejection.InvalidAction.name
			)
			return
		}
		when (val decoded = ReaderPageQaFaultCommandDecoder.decode(
			requestId = intent.getStringExtra("requestId"),
			command = intent.getStringExtra("command"),
			faultName = intent.getStringExtra("fault")
		)) {
			is ReaderPageQaFaultCommand.Enqueue -> {
				val accepted = ReaderPageQaFaultControl.enqueue(
					decoded.requestId,
					decoded.fault
				)
				Log.i(
					Tag,
					"requestId=${decoded.requestId} fault=${decoded.fault} " +
						"accepted=$accepted"
				)
			}
			is ReaderPageQaFaultCommand.ReleasePublication -> Log.i(
				Tag,
				"requestId=${decoded.requestId} releasePublication=" +
					ReaderPageQaFaultControl.releasePublication(decoded.requestId)
			)
			is ReaderPageQaFaultCommand.ReleaseRelocation -> Log.i(
				Tag,
				"requestId=${decoded.requestId} releaseRelocation=" +
					ReaderPageQaFaultControl.releaseRelocationAck(decoded.requestId)
			)
			is ReaderPageQaFaultCommand.ReleaseVisualState -> Log.i(
				Tag,
				"requestId=${decoded.requestId} releaseVisualState=" +
					ReaderPageQaFaultControl.releaseVisualState(decoded.requestId)
			)
			is ReaderPageQaFaultCommand.Clear -> Log.i(
				Tag,
				"requestId=${decoded.requestId} cleared=" +
					ReaderPageQaFaultControl.clear(decoded.requestId)
			)
			is ReaderPageQaFaultCommand.Rejected -> Log.i(
				Tag,
				"requestId=${decoded.requestId} accepted=false reason=" +
					decoded.reason.name
			)
		}
	}
}

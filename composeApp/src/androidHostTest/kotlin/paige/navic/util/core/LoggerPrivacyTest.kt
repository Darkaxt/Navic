package paige.navic.util.core

import android.util.Log
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class LoggerPrivacyTest {
	@Before
	fun setUp() {
		ShadowLog.clear()
	}

	@After
	fun tearDown() {
		Logger.configureIssueLogSink(null)
	}

	@Test
	fun throwableContentDoesNotReachAndroidLogAtAnyLevel() {
		val tag = "SyntheticReaderPrivacy"
		val throwableMarker = "SYNTHETIC_THROWABLE_MARKER"

		Logger.d(tag, "reader-debug state=failed", IllegalStateException(throwableMarker))
		Logger.i(tag, "reader-info state=failed", IllegalStateException(throwableMarker))
		Logger.w(tag, "reader-warning state=failed", IllegalStateException(throwableMarker))
		Logger.e(tag, "reader-error state=failed", IllegalStateException(throwableMarker))

		val androidEntries = ShadowLog.getLogsForTag(tag)
		assertEquals(listOf(Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR), androidEntries.map { it.type })
		assertEquals(
			listOf(
				"reader-debug state=failed",
				"reader-info state=failed",
				"reader-warning state=failed",
				"reader-error state=failed"
			),
			androidEntries.map { it.msg }
		)
		androidEntries.forEach { entry ->
			assertNull(entry.throwable)
			assertFalse(entry.toString().contains(throwableMarker))
		}
	}

	@Test
	fun throwableContentDoesNotReachIssueLogSinkAtAnyLevel() {
		val tag = "SyntheticReaderPrivacy"
		val throwableMarker = "SYNTHETIC_THROWABLE_MARKER"
		val events = mutableListOf<LoggerEvent>()
		Logger.configureIssueLogSink(events::add)

		Logger.d(tag, "reader-debug state=failed", IllegalStateException(throwableMarker))
		Logger.i(tag, "reader-info state=failed", IllegalStateException(throwableMarker))
		Logger.w(tag, "reader-warning state=failed", IllegalStateException(throwableMarker))
		Logger.e(tag, "reader-error state=failed", IllegalStateException(throwableMarker))

		assertEquals(
			listOf(AppLogLevel.Debug, AppLogLevel.Info, AppLogLevel.Warning, AppLogLevel.Error),
			events.map { it.level }
		)
		events.forEach { event ->
			assertNull(event.throwable)
			assertFalse(event.toString().contains(throwableMarker))
		}
	}
}

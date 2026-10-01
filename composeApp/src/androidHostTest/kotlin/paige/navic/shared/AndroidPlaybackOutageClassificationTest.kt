package paige.navic.shared

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoTimeoutException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidPlaybackOutageClassificationTest {
	@Test
	fun internalTimeoutIsNotANavidromeOutage() {
		assertFalse(isNavidromePlaybackOutage(PlaybackException("Unexpected runtime error",
			ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_SET_FOREGROUND_MODE),
			PlaybackException.ERROR_CODE_TIMEOUT)))
	}

	@Test
	fun sourceTransportFailuresAreOutagesButTerminalHttpErrorsAreNot() {
		assertTrue(isNavidromePlaybackOutage(PlaybackException("Source error",
			SocketTimeoutException("read timed out"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
		assertTrue(isNavidromePlaybackOutage(PlaybackException("Source error",
			IllegalStateException("Response code: 503"), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)))
		assertFalse(isNavidromePlaybackOutage(PlaybackException("Source error",
			IllegalStateException("Response code: 403"), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)))
	}
}

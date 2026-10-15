package paige.navic.ui.screens.reader

import android.os.Build
import android.os.Looper
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Drive the ORIGINAL shared teardown task, not a success substitute. The owned
 * fixture must run its Main continuation after real off-main cleanup returns. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderOwnedMainTeardownDriveHostTest {
    @Test fun originalTeardownMainReturnIsDrivenAfterOwnedOffMainCleanup() = runTest {
        // Same issuing-thread driver already used by NativeInstallationFixture.
        // No production dispatcher, timer, cleanup owner or completion is replaced.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val entered = CompletableDeferred<Unit>()
        val allowReturn = CompletableDeferred<Unit>()
        val original = ReaderPageSharedTeardownTask(scope) {
            withContext(Dispatchers.Default) {
                entered.complete(Unit)
                allowReturn.await()
            }
        }
        val closed = original.start()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            withContext(Dispatchers.Default) {
                entered.await()
                // Force return AFTER the fixture's only idle, making the missing
                // Main continuation drive deterministic instead of an IO race.
                allowReturn.complete(Unit)
                withTimeout(5_000L) { closed.await() }
            }
            assertTrue(closed.isCompleted, "Only the original teardown producer may establish completion")
        } finally {
            try {
                allowReturn.complete(Unit)
                shadowOf(Looper.getMainLooper()).idle()
                scope.cancel()
                shadowOf(Looper.getMainLooper()).idle()
                testScheduler.runCurrent()
            } finally { Dispatchers.resetMain() }
        }
    }
}

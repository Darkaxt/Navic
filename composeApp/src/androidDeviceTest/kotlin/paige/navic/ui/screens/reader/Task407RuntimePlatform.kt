package paige.navic.ui.screens.reader

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.runner.AndroidJUnitRunner
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal const val Task407RuntimePackage = "paige.navic.task407.runtime"

/** Test-only self-instrumented Activity; no Navic account/application startup. */
class Task407RuntimeActivity : ComponentActivity() {
    internal lateinit var readerComposeView: ComposeView
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        task407Require(packageName == Task407RuntimePackage, "A407.Runtime.DisposablePackageRequired")
        readerComposeView = ComposeView(this)
        setContentView(readerComposeView)
    }
}

/** Never expose a framework Throwable message, cause, suppressed failure or stack. */
class Task407RuntimeRunner : AndroidJUnitRunner() {
    override fun onException(obj: Any?, e: Throwable?): Boolean {
        finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "E407.Runtime.FrameworkException") })
        return true
    }
}

/** Only source-static labels may be supplied. Cleanup labels survive sanitization; original
 * exceptions never do. Primary label remains first even when several cleanup steps fail.
 */
internal class Task407FixedFailure(val fixedLabel: String, val cleanupLabels: List<String> = emptyList()) :
    AssertionError((listOf(fixedLabel) + cleanupLabels).joinToString("\n")) {
    init { stackTrace = emptyArray() }
    fun sanitizedCopy() = Task407FixedFailure(fixedLabel, cleanupLabels.toList())
}
internal fun task407Require(condition: Boolean, fixedLabel: String) {
    if (!condition) throw Task407FixedFailure(fixedLabel)
}
internal fun <T : Any> task407Required(value: T?, fixedLabel: String): T {
    task407Require(value != null, fixedLabel)
    return value!!
}

/** Accounting for test/cleanup FAILURE only, not physical ownership or successful completion. */
internal class Task407FailureAccount {
    private var primaryLabel: String? = null
    private val cleanupLabels = mutableListOf<String>()
    fun record(failure: Throwable, fixedFallback: String) {
        val safe = failure as? Task407FixedFailure
        val label = safe?.fixedLabel ?: fixedFallback
        if (primaryLabel == null) primaryLabel = label else cleanupLabels += label
        if (safe != null) cleanupLabels += safe.cleanupLabels
    }
    fun attempt(fixedFallback: String, action: () -> Unit) {
        try { action() } catch (failure: Throwable) { record(failure, fixedFallback) }
    }
    fun throwIfAny() {
        primaryLabel?.let { throw Task407FixedFailure(it, cleanupLabels.toList()) }
    }
}

internal class Task407FixedDiagnosticsRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try { base.evaluate() }
            catch (safe: Task407FixedFailure) { throw safe.sanitizedCopy() }
            catch (_: Throwable) { throw Task407FixedFailure("E407.Runtime.UnexpectedFailure") }
        }
    }
}

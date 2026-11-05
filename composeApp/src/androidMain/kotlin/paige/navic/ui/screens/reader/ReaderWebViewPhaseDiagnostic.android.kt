package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderTransitionCancellationReason
import paige.navic.reader.ReaderTransitionFactKind
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.util.core.Logger

internal enum class ReaderWebViewDiagnosticSource { Engine, PassiveRaster }
internal enum class ReaderWebViewDiagnosticPhase {
	RuntimeLoad, Initialization, Ready, Query, Commit, PixelCopy, Retirement, Cleanup
}
internal enum class ReaderWebViewDiagnosticState { Started, Completed, Rejected, Failed, Cancelled, Retired }
internal enum class ReaderWebViewDiagnosticReason {
	None, RendererGone, RuntimeReadyPollBudget, CommitPollBudget, WebViewError, HttpError,
	Cancelled, CleanupThrew, PixelCopyResult, PixelCopySubmissionThrew,
	CommitUnavailable, RuntimeLoadThrew, JavascriptSubmissionThrew, OperationIdInvalid, OperationFailed,
	ReceiptRejected, Destroyed, Paused, NotReady, GeometryMismatch, UnsupportedPlatform,
	StaleVisualState, ViewSizeMismatch, WindowBounds, BitmapAllocation, QueryRejected,
	QueryFailed, RuntimeRetired, PublicationReplaced, Closed, SubmissionRejected, SubmissionThrew
}

/** Fixed categories and primitive platform results only: never payloads, identities or exceptions. */
internal object ReaderWebViewPhaseDiagnostic {
	fun format(
		source: ReaderWebViewDiagnosticSource,
		phase: ReaderWebViewDiagnosticPhase,
		state: ReaderWebViewDiagnosticState,
		reason: ReaderWebViewDiagnosticReason,
		elapsedMs: Long,
		code: Int? = null,
		mainFrame: Boolean? = null,
		didCrash: Boolean? = null,
		priorityAtExit: Int? = null
	): String = buildString {
		append("reader-webview-phase source=").append(source.name)
		append(" phase=").append(phase.name)
		append(" state=").append(state.name)
		append(" reason=").append(reason.name)
		append(" elapsedMs=").append(elapsedMs.coerceAtLeast(0L))
		if (code != null) append(" code=").append(code)
		if (mainFrame != null) append(" mainFrame=").append(mainFrame)
		if (didCrash != null) append(" didCrash=").append(didCrash)
		if (priorityAtExit != null) append(" priorityAtExit=").append(priorityAtExit)
	}
}

internal const val ReaderStageDiagnosticTag = "ReaderStageDiagnostic"

internal enum class ReaderStageDiagnosticPhase {
	MaterialAllocation,
	InstalledPreparation,
	PublicationWrite,
	PublicationCost,
	PublicationAcceptance,
	TargetProgress,
	InstalledProgress,
	RasterReady,
	DeckPreparation,
	DeckBuild,
	DeckSubmission,
	CoordinatorFact,
	DeadlineProgress,
	DisplayPublication,
	ShellPublication,
	TurnTerminal
}

internal enum class ReaderStageDiagnosticReason {
	None,
	Stale,
	Closed,
	Cancelled,
	DiskCapacity,
	EncodeIdentityReleasing,
	CapacityReached,
	PortRejected,
	CommandThrew,
	NotReady,
	DeckCapabilitiesUnavailable,
	DeckSessionDetached,
	DeckStaleGeneration,
	DeckConflictingGeneration,
	DeckResourceCapacity,
	DeckDisposed,
	DeckInvalidContent,
	DeckReplacedWhileSettling
}

/** Ordered bits describe measured spans, including genuine sub-millisecond (zero ms) work. */
internal enum class ReaderPublicationCostSpan(val field: String) {
	IoDispatch("ioDispatchMs"), IoReturn("ioReturnMs"), Encode("encodeMs"),
	RasterSync("rasterSyncMs"), Hash("hashMs"), CacheLock("cacheLockMs"),
	RasterPromote("rasterPromoteMs"), ManifestSerialize("manifestSerializeMs"),
	ManifestSync("manifestSyncMs"), ManifestPromote("manifestPromoteMs")
}

/** Publication-local scalars only. No clock/sink callbacks are invoked under cache locks. */
internal class ReaderPublicationCost {
	private val durations = LongArray(ReaderPublicationCostSpan.entries.size)
	var measuredMask: Int = 0
		private set

	fun record(span: ReaderPublicationCostSpan, startedNanos: Long, endedNanos: Long = System.nanoTime()) {
		val delta = endedNanos - startedNanos
		if (delta < 0L) return
		durations[span.ordinal] = delta / 1_000_000L
		measuredMask = measuredMask or (1 shl span.ordinal)
	}

	fun appendTo(output: StringBuilder) {
		output.append(" measuredMask=").append(measuredMask)
		ReaderPublicationCostSpan.entries.forEach { span ->
			output.append(' ').append(span.field).append('=').append(durations[span.ordinal])
		}
	}
}

/** Preserves the original operation's return/exception; timing never invokes external observers. */
internal inline fun <T> readerMeasurePublicationCost(
	cost: ReaderPublicationCost?,
	span: ReaderPublicationCostSpan,
	action: () -> T
): T {
	if (cost == null) return action()
	val started = System.nanoTime()
	return try { action() } finally { cost.record(span, started) }
}

/** Fixed stage categories and scalar fields only: never identities, payloads or exceptions. */
internal object ReaderStageDiagnostic {
	fun format(
		phase: ReaderStageDiagnosticPhase,
		state: ReaderWebViewDiagnosticState,
		reason: ReaderStageDiagnosticReason,
		elapsedMs: Long,
		current: Boolean? = null,
		persisted: Boolean? = null,
		completedCount: Int? = null,
		requiredCount: Int? = null,
		factKind: ReaderTransitionFactKind? = null,
		classification: ReaderTransitionFactClassification? = null,
		applied: Boolean? = null,
		outcome: ReaderTransitionOutcomeKind? = null,
		failure: ReaderTransitionFailureReason? = null,
		cancellation: ReaderTransitionCancellationReason? = null,
		cost: ReaderPublicationCost? = null
	): String = buildString {
		append("reader-stage phase=").append(phase.name)
		append(" state=").append(state.name)
		append(" reason=").append(reason.name)
		append(" elapsedMs=").append(elapsedMs.coerceAtLeast(0L))
		if (current != null) append(" current=").append(current)
		if (persisted != null) append(" persisted=").append(persisted)
		if (completedCount != null) append(" completedCount=").append(completedCount)
		if (requiredCount != null) append(" requiredCount=").append(requiredCount)
		if (factKind != null) append(" fact=").append(factKind.name)
		if (classification != null) append(" classification=").append(classification.name)
		if (applied != null) append(" applied=").append(applied)
		if (outcome != null) append(" outcome=").append(outcome.name)
		if (failure != null) append(" failure=").append(failure.name)
		if (cancellation != null) append(" cancellation=").append(cancellation.name)
		cost?.appendTo(this)
	}.also { message -> require(isSafeMessage(message)) }

	fun emit(
		phase: ReaderStageDiagnosticPhase,
		state: ReaderWebViewDiagnosticState,
		reason: ReaderStageDiagnosticReason,
		elapsedMs: Long,
		current: Boolean? = null,
		persisted: Boolean? = null,
		completedCount: Int? = null,
		requiredCount: Int? = null,
		factKind: ReaderTransitionFactKind? = null,
		classification: ReaderTransitionFactClassification? = null,
		applied: Boolean? = null,
		outcome: ReaderTransitionOutcomeKind? = null,
		failure: ReaderTransitionFailureReason? = null,
		cancellation: ReaderTransitionCancellationReason? = null,
		cost: ReaderPublicationCost? = null,
		write: (String) -> Unit = { Logger.i(ReaderStageDiagnosticTag, it) }
	): Unit {
		runCatching {
			write(format(
				phase = phase,
				state = state,
				reason = reason,
				elapsedMs = elapsedMs,
				current = current,
				persisted = persisted,
				completedCount = completedCount,
				requiredCount = requiredCount,
				factKind = factKind,
				classification = classification,
				applied = applied,
				outcome = outcome,
				failure = failure,
				cancellation = cancellation,
				cost = cost
			))
		}
	}

	fun isSafeMessage(message: String): Boolean {
		if (message.length > 512 || message.isEmpty() || message.contains("  ")) return false
		val fields = message.split(' ')
		if (fields.firstOrNull() != "reader-stage") return false
		val pairs = fields.drop(1).map { field ->
			field.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() && it[1].isNotEmpty() }
				?: return false
		}
		fun valueAt(index: Int, key: String): String? = pairs.getOrNull(index)
			?.takeIf { it[0] == key }
			?.get(1)
		val phase = valueAt(0, "phase").enumValueOrNull(ReaderStageDiagnosticPhase.entries) ?: return false
		if (valueAt(1, "state").enumValueOrNull(ReaderWebViewDiagnosticState.entries) == null) return false
		if (valueAt(2, "reason").enumValueOrNull(ReaderStageDiagnosticReason.entries) == null) return false
		if (valueAt(3, "elapsedMs").nonnegativeLongOrNull() == null) return false
		val extra = pairs.drop(4)
		return when (phase) {
			ReaderStageDiagnosticPhase.PublicationCost ->
				valueAt(1, "state") in setOf("Completed", "Failed") &&
					valueAt(2, "reason") == "None" && extra.publicationCostPairs()
			ReaderStageDiagnosticPhase.PublicationWrite -> extra.booleanPair("current", "persisted")
			ReaderStageDiagnosticPhase.PublicationAcceptance -> extra.booleanPair("applied")
			ReaderStageDiagnosticPhase.TargetProgress,
			ReaderStageDiagnosticPhase.RasterReady -> extra.progressPairs()
			ReaderStageDiagnosticPhase.CoordinatorFact -> extra.coordinatorFactPairs()
			else -> extra.isEmpty()
		}
	}

	private fun List<List<String>>.publicationCostPairs(): Boolean {
		if (size != 11 || this[0][0] != "measuredMask") return false
		val mask = this[0][1].nonnegativeIntOrNull()?.takeIf { it <= 1023 } ?: return false
		return ReaderPublicationCostSpan.entries.all { span ->
			val pair = this[span.ordinal + 1]
			val duration = pair[1].nonnegativeLongOrNull()
			pair[0] == span.field && duration != null &&
				(mask and (1 shl span.ordinal) != 0 || duration == 0L)
		}
	}

	private fun List<List<String>>.booleanPair(vararg keys: String): Boolean =
		size == keys.size && indices.all { index -> this[index][0] == keys[index] && this[index][1].booleanOrNull() != null }

	private fun List<List<String>>.progressPairs(): Boolean {
		if (size != 2 || this[0][0] != "completedCount" || this[1][0] != "requiredCount") return false
		val completed = this[0][1].nonnegativeIntOrNull() ?: return false
		val required = this[1][1].nonnegativeIntOrNull() ?: return false
		return completed <= required
	}

	private fun List<List<String>>.coordinatorFactPairs(): Boolean {
		if (size !in 2..6 || this[0][0] != "fact" || this[1][0] != "classification") return false
		if (this[0][1].enumValueOrNull(ReaderTransitionFactKind.entries) == null) return false
		if (this[1][1].enumValueOrNull(ReaderTransitionFactClassification.entries) == null) return false
		var index = 2
		if (getOrNull(index)?.get(0) == "applied") {
			if (this[index][1].booleanOrNull() == null) return false
			index++
		}
		var outcome: ReaderTransitionOutcomeKind? = null
		if (getOrNull(index)?.get(0) == "outcome") {
			outcome = this[index][1].enumValueOrNull(ReaderTransitionOutcomeKind.entries) ?: return false
			index++
		}
		if (getOrNull(index)?.get(0) == "failure") {
			if (outcome != ReaderTransitionOutcomeKind.Failed ||
				this[index][1].enumValueOrNull(ReaderTransitionFailureReason.entries) == null) return false
			index++
		}
		if (getOrNull(index)?.get(0) == "cancellation") {
			if (outcome != ReaderTransitionOutcomeKind.Cancelled ||
				this[index][1].enumValueOrNull(ReaderTransitionCancellationReason.entries) == null) return false
			index++
		}
		return index == size
	}

	private fun String?.booleanOrNull(): Boolean? = when (this) {
		"true" -> true
		"false" -> false
		else -> null
	}

	private fun String?.nonnegativeLongOrNull(): Long? =
		this?.takeIf { value -> value.isNotEmpty() && value.all { it in '0'..'9' } }
			?.toLongOrNull()?.takeIf { it >= 0L }

	private fun String?.nonnegativeIntOrNull(): Int? =
		this?.takeIf { value -> value.isNotEmpty() && value.all { it in '0'..'9' } }
			?.toIntOrNull()?.takeIf { it >= 0 }

	private fun <T : Enum<T>> String?.enumValueOrNull(values: List<T>): T? =
		values.firstOrNull { it.name == this }
}

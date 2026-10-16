package paige.navic.ui.screens.reader

/** Diagnostic inputs only. LOGGER means arrival at the qualified existing logger observer;
 * SAMPLE means a later main-looper read, never an original callback/timer-bind timestamp. */
internal enum class Task407StartupPhaseEvent {
    OBSERVATION_INSTALL_ATTEMPT,
    FIXTURE_MOUNT_ENTERED,
    LOGGER_CONSOLE_MODULE_LOADED,
    LOGGER_BRIDGE_READY,
    LOGGER_BRIDGE_PUBLICATION_READY,
    LOGGER_BRIDGE_COMMAND_ACK,
    LOGGER_BRIDGE_COMMAND_FAILED,
    LOGGER_CURRENT_DESTINATION_RESOLVED,
    LOGGER_OPEN_PUBLICATION_DISPATCHED,
    LOGGER_CURRENT_DESTINATION_QUERY_DISPATCHED,
    SAMPLE_ROOT_PRESENT,
    SAMPLE_COORDINATOR_PRESENT,
    SAMPLE_MATERIAL_PRESENT,
    SAMPLE_ENGINE_PRESENT,
    SAMPLE_ENGINE_INITIALIZATION_COMPLETED,
    SAMPLE_ENGINE_ENTRYPOINT,
    SAMPLE_ENGINE_ATTACHED,
    SAMPLE_ENGINE_SIZED,
    SAMPLE_ENGINE_PROGRESS_COMPLETE,
    SAMPLE_ENGINE_RETIRED,
    SAMPLE_CONSTRUCTION_MOUNTED,
    SAMPLE_CONSTRUCTION_EXECUTOR_BOUND,
    SAMPLE_CONSTRUCTION_WAITING,
    SAMPLE_CONSTRUCTION_STARTING,
    SAMPLE_CONSTRUCTION_QUERY,
    SAMPLE_CONSTRUCTION_RETIRED,
    SAMPLE_INITIAL_SEMANTIC_INGRESS,
    SAMPLE_TRANSITION_REGISTERED,
    SAMPLE_TRANSITION_ACTIVE,
    SAMPLE_RASTER_WORK_REGISTERED,
    SAMPLE_RASTER_PREPARING,
    SAMPLE_RASTER_READY,
    SAMPLE_RASTER_FAILED,
    SAMPLE_PASSIVE_RUNTIME_READY,
    SAMPLE_TERMINAL_MATERIAL_TIMEOUT
}

/** An installed sink checked at samples cannot establish uninterrupted historical continuity. */
internal enum class Task407StartupObserverContinuity { UNPROVEN, GAP_OBSERVED }

internal data class Task407StartupPhaseRecord(val sequence: Int, val elapsedMillis: Long,
    val event: Task407StartupPhaseEvent)
internal data class Task407StartupPhaseSnapshot(val records: List<Task407StartupPhaseRecord>,
    val droppedRecords: Int, val clockClamped: Boolean,
    val observerContinuity: Task407StartupObserverContinuity)

/** Pure, bounded fixture-owned witness. Never called by production reader code. No arbitrary
 * string/message/identity input or authority output. The caller constructs this at its logger
 * observation install attempt, NOT at the production transition timer's bind.
 *
 * One lock orders arrival at this recorder; it cannot reconstruct upstream cross-thread order.
 * Equal millisecond timestamps remain in arrival order. Each enum retains its first occurrence
 * only; neither sampled absence nor repeated occurrences are an exhaustive event history. */
internal class Task407StartupPhaseWitness(private val nowMillis: () -> Long, private val capacity: Int = 64) {
    init { require(capacity in 1..64) }
    private val anchorMillis = nowMillis()
    private val seen = mutableSetOf(Task407StartupPhaseEvent.OBSERVATION_INSTALL_ATTEMPT)
    private val records = mutableListOf(Task407StartupPhaseRecord(0, 0L,
        Task407StartupPhaseEvent.OBSERVATION_INSTALL_ATTEMPT))
    private var droppedRecords = 0
    private var clockClamped = false
    private var continuity = Task407StartupObserverContinuity.UNPROVEN

    @Synchronized fun recordFirst(event: Task407StartupPhaseEvent) {
        if (!seen.add(event)) return
        if (records.size >= capacity) {
            droppedRecords = (droppedRecords + 1).coerceAtMost(65535)
            return
        }
        val now = nowMillis()
        val delta = now - anchorMillis
        val prior = records.last().elapsedMillis
        val elapsed = when {
            now < anchorMillis || delta < 0L -> { clockClamped = true; prior }
            delta > 180000L -> { clockClamped = true; 180000L }
            delta < prior -> { clockClamped = true; prior }
            else -> delta
        }
        records += Task407StartupPhaseRecord(records.size, elapsed, event)
    }

    @Synchronized fun observerGapObserved() { continuity = Task407StartupObserverContinuity.GAP_OBSERVED }
    @Synchronized fun snapshot() = Task407StartupPhaseSnapshot(records.toList(), droppedRecords,
        clockClamped, continuity)
}

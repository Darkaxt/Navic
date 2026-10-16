package paige.navic.ui.screens.reader

import kotlin.test.*

/** Pure recorder behavior only. This does not execute the Android logger, fixture or reader. */
class Task407StartupPhaseWitnessHostTest {
    @Test fun firstArrivalOrderIsNotReorderedIntoPhaseOrCausalOrder() {
        var now = 900L
        val witness = Task407StartupPhaseWitness({ now })
        now += 10
        witness.recordFirst(Task407StartupPhaseEvent.SAMPLE_TERMINAL_MATERIAL_TIMEOUT)
        witness.recordFirst(Task407StartupPhaseEvent.LOGGER_BRIDGE_READY)
        now += 1
        witness.recordFirst(Task407StartupPhaseEvent.LOGGER_OPEN_PUBLICATION_DISPATCHED)
        now += 30
        witness.recordFirst(Task407StartupPhaseEvent.LOGGER_BRIDGE_READY)
        val snapshot = witness.snapshot()
        assertEquals(listOf(Task407StartupPhaseEvent.OBSERVATION_INSTALL_ATTEMPT,
            Task407StartupPhaseEvent.SAMPLE_TERMINAL_MATERIAL_TIMEOUT,
            Task407StartupPhaseEvent.LOGGER_BRIDGE_READY,
            Task407StartupPhaseEvent.LOGGER_OPEN_PUBLICATION_DISPATCHED), snapshot.records.map { it.event })
        assertEquals(listOf(0, 1, 2, 3), snapshot.records.map { it.sequence })
        assertEquals(listOf(0L, 10L, 10L, 11L), snapshot.records.map { it.elapsedMillis })
        assertEquals(0, snapshot.droppedRecords)
        assertFalse(snapshot.clockClamped)
    }

    @Test fun clockRegressionAndOverflowClampMonotonicallyWithoutInventingElapsedTime() {
        var now = 100L
        val witness = Task407StartupPhaseWitness({ now })
        now = 110L
        witness.recordFirst(Task407StartupPhaseEvent.LOGGER_BRIDGE_READY)
        now = 105L
        witness.recordFirst(Task407StartupPhaseEvent.SAMPLE_ENGINE_INITIALIZATION_COMPLETED)
        now = Long.MAX_VALUE
        witness.recordFirst(Task407StartupPhaseEvent.LOGGER_OPEN_PUBLICATION_DISPATCHED)
        now = Long.MIN_VALUE
        witness.recordFirst(Task407StartupPhaseEvent.SAMPLE_TERMINAL_MATERIAL_TIMEOUT)
        val snapshot = witness.snapshot()
        assertEquals(listOf(0L, 10L, 10L, 180000L, 180000L), snapshot.records.map { it.elapsedMillis })
        assertTrue(snapshot.clockClamped)
    }

    @Test fun recordCapAndUniqueDropCountCannotMasqueradeAsCompleteHistory() {
        val witness = Task407StartupPhaseWitness({ 0L }, capacity = 2)
        witness.recordFirst(Task407StartupPhaseEvent.LOGGER_BRIDGE_READY)
        repeat(100) { witness.recordFirst(Task407StartupPhaseEvent.SAMPLE_TERMINAL_MATERIAL_TIMEOUT) }
        witness.recordFirst(Task407StartupPhaseEvent.LOGGER_OPEN_PUBLICATION_DISPATCHED)
        val snapshot = witness.snapshot()
        assertEquals(2, snapshot.records.size)
        assertEquals(2, snapshot.droppedRecords, "Only first occurrences belong to this witness")
        assertEquals(Task407StartupObserverContinuity.UNPROVEN, snapshot.observerContinuity)
        for (capacity in listOf(0, 65)) assertFailsWith<IllegalArgumentException> {
            Task407StartupPhaseWitness({ 0L }, capacity)
        }
    }

    @Test fun observedGapIsStickyAndSnapshotDoesNotChangeWithLaterArrivals() {
        var now = 40L
        val witness = Task407StartupPhaseWitness({ now })
        val before = witness.snapshot()
        assertEquals(Task407StartupObserverContinuity.UNPROVEN, before.observerContinuity)
        witness.observerGapObserved()
        now++
        witness.recordFirst(Task407StartupPhaseEvent.LOGGER_BRIDGE_COMMAND_ACK)
        witness.observerGapObserved()
        val after = witness.snapshot()
        assertEquals(Task407StartupObserverContinuity.GAP_OBSERVED, after.observerContinuity)
        assertEquals(1, before.records.size)
        assertEquals(2, after.records.size)
        assertEquals(Task407StartupObserverContinuity.UNPROVEN, before.observerContinuity)
    }

    @Test fun recorderAcceptsOnlyClosedTypedEventsNotArbitraryStringsOrPayloads() {
        val record = Task407StartupPhaseWitness::class.java.declaredMethods.single { it.name == "recordFirst" }
        assertEquals(listOf(Task407StartupPhaseEvent::class.java), record.parameterTypes.toList())
        val witness = Task407StartupPhaseWitness({ 0L })
        Task407StartupPhaseEvent.entries.forEach(witness::recordFirst)
        assertTrue(witness.snapshot().records.all { it.event in Task407StartupPhaseEvent.entries })
        assertEquals(Task407StartupPhaseEvent.entries.size, witness.snapshot().records.size)
        assertEquals(0, witness.snapshot().droppedRecords)
    }
}

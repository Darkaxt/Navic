package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderTransitionFailureReason

/** Read-only aggregation of original owner custody. The existing activation coordinator
 * owns selection, drain, restoration and installation; this capture cannot grant any of them.
 * No token, clock, physical owner, callback dispatcher or release registration is created. */
internal class ReaderOriginalPhysicalInventoryCapture(
    private val admissionEpoch: (ReaderLegacyPhysicalDomain) -> Long?,
    private val freezeOriginalOwners: (ReaderLegacyPhysicalDomain, ReaderSemanticCommandExecutor) -> ReaderPortCommandResult,
    private val captureOriginalOwners: (ReaderLegacyPhysicalDomain, ReaderSemanticCommandExecutor) -> List<ReaderLegacyConnectedSourceInventory>?,
    private val originalOwnersRestored: (ReaderSemanticCommandExecutor) -> Boolean
) {
    private var domain: ReaderLegacyPhysicalDomain? = null
    private var executor: ReaderSemanticCommandExecutor? = null
    private var epoch: Long? = null
    private var frozen = false
    private var retired = false
    private var snapshotSequence = 0L
    private var discoveryVersion = 0L
    private var previousResources: List<ReaderFrozenLegacyResource>? = null

    /** Authenticate the original main-thread/session/lifetime custody before a caller
     * may close consequence routes. This read never freezes an owner or allocates custody. */
    fun canFreeze(next: ReaderLegacyPhysicalDomain, originalExecutor: ReaderSemanticCommandExecutor): Boolean {
        val currentEpoch = admissionEpoch(next) ?: return false
        if (retired || (domain != null && (domain != next || executor !== originalExecutor || epoch != currentEpoch))) return false
        return !frozen || currentSources(next, originalExecutor) != null
    }

    fun freeze(next: ReaderLegacyPhysicalDomain, originalExecutor: ReaderSemanticCommandExecutor): ReaderPortCommandResult {
        val currentEpoch = admissionEpoch(next) ?: return rejected()
        if (retired || (domain != null && (domain != next || executor !== originalExecutor || epoch != currentEpoch))) return rejected()
        if (frozen) return if (currentSources(next, originalExecutor) != null) ReaderPortCommandResult.Accepted else rejected()
        domain = next
        executor = originalExecutor
        epoch = currentEpoch
        val result = freezeOriginalOwners(next, originalExecutor)
        if (result != ReaderPortCommandResult.Accepted) return result
        if (retired || admissionEpoch(next) != epoch) return rejected()
        // Accepted means every original admission fence accepted, not that callback
        // returns have settled. Snapshot remains incomplete until all live sources agree.
        frozen = true
        return ReaderPortCommandResult.Accepted
    }

    fun snapshot(exact: ReaderLegacyPhysicalDomain): ReaderLegacyResourceInventory {
        val originalExecutor = executor ?: return ReaderLegacyResourceInventory.Incomplete
        if (retired || !frozen || domain != exact || admissionEpoch(exact) != epoch || snapshotSequence == Long.MAX_VALUE) return ReaderLegacyResourceInventory.Incomplete
        val sources = currentSources(exact, originalExecutor) ?: return ReaderLegacyResourceInventory.Incomplete
        if (retired || admissionEpoch(exact) != epoch) return ReaderLegacyResourceInventory.Incomplete
        val resources = sources.flatMap { it.resources }.sortedWith(compareBy(
            { it.physicalIdentity.source.ordinal }, { it.physicalIdentity.sourceLocalToken.value }))
        if (resources.map { it.physicalIdentity }.distinct().size != resources.size) return ReaderLegacyResourceInventory.Incomplete
        if (previousResources != resources) {
            if (discoveryVersion == Long.MAX_VALUE) return ReaderLegacyResourceInventory.Incomplete
            discoveryVersion++
            previousResources = resources
        }
        snapshotSequence++
        return ReaderLegacyResourceInventory.Complete(exact.freezeToken, snapshotSequence, discoveryVersion,
            sources.map { it.source }.toSet(), resources)
    }

    private fun currentSources(exact: ReaderLegacyPhysicalDomain, originalExecutor: ReaderSemanticCommandExecutor): List<ReaderLegacyConnectedSourceInventory>? {
        val sources = captureOriginalOwners(exact, originalExecutor) ?: return null
        if (sources.size != ReaderLegacyInventorySource.entries.size || sources.map { it.source }.toSet() != ReaderLegacyInventorySource.entries.toSet() ||
            sources.any { it.domain != exact }) return null
        return sources
    }

    fun executorForFrozenDomain(exact: ReaderLegacyPhysicalDomain): ReaderSemanticCommandExecutor? {
        if (retired || !frozen || domain != exact || admissionEpoch(exact) != epoch) return null
        return executor
    }

    /** Forget only capture metadata after the original owners have really reopened.
     * The Root consumer validates each original owner before this synchronous step. */
    fun forgetRestoredScope(exact: ReaderLegacyPhysicalDomain, originalExecutor: ReaderSemanticCommandExecutor): Boolean {
        if (retired || !frozen || domain != exact || executor !== originalExecutor || admissionEpoch(exact) != epoch ||
            !originalOwnersRestored(originalExecutor)) return false
        domain = null
        executor = null
        epoch = null
        frozen = false
        previousResources = null
        snapshotSequence = 0L
        discoveryVersion = 0L
        return true
    }

    fun retire() {
        retired = true
        frozen = false
        executor = null
        previousResources = null
    }

    private fun rejected() = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
}

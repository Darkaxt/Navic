package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderInitialPresentationInputLease
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderTransitionFailureReason

/** One original composite may own several inventory sources (bundle, or frame/Bridge/timer).
 * These are borrowed original commands and current fence reads, never replacement owners. */
internal class ReaderOriginalRestorationGroup(
    val sources: Set<ReaderLegacyInventorySource>,
    val restore: () -> ReaderPortCommandResult,
    val isRestored: () -> Boolean,
    val captureCurrency: () -> (() -> Boolean)
)

/** Root checkpoint custody only. The existing activation coordinator still chooses,
 * drains, restores and installs. Incomplete production installation remains rejected. */
internal class ReaderOriginalLegacyFreezeAndInventoryPort(
    private val domain: ReaderLegacyPhysicalDomain,
    private val executor: ReaderSemanticCommandExecutor,
    private val capture: ReaderOriginalPhysicalInventoryCapture,
    private val store: ReaderActivatedSessionSnapshotStore,
    private val checkpointId: ReaderLegacyRestorationCheckpointId,
    private val groups: List<ReaderOriginalRestorationGroup>,
    private val captureNeutralLease: () -> ReaderInitialPresentationInputLease?,
    private val drainOriginal: (ReaderLegacyPhysicalIdentity, (ReaderLegacyPhysicalIdentity) -> Unit) -> ReaderPortCommandResult,
    private val finalizeOriginal: (ReaderImportedLegacyResourceRegistration?, Set<ReaderLegacyPhysicalIdentity>) -> ReaderPortCommandResult =
        { _, _ -> ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.ActivationPrerequisiteMissing) }
) : ReaderLegacyFreezeAndInventoryPort {
    private class RestartCustody(
        val handle: ReaderLegacySourceRestartHandle,
        val group: ReaderOriginalRestorationGroup,
        val isCurrent: () -> Boolean
    )
    private var frozen = false
    private var frozenRouteGeneration: Long? = null
    private var finished = false
    private var checkpoint: ReaderLegacyRestorationCheckpoint? = null
    private var drainStarted = false
    private var restoring = false
    private var physicalCommandInProgress = false
    private val restartCustody = linkedMapOf<ReaderLegacyInventorySource, RestartCustody>()
    private val restoredGroups = linkedSetOf<ReaderOriginalRestorationGroup>()
    private val confirmedSources = linkedSetOf<ReaderLegacyInventorySource>()
    private val pendingRetirements = linkedSetOf<ReaderLegacyPhysicalIdentity>()
    private val originalConfirmedRetirements = linkedSetOf<ReaderLegacyPhysicalIdentity>()
    private var retirementContractValid = true

    init {
        require(groups.flatMap { it.sources }.let { sources ->
            sources.size == ReaderLegacyInventorySource.entries.size && sources.toSet() == ReaderLegacyInventorySource.entries.toSet()
        })
    }

    override fun freeze(): ReaderLegacyFreezeToken {
        if (!finished && !frozen && capture.canFreeze(domain, executor) && store.holdLegacyConsequences(this)) {
            frozenRouteGeneration = store.legacyRouteGeneration
            frozen = capture.freeze(domain, executor) == ReaderPortCommandResult.Accepted
        }
        return domain.freezeToken // A failed physical freeze still yields Incomplete, never a success inventory.
    }

    private fun current(): Boolean = !finished && frozen && store.ownsLegacyConsequenceHold(this) &&
        frozenRouteGeneration == store.legacyRouteGeneration && capture.executorForFrozenDomain(domain) === executor

    override fun inventory(token: ReaderLegacyFreezeToken): ReaderLegacyResourceInventory =
        if (token == domain.freezeToken && current() && !restoring) capture.snapshot(domain)
        else ReaderLegacyResourceInventory.Incomplete

    override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken): ReaderLegacyRestorationCheckpoint? {
        if (token != domain.freezeToken || !current() || restoring || drainStarted) return null
        val inventory = capture.snapshot(domain) as? ReaderLegacyResourceInventory.Complete ?: return null
        checkpoint?.let { saved ->
            return if (saved.routeGeneration == store.legacyRouteGeneration && restartCustody.values.all { it.isCurrent() }) saved else null
        }
        // This first producer handles genuine unbound startup. A bound owner must not
        // be renamed Neutral to get an incomplete package or checkpoint admitted.
        if (ReaderAdoptedPredecessorSelector.select(inventory.resources) != ReaderAdoptedPredecessorSelection.Neutral) return null
        val lease = captureNeutralLease() ?: return null
        if (lease != ReaderInitialPresentationInputLease.None && lease != ReaderInitialPresentationInputLease.ChromeOnly) return null
        for (group in groups) {
            val currency = group.captureCurrency()
            if (!currency()) return null
            for (source in group.sources) {
                restartCustody[source] = RestartCustody(ReaderLegacySourceRestartHandle(source, checkpointId.value), group, currency)
            }
        }
        if (!current()) { restartCustody.clear(); return null }
        return ReaderLegacyRestorationCheckpoint(
            id = checkpointId, freezeToken = token, routeGeneration = requireNotNull(frozenRouteGeneration),
            completedSources = restartCustody.keys.toSet(), restartHandles = restartCustody.mapValues { it.value.handle },
            initialOwner = ReaderPresentationFrameOwner.Neutral, initialBinding = null,
            initialPhysicalIdentity = null, initialResourceKind = null, initialProvenance = null,
            requestedLease = lease, physicalLease = lease
        ).also { checkpoint = it }
    }

    override fun drain(token: ReaderLegacyFreezeToken, physicalIdentity: ReaderLegacyPhysicalIdentity,
        onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit): ReaderPortCommandResult {
        if (token != domain.freezeToken || physicalIdentity.domain != domain || !current() || restoring ||
            physicalCommandInProgress || checkpoint == null || restartCustody.values.any { !it.isCurrent() }) return rejected()
        // Cached checkpoint custody is not proof that every original owner is STILL
        // fenced. Require a fresh complete capture before destructive original dispatch.
        val inventory = capture.snapshot(domain) as? ReaderLegacyResourceInventory.Complete ?: return rejected()
        if (!retirementContractValid || physicalIdentity in pendingRetirements || physicalIdentity in originalConfirmedRetirements ||
            inventory.resources.none { it.physicalIdentity == physicalIdentity }) return rejected()
        drainStarted = true // Before the original call can retire custody or reenter.
        pendingRetirements += physicalIdentity
        physicalCommandInProgress = true
        return try {
            val result = drainOriginal(physicalIdentity) { exact ->
                if (exact != physicalIdentity || !pendingRetirements.remove(exact) || !originalConfirmedRetirements.add(exact))
                    retirementContractValid = false
                onConfirmed(exact)
            }
            if (result != ReaderPortCommandResult.Accepted) {
                pendingRetirements.remove(physicalIdentity)
                if (physicalIdentity in originalConfirmedRetirements) retirementContractValid = false
            }
            result
        } finally { physicalCommandInProgress = false }
    }

    override fun finalizeActivatedHandoff(token: ReaderLegacyFreezeToken, selected: ReaderImportedLegacyResourceRegistration?,
        confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>): ReaderPortCommandResult {
        val saved = checkpoint ?: return rejected()
        val route = frozenRouteGeneration ?: return rejected()
        if (finished || !frozen || restoring || physicalCommandInProgress || token != domain.freezeToken ||
            !retirementContractValid || pendingRetirements.isNotEmpty() || confirmedRetirements != originalConfirmedRetirements ||
            !store.ownsOriginalFinalizationCustody(this, domain, route) ||
            capture.executorForFrozenDomain(domain) !== executor || restartCustody.values.any { !it.isCurrent() } ||
            saved.initialPhysicalIdentity != selected?.physicalIdentity) return rejected()
        val inventory = capture.snapshot(domain) as? ReaderLegacyResourceInventory.Complete ?: return rejected()
        if (inventory.resources.any { row -> row.physicalIdentity != selected?.physicalIdentity &&
                (row.physicalIdentity !in confirmedRetirements || row.state != ReaderLegacyResourceState.Released) }) return rejected()
        // Physical-only terminal retirement is also available to the original Root
        // before installation; it grants neither command authority nor restoration.
        physicalCommandInProgress = true
        finished = true // Consume restart custody before irreversible original calls can reenter.
        return try { finalizeOriginal(selected, confirmedRetirements) }
        finally {
            physicalCommandInProgress = false
            restartCustody.clear()
            checkpoint = null
        }
    }

    private fun restoreGroup(group: ReaderOriginalRestorationGroup): ReaderPortCommandResult {
        if (group in restoredGroups) return if (group.isRestored()) ReaderPortCommandResult.Accepted else rejected()
        if (physicalCommandInProgress) return rejected()
        physicalCommandInProgress = true
        val result = try { group.restore() } finally { physicalCommandInProgress = false }
        if (result != ReaderPortCommandResult.Accepted) return result
        if (!current() || !group.isRestored()) return rejected()
        restoredGroups += group
        return ReaderPortCommandResult.Accepted
    }

    override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken): ReaderPortCommandResult {
        if (token != domain.freezeToken || !current() || drainStarted || restoring || physicalCommandInProgress) return rejected()
        restoring = true
        for (group in groups) {
            val result = restoreGroup(group)
            if (result != ReaderPortCommandResult.Accepted) return result
        }
        return if (completeOriginalRestoration()) ReaderPortCommandResult.Accepted else rejected()
    }

    override fun restoreFromActivationCheckpoint(checkpoint: ReaderLegacyRestorationCheckpoint,
        source: ReaderLegacyInventorySource,
        onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit): ReaderPortCommandResult {
        if (this.checkpoint !== checkpoint || !current() || physicalCommandInProgress || source in confirmedSources) return rejected()
        val custody = restartCustody[source] ?: return rejected()
        if (checkpoint.restartHandles[source] !== custody.handle || restartCustody.values.any { !it.isCurrent() }) return rejected()
        restoring = true
        val result = restoreGroup(custody.group)
        if (result != ReaderPortCommandResult.Accepted) return result
        if (!current() || !custody.isCurrent() || !custody.group.isRestored()) return rejected()
        confirmedSources += source // Consume before callback; reentrant close/refreeze cannot regain this confirmation.
        onConfirmed(source, ReaderLegacyRestorationResult.Restored)
        return ReaderPortCommandResult.Accepted
    }

    override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint): ReaderLegacyCommitRestoredResult {
        if (this.checkpoint !== checkpoint || !restoring || confirmedSources != ReaderLegacyInventorySource.entries.toSet() ||
            restartCustody.values.any { !it.isCurrent() }) return ReaderLegacyCommitRestoredResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
        return if (completeOriginalRestoration()) ReaderLegacyCommitRestoredResult.Applied
        else ReaderLegacyCommitRestoredResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
    }

    private fun completeOriginalRestoration(): Boolean {
        if (!current() || physicalCommandInProgress || groups.any { it !in restoredGroups || !it.isRestored() }) return false
        val routeGeneration = frozenRouteGeneration ?: return false
        // Preflight the original route write BEFORE forgetting capture metadata. A
        // rejected/stale/exhausted route transaction must not partially clear custody.
        if (!store.canCommitRestoredLegacyConsequences(this, routeGeneration)) return false
        if (!capture.forgetRestoredScope(domain, executor)) return false
        // No callback/external call between genuine all-source validation and this
        // original store's one synchronous route reopen; installed routes remain shut.
        check(store.commitRestoredLegacyConsequences(this, routeGeneration))
        finished = true
        restartCustody.clear()
        return true
    }

    private fun rejected() = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
}

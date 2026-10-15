package paige.navic.reader

import kotlin.test.*

/** Pure policy guardrails only. Android must obtain the profile from the original
 * Curl plan owner; these synthetic facts are not Foliate or renderer readiness. */
class ReaderInitialMaterialProfileCustodyTest {
    @Test fun initialProvisionalProfileRequiresItsExactProfileFactBeforeAllocation() {
        val f = Fixture()
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        val allocation = f.allocation(id, f.binding.copy(profileGeneration = 9L))
        assertSame(resolved, resolved.reduce(ReaderTransitionFact.MaterialBindingAllocated(id, allocation)).state,
            "Unqualified profile promotion cannot start raster work")
        val qualified = resolved.reduce(ReaderTransitionFact.PaginationProfileReady(id, 9L))
        assertTrue(qualified.commands.isEmpty(), "Profile qualification is not material or display readiness")
        val prepared = qualified.state.reduce(ReaderTransitionFact.MaterialBindingAllocated(id, allocation))
        assertTrue(prepared.commands.singleOrNull() is ReaderTransitionCommand.RequestRasterPreparation,
            "Exact original profile evidence must permit the initial allocated raster command")
        assertSame(f.baseline.committed, prepared.state.committed,
            "Material allocation cannot publish an owner")
        assertTrue(checkNotNull(prepared.state.active).resolvedSuccessorBinding == allocation.allocatedBinding,
            "Allocated custody must retain the qualified profile and exact semantic destination")
    }

    @Test fun initialProfileCannotBeQualifiedBeforeTheSemanticReceipt() {
        val f = Fixture()
        val admitted = f.start()
        val id = checkNotNull(admitted.active).id
        val result = admitted.reduce(ReaderTransitionFact.PaginationProfileReady(id, 9L))
        assertSame(admitted, result.state, "Profile evidence cannot manufacture semantic authority")
        assertTrue(result.commands.isEmpty())
    }

    @Test fun wrongTransitionProfileCannotQualifyInitialMaterial() {
        val f = Fixture()
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        val result = resolved.reduce(ReaderTransitionFact.PaginationProfileReady(id.copy(coordinatorEpoch = id.coordinatorEpoch + 1L), 9L))
        assertSame(resolved, result.state, "A different operation cannot qualify the pending allocation")
        assertTrue(result.commands.isEmpty())
    }

    @Test fun qualifiedInitialProfileCannotBeReplacedByAnotherProfileFact() {
        val f = Fixture()
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        val qualified = resolved.reduce(ReaderTransitionFact.PaginationProfileReady(id, 9L)).state
        val repeated = qualified.reduce(ReaderTransitionFact.PaginationProfileReady(id, 10L))
        assertSame(qualified, repeated.state, "A qualified profile is immutable within the allocation command")
        assertTrue(repeated.commands.isEmpty())
        val wrong = f.allocation(id, f.binding.copy(profileGeneration = 10L))
        assertSame(qualified, qualified.reduce(ReaderTransitionFact.MaterialBindingAllocated(id, wrong)).state,
            "Later profile evidence cannot outvote exact allocation currency")
    }

    @Test fun profileQualificationDoesNotRelaxSemanticDestinationOrViewportEquality() {
        val f = Fixture()
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        val qualified = resolved.reduce(ReaderTransitionFact.PaginationProfileReady(id, 9L)).state
        val exact = f.binding.copy(profileGeneration = 9L)
        val changed = listOf(exact.copy(viewportGeneration = exact.viewportGeneration + 1L),
            exact.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity(exact.foliateSessionId, 12L)))
        for (binding in changed) {
            val allocation = f.allocation(id, binding)
            val result = qualified.reduce(ReaderTransitionFact.MaterialBindingAllocated(id, allocation))
            assertSame(qualified, result.state, "Qualified profile must not broaden semantic or viewport custody")
            assertTrue(result.commands.isEmpty())
        }
    }

    @Test fun initialAlreadyQualifiedSemanticProfileCannotBePromoted() {
        val f = Fixture(profile = 4L)
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        val result = resolved.reduce(ReaderTransitionFact.PaginationProfileReady(id, 9L))
        assertSame(resolved, result.state, "Nonprovisional semantic profile cannot be silently replaced")
        assertTrue(result.commands.isEmpty())
    }

    @Test fun unsolicitedRelocationDoesNotAcquireInitialProvisionalProfilePrivilege() {
        val f = Fixture()
        val resolved = f.baseline.reduce(ReaderTransitionFact.FoliateDestinationCommitted(null, f.binding)).state
        val id = checkNotNull(resolved.active).id
        assertEquals(ReaderTransitionOperation.ExternalSemanticRelocation, id.operation)
        val result = resolved.reduce(ReaderTransitionFact.PaginationProfileReady(id, 9L))
        assertSame(resolved, result.state, "Initial profile qualification cannot alter an unrelated relocation")
        assertTrue(result.commands.isEmpty())
    }

    @Test fun initialProvisionalProfileCannotAllocateZeroProfileMaterial() {
        val f = Fixture()
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        assertFailsWith<IllegalArgumentException>("Provisional zero cannot carry renderer material identity") {
            f.allocation(id, f.binding)
        }
        assertSame(f.baseline.committed, resolved.committed)
    }

    @Test fun profileCallbackBelongsOnlyToTheUnqualifiedInitialAllocationCommand() {
        val f = Fixture()
        val started = f.start()
        assertFalse(ReaderTransitionFactKind.PaginationProfileReady in checkNotNull(started.active).phase.contract.callbackSources)
        val resolved = f.resolve()
        val current = checkNotNull(resolved.active)
        assertTrue(ReaderTransitionFactKind.PaginationProfileReady in current.phase.contract.callbackSources,
            "Initial profile evidence needs declared callback custody before its producer runs")
        assertEquals(setOf(ReaderTransitionCommandStage.MaterialAllocation), current.pendingCommandStages)
        val qualified = resolved.reduce(ReaderTransitionFact.PaginationProfileReady(current.id, 9L)).state
        assertFalse(ReaderTransitionFactKind.PaginationProfileReady in checkNotNull(qualified.active).phase.contract.callbackSources,
            "Qualification consumes only its callback custody, not the allocation command")
        assertEquals(current.pendingCommandStages, checkNotNull(qualified.active).pendingCommandStages)
        assertEquals(current.phase.contract.awaitedProofs, checkNotNull(qualified.active).phase.contract.awaitedProofs)
    }

    @Test fun completedInitialAllocationCannotRequalifyItsProfile() {
        val f = Fixture()
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        val qualified = resolved.reduce(ReaderTransitionFact.PaginationProfileReady(id, 9L)).state
        val prepared = qualified.reduce(ReaderTransitionFact.MaterialBindingAllocated(id,
            f.allocation(id, f.binding.copy(profileGeneration = 9L)))).state
        assertEquals(setOf(ReaderTransitionCommandStage.RasterPreparation), checkNotNull(prepared.active).pendingCommandStages)
        assertSame(prepared, prepared.reduce(ReaderTransitionFact.PaginationProfileReady(id, 10L)).state,
            "A completed allocation cannot reopen profile qualification")
    }

    @Test fun cancelledOrClosedInitialAllocationCannotRequalifyItsProfile() {
        val f = Fixture()
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        val terminal = listOf(resolved.reduce(ReaderTransitionFact.Intent(id, ReaderCancelIntent)).state,
            resolved.reduce(ReaderTransitionFact.PublicationClosed(null)).state)
        for (state in terminal) {
            val result = state.reduce(ReaderTransitionFact.PaginationProfileReady(id, 9L))
            assertSame(state, result.state, "Terminal disposition cannot reacquire profile or material command authority")
            assertTrue(result.commands.isEmpty())
        }
    }

    @Test fun initialAlreadyQualifiedSemanticProfileStillAllocatesWithoutAnotherProfileFact() {
        val f = Fixture(profile = 4L)
        val resolved = f.resolve()
        val id = checkNotNull(resolved.active).id
        val result = resolved.reduce(ReaderTransitionFact.MaterialBindingAllocated(id, f.allocation(id, f.binding)))
        assertTrue(result.commands.singleOrNull() is ReaderTransitionCommand.RequestRasterPreparation,
            "A positive exact semantic profile needs no provisional qualification")
    }

    @Test fun retryOfUnqualifiedInitialAllocationRetainsSemanticAuthorityAndFreshProfileCustody() {
        val f = Fixture()
        val resolved = f.resolve()
        val previousId = checkNotNull(resolved.active).id
        val failed = resolved.reduce(ReaderTransitionFact.CommandRejected(previousId,
            ReaderTransitionCommandStage.MaterialAllocation, ReaderTransitionCommandRejectionReason.MaterialAllocationRejected)).state
        val retried = failed.reduce(ReaderTransitionFact.Retry(previousId))
        val current = checkNotNull(retried.state.active)
        assertEquals(previousId.sequence + 1L, current.id.sequence)
        assertEquals(previousId.parentIdentity(), current.id.parent)
        assertTrue(current.authoritativeDestinationCommitted)
        assertTrue(current.resolvedSuccessorBinding == f.binding, "Retry retains the exact original semantic destination")
        assertTrue(retried.commands.singleOrNull() is ReaderTransitionCommand.AllocateMaterialBinding,
            "Retry cannot synthesize a new semantic receipt or material readiness")
        val qualified = retried.state.reduce(ReaderTransitionFact.PaginationProfileReady(current.id, 9L)).state
        val result = qualified.reduce(ReaderTransitionFact.MaterialBindingAllocated(current.id,
            f.allocation(current.id, f.binding.copy(profileGeneration = 9L))))
        assertTrue(result.commands.singleOrNull() is ReaderTransitionCommand.RequestRasterPreparation,
            "Lawful Retry must not remain trapped at a provisional profile")
        assertSame(retried.state, retried.state.reduce(ReaderTransitionFact.PaginationProfileReady(previousId, 9L)).state,
            "Old profile evidence cannot qualify the fresh Retry command")
    }

    private class Fixture(profile: Long = 0L) {
        val baseline = ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(
            ReaderInitialCommittedPresentationOrigin.Neutral(7L, 11L,
                ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.None)))
        val binding = ReaderPresentationBinding("public-synthetic-initial-profile", 2L, 3L, profile,
            ReaderDestinationCommitIdentity("public-synthetic-initial-profile", 5L))
        fun start(): ReaderTransitionJournal = baseline.reduce(ReaderTransitionFact.Intent(null,
            ReaderBootstrapNativePageIntent(ReaderSemanticRequestHandle(1L)))).state
        fun resolve(): ReaderTransitionJournal {
            val started = start()
            return started.reduce(ReaderTransitionFact.FoliateDestinationCommitted(checkNotNull(started.active).id, binding)).state
        }
        fun allocation(id: ReaderTransitionId, binding: ReaderPresentationBinding) = ReaderMaterialGenerationAllocation(
            id, binding.copy(preparationGeneration = 6L, rasterGeneration = 7L, textureGeneration = 8L), 6L, 7L, 8L)
    }
}

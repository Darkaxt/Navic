# Bindery Cache Coherency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve B19 with credential-scoped cache keys, mutation-aware invalidation, real forced refresh, and preserved stale-on-error behavior.

**Architecture:** Cache identity and invalidation operations live in `BinderyMetadataCache`. `BinderyRepository` owns refresh and mutation policy. Existing Bindery view models pass their `fullRefresh` intent to repository reads.

**Tech Stack:** Kotlin Multiplatform, Okio SHA-256, Room DAO queries, coroutines, Kotlin test.

---

## Task 1: Credential-Scoped Keys

- [x] Add failing tests proving different API keys produce different cache keys, equal trimmed keys are stable, base URLs remain isolated, and neither key contains plaintext.
- [x] Add `binderyApiKeyFingerprint` using Okio SHA-256 and require the fingerprint in `binderyMetadataCacheKey`.
- [x] Pass the configured fingerprint at every repository cache read/write and update seeded-cache fixtures.
- [x] Run focused cache-key and repository cache tests, then commit.

## Task 2: Targeted Mutation Invalidation

- [x] Add failing tests proving progress PUT invalidates only the matching `BookSync` path, known book actions avoid a base-wide purge, and unknown actions retain a conservative base-wide fallback.
- [x] Add `clearPayload(baseUrl, payloadType, pathPrefix)` to the cache interface, Room DAO, no-op cache, and recording cache.
- [x] Implement progress and action invalidation after successful mutations only.
- [x] Run focused mutation tests, then commit.

## Task 3: Forced Refresh With Stale Fallback

- [x] Add failing tests proving `forceRefresh=true` bypasses a fresh cache and returns stale cache with failure state if the live request fails.
- [x] Thread `forceRefresh` through cached payload helpers and public metadata reads.
- [x] Pass existing view-model `fullRefresh` flags to repository calls for catalog, manifest, resources, audiobook metadata, sync, and findings.
- [x] Run focused repository/view-model tests, then commit.

Implementation evidence: RED runs failed on the absent fingerprint/key parameter, targeted invalidation ledger, and force-refresh parameter. Focused cache, repository, optional-state, mutation, DAO/DI, and progress tests passed after implementation. The cache schema did not change; Room generated the new targeted delete query. Broad UI and Android validation remains deferred by execution order.

## Deferred Validation

Room integration, all Bindery screens, Android device behavior, release versioning, and publication remain deferred until all roadmap code changes are implemented.

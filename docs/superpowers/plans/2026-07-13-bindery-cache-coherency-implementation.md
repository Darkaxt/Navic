# Bindery Cache Coherency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve B19 with credential-scoped cache keys, mutation-aware invalidation, real forced refresh, and preserved stale-on-error behavior.

**Architecture:** Cache identity and invalidation operations live in `BinderyMetadataCache`. `BinderyRepository` owns refresh and mutation policy. Existing Bindery view models pass their `fullRefresh` intent to repository reads.

**Tech Stack:** Kotlin Multiplatform, Okio SHA-256, Room DAO queries, coroutines, Kotlin test.

---

## Task 1: Credential-Scoped Keys

- [ ] Add failing tests proving different API keys produce different cache keys, equal trimmed keys are stable, base URLs remain isolated, and neither key contains plaintext.
- [ ] Add `binderyApiKeyFingerprint` using Okio SHA-256 and require the fingerprint in `binderyMetadataCacheKey`.
- [ ] Pass the configured fingerprint at every repository cache read/write and update seeded-cache fixtures.
- [ ] Run focused cache-key and repository cache tests, then commit.

## Task 2: Targeted Mutation Invalidation

- [ ] Add failing tests proving progress PUT invalidates only the matching `BookSync` path, known book actions avoid a base-wide purge, and unknown actions retain a conservative base-wide fallback.
- [ ] Add `clearPayload(baseUrl, payloadType, pathPrefix)` to the cache interface, Room DAO, no-op cache, and recording cache.
- [ ] Implement progress and action invalidation after successful mutations only.
- [ ] Run focused mutation tests, then commit.

## Task 3: Forced Refresh With Stale Fallback

- [ ] Add failing tests proving `forceRefresh=true` bypasses a fresh cache and returns stale cache with failure state if the live request fails.
- [ ] Thread `forceRefresh` through cached payload helpers and public metadata reads.
- [ ] Pass existing view-model `fullRefresh` flags to repository calls for catalog, manifest, resources, audiobook metadata, sync, and findings.
- [ ] Run focused repository/view-model tests, then commit.

## Deferred Validation

Room integration, all Bindery screens, Android device behavior, release versioning, and publication remain deferred until all roadmap code changes are implemented.

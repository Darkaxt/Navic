# Package Boundaries

Navic uses feature-owned packages inside each architectural layer.

- `domain.models` contains transport-independent application data.
- `domain.repositories` contains repository contracts and domain-facing results. It must not declare HTTP clients, wire DTOs, or URL transport policy. Domain snapshots may use serialization for persistence.
- `data.remote.<feature>` owns HTTP clients, wire DTOs, serialization, DTO mapping, endpoint construction, and remote-source security policy.
- `data.database` owns database-backed state and persistence adapters.
- `ui.<feature>` owns presentation state and interaction policy.

Remote data code may map into domain types. Domain models and contracts must not import Ktor or wire DTOs.
Package moves should be feature-sized and leave no compatibility aliases behind.

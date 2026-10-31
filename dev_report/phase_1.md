# Phase 1 — Foundation and Persistence Status

**Audit date:** 2026-10-24  
**Status: COMPLETED for the Phase 1 foundation/configuration scope.** PostgreSQL schema migration, startup seeding, Actuator health, and typed environment-backed AI provider settings are implemented. The AI adapter itself remains later-phase work.

## Scope reviewed

Phase 1 was checked against `docs/IMPLEMENTATION_PLAN.md` (Phase 1 — foundation and persistence), the configured stack, the migration, startup seeder, application properties, packaged source data, README, and current runtime/test evidence.

## Checklist

| Plan item | Status | Evidence / remaining work |
|---|---|---|
| PostgreSQL schema for workflows, runs, steps, approvals, queue jobs, and seed metadata | **Mostly complete** | Flyway V1 creates `workflows`, `runs`, `steps`, `approvals`, and `queue_jobs`, with state checks, foreign keys, and indexes. There is no separate seed-metadata table; seed identity/repeatability currently relies on the workflow primary key and conflict-safe insert. This is sufficient for the current four fixed seeds, but should be revisited if seed version tracking is required. |
| Repeatable seed loading | **Complete for current seeds** | `SeedWorkflowLoader` loads `data/seed_workflows.json`, validates required IDs/names and duplicate IDs, and inserts as Published with `ON CONFLICT (id) DO NOTHING` inside a transaction. Runtime check found four Published workflows. Existing rows are intentionally not overwritten. |
| Database URL/credentials configuration | **Complete** | URL, username, password and JDBC driver are configured through Spring properties; password defaults to empty and must come from local environment configuration. |
| Mock-world URL configuration | **Complete** | `MOCK_WORLD_URL` maps to `relay.mock-world.url`, defaulting locally to `http://localhost:9210`. |
| Demo token configuration | **Configured; not yet consumed** | `RELAY_DEMO_TOKEN` maps to `relay.demo-token`. Authentication endpoints/security are later implementation work, so this property is not yet used by request handling. |
| Provider credentials or test-adapter flag | **Complete for configuration** | `RELAY_AI_ADAPTER`, `RELAY_AI_BASE_URL`, `RELAY_AI_MODEL`, and optional `RELAY_AI_API_KEY` bind to typed `AiProviderProperties`. Defaults select mock mode at `http://localhost:9001/v1` with model `alpha-small`. The AI HTTP adapter and provider calls are not implemented yet. |
| Health/readiness endpoints | **Complete for the foundation** | Actuator exposes health/info and enables health probes. `/actuator/health` returned `UP` with `liveness` and `readiness` groups. |
| Repeatable application startup | **Complete for current state** | Startup applied Flyway V1, initialized Hibernate, started Tomcat on 8080, and loaded four Published seeds. A second context start reported schema version 1 up to date; conflict-safe inserts prevent duplicate rows. |

## Verification evidence

- `mvn -q -Dtest=AiProviderPropertiesTest test`: PASS; confirms adapter, URL, model, and API-key binding without database access.
- `mvn -q -DskipTests package`: PASS after the provider configuration edits.
- `mvn test`: passed before these provider settings were added; not rerun afterward because `RELAY_DB_PASSWORD` is not present in the current terminal environment.
- `python scripts/validate_pack.py`: PASS; 7 node types, 3 triggers, 4 seed workflows, 8 sample payloads, and 15 NL eval cases.
- Prior live startup applied Flyway migration V1 against PostgreSQL 18.6, returned `/actuator/health` as `UP`, and a database query found four Published workflows.

## Completion decision

Phase 1 is **complete for the current plan scope**. There is no separate seed-metadata table; stable workflow IDs plus conflict-safe transactional insertion provide repeatable loading for the current fixed seed set. Provider settings are configured, but no AI HTTP adapter or provider call is claimed. Workflow APIs, validators, execution worker, and business behavior belong to later phases.

## Remaining Phase 1 gates

1. Implement the AI provider adapter and route mock/real requests through the configured settings in the later AI phase; Phase 1 only configures the adapter mode and provider values.
2. Optionally add formal automated assertions for migration status and seed count; current evidence includes live manual DB checks and context startup, but no dedicated migration/seed integration test.

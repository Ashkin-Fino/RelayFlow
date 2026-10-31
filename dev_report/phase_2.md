# Phase 2 — Workflow Definition Lifecycle

**Audit date:** 2026-10-30  
**Status: IMPLEMENTED; live database/API integration remains unverified in this run.** The contract routes, create/list/publish flow, catalog-backed definition validator, and demo-token authentication are implemented. `GET /workflows/{id}` is intentionally not exposed; the Phase 2 route list in `docs/IMPLEMENTATION_PLAN.md` now uses the fixed publish route instead.

## Requirements and implementation map

| Requirement | Implementation | Status |
|---|---|---|
| `GET /workflows` lists workflows with at least `id` and `status` | `WorkflowController` returns workflow summaries ordered by ID from PostgreSQL | Implemented |
| `POST /workflows` accepts canonical definition and creates Draft | `WorkflowService.create` validates then inserts the definition JSONB with status `draft`; returns 201 with ID/status | Implemented |
| `POST /workflows/{workflowId}/publish` publishes stored definition | Loads the stored JSONB definition, revalidates it, updates status to `published`, and returns ID/status | Implemented |
| Persistence boundary | `WorkflowService` delegates to `WorkflowRepository`/`WorkflowEntity` in `workflow.persistence`; the entity maps `definition` as JSONB | Implemented; persistence is kept out of the service as inline SQL |
| Catalog node types, required parameters, types, and enum values | `WorkflowDefinitionValidator` reads metadata from `NodeCatalog` and validates catalog entries | Implemented |
| Trigger configuration and canonical definition shape | Validator checks required top-level fields, trigger type/config, limits/max_steps, nodes/params | Implemented |
| Entry and outgoing pointers; legal loops | Validator requires an existing entry and checks every required `next` or condition branch target; backward/self edges are allowed | Implemented |
| Bearer demo-token authentication | `DemoTokenFilter` protects application routes other than Actuator and the future webhook prefix; uses constant-time token comparison and returns structured 401 | Implemented |
| Structured API errors | Advice maps invalid definitions to 422, unknown workflow to 404, duplicate ID to 409, malformed JSON to 400; error payload uses `error.message` and `error.code` | Implemented |
| Workflow detail `GET /workflows/{id}` | Excluded per fixed-contract route choice; no such endpoint is implemented | Intentionally excluded |

## Verification

- `WorkflowDefinitionValidatorTest`: validates all four canonical seeds; checks unknown node type, missing required parameter, invalid enum, invalid entry, invalid pointer, and accepts a self-loop.
- `WorkflowControllerTest`: exercises list/create/publish route mappings, verifies valid/missing/invalid bearer tokens, and checks structured validation errors using standalone MockMvc.
- `NodeCatalogTest`: packaged catalog integrity check.
- `python scripts/validate_pack.py`: verifies canonical pack data remains consistent.
- `mvn -q "-Dtest=WorkflowDefinitionValidatorTest,WorkflowControllerTest,NodeCatalogTest,AiProviderPropertiesTest" test`: PASS.
- `mvn -q -DskipTests package`: PASS; `git diff --check`: PASS.
- A live `scripts/smoke_test.py` against the endpoints should be recorded separately when the caller's `RELAY_DB_PASSWORD` is available in the test terminal.

## Limitations and next dependencies

- No workflow detail endpoint was added, as requested; consumers get list summaries and can publish by ID.
- Definitions are validated at both create and publish time. There is no workflow update endpoint in this phase.
- Runtime node execution, worker queue, manual/webhook triggers, run traces, and approval decisions are Phase 3+ work. The workflow publish endpoint does not claim that those features exist.
- `DemoTokenFilter` leaves `/actuator/**` public for health checks and `/hooks/**` unfiltered for the future per-workflow secret mechanism; webhook routes are not implemented yet.
- Validation uses the catalog as the source for types, required params, enum values, and branches. It does not evaluate templates or enforce runtime approval gates; those belong to later engine phases.
